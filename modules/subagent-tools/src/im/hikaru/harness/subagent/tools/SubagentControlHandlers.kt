package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.UserMessageSource
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.SessionOrigin
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.api.DescendantStatus
import im.hikaru.harness.session.api.SessionApi
import im.hikaru.harness.session.api.SessionDescendantsResult
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Identity and liveness checks shared by the subagent control tools. */
internal class SubagentControlChecks(
    private val hostContext: Context,
    private val registry: AgentRegistry,
    private val store: SessionStore,
) {
    fun checkContext(context: Context) {
        check(!context.isDisposed) { "Execution context is closed" }
        check(context.get(AgentKey) === registry) { "Execution uses another AgentRegistry" }
        check(context.get(SessionKey) === store) { "Execution uses another SessionStore" }
    }

    suspend fun checkLive(agent: Agent) {
        checkContext(agent.context)
        check(agent.id.value == agent.session.id.value) { "Agent/session ID mismatch" }
        check(registry.get(agent.id) === agent) { "Live agent identity changed" }
        check(store.get(agent.session.id) === agent.session && agent.session.isLive()) { "Session is no longer live" }
    }

    /** Resolve the exact live calling agent supplied by the Agent, never by tool arguments. */
    suspend fun caller(execution: ToolExecutionContext): Agent {
        checkContext(hostContext)
        checkContext(execution.context)
        val caller = registry.get(AgentId(execution.session.id.value))
            ?: error("Calling agent is not in the live registry")
        check(caller.session === execution.session) { "Execution session identity changed" }
        checkLive(caller)
        return caller
    }
}

internal class SendMessageHandler(
    private val checks: SubagentControlChecks,
    private val registry: AgentRegistry,
) : ContextualToolHandler {
    private data class Arguments(val agentId: String, val message: String)

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        val result = try {
            require(request.name == SEND_MESSAGE_TOOL_NAME) { "Unexpected tool name" }
            val arguments = decode(request.arguments)
            val caller = checks.caller(execution)
            val targetId = AgentId(arguments.agentId)
            require(targetId != caller.id) { "An agent cannot message itself" }
            val target = registry.get(targetId) ?: error("Unknown live agent '${arguments.agentId}'")
            checks.checkLive(target)
            require(isDirectChild(caller, target) || isDirectParent(caller, target)) {
                "send_message only targets your direct child or your direct parent"
            }
            val message = createUserMessage(
                content = listOf(TextBlock(arguments.message)),
                source = PluginMessageSource(SEND_MESSAGE_TOOL_NAME),
            )
            // The receipt is the delivery acknowledgement; do not re-check the target
            // afterwards, because a concurrent dispose must not relabel accepted input.
            val receipt = when (target.status) {
                AgentStatus.RUNNING -> target.steer(message)
                AgentStatus.IDLE -> target.followup(message)
            }
            SendMessageResult(messageId = receipt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SendMessageResult(error = error.message ?: "Message delivery failed")
        }
        return ToolExecutionResult(
            content = listOf(TextBlock(Json.encodeToString(SendMessageResult.serializer(), result))),
            isError = result.error != null,
        )
    }

    private fun isDirectChild(caller: Agent, target: Agent): Boolean =
        target.session.header.origin == SessionOrigin.SUBAGENT &&
            target.session.header.parentSession == caller.session.id

    private fun isDirectParent(caller: Agent, target: Agent): Boolean =
        caller.session.header.origin == SessionOrigin.SUBAGENT &&
            caller.session.header.parentSession == target.session.id

    private fun decode(value: String): Arguments {
        val objectValue = Json.parseToJsonElement(value) as? JsonObject ?: error("Arguments must be an object")
        require(objectValue.keys == setOf("agent_id", "message")) {
            "Only agent_id and message are allowed"
        }
        fun string(name: String): String {
            val primitive = objectValue[name] as? JsonPrimitive ?: error("$name must be a string")
            require(primitive.isString && primitive.content.isNotBlank()) { "$name must be a nonblank string" }
            return primitive.content
        }
        return Arguments(string("agent_id"), string("message"))
    }
}

internal class InterruptAgentHandler(
    private val checks: SubagentControlChecks,
    private val registry: AgentRegistry,
    private val sessionApi: SessionApi,
) : ContextualToolHandler {
    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        val result = try {
            require(request.name == INTERRUPT_AGENT_TOOL_NAME) { "Unexpected tool name" }
            val agentId = decode(request.arguments)
            val caller = checks.caller(execution)
            require(agentId != caller.session.id.value) { "An agent cannot interrupt itself" }
            val target = registry.get(AgentId(agentId))
                ?: error("Unknown live agent '$agentId'")
            checks.checkLive(target)
            // Only a verified live descendant of the exact caller may be interrupted.
            val descendants = when (val read = sessionApi.descendants(caller.session.id)) {
                is SessionDescendantsResult.Available -> read.children
                is SessionDescendantsResult.Unavailable -> error("Live subagent listing is unavailable")
            }
            require(descendants.any { it.child == target.session.id }) {
                "interrupt_agent only targets a live agent created under you"
            }
            // keepInbox leaves parked follow-ups and this child's own descendants untouched.
            target.cancel(keepInbox = true)
            InterruptAgentResult(accepted = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            InterruptAgentResult(error = error.message ?: "Interrupt failed")
        }
        return ToolExecutionResult(
            content = listOf(TextBlock(Json.encodeToString(InterruptAgentResult.serializer(), result))),
            isError = result.error != null,
        )
    }

    private fun decode(value: String): String {
        val objectValue = Json.parseToJsonElement(value) as? JsonObject ?: error("Arguments must be an object")
        require(objectValue.keys == setOf("agent_id")) { "Only agent_id is allowed" }
        val primitive = objectValue["agent_id"] as? JsonPrimitive ?: error("agent_id must be a string")
        require(primitive.isString && primitive.content.isNotBlank()) { "agent_id must be a nonblank string" }
        return primitive.content
    }
}

internal class ListAgentsHandler(
    private val checks: SubagentControlChecks,
    private val registry: AgentRegistry,
    private val sessionApi: SessionApi,
) : ContextualToolHandler {
    private enum class Scope { CHILDREN, DESCENDANTS }

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        return try {
            require(request.name == LIST_AGENTS_TOOL_NAME) { "Unexpected tool name" }
            val scope = decode(request.arguments)
            val caller = checks.caller(execution)
            val descendants = when (val read = sessionApi.descendants(caller.session.id)) {
                is SessionDescendantsResult.Available -> read.children
                is SessionDescendantsResult.Unavailable -> error("Live subagent listing is unavailable")
            }
            val selected = when (scope) {
                Scope.CHILDREN -> descendants.filter { it.parentSession == caller.session.id }
                Scope.DESCENDANTS -> descendants
            }
            val entries = selected.map { descendant ->
                val agent = registry.get(AgentId(descendant.child.value))
                val depth = (descendant.verifiedAncestorPath.size - 1).toLong()
                ListAgentsEntry(
                    id = descendant.child.value,
                    label = agent?.let { labelOf(it) } ?: descendant.child.value,
                    status = if (descendant.status == DescendantStatus.RUNNING) "running" else "inactive",
                    parent = descendant.parentSession.value.takeIf { scope == Scope.DESCENDANTS },
                    depth = depth.takeIf { scope == Scope.DESCENDANTS },
                )
            }
            val encoded = Json.encodeToString(ListSerializer(ListAgentsEntry.serializer()), entries)
            ToolExecutionResult(content = listOf(TextBlock(encoded)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ToolExecutionResult(
                content = listOf(TextBlock(error.message ?: "Subagent listing failed")),
                isError = true,
            )
        }
    }

    /**
     * Session headers do not carry the delegation label yet. Use the first user-visible
     * task text so the model still gets a stable, recognizable label; fall back to the id.
     */
    private suspend fun labelOf(agent: Agent): String {
        val text = runCatching {
            agent.session.deriveMessages().asSequence()
                .filter { it.source == UserMessageSource }
                .flatMap { it.content.asSequence() }
                .filterIsInstance<TextBlock>()
                .firstOrNull()
                ?.text
        }.getOrNull()
        return text
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(80)
            ?.takeIf { it.isNotBlank() }
            ?: agent.id.value
    }

    private fun decode(value: String): Scope {
        val objectValue = Json.parseToJsonElement(value) as? JsonObject ?: error("Arguments must be an object")
        require(objectValue.keys.all { it == "scope" }) { "Only scope is allowed" }
        val primitive = objectValue["scope"] as? JsonPrimitive
        if (primitive == null) return Scope.CHILDREN
        require(primitive.isString) { "scope must be a string" }
        return when (primitive.content) {
            "children" -> Scope.CHILDREN
            "descendants" -> Scope.DESCENDANTS
            else -> error("scope must be 'children' or 'descendants'")
        }
    }
}

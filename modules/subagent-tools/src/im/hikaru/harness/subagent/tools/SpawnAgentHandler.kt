package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentHandle
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.SessionOrigin
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class SpawnAgentHandler(
    private val hostContext: Context,
    private val registry: AgentRegistry,
    private val store: SessionStore,
) : ContextualToolHandler, Disposable {
    private data class CallKey(val parent: Session, val call: CallId)
    private data class Arguments(val prompt: String, val provider: String, val model: String)

    private val mutex = Mutex()
    private val resultJson = Json { encodeDefaults = true }
    private var disposed = false
    private val owned = mutableSetOf<AgentHandle>()
    // A cancelled delivery can already have committed an Inbox insert. Keep its ID
    // under this call key so a repeated invocation cannot create a second child.
    private val calls = mutableMapOf<CallKey, SpawnAgentResult?>()

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        val arguments: Arguments
        val ancestry: List<Agent>
        try {
            require(request.name == SPAWN_AGENT_TOOL_NAME) { "Unexpected tool name" }
            arguments = decodeArguments(request.arguments)
            ancestry = captureAncestry(execution)
            require(request.scope == null || request.scope == execution.session.id.value) {
                "Tool scope does not match the trusted parent session"
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return result(SpawnAgentResult(SpawnAgentOutcome.REJECTED, error = error.message ?: "Invalid spawn request"))
        }

        val key = CallKey(execution.session, request.callId)
        mutex.withLock {
            if (disposed) return result(SpawnAgentResult(SpawnAgentOutcome.REJECTED, error = "Subagent tools disposed"))
            if (calls.containsKey(key)) {
                return result(calls[key] ?: SpawnAgentResult(
                    SpawnAgentOutcome.REJECTED, parentSessionId = execution.session.id.value,
                    error = "This spawn call is already in flight; do not retry with a new call ID",
                ))
            }
            calls[key] = null
        }

        var child: AgentHandle? = null
        var state = SpawnAgentResult(SpawnAgentOutcome.CREATE_FAILED, parentSessionId = execution.session.id.value)
        try {
            validateSnapshot(execution, ancestry)
            val cwd = execution.session.header.cwd ?: ancestry.first().options.cwd
            val created = registry.create(
                options = AgentOptions(cwd = cwd, provider = arguments.provider, model = arguments.model),
                sessionOptions = CreateSessionOptions(
                    cwd = cwd, parentSession = execution.session.id, origin = SessionOrigin.SUBAGENT,
                ),
            )
            child = created
            state = state.copy(childSessionId = created.agent.session.id.value)
            // Register before any cancellable operation after create returns.
            withContext(NonCancellable) { mutex.withLock { owned += created } }
            validateSnapshot(execution, ancestry)
            validateChild(created.agent, execution.session)
            currentCoroutineContext().ensureActive()
            val message = createUserMessage(listOf(TextBlock(arguments.prompt)))
            state = state.copy(
                inputMessageId = message.id.value, delivery = SpawnAgentDelivery.UNKNOWN,
                outcome = SpawnAgentOutcome.CREATED_DELIVERY_UNKNOWN,
            )
            try {
                val receipt = created.agent.followup(message)
                state = state.copy(
                    outcome = SpawnAgentOutcome.ACCEPTED,
                    receiptId = receipt, delivery = SpawnAgentDelivery.ACCEPTED,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A handler can throw after an Inbox commit. Do not claim unsent.
                state = state.copy(
                    outcome = SpawnAgentOutcome.CREATED_PROMPT_FAILED,
                    error = error.message ?: "Prompt delivery failed",
                )
            }
            validateSnapshot(execution, ancestry)
            validateChild(created.agent, execution.session)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                state = state.copy(error = if (state.delivery == SpawnAgentDelivery.NOT_ATTEMPTED) {
                    "Spawn cancelled before prompt delivery"
                } else {
                    "Spawn cancelled: delivery may already have been accepted"
                })
                val handle = child
                if (handle != null) {
                    val invalidParent = try {
                        validateSnapshot(execution, ancestry)
                        false
                    } catch (_: Exception) {
                        true
                    }
                    if (state.delivery == SpawnAgentDelivery.NOT_ATTEMPTED || invalidParent) {
                        state = rollback(handle, state)
                    }
                }
                mutex.withLock { calls[key] = state }
            }
            throw error
        } catch (error: Exception) {
            state = state.copy(error = error.message ?: "Spawn creation or lineage verification failed")
            val handle = child
            if (handle != null) state = rollback(handle, state)
        }
        withContext(NonCancellable) { mutex.withLock { calls[key] = state } }
        return result(state)
    }

    private suspend fun captureAncestry(execution: ToolExecutionContext): List<Agent> {
        checkContext(execution.context)
        val path = mutableListOf<Agent>()
        val seen = mutableSetOf<String>()
        var session: Session? = execution.session
        while (session != null) {
            check(seen.add(session.id.value)) { "Parent ancestry contains a cycle or self reference" }
            val agent = registry.get(AgentId(session.id.value)) ?: error("Parent ancestry is not in the live registry")
            check(agent.session === session) { "Parent session identity changed" }
            checkLive(agent)
            path += agent
            val header = session.header
            when (header.origin) {
                null -> Unit // Ordinary branches can delegate; their own branch edge is not SUBAGENT.
                SessionOrigin.SUBAGENT -> check(header.parentSession != null) { "SUBAGENT has no parent" }
                else -> error("Unsupported ancestry origin")
            }
            session = header.parentSession?.let { store.get(it) ?: error("Parent ancestor is missing") }
        }
        check(path.isNotEmpty()) { "No live parent" }
        check(path.first().context === execution.context) { "Execution context is not the live parent Agent context" }
        validateSnapshot(execution, path)
        return path
    }

    private suspend fun validateSnapshot(execution: ToolExecutionContext, path: List<Agent>) {
        mutex.withLock { check(!disposed) { "Subagent tools disposed during spawn" } }
        checkContext(hostContext)
        checkContext(execution.context)
        check(path.first().session === execution.session) { "Execution parent identity changed" }
        for ((index, agent) in path.withIndex()) {
            checkLive(agent)
            val expectedParent = path.getOrNull(index + 1)?.session?.id
            check(agent.session.header.parentSession == expectedParent) { "Parent ancestry changed" }
        }
    }

    private fun checkContext(context: Context) {
        check(!context.isDisposed) { "Execution context is closed" }
        // get() also rejects a disposing Context; isDisposing is runtime-internal.
        check(context.get(AgentKey) === registry) { "Execution uses another AgentRegistry" }
        check(context.get(SessionKey) === store) { "Execution uses another SessionStore" }
    }

    private suspend fun checkLive(agent: Agent) {
        checkContext(agent.context)
        check(agent.id.value == agent.session.id.value) { "Agent/session ID mismatch" }
        check(registry.get(agent.id) === agent) { "Live agent identity changed" }
        check(store.get(agent.session.id) === agent.session && agent.session.isLive()) { "Session is no longer live" }
    }

    private suspend fun validateChild(child: Agent, parent: Session) {
        checkLive(child)
        check(child.session !== parent && child.session.id != parent.id) { "Child aliases parent" }
        check(child.session.header.origin == SessionOrigin.SUBAGENT && child.session.header.parentSession == parent.id) {
            "Created child lineage does not match the trusted parent"
        }
    }

    private suspend fun rollback(handle: AgentHandle, state: SpawnAgentResult): SpawnAgentResult =
        withContext(NonCancellable) {
            try {
                // Never find by ID and dispose: this handle owns only this registration.
                handle.dispose()
                mutex.withLock { owned.remove(handle) }
                state.copy(outcome = SpawnAgentOutcome.ROLLED_BACK)
            } catch (error: Exception) {
                state.copy(outcome = SpawnAgentOutcome.ROLLBACK_FAILED, rollbackError = error.message ?: "Child cleanup failed")
            }
        }

    override suspend fun dispose(): Unit = withContext(NonCancellable) {
        val handles = mutex.withLock {
            disposed = true
            owned.toList().asReversed().also { owned.clear() }
        }
        var failure: Throwable? = null
        for (handle in handles) {
            try {
                handle.dispose()
            } catch (error: Exception) {
                if (failure == null) failure = error else if (error !== failure) failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        Unit
    }

    private fun decodeArguments(value: String): Arguments {
        val objectValue = Json.parseToJsonElement(value) as? JsonObject ?: error("Arguments must be an object")
        require(objectValue.keys == setOf("prompt", "provider", "model")) {
            "Only prompt, provider and model are allowed; parent identity and cwd cannot be supplied"
        }
        fun string(name: String): String {
            val primitive = objectValue[name] as? JsonPrimitive ?: error("$name must be a string")
            require(primitive.isString && primitive.content.isNotBlank()) { "$name must be a nonblank string" }
            return primitive.content
        }
        return Arguments(string("prompt"), string("provider"), string("model"))
    }

    private fun result(value: SpawnAgentResult): ToolExecutionResult = ToolExecutionResult(
        content = listOf(TextBlock(resultJson.encodeToString(SpawnAgentResult.serializer(), value))),
        isError = value.outcome != SpawnAgentOutcome.ACCEPTED,
    )
}

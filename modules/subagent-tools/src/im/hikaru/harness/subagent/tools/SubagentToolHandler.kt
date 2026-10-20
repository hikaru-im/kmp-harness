package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentHandle
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
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

internal class SubagentToolHandler(
    private val hostContext: Context,
    private val registry: AgentRegistry,
    private val store: SessionStore,
    private val settlement: SubagentSettlement,
    private val maxDepth: Long = DEFAULT_SUBAGENT_MAX_DEPTH,
    private val maxActiveSubagents: Int = DEFAULT_MAX_ACTIVE_SUBAGENTS,
) : ContextualToolHandler, Disposable {
    private data class CallKey(val parent: Session, val call: CallId)
    private data class Arguments(
        val description: String,
        val prompt: String,
        val cwd: String?,
        val provider: String?,
        val model: String?,
        val reasoningEffort: String?,
    )

    private val mutex = Mutex()
    private val resultJson = Json { encodeDefaults = true }
    private var disposed = false
    private val owned = mutableSetOf<AgentHandle>()
    // Lineage root of every child this handler created. Capacity is shared by all
    // subagents of one uninterrupted parent link, as in DSH's ActivationPool.
    private val lineageRoots = mutableMapOf<SessionId, SessionId>()
    // Spawns admitted but not yet registered in `owned`; they consume capacity too.
    private val pendingSpawns = mutableMapOf<SessionId, Int>()
    // A cancelled delivery can already have committed an Inbox insert. Keep its ID
    // under this call key so a repeated invocation cannot create a second child.
    private val calls = mutableMapOf<CallKey, SubagentResult?>()

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        val arguments: Arguments
        val ancestry: List<Agent>
        try {
            require(request.name == SUBAGENT_TOOL_NAME) { "Unexpected tool name" }
            arguments = decodeArguments(request.arguments)
            ancestry = captureAncestry(execution)
            require(request.scope == null || request.scope == execution.session.id.value) {
                "Tool scope does not match the trusted parent session"
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return result(SubagentResult(SubagentOutcome.REJECTED, error = error.message ?: "Invalid subagent request"))
        }

        val key = CallKey(execution.session, request.callId)
        // DSH resolves the child depth from the persisted parent header and caps
        // every new activation at the configured depth and lineage capacity.
        val lineageRoot = ancestry.last().session.id
        val childDepth = (execution.session.header.delegationDepth ?: 0L) + 1
        mutex.withLock {
            if (disposed) return result(SubagentResult(SubagentOutcome.REJECTED, error = "Subagent tool disposed"))
            if (calls.containsKey(key)) {
                return result(calls[key] ?: SubagentResult(
                    SubagentOutcome.REJECTED, parentSessionId = execution.session.id.value,
                    error = "This subagent call is already in flight; do not retry with a new call ID",
                ))
            }
            if (childDepth > maxDepth) {
                return result(SubagentResult(
                    SubagentOutcome.REJECTED, parentSessionId = execution.session.id.value,
                    error = "subagent depth $childDepth exceeds maxDepth $maxDepth",
                ))
            }
            if (activeChildrenLocked(lineageRoot) >= maxActiveSubagents) {
                return result(SubagentResult(
                    SubagentOutcome.REJECTED, parentSessionId = execution.session.id.value,
                    error = "subagent limit reached (active child limit: $maxActiveSubagents); " +
                        "wait for an existing child to finish or complete this work with the current agents",
                ))
            }
            calls[key] = null
            pendingSpawns[lineageRoot] = (pendingSpawns[lineageRoot] ?: 0) + 1
        }

        var child: AgentHandle? = null
        var state = SubagentResult(SubagentOutcome.CREATE_FAILED, parentSessionId = execution.session.id.value)
        try {
            validateSnapshot(execution, ancestry)
            val parentOptions = ancestry.first().options
            val cwd = arguments.cwd ?: execution.session.header.cwd ?: parentOptions.cwd
            val created = registry.create(
                options = AgentOptions(
                    cwd = cwd,
                    provider = arguments.provider ?: parentOptions.provider,
                    model = arguments.model ?: parentOptions.model,
                    reasoningEffort = arguments.reasoningEffort?.let(::ReasoningEffortId)
                        ?: parentOptions.reasoningEffort,
                    maxTokens = parentOptions.maxTokens,
                ),
                sessionOptions = CreateSessionOptions(
                    cwd = cwd, parentSession = execution.session.id, origin = SessionOrigin.SUBAGENT,
                    delegationDepth = childDepth,
                ),
            )
            child = created
            state = state.copy(childSessionId = created.agent.session.id.value)
            // Register before any cancellable operation after create returns.
            withContext(NonCancellable) {
                mutex.withLock {
                    owned += created
                    lineageRoots[created.agent.session.id] = lineageRoot
                }
            }
            validateSnapshot(execution, ancestry)
            validateChild(created.agent, execution.session)
            currentCoroutineContext().ensureActive()
            val message = createUserMessage(
                listOf(
                    TextBlock(arguments.prompt),
                    TextBlock(returnGuidance(execution.session.id.value)),
                ),
            )
            state = state.copy(
                inputMessageId = message.id.value, delivery = SubagentDelivery.UNKNOWN,
                outcome = SubagentOutcome.CREATED_DELIVERY_UNKNOWN,
            )
            try {
                val receipt = created.agent.followup(message)
                state = state.copy(
                    outcome = SubagentOutcome.ACCEPTED,
                    receiptId = receipt, delivery = SubagentDelivery.ACCEPTED,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A handler can throw after an Inbox commit. Do not claim unsent.
                state = state.copy(
                    outcome = SubagentOutcome.CREATED_PROMPT_FAILED,
                    error = error.message ?: "Prompt delivery failed",
                )
            }
            validateSnapshot(execution, ancestry)
            validateChild(created.agent, execution.session)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                state = state.copy(error = if (state.delivery == SubagentDelivery.NOT_ATTEMPTED) {
                    "Subagent creation cancelled before prompt delivery"
                } else {
                    "Subagent creation cancelled: delivery may already have been accepted"
                })
                val handle = child
                if (handle != null) {
                    val invalidParent = try {
                        validateSnapshot(execution, ancestry)
                        false
                    } catch (_: Exception) {
                        true
                    }
                    if (state.delivery == SubagentDelivery.NOT_ATTEMPTED || invalidParent) {
                        state = rollback(handle, state)
                    }
                }
                mutex.withLock {
                    calls[key] = state
                    releasePendingLocked(lineageRoot)
                }
            }
            throw error
        } catch (error: Exception) {
            state = state.copy(error = error.message ?: "Subagent creation or lineage verification failed")
            val handle = child
            if (handle != null) state = rollback(handle, state)
        }
        withContext(NonCancellable) {
            mutex.withLock {
                calls[key] = state
                releasePendingLocked(lineageRoot)
            }
        }
        if (state.outcome == SubagentOutcome.ACCEPTED) {
            child?.let { settlement.watch(ancestry.first(), it.agent) }
        }
        return result(state)
    }

    private suspend fun activeChildrenLocked(root: SessionId): Int {
        var active = pendingSpawns[root] ?: 0
        for (handle in owned) {
            if (lineageRoots[handle.agent.session.id] != root) continue
            if (isActive(handle.agent)) active++
        }
        return active
    }

    private suspend fun isActive(agent: Agent): Boolean {
        if (agent.status == AgentStatus.RUNNING) return true
        val inbox = agent.inbox.snapshot()
        return inbox.nextTurn.isNotEmpty() || inbox.nextStep.isNotEmpty()
    }

    private fun releasePendingLocked(root: SessionId) {
        val remaining = (pendingSpawns[root] ?: return) - 1
        if (remaining > 0) pendingSpawns[root] = remaining else pendingSpawns.remove(root)
    }

    /** DSH's `withContinuableReturnGuidance`: the parent id is part of the task. */
    private fun returnGuidance(parentId: String): String {
        val encoded = JsonPrimitive(parentId).toString()
        return "Your parent agent id is $encoded. Before you finish, send your result to that agent with " +
            "send_message({ agent_id: $encoded, message: \"<self-contained result>\" }). The parent shares " +
            "your workspace but does not automatically receive your transcript, tool output, or reasoning. Send " +
            "earlier messages as well when a finding changes what the parent should do next; sending a message " +
            "does not end your turn."
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
        mutex.withLock { check(!disposed) { "Subagent tool disposed during spawn" } }
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

    private suspend fun rollback(handle: AgentHandle, state: SubagentResult): SubagentResult =
        withContext(NonCancellable) {
            try {
                // Never find by ID and dispose: this handle owns only this registration.
                val sessionId = handle.agent.session.id
                handle.dispose()
                mutex.withLock {
                    owned.remove(handle)
                    lineageRoots.remove(sessionId)
                }
                state.copy(outcome = SubagentOutcome.ROLLED_BACK)
            } catch (error: Exception) {
                state.copy(outcome = SubagentOutcome.ROLLBACK_FAILED, rollbackError = error.message ?: "Child cleanup failed")
            }
        }

    override suspend fun dispose(): Unit = withContext(NonCancellable) {
        val handles = mutex.withLock {
            disposed = true
            owned.toList().asReversed().also {
                owned.clear()
                lineageRoots.clear()
                pendingSpawns.clear()
            }
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
        require(
            objectValue.keys.all { it in setOf("description", "prompt", "cwd", "provider", "model", "reasoning_effort") },
        ) {
            "Only description, prompt, cwd, provider, model and reasoning_effort are allowed; " +
                "parent identity cannot be supplied (got ${objectValue.keys})"
        }
        fun string(name: String): String {
            val primitive = objectValue[name] as? JsonPrimitive ?: error("$name must be a string")
            require(primitive.isString && primitive.content.isNotBlank()) { "$name must be a nonblank string" }
            return primitive.content
        }
        fun optionalString(name: String): String? =
            if (name in objectValue) string(name) else null
        val provider = optionalString("provider")
        val model = optionalString("model")
        require((provider == null) == (model == null)) {
            "provider and model must be supplied together"
        }
        // DSH uses `description` as the child label. Session headers have no label field yet,
        // so list_agents derives its label from the first task message instead.
        return Arguments(
            description = string("description"),
            prompt = string("prompt"),
            cwd = optionalString("cwd"),
            provider = provider,
            model = model,
            reasoningEffort = optionalString("reasoning_effort"),
        )
    }

    private fun result(value: SubagentResult): ToolExecutionResult = ToolExecutionResult(
        content = listOf(TextBlock(resultJson.encodeToString(SubagentResult.serializer(), value))),
        isError = value.outcome != SubagentOutcome.ACCEPTED,
    )
}

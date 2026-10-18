package im.hikaru.harness.client.conversation

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.app.SessionStreamDriver
import im.hikaru.harness.client.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Authenticated server/tenant/member identity is part of scope. null identity denotes the local Host. */
public data class ConversationHostScope(
    val hostId: HostId,
    val generation: Long,
    val identity: MemberIdentity?,
) {
    init { require(generation >= 0) }
    internal fun sameHost(other: ConversationHostScope): Boolean = hostId == other.hostId && identity == other.identity
}

/** LocalIdle is only valid for a connection whose awaitIdle actually waits for its agent. */
public enum class CompletionCapability { LocalIdle, DeliveryAcknowledgedOnly }
public enum class ConversationDataPhase { Unbound, Loading, Ready, Empty, Disconnected, Failed }
public enum class ConversationAction { Idle, Creating, Associating, Sending, WaitingForLocalIdle, RunningUnknown }
public enum class ConversationRunState { Unknown, Running, Idle }
public enum class ConversationErrorKind { List, History, Create, Association, Prompt, Completion, Cancel, Stream }
public enum class ConversationRetry { Refresh, History, SubmitExplicitly, Cancel }

public data class ConversationError(
    val kind: ConversationErrorKind,
    val message: String,
    val retry: ConversationRetry?,
)

public data class ConversationState(
    val host: ConversationHostScope? = null,
    val connected: Boolean = false,
    val listPhase: ConversationDataPhase = ConversationDataPhase.Disconnected,
    val historyPhase: ConversationDataPhase = ConversationDataPhase.Unbound,
    val sessions: List<SessionSummary> = emptyList(),
    val selectedSessionId: SessionId? = null,
    val history: List<Message> = emptyList(),
    val draft: String = "",
    val draftRevision: Long = 0,
    val action: ConversationAction = ConversationAction.Idle,
    val runState: ConversationRunState = ConversationRunState.Unknown,
    val cancelling: Boolean = false,
    val completionCapability: CompletionCapability = CompletionCapability.DeliveryAcknowledgedOnly,
    val lastAcceptedReceipt: SessionPromptReceipt? = null,
    val errors: Map<ConversationErrorKind, ConversationError> = emptyMap(),
) {
    public val canSubmit: Boolean get() = connected && host != null && draft.isNotBlank() &&
        action == ConversationAction.Idle && !cancelling
    // Stop may be available for a selected real session whose run status is unknown.
    public val canCancel: Boolean get() = connected && selectedSessionId != null && !cancelling &&
        action != ConversationAction.Creating && action != ConversationAction.Sending &&
        (action == ConversationAction.Associating || runState != ConversationRunState.Idle)
    public val lastAcceptedMessageId: String? get() = lastAcceptedReceipt?.messageId
}

/** Inject operations for delayed/failure tests. Production uses only the existing Session API. */
public interface ConversationOperations {
    public suspend fun list(connection: Connection): List<SessionSummary>
    public suspend fun create(connection: Connection, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary
    public suspend fun history(connection: Connection, id: SessionId): List<Message>
    public suspend fun prompt(connection: Connection, id: SessionId, message: Message): SessionPrompt
    public suspend fun cancel(connection: Connection, id: SessionId)
}

public object RealConversationOperations : ConversationOperations {
    override suspend fun list(connection: Connection): List<SessionSummary> = connection.session.list()
    override suspend fun create(connection: Connection, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
        connection.session.create(options = options, agentOptions = agentOptions)
    override suspend fun history(connection: Connection, id: SessionId): List<Message> = connection.session.history(id)
    override suspend fun prompt(connection: Connection, id: SessionId, message: Message): SessionPrompt = connection.session.prompt(id, message)
    override suspend fun cancel(connection: Connection, id: SessionId) = connection.session.cancel(id)
}

/** Flows and callbacks belong to the captured connection, never an owner.currentConnection getter. */
public data class ConversationStreams(
    val events: Flow<RelayEvent>,
    val recoveries: Flow<HistoryRecovery>,
    val subscribe: suspend (SessionId) -> Unit,
    val unsubscribe: suspend (SessionId) -> Unit,
) {
    public companion object {
        public fun remote(connection: RemoteConnection): ConversationStreams = ConversationStreams(
            connection.events, connection.historyRecoveries,
            { connection.subscribe(StreamId(it.value)) },
            { connection.unsubscribe(StreamId(it.value)) },
        )
    }
}

/**
 * Create once at app level. Home and Thread observe the same immutable StateFlow.
 * Commands AND the injected coroutine scope must be confined to one dispatcher (normally UI/Main).
 * Busy/request state is acquired synchronously before any service suspension.
 * bind/select release client work without cancelling backend execution; cancel explicitly stops it.
 * close cancels owned work; subscription release is bounded by cleanupTimeoutMillis.
 */
public class ConversationController(
    scope: CoroutineScope,
    private val operations: ConversationOperations = RealConversationOperations,
    private val cleanupTimeoutMillis: Long = 2_000,
) : AutoCloseable {
    init { require(cleanupTimeoutMillis > 0) }
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + owner)
    private val mutableState = MutableStateFlow(ConversationState())
    public val state: StateFlow<ConversationState> = mutableState.asStateFlow()
    // Scoped to the bound Host/member; null owns the Home draft before session creation.
    private val drafts = mutableMapOf<SessionId?, String>()
    private var connection: Connection? = null
    private var streams: ConversationStreams? = null
    private var epoch = 0L
    private var selection = 0L
    private var listRequest = 0L
    private var historyRequest = 0L
    private var sendRequest = 0L
    private var cancelRequest = 0L
    private var listJob: Job? = null
    private var historyJob: Job? = null
    private var sendJob: Job? = null
    private var cancelJob: Job? = null
    private var streamJob: Job? = null
    private var closed = false

    /**
     * null scope is logout (clear everything); null connection + retained scope is temporary disconnect.
     * Capability defaults to delivery-only. Repeated identical binds are no-ops.
     * A Host/account change clears selection; subsequently select the new Host's route id explicitly.
     */
    public fun bind(
        connection: Connection?,
        host: ConversationHostScope?,
        completionCapability: CompletionCapability = CompletionCapability.DeliveryAcknowledgedOnly,
        streams: ConversationStreams? = (connection as? RemoteConnection)?.let { ConversationStreams.remote(it) },
    ) {
        check(!closed)
        require(connection == null || host != null) { "A connection needs an explicit Host scope" }
        require(connection !is RemoteConnection || host?.identity != null) { "Relay needs authenticated identity" }
        require(connection !is RemoteConnection || completionCapability == CompletionCapability.DeliveryAcknowledgedOnly) {
            "Relay awaitIdle is delivery-only"
        }
        if (this.connection === connection && state.value.host == host && state.value.completionCapability == completionCapability) return
        val old = state.value
        val sameHost = host != null && old.host?.sameHost(host) == true
        epoch++
        selection++
        invalidateRequests()
        if (!sameHost) drafts.clear()
        this.connection = connection
        this.streams = streams
        mutableState.value = if (host == null) ConversationState() else ConversationState(
            host = host, connected = connection != null,
            selectedSessionId = if (sameHost) old.selectedSessionId else null,
            draft = if (sameHost) old.draft else "", draftRevision = old.draftRevision + 1,
            completionCapability = completionCapability,
        )
        replaceStream()
        if (connection != null) refresh()
    }

    /** Route selection is authoritative, including ids absent from the live list. */
    public fun select(id: SessionId?) {
        check(!closed)
        val old = state.value
        if (old.selectedSessionId == id) return
        drafts[old.selectedSessionId] = old.draft
        selection++
        val wasLoadingList = state.value.listPhase == ConversationDataPhase.Loading
        invalidateRequests()
        mutableState.value = state.value.copy(
            selectedSessionId = id, history = emptyList(), historyPhase = ConversationDataPhase.Unbound,
            draft = drafts[id].orEmpty(), draftRevision = old.draftRevision + 1,
            action = ConversationAction.Idle, runState = ConversationRunState.Unknown, cancelling = false,
            lastAcceptedReceipt = null, errors = emptyMap(),
        )
        replaceStream()
        if (wasLoadingList) refreshSessions()
        refreshHistory()
    }

    public fun updateDraft(text: String) {
        check(!closed)
        drafts[state.value.selectedSessionId] = text
        mutableState.value = state.value.copy(draft = text, draftRevision = state.value.draftRevision + 1)
    }

    /** Data refresh never cancels or retries an execution command. */
    public fun refresh() { refreshSessions(); refreshHistory() }

    /**
     * An external archive/unarchive changed which chats are live: an archived chat stops its Agent
     * and a restored one is attached again. Re-read the live list so a chat that left it disappears
     * and one that returned appears, without the user pressing refresh. A [retired] chat that is
     * still the current selection is deselected, so no surface keeps presenting a stopped session.
     * Like [refresh], this never cancels or retries an execution command.
     */
    public fun invalidateSessions(retired: SessionId? = null) {
        check(!closed)
        if (retired != null && state.value.selectedSessionId == retired) select(null)
        refreshSessions()
    }

    public fun refreshSessions(): Job? {
        val fence = capture() ?: return null
        val request = ++listRequest
        listJob?.cancel()
        mutableState.value = state.value.copy(listPhase = ConversationDataPhase.Loading, errors = without(ConversationErrorKind.List))
        return start({ listJob = it }) {
            try {
                val result = operations.list(fence.connection).toList()
                currentCoroutineContext().ensureActive()
                if (valid(fence) && request == listRequest) mutableState.value = state.value.copy(
                    sessions = result, listPhase = if (result.isEmpty()) ConversationDataPhase.Empty else ConversationDataPhase.Ready,
                    errors = without(ConversationErrorKind.List),
                )
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (valid(fence) && request == listRequest) {
                    mutableState.value = state.value.copy(listPhase = ConversationDataPhase.Failed)
                    fail(ConversationErrorKind.List, ConversationRetry.Refresh)
                }
            } finally {
                if (valid(fence) && request == listRequest && state.value.listPhase == ConversationDataPhase.Loading)
                    mutableState.value = state.value.copy(listPhase = ConversationDataPhase.Unbound)
            }
        }
    }

    public fun refreshHistory(): Job? {
        val fence = capture() ?: return null
        val id = fence.selected ?: return null
        val request = ++historyRequest
        historyJob?.cancel()
        mutableState.value = state.value.copy(historyPhase = ConversationDataPhase.Loading, errors = without(ConversationErrorKind.History))
        return start({ historyJob = it }) {
            try {
                val result = operations.history(fence.connection, id).map(::snapshotMessage)
                currentCoroutineContext().ensureActive()
                if (valid(fence) && request == historyRequest) mutableState.value = state.value.copy(
                    history = result, historyPhase = if (result.isEmpty()) ConversationDataPhase.Empty else ConversationDataPhase.Ready,
                    errors = without(ConversationErrorKind.History),
                )
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (valid(fence) && request == historyRequest) {
                    mutableState.value = state.value.copy(historyPhase = ConversationDataPhase.Failed)
                    fail(ConversationErrorKind.History, ConversationRetry.History)
                }
            } finally {
                if (valid(fence) && request == historyRequest && state.value.historyPhase == ConversationDataPhase.Loading)
                    mutableState.value = state.value.copy(historyPhase = ConversationDataPhase.Unbound)
            }
        }
    }

    /** Retry is explicit through this entry. Ambiguous transport errors are never automatically resent. */
    public fun submit(
        options: CreateSessionOptions = CreateSessionOptions(),
        agentOptions: AgentOptions = AgentOptions(),
        prepareCreation: (suspend () -> CreateSessionOptions?)? = null,
        onSessionCreated: (suspend (SessionSummary) -> Boolean)? = null,
    ): Job? {
        if (!state.value.canSubmit) return null
        val initial = capture() ?: return null
        val request = ++sendRequest
        val draft = state.value.draft
        val revision = state.value.draftRevision
        val capability = state.value.completionCapability
        mutableState.value = state.value.copy(
            action = if (initial.selected == null) ConversationAction.Creating else ConversationAction.Sending,
            errors = without(ConversationErrorKind.Create, ConversationErrorKind.Association, ConversationErrorKind.Prompt, ConversationErrorKind.Completion),
        )
        return start({ sendJob = it }) {
            var fence = initial
            var phase = if (initial.selected == null) ConversationErrorKind.Create else ConversationErrorKind.Prompt
            try {
                if (fence.selected == null) {
                    val prepared = if (prepareCreation == null) options else prepareCreation()
                    currentCoroutineContext().ensureActive()
                    if (!valid(fence) || request != sendRequest || prepared == null) return@start
                    val created = operations.create(fence.connection, prepared, agentOptions)
                    currentCoroutineContext().ensureActive()
                    if (!valid(fence) || request != sendRequest) return@start
                    // Rebase only this send to the newly created real selection.
                    selection++
                    listRequest++
                    historyRequest++
                    listJob?.cancel()
                    historyJob?.cancel()
                    // Transfer the latest Home draft without changing its edit revision.
                    drafts.remove(null)
                    drafts[created.id] = state.value.draft
                    mutableState.value = state.value.copy(
                        selectedSessionId = created.id, sessions = (state.value.sessions + created).distinctBy { it.id },
                        listPhase = ConversationDataPhase.Ready, history = emptyList(), historyPhase = ConversationDataPhase.Unbound,
                        lastAcceptedReceipt = null,
                        action = if (onSessionCreated == null) ConversationAction.Sending else ConversationAction.Associating,
                        runState = ConversationRunState.Unknown,
                    )
                    fence = capture() ?: return@start
                    replaceStream()
                    phase = ConversationErrorKind.Association
                    if (onSessionCreated != null && !onSessionCreated(created)) {
                        currentCoroutineContext().ensureActive()
                        if (valid(fence) && request == sendRequest) fail(ConversationErrorKind.Association, null)
                        return@start
                    }
                    currentCoroutineContext().ensureActive()
                    if (!valid(fence) || request != sendRequest) return@start
                    mutableState.value = state.value.copy(action = ConversationAction.Sending)
                    phase = ConversationErrorKind.Prompt
                }
                val id = requireNotNull(fence.selected)
                val prompt = operations.prompt(fence.connection, id, userMessage(draft))
                currentCoroutineContext().ensureActive()
                if (!valid(fence) || request != sendRequest) return@start
                val current = state.value
                val clear = current.draft == draft && current.draftRevision == revision
                drafts[id] = if (clear) "" else current.draft
                mutableState.value = current.copy(
                    draft = if (clear) "" else current.draft,
                    draftRevision = if (clear) revision + 1 else current.draftRevision,
                    lastAcceptedReceipt = prompt.receipt,
                    action = if (capability == CompletionCapability.LocalIdle) ConversationAction.WaitingForLocalIdle else ConversationAction.RunningUnknown,
                    runState = if (capability == CompletionCapability.LocalIdle) ConversationRunState.Running else ConversationRunState.Unknown,
                )
                refreshHistory()
                if (capability == CompletionCapability.LocalIdle) {
                    phase = ConversationErrorKind.Completion
                    prompt.awaitIdle()
                    val failure = prompt.failure()
                    currentCoroutineContext().ensureActive()
                    if (!valid(fence) || request != sendRequest) return@start
                    mutableState.value = state.value.copy(action = ConversationAction.Idle, runState = ConversationRunState.Idle)
                    if (failure != null) {
                        // Only stable codes reach the UI, never raw Provider messages or request payloads.
                        val code = failure.code.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,96}")) } ?: "LLM_REQUEST_FAILED"
                        val error = ConversationError(ConversationErrorKind.Completion,
                            "模型执行失败（$code）。", ConversationRetry.History)
                        mutableState.value = state.value.copy(errors = state.value.errors + (ConversationErrorKind.Completion to error))
                    }
                    refreshHistory()
                }
                // Delivery-only deliberately does not call awaitIdle: Relay returns Unit immediately.
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (valid(fence) && request == sendRequest) {
                    mutableState.value = state.value.copy(action = ConversationAction.Idle, runState = ConversationRunState.Unknown)
                    fail(phase, when (phase) {
                        ConversationErrorKind.Completion -> ConversationRetry.History
                        ConversationErrorKind.Association -> null
                        else -> ConversationRetry.SubmitExplicitly
                    })
                }
            } finally {
                if (valid(fence) && request == sendRequest && state.value.action in listOf(
                        ConversationAction.Creating, ConversationAction.Associating, ConversationAction.Sending, ConversationAction.WaitingForLocalIdle)) {
                    mutableState.value = state.value.copy(action = ConversationAction.Idle, runState = ConversationRunState.Unknown)
                }
            }
        }
    }

    public fun cancel(): Job? {
        if (!state.value.canCancel) return null
        val fence = capture() ?: return null
        val id = fence.selected ?: return null
        val request = ++cancelRequest
        val associating = state.value.action == ConversationAction.Associating
        val associationJob = if (associating) sendJob else null
        if (associating) {
            sendRequest++
            associationJob?.cancel()
        }
        mutableState.value = state.value.copy(cancelling = true, errors = without(ConversationErrorKind.Cancel))
        return start({ cancelJob = it }) {
            try {
                if (associating) {
                    // No prompt was sent: stop the local association and wait for its cleanup.
                    associationJob?.join()
                    currentCoroutineContext().ensureActive()
                    if (valid(fence) && request == cancelRequest)
                        mutableState.value = state.value.copy(action = ConversationAction.Idle, runState = ConversationRunState.Unknown)
                    return@start
                }
                operations.cancel(fence.connection, id)
                currentCoroutineContext().ensureActive()
                if (!valid(fence) || request != cancelRequest) return@start
                sendRequest++
                sendJob?.cancel()
                // Stop acknowledgement also does not prove remote idle.
                mutableState.value = state.value.copy(action = ConversationAction.Idle, runState = ConversationRunState.Unknown)
                refreshHistory()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (valid(fence) && request == cancelRequest) fail(ConversationErrorKind.Cancel, ConversationRetry.Cancel)
            } finally {
                if (valid(fence) && request == cancelRequest) mutableState.value = state.value.copy(cancelling = false)
            }
        }
    }

    /** Clears private data immediately; subscription finalizers run for at most cleanupTimeoutMillis. */
    override fun close() {
        if (closed) return
        closed = true
        epoch++
        invalidateRequests()
        streamJob?.cancel()
        owner.cancel()
        connection = null
        streams = null
        drafts.clear()
        mutableState.value = ConversationState()
    }

    private fun replaceStream() {
        val previous = streamJob
        previous?.cancel()
        val fence = capture()
        val source = streams
        val id = fence?.selected
        streamJob = if (fence == null || source == null || id == null) null else work.launch {
            // Avoid an old unsubscribe racing a new subscribe for the same connection/session.
            previous?.join()
            if (!valid(fence)) return@launch
            try {
                SessionStreamDriver(
                    events = source.events.filter { it.hostId == fence.host.hostId },
                    recoveries = source.recoveries,
                    subscribe = source.subscribe,
                    unsubscribe = { selected -> withTimeout(cleanupTimeoutMillis) { source.unsubscribe(selected) } },
                    refresh = { if (valid(fence)) refreshHistory() },
                ).run(id)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (valid(fence)) fail(ConversationErrorKind.Stream, ConversationRetry.History)
            }
        }
    }

    private fun start(register: (Job) -> Unit, block: suspend () -> Unit): Job {
        val job = work.launch(start = CoroutineStart.LAZY) { block() }
        register(job)
        job.start()
        return job
    }

    private fun invalidateRequests() {
        listRequest++; historyRequest++; sendRequest++; cancelRequest++
        listJob?.cancel(); historyJob?.cancel(); sendJob?.cancel(); cancelJob?.cancel()
    }

    private fun capture(): Fence? {
        if (closed || !owner.isActive) return null
        return Fence(connection ?: return null, state.value.host ?: return null, epoch, selection, state.value.selectedSessionId)
    }

    private fun valid(fence: Fence): Boolean = !closed && connection === fence.connection &&
        state.value.host == fence.host && epoch == fence.epoch && selection == fence.selection && state.value.selectedSessionId == fence.selected

    private fun without(vararg kinds: ConversationErrorKind): Map<ConversationErrorKind, ConversationError> = state.value.errors - kinds.toSet()
    private fun fail(kind: ConversationErrorKind, retry: ConversationRetry?) {
        val message = when (kind) {
            ConversationErrorKind.List -> "会话列表不可用。"
            ConversationErrorKind.History -> "会话历史不可用，请刷新。"
            ConversationErrorKind.Create -> "创建结果未能确认。请确认后再重试。"
            ConversationErrorKind.Association -> "会话已创建，但项目关联未能确认；消息尚未发送。"
            ConversationErrorKind.Prompt -> "发送结果未能确认。请刷新会话并确认后再重试。"
            ConversationErrorKind.Completion -> "消息已接收，但完成状态未能确认。"
            ConversationErrorKind.Cancel -> "停止结果未能确认，请重试。"
            ConversationErrorKind.Stream -> "实时更新不可用，请刷新会话。"
        }
        mutableState.value = state.value.copy(errors = state.value.errors + (kind to ConversationError(kind, message, retry)))
    }

    private data class Fence(val connection: Connection, val host: ConversationHostScope, val epoch: Long, val selection: Long, val selected: SessionId?)
}

private fun snapshotMessage(message: Message): Message = message.copy(content = message.content.map(::snapshotContent))
private fun snapshotContent(content: MessageContent): MessageContent = when (content) {
    is ToolResultContent -> content.copy(content = content.content.map(::snapshotContent))
    else -> content
}

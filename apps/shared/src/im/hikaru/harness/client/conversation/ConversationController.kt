package im.hikaru.harness.client.conversation

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.app.SessionStreamDriver
import im.hikaru.harness.client.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public sealed interface ConversationCommand {
    public data class Rename(val title: String) : ConversationCommand
    public data object TogglePin : ConversationCommand
    public data object MarkUnread : ConversationCommand
    public data object MarkRead : ConversationCommand
    public data object ToggleArchive : ConversationCommand
    public data object Delete : ConversationCommand
}

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
public enum class ConversationErrorKind { List, History, Create, Association, Prompt, Completion, Cancel, Fork, Stream }
public enum class ConversationRetry { Refresh, History, SubmitExplicitly, Cancel }

/**
 * Editing a sent user message forks the conversation before it and resends the edited text (DSH
 * `fork({ sessionId, atSeq })`). The original conversation is never rewritten.
 */
public data class ConversationEdit(
    val messageId: String,
    val text: String,
    val previousDraft: String,
)

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
    val historyReadSeq: Long = -1,
    val modelConfiguration: AgentOptions? = null,
    val draft: String = "",
    val draftRevision: Long = 0,
    val action: ConversationAction = ConversationAction.Idle,
    val runState: ConversationRunState = ConversationRunState.Unknown,
    val cancelling: Boolean = false,
    val completionCapability: CompletionCapability = CompletionCapability.DeliveryAcknowledgedOnly,
    val lastAcceptedReceipt: SessionPromptReceipt? = null,
    val errors: Map<ConversationErrorKind, ConversationError> = emptyMap(),
    val managingSessionId: SessionId? = null,
    val managementError: String? = null,
    val editing: ConversationEdit? = null,
) {
    public val canSubmit: Boolean get() = connected && host != null && draft.isNotBlank() &&
        action == ConversationAction.Idle && !cancelling && sessions.firstOrNull { it.id == selectedSessionId }?.archived != true
    // Stop follows the Host's authoritative `running`, never a guess: an unknown status offers nothing.
    public val canCancel: Boolean get() = connected && selectedSessionId != null && !cancelling &&
        sessions.firstOrNull { it.id == selectedSessionId }?.archived != true &&
        action != ConversationAction.Creating && action != ConversationAction.Sending &&
        (action == ConversationAction.Associating || runState == ConversationRunState.Running)
    // Editing a settled message forks a new conversation, so it needs an idle, settled session.
    public val canEdit: Boolean get() = connected && selectedSessionId != null && !cancelling &&
        action == ConversationAction.Idle && runState == ConversationRunState.Idle &&
        sessions.firstOrNull { it.id == selectedSessionId }?.archived != true
    public val lastAcceptedMessageId: String? get() = lastAcceptedReceipt?.messageId
}

/** Inject operations for delayed/failure tests. Production uses only the existing Session API. */
public interface ConversationOperations {
    public suspend fun delete(connection: Connection, id: SessionId, revision: Long) = connection.session.deleteConversation(id, revision)
    public suspend fun update(connection: Connection, update: ConversationUpdate): SessionSummary = connection.session.updateConversation(update)
    public suspend fun archive(connection: Connection, id: SessionId, archived: Boolean) = connection.session.archiveConversation(id, archived)
    public suspend fun list(connection: Connection): List<SessionSummary>
    public suspend fun create(connection: Connection, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary
    public suspend fun history(connection: Connection, id: SessionId): List<Message>
    public suspend fun prompt(connection: Connection, id: SessionId, message: Message): SessionPrompt
    public suspend fun prompt(connection: Connection, id: SessionId, message: Message, options: AgentOptions): SessionPrompt =
        prompt(connection, id, message)
    /** DSH Stop keeps the queued inbox; only the running turn is aborted. */
    public suspend fun cancel(connection: Connection, id: SessionId, keepInbox: Boolean = true) =
        connection.session.cancel(id, keepInbox = keepInbox)
    public suspend fun fork(connection: Connection, parent: SessionId, beforeMessageId: String): SessionSummary =
        connection.session.fork(parent, beforeMessageId)
}

public object RealConversationOperations : ConversationOperations {
    override suspend fun list(connection: Connection): List<SessionSummary> = connection.session.list()
    override suspend fun create(connection: Connection, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
        connection.session.create(options = options, agentOptions = agentOptions)
    override suspend fun history(connection: Connection, id: SessionId): List<Message> = connection.session.history(id)
    override suspend fun prompt(connection: Connection, id: SessionId, message: Message): SessionPrompt = connection.session.prompt(id, message)
    override suspend fun prompt(connection: Connection, id: SessionId, message: Message, options: AgentOptions): SessionPrompt =
        if (options.provider == null || options.model == null) connection.session.prompt(id, message)
        else connection.session.prompt(id, message, options)
    override suspend fun cancel(connection: Connection, id: SessionId, keepInbox: Boolean) = connection.session.cancel(id, keepInbox = keepInbox)
    override suspend fun fork(connection: Connection, parent: SessionId, beforeMessageId: String): SessionSummary =
        connection.session.fork(parent, beforeMessageId)
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
    /** Streamed assistant text is published at most once per frame interval instead of per token. */
    private val liveFrameMillis: Long = 33,
) : AutoCloseable {
    init {
        require(cleanupTimeoutMillis > 0)
        require(liveFrameMillis >= 0)
    }
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + owner)
    private val mutableState = MutableStateFlow(ConversationState())
    public val state: StateFlow<ConversationState> = mutableState.asStateFlow()
    // Streaming text lives beside `state`, not inside it: only the transcript collects this flow, so a
    // token never recomposes the shell or the session list.
    private val mutableLive = MutableStateFlow(LiveTranscript())
    public val live: StateFlow<LiveTranscript> = mutableLive.asStateFlow()
    private val liveFold = LiveAssistantFold()
    private var livePublishJob: Job? = null
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
    private val managementMutex = Mutex()
    private var readJob: Job? = null
    private var readTarget: Pair<SessionId, Long>? = null
    private var readGeneration = 0L

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
        readJob?.cancel()
        readTarget = null; readGeneration++
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
        // A read acknowledgement may already be committed remotely. Keep its returned revision
        // even when navigation moves elsewhere, so the next metadata command is not stale.
        readTarget = null; readGeneration++
        selection++
        val wasLoadingList = state.value.listPhase == ConversationDataPhase.Loading
        invalidateRequests()
        mutableState.value = state.value.copy(
            selectedSessionId = id, history = emptyList(), modelConfiguration = null, historyReadSeq = -1, historyPhase = ConversationDataPhase.Unbound,
            draft = drafts[id].orEmpty(), draftRevision = old.draftRevision + 1,
            action = ConversationAction.Idle, runState = state.value.sessions
                .firstOrNull { it.id == id }?.let { if (it.running) ConversationRunState.Running else ConversationRunState.Idle }
                ?: ConversationRunState.Unknown,
            cancelling = false, lastAcceptedReceipt = null, errors = emptyMap(), editing = null,
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

    /**
     * Loads a settled user message into the composer. Submitting forks the conversation before that
     * message and sends the edited text into the child; the original conversation stays untouched.
     */
    public fun beginEdit(messageId: String) {
        check(!closed)
        val current = state.value
        if (!current.canEdit) return
        val message = current.history.firstOrNull { it.id == messageId } ?: return
        if (message.role != MessageRole.USER || message.source != UserMessageSource) return
        val text = message.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        if (text.isBlank()) return
        val session = current.selectedSessionId
        drafts[session] = text
        mutableState.value = current.copy(
            draft = text, draftRevision = current.draftRevision + 1,
            editing = ConversationEdit(messageId, text, current.draft),
            errors = current.errors - ConversationErrorKind.Fork - ConversationErrorKind.Prompt,
        )
    }

    /** Leaves the edit without sending and restores the draft the composer held before it. */
    public fun cancelEdit() {
        check(!closed)
        val editing = state.value.editing ?: return
        drafts[state.value.selectedSessionId] = editing.previousDraft
        mutableState.value = state.value.copy(draft = editing.previousDraft,
            draftRevision = state.value.draftRevision + 1, editing = null)
    }

    /** A management command never sends a prompt and is never automatically retried. */
    public fun manage(id: SessionId, command: ConversationCommand): Deferred<Boolean>? {
        if (!state.value.connected || state.value.managingSessionId != null) return null
        if (state.value.sessions.none { it.id == id }) return null
        val source = connection ?: return null
        val ownerEpoch = epoch
        if (command == ConversationCommand.MarkUnread) { readTarget = null; readGeneration++ }
        mutableState.value = state.value.copy(managingSessionId = id, managementError = null)
        return work.async {
            fun current(): Boolean = !closed && epoch == ownerEpoch && connection === source
            try {
                managementMutex.withLock {
                    if (!current()) return@async false
                    val summary = state.value.sessions.firstOrNull { it.id == id } ?: return@async false
                    when (command) {
                        ConversationCommand.Delete -> {
                            operations.delete(source, id, summary.revision)
                            if (current()) {
                                removeSummary(id)
                                if (state.value.selectedSessionId == id) { updateDraft(""); select(null) }
                                drafts.remove(id)
                            }
                        }
                        ConversationCommand.ToggleArchive -> {
                            operations.archive(source, id, !summary.archived)
                            if (current()) {
                                replaceSummary(summary.copy(archived = !summary.archived, live = false))
                            }
                        }
                        is ConversationCommand.Rename, ConversationCommand.TogglePin, ConversationCommand.MarkUnread,
                        ConversationCommand.MarkRead -> {
                            val updated = operations.update(source, ConversationUpdate(id, summary.revision,
                                if (command is ConversationCommand.Rename) command.title.trim().also { require(it.isNotEmpty()) } else summary.customTitle,
                                if (command == ConversationCommand.TogglePin) !summary.pinned else summary.pinned,
                                when (command) {
                                    ConversationCommand.MarkUnread -> -1
                                    // A chat can only be read through the newest event the list knows.
                                    ConversationCommand.MarkRead -> maxOf(summary.lastReadSeq, summary.lastEventSeq)
                                    else -> summary.lastReadSeq
                                }))
                            currentCoroutineContext().ensureActive()
                            if (current()) replaceSummary(updated)
                        }
                    }
                }
                current()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (current()) {
                    mutableState.value = state.value.copy(managementError = when (command) {
                        is ConversationCommand.Rename -> null // The editor keeps the draft and displays its own failure.
                        ConversationCommand.Delete -> "删除未能确认，列表已刷新。如果会话正在执行，请先停止后重试。"
                        else -> "会话操作未能确认，请重试。"
                    })
                    refreshSessions()
                }
                false
            } finally {
                if (current()) mutableState.value = state.value.copy(managingSessionId = null)
            }
        }
    }

    /** Read acknowledgements do not occupy the user-command busy state or retry execution. */
    public fun markRead(id: SessionId, throughSeq: Long): Job? {
        val source = connection ?: return null
        val ownerEpoch = epoch
        readTarget = id to throughSeq
        if (readJob?.isActive == true) return readJob
        return work.launch {
            while (true) {
                val target = readTarget ?: break
                readTarget = null
                val generation = readGeneration
                managementMutex.withLock {
                    fun currentHost(): Boolean = !closed && epoch == ownerEpoch && connection === source
                    if (!currentHost() || state.value.selectedSessionId != target.first || generation != readGeneration) return@withLock
                    val summary = state.value.sessions.firstOrNull { it.id == target.first } ?: return@withLock
                    if (!summary.managementAvailable || target.second <= summary.lastReadSeq) return@withLock
                    try {
                        val updated = operations.update(source, ConversationUpdate(target.first, summary.revision, summary.customTitle,
                            summary.pinned, minOf(target.second, summary.lastEventSeq)))
                        currentCoroutineContext().ensureActive()
                        if (currentHost()) replaceSummary(updated)
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Throwable) { /* A later history update can acknowledge again. */ }
                }
            }
        }.also { readJob = it }
    }

    private fun replaceSummary(summary: SessionSummary) {
        listRequest++; listJob?.cancel()
        mutableState.value = state.value.copy(sessions = state.value.sessions.map { if (it.id == summary.id) summary else it },
            listPhase = ConversationDataPhase.Ready)
    }

    private fun removeSummary(id: SessionId) {
        listRequest++; listJob?.cancel()
        val remaining = state.value.sessions.filterNot { it.id == id }
        mutableState.value = state.value.copy(sessions = remaining,
            listPhase = if (remaining.isEmpty()) ConversationDataPhase.Empty else ConversationDataPhase.Ready)
    }

    /** Data refresh never cancels or retries an execution command. */
    public fun dismissManagementError() { mutableState.value = state.value.copy(managementError = null) }
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

    public fun refreshSessions(): Job? = refreshSessions(quiet = false)

    /**
     * [quiet] keeps the visible list phase untouched while the stream refreshes the authoritative
     * run state. Without it, every streamed event would flash the list back to Loading.
     */
    private fun refreshSessions(quiet: Boolean): Job? {
        val fence = capture() ?: return null
        if (quiet) {
            // A list already in flight publishes the same authoritative state; replacing it would
            // strand its Loading phase.
            listJob?.takeIf { it.isActive }?.let { return it }
        } else {
            listJob?.cancel()
        }
        val request = ++listRequest
        if (!quiet) {
            mutableState.value = state.value.copy(listPhase = ConversationDataPhase.Loading, errors = without(ConversationErrorKind.List))
        }
        return start({ listJob = it }) {
            try {
                val result = operations.list(fence.connection).toList()
                currentCoroutineContext().ensureActive()
                if (valid(fence) && request == listRequest) {
                    val settledPhase = if (result.isEmpty()) ConversationDataPhase.Empty else ConversationDataPhase.Ready
                    val runState = syncedRunState(result)
                    mutableState.value = state.value.copy(
                        sessions = result,
                        listPhase = if (!quiet || state.value.listPhase == ConversationDataPhase.Loading) settledPhase
                            else state.value.listPhase,
                        errors = without(ConversationErrorKind.List),
                        action = settledAction(runState),
                        runState = runState,
                    )
                    if (historyNeedsCatchup()) refreshHistory(quiet = quiet)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (!quiet && valid(fence) && request == listRequest) {
                    mutableState.value = state.value.copy(listPhase = ConversationDataPhase.Failed)
                    fail(ConversationErrorKind.List, ConversationRetry.Refresh)
                }
            } finally {
                if (!quiet && valid(fence) && request == listRequest && state.value.listPhase == ConversationDataPhase.Loading)
                    mutableState.value = state.value.copy(listPhase = ConversationDataPhase.Unbound)
            }
        }
    }

    /**
     * `running` is the only authority for Stop. A delivery-only turn settles to Idle once the Host
     * reports it, which re-enables the composer; a locally tracked command keeps its own state.
     */
    private fun syncedRunState(sessions: List<SessionSummary>): ConversationRunState {
        if (state.value.action !in listOf(ConversationAction.Idle, ConversationAction.RunningUnknown))
            return state.value.runState
        return sessions.firstOrNull { it.id == state.value.selectedSessionId }
            ?.let { if (it.running) ConversationRunState.Running else ConversationRunState.Idle }
            ?: ConversationRunState.Unknown
    }

    private fun settledAction(runState: ConversationRunState): ConversationAction =
        if (runState == ConversationRunState.Idle && state.value.action == ConversationAction.RunningUnknown)
            ConversationAction.Idle else state.value.action

    public fun refreshHistory(): Job? = refreshHistory(quiet = false)

    /**
     * [quiet] reconciles the authoritative snapshot after streamed messages settled without flashing
     * the transcript back to Loading. A list or history refresh already in flight publishes a newer
     * snapshot than a quiet call would, so it is reused instead of being replaced.
     */
    private fun refreshHistory(quiet: Boolean): Job? {
        val fence = capture() ?: return null
        val id = fence.selected ?: return null
        val readableThrough = state.value.sessions.firstOrNull { it.id == id }?.lastEventSeq ?: -1
        if (quiet) {
            historyJob?.takeIf { it.isActive }?.let { return it }
        } else {
            historyJob?.cancel()
        }
        val request = ++historyRequest
        if (!quiet) {
            mutableState.value = state.value.copy(historyPhase = ConversationDataPhase.Loading, errors = without(ConversationErrorKind.History))
        }
        return start({ historyJob = it }) {
            try {
                val result = operations.history(fence.connection, id).map(::snapshotMessage)
                val configuration = fence.connection.session.modelConfiguration(id)
                currentCoroutineContext().ensureActive()
                if (valid(fence) && request == historyRequest) {
                    mutableState.value = state.value.copy(
                        history = result, historyReadSeq = readableThrough, modelConfiguration = configuration,
                        historyPhase = if (result.isEmpty()) ConversationDataPhase.Empty else ConversationDataPhase.Ready,
                        errors = without(ConversationErrorKind.History),
                    )
                    // This request is settled; a catch-up must not reuse this now-finished job.
                    if (request == historyRequest) historyJob = null
                    if (historyNeedsCatchup()) refreshHistory(quiet = quiet)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (!quiet && valid(fence) && request == historyRequest) {
                    mutableState.value = state.value.copy(historyPhase = ConversationDataPhase.Failed)
                    fail(ConversationErrorKind.History, ConversationRetry.History)
                }
            } finally {
                if (valid(fence) && request == historyRequest && state.value.historyPhase == ConversationDataPhase.Loading)
                    mutableState.value = state.value.copy(historyPhase = ConversationDataPhase.Unbound)
            }
        }
    }

    private fun historyNeedsCatchup(): Boolean = state.value.historyPhase == ConversationDataPhase.Ready &&
        (state.value.sessions.firstOrNull { it.id == state.value.selectedSessionId }?.lastEventSeq ?: -1) > state.value.historyReadSeq

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
        val edit = state.value.editing?.takeIf { initial.selected != null }
        mutableState.value = state.value.copy(
            action = if (initial.selected == null) ConversationAction.Creating else ConversationAction.Sending,
            errors = without(ConversationErrorKind.Create, ConversationErrorKind.Association, ConversationErrorKind.Prompt,
                ConversationErrorKind.Completion, ConversationErrorKind.Fork),
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
                        listPhase = ConversationDataPhase.Ready, history = emptyList(), modelConfiguration = null, historyPhase = ConversationDataPhase.Unbound,
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
                if (edit != null) {
                    // DSH edits by forking: the child carries the history before this message and the
                    // edited text becomes its next prompt. The original conversation is untouched.
                    val parent = requireNotNull(fence.selected)
                    phase = ConversationErrorKind.Fork
                    val forked = operations.fork(fence.connection, parent, edit.messageId)
                    currentCoroutineContext().ensureActive()
                    if (!valid(fence) || request != sendRequest) return@start
                    selection++
                    listRequest++
                    historyRequest++
                    listJob?.cancel()
                    historyJob?.cancel()
                    drafts.remove(parent)
                    drafts[forked.id] = state.value.draft
                    mutableState.value = state.value.copy(
                        selectedSessionId = forked.id, sessions = (state.value.sessions + forked).distinctBy { it.id },
                        listPhase = ConversationDataPhase.Ready, history = emptyList(), modelConfiguration = null, historyPhase = ConversationDataPhase.Unbound,
                        lastAcceptedReceipt = null, action = ConversationAction.Sending,
                        runState = ConversationRunState.Unknown,
                    )
                    fence = capture() ?: return@start
                    replaceStream()
                    phase = ConversationErrorKind.Prompt
                }
                val id = requireNotNull(fence.selected)
                val prompt = operations.prompt(fence.connection, id, userMessage(draft), if (initial.selected == null) AgentOptions() else agentOptions)
                currentCoroutineContext().ensureActive()
                if (!valid(fence) || request != sendRequest) return@start
                val current = state.value
                val clear = current.draft == draft && current.draftRevision == revision
                drafts[id] = if (clear) "" else current.draft
                mutableState.value = current.copy(
                    draft = if (clear) "" else current.draft,
                    draftRevision = if (clear) revision + 1 else current.draftRevision,
                    lastAcceptedReceipt = prompt.receipt, editing = null,
                    action = if (capability == CompletionCapability.LocalIdle) ConversationAction.WaitingForLocalIdle else ConversationAction.RunningUnknown,
                    runState = if (capability == CompletionCapability.LocalIdle) ConversationRunState.Running else ConversationRunState.Unknown,
                )
                refreshHistory()
                refreshSessions()
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
                    // The stream already appended the settled turn; reconcile silently so the transcript
                    // never drops back to its loading state after an answer finishes.
                    refreshHistory(quiet = true)
                    refreshSessions(quiet = true)
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
                // DSH Stop aborts the running turn but keeps the queued inbox.
                operations.cancel(fence.connection, id, keepInbox = true)
                currentCoroutineContext().ensureActive()
                if (!valid(fence) || request != cancelRequest) return@start
                sendRequest++
                sendJob?.cancel()
                // Stop acknowledgement also does not prove remote idle; ask the Host for the truth.
                mutableState.value = state.value.copy(action = ConversationAction.Idle, runState = ConversationRunState.Unknown)
                refreshHistory(quiet = true)
                refreshSessions(quiet = true)
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
        livePublishJob?.cancel()
        livePublishJob = null
        liveFold.reset()
        mutableLive.value = LiveTranscript()
        owner.cancel()
        connection = null
        streams = null
        drafts.clear()
        mutableState.value = ConversationState()
    }

    private fun replaceStream() {
        val previous = streamJob
        previous?.cancel()
        // A different Host, member, Session, or connection never inherits another transcript's deltas.
        stopLive()
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
                    // A gap is the only signal that the delta fold can no longer be trusted: re-read the
                    // authoritative history and start the fold over.
                    refresh = {
                        if (valid(fence)) {
                            stopLive()
                            refreshHistory(quiet = true)
                            refreshSessions(quiet = true)
                        }
                    },
                    onEvent = ::onStreamEvent,
                ).run(id)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Throwable) {
                if (valid(fence)) fail(ConversationErrorKind.Stream, ConversationRetry.History)
            }
        }
    }

    /**
     * Folds one Session stream event into the live tail or the authoritative history.
     *
     * Deltas never touch [ConversationState]: they are throttled into [live]. A settled message is
     * appended to history immediately (deduplicated by id) so the tail disappears exactly when its
     * own message takes over, and `turn/end` reconciles the list and history quietly.
     */
    private fun onStreamEvent(event: RelayEvent) {
        val fence = capture() ?: return
        val id = fence.selected ?: return
        if (event.streamId.value != id.value) return
        when (event.event) {
            in LiveSessionEventNames.attentionBoundaries -> {
                // A retry or a new step must not concatenate the previous attempt's partial text.
                liveFold.reset()
                publishLive(immediate = true)
            }

            LiveSessionEventNames.ASSISTANT_CHUNK -> {
                val update = LiveSessionEventCodec.decode(event) as? LiveUpdate.Chunk ?: return
                liveFold.apply(update.turn, update.step, update.chunk)
                publishLive(immediate = false)
            }

            LiveSessionEventNames.ASSISTANT_MESSAGE -> {
                val update = LiveSessionEventCodec.decode(event) as? LiveUpdate.AssistantSettled ?: return
                // The settled message takes over from the tail; the two must never both be visible.
                liveFold.reset()
                publishLive(immediate = true)
                appendSettled(update.message)
            }

            LiveSessionEventNames.USER_MESSAGE -> {
                val update = LiveSessionEventCodec.decode(event) as? LiveUpdate.UserMessage ?: return
                appendSettled(update.message)
            }

            LiveSessionEventNames.TURN_START -> {
                // The Host now reports the turn as running; the list carries Stop's authority.
                refreshSessions(quiet = true)
            }

            LiveSessionEventNames.TURN_END -> {
                liveFold.reset()
                publishLive(immediate = true)
                scheduleReconcile(fence)
            }
        }
    }

    /** Appends a durable Session message; IDs make the Host snapshot and the stream converge. */
    private fun appendSettled(message: Message) {
        val current = state.value
        if (current.history.any { it.id == message.id }) return
        val settled = snapshotMessage(message).let {
            if (it.timestampMillis == null) it.copy(timestampMillis = System.currentTimeMillis()) else it
        }
        mutableState.value = current.copy(
            history = current.history + settled,
            // An initial snapshot in flight owns the phase; it replaces this list when it lands.
            historyPhase = if (current.historyPhase == ConversationDataPhase.Loading) current.historyPhase
                else ConversationDataPhase.Ready,
        )
    }

    /**
     * Reconciles the transcript after a turn settled. Waiting for an in-flight snapshot first keeps
     * this refresh from being satisfied by a fetch that started before the final message.
     */
    private fun scheduleReconcile(fence: Fence) {
        work.launch {
            historyJob?.takeIf { it.isActive }?.join()
            if (!valid(fence)) return@launch
            refreshHistory(quiet = true)
            if (valid(fence)) refreshSessions(quiet = true)
        }
    }

    private fun publishLive(immediate: Boolean) {
        val session = state.value.selectedSessionId
        if (session == null) {
            livePublishJob?.cancel()
            livePublishJob = null
            mutableLive.value = LiveTranscript()
            return
        }
        if (immediate) {
            livePublishJob?.cancel()
            livePublishJob = null
            mutableLive.value = LiveTranscript(session, listOfNotNull(liveFold.message()))
        } else if (livePublishJob?.isActive != true) {
            livePublishJob = work.launch {
                delay(liveFrameMillis)
                livePublishJob = null
                if (closed) return@launch
                val current = state.value.selectedSessionId ?: return@launch
                mutableLive.value = LiveTranscript(current, listOfNotNull(liveFold.message()))
            }
        }
    }

    private fun stopLive() {
        liveFold.reset()
        livePublishJob?.cancel()
        livePublishJob = null
        mutableLive.value = LiveTranscript(state.value.selectedSessionId)
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
            ConversationErrorKind.Fork -> "未能创建编辑分支，请重试。"
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

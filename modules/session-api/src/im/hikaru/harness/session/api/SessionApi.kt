package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionOrigin
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.UserMessageEvent
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.ErrorTurnEndReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import im.hikaru.harness.session.persistence.*
import im.hikaru.harness.session.SessionArchiveKey
import im.hikaru.harness.session.deriveMessages
import im.hikaru.harness.session.foldRequestContext
import im.hikaru.harness.session.foldRequestHeader
import im.hikaru.harness.agent.AgentQuiescentRestore
import im.hikaru.harness.agent.AgentArchiveBegin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Serializable
data class SessionSummary(
    val id: SessionId,
    val createdAt: Long,
    val cwd: String? = null,
    val agentPreset: String? = null,
    val title: String? = null,
    val updatedAt: Long = createdAt,
    val pinned: Boolean = false,
    val unread: Boolean = false,
    val archived: Boolean = false,
    val revision: Long = 0,
    val lastEventSeq: Long = -1,
    val managementAvailable: Boolean = false,
    val archiveAvailable: Boolean = false,
    val live: Boolean = true,
    /** Authoritative execution state of a live Agent; only a running Agent may be stopped. */
    val running: Boolean = false,
    val customTitle: String? = null,
    val lastReadSeq: Long = -1,
)

@Serializable
data class ConversationUpdate(val id: SessionId, val expectedRevision: Long, val title: String?,
    val pinned: Boolean, val lastReadSeq: Long)

@Serializable
data class SessionPromptReceipt(
    val sessionId: SessionId,
    val messageId: String,
)

/** Provider-neutral Session facade. */
interface SessionApi {
    suspend fun deleteConversation(id: SessionId, expectedRevision: Long): Unit = error("Conversation deletion is unavailable")
    suspend fun updateConversation(update: ConversationUpdate): SessionSummary = error("Conversation management is unavailable")
    suspend fun archiveConversation(id: SessionId, archived: Boolean): Unit = error("Archive management is unavailable")
    suspend fun terminalCapability(session: SessionId): HostTerminalDiscoveryResult =
        HostTerminalDiscoveryResult.Unavailable(session, "Terminal Host service is unavailable")

    suspend fun createTerminal(request: TerminalCreateRequest): HostTerminalCreateResult =
        HostTerminalCreateResult.Unavailable(request.owner, "Terminal Host service is unavailable")

    suspend fun terminals(owner: TerminalOwner): TerminalListResult =
        TerminalListResult.Unavailable(owner, "Terminal Host service is unavailable")

    suspend fun readTerminal(key: TerminalKey): TerminalReadResult =
        TerminalReadResult.Unavailable(key, "Terminal Host service is unavailable")

    suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult =
        BackgroundProcessesResult.Unavailable(parent, "Background process service is unavailable")

    suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult =
        BackgroundOutputResult.Unavailable(parent, process, "Background output service is unavailable")

    suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult =
        BackgroundInputResult.Unavailable(parent, process, "Background input service is unavailable")

    suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        BackgroundControlResult.Unavailable(parent, process, "Background input service is unavailable")

    suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        BackgroundControlResult.Unavailable(parent, process, "Background process control service is unavailable")

    suspend fun descendants(parent: SessionId): SessionDescendantsResult =
        SessionDescendantsResult.Unavailable(parent)

    suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult =
        ChildHistoryResult.Unavailable(parent, child)

    suspend fun list(): List<SessionSummary>

    suspend fun create(
        id: SessionId? = null,
        options: CreateSessionOptions = CreateSessionOptions(),
        agentOptions: AgentOptions = AgentOptions(),
    ): SessionSummary

    suspend fun history(id: SessionId): List<Message>

    suspend fun prompt(
        id: SessionId,
        message: Message,
    ): SessionPrompt

    suspend fun cancel(
        id: SessionId,
        keepInbox: Boolean = false,
    )

    /**
     * Creates a new conversation whose history is [SessionId]'s events before [beforeMessageId],
     * leaving the original untouched (DSH `fork`). The caller prompts the edited text into the child.
     */
    suspend fun fork(
        parent: SessionId,
        beforeMessageId: String,
    ): SessionSummary = error("Session forking is unavailable")
}

/** A prompt completion handle that does not expose Agent internals. */
interface SessionPrompt {
    val receipt: SessionPromptReceipt
    suspend fun awaitIdle()
    /** Recorded failure of this prompt's turn; does not expose Provider response bodies. */
    suspend fun failure(): SessionPromptFailure? = null
}

data class SessionPromptFailure(val code: String, val status: Int? = null)

internal class DefaultSessionApi(
    private val context: Context,
) : SessionApi {
    private val restoreMutex = Mutex()
    private val descendantsReader = LiveSessionDescendants(context.agents)

    override suspend fun terminalCapability(session: SessionId): HostTerminalDiscoveryResult =
        context.get(SessionTerminalHostKey)?.discover(session)
            ?: HostTerminalDiscoveryResult.Unavailable(session, "Terminal Host service is unavailable")

    override suspend fun createTerminal(request: TerminalCreateRequest): HostTerminalCreateResult =
        context.get(SessionTerminalHostKey)?.create(request)
            ?: HostTerminalCreateResult.Unavailable(request.owner, "Terminal Host service is unavailable")

    override suspend fun terminals(owner: TerminalOwner): TerminalListResult =
        context.get(SessionTerminalHostKey)?.list(owner)
            ?: TerminalListResult.Unavailable(owner, "Terminal Host service is unavailable")

    override suspend fun readTerminal(key: TerminalKey): TerminalReadResult =
        context.get(SessionTerminalHostKey)?.read(key)
            ?: TerminalReadResult.Unavailable(key, "Terminal Host service is unavailable")

    override suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult =
        context.get(SessionTerminalHostKey)?.write(key, input)
            ?: TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    override suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult =
        context.get(SessionTerminalHostKey)?.input(key, input)
            ?: TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    override suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult =
        context.get(SessionTerminalHostKey)?.resize(key, size)
            ?: TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    override suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult =
        context.get(SessionTerminalHostKey)?.close(key)
            ?: TerminalMutationResult.Unavailable(key, "Terminal Host service is unavailable")

    override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult =
        context.get(BackgroundProcessesKey)?.list(parent)
            ?: BackgroundProcessesResult.Unavailable(parent, "Background process service is unavailable")

    override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult =
        context.get(BackgroundProcessesKey)?.read(parent, process)
            ?: BackgroundOutputResult.Unavailable(parent, process, "Background output service is unavailable")

    override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult =
        context.get(BackgroundProcessesKey)?.write(parent, process, input)
            ?: BackgroundInputResult.Unavailable(parent, process, "Background input service is unavailable")

    override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        context.get(BackgroundProcessesKey)?.closeInput(parent, process)
            ?: BackgroundControlResult.Unavailable(parent, process, "Background input service is unavailable")

    override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        context.get(BackgroundProcessesKey)?.cancel(parent, process)
            ?: BackgroundControlResult.Unavailable(parent, process, "Background process control service is unavailable")

    override suspend fun descendants(parent: SessionId): SessionDescendantsResult =
        descendantsReader.descendants(parent)

    override suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult =
        descendantsReader.readChildHistory(parent, child)

    override suspend fun list(): List<SessionSummary> = conversationSummaries()

    private suspend fun conversationSummaries(id: SessionId? = null, includeSubagents: Boolean = false): List<SessionSummary> {
        val agents = context.agents.list()
        val live = agents.map { it.session }.filter { id == null || it.id == id }.associateBy { it.id }
        val running = agents.filter { it.status == AgentStatus.RUNNING }.mapTo(mutableSetOf()) { it.session.id }
        val persistence = context.get(SessionPersistenceKey)
        val catalog = persistence?.conversationCatalog(id)?.associateBy { it.header.id }
        val durableHeaders = catalog?.mapValues { it.value.header }
            ?: persistence?.listHeaders().orEmpty().filter { id == null || it.id == id }.associateBy { it.id }
        val headers = durableHeaders + live.mapValues { it.value.header }
        val automaticallySaved = context.get(im.hikaru.harness.session.SessionDurabilityKey) != null
        val gate = context.get(SessionArchiveKey)
        return headers.values.filter { includeSubagents || it.origin != im.hikaru.harness.session.SessionOrigin.SUBAGENT }.mapNotNull { header ->
            val durable = catalog?.get(header.id)
            val metadata = if (catalog != null) durable?.metadata else persistence?.metadata(header.id)
            if (metadata?.deleted == true) return@mapNotNull null
            // A deleted live instance must stay hidden even if its best-effort cleanup has failed.
            if (catalog != null && durable == null && persistence?.metadata(header.id)?.deleted == true) return@mapNotNull null
            val durableActivity = durable?.activity?.takeIf { automaticallySaved || header.id !in live }
            val events = if (durableActivity != null) emptyList() else live[header.id]?.events() ?: persistence?.readEvents(header.id).orEmpty()
            val automaticTitle = im.hikaru.harness.session.defaultConversationTitle(deriveMessages(events))
            val lastSeq = durableActivity?.lastSeq ?: events.lastOrNull()?.seq ?: -1
            SessionSummary(id = header.id, createdAt = header.createdAt, cwd = header.cwd, agentPreset = header.agentPreset,
                title = metadata?.title ?: durableActivity?.title ?: automaticTitle,
                updatedAt = durableActivity?.updatedAt ?: events.lastOrNull()?.time ?: header.createdAt,
                pinned = metadata?.pinned ?: false, unread = lastSeq > (metadata?.lastReadSeq ?: -1),
                archived = gate?.isArchived(header) ?: false, revision = metadata?.revision ?: 0, lastEventSeq = lastSeq,
                managementAvailable = persistence?.supportsConversationMetadata == true, archiveAvailable = gate?.supportsManagement == true,
                live = header.id in live, running = header.id in running,
                customTitle = metadata?.title, lastReadSeq = metadata?.lastReadSeq ?: -1)
        }
    }

    override suspend fun updateConversation(update: ConversationUpdate): SessionSummary {
        val persistence = context.get(SessionPersistenceKey) ?: error("Conversation persistence is unavailable")
        context.sessions.get(update.id)?.let { checkpoint(it) }
        persistence.updateMetadata(update.id, update.expectedRevision, update.title, update.pinned, update.lastReadSeq)
        return conversationSummaries(update.id).single()
    }

    override suspend fun deleteConversation(id: SessionId, expectedRevision: Long) = restoreMutex.withLock {
        val persistence = context.get(SessionPersistenceKey) ?: error("Conversation persistence is unavailable")
        when (val prepared = context.agents.beginArchive(AgentId(id.value))) {
            is AgentArchiveBegin.Granted -> try {
                withContext(NonCancellable) {
                    persistence.deleteConversation(id, expectedRevision)
                    check(prepared.preparation.commit() == null) { "Deleted; execution cleanup needs refresh" }
                }
            } finally { prepared.preparation.dispose() }
            AgentArchiveBegin.NotRegistered -> {
                check(context.sessions.get(id) == null) { "Conversation is still live" }
                persistence.deleteConversation(id, expectedRevision)
            }
            else -> error("Conversation is busy; delete after execution stops")
        }
    }

    override suspend fun archiveConversation(id: SessionId, archived: Boolean): Unit = restoreMutex.withLock {
        val persistence = context.get(SessionPersistenceKey) ?: error("Conversation persistence is unavailable")
        check(persistence.metadata(id)?.deleted != true) { "Conversation was deleted" }
        val gate = context.get(SessionArchiveKey)?.takeIf { it.supportsManagement } ?: error("Archive management is unavailable")
        if (!archived) {
            val header = persistence.listHeaders()?.firstOrNull { it.id == id } ?: error("Session is not durable")
            check(gate.setArchived(header, false) == null) { "Unarchived; crash durability is uncertain" }
            return@withLock
        }
        when (val prepared = context.agents.beginArchive(AgentId(id.value))) {
            is AgentArchiveBegin.Granted -> try {
                val session = context.sessions.get(id) ?: error("Session is unavailable")
                checkpoint(session)
                withContext(NonCancellable) {
                    val durabilityWarning = gate.setArchived(session.header, true)
                    val cleanupWarning = prepared.preparation.commit()
                    check(durabilityWarning == null && cleanupWarning == null) { "Archived; durability or cleanup needs refresh" }
                }
            } finally { prepared.preparation.dispose() }
            AgentArchiveBegin.NotRegistered -> {
                val header = persistence.listHeaders()?.firstOrNull { it.id == id } ?: error("Session is not durable")
                check(context.sessions.get(id) == null) { "Session is still live" }
                check(gate.setArchived(header, true) == null) { "Archived; crash durability is uncertain" }
            }
            else -> error("Conversation is busy; archive after execution stops")
        }
    }

    override suspend fun create(
        id: SessionId?,
        options: CreateSessionOptions,
        agentOptions: AgentOptions,
    ): SessionSummary {
        if (id != null) check(context.get(SessionPersistenceKey)?.listHeaders()?.none { it.id == id } != false) {
            "Session already exists in durable storage"
        }
        // DSH resolves `workspace?.path ?? request.cwd ?? defaultCwd`; the Host's own directory is the
        // last resort, so a chat started without a project still has a real working directory.
        val resolved = options.copy(cwd = options.cwd ?: context.get(SessionDefaultCwdKey))
        val handle = context.agents.create(id?.let { AgentId(it.value) }, agentOptions, resolved)
        checkpoint(handle.agent.session)
        return conversationSummaries(handle.agent.session.id, includeSubagents = true).single()
    }

    override suspend fun history(id: SessionId): List<Message> {
        check(context.get(SessionPersistenceKey)?.metadata(id)?.deleted != true) { "Conversation was deleted" }
        return context.sessions.get(id)?.deriveMessages() ?: context.get(SessionPersistenceKey)?.readEvents(id)
            ?.let(::deriveMessages) ?: error("Session history is unavailable")
    }

    override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
        check(context.get(SessionPersistenceKey)?.metadata(id)?.deleted != true) { "Conversation was deleted" }
        val agent = restoreMutex.withLock {
            context.agents.get(AgentId(id.value)) ?: run {
                val persistence = context.get(SessionPersistenceKey) ?: error("Session persistence is unavailable")
                val loaded = persistence.loadQuiescent(id)
                check(loaded is SessionQuiescentLoad.Loaded) { "Session cannot be restored: ${(loaded as SessionQuiescentLoad.Rejected).reason}" }
                val events = loaded.session.events()
                val request = foldRequestContext(events)
                val config = im.hikaru.harness.session.foldRequestHeader(events)?.config
                try { when (val restored = context.agents.restoreQuiescent(loaded.session, AgentOptions(
                    cwd = loaded.session.header.cwd, preset = loaded.session.header.agentPreset,
                    provider = request?.provider ?: config?.provider, model = request?.model ?: config?.model,
                    reasoningEffort = config?.reasoningEffort, maxTokens = config?.maxTokens))) {
                    is AgentQuiescentRestore.Granted -> restored.handle.agent
                    is AgentQuiescentRestore.Rejected -> error(restored.reason)
                } } catch (error: Throwable) {
                    withContext(NonCancellable) { loaded.handle.dispose() }
                    throw error
                }
            }
        }
        val messageId = agent.followup(message)
        checkpoint(agent.session)
        return DefaultSessionPrompt(
            receipt = SessionPromptReceipt(id, messageId),
            await = agent::awaitIdle,
            session = agent.session,
            originalMessageId = message.id.value,
            checkpoint = { checkpoint(agent.session) },
        )
    }

    override suspend fun cancel(id: SessionId, keepInbox: Boolean) {
        requireAgent(id).cancel(keepInbox = keepInbox)
        context.sessions.get(id)?.let { checkpoint(it) }
    }

    override suspend fun fork(parent: SessionId, beforeMessageId: String): SessionSummary {
        check(context.get(SessionPersistenceKey)?.metadata(parent)?.deleted != true) { "Conversation was deleted" }
        val parentSession = context.sessions.get(parent)
        // A turn in flight has not settled its events; copying that prefix would inherit a partial
        // turn. DSH forks a settled conversation, which is also the only state the edit entry offers.
        require(context.agents.get(AgentId(parent.value))?.status != AgentStatus.RUNNING) {
            "Session is running; stop it before editing a message"
        }
        val events = parentSession?.events()
            ?: context.get(SessionPersistenceKey)?.readEvents(parent)
            ?: error("Session history is unavailable")
        val header = parentSession?.header
            ?: context.get(SessionPersistenceKey)?.listHeaders()?.firstOrNull { it.id == parent }
            ?: error("Session is not available")
        val cut = events.indexOfFirst { envelope ->
            envelope.type == SessionEventNames.USER_MESSAGE &&
                Json.decodeFromJsonElement(UserMessageEvent.serializer(), envelope.data).message.id.value == beforeMessageId
        }
        require(cut >= 0) { "Message '$beforeMessageId' is not a user message of this Session" }
        val config = foldRequestHeader(events)?.config
        val handle = context.agents.create(
            options = AgentOptions(
                cwd = header.cwd, preset = header.agentPreset,
                provider = config?.provider, model = config?.model,
                reasoningEffort = config?.reasoningEffort, maxTokens = config?.maxTokens,
            ),
            sessionOptions = CreateSessionOptions(
                cwd = header.cwd ?: context.get(SessionDefaultCwdKey),
                parentSession = parent,
                seedLength = cut.toLong(),
                origin = SessionOrigin.FORK,
                agentPreset = header.agentPreset,
            ),
            seedEvents = events.take(cut),
        )
        checkpoint(handle.agent.session)
        return conversationSummaries(handle.agent.session.id, includeSubagents = true).single()
    }

    /** The durability hook already commits every event; explicit snapshots serve manual-save hosts. */
    private suspend fun checkpoint(session: Session) {
        if (context.get(im.hikaru.harness.session.SessionDurabilityKey) == null)
            context.get(SessionPersistenceKey)?.flush(session)
    }

    private suspend fun requireAgent(id: SessionId) =
        context.agents.get(AgentId(id.value))
            ?: error("Session '${id.value}' is not live")

}

private class DefaultSessionPrompt(
    override val receipt: SessionPromptReceipt,
    private val await: suspend () -> Unit,
    private val session: Session,
    private val originalMessageId: String,
    private val checkpoint: suspend () -> Unit = {},
) : SessionPrompt {
    override suspend fun awaitIdle() { await(); checkpoint() }

    override suspend fun failure(): SessionPromptFailure? {
        val events = session.events()
        val turn = events.asSequence().filter { it.type == SessionEventNames.USER_MESSAGE }
            .map { Json.decodeFromJsonElement(UserMessageEvent.serializer(), it.data) }
            .firstOrNull { it.message.id.value == originalMessageId }?.turn ?: return null
        val end = events.asReversed().firstOrNull {
            it.type == SessionEventNames.TURN_END &&
                Json.decodeFromJsonElement(TurnEndEvent.serializer(), it.data).turn == turn
        } ?: return null
        val reason = Json.decodeFromJsonElement(TurnEndEvent.serializer(), end.data).reason
        return (reason as? ErrorTurnEndReason)?.failure?.let { SessionPromptFailure(it.code, it.status) }
    }
}

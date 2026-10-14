package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.sessions
import kotlinx.serialization.Serializable

@Serializable
data class SessionSummary(
    val id: SessionId,
    val createdAt: Long,
    val cwd: String? = null,
    val agentPreset: String? = null,
)

@Serializable
data class SessionPromptReceipt(
    val sessionId: SessionId,
    val messageId: String,
)

/** Provider-neutral Session facade. */
interface SessionApi {
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
}

/** A prompt completion handle that does not expose Agent internals. */
interface SessionPrompt {
    val receipt: SessionPromptReceipt
    suspend fun awaitIdle()
}

internal class DefaultSessionApi(
    private val context: Context,
) : SessionApi {
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

    override suspend fun list(): List<SessionSummary> =
        context.agents.list().map { agent -> summary(agent.session) }

    override suspend fun create(
        id: SessionId?,
        options: CreateSessionOptions,
        agentOptions: AgentOptions,
    ): SessionSummary {
        val handle = context.agents.create(id?.let { AgentId(it.value) }, agentOptions, options)
        return summary(handle.agent.session)
    }

    override suspend fun history(id: SessionId): List<Message> =
        requireAgent(id).session.deriveMessages()

    override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
        val agent = requireAgent(id)
        val messageId = agent.followup(message)
        return DefaultSessionPrompt(
            receipt = SessionPromptReceipt(id, messageId),
            await = agent::awaitIdle,
        )
    }

    override suspend fun cancel(id: SessionId, keepInbox: Boolean) {
        requireAgent(id).cancel(keepInbox = keepInbox)
    }

    private suspend fun requireAgent(id: SessionId) =
        context.agents.get(AgentId(id.value))
            ?: error("Session '${id.value}' is not live")

    private fun summary(session: Session): SessionSummary =
        SessionSummary(
            id = session.id,
            createdAt = session.header.createdAt,
            cwd = session.header.cwd,
            agentPreset = session.header.agentPreset,
        )
}

private class DefaultSessionPrompt(
    override val receipt: SessionPromptReceipt,
    private val await: suspend () -> Unit,
) : SessionPrompt {
    override suspend fun awaitIdle() = await()
}

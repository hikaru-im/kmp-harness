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

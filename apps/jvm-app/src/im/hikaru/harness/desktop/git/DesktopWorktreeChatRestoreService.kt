package im.hikaru.harness.desktop.git

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentQuiescentRestore
import im.hikaru.harness.agent.agents
import im.hikaru.harness.client.git.ManagedWorktreeChatRestore
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.foldRequestHeader
import im.hikaru.harness.session.persistence.SessionPersistenceKey
import im.hikaru.harness.session.persistence.SessionQuiescentLoad
import im.hikaru.harness.session.persistence.SessionQuiescentRejection
import im.hikaru.harness.session.sessions

/**
 * Production quiescent restore of one unarchived chat.
 *
 * It refuses to publish a partial instance: the chat is rehydrated only from validated, quiescent
 * durable history, and an Agent is attached only through a factory that declares it performs no
 * autonomous work. No prompt is sent, no open turn is repaired and nothing is flushed. The model
 * for the restored Agent comes from the chat's own last request context, not from the settings page.
 */
class DesktopWorktreeChatRestoreService(private val context: Context) {
    suspend fun restore(id: String): ManagedWorktreeChatRestore {
        val sessionId = SessionId(id)
        if (context.sessions.get(sessionId) != null) return ManagedWorktreeChatRestore.AlreadyLive
        val persistence = context.get(SessionPersistenceKey) ?: return ManagedWorktreeChatRestore.Unavailable
        return when (val loaded = persistence.loadQuiescent(sessionId)) {
            is SessionQuiescentLoad.Rejected -> when (loaded.reason) {
                SessionQuiescentRejection.NOT_FOUND -> ManagedWorktreeChatRestore.NoHistory
                else -> ManagedWorktreeChatRestore.Failed(loaded.reason.name)
            }
            is SessionQuiescentLoad.Loaded -> {
                val model = loaded.session.requestContext()
                val config = foldRequestHeader(loaded.session.events())?.config
                val options = AgentOptions(
                    cwd = loaded.session.header.cwd,
                    preset = loaded.session.header.agentPreset,
                    provider = model?.provider ?: config?.provider,
                    model = model?.model ?: config?.model,
                    reasoningEffort = config?.reasoningEffort,
                    maxTokens = config?.maxTokens,
                    reasoning = config?.reasoning,
                )
                when (val restored = context.agents.restoreQuiescent(loaded.session, options)) {
                    is AgentQuiescentRestore.Granted -> ManagedWorktreeChatRestore.Restored
                    is AgentQuiescentRestore.Rejected -> {
                        // Never leave a live, agentless Session behind after a rejected attach.
                        loaded.handle.dispose()
                        ManagedWorktreeChatRestore.Failed(restored.reason)
                    }
                }
            }
        }
    }
}

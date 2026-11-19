package im.hikaru.harness.session.api

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionOrigin
import kotlinx.coroutines.CancellationException

/** Finite live reads, with identity rechecks after any history suspension. Not a lifecycle service. */
internal class LiveSessionDescendants(
    private val registry: AgentRegistry,
    private val readMessages: suspend (Session) -> List<Message> = { it.deriveMessages() },
) {
    suspend fun descendants(parent: SessionId): SessionDescendantsResult.Available {
        val snapshot = snapshot()
        val root = snapshot[parent] ?: error("Parent session is not live")
        val verified = snapshot.keys.mapNotNull { child ->
            verifiedPath(snapshot, parent, child)?.let { child to it }
        }
        val children = verified.map { (child, proof) ->
            val entry = snapshot.getValue(child)
            val options = entry.agent.options
            // The final reply only enriches the row; a history read failure must not hide the
            // child from the overview. Cancellation still propagates.
            val finalReply = try {
                lastAssistantReply(readMessages(entry.session))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            SessionDescendant(
                child = child,
                parentSession = checkNotNull(entry.session.header.parentSession),
                verifiedAncestorPath = proof.path,
                status = when (entry.agent.status) {
                    AgentStatus.RUNNING -> DescendantStatus.RUNNING
                    AgentStatus.IDLE -> DescendantStatus.UNKNOWN
                },
                startedAtEpochMillis = entry.session.header.createdAt,
                provider = options.provider,
                model = options.model,
                reasoningEffort = (options.reasoning?.effort ?: options.reasoningEffort)?.value,
                reasoning = options.reasoning,
                lastAssistantMessage = finalReply?.text,
                lastAssistantAtEpochMillis = finalReply?.timestampMillis,
            )
        }
        requireCurrent(listOf(root) + verified.flatMap { it.second.entries })
        return SessionDescendantsResult.Available(parent, children)
    }

    suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult.Available {
        val snapshot = snapshot()
        check(parent in snapshot) { "Parent session is not live" }
        val proof = verifiedPath(snapshot, parent, child)
            ?: throw IllegalArgumentException("Session is not a verified live descendant")
        requireCurrent(proof.entries)
        val messages = readMessages(snapshot.getValue(child).session)
        // A removed/replaced parent, intermediate ancestor or child invalidates the read.
        requireCurrent(proof.entries)
        return ChildHistoryResult.Available(parent, child, proof.path, messages)
    }

    private suspend fun snapshot(): Map<SessionId, Entry> = registry.list().associate { agent ->
        val session = agent.session
        check(agent.id.value == session.id.value) { "Agent session identity changed" }
        session.id to Entry(agent, session)
    }

    private fun verifiedPath(
        snapshot: Map<SessionId, Entry>,
        parent: SessionId,
        child: SessionId,
    ): Proof? {
        if (parent == child) return null
        val seen = mutableSetOf<SessionId>()
        val ancestry = mutableListOf<Entry>()
        val reversePath = mutableListOf<SessionId>()
        var cursor: SessionId? = child
        var reachedParent = false
        while (cursor != null) {
            if (!seen.add(cursor)) return null
            val entry = snapshot[cursor] ?: return null
            ancestry += entry
            if (!reachedParent) reversePath += cursor
            if (cursor == parent) reachedParent = true
            val header = entry.session.header
            if (!reachedParent && header.origin != SessionOrigin.SUBAGENT) return null
            // Validate through the root as well so parent->child back-links cannot hide a cycle.
            // Origin on/above the requested parent need not be SUBAGENT (ordinary roots/forks).
            cursor = header.parentSession
        }
        if (!reachedParent) return null
        return Proof(reversePath.asReversed().toList(), ancestry.toList())
    }

    private suspend fun requireCurrent(entries: List<Entry>) {
        val current = snapshot()
        for (entry in entries.distinctBy { it.session.id }) {
            val latest = current[entry.session.id]
            check(latest != null && latest.agent === entry.agent && latest.session === entry.session) {
                "Descendant lineage changed during read"
            }
            check(entry.agent.session === entry.session && entry.session.isLive()) {
                "Descendant session is no longer live"
            }
        }
    }

    /** The newest assistant text: reasoning-only and tool-only turns are not a final reply. */
    private fun lastAssistantReply(messages: List<Message>): FinalReply? =
        messages.asReversed().firstNotNullOfOrNull { message ->
            if (message.role != MessageRole.ASSISTANT) return@firstNotNullOfOrNull null
            val text = message.content.filterIsInstance<TextBlock>()
                .joinToString(separator = "\n") { it.text }
                .trim()
            if (text.isEmpty()) null else FinalReply(text, message.timestampMillis)
        }

    private data class Entry(val agent: Agent, val session: Session)
    private data class Proof(val path: List<SessionId>, val entries: List<Entry>)
    private data class FinalReply(val text: String, val timestampMillis: Long?)
}

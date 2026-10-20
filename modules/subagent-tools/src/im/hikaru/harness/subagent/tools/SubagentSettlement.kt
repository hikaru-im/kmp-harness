package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.AbortedTurnEndReason
import im.hikaru.harness.session.AssistantChunkEvent
import im.hikaru.harness.session.AssistantMessageEvent
import im.hikaru.harness.session.BlockedTurnEndReason
import im.hikaru.harness.session.CompletedTurnEndReason
import im.hikaru.harness.session.ErrorTurnEndReason
import im.hikaru.harness.session.InterruptedTurnEndReason
import im.hikaru.harness.session.MaxTokensTurnEndReason
import im.hikaru.harness.session.SessionEventEnvelope
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.TurnEndReason
import im.hikaru.harness.session.api.DescendantStatus
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.api.SessionDescendantsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Delivers DSH's settlement notice to a delegating parent.
 *
 * A child works without a caller attached to its result, so the runtime has to
 * account for the ending itself: once the child's turn ends with nothing left
 * in its inbox (and no nested child still working), the parent receives a
 * user-visible message carrying the child's closing assistant output.
 *
 * The notice is additive. A child that returned its result with
 * `send_message` before finishing still produces one, because the parent never
 * sees the child's transcript or tool output.
 */
internal class SubagentSettlement(
    private val context: Context,
    private val registry: AgentRegistry,
) : Disposable {
    private val scope: CoroutineScope = context.managedScope()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Follow one accepted child until it is disposed, notifying the exact live
     * parent every time a turn settles. The seed pass covers a child that
     * finished before this listener was registered.
     */
    fun watch(parent: Agent, child: Agent) {
        val events = Channel<SessionEventEnvelope>(Channel.UNLIMITED)
        val subscription = context.on(SessionEvents.Appended) { notice ->
            if (notice.session.id == child.session.id) events.trySend(notice.event)
        }
        scope.launch {
            try {
                val fold = AssistantOutputFold(json)
                var lastSeq = -1L
                var terminal: TurnEndEvent? = null
                for (event in child.session.events()) {
                    lastSeq = maxOf(lastSeq, event.seq)
                    terminal = fold.push(event) ?: terminal
                }
                if (terminal != null) settle(parent, child, terminal, fold.output())
                for (event in events) {
                    if (event.seq <= lastSeq) continue
                    lastSeq = event.seq
                    val ending = fold.push(event) ?: continue
                    settle(parent, child, ending, fold.output())
                }
            } finally {
                subscription.dispose()
                events.close()
            }
        }
    }

    override suspend fun dispose(): Unit = withContext(NonCancellable) {
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun settle(
        parent: Agent,
        child: Agent,
        terminal: TurnEndEvent,
        output: List<ContentBlock>?,
    ) {
        if (registry.get(parent.id) !== parent) return
        if (!parent.session.isLive() || !child.session.isLive()) return
        if (registry.get(child.id) !== child) return
        // DSH defers the notice while the child still owns input or work.
        val inbox = child.inbox.snapshot()
        if (inbox.nextTurn.isNotEmpty() || inbox.nextStep.isNotEmpty()) return
        val sessionApi = context.get(SessionApiKey)
        if (sessionApi != null) {
            val descendants = sessionApi.descendants(child.session.id)
            if (descendants is SessionDescendantsResult.Available &&
                descendants.children.any { it.status == DescendantStatus.RUNNING }
            ) {
                return
            }
        }
        val message = createUserMessage(
            content = settlementContent(child.id.value, terminal.reason, output),
            source = PluginMessageSource(TOOL_SUBAGENT_PLUGIN_NAME),
        )
        try {
            if (parent.status == AgentStatus.RUNNING) parent.steer(message) else parent.followup(message)
        } catch (_: Exception) {
            // The parent may be disposing; a settlement notice is best effort.
        }
    }

    private fun settlementContent(
        childId: String,
        reason: TurnEndReason,
        output: List<ContentBlock>?,
    ): List<ContentBlock> {
        val summary = settlementSummary(childId, reason)
        val closing = output.orEmpty().filterIsInstance<TextBlock>().filter { it.text.isNotEmpty() }
        return buildList {
            add(TextBlock(summary))
            if (closing.isEmpty()) {
                add(TextBlock("It left no closing message."))
            } else {
                add(TextBlock("Its closing message:"))
                addAll(closing)
            }
        }
    }

    private fun settlementSummary(childId: String, reason: TurnEndReason): String {
        val subject = "Background subagent $childId"
        return when (reason) {
            CompletedTurnEndReason ->
                "$subject finished and will do no further work unless you send it more."
            is AbortedTurnEndReason, InterruptedTurnEndReason ->
                "$subject was stopped before it finished."
            BlockedTurnEndReason -> "$subject declined the task."
            MaxTokensTurnEndReason -> "$subject ran out of room before it finished."
            is ErrorTurnEndReason -> "$subject failed before it finished."
            else -> "$subject ended abnormally ($reason) before it finished."
        }
    }

    /**
     * DSH's `AssistantOutputFold`: the last non-empty assistant message wins,
     * with streamed text as the fallback for backends that emit only chunks.
     */
    private class AssistantOutputFold(private val json: Json) {
        private var message: List<ContentBlock>? = null
        private val streamed = StringBuilder()

        /** Feeds one session event; returns its turn ending when it carries one. */
        fun push(event: SessionEventEnvelope): TurnEndEvent? {
            when (event.type) {
                SessionEventNames.ASSISTANT_MESSAGE -> {
                    val data = json.decodeFromString(AssistantMessageEvent.serializer(), event.data.toString())
                    if (data.message.content.isNotEmpty()) message = data.message.content
                }
                SessionEventNames.ASSISTANT_CHUNK -> {
                    val data = json.decodeFromString(AssistantChunkEvent.serializer(), event.data.toString())
                    val chunk = data.chunk
                    if (chunk is TextDeltaChunk && chunk.text.isNotEmpty()) streamed.append(chunk.text)
                }
                SessionEventNames.TURN_END ->
                    return json.decodeFromString(TurnEndEvent.serializer(), event.data.toString())
            }
            return null
        }

        fun output(): List<ContentBlock>? =
            message ?: streamed.toString().takeIf { it.isNotEmpty() }?.let { listOf(TextBlock(it)) }
    }
}

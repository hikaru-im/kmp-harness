package im.hikaru.harness.client.app

import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.ToolResultContent
import im.hikaru.harness.client.connection.UserMessageSource

/**
 * Reserved for CONV-01/CONV-09: the per-turn Git diff summary. The value stays `null` until M2
 * supplies repository data; it must never be guessed from message text.
 */
internal data class TurnDiffSummary(
    val filesChanged: Int,
    val additions: Int,
    val deletions: Int,
)

/**
 * One Codex turn: the human prompt that opens it plus every message it owns up to the next human
 * prompt. A history that starts without a human prompt still forms one turn so no message is lost;
 * that legacy prefix simply has no [prompt].
 *
 * The turn id is the head message id. Message ids are stable across streaming and history reloads,
 * which keeps both the lazy key and the time-separator anchor stable while a turn grows.
 */
internal data class ConversationTurn(
    val id: String,
    val prompt: Message?,
    val messages: List<Message>,
    val diffSummary: TurnDiffSummary? = null,
) {
    /** The turn's first durable event time; a streaming-only turn has none. */
    val anchorTimestampMillis: Long? get() = messages.firstNotNullOfOrNull { it.timestampMillis }
}

internal fun isHumanMessage(message: Message): Boolean =
    message.role == MessageRole.USER && message.source == UserMessageSource

/** Groups a transcript into human-delimited turns without copying the messages themselves. */
internal fun conversationTurns(messages: List<Message>): List<ConversationTurn> {
    val turns = mutableListOf<ConversationTurn>()
    var index = 0
    while (index < messages.size) {
        // One turn: its head (normally the human prompt) plus every following non-human message.
        var cursor = index + 1
        while (cursor < messages.size && !isHumanMessage(messages[cursor])) cursor++
        val owned = messages.subList(index, cursor).toList()
        turns += ConversationTurn(
            id = messages[index].id,
            prompt = owned.first().takeIf(::isHumanMessage),
            messages = owned,
        )
        index = cursor
    }
    return turns
}

/**
 * Live Codex prints one `用时 {duration}` header at the top of a turn's activity; the duration is
 * the turn's own wall clock, measured from the human prompt to the turn's last event. The header is
 * attached to the first message of the turn that holds activity so a turn prints it once.
 */
internal fun turnWorkedFor(turns: List<ConversationTurn>): Map<String, Long> {
    val headers = mutableMapOf<String, Long>()
    turns.forEach { turn ->
        val activity = turn.messages.filterNot(::isHumanMessage)
        val firstActivity = activity.firstOrNull { message ->
            message.content.any { it is ReasoningContent || it is ToolCallContent || it is ToolResultContent }
        }
        val start = turn.prompt?.timestampMillis
            ?: activity.firstNotNullOfOrNull { it.timestampMillis }
        val end = activity.mapNotNull { it.timestampMillis }.maxOrNull()
        if (firstActivity != null && start != null && end != null && end > start) {
            headers[firstActivity.id] = end - start
        }
    }
    return headers
}

/** The action row sits under the last text of a turn, not under every message inside it. */
internal fun turnLastTextMessageIds(turns: List<ConversationTurn>): Set<String> {
    val ids = mutableSetOf<String>()
    turns.forEach { turn ->
        turn.messages.filterNot(::isHumanMessage)
            .lastOrNull { message -> message.content.any { it is TextContent } }
            ?.let { ids += it.id }
    }
    return ids
}

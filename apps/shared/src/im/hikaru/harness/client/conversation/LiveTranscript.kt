package im.hikaru.harness.client.conversation

import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageContent
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Assistant material the client is still receiving, folded straight from Session stream events.
 *
 * The transcript is deliberately separate from [ConversationState.history]: a history entry is
 * authoritative and survives a refresh, while this tail exists only until the matching
 * `assistant/message` settlement replaces it. Keeping it out of [ConversationState] also keeps the
 * app shell from recomposing once per token.
 */
public data class LiveTranscript(
    val sessionId: SessionId? = null,
    /** At most one synthetic assistant message: the step whose deltas are arriving. */
    val messages: List<Message> = emptyList(),
)

/** Session event names the client folds without reading history. */
internal object LiveSessionEventNames {
    const val ASSISTANT_CHUNK = "assistant/chunk"
    const val ASSISTANT_MESSAGE = "assistant/message"
    const val USER_MESSAGE = "user/message"
    const val TURN_START = "turn/start"
    const val TURN_END = "turn/end"
    const val STEP_START = "step/start"
    const val REQUEST_HEADER = "request/header"
    const val LLM_RETRY = "llm/retry"

    /** Events that begin a fresh attempt; a retry must not concatenate partial output. */
    val attentionBoundaries: Set<String> = setOf(STEP_START, REQUEST_HEADER, LLM_RETRY)
}

/** One decoded stream event the fold cares about. */
internal sealed interface LiveUpdate {
    data class Chunk(val turn: Long, val step: Long, val chunk: LiveChunk) : LiveUpdate

    data class AssistantSettled(val turn: Long, val step: Long, val message: Message) : LiveUpdate

    data class UserMessage(val turn: Long, val message: Message) : LiveUpdate
}

internal sealed interface LiveChunk {
    data class Start(val index: Int, val blockType: String) : LiveChunk

    data class Text(val index: Int, val text: String) : LiveChunk

    data class Reasoning(val index: Int, val text: String) : LiveChunk

    data class ToolCall(val index: Int, val id: String, val name: String?, val argumentsDelta: String) : LiveChunk

    data class End(val index: Int, val block: MessageContent) : LiveChunk

    /** Usage and finish frames do not change displayed content. */
    data object Ignored : LiveChunk
}

/**
 * Decodes the event payloads this client folds. The shapes mirror the Host's Session event JSON;
 * unknown fields and unknown chunk types are tolerated so a newer Host cannot break rendering.
 */
internal object LiveSessionEventCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decode(event: RelayEvent): LiveUpdate? =
        when (event.event) {
            LiveSessionEventNames.ASSISTANT_CHUNK -> runCatching {
                val wire = json.decodeFromJsonElement(WireAssistantChunk.serializer(), event.payload)
                LiveUpdate.Chunk(wire.turn, wire.step, wire.chunk.toLiveChunk())
            }.getOrNull()

            LiveSessionEventNames.ASSISTANT_MESSAGE -> runCatching {
                val wire = json.decodeFromJsonElement(WireAssistantMessage.serializer(), event.payload)
                LiveUpdate.AssistantSettled(wire.turn, wire.step, wire.message)
            }.getOrNull()

            LiveSessionEventNames.USER_MESSAGE -> runCatching {
                val wire = json.decodeFromJsonElement(WireUserMessage.serializer(), event.payload)
                LiveUpdate.UserMessage(wire.turn, wire.message)
            }.getOrNull()

            else -> null
        }
}

/**
 * Accumulates `assistant/chunk` deltas into the content of one assistant step.
 *
 * Mirrors the Host's BlockAssembler for display: completed blocks win, a tool call becomes visible
 * only once its identity (id and name) is known, and empty text/reasoning blocks stay hidden.
 */
internal class LiveAssistantFold {
    private class Block(var type: String) {
        // Tokens append in O(1); each published frame materializes the current text once.
        val text = StringBuilder()
        var toolCallId: String? = null
        var toolCallName: String? = null
        val toolCallArguments = StringBuilder()
        var completed: MessageContent? = null
    }

    private var turn: Long? = null
    private var step: Long? = null
    private val order = mutableListOf<Int>()
    private val blocks = mutableMapOf<Int, Block>()

    fun reset() {
        turn = null
        step = null
        order.clear()
        blocks.clear()
    }

    fun apply(chunkTurn: Long, chunkStep: Long, chunk: LiveChunk) {
        if (turn != chunkTurn || step != chunkStep) {
            reset()
            turn = chunkTurn
            step = chunkStep
        }
        when (chunk) {
            is LiveChunk.Start -> block(chunk.index, chunk.blockType)

            is LiveChunk.Text -> block(chunk.index, "text").let { if (it.completed == null) it.text.append(chunk.text) }
            is LiveChunk.Reasoning -> block(chunk.index, "reasoning").let { if (it.completed == null) it.text.append(chunk.text) }

            is LiveChunk.ToolCall -> block(chunk.index, "tool-call").let { live ->
                if (live.completed == null) {
                    live.toolCallId = chunk.id.takeIf(String::isNotBlank) ?: live.toolCallId
                    live.toolCallName = chunk.name?.takeIf(String::isNotBlank) ?: live.toolCallName
                    live.toolCallArguments.append(chunk.argumentsDelta)
                }
            }

            is LiveChunk.End -> block(chunk.index, chunk.block.blockType()).let { live ->
                if (live.completed == null) live.completed = chunk.block
            }

            LiveChunk.Ignored -> Unit
        }
    }

    /** The visible assistant message for the current step, or null while nothing is displayable. */
    fun message(): Message? {
        val currentTurn = turn ?: return null
        val currentStep = step ?: return null
        val content = order.mapNotNull { index -> blocks[index]?.let(::assemble) }
        if (content.isEmpty()) return null
        return Message(
            id = liveMessageId(currentTurn, currentStep),
            role = MessageRole.ASSISTANT,
            content = content,
            source = ModelMessageSource("live", "live"),
            // Streamed text has no durable timestamp; the settled message carries the event time.
            timestampMillis = null,
        )
    }

    private fun block(index: Int, type: String): Block = blocks.getOrPut(index) {
        order += index
        Block(type)
    }

    private fun assemble(block: Block): MessageContent? {
        block.completed?.let { completed -> return completed.takeIf { it.isVisible() } }
        return when (block.type) {
            "text" -> TextContent(block.text.toString()).takeIf { block.text.isNotEmpty() }
            "reasoning" -> ReasoningContent(block.text.toString()).takeIf { block.text.isNotEmpty() }
            "tool-call" -> {
                val id = block.toolCallId
                val name = block.toolCallName
                if (id.isNullOrBlank() || name.isNullOrBlank()) null
                else ToolCallContent(id, name, block.toolCallArguments.toString())
            }

            else -> null
        }
    }

    private fun MessageContent.isVisible(): Boolean = when (this) {
        is TextContent -> text.isNotEmpty()
        is ReasoningContent -> text.isNotEmpty()
        else -> true
    }

    companion object {
        const val LIVE_ID_PREFIX = "live:"

        fun liveMessageId(turn: Long, step: Long): String = "$LIVE_ID_PREFIX$turn:$step"

        fun isLiveId(id: String): Boolean = id.startsWith(LIVE_ID_PREFIX)
    }
}

private fun MessageContent.blockType(): String = when (this) {
    is TextContent -> "text"
    is ReasoningContent -> "reasoning"
    is ToolCallContent -> "tool-call"
    else -> "tool-result"
}

private fun WireChunk.toLiveChunk(): LiveChunk = when (this) {
    is WireBlockStart -> LiveChunk.Start(index, blockType)
    is WireTextDelta -> LiveChunk.Text(index, text)
    is WireReasoningDelta -> LiveChunk.Reasoning(index, text)
    is WireToolCallDelta -> LiveChunk.ToolCall(index, id, name, argumentsDelta)
    is WireBlockEnd -> LiveChunk.End(index, block)
    is WireUsage -> LiveChunk.Ignored
    is WireFinish -> LiveChunk.Ignored
}

@Serializable
private data class WireAssistantChunk(
    val turn: Long,
    val step: Long,
    val chunk: WireChunk,
)

@Serializable
private sealed interface WireChunk

@Serializable
@SerialName("block-start")
private data class WireBlockStart(
    val index: Int,
    val blockType: String,
) : WireChunk

@Serializable
@SerialName("text-delta")
private data class WireTextDelta(
    val index: Int,
    val text: String,
) : WireChunk

@Serializable
@SerialName("reasoning-delta")
private data class WireReasoningDelta(
    val index: Int,
    val text: String,
) : WireChunk

@Serializable
@SerialName("tool-call-delta")
private data class WireToolCallDelta(
    val index: Int,
    val id: String,
    val name: String? = null,
    val argumentsDelta: String,
) : WireChunk

@Serializable
@SerialName("block-end")
private data class WireBlockEnd(
    val index: Int,
    val block: MessageContent,
) : WireChunk

@Serializable
@SerialName("usage")
private data class WireUsage(
    val usage: JsonElement? = null,
) : WireChunk

@Serializable
@SerialName("finish")
private data class WireFinish(
    val reason: JsonElement? = null,
    val replayState: JsonElement? = null,
) : WireChunk

@Serializable
private data class WireAssistantMessage(
    val turn: Long,
    val step: Long,
    val message: Message,
    val usage: JsonElement? = null,
    val interrupted: Boolean? = null,
)

@Serializable
private data class WireUserMessage(
    val turn: Long,
    val message: Message,
)

package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.ModelMessageSource
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolResultBlock
import im.hikaru.harness.llm.koog.KoogReplayContext
import im.hikaru.harness.llm.koog.KoogReplayRestorer

/** Restores private reasoning metadata without replacing durable Harness content. */
public class OpenAiResponsesReplayRestorer : KoogReplayRestorer {
    override fun restore(
        message: HarnessMessage,
        base: KoogMessage.Assistant,
        context: KoogReplayContext,
    ): KoogMessage.Assistant {
        val state = decodeState(message)
        if (state.kind == OPENAI_RESPONSES_FOREIGN_REPLAY_KIND) {
            return base
        }
        validateIdentity(state, context)
        validateBlocks(state, message.content, base.parts)

        val parts =
            base.parts.mapIndexed { index, part ->
                val replayBlock = state.blocks[index]
                if (replayBlock.type != "reasoning") {
                    part
                } else {
                    val reasoning =
                        part as? MessagePart.Reasoning
                            ?: invalidResponsesReplay(
                                "OpenAI Responses reasoning replay requires a Koog reasoning part"
                            )
                    reasoning.copy(
                        id = replayBlock.reasoningId,
                        summary =
                            replayBlock.reasoningSummary.takeIf(List<String>::isNotEmpty),
                        encrypted = replayBlock.reasoningEncrypted,
                    )
                }
            }
        return base.copy(parts = parts)
    }

    private fun decodeState(message: HarnessMessage): OpenAiResponsesReplayState {
        val raw =
            (message.source as? ModelMessageSource)?.replayState
                ?: invalidResponsesReplay(
                    "OpenAI Responses replay requires model replay state"
                )
        return try {
            OpenAiResponsesReplayJsonCodec.decode(raw)
        } catch (error: LlmException) {
            throw error
        } catch (error: IllegalArgumentException) {
            invalidResponsesReplay("Invalid OpenAI Responses replay state", error)
        } catch (error: IllegalStateException) {
            invalidResponsesReplay("Invalid OpenAI Responses replay state", error)
        }
    }

    private fun validateIdentity(
        state: OpenAiResponsesReplayState,
        context: KoogReplayContext,
    ) {
        if (state.kind == OPENAI_RESPONSES_FOREIGN_REPLAY_KIND) return
        if (
            state.kind != OPENAI_RESPONSES_REPLAY_KIND ||
                state.version != OPENAI_RESPONSES_REPLAY_VERSION
        ) {
            invalidResponsesReplay("Unsupported OpenAI Responses replay kind/version")
        }
        if (
            context.targetApi != OPENAI_RESPONSES_ROUTE_ID ||
                state.provider != context.sourceProvider ||
                context.targetProvider != context.sourceProvider ||
                state.model != context.sourceModel ||
                context.targetModel != context.sourceModel
        ) {
            invalidResponsesReplay(
                "OpenAI Responses replay source and target identity do not match"
            )
        }
    }

    private fun validateBlocks(
        state: OpenAiResponsesReplayState,
        content: List<ContentBlock>,
        parts: List<MessagePart.ResponsePart>,
    ) {
        if (state.kind == OPENAI_RESPONSES_FOREIGN_REPLAY_KIND) return
        if (state.blocks.size != content.size || state.blocks.size != parts.size) {
            invalidResponsesReplay(
                "OpenAI Responses replay block count does not match durable content"
            )
        }
        state.blocks.forEachIndexed { index, block ->
            if (block.index != index) {
                invalidResponsesReplay(
                    "OpenAI Responses replay block indexes are not contiguous"
                )
            }
            if (block.type != content[index].replayType()) {
                invalidResponsesReplay(
                    "OpenAI Responses replay block type does not match durable content"
                )
            }
            if (block.type != "reasoning" &&
                (block.reasoningId != null ||
                    block.reasoningSummary.isNotEmpty() ||
                    block.reasoningEncrypted != null)
            ) {
                invalidResponsesReplay(
                    "OpenAI Responses reasoning metadata is attached to a non-reasoning block"
                )
            }
        }
    }

    private fun ContentBlock.replayType(): String =
        when (this) {
            is TextBlock -> "text"
            is ReasoningBlock -> "reasoning"
            is ToolCallBlock -> "tool-call"
            is ToolResultBlock -> "tool-result"
        }
}

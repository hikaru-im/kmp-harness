package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.ReasoningDeltaChunk
import im.hikaru.harness.llm.StreamChunk

/** reasoning StreamFrame 到 Harness reasoning chunk 的扩展点。 */
fun interface KoogReasoningMapper {
    fun map(
        frame: StreamFrame,
        state: KoogStreamState,
        context: KoogStreamContext,
    ): List<StreamChunk>
}

/** 只映射可由 Harness ReasoningBlock 无损表示的纯文本 reasoning。 */
internal class DefaultKoogReasoningMapper : KoogReasoningMapper {
    override fun map(
        frame: StreamFrame,
        state: KoogStreamState,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): List<StreamChunk> =
        when (frame) {
            is StreamFrame.ReasoningDelta -> mapDelta(frame, state)
            is StreamFrame.ReasoningComplete -> mapComplete(frame, state)
            else ->
                throw LlmException(
                    message = "Koog reasoning mapper received a non-reasoning frame",
                    code = KoogLlmErrorCode.INVALID_STREAM_STATE,
                )
        }

    private fun mapDelta(
        frame: StreamFrame.ReasoningDelta,
        state: KoogStreamState,
    ): List<StreamChunk> {
        if (frame.id != null || frame.summary != null) {
            unsupportedReasoning(
                "Koog reasoning delta contains id or summary"
            )
        }
        val text =
            frame.text
                ?: unsupportedReasoning("Koog reasoning delta contains no text")
        val block =
            state.open(
                providerIndex = frame.index,
                type = KoogStreamBlockType.REASONING,
            )
        return buildList {
            if (block.startIfNeeded()) {
                add(
                    BlockStartChunk(
                        index = block.outputIndex,
                        blockType = block.type.harnessType,
                    )
                )
            }
            block.append(text)
            add(
                ReasoningDeltaChunk(
                    index = block.outputIndex,
                    text = text,
                )
            )
        }
    }

    private fun mapComplete(
        frame: StreamFrame.ReasoningComplete,
        state: KoogStreamState,
    ): List<StreamChunk> {
        if (frame.id != null || frame.summary != null || frame.encrypted != null) {
            unsupportedReasoning(
                "Koog reasoning complete contains id, summary, or encrypted content"
            )
        }
        val block =
            state.open(
                providerIndex = frame.index,
                type = KoogStreamBlockType.REASONING,
            )
        return buildList {
            if (block.startIfNeeded()) {
                add(
                    BlockStartChunk(
                        index = block.outputIndex,
                        blockType = block.type.harnessType,
                    )
                )
            }
            block.complete()
            add(
                BlockEndChunk(
                    index = block.outputIndex,
                    block = ReasoningBlock(frame.content.joinToString("")),
                )
            )
        }
    }
}

private fun unsupportedReasoning(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.UNSUPPORTED_REASONING_CONTENT,
    )

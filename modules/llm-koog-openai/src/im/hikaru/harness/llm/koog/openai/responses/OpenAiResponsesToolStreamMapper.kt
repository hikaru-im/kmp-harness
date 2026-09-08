package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogStreamBlockType
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogStreamState
import im.hikaru.harness.llm.koog.KoogToolStreamMapper

/**
 * Koog 1.1.1 exposes Responses argument deltas with output item id, but the complete frame with call id.
 * The adapter therefore waits for the authoritative complete frame and emits one lossless final tool block.
 */
public class OpenAiResponsesToolStreamMapper : KoogToolStreamMapper {
    override fun map(
        frame: StreamFrame,
        state: KoogStreamState,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): List<StreamChunk> =
        when (frame) {
            is StreamFrame.ToolCallDelta -> emptyList()
            is StreamFrame.ToolCallComplete -> {
                val block =
                    state.open(
                        providerIndex = frame.index,
                        type = KoogStreamBlockType.TOOL_CALL,
                    )
                block.observeToolCallIdentity(frame.id, frame.name)
                val wasStarted = block.started
                block.startIfNeeded()
                val id = CallId(block.requireToolCallId())
                val name = block.requireToolCallName()
                block.complete(finalText = frame.content)
                buildList {
                    if (!wasStarted) {
                        add(
                            BlockStartChunk(
                                index = block.outputIndex,
                                blockType = block.type.harnessType,
                            )
                        )
                    }
                    add(
                        BlockEndChunk(
                            index = block.outputIndex,
                            block = ToolCallBlock(id, name, frame.content),
                        )
                    )
                }
            }
            else ->
                throw LlmException(
                    message = "OpenAI Responses tool mapper received a non-tool frame",
                    code = KoogLlmErrorCode.INVALID_STREAM_STATE,
                )
        }
}

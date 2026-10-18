package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallDeltaChunk

/** Koog tool-call 流帧到 Harness tool-call chunk 的扩展点。 */
fun interface KoogToolStreamMapper {
    fun map(
        frame: StreamFrame,
        state: KoogStreamState,
        context: KoogStreamContext,
    ): List<StreamChunk>
}

/** Tool-call mapping; protocols with separate text/choice and tool index spaces opt in explicitly. */
class DefaultKoogToolStreamMapper(
    private val independentIndices: Boolean = false,
) : KoogToolStreamMapper {
    override fun map(
        frame: StreamFrame,
        state: KoogStreamState,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): List<StreamChunk> =
        when (frame) {
            is StreamFrame.ToolCallDelta -> mapDelta(frame, state)
            is StreamFrame.ToolCallComplete -> mapComplete(frame, state)
            else ->
                throw LlmException(
                    message = "Koog tool stream mapper received a non-tool frame",
                    code = KoogLlmErrorCode.INVALID_STREAM_STATE,
                )
        }

    private fun mapDelta(
        frame: StreamFrame.ToolCallDelta,
        state: KoogStreamState,
    ): List<StreamChunk> {
        val (completedReasoning, block) = open(state, frame.index)
        block.observeToolCallIdentity(frame.id, frame.name)
        val content = frame.content
        val id = content?.let { CallId(block.requireToolCallId()) }
        val name = content?.let { block.takeToolCallNameForDelta() }
        return buildList {
            addAll(completedReasoning)
            if (block.startIfNeeded()) {
                add(
                    BlockStartChunk(
                        index = block.outputIndex,
                        blockType = block.type.harnessType,
                    )
                )
            }
            if (content != null) {
                block.append(content)
                add(
                    ToolCallDeltaChunk(
                        index = block.outputIndex,
                        id = checkNotNull(id),
                        name = name,
                        argumentsDelta = content,
                    )
                )
            }
        }
    }

    private fun mapComplete(
        frame: StreamFrame.ToolCallComplete,
        state: KoogStreamState,
    ): List<StreamChunk> {
        val (completedReasoning, block) = open(state, frame.index)
        block.observeToolCallIdentity(frame.id, frame.name)
        val wasStarted = block.started
        if (!wasStarted) {
            block.startIfNeeded()
        }
        val id = CallId(block.requireToolCallId())
        val name = block.requireToolCallName()
        block.complete(finalText = frame.content)
        return buildList {
            addAll(completedReasoning)
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
                    block =
                        ToolCallBlock(
                            id = id,
                            name = name,
                            arguments = frame.content,
                        ),
                )
            )
        }
    }

    private fun open(state: KoogStreamState, index: Int?): Pair<List<StreamChunk>, KoogStreamBlockState> =
        if (independentIndices) state.openIndependentToolCall(index)
        else emptyList<StreamChunk>() to state.open(index, KoogStreamBlockType.TOOL_CALL)
}

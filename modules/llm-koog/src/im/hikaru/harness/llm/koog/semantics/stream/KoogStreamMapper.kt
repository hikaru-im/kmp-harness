package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

/** 把 Koog StreamFrame 流转换成 Harness StreamChunk 流。 */
internal class KoogStreamMapper(
    finishReasonMapper: KoogFinishReasonMapper = StopOnlyKoogFinishReasonMapper(),
    usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
    private val reasoningMapper: KoogReasoningMapper = DefaultKoogReasoningMapper(),
    private val toolStreamMapper: KoogToolStreamMapper = DefaultKoogToolStreamMapper(),
    toolCallTerminalPolicy: KoogToolCallTerminalPolicy =
        StrictKoogToolCallTerminalPolicy,
    textTerminalPolicy: KoogTextTerminalPolicy =
        StrictKoogTextTerminalPolicy,
    private val replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
) {
    private val terminalMapper =
        KoogStreamTerminalMapper(
            finishReasonMapper = finishReasonMapper,
            usageMapper = usageMapper,
            toolCallTerminalPolicy = toolCallTerminalPolicy,
            textTerminalPolicy = textTerminalPolicy,
            replayWriter = replayWriter,
        )

    fun map(
        frames: Flow<StreamFrame>,
        context: KoogStreamContext,
    ): Flow<StreamChunk> =
        flow {
            val state = KoogStreamState()
            emitAll(
                frames.transformWhile { frame ->
                    when (frame) {
                        is StreamFrame.TextDelta -> {
                            val block =
                                state.open(
                                    providerIndex = frame.index,
                                    type = KoogStreamBlockType.TEXT,
                                )
                            if (block.startIfNeeded()) {
                                emit(
                                    BlockStartChunk(
                                        index = block.outputIndex,
                                        blockType = block.type.harnessType,
                                    )
                                )
                            }
                            block.append(frame.text)
                            emit(
                                TextDeltaChunk(
                                    index = block.outputIndex,
                                    text = frame.text,
                                )
                            )
                            true
                        }

                        is StreamFrame.TextComplete -> {
                            val block =
                                state.open(
                                    providerIndex = frame.index,
                                    type = KoogStreamBlockType.TEXT,
                                )
                            if (block.startIfNeeded()) {
                                emit(
                                    BlockStartChunk(
                                        index = block.outputIndex,
                                        blockType = block.type.harnessType,
                                    )
                                )
                            }
                            block.complete(finalText = frame.text)
                            emit(
                                BlockEndChunk(
                                    index = block.outputIndex,
                                    block = TextBlock(frame.text),
                                )
                            )
                            true
                        }

                        is StreamFrame.ReasoningDelta,
                        is StreamFrame.ReasoningComplete -> {
                            reasoningMapper
                                .map(frame, state, context)
                                .forEach { chunk -> emit(chunk) }
                            true
                        }

                        is StreamFrame.ToolCallDelta,
                        is StreamFrame.ToolCallComplete -> {
                            toolStreamMapper
                                .map(frame, state, context)
                                .forEach { chunk -> emit(chunk) }
                            true
                        }

                        is StreamFrame.End -> {
                            terminalMapper
                                .map(frame, state, context)
                                .forEach { chunk -> emit(chunk) }
                            false
                        }
                    }
                }
            )
            state.requireFinished()
    }
}

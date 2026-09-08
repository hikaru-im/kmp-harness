package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.buildStreamFrameFlow
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.ReasoningDeltaChunk
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KoogReasoningStreamIntegrationContractTest {

    @Test
    fun koogBuilderShouldMapSequentialIndexlessTextAndReasoningBlocks() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    frames =
                        buildStreamFrameFlow {
                            emitTextDelta("answer")
                            emitReasoningDelta(text = "thinking")
                            emitTextDelta("final")
                            emitEnd(finishReason = "stop")
                        },
                    context = testKoogStreamContext(),
                ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "answer"),
                BlockEndChunk(index = 0, block = TextBlock("answer")),
                BlockStartChunk(index = 1, blockType = "reasoning"),
                ReasoningDeltaChunk(index = 1, text = "thinking"),
                BlockEndChunk(index = 1, block = ReasoningBlock("thinking")),
                BlockStartChunk(index = 2, blockType = "text"),
                TextDeltaChunk(index = 2, text = "final"),
                BlockEndChunk(index = 2, block = TextBlock("final")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun reasoningFramesShouldUseSharedBlockLifecycleAndFinishNormally() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    frames =
                        flowOf(
                            StreamFrame.ReasoningDelta(
                                text = "think",
                                index = 4,
                            ),
                            StreamFrame.ReasoningComplete(
                                id = null,
                                content = listOf("think", "ing"),
                                index = 4,
                            ),
                            StreamFrame.End(finishReason = "stop"),
                        ),
                    context = testKoogStreamContext(),
                ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "reasoning"),
                ReasoningDeltaChunk(index = 0, text = "think"),
                BlockEndChunk(index = 0, block = ReasoningBlock("thinking")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun textAndReasoningShouldShareFirstSeenOutputIndexOrder() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    frames =
                        flowOf(
                            StreamFrame.TextDelta("answer", index = 7),
                            StreamFrame.ReasoningComplete(
                                id = null,
                                content = listOf("thinking"),
                                index = 4,
                            ),
                            StreamFrame.TextComplete("answer", index = 7),
                            StreamFrame.End(finishReason = "stop"),
                        ),
                    context = testKoogStreamContext(),
                ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "answer"),
                BlockStartChunk(index = 1, blockType = "reasoning"),
                BlockEndChunk(index = 1, block = ReasoningBlock("thinking")),
                BlockEndChunk(index = 0, block = TextBlock("answer")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }
}

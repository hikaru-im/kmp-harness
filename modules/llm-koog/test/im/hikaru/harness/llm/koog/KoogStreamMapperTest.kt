package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun KoogStreamMapper.map(frames: Flow<StreamFrame>): Flow<StreamChunk> =
    map(frames, testKoogStreamContext())

class KoogStreamMapperTest {

    @Test
    fun shouldMapTextDeltaCompleteAndEnd() = runTest {
        val frames =
            flowOf(
                StreamFrame.TextDelta("hel", index = 7),
                StreamFrame.TextDelta("lo", index = 7),
                StreamFrame.TextComplete("hello", index = 7),
                StreamFrame.End(finishReason = "stop"),
            )

        val chunks =
            KoogStreamMapper()
                .map(frames)
                .toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "hel"),
                TextDeltaChunk(index = 0, text = "lo"),
                BlockEndChunk(index = 0, block = TextBlock("hello")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun completeWithoutDeltaShouldStillOpenAndCloseTextBlock() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    flowOf(
                        StreamFrame.TextComplete("complete-only", index = 3),
                        StreamFrame.End(finishReason = "stop"),
                    )
                )
                .toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                BlockEndChunk(index = 0, block = TextBlock("complete-only")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun eachCollectionShouldOwnIndependentStreamState() = runTest {
        val mapped =
            KoogStreamMapper().map(
                flowOf(
                    StreamFrame.TextDelta("hello", index = 4),
                    StreamFrame.TextComplete("hello", index = 4),
                    StreamFrame.End(finishReason = "stop"),
                )
            )
        val expected =
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "hello"),
                BlockEndChunk(index = 0, block = TextBlock("hello")),
                FinishChunk(StopFinishReason),
            )

        assertEquals(expected, mapped.toList())
        assertEquals(expected, mapped.toList())
    }

    @Test
    fun missingIndexesShouldCreateSequentialImplicitTextBlocks() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    flowOf(
                        StreamFrame.TextDelta("first", index = null),
                        StreamFrame.TextComplete("first", index = null),
                        StreamFrame.TextComplete("second", index = null),
                        StreamFrame.End(finishReason = "stop"),
                    )
                ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "first"),
                BlockEndChunk(index = 0, block = TextBlock("first")),
                BlockStartChunk(index = 1, blockType = "text"),
                BlockEndChunk(index = 1, block = TextBlock("second")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun negativeProviderIndexesShouldFailWithStableCode() = runTest {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamMapper()
                    .map(flowOf(StreamFrame.TextDelta("negative", index = -1)))
                    .toList()
            }
        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }

    @Test
    fun successfulEndShouldRejectAnOpenTextBlock() = runTest {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamMapper()
                    .map(
                        flowOf(
                            StreamFrame.TextDelta("unfinished", index = 0),
                            StreamFrame.End(finishReason = "stop"),
                        )
                    )
                    .toList()
            }

        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }

    @Test
    fun successfulEndWithoutContentShouldUseEmptyResponseCode() = runTest {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamMapper()
                    .map(flowOf(StreamFrame.End(finishReason = "stop")))
                    .toList()
            }

        assertEquals(LlmErrorCode.EMPTY_RESPONSE, error.code)
    }

    @Test
    fun unsupportedFramesShouldReachTheirExplicitMapperFailures() = runTest {
        val unsupported =
            listOf(
                StreamFrame.ReasoningDelta(
                    id = "private-id",
                    text = "thinking",
                    index = 0,
                ) to KoogLlmErrorCode.UNSUPPORTED_REASONING_CONTENT,
            )

        unsupported.forEach { (frame, expectedCode) ->
            val error =
                assertFailsWith<LlmException> {
                    KoogStreamMapper()
                        .map(flowOf(frame))
                        .toList()
                }
            assertEquals(expectedCode, error.code)
        }
    }

    @Test
    fun unknownFinishReasonShouldNotBeGuessed() = runTest {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamMapper()
                    .map(
                        flowOf(
                            StreamFrame.TextComplete("answer", index = 0),
                            StreamFrame.End(finishReason = "length"),
                        )
                    )
                    .toList()
            }

        assertEquals(KoogLlmErrorCode.UNSUPPORTED_FINISH_REASON, error.code)
    }

    @Test
    fun endShouldTerminateBeforeTrailingProviderFrames() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    flowOf(
                        StreamFrame.TextDelta("done", index = 0),
                        StreamFrame.TextComplete("done", index = 0),
                        StreamFrame.End(finishReason = "stop"),
                        StreamFrame.TextDelta("must-not-appear", index = 1),
                    )
                )
                .toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "done"),
                BlockEndChunk(index = 0, block = TextBlock("done")),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun providerCompletionWithoutEndShouldFailWithStableCode() = runTest {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamMapper()
                    .map(
                        flowOf(
                            StreamFrame.TextComplete("answer", index = 0),
                        )
                    )
                    .toList()
            }

        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }

    @Test
    fun duplicateTextCompleteShouldFailWithStableCode() = runTest {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamMapper()
                    .map(
                        flowOf(
                            StreamFrame.TextComplete("answer", index = 0),
                            StreamFrame.TextComplete("answer again", index = 0),
                        )
                    )
                    .toList()
            }

        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
    }
}

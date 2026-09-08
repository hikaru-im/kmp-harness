package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.AbortedFinishReason
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.UsageChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

class KoogStreamTerminalMapperTest {

    @Test
    fun absentUsageShouldProduceOnlyFinishAfterStateValidation() {
        val state = completedTextState()

        val chunks =
            KoogStreamTerminalMapper().map(
                frame = StreamFrame.End(finishReason = "stop"),
                state = state,
                context = testKoogStreamContext(),
            )

        assertEquals(listOf(FinishChunk(StopFinishReason)), chunks)
        assertTrue(state.finished)
    }

    @Test
    fun reportedUsageAndFinishShouldShareContextAndKeepTerminalOrder() {
        val expectedContext =
            testKoogStreamContext(
                provider = "harness-route",
                model = "provider-model",
                koogProvider = "koog-provider",
            )
        var usageContext: KoogStreamContext? = null
        var finishContext: KoogStreamContext? = null
        val usage = TokenUsage(inputTokens = 8, outputTokens = 4)
        val mapper =
            KoogStreamTerminalMapper(
                usageMapper =
                    KoogUsageMapper { _, context ->
                        usageContext = context
                        usage
                    },
                finishReasonMapper =
                    KoogFinishReasonMapper { reason, context ->
                        assertEquals("stop", reason)
                        finishContext = context
                        StopFinishReason
                    },
            )

        val chunks =
            mapper.map(
                frame =
                    StreamFrame.End(
                        finishReason = "stop",
                        metaInfo =
                            ResponseMetaInfo.Empty.copy(
                                totalTokensCount = 12,
                                inputTokensCount = 8,
                                outputTokensCount = 4,
                            ),
                    ),
                state = completedTextState(),
                context = expectedContext,
            )

        assertEquals(expectedContext, usageContext)
        assertEquals(expectedContext, finishContext)
        assertEquals(
            listOf(
                UsageChunk(usage),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun invalidReportedUsageShouldFailBeforeAnyTerminalSnapshotExists() {
        val error =
            assertFailsWith<LlmException> {
                KoogStreamTerminalMapper().map(
                    frame =
                        StreamFrame.End(
                            finishReason = "stop",
                            metaInfo =
                                ResponseMetaInfo.Empty.copy(
                                    totalTokensCount = 12,
                                    inputTokensCount = 8,
                                ),
                        ),
                    state = completedTextState(),
                    context = testKoogStreamContext(),
                )
            }

        assertEquals(KoogLlmErrorCode.INVALID_USAGE, error.code)
    }

    @Test
    fun invalidSuccessfulStateShouldFailBeforeUsageAndFinishSemantics() {
        var usageCalls = 0
        var finishCalls = 0
        val mapper =
            KoogStreamTerminalMapper(
                usageMapper =
                    KoogUsageMapper { _, _ ->
                        usageCalls++
                        null
                    },
                finishReasonMapper =
                    KoogFinishReasonMapper { _, _ ->
                        finishCalls++
                        StopFinishReason
                    },
            )

        val error =
            assertFailsWith<LlmException> {
                mapper.map(
                    frame = StreamFrame.End(finishReason = "stop"),
                    state = KoogStreamState(),
                    context = testKoogStreamContext(),
                )
            }

        assertEquals(LlmErrorCode.EMPTY_RESPONSE, error.code)
        assertEquals(0, usageCalls)
        assertEquals(0, finishCalls)
    }

    @Test
    fun providerNeutralTerminalShouldNotGuessToolCallCompletionFromFinishString() {
        var finishCalls = 0
        val error =
            assertFailsWith<LlmException> {
                KoogStreamTerminalMapper(
                    finishReasonMapper =
                        KoogFinishReasonMapper { _, _ ->
                            finishCalls++
                            ToolCallsFinishReason
                        }
                ).map(
                    frame = StreamFrame.End(finishReason = "tool_calls"),
                    state = openToolCallState(),
                    context = testKoogStreamContext(),
                )
            }

        assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
        assertEquals(0, finishCalls)
    }

    @Test
    fun injectedTerminalPreparerShouldCloseProviderSpecificBlocksBeforeFinish() {
        val expectedContext = testKoogStreamContext(provider = "openai")
        val chunks =
            KoogStreamTerminalMapper(
                toolCallTerminalPolicy =
                    KoogToolCallTerminalPolicy { frame, context ->
                        assertEquals("tool_calls", frame.finishReason)
                        assertEquals(expectedContext, context)
                        true
                    },
                finishReasonMapper =
                    KoogFinishReasonMapper { _, _ -> ToolCallsFinishReason },
            ).map(
                frame = StreamFrame.End(finishReason = "tool_calls"),
                state = openToolCallState(),
                context = expectedContext,
            )

        assertEquals(
            listOf(
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{}",
                        ),
                ),
                FinishChunk(ToolCallsFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun replayWriterShouldNotRunForAbortedOrErrorTerminals() {
        listOf(
            AbortedFinishReason(LlmFailure(message = "cancelled", code = "CANCELLED")),
            ErrorFinishReason(LlmFailure(message = "timed out", code = "TIMEOUT")),
        ).forEach { terminalReason ->
            var writes = 0
            val chunks =
                KoogStreamTerminalMapper(
                    finishReasonMapper = KoogFinishReasonMapper { _, _ -> terminalReason },
                    replayWriter =
                        KoogReplayWriter { _, _, _, _ ->
                            writes++
                            error("replay writer must not be called for failed terminals")
                        },
                ).map(
                    frame = StreamFrame.End(finishReason = "stop"),
                    state = completedTextState(),
                    context = testKoogStreamContext(),
                )

            assertEquals(0, writes)
            assertNull((chunks.last() as FinishChunk).replayState)
        }
    }

    private fun completedTextState(): KoogStreamState =
        KoogStreamState().also { state ->
            state.open(0, KoogStreamBlockType.TEXT).also { block ->
                block.startIfNeeded()
                block.complete()
            }
        }

    private fun openToolCallState(): KoogStreamState =
        KoogStreamState().also { state ->
            state.open(0, KoogStreamBlockType.TOOL_CALL).also { block ->
                block.observeToolCallIdentity(id = "call-1", name = "lookup")
                block.startIfNeeded()
                block.append("{}")
            }
        }
}

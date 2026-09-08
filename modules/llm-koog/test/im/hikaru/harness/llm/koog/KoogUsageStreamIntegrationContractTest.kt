package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.UsageChunk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KoogUsageStreamIntegrationContractTest {

    @Test
    fun defaultMapperShouldEmitCommonUsageImmediatelyBeforeFinish() = runTest {
        val chunks =
            KoogStreamMapper()
                .map(
                    frames =
                        flowOf(
                            StreamFrame.TextComplete("answer", index = 0),
                            StreamFrame.End(
                                finishReason = "stop",
                                metaInfo =
                                    ResponseMetaInfo.Empty.copy(
                                        totalTokensCount = 12,
                                        inputTokensCount = 8,
                                        outputTokensCount = 4,
                                    ),
                            ),
                        ),
                    context = testKoogStreamContext(),
                ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                BlockEndChunk(index = 0, block = TextBlock("answer")),
                UsageChunk(TokenUsage(inputTokens = 8, outputTokens = 4)),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }

    @Test
    fun endShouldPassContextAndEmitUsageImmediatelyBeforeFinish() = runTest {
        val context =
            testKoogStreamContext(
                provider = "harness-route",
                model = "provider-model",
                koogProvider = "koog-provider",
            )
        var usageContext: KoogStreamContext? = null
        var finishContext: KoogStreamContext? = null
        val mapper =
            KoogStreamMapper(
                usageMapper =
                    KoogUsageMapper { metaInfo, seenContext ->
                        usageContext = seenContext
                        TokenUsage(
                            inputTokens = metaInfo.inputTokensCount!!.toLong(),
                            outputTokens = metaInfo.outputTokensCount!!.toLong(),
                        )
                    },
                finishReasonMapper =
                    KoogFinishReasonMapper { reason, seenContext ->
                        assertEquals("stop", reason)
                        finishContext = seenContext
                        StopFinishReason
                    },
            )

        val chunks =
            mapper.map(
                frames =
                    flowOf(
                        StreamFrame.TextComplete("answer", index = 3),
                        StreamFrame.End(
                            finishReason = "stop",
                            metaInfo =
                                ResponseMetaInfo.Empty.copy(
                                    totalTokensCount = 12,
                                    inputTokensCount = 8,
                                    outputTokensCount = 4,
                                ),
                        ),
                    ),
                context = context,
            ).toList()

        assertEquals(context, usageContext)
        assertEquals(context, finishContext)
        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                BlockEndChunk(index = 0, block = TextBlock("answer")),
                UsageChunk(TokenUsage(inputTokens = 8, outputTokens = 4)),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
    }
}

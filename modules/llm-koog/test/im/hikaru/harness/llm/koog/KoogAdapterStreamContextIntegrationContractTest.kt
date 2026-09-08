package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.createUserMessage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KoogAdapterStreamContextIntegrationContractTest {

    @Test
    fun adapterShouldKeepHarnessRouteAndKoogProviderDistinct() = runTest {
        val model = testKoogModel(id = "provider-model", provider = "koog-provider")
        var usageContext: KoogStreamContext? = null
        var finishContext: KoogStreamContext? = null
        val adapter =
            KoogLlmAdapter(
                executor =
                    RecordingPromptExecutor(
                        frames =
                            flowOf(
                                StreamFrame.TextComplete("answer", index = 0),
                                StreamFrame.End(
                                    finishReason = "stop",
                                    metaInfo =
                                        ResponseMetaInfo.Empty.copy(
                                            totalTokensCount = 3,
                                            inputTokensCount = 2,
                                            outputTokensCount = 1,
                                        ),
                                ),
                            )
                    ),
                routes =
                    listOf(
                        testKoogRoute(
                            model = model,
                            provider = "harness-route",
                        )
                    ),
                usageMapper =
                    KoogUsageMapper { _, context ->
                        usageContext = context
                        TokenUsage(inputTokens = 2, outputTokens = 1)
                    },
                finishReasonMapper =
                    KoogFinishReasonMapper { _, context ->
                        finishContext = context
                        StopFinishReason
                    },
            )

        adapter.stream(
            GenerateOptions(
                provider = "harness-route",
                model = model.id,
                messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            )
        ).toList()

        val expected =
            KoogStreamContext(
                provider = "harness-route",
                model = "provider-model",
                koogProvider = "koog-provider",
            )
        assertEquals(expected, usageContext)
        assertEquals(expected, finishContext)
    }
}

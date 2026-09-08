package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.ToolSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KoogProviderSemanticsTest {

    @Test
    fun registryShouldRequireAnExactUniqueProviderMatch() {
        val routes =
            listOf(
                testKoogRoute(),
                testKoogRoute(
                    model = testKoogModel(id = "backup-model", provider = "backup"),
                    provider = "backup",
                ),
            )
        val invalid =
            listOf(
                emptyList(),
                listOf(KoogProviderSemantics("test")),
                listOf(
                    KoogProviderSemantics("test"),
                    KoogProviderSemantics("unknown"),
                ),
                listOf(
                    KoogProviderSemantics("test"),
                    KoogProviderSemantics("test"),
                ),
            )

        invalid.forEach { semantics ->
            assertFailsWith<IllegalArgumentException> {
                KoogProviderSemanticsRegistry(routes, semantics)
            }
        }
    }

    @Test
    fun registryShouldDispatchEverySemanticByTheSameHarnessRoute() {
        val alphaModel = testKoogModel(id = "alpha-model", provider = "koog-alpha")
        val betaModel = testKoogModel(id = "beta-model", provider = "koog-beta")
        val routes =
            listOf(
                testKoogRoute(model = alphaModel, provider = "alpha"),
                testKoogRoute(model = betaModel, provider = "beta"),
            )
        val providerError = IllegalStateException("beta failed")
        val betaFailure = LlmFailure(message = "beta rejected", code = "BETA")
        val registry =
            KoogProviderSemanticsRegistry(
                routes = routes,
                semantics =
                    listOf(
                        semantics(
                            provider = "alpha",
                            optionMarker = "alpha-options",
                            usage = TokenUsage(inputTokens = 1, outputTokens = 2),
                            finishReason = StopFinishReason,
                            failure = null,
                        ),
                        semantics(
                            provider = "beta",
                            optionMarker = "beta-options",
                            usage = TokenUsage(inputTokens = 3, outputTokens = 4),
                            finishReason = MaxTokensFinishReason,
                            failure = betaFailure,
                            expectedError = providerError,
                        ),
                    ),
            )
        val betaOptions =
            GenerateOptions(
                provider = "beta",
                model = betaModel.id,
                messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            )
        val streamContext =
            KoogStreamContext(
                provider = "beta",
                model = betaModel.id,
                koogProvider = betaModel.provider.id,
            )
        val failureContext =
            KoogFailureContext(
                provider = "beta",
                model = betaModel.id,
            )

        assertEquals("beta-options", registry.map(betaOptions, betaModel).user)
        assertEquals(
            TokenUsage(inputTokens = 3, outputTokens = 4),
            registry.map(ResponseMetaInfo.Empty, streamContext),
        )
        assertEquals(MaxTokensFinishReason, registry.map("max", streamContext))
        assertEquals(betaFailure, registry.classify(providerError, failureContext))
        assertNull(
            registry.classify(
                providerError,
                KoogFailureContext(provider = "unknown", model = "model"),
            )
        )
    }

    @Test
    fun missingRuntimeSemanticsShouldUseStableProviderCode() {
        val registry =
            KoogProviderSemanticsRegistry(
                routes = listOf(testKoogRoute()),
                semantics = listOf(KoogProviderSemantics("test")),
            )
        val error =
            assertFailsWith<LlmException> {
                registry.map(
                    ResponseMetaInfo.Empty,
                    KoogStreamContext(
                        provider = "unknown",
                        model = "model",
                        koogProvider = "koog",
                    ),
                )
            }

        assertEquals(KoogLlmErrorCode.UNKNOWN_PROVIDER, error.code)
    }

    @Test
    fun registryShouldDispatchRequestAndStreamExtensionPointsByTargetRoute() {
        val betaModel = testKoogModel(id = "beta-model", provider = "koog-beta")
        var messageCalls = 0
        var toolSchemaCalls = 0
        var reasoningCalls = 0
        var toolStreamCalls = 0
        var toolCallTerminalPolicyCalls = 0
        val registry =
            KoogProviderSemanticsRegistry(
                routes =
                    listOf(
                        testKoogRoute(),
                        testKoogRoute(model = betaModel, provider = "beta").copy(
                            api = "beta-api"
                        ),
                    ),
                semantics =
                    listOf(
                        KoogProviderSemantics("test"),
                        KoogProviderSemantics(
                            provider = "beta-api",
                            messageMapper =
                                KoogMessageMapper { _, context ->
                                    assertEquals("beta", context.targetContext?.provider)
                                    assertEquals("beta-api", context.targetContext?.api)
                                    messageCalls++
                                    KoogMessage.User(
                                        parts = emptyList(),
                                        metaInfo = RequestMetaInfo.Empty,
                                    )
                                },
                            toolMapper =
                                KoogToolMapper { tools, context ->
                                    assertEquals("beta", context.provider)
                                    assertEquals("beta-api", context.api)
                                    assertEquals(1, tools?.size)
                                    toolSchemaCalls++
                                    emptyList()
                                },
                            reasoningMapper =
                                KoogReasoningMapper { _, _, context ->
                                    assertEquals("beta", context.provider)
                                    assertEquals("beta-api", context.api)
                                    reasoningCalls++
                                    listOf(BlockStartChunk(index = 0, blockType = "reasoning"))
                                },
                            toolStreamMapper =
                                KoogToolStreamMapper { _, _, context ->
                                    assertEquals("beta", context.provider)
                                    assertEquals("beta-api", context.api)
                                    toolStreamCalls++
                                    listOf(BlockStartChunk(index = 0, blockType = "tool-call"))
                                },
                            toolCallTerminalPolicy =
                                KoogToolCallTerminalPolicy { _, context ->
                                    assertEquals("beta", context.provider)
                                    assertEquals("beta-api", context.api)
                                    toolCallTerminalPolicyCalls++
                                    true
                                },
                        ),
                    ),
            )
        val betaContext =
            KoogStreamContext(
                provider = "beta",
                model = betaModel.id,
                koogProvider = betaModel.provider.id,
                api = "beta-api",
            )

        registry.messageMapper.map(
            createUserMessage(listOf(TextBlock("hello"))),
            KoogMessageMappingContext(targetContext = betaContext),
        )
        registry.toolMapper.map(
            tools = listOf(ToolSchema("lookup", "Lookup", kotlinx.serialization.json.JsonObject(emptyMap()))),
            context = betaContext,
        )
        registry.reasoningMapper.map(
            frame = StreamFrame.ReasoningDelta(text = "think", index = 0),
            state = KoogStreamState(),
            context = betaContext,
        )
        registry.toolStreamMapper.map(
            frame =
                StreamFrame.ToolCallDelta(
                    id = "call-1",
                    name = "lookup",
                    content = "{",
                    index = 0,
                ),
            state = KoogStreamState(),
            context = betaContext,
        )
        assertEquals(
            true,
            registry.toolCallTerminalPolicy.shouldCompleteOpenToolCalls(
                frame = StreamFrame.End(finishReason = "provider-finish"),
                context = betaContext,
            ),
        )

        assertEquals(1, messageCalls)
        assertEquals(1, toolSchemaCalls)
        assertEquals(1, reasoningCalls)
        assertEquals(1, toolStreamCalls)
        assertEquals(1, toolCallTerminalPolicyCalls)
    }

    private fun semantics(
        provider: String,
        optionMarker: String,
        usage: TokenUsage,
        finishReason: im.hikaru.harness.llm.FinishReason,
        failure: LlmFailure?,
        expectedError: Throwable? = null,
    ): KoogProviderSemantics =
        KoogProviderSemantics(
            provider = provider,
            optionMapper =
                KoogOptionMapper { options, _ ->
                    assertEquals(provider, options.provider)
                    LLMParams(user = optionMarker)
                },
            usageMapper =
                KoogUsageMapper { _, context ->
                    assertEquals(provider, context.provider)
                    usage
                },
            finishReasonMapper =
                KoogFinishReasonMapper { _, context ->
                    assertEquals(provider, context.provider)
                    finishReason
                },
            failureClassifier =
                KoogProviderFailureClassifier { error, context ->
                    assertEquals(provider, context.provider)
                    if (expectedError == null || error === expectedError) failure else null
                },
        )
}

package im.hikaru.harness.llm.koog.openai.semantics

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.koog.KoogFailureContext
import im.hikaru.harness.llm.koog.KoogCredentialRef
import im.hikaru.harness.llm.koog.KoogProviderSettings
import im.hikaru.harness.llm.koog.resolveRoutes
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatFinishReasonMapper
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatToolCallTerminalPolicy
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatUsageMapper
import im.hikaru.harness.llm.koog.openai.client.EnvironmentKoogCredentialResolver
import im.hikaru.harness.llm.koog.openai.client.OpenAiPromptExecutorFactory
import im.hikaru.harness.llm.koog.openai.failure.OpenAiFailureClassifier
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesFinishReasonMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class OpenAiSemanticsTest {

    private val textOptions =
        GenerateOptions(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = OpenAIModels.Chat.GPT4oMini.id,
            messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
        )

    @Test
    fun chatOptionsShouldPreserveOpenAiFieldsAndUseChatParams() {
        val params =
            OpenAiChatOptionMapper().map(
                options =
                    textOptions.copy(
                        temperature = 0.2,
                        maxTokens = 512,
                        stop = listOf("END", "DONE"),
                    ),
                model = OpenAIModels.Chat.GPT4oMini,
            )

        val openAi = assertIs<OpenAIChatParams>(params)
        assertEquals(0.2, openAi.temperature)
        assertEquals(512, openAi.maxTokens)
        assertEquals(listOf("END", "DONE"), openAi.stop)
        assertNull(openAi.reasoningEffort)
    }

    @Test
    fun responsesOptionsShouldUseNativeResponsesParamsAndRejectChatOnlyFields() {
        val responsesOptions =
            textOptions.copy(
                temperature = 0.2,
                maxTokens = 512,
            )
        val params =
            OpenAiResponsesOptionMapper().map(
                options = responsesOptions,
                model = OpenAIModels.Chat.GPT4oMini,
            )

        val responses = assertIs<OpenAIResponsesParams>(params)
        assertEquals(0.2, responses.temperature)
        assertEquals(512, responses.maxTokens)
        assertEquals(false, responses.store)

        listOf(
            responsesOptions.copy(stop = listOf("END")),
            responsesOptions.copy(reasoningEffort = ReasoningEffortId("low")),
        ).forEach { options ->
            val error =
                assertFailsWith<LlmException> {
                    OpenAiResponsesOptionMapper().map(options, OpenAIModels.Chat.GPT4oMini)
                }
            assertEquals(im.hikaru.harness.llm.koog.KoogLlmErrorCode.UNSUPPORTED_OPTION, error.code)
        }
    }

    @Test
    fun reasoningModelShouldMapEffortAndRejectSamplingFieldsItCannotUse() {
        listOf(
            "low" to ReasoningEffort.LOW,
            "medium" to ReasoningEffort.MEDIUM,
            "high" to ReasoningEffort.HIGH,
        ).forEach { (effort, expected) ->
            val params =
                OpenAiChatOptionMapper().map(
                    options =
                        textOptions.copy(
                            model = OpenAIModels.Chat.O3Mini.id,
                            reasoningEffort = ReasoningEffortId(effort),
                        ),
                    model = OpenAIModels.Chat.O3Mini,
                )
            assertEquals(expected, assertIs<OpenAIChatParams>(params).reasoningEffort)
        }

        listOf(
            textOptions.copy(model = OpenAIModels.Chat.O3Mini.id, temperature = 0.3),
            textOptions.copy(model = OpenAIModels.Chat.O3Mini.id, stop = listOf("END")),
            textOptions.copy(model = OpenAIModels.Chat.O3Mini.id, reasoningEffort = ReasoningEffortId("none")),
            textOptions.copy(model = OpenAIModels.Chat.O3Mini.id, reasoningEffort = ReasoningEffortId("minimal")),
            textOptions.copy(model = OpenAIModels.Chat.O3Mini.id, reasoningEffort = ReasoningEffortId("unknown")),
        ).forEach { options ->
            val error =
                assertFailsWith<LlmException> {
                    OpenAiChatOptionMapper().map(options, OpenAIModels.Chat.O3Mini)
                }
            assertEquals(im.hikaru.harness.llm.koog.KoogLlmErrorCode.UNSUPPORTED_OPTION, error.code)
        }
    }

    @Test
    fun terminalPolicyShouldSelectOnlyOpenAiToolCallsFinish() {
        val policy = OpenAiChatToolCallTerminalPolicy()
        assertEquals(
            true,
            policy.shouldCompleteOpenToolCalls(
                frame = ai.koog.prompt.streaming.StreamFrame.End(finishReason = "tool_calls"),
                context = testContext(),
            ),
        )
        assertEquals(
            false,
            policy.shouldCompleteOpenToolCalls(
                frame = ai.koog.prompt.streaming.StreamFrame.End(finishReason = "stop"),
                context = testContext(),
            ),
        )
    }

    @Test
    fun finishAndUsageMappersShouldPreserveOpenAiTerminalSemantics() {
        val finish = OpenAiChatFinishReasonMapper()
        val context = testContext()
        assertEquals(im.hikaru.harness.llm.StopFinishReason, finish.map("stop", context))
        assertEquals(im.hikaru.harness.llm.MaxTokensFinishReason, finish.map("length", context))
        assertEquals(im.hikaru.harness.llm.ToolCallsFinishReason, finish.map("tool_calls", context))

        val usage =
            OpenAiChatUsageMapper().map(
                metaInfo =
                    ai.koog.prompt.message.ResponseMetaInfo.Empty.copy(
                        totalTokensCount = 12,
                        inputTokensCount = 8,
                        outputTokensCount = 4,
                    ),
                context = context,
            )
        assertEquals(TokenUsage(inputTokens = 8, outputTokens = 4), usage)

        val error =
            assertFailsWith<LlmException> {
                finish.map("content_filter", context)
            }
        assertEquals(im.hikaru.harness.llm.koog.KoogLlmErrorCode.UNSUPPORTED_FINISH_REASON, error.code)
    }

    @Test
    fun responsesFinishMapperShouldTreatCompletedResponseAsStop() {
        val finish = OpenAiResponsesFinishReasonMapper()
        val context =
            testContext().copy(api = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID)
        assertEquals(im.hikaru.harness.llm.StopFinishReason, finish.map(null, context))
        assertEquals(im.hikaru.harness.llm.MaxTokensFinishReason, finish.map("max_output_tokens", context))
        assertEquals(im.hikaru.harness.llm.ToolCallsFinishReason, finish.map("tool_calls", context))
    }

    @Test
    fun failureClassifierShouldUseStructuredHttpFields() {
        val classifier = OpenAiFailureClassifier()
        val context =
            KoogFailureContext(
                provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
                model = "gpt-4o-mini",
                api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
            )

        val rateLimit =
            classifier.classify(
                KoogHttpClientException(
                    clientName = "OpenAILLMClient",
                    statusCode = 429,
                    errorBody = """{"error":{"type":"rate_limit_error","message":"slow down"}}""",
                ),
                context,
            )
        assertEquals("RATE_LIMIT", rateLimit?.code)
        assertEquals(429, rateLimit?.status)
        assertEquals("slow down", rateLimit?.message)

        val quota =
            classifier.classify(
                KoogHttpClientException(
                    statusCode = 429,
                    errorBody = """{"error":{"code":"insufficient_quota","message":"quota exhausted"}}""",
                ),
                context,
            )
        assertEquals(LlmErrorCode.QUOTA_EXCEEDED, quota?.code)

        val contextWindow =
            classifier.classify(
                KoogHttpClientException(
                    statusCode = 400,
                    errorBody = """{"error":{"code":"context_length_exceeded","message":"too long"}}""",
                ),
                context,
            )
        assertEquals(LlmErrorCode.CONTEXT_WINDOW_EXCEEDED, contextWindow?.code)

        val credential =
            classifier.classify(
                KoogHttpClientException(
                    statusCode = 401,
                    errorBody = """{"error":{"code":"invalid_api_key","message":"bad key"}}""",
                ),
                context,
            )
        assertEquals(LlmErrorCode.INVALID_CREDENTIAL, credential?.code)

        val server =
            classifier.classify(
                KoogHttpClientException(statusCode = 503, errorBody = "{}"),
                context,
            )
        assertEquals("SERVER", server?.code)
        val gatewayTimeout =
            classifier.classify(
                KoogHttpClientException(statusCode = 504, errorBody = "{}"),
                context,
            )
        assertEquals("TIMEOUT", gatewayTimeout?.code)

        val nestedTimeout =
            classifier.classify(
                IllegalStateException("wrapper", IllegalStateException("inner", SocketTimeoutException("idle"))),
                context,
            )
        assertEquals("TIMEOUT", nestedTimeout?.code)

        val nestedTransport =
            classifier.classify(
                IllegalStateException("wrapper", IllegalStateException("inner", IOException("offline"))),
                context,
            )
        assertEquals("TRANSPORT", nestedTransport?.code)
        assertNull(classifier.classify(IllegalStateException("unknown"), context))
        assertNull(classifier.classify(IllegalStateException("unknown"), context.copy(api = "other")))
    }

    @Test
    fun environmentResolverShouldMapCredentialReferenceWithoutPersistingValue() = runTest {
        val resolver =
            EnvironmentKoogCredentialResolver { name ->
                assertEquals("OPENAI_API_KEY", name)
                "secret-value"
            }
        assertEquals(
            "secret-value",
            resolver.resolve(KoogCredentialRef("openai-api-key")),
        )
    }

    @Test
    fun factoryShouldResolveCredentialOnlyAtActivationAndCloseExecutor() = runTest {
        var calls = 0
        val factory = OpenAiPromptExecutorFactory()
        val settings = OpenAiKoogCatalog.defaultSettings(credentialName = "openai-api-key")
        val executor =
            factory.create(
                routes = settings.resolveRoutes(OpenAiKoogCatalog.installedRoutes()),
                settings = settings.providers.values.toList(),
                credentials = im.hikaru.harness.llm.koog.KoogCredentialResolver {
                    calls++
                    "test-secret"
                },
            )
        assertEquals(1, calls)
        executor.close()
    }

    @Test
    fun factoryShouldUseStableCredentialCodeWhenReferenceIsMissing() = runTest {
        val settings =
            im.hikaru.harness.llm.koog.KoogLlmSettings(
                mapOf(
                    OpenAiKoogCatalog.OPENAI_PROVIDER_ID to
                        KoogProviderSettings(
                            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
                            api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                        )
                )
            )
        val error =
            assertFailsWith<LlmException> {
                OpenAiPromptExecutorFactory().create(
                    routes = settings.resolveRoutes(OpenAiKoogCatalog.installedRoutes()),
                    settings = settings.providers.values.toList(),
                    credentials = im.hikaru.harness.llm.koog.KoogCredentialResolver { null },
                )
            }
        assertEquals(LlmErrorCode.INVALID_CREDENTIAL, error.code)
    }

    private fun testContext() =
        im.hikaru.harness.llm.koog.KoogStreamContext(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = "gpt-4o-mini",
            koogProvider = "OpenAI",
            api = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
        )
}

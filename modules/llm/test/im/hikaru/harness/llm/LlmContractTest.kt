package im.hikaru.harness.llm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmContractTest {

    private val json =
        Json {
            classDiscriminator = "type"
            encodeDefaults = true
        }

    @Test
    fun streamChunksShouldRoundTripWithoutProviderTypes() {
        val chunks: List<StreamChunk> =
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "hello"),
                BlockEndChunk(index = 0, block = TextBlock("hello")),
                UsageChunk(
                    TokenUsage(
                        inputTokens = 10,
                        outputTokens = 2,
                        cacheReadTokens = 3,
                    )
                ),
                FinishChunk(StopFinishReason),
            )

        val encoded = json.encodeToString(chunks)
        val restored = json.decodeFromString<List<StreamChunk>>(encoded)

        assertEquals(chunks, restored)
    }

    @Test
    fun callConfigShouldRoundTripWithOpaqueReasoningEffort() {
        val config =
            LlmCallConfig(
                provider = "provider",
                model = "model",
                reasoningEffort = ReasoningEffortId("provider-owned"),
                temperature = 0.2,
                maxTokens = 4_096,
                stop = listOf("END"),
            )

        val restored =
            json.decodeFromString<LlmCallConfig>(
                json.encodeToString(config)
            )

        assertEquals(config, restored)
    }

    @Test
    fun errorClassifiersShouldSeparateCapacityQuotaAndRateLimit() {
        assertTrue(
            isContextWindowExceededError(
                "input exceeds the model context window limit"
            )
        )
        assertFalse(
            isContextWindowExceededError(
                "temperature exceeds maximum allowed value"
            )
        )
        assertTrue(isQuotaExceededError("insufficient_quota"))
        assertFalse(isQuotaExceededError("HTTP 429: rate limit reached"))
    }

    @Test
    fun llmExceptionShouldExposeSerializableFailureFacts() {
        val error =
            LlmException(
                message = "busy",
                code = "RATE_LIMIT",
                status = 429,
                providerRetryAfterMs = 2_000,
                requestId = ProviderRequestId("request"),
            )

        assertEquals(
            LlmFailure(
                message = "busy",
                code = "RATE_LIMIT",
                status = 429,
                providerRetryAfterMs = 2_000,
                requestId = ProviderRequestId("request"),
            ),
            error.failure,
        )
    }
}

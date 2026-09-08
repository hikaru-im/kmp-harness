package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.ProviderRequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KoogFailureMapperTest {

    private val context =
        KoogFailureContext(
            provider = "test",
            model = "test-model",
        )

    @Test
    fun cancellationShouldPropagateWithoutBecomingProviderFailure() {
        val cancelled = CancellationException("cancelled")

        val thrown =
            assertFailsWith<CancellationException> {
                DefaultKoogFailureMapper().map(cancelled, context)
            }

        assertSame(cancelled, thrown)
    }

    @Test
    fun llmExceptionShouldKeepItsStableFailureFields() {
        val error =
            LlmException(
                message = "rate limited",
                code = "RATE_LIMIT",
                status = 429,
                providerRetryAfterMs = 2_000,
                requestId = ProviderRequestId("request-42"),
            )

        var classifierCalls = 0
        val mapper =
            DefaultKoogFailureMapper(
                KoogProviderFailureClassifier { _, _ ->
                    classifierCalls++
                    error("Stable LlmException must bypass Provider classification")
                }
            )

        assertEquals(error.failure, mapper.map(error, context))
        assertEquals(0, classifierCalls)
    }

    @Test
    fun unknownExceptionShouldUseCoreFallbackWithoutLosingMessage() {
        val failure =
            DefaultKoogFailureMapper().map(
                error = IllegalStateException("provider client failed"),
                context = context,
            )

        assertEquals(LlmErrorCode.UNKNOWN, failure.code)
        assertEquals("provider client failed", failure.message)
    }

    @Test
    fun recognizedProviderFailureShouldPreserveClassifierFacts() {
        val providerError = IllegalStateException("raw provider exception")
        val expected =
            LlmFailure(
                message = "rate limited",
                code = "RATE_LIMIT",
                status = 429,
                providerRetryAfterMs = 1_500,
                requestId = ProviderRequestId("request-7"),
            )
        val mapper =
            DefaultKoogFailureMapper(
                KoogProviderFailureClassifier { error, classifiedContext ->
                    assertEquals(context, classifiedContext)
                    if (error === providerError) expected else null
                }
            )

        assertEquals(expected, mapper.map(providerError, context))
    }

    @Test
    fun downstreamCollectorFailureShouldBypassProviderClassifier() = runTest {
        val downstreamError = IllegalStateException("downstream consumer failed")
        var classifierCalls = 0
        val mapper =
            DefaultKoogFailureMapper(
                KoogProviderFailureClassifier { _, _ ->
                    classifierCalls++
                    null
                }
            )

        val thrown =
            assertFailsWith<IllegalStateException> {
                flowOf("chunk")
                    .mapKoogFailures(mapper, context)
                    .collect {
                        throw downstreamError
                    }
            }

        assertSame(downstreamError, thrown)
        assertEquals(0, classifierCalls)
    }
}

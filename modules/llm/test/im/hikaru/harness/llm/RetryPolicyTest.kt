package im.hikaru.harness.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryPolicyTest {

    @Test
    fun defaultPolicyShouldMatchDshNormalMode() {
        val policy = defaultRetryPolicy() as NormalRetryPolicy

        assertEquals(2, policy.maxRetries)
        assertEquals(500, policy.initialDelayMs)
        assertEquals(10_000, policy.maxDelayMs)
        assertEquals(0.1, policy.jitterRatio)
        assertEquals(
            listOf(
                LlmErrorCode.EMPTY_RESPONSE,
                "RATE_LIMIT",
                "SERVER",
                "TIMEOUT",
                "TRANSPORT",
            ),
            policy.retryableCodes,
        )
    }

    @Test
    fun invalidBackoffAndDuplicateCodesShouldBeRejected() {
        assertFailsWith<IllegalArgumentException> {
            AlwaysRetryPolicy(
                initialDelayMs = 1_000,
                maxDelayMs = 500,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            NormalRetryPolicy(
                retryableCodes = listOf("SERVER", "SERVER"),
            )
        }
    }
}

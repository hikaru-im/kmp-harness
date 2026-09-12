package im.hikaru.harness.client.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReconnectPolicyTest {
    @Test
    fun delayGrowsExponentiallyAndIsCapped() {
        val policy = ReconnectPolicy(initialDelayMillis = 100, maxDelayMillis = 800, maxAttempts = 10)

        assertEquals(100L, policy.nextDelay(0))
        assertEquals(200L, policy.nextDelay(1))
        assertEquals(400L, policy.nextDelay(2))
        assertEquals(800L, policy.nextDelay(3))
        assertEquals(800L, policy.nextDelay(9))
    }

    @Test
    fun attemptBudgetIsBounded() {
        val policy = ReconnectPolicy(initialDelayMillis = 100, maxDelayMillis = 800, maxAttempts = 3)

        assertEquals(3, policy.attemptLimit)
        assertEquals(100L, policy.nextDelay(0))
        assertEquals(200L, policy.nextDelay(1))
        assertEquals(400L, policy.nextDelay(2))
        assertNull(policy.nextDelay(3))
    }

    @Test
    fun invalidAttemptsAreRejected() {
        val policy = ReconnectPolicy(initialDelayMillis = 10, maxDelayMillis = 20, maxAttempts = 2)

        assertEquals(10L, policy.nextDelay(0))
        assertNull(policy.nextDelay(-1))
    }
}
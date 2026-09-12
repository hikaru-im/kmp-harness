package im.hikaru.harness.client.connection

/**
 * Bounded exponential backoff for remote reconnect attempts.
 *
 * The policy never allows an unbounded reconnect loop: after [maxAttempts] consecutive failures it
 * stops, so a permanently unreachable backend stays observable instead of spinning forever.
 */
public class ReconnectPolicy(
    private val initialDelayMillis: Long = 500L,
    private val maxDelayMillis: Long = 15_000L,
    private val maxAttempts: Int = 6,
) {
    init {
        require(initialDelayMillis > 0L) { "Initial reconnect delay must be positive" }
        require(maxDelayMillis >= initialDelayMillis) { "Max reconnect delay must not be below the initial delay" }
        require(maxAttempts > 0) { "Reconnect attempt budget must be positive" }
    }

    /** Total consecutive attempts allowed before the caller surfaces a stable disconnected state. */
    public val attemptLimit: Int get() = maxAttempts

    /** Delay before attempt number [attempt] (zero-based), or null once the budget is exhausted. */
    public fun nextDelay(attempt: Int): Long? {
        if (attempt < 0 || attempt >= maxAttempts) return null
        var delay = initialDelayMillis
        repeat(attempt) {
            delay = if (delay >= maxDelayMillis / 2) maxDelayMillis else delay * 2
        }
        return delay.coerceAtMost(maxDelayMillis)
    }
}

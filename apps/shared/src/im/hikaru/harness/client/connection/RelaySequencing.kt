package im.hikaru.harness.client.connection

import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId

/** How one relay event relates to the sequence the client already observed for the same stream. */
public sealed interface SequenceOutcome {
    /** The event continues the stream and must be delivered to the caller. */
    public data object Deliver : SequenceOutcome

    /** The event was already delivered and must be dropped. */
    public data object Duplicate : SequenceOutcome

    /** The stream skipped events; the caller must reconcile through history before trusting it. */
    public data class Gap(
        public val expected: Long,
        public val received: Long,
    ) : SequenceOutcome
}

/**
 * Per-stream sequence bookkeeping for relay events.
 *
 * A stream without a known head is anchored by its first event. Afterwards duplicates are dropped and
 * gaps become observable instead of silently reordering client state.
 */
public class SequenceTracker {
    private val next = mutableMapOf<String, Long>()

    /** The next sequence expected for [streamId], or null while the stream head is unknown. */
    public fun expected(streamId: StreamId): Long? = next[streamId.value]

    public fun accept(event: RelayEvent): SequenceOutcome {
        val key = event.streamId.value
        val expected = next[key]
        return when {
            expected == null -> {
                next[key] = event.sequence + 1
                SequenceOutcome.Deliver
            }

            event.sequence < expected -> SequenceOutcome.Duplicate

            event.sequence > expected -> {
                next[key] = event.sequence + 1
                SequenceOutcome.Gap(expected = expected, received = event.sequence)
            }

            else -> {
                next[key] = expected + 1
                SequenceOutcome.Deliver
            }
        }
    }

    /** Continue [streamId] after a history reconciliation. */
    public fun align(streamId: StreamId, nextSequence: Long) {
        require(nextSequence >= 0L) { "Sequence must not be negative" }
        next[streamId.value] = nextSequence
    }

    /** Drop all bookkeeping for [streamId], for example after cancelling its subscription. */
    public fun forget(streamId: StreamId) {
        next.remove(streamId.value)
    }
}

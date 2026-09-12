package im.hikaru.harness.client.connection

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SequenceTrackerTest {
    private val stream = StreamId("session-1")

    private fun event(sequence: Long, streamId: StreamId = stream) =
        RelayEvent(
            eventId = EventId("event-" + sequence),
            hostId = HostId("desktop-local"),
            streamId = streamId,
            sequence = sequence,
            event = "assistant/chunk",
            payload = JsonPrimitive("payload"),
        )

    @Test
    fun firstEventAnchorsTheStream() {
        val tracker = SequenceTracker()

        assertNull(tracker.expected(stream))
        assertEquals(SequenceOutcome.Deliver, tracker.accept(event(7)))
        assertEquals(8L, tracker.expected(stream))
    }

    @Test
    fun duplicateSequenceIsDroppedWithoutMovingTheHead() {
        val tracker = SequenceTracker()
        tracker.accept(event(0))

        assertEquals(SequenceOutcome.Duplicate, tracker.accept(event(0)))
        assertEquals(1L, tracker.expected(stream))
    }

    @Test
    fun outOfOrderEventReportsAGap() {
        val tracker = SequenceTracker()
        tracker.accept(event(0))

        val outcome = tracker.accept(event(4))

        assertEquals(SequenceOutcome.Gap(expected = 1L, received = 4L), outcome)
        assertEquals(5L, tracker.expected(stream))
    }

    @Test
    fun streamsAreTrackedIndependently() {
        val tracker = SequenceTracker()
        tracker.accept(event(0))

        assertEquals(SequenceOutcome.Deliver, tracker.accept(event(0, StreamId("session-2"))))
        assertEquals(SequenceOutcome.Duplicate, tracker.accept(event(0)))
    }

    @Test
    fun alignContinuesAfterHistoryRecoveryAndForgetDropsTheStream() {
        val tracker = SequenceTracker()
        tracker.accept(event(0))

        tracker.align(stream, 9L)
        assertEquals(SequenceOutcome.Deliver, tracker.accept(event(9)))

        tracker.forget(stream)
        assertNull(tracker.expected(stream))
    }
}
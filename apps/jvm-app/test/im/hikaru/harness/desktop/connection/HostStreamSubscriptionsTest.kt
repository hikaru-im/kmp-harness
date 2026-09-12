package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostStreamSubscriptionsTest {
    private fun event(stream: String) =
        RelayEvent(
            eventId = EventId("event-" + stream),
            hostId = HostId("desktop-local"),
            streamId = StreamId(stream),
            sequence = 0L,
            event = "assistant/chunk",
            payload = JsonPrimitive("chunk"),
        )

    @Test
    fun onlySubscribedStreamsArePublished() {
        val subscriptions = HostStreamSubscriptions()

        assertFalse(subscriptions.accepts(event("session-1")))
        assertTrue(subscriptions.subscribe("session-1"))
        assertTrue(subscriptions.accepts(event("session-1")))
        assertFalse(subscriptions.accepts(event("session-2")))
    }

    @Test
    fun unsubscribeAndClearDropStreams() {
        val subscriptions = HostStreamSubscriptions()
        subscriptions.subscribe("session-1")
        subscriptions.subscribe("session-2")
        assertEquals(2, subscriptions.count())

        assertTrue(subscriptions.unsubscribe("session-1"))
        assertFalse(subscriptions.isSubscribed("session-1"))
        assertFalse(subscriptions.unsubscribe("session-1"))

        subscriptions.clear()
        assertEquals(0, subscriptions.count())
        assertFalse(subscriptions.accepts(event("session-2")))
    }
}
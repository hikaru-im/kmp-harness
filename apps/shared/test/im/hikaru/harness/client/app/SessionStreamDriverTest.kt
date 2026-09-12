package im.hikaru.harness.client.app

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.connection.HistoryRecovery
import im.hikaru.harness.client.connection.SessionId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionStreamDriverTest {
    private val selected = SessionId("session-1")

    @Test
    fun leavingTheSelectionReleasesTheSubscription() = runBlocking {
        val subscribed = mutableListOf<SessionId>()
        val unsubscribed = mutableListOf<SessionId>()
        val driver =
            SessionStreamDriver(
                events = emptyFlow(),
                recoveries = emptyFlow(),
                subscribe = { subscribed += it },
                unsubscribe = { unsubscribed += it },
                refresh = {},
            )

        val running = launch { driver.run(selected) }
        withTimeout(5_000) { while (subscribed.isEmpty()) delay(5) }

        running.cancel()
        running.join()

        assertEquals(listOf(selected), subscribed)
        assertEquals(listOf(selected), unsubscribed, "leaving the selection must release the subscription")
    }

    @Test
    fun forwardedEventsAndGapRecoveriesRefreshOnlyTheSelectedSession() = runBlocking {
        val events = MutableSharedFlow<RelayEvent>(replay = 8, extraBufferCapacity = 8)
        val recoveries = MutableSharedFlow<HistoryRecovery>(replay = 8, extraBufferCapacity = 8)
        events.emit(event("session-1", sequence = 0))
        events.emit(event("session-2", sequence = 0))
        recoveries.emit(HistoryRecovery(StreamId("session-1"), emptyList()))
        recoveries.emit(HistoryRecovery(StreamId("session-2"), emptyList()))

        val refreshed = mutableListOf<SessionId>()
        val seen = mutableListOf<RelayEvent>()
        val driver =
            SessionStreamDriver(
                events = events,
                recoveries = recoveries,
                subscribe = {},
                unsubscribe = {},
                refresh = { refreshed += it },
                onEvent = { seen += it },
            )

        val running = launch { driver.run(selected) }
        withTimeout(5_000) { while (refreshed.size < 2) delay(5) }
        delay(50)

        assertEquals(listOf("session-1"), seen.map { it.streamId.value })
        assertEquals(listOf(selected, selected), refreshed)

        running.cancel()
        running.join()
    }

    @Test
    fun replacingTheConnectionResubscribesAndKeepsReceivingEvents() = runBlocking {
        val events = MutableSharedFlow<RelayEvent>(replay = 4, extraBufferCapacity = 8)
        val recoveries = MutableSharedFlow<HistoryRecovery>(replay = 4, extraBufferCapacity = 8)
        val connections = MutableStateFlow("conn-1")
        val lifecycle = mutableListOf<String>()
        val seen = mutableListOf<RelayEvent>()
        val driver =
            SessionStreamDriver(
                events = events,
                recoveries = recoveries,
                subscribe = { lifecycle += "subscribe" },
                unsubscribe = { lifecycle += "unsubscribe" },
                refresh = {},
                onEvent = { seen += it },
                connectionChanges = connections,
            )

        val running = launch { driver.run(selected) }
        withTimeout(5_000) { while (lifecycle.size < 1) delay(5) }

        // A reconnect replaces the connection object; the driver must move the subscription with it.
        connections.value = "conn-2"
        withTimeout(5_000) { while (lifecycle.size < 3) delay(5) }
        assertEquals(
            listOf("subscribe", "unsubscribe", "subscribe"),
            lifecycle,
            "the previous connection must be released before the new one is subscribed",
        )

        events.emit(event("session-1", sequence = 5))
        withTimeout(5_000) { while (seen.isEmpty()) delay(5) }
        assertEquals(listOf("session-1"), seen.map { it.streamId.value })

        running.cancel()
        running.join()
        assertEquals("unsubscribe", lifecycle.last(), "leaving the selection must still release the subscription")
    }

    @Test
    fun aFailedHistoryRefreshDoesNotReleaseTheSubscription() = runBlocking {
        val events = MutableSharedFlow<RelayEvent>(replay = 4, extraBufferCapacity = 8)
        val recoveries = MutableSharedFlow<HistoryRecovery>(replay = 4, extraBufferCapacity = 8)
        val unsubscribed = mutableListOf<SessionId>()
        var refreshes = 0
        val driver =
            SessionStreamDriver(
                events = events,
                recoveries = recoveries,
                subscribe = {},
                unsubscribe = { unsubscribed += it },
                refresh = {
                    refreshes += 1
                    throw IllegalStateException("history is unavailable on a closed connection")
                },
            )

        val running = launch { driver.run(selected) }
        withTimeout(5_000) {
            while (refreshes < 1) {
                events.emit(event("session-1", sequence = refreshes.toLong()))
                delay(10)
            }
        }

        assertTrue(running.isActive, "a failed refresh must not end the driver")
        assertTrue(unsubscribed.isEmpty(), "a transient refresh failure must not release the subscription")

        running.cancel()
        running.join()
        assertEquals(listOf(selected), unsubscribed)
    }

    private fun event(stream: String, sequence: Long) =
        RelayEvent(
            eventId = EventId("event-" + stream + "-" + sequence),
            hostId = HostId("desktop-local"),
            streamId = StreamId(stream),
            sequence = sequence,
            event = "assistant/chunk",
            payload = JsonPrimitive("chunk"),
        )
}
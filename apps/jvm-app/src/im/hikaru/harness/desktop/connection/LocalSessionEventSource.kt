package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.SessionEventNotice
import im.hikaru.harness.session.SessionEvents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * The in-process event feed the desktop client subscribes to, shaped exactly like the Relay's.
 *
 * [HostEventStream] publishes to the RuoYi backend; this class serves the client that drives the same
 * Host through [LocalConnection], so both transports fold one event vocabulary. Only streams the
 * client selected leave the Host. The bounded buffer keeps Session appends from blocking; a dropped
 * chunk is corrected by the turn-end history reconciliation, never persisted.
 */
public class LocalSessionEventSource(
    context: Context,
    private val hostId: HostId,
    capacity: Int = 1024,
) : AutoCloseable {
    private val channel = Channel<RelayEvent>(capacity)
    private val subscribed = ConcurrentHashMap.newKeySet<String>()

    init {
        // The Host runtime Context owns this listener's disposal, so it is released when the host
        // shuts down rather than by this type.
        context.on(SessionEvents.Appended) { notice ->
            if (subscribed.contains(notice.session.id.value)) channel.trySend(toRelayEvent(notice))
        }
    }

    public val events: Flow<RelayEvent> = channel.receiveAsFlow()

    public fun subscribe(session: SessionId): Boolean = subscribed.add(session.value)

    public fun unsubscribe(session: SessionId): Boolean = subscribed.remove(session.value)

    /** Stops forwarding new events; the owning Host Context releases the listener on shutdown. */
    override fun close() {
        subscribed.clear()
        channel.close()
    }

    private fun toRelayEvent(notice: SessionEventNotice): RelayEvent =
        RelayEvent(
            eventId = EventId(notice.session.id.value + ":" + notice.event.seq),
            hostId = hostId,
            streamId = StreamId(notice.session.id.value),
            sequence = notice.event.seq,
            event = notice.event.type,
            payload = notice.event.data,
        )
}

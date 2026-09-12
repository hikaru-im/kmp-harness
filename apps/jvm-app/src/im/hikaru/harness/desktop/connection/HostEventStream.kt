package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.SessionEventNotice
import im.hikaru.harness.session.SessionEvents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Turns the local Harness Session event log into the ordered Relay events a RuoYi backend can forward.
 *
 * Only appended Session events leave the Host; Agent commands, profile and credentials stay
 * Host-owned. The bounded buffer keeps a slow Relay from blocking the Agent loop, and an event dropped
 * under backpressure is recovered by the client through a detected sequence gap plus history.
 */
public class HostEventStream(
    context: Context,
    private val hostId: HostId,
) : AutoCloseable {
    private val channel = Channel<RelayEvent>(capacity = 256)

    init {
        // The Host runtime Context owns this listener's disposal, so it is released when the host
        // shuts down rather than by this type.
        context.on(SessionEvents.Appended) { notice ->
            channel.trySend(toRelayEvent(notice))
        }
    }

    public val events: Flow<RelayEvent> = channel.receiveAsFlow()

    private fun toRelayEvent(notice: SessionEventNotice): RelayEvent =
        RelayEvent(
            eventId = EventId(notice.session.id.value + ":" + notice.event.seq),
            hostId = hostId,
            streamId = StreamId(notice.session.id.value),
            sequence = notice.event.seq,
            event = notice.event.type,
            payload = notice.event.data,
        )

    /** Stops forwarding new events; the owning Host Context releases the listener on shutdown. */
    override fun close() {
        channel.close()
    }
}

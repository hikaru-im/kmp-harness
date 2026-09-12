package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.relay.RelayEvent

/**
 * Local view of which Harness event streams this Desktop Host forwards to the RuoYi Relay.
 *
 * The Relay owns client-facing subscription isolation; the Host keeps its own view so it never
 * publishes a stream that no authenticated client asked for.
 */
public class HostStreamSubscriptions {
    private val subscribed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    public fun subscribe(streamId: String): Boolean = subscribed.add(streamId)

    public fun unsubscribe(streamId: String): Boolean = subscribed.remove(streamId)

    public fun isSubscribed(streamId: String): Boolean = subscribed.contains(streamId)

    public fun count(): Int = subscribed.size

    public fun clear() {
        subscribed.clear()
    }

    /** True when [event] belongs to a stream this Host is currently publishing. */
    public fun accepts(event: RelayEvent): Boolean = isSubscribed(event.streamId.value)
}
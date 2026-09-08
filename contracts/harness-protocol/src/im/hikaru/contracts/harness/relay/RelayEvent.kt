package im.hikaru.contracts.harness.relay

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Harness Host 经 RuoYi Backend Relay 下发的有序事件。 */
@Serializable
public data class RelayEvent(
    val eventId: EventId,
    val hostId: HostId,
    val streamId: StreamId,
    val sequence: Long,
    val event: String,
    val payload: JsonElement,
) {
    init {
        require(sequence >= 0) {
            "Relay event sequence must not be negative"
        }
        require(event.isNotBlank()) {
            "Relay event name must not be blank"
        }
    }
}

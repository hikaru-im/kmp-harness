package im.hikaru.contracts.harness.relay

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 经 RuoYi Backend Relay 发往指定 Harness Host 的请求内容。 */
@Serializable
public data class RelayRequest(
    val requestId: RequestId,
    val hostId: HostId,
    val correlationId: CorrelationId? = null,
    val method: String,
    val payload: JsonElement? = null,
) {
    init {
        require(method.isNotBlank()) {
            "Relay request method must not be blank"
        }
    }
}

package im.hikaru.contracts.harness.relay

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Harness Host 经 RuoYi Backend Relay 返回的请求结果。 */
@Serializable
public data class RelayResponse(
    val requestId: RequestId,
    val hostId: HostId,
    val correlationId: CorrelationId? = null,
    val result: ApiResult<JsonElement>,
) {
    init {
        require(result.code != null) {
            "Relay response result code must not be null"
        }
    }
}

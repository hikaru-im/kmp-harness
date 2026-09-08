package im.hikaru.contracts.harness.relay

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import kotlinx.serialization.Serializable

/** Desktop Host 主动向 RuoYi Relay 注册时发送的内容。 */
@Serializable
public data class HostRegistrationRequest(
    val host: HostDescription,
)

/** 一次有效 Host 连接的公开注册结果。 */
@Serializable
public data class HostRegistrationInfo(
    val hostId: HostId,
    val generation: Long,
) {
    init {
        require(generation > 0) {
            "Host registration generation must be positive"
        }
    }
}

/** RuoYi Relay 对 Host 注册请求的响应。 */
@Serializable
public data class HostRegistrationResponse(
    val result: ApiResult<HostRegistrationInfo>,
) {
    init {
        require(result.code != null) {
            "Host registration result code must not be null"
        }
    }
}

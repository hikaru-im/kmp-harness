package im.hikaru.contracts.harness.identity

import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.CapabilityId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.protocol.supportsCapability
import im.hikaru.contracts.harness.protocol.validateCapabilities
import kotlinx.serialization.Serializable

/**
 * 客户端在 Harness 握手时提交的描述。
 *
 * RuoYi 登录用户、租户和权限来自已认证连接，不能由本模型声明。
 */
@Serializable
public data class ClientDescription(
    val protocolVersion: ProtocolVersion,
    val clientId: ClientId,
    val displayName: String,
    val capabilities: List<Capability> = emptyList(),
) {
    init {
        require(displayName.isNotBlank()) {
            "Client display name must not be blank"
        }
        validateCapabilities(
            owner = "Client",
            capabilities = capabilities,
        )
    }

    /** 判断当前客户端是否支持 [id]，且能力版本不低于 [minimumVersion]。 */
    public fun supports(
        id: CapabilityId,
        minimumVersion: Int = 1,
    ): Boolean = capabilities.supportsCapability(id, minimumVersion)
}

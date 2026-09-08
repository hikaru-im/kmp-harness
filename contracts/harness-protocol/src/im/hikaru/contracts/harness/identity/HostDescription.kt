package im.hikaru.contracts.harness.identity

import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.CapabilityId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.protocol.supportsCapability
import im.hikaru.contracts.harness.protocol.validateCapabilities
import kotlinx.serialization.Serializable

/**
 * Harness Host 注册和握手时使用的元数据。
 *
 * [capabilities] 描述 Host 的协议能力，不代表当前 RuoYi 用户有权使用这些能力。
 */
@Serializable
public data class HostDescription(
    val protocolVersion: ProtocolVersion,
    val hostId: HostId,
    val displayName: String,
    val capabilities: List<Capability> = emptyList(),
) {
    init {
        require(displayName.isNotBlank()) {
            "Host display name must not be blank"
        }
        validateCapabilities(
            owner = "Host",
            capabilities = capabilities,
        )
    }

    /** 判断当前 Host 是否支持 [id]，且能力版本不低于 [minimumVersion]。 */
    public fun supports(
        id: CapabilityId,
        minimumVersion: Int = 1,
    ): Boolean = capabilities.supportsCapability(id, minimumVersion)
}

package im.hikaru.contracts.harness.handshake

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.CapabilityId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.protocol.supportsCapability
import im.hikaru.contracts.harness.protocol.validateCapabilities
import kotlinx.serialization.Serializable

/**
 * Harness 协议握手成功结果。
 *
 * HTTP 或 Relay 失败使用 RuoYi 的数字错误码与 `ApiResult`，不定义第二套 Harness 错误信封。
 */
@Serializable
public data class HandshakeResponse(
    val host: HostDescription,
    val negotiatedProtocolVersion: ProtocolVersion,
    val negotiatedCapabilities: List<Capability> = emptyList(),
) {
    init {
        require(negotiatedProtocolVersion.major == host.protocolVersion.major) {
            "Negotiated protocol major version must match host protocol major version"
        }
        require(negotiatedProtocolVersion.minor <= host.protocolVersion.minor) {
            "Negotiated protocol minor version must not exceed host protocol minor version"
        }
        validateCapabilities(
            owner = "Negotiated",
            capabilities = negotiatedCapabilities,
        )

        val hostCapabilities = host.capabilities.associateBy(Capability::id)
        negotiatedCapabilities.forEach { capability ->
            val hostCapability =
                requireNotNull(hostCapabilities[capability.id]) {
                    "Negotiated capability ${capability.id} is not supported by host"
                }
            require(capability.version <= hostCapability.version) {
                "Negotiated capability ${capability.id} version ${capability.version} " +
                    "exceeds host version ${hostCapability.version}"
            }
        }
    }

    /** 判断协商结果是否包含 [id]，且能力版本不低于 [minimumVersion]。 */
    public fun supports(
        id: CapabilityId,
        minimumVersion: Int = 1,
    ): Boolean = negotiatedCapabilities.supportsCapability(id, minimumVersion)
}

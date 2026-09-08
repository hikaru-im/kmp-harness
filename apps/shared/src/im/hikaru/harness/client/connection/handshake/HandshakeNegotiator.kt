package im.hikaru.harness.client.connection.handshake

import im.hikaru.contracts.harness.handshake.HandshakeRequest
import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.ProtocolVersion

/** 根据双方声明生成确定性的协议与能力协商结果。 */
public object HandshakeNegotiator {
    public fun negotiate(
        request: HandshakeRequest,
        host: HostDescription,
    ): HandshakeNegotiationResult {
        val client = request.client
        val clientProtocolVersion = client.protocolVersion
        val hostProtocolVersion = host.protocolVersion

        if (clientProtocolVersion.major != hostProtocolVersion.major) {
            return HandshakeNegotiationResult.Rejected(
                HandshakeRejection(
                    reason = HandshakeRejectionReason.IncompatibleProtocolVersion,
                    message =
                        "Client protocol major version ${clientProtocolVersion.major} " +
                            "is incompatible with host protocol major version " +
                            hostProtocolVersion.major,
                    clientProtocolVersion = clientProtocolVersion,
                    hostProtocolVersion = hostProtocolVersion,
                ),
            )
        }

        val clientCapabilities = client.capabilities.associateBy(Capability::id)
        val negotiatedCapabilities =
            host.capabilities.mapNotNull { hostCapability ->
                val clientCapability = clientCapabilities[hostCapability.id]
                    ?: return@mapNotNull null
                Capability(
                    id = hostCapability.id,
                    version = minOf(hostCapability.version, clientCapability.version),
                )
            }

        return HandshakeNegotiationResult.Accepted(
            HandshakeResponse(
                host = host,
                negotiatedProtocolVersion =
                    ProtocolVersion(
                        major = hostProtocolVersion.major,
                        minor = minOf(hostProtocolVersion.minor, clientProtocolVersion.minor),
                    ),
                negotiatedCapabilities = negotiatedCapabilities,
            ),
        )
    }
}

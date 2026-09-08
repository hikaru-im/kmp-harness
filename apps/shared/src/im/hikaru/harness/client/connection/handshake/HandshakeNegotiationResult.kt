package im.hikaru.harness.client.connection.handshake

import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.protocol.ProtocolVersion

/** 协商器返回的本地结果；连接实现负责把它映射为成功响应或失败响应。 */
public sealed interface HandshakeNegotiationResult {
    public data class Accepted(
        val response: HandshakeResponse,
    ) : HandshakeNegotiationResult

    public data class Rejected(
        val rejection: HandshakeRejection,
    ) : HandshakeNegotiationResult
}

/** 握手失败的本地策略结果，由 RuoYi 接入层映射为模块数字错误码。 */
public data class HandshakeRejection(
    val reason: HandshakeRejectionReason,
    val message: String,
    val clientProtocolVersion: ProtocolVersion,
    val hostProtocolVersion: ProtocolVersion,
)

public enum class HandshakeRejectionReason {
    IncompatibleProtocolVersion,
}

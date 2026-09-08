package im.hikaru.contracts.harness.handshake

import im.hikaru.contracts.harness.identity.ClientDescription
import kotlinx.serialization.Serializable

/** 客户端针对一个已授权 Host 发起的 Harness 协议握手。 */
@Serializable
public data class HandshakeRequest(
    val client: ClientDescription,
)

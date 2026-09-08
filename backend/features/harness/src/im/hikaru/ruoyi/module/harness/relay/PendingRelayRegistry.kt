package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.relay.RelayResponse

/** Relay requestId 与发起客户端之间的短期相关性注册表。 */
interface PendingRelayRegistry {
    fun register(request: PendingRelayRequest): Boolean

    fun complete(connection: HostConnection, response: RelayResponse): PendingRelayRequest?

    fun removeByHost(connection: HostConnection): List<PendingRelayRequest>

    fun removeByClientSession(sessionId: String): List<PendingRelayRequest>
}

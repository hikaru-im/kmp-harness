package im.hikaru.harness.client.connection

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.client.account.MemberIdentity

public enum class ConnectionTransport(public val label: String) { Local("本机"), Relay("Relay") }

public enum class ConnectionsPhase(public val label: String) {
    Idle("未连接"), Discovering("发现中"), Connecting("连接中"), Online("在线"),
    Reconnecting("重新连接中"), Disconnected("已断开"), Incompatible("协议不兼容"), RequiresLogin("需要登录"),
}

/** All host objects come from describe/discovery; absence never produces example hosts. */
public data class ConnectionsState(
    val scope: MemberIdentity? = null,
    val transport: ConnectionTransport = ConnectionTransport.Local,
    val phase: ConnectionsPhase = ConnectionsPhase.Idle,
    val currentHost: HostDescription? = null,
    val hosts: List<HostDescription> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshUnavailableReason: String? = "当前没有可刷新的连接。",
    val selectionUnavailableReason: String? = "本机模式不支持切换远程 Host。",
) {
    val busy: Boolean get() = refreshing || phase in listOf(ConnectionsPhase.Discovering, ConnectionsPhase.Connecting, ConnectionsPhase.Reconnecting)
    val canRefresh: Boolean get() = !refreshing && refreshUnavailableReason == null
    val canSelect: Boolean get() = !busy && selectionUnavailableReason == null
}

package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.identity.HostDescription

/** 一个由认证 WebSocket Session 拥有的在线 Harness Host。 */
data class HostConnection(
    val principal: HarnessPrincipal,
    val sessionId: String,
    val generation: Long,
    val description: HostDescription,
)

/** Host 注册完成后的状态变化。 */
data class HostRegistration(
    val connection: HostConnection,
    val displacedConnections: List<HostConnection>,
)

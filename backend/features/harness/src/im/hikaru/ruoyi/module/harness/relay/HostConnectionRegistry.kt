package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId

/** 当前后端实例中的 Harness Host 连接注册表。 */
interface HostConnectionRegistry {
    fun register(
        principal: HarnessPrincipal,
        sessionId: String,
        description: HostDescription,
    ): HostRegistration

    fun find(principal: HarnessPrincipal, hostId: HostId): HostConnection?

    fun findBySession(sessionId: String): HostConnection?

    fun findAll(principal: HarnessPrincipal): List<HostConnection>

    fun unregister(sessionId: String): HostConnection?
}

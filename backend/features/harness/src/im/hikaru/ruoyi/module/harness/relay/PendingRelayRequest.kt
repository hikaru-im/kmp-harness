package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.relay.RelayRequest

/** 已转发给特定 Host generation、等待响应的请求。 */
data class PendingRelayRequest(
    val principal: HarnessPrincipal,
    val request: RelayRequest,
    val clientSessionId: String,
    val hostSessionId: String,
    val hostGeneration: Long,
)

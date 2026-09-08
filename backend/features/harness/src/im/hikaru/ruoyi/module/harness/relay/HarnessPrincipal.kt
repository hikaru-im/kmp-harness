package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import org.springframework.web.socket.WebSocketSession

/**
 * 从 RuoYi 已认证 WebSocket Session 提取的 Relay 身份。
 *
 * Harness payload 不允许覆盖这些字段。
 */
data class HarnessPrincipal(
    val tenantId: Long,
    val userType: Int,
    val userId: Long,
) {
    companion object {
        fun from(session: WebSocketSession): HarnessPrincipal? {
            val loginUser = WebSocketFrameworkUtils.getLoginUser(session) ?: return null
            return HarnessPrincipal(
                tenantId = loginUser.tenantId ?: return null,
                userType = loginUser.userType ?: return null,
                userId = loginUser.id ?: return null,
            )
        }
    }
}

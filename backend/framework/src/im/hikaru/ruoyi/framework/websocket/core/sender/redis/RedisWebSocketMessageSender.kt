package im.hikaru.ruoyi.framework.websocket.core.sender.redis

import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.sender.AbstractWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager

class RedisWebSocketMessageSender(
    sessionManager: WebSocketSessionManager,
    private val redisMQTemplate: RedisMQTemplate,
) : AbstractWebSocketMessageSender(sessionManager) {

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) =
        sendRedisMessage(null, userId, userType, messageType, messageContent)

    override fun send(userType: Int?, messageType: String, messageContent: String) =
        sendRedisMessage(null, null, userType, messageType, messageContent)

    override fun send(sessionId: String, messageType: String, messageContent: String) =
        sendRedisMessage(sessionId, null, null, messageType, messageContent)

    private fun sendRedisMessage(
        sessionId: String?, userId: Long?, userType: Int?,
        messageType: String, messageContent: String,
    ) {
        val mqMessage = RedisWebSocketMessage().apply {
            tenantId = TenantContextHolder.getTenantId().takeUnless { TenantContextHolder.isIgnore() }
            this.sessionId = sessionId
            this.userId = userId
            this.userType = userType
            this.messageType = messageType
            this.messageContent = messageContent
        }
        redisMQTemplate.send(mqMessage)
    }
}

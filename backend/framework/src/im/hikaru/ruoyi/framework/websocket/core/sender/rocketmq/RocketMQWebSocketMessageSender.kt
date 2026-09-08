package im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.sender.AbstractWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import org.apache.rocketmq.spring.core.RocketMQTemplate

class RocketMQWebSocketMessageSender(
    sessionManager: WebSocketSessionManager,
    private val rocketMQTemplate: RocketMQTemplate,
    private val topic: String,
) : AbstractWebSocketMessageSender(sessionManager) {

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) =
        sendRocketMQMessage(null, userId, userType, messageType, messageContent)

    override fun send(userType: Int?, messageType: String, messageContent: String) =
        sendRocketMQMessage(null, null, userType, messageType, messageContent)

    override fun send(sessionId: String, messageType: String, messageContent: String) =
        sendRocketMQMessage(sessionId, null, null, messageType, messageContent)

    private fun sendRocketMQMessage(
        sessionId: String?, userId: Long?, userType: Int?,
        messageType: String, messageContent: String,
    ) {
        val message = RocketMQWebSocketMessage().apply {
            tenantId = TenantContextHolder.getTenantId().takeUnless { TenantContextHolder.isIgnore() }
            this.sessionId = sessionId
            this.userId = userId
            this.userType = userType
            this.messageType = messageType
            this.messageContent = messageContent
        }
        rocketMQTemplate.syncSend(topic, message)
    }
}

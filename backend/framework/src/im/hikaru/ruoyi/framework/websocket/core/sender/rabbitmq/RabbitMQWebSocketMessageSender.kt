package im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.sender.AbstractWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate

class RabbitMQWebSocketMessageSender(
    sessionManager: WebSocketSessionManager,
    private val rabbitTemplate: RabbitTemplate,
    private val topicExchange: TopicExchange,
) : AbstractWebSocketMessageSender(sessionManager) {

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) =
        sendRabbitMQMessage(null, userId, userType, messageType, messageContent)

    override fun send(userType: Int?, messageType: String, messageContent: String) =
        sendRabbitMQMessage(null, null, userType, messageType, messageContent)

    override fun send(sessionId: String, messageType: String, messageContent: String) =
        sendRabbitMQMessage(sessionId, null, null, messageType, messageContent)

    private fun sendRabbitMQMessage(
        sessionId: String?, userId: Long?, userType: Int?,
        messageType: String, messageContent: String,
    ) {
        val message = RabbitMQWebSocketMessage().apply {
            tenantId = TenantContextHolder.getTenantId().takeUnless { TenantContextHolder.isIgnore() }
            this.sessionId = sessionId
            this.userId = userId
            this.userType = userType
            this.messageType = messageType
            this.messageContent = messageContent
        }
        rabbitTemplate.convertAndSend(topicExchange.name, null, message)
    }
}

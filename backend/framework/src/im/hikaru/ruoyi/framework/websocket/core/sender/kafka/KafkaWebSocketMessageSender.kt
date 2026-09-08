package im.hikaru.ruoyi.framework.websocket.core.sender.kafka

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.sender.AbstractWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.ExecutionException

class KafkaWebSocketMessageSender(
    sessionManager: WebSocketSessionManager,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val topic: String,
) : AbstractWebSocketMessageSender(sessionManager) {

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) =
        sendKafkaMessage(null, userId, userType, messageType, messageContent)

    override fun send(userType: Int?, messageType: String, messageContent: String) =
        sendKafkaMessage(null, null, userType, messageType, messageContent)

    override fun send(sessionId: String, messageType: String, messageContent: String) =
        sendKafkaMessage(sessionId, null, null, messageType, messageContent)

    private fun sendKafkaMessage(
        sessionId: String?, userId: Long?, userType: Int?,
        messageType: String, messageContent: String,
    ) {
        val message = KafkaWebSocketMessage().apply {
            tenantId = TenantContextHolder.getTenantId().takeUnless { TenantContextHolder.isIgnore() }
            this.sessionId = sessionId
            this.userId = userId
            this.userType = userType
            this.messageType = messageType
            this.messageContent = messageContent
        }
        try {
            kafkaTemplate.send(topic, message).get()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("[sendKafkaMessage][failed to send message({}) to Kafka]", message, ex)
        } catch (ex: ExecutionException) {
            logger.error("[sendKafkaMessage][failed to send message({}) to Kafka]", message, ex)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(KafkaWebSocketMessageSender::class.java)
    }
}

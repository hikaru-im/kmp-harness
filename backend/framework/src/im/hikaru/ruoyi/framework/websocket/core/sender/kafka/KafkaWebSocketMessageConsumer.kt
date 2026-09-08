package im.hikaru.ruoyi.framework.websocket.core.sender.kafka

import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import org.springframework.kafka.annotation.KafkaListener

class KafkaWebSocketMessageConsumer(
    private val sender: KafkaWebSocketMessageSender,
) {

    @KafkaListener(
        topics = ["\${yudao.websocket.sender-kafka.topic}"],
        groupId = "\${yudao.websocket.sender-kafka.consumer-group}-#{T(java.util.UUID).randomUUID()}",
    )
    fun onMessage(message: KafkaWebSocketMessage) {
        val deliver = Runnable {
            sender.send(
                message.sessionId,
                message.userType,
                message.userId,
                requireNotNull(message.messageType),
                requireNotNull(message.messageContent),
            )
        }
        message.tenantId?.let { TenantUtils.execute(it, deliver) } ?: deliver.run()
    }
}

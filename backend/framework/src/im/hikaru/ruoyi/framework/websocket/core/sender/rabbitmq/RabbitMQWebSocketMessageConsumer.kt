package im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq

import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener

@RabbitListener(
    bindings = [
        QueueBinding(
            value = Queue(
                name = "\${yudao.websocket.sender-rabbitmq.queue}-#{T(java.util.UUID).randomUUID()}",
                autoDelete = "true",
            ),
            exchange = Exchange(
                name = "\${yudao.websocket.sender-rabbitmq.exchange}",
                type = ExchangeTypes.TOPIC,
                declare = "false",
            ),
        ),
    ],
)
class RabbitMQWebSocketMessageConsumer(
    private val sender: RabbitMQWebSocketMessageSender,
) {

    @RabbitHandler
    fun onMessage(message: RabbitMQWebSocketMessage) {
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

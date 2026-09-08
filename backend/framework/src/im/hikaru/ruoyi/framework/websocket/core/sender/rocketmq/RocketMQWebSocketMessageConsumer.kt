package im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq

import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import org.apache.rocketmq.spring.annotation.MessageModel
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener
import org.apache.rocketmq.spring.core.RocketMQListener

@RocketMQMessageListener(
    topic = "\${yudao.websocket.sender-rocketmq.topic}",
    consumerGroup = "\${yudao.websocket.sender-rocketmq.consumer-group}",
    messageModel = MessageModel.BROADCASTING,
)
class RocketMQWebSocketMessageConsumer(
    private val sender: RocketMQWebSocketMessageSender,
) : RocketMQListener<RocketMQWebSocketMessage> {

    override fun onMessage(message: RocketMQWebSocketMessage) {
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

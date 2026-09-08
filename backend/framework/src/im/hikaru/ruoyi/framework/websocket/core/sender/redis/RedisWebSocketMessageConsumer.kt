package im.hikaru.ruoyi.framework.websocket.core.sender.redis

import im.hikaru.ruoyi.framework.mq.redis.core.pubsub.AbstractRedisChannelMessageListener
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils

class RedisWebSocketMessageConsumer(
    private val redisWebSocketMessageSender: RedisWebSocketMessageSender,
) : AbstractRedisChannelMessageListener<RedisWebSocketMessage>() {

    override fun onMessage(message: RedisWebSocketMessage) {
        val deliver = Runnable {
            redisWebSocketMessageSender.send(
                message.sessionId, message.userType, message.userId,
                message.messageType!!, message.messageContent!!,
            )
        }
        message.tenantId?.let { TenantUtils.execute(it, deliver) } ?: deliver.run()
    }
}

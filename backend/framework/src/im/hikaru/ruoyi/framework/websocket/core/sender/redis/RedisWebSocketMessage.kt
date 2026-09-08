package im.hikaru.ruoyi.framework.websocket.core.sender.redis

import im.hikaru.ruoyi.framework.mq.redis.core.pubsub.AbstractRedisChannelMessage

class RedisWebSocketMessage : AbstractRedisChannelMessage() {
    var tenantId: Long? = null
    var sessionId: String? = null
    var userType: Int? = null
    var userId: Long? = null
    var messageType: String? = null
    var messageContent: String? = null
}

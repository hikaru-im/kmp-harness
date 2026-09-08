package im.hikaru.ruoyi.framework.websocket.core.sender.rocketmq

class RocketMQWebSocketMessage {
    var tenantId: Long? = null
    var sessionId: String? = null
    var userType: Int? = null
    var userId: Long? = null
    var messageType: String? = null
    var messageContent: String? = null
}

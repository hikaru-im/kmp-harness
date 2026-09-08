package im.hikaru.ruoyi.framework.websocket.core.sender.rabbitmq

import java.io.Serializable

class RabbitMQWebSocketMessage : Serializable {
    var tenantId: Long? = null
    var sessionId: String? = null
    var userType: Int? = null
    var userId: Long? = null
    var messageType: String? = null
    var messageContent: String? = null
}

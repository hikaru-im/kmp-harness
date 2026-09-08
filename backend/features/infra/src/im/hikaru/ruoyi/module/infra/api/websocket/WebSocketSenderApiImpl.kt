package im.hikaru.ruoyi.module.infra.api.websocket

import im.hikaru.ruoyi.framework.websocket.core.sender.WebSocketMessageSender
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * WebSocket 发送器的 API 实现类 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Component
class WebSocketSenderApiImpl : WebSocketSenderApi {

    @Autowired(required = false) // 由于 yudao.websocket.enable 配置项，可以关闭 WebSocket 的功能，所以这里只能不强制注入
    private var webSocketMessageSender: WebSocketMessageSender? = null

    override fun send(userType: Int, userId: Long, messageType: String, messageContent: String) {
        webSocketMessageSender?.send(userType, userId, messageType, messageContent)
    }

    override fun send(userType: Int, messageType: String, messageContent: String) {
        webSocketMessageSender?.send(userType, messageType, messageContent)
    }

    override fun send(sessionId: String, messageType: String, messageContent: String) {
        webSocketMessageSender?.send(sessionId, messageType, messageContent)
    }
}

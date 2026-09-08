package im.hikaru.ruoyi.module.infra.websocket

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketMessageListener
import im.hikaru.ruoyi.framework.websocket.core.sender.WebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import im.hikaru.ruoyi.module.infra.websocket.message.DemoReceiveMessage
import im.hikaru.ruoyi.module.infra.websocket.message.DemoSendMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/** Demo WebSocket listener supporting direct and broadcast messages. */
@Component
class DemoWebSocketMessageListener : WebSocketMessageListener<DemoSendMessage> {

    @Autowired(required = false)
    private var webSocketMessageSender: WebSocketMessageSender? = null

    override fun onMessage(session: WebSocketSession, message: DemoSendMessage) {
        val fromUserId = WebSocketFrameworkUtils.getLoginUserId(session)
        val sender = webSocketMessageSender ?: return
        if (message.toUserId != null) {
            sender.sendObject(
                UserTypeEnum.ADMIN.value,
                message.toUserId,
                "demo-message-receive",
                DemoReceiveMessage().apply {
                    this.fromUserId = fromUserId
                    text = message.text
                    single = true
                },
            )
            return
        }
        sender.sendObject(
            UserTypeEnum.ADMIN.value,
            "demo-message-receive",
            DemoReceiveMessage().apply {
                this.fromUserId = fromUserId
                text = message.text
                single = false
            },
        )
    }

    override fun getType(): String = "demo-message-send"
}

package im.hikaru.ruoyi.framework.websocket.core.listener

import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession

/** WebSocket 连接建立和关闭时的扩展回调。 */
interface WebSocketSessionLifecycleListener {
    fun afterConnectionEstablished(session: WebSocketSession) = Unit

    fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) = Unit
}

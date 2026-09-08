package im.hikaru.ruoyi.framework.websocket.core.listener

import org.springframework.web.socket.WebSocketSession

interface WebSocketMessageListener<T> {
    fun onMessage(session: WebSocketSession, message: T)
    fun getType(): String
}

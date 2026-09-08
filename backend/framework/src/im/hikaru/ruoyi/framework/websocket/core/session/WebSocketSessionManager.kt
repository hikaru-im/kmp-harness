package im.hikaru.ruoyi.framework.websocket.core.session

import org.springframework.web.socket.WebSocketSession

interface WebSocketSessionManager {
    fun addSession(session: WebSocketSession)
    fun removeSession(session: WebSocketSession)
    fun getSession(id: String): WebSocketSession?
    fun getSessionList(userType: Int?): Collection<WebSocketSession>
    fun getSessionList(userType: Int?, userId: Long?): Collection<WebSocketSession>
}

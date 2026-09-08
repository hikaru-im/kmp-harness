package im.hikaru.ruoyi.framework.websocket.core.session

import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketSessionLifecycleListener
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.WebSocketHandlerDecorator

class WebSocketSessionHandlerDecorator(
    delegate: WebSocketHandler,
    private val sessionManager: WebSocketSessionManager,
    private val lifecycleListeners: List<WebSocketSessionLifecycleListener> = emptyList(),
) : WebSocketHandlerDecorator(delegate) {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val managedSession =
            if (session is ConcurrentWebSocketSessionDecorator) {
                session
            } else {
                ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT)
            }
        sessionManager.addSession(managedSession)
        notifyListeners("建立", managedSession) { listener ->
            listener.afterConnectionEstablished(managedSession)
        }
        super.afterConnectionEstablished(managedSession)
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        try {
            super.afterConnectionClosed(session, closeStatus)
        } finally {
            sessionManager.removeSession(session)
            notifyListeners("关闭", session) { listener ->
                listener.afterConnectionClosed(session, closeStatus)
            }
        }
    }

    private fun notifyListeners(
        action: String,
        session: WebSocketSession,
        callback: (WebSocketSessionLifecycleListener) -> Unit,
    ) {
        lifecycleListeners.forEach { listener ->
            try {
                callback(listener)
            } catch (ex: Throwable) {
                log.error("[notifyListeners][连接{}回调失败，session({})]", action, session.id, ex)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WebSocketSessionHandlerDecorator::class.java)
        private const val SEND_TIME_LIMIT = 1000 * 5
        private const val BUFFER_SIZE_LIMIT = 1024 * 100
    }
}

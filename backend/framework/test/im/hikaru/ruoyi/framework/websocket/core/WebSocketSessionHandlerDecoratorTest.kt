package im.hikaru.ruoyi.framework.websocket.core

import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketSessionLifecycleListener
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionHandlerDecorator
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession

class WebSocketSessionHandlerDecoratorTest {
    @Test
    fun `connection close removes session and notifies every lifecycle listener`() {
        val delegate = mock(WebSocketHandler::class.java)
        val sessionManager = mock(WebSocketSessionManager::class.java)
        val session = mock(WebSocketSession::class.java)
        `when`(session.id).thenReturn("session-1")
        val callbacks = mutableListOf<String>()
        val failing =
            object : WebSocketSessionLifecycleListener {
                override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
                    callbacks += "failing"
                    error("预期的测试异常")
                }
            }
        val succeeding =
            object : WebSocketSessionLifecycleListener {
                override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
                    callbacks += "succeeding"
                }
            }
        val decorator =
            WebSocketSessionHandlerDecorator(
                delegate = delegate,
                sessionManager = sessionManager,
                lifecycleListeners = listOf(failing, succeeding),
            )

        decorator.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(delegate).afterConnectionClosed(session, CloseStatus.NORMAL)
        verify(sessionManager).removeSession(session)
        assertEquals(listOf("failing", "succeeding"), callbacks)
    }
}

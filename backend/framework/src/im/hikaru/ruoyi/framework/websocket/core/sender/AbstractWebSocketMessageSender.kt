package im.hikaru.ruoyi.framework.websocket.core.sender

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.websocket.core.message.JsonWebSocketMessage
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

abstract class AbstractWebSocketMessageSender(
    private val sessionManager: WebSocketSessionManager,
) : WebSocketMessageSender {

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) =
        send(null, userType, userId, messageType, messageContent)

    override fun send(userType: Int?, messageType: String, messageContent: String) =
        send(null, userType, null, messageType, messageContent)

    override fun send(sessionId: String, messageType: String, messageContent: String) =
        send(sessionId, null, null, messageType, messageContent)

    fun send(
        sessionId: String?, userType: Int?, userId: Long?,
        messageType: String, messageContent: String,
    ) {
        var sessions: List<WebSocketSession> = emptyList()
        when {
            !sessionId.isNullOrEmpty() ->
                sessionManager.getSession(sessionId)?.let { sessions = listOf(it) }
            userType != null && userId != null ->
                sessions = sessionManager.getSessionList(userType, userId).toList()
            userType != null ->
                sessions = sessionManager.getSessionList(userType).toList()
        }
        if (sessions.isEmpty() && log.isDebugEnabled) {
            log.debug(
                "[send][sessionId({}) userType({}) userId({}) messageType({}) messageContent({}) 未匹配到会话]",
                sessionId, userType, userId, messageType, messageContent,
            )
        }
        doSend(sessions, messageType, messageContent)
    }

    private fun doSend(sessions: Collection<WebSocketSession>, messageType: String, messageContent: String) {
        val message = JsonWebSocketMessage(type = messageType, content = messageContent)
        val payload = JsonUtils.toJsonString(message)
        sessions.forEach { session ->
            if (!session.isOpen) {
                log.error("[doSend][session({}) 已关闭, message({})]", session.id, message)
                return@forEach
            }
            try {
                session.sendMessage(TextMessage(payload))
                log.info("[doSend][session({}) 发送消息成功，message({})]", session.id, message)
            } catch (ex: java.io.IOException) {
                log.error("[doSend][session({}) 发送消息失败，message({})]", session.id, message, ex)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AbstractWebSocketMessageSender::class.java)
    }
}

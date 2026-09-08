package im.hikaru.ruoyi.framework.websocket.core.handler

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.framework.websocket.core.listener.RawWebSocketMessageListener
import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketMessageListener
import im.hikaru.ruoyi.framework.websocket.core.message.JsonWebSocketMessage
import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import org.slf4j.LoggerFactory
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class JsonWebSocketMessageHandler(
    listenersList: List<WebSocketMessageListener<*>>,
) : TextWebSocketHandler() {

    private val listeners: MutableMap<String, WebSocketMessageListener<*>> = HashMap()

    init {
        listenersList.forEach { listener -> listeners[listener.getType()] = listener }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // 1.1 空消息
        if (message.payloadLength == 0) return
        // 1.2 ping 心跳
        if (message.payloadLength == 4 && message.payload == "ping") {
            session.sendMessage(TextMessage("pong"))
            return
        }
        try {
            val jsonMessage = JsonUtils.parseObject(message.payload, JsonWebSocketMessage::class.java)
            if (jsonMessage == null) {
                log.error("[handleTextMessage][session({}) message({}) 解析为空]", session.id, message.payload)
                return
            }
            if (jsonMessage.type.isNullOrEmpty()) {
                log.error("[handleTextMessage][session({}) message({}) 类型为空]", session.id, message.payload)
                return
            }
            @Suppress("UNCHECKED_CAST")
            val messageListener = listeners[jsonMessage.type] as? WebSocketMessageListener<Any> ?: run {
                log.error("[handleTextMessage][session({}) message({}) 监听器为空]", session.id, message.payload)
                return
            }
            TenantUtils.execute(WebSocketFrameworkUtils.getTenantId(session), Runnable {
                if (messageListener is RawWebSocketMessageListener) {
                    messageListener.onMessage(session, jsonMessage.content.orEmpty())
                    return@Runnable
                }
                val type = resolveMessageType(messageListener)
                val messageObj: Any? = JsonUtils.parseObject(jsonMessage.content, type)
                @Suppress("UNCHECKED_CAST")
                messageListener.onMessage(session, messageObj as Any)
            })
        } catch (ex: Throwable) {
            log.error("[handleTextMessage][session({}) message({}) 处理异常]", session.id, message.payload, ex)
        }
    }

    private fun resolveMessageType(listener: WebSocketMessageListener<*>): Type {
        var clazz: Class<*> = listener.javaClass
        while (clazz != Any::class.java) {
            val genericSuper = clazz.genericSuperclass
            if (genericSuper is ParameterizedType &&
                genericSuper.rawType == WebSocketMessageListener::class.java
            ) {
                return genericSuper.actualTypeArguments[0]
            }
            for (gi in clazz.genericInterfaces) {
                if (gi is ParameterizedType && gi.rawType == WebSocketMessageListener::class.java) {
                    return gi.actualTypeArguments[0]
                }
            }
            clazz = clazz.superclass
        }
        return Any::class.java
    }

    companion object {
        private val log = LoggerFactory.getLogger(JsonWebSocketMessageHandler::class.java)
    }
}

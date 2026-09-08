package im.hikaru.ruoyi.framework.websocket.core.sender

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils

interface WebSocketMessageSender {
    fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String)
    fun send(userType: Int?, messageType: String, messageContent: String)
    fun send(sessionId: String, messageType: String, messageContent: String)

    fun sendObject(userType: Int?, userId: Long?, messageType: String, messageContent: Any?) {
        send(userType, userId, messageType, JsonUtils.toJsonString(messageContent))
    }

    fun sendObject(userType: Int?, messageType: String, messageContent: Any?) {
        send(userType, messageType, JsonUtils.toJsonString(messageContent))
    }

    fun sendObject(sessionId: String, messageType: String, messageContent: Any?) {
        send(sessionId, messageType, JsonUtils.toJsonString(messageContent))
    }
}

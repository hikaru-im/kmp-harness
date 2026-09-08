package im.hikaru.ruoyi.module.infra.api.websocket

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils

/**
 * WebSocket 发送器的 API 接口 (迁移自 Java)
 *
 * 对 WebSocketMessageSender 进行封装，提供给其它模块使用
 *
 * @author 芋道源码
 */
interface WebSocketSenderApi {

    /**
     * 发送消息给指定用户
     *
     * @param userType 用户类型
     * @param userId 用户编号
     * @param messageType 消息类型
     * @param messageContent 消息内容，JSON 格式
     */
    fun send(userType: Int, userId: Long, messageType: String, messageContent: String)

    /**
     * 发送消息给指定用户类型
     */
    fun send(userType: Int, messageType: String, messageContent: String)

    /**
     * 发送消息给指定 Session
     */
    fun send(sessionId: String, messageType: String, messageContent: String)

    /**
     * 发送对象消息给指定用户
     */
    fun sendObject(userType: Int, userId: Long, messageType: String, messageContent: Any?) {
        send(userType, userId, messageType, JsonUtils.toJsonString(messageContent))
    }

    /**
     * 发送对象消息给指定用户类型
     */
    fun sendObject(userType: Int, messageType: String, messageContent: Any?) {
        send(userType, messageType, JsonUtils.toJsonString(messageContent))
    }

    /**
     * 发送对象消息给指定 Session
     */
    fun sendObject(sessionId: String, messageType: String, messageContent: Any?) {
        send(sessionId, messageType, JsonUtils.toJsonString(messageContent))
    }
}

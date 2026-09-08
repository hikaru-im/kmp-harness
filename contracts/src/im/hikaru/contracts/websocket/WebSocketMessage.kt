package im.hikaru.contracts.websocket

import kotlinx.serialization.Serializable

/**
 * RuoYi WebSocket 的通用消息外层。
 *
 * [content] 是一段 JSON 字符串，由 [type] 对应的监听器再次解码。
 */
@Serializable
public data class WebSocketMessage(
    val type: String,
    val content: String,
) {
    init {
        require(type.isNotBlank()) {
            "WebSocket message type must not be blank"
        }
    }
}

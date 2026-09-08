package im.hikaru.ruoyi.framework.websocket.core.message

import java.io.Serializable

class JsonWebSocketMessage(
    var type: String? = null,
    var content: String? = null,
) : Serializable

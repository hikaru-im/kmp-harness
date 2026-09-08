package im.hikaru.ruoyi.module.infra.websocket.message

/** Message sent from a WebSocket client to the server. */
class DemoSendMessage {
    var toUserId: Long? = null
    var text: String? = null
}

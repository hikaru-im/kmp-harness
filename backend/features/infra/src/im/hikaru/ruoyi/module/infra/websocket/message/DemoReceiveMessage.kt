package im.hikaru.ruoyi.module.infra.websocket.message

/** Message sent from server to a WebSocket client. */
class DemoReceiveMessage {
    var fromUserId: Long? = null
    var text: String? = null
    var single: Boolean? = null
}

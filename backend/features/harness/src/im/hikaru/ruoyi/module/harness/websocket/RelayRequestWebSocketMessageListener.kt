package im.hikaru.ruoyi.module.harness.websocket

import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.RelayRequest
import im.hikaru.ruoyi.framework.websocket.core.listener.RawWebSocketMessageListener
import im.hikaru.ruoyi.module.harness.relay.HarnessRelayService
import kotlinx.serialization.decodeFromString
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/** 接收客户端发往指定 Desktop Host 的 Relay 请求。 */
@Component
class RelayRequestWebSocketMessageListener(
    private val relayService: HarnessRelayService,
) : RawWebSocketMessageListener {
    override fun getType(): String = HarnessWebSocketMessageTypes.RelayRequest

    override fun onMessage(session: WebSocketSession, message: String) {
        relayService.routeRequest(
            session,
            HarnessProtocolJson.decodeFromString<RelayRequest>(message),
        )
    }
}

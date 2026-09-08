package im.hikaru.ruoyi.module.harness.websocket

import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.ruoyi.framework.websocket.core.listener.RawWebSocketMessageListener
import im.hikaru.ruoyi.module.harness.relay.HarnessRelayService
import kotlinx.serialization.decodeFromString
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/** 接收 Desktop Host 返回的 Relay 响应。 */
@Component
class RelayResponseWebSocketMessageListener(
    private val relayService: HarnessRelayService,
) : RawWebSocketMessageListener {
    override fun getType(): String = HarnessWebSocketMessageTypes.RelayResponse

    override fun onMessage(session: WebSocketSession, message: String) {
        relayService.routeResponse(
            session,
            HarnessProtocolJson.decodeFromString<RelayResponse>(message),
        )
    }
}

package im.hikaru.ruoyi.module.harness.websocket

import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.ruoyi.framework.websocket.core.listener.RawWebSocketMessageListener
import im.hikaru.ruoyi.module.harness.relay.HarnessRelayService
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

@Component
class RelayEventWebSocketMessageListener(private val relayService: HarnessRelayService) : RawWebSocketMessageListener {
    override fun getType(): String = HarnessWebSocketMessageTypes.RelayEvent
    override fun onMessage(session: WebSocketSession, message: String) {
        relayService.routeEvent(session, HarnessProtocolJson.decodeFromString<RelayEvent>(message))
    }
}

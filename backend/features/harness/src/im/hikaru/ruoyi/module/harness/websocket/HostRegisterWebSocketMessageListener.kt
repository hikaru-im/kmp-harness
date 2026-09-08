package im.hikaru.ruoyi.module.harness.websocket

import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.HostRegistrationRequest
import im.hikaru.ruoyi.framework.websocket.core.listener.RawWebSocketMessageListener
import im.hikaru.ruoyi.module.harness.relay.HarnessRelayService
import kotlinx.serialization.decodeFromString
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/** 接收 Desktop Host 主动注册消息。 */
@Component
class HostRegisterWebSocketMessageListener(
    private val relayService: HarnessRelayService,
) : RawWebSocketMessageListener {
    override fun getType(): String = HarnessWebSocketMessageTypes.HostRegister

    override fun onMessage(session: WebSocketSession, message: String) {
        relayService.registerHost(
            session,
            HarnessProtocolJson.decodeFromString<HostRegistrationRequest>(message),
        )
    }
}

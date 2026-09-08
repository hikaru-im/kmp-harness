package im.hikaru.ruoyi.framework.websocket.config

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("yudao.websocket")
@Validated
class WebSocketProperties {
    @NotEmpty(message = "WebSocket 的连接路径不能为空")
    var path: String = "/ws"

    @NotNull(message = "WebSocket 的消息发送者不能为空")
    var senderType: String = "local"
}

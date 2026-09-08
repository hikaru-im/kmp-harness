package im.hikaru.contracts.harness.relay

/** RuoYi WebSocket `type` 字段中使用的 Harness 消息类型。 */
public object HarnessWebSocketMessageTypes {
    public const val HostRegister: String = "harness.host.register"
    public const val HostRegistered: String = "harness.host.registered"
    public const val HandshakeRequest: String = "harness.handshake.request"
    public const val HandshakeResponse: String = "harness.handshake.response"
    public const val RelayRequest: String = "harness.relay.request"
    public const val RelayResponse: String = "harness.relay.response"
    public const val RelayEvent: String = "harness.relay.event"
}

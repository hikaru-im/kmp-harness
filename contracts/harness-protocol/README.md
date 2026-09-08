# Harness Protocol

`harness-protocol` 是建立在 RuoYi wire contract 之上的 Harness 扩展模块，包名统一位于
`im.hikaru.contracts.harness`。它不重新定义 HTTP/WebSocket 通用协议。

```text
im.hikaru.contracts.harness
├── protocol/   ProtocolVersion、Capability
├── identity/   ClientId、HostId 与双方描述
├── handshake/  Harness 版本和 capability 协商
├── relay/      Relay 请求、响应、事件和 WebSocket type
└── session/    Host 内部 SessionId
```

## RuoYi 基础

HTTP 握手或查询由 RuoYi Controller 返回：

```text
CommonResult<HandshakeResponse>
        ↓ JSON
ApiResult<HandshakeResponse>
```

远程实时消息使用 RuoYi WebSocket 双层编码：

```text
WebSocketMessage(
    type = "harness.relay.request",
    content = Json.encodeToString(RelayRequest(...)),
)
```

Host 注册使用 `HostRegistrationRequest` 和 `HostRegistrationResponse`，成功响应返回当前连接的
generation。Relay 响应使用 `RelayResponse.result: ApiResult<JsonElement>`，不再保留旧的
`ResponseEnvelope.success/error` 和字符串 `ErrorCode`。具体失败由
`backend/features/harness` 按 RuoYi 模块数字错误码映射。

## 消息方向

```text
Mobile RemoteConnection
    -> RuoYi WebSocketMessage
    -> Backend Harness Relay
    -> Desktop HostRelayConnection
    -> API Gateway
```

`HostId` 是 Relay 路由目标；用户、租户和权限来自 RuoYi 已认证 WebSocket Session，不能由
ClientDescription、RelayRequest 或 payload 自行声明。`SessionId` 只在一个 Host 内唯一，跨 Host
引用必须同时携带 HostId。

LocalConnection 不需要 RuoYi WebSocket 外层，但必须复用同一套 Handshake、Gateway method 和
领域请求/响应模型，确保本地与远程 Agent 行为一致。

Relay 能传输某个 endpoint 不等于 Backend 拥有该领域。Harness Settings、Credentials、LLM/MCP、
Agent/Expert、Knowledge 和 Skill 仍由 Host 管理；RuoYi 的配置模块只管理后端服务自身。敏感
endpoint 是否允许远程调用由 Host capability、ACL 和 endpoint policy 共同决定。

## 边界

本模块只包含可序列化 wire model，不包含：

- RuoYi Controller、WebSocket listener 或错误码实现；
- Connection、重连、超时和请求 pending map；
- API Gateway 分派逻辑；
- Repository、ViewModel 和 UI 状态；
- Runtime Service、Plugin 或 Fiber。

# Shared Client

`apps/shared` 保存 Android、iOS 和 Desktop 共用的客户端代码，包括 Connection contract、远程
连接、客户端状态、Repository、ViewModel 和 Compose UI。

客户端代码通过根 `contracts` 使用 RuoYi API，通过 `contracts/harness-protocol` 使用 Harness
握手与 Relay 消息，不依赖 Harness Runtime、Loader、API Gateway 或具体 Plugin。Desktop 需要的
`LocalConnection` 由 `apps/jvm-app` 实现并注入共享客户端；Android 和 iOS 使用连接 RuoYi
Backend Relay 的 `RemoteConnection`。

```text
apps/shared
├── connection/    连接接口、远程连接与握手
├── agent/         Agent 客户端状态、Repository 与 ViewModel
└── host/          客户端当前连接的 Host 状态
```

当前已经实现纯 KMP 握手协商器，以及最小类型化 `Connection` / `HostApi` contract：

```kotlin
interface Connection {
    val host: HostApi
}

interface HostApi {
    suspend fun describe(): ApiResult<HostDescription>
}
```

Desktop 的 LocalConnection 已实现；远程传输、Repository 和 ViewModel 仍是规划项。

`RemoteConnection` 的 HTTP 结果使用 RuoYi `ApiResult`，WebSocket 外层使用 RuoYi
`WebSocketMessage(type/content)`；不得再增加一套客户端专用 success/error envelope。

客户端状态需要处理 Host 描述、capability、会话投影和断线恢复时，应实现为 `apps/shared` 内部
对象并由 Repository 复用，不能把原始事件消费和重连逻辑散落到多个 ViewModel。它仍然只是
客户端实现，不会拆成 `modules/client-runtime` 或 `modules/client/repository`。

本地与远程共享类型化 API 和 `ApiResult` 语义，不要求共享物理传输：LocalConnection 直接调用
Gateway；RemoteConnection 才负责 JSON、请求相关性、超时与 Relay。

远程链路固定为：

```text
RemoteConnection -> RuoYi Backend Relay -> Desktop HostRelayConnection
```

Mobile 不直接连接 Desktop Host。

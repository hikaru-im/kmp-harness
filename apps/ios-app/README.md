# iOS App

iOS App 与 Android 一样是远程客户端，不运行 Harness Runtime、Loader 或 Agent Plugin。

```text
Compose UI
    -> apps/shared Repository / ViewModel
    -> RemoteConnection
    -> RuoYi Backend Relay
    -> Desktop Harness Host
```

iOS 复用 `apps/shared` 的客户端协议和状态逻辑，只在应用层提供平台能力。远程连接使用
`RemoteConnection`；Apple 编译与模拟器运行仍需在 macOS 环境执行。

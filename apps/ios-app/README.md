# iOS App

iOS App 与 Android 一样是远程客户端，不运行 Harness Runtime、Loader 或 Agent Plugin。

```text
Compose UI
    -> apps/shared Repository / ViewModel
    -> RemoteConnection
    -> RuoYi Backend Relay
    -> Desktop Harness Host
```

iOS 复用 `apps/shared` 的客户端协议和状态逻辑，只在应用层提供平台能力。当前应用只完成
Compose 入口，远程连接尚未实现。

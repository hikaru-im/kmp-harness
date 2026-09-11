# Android App

Android App 是远程客户端，不运行 Harness Runtime、Loader 或 Agent Plugin。

```text
Compose UI
    -> apps/shared Repository / ViewModel
    -> RemoteConnection
    -> RuoYi Backend Relay
    -> Desktop Harness Host
```

RuoYi 后端负责登录、租户、设备、Host 授权和远程转发。Android 不直接发现或连接 Desktop
端口，也不持有 Host 凭据。远程连接使用 `RemoteConnection`，并遵守 Host generation、
订阅归属、序号缺口和敏感 API 拒绝策略。

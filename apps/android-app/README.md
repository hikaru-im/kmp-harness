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
端口，也不持有 Host 凭据。当前应用只完成 Compose 和 Android 日志基础装配，远程连接尚未实现。

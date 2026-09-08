# Backend Server

`backend/server` 是 RuoYi Spring Boot 最终装配层，负责组合 `backend/features/*`、加载配置并启动
公网 API。业务实现保留在对应 feature 中，Server 不复制 Service 或 Controller。

Server 已装配 `backend/features/harness` 的 WebSocket Relay。Agent Runtime 仍运行在 Desktop
Host，远程请求通过 Host 主动建立的连接转发；Server 不执行 Agent Plugin。

Server 不依赖 Compose 客户端，也不直接依赖 `apps/*`。普通 API 使用根 `contracts` 对齐 RuoYi
wire model；Harness Relay 使用 `contracts/harness-protocol` 作为内层消息。

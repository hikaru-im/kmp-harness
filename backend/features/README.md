# Backend Features

`backend/features` 保存 RuoYi 业务与基础设施 feature。现有 infra、member、mp、pay、sync、system
保持各自边界，通过 `backend/common`、`backend/framework` 和共享 contracts 协作。

`harness` feature 负责远程 Harness 接入与 Relay，而不是 Agent Plugin 或 Harness 配置中心：

```text
harness/
├── Host connection registry
├── Client/Host WebSocket endpoint
├── Relay routing and correlation
├── Tenant and permission checks
└── Host presence events
```

它不实现 Runtime、Loader、Settings、Credentials、LLM、MCP 或 Agent 业务能力，也不直接持有
Desktop Session 内部状态。
该 feature 依赖 `contracts/harness-protocol`，使用 RuoYi WebSocket Session、用户类型和租户
上下文完成接入；不能信任 Relay payload 自带的用户或 tenant 字段。

当前已实现 Host 注册、connection generation、requestId 相关性和 request/response 转发。事件
订阅、请求超时、多实例路由与持久化 Host 元数据仍是后续能力。

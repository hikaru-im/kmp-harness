# Backend

`backend` 以 RuoYi 为基础，承担产品后端、持久化和公网入口。它不等同于 Harness Runtime，
第一阶段也不在后端进程中执行 Desktop Agent。

现有职责：

- 用户、会员、租户和权限。
- 系统、基础设施、支付、公众号与 App Sync。
- PostgreSQL/MySQL 数据库脚本和管理后台。

Harness Relay 职责：

- Host 注册、在线状态和连接 generation。
- Client 与 Host 的认证、租户隔离和授权。
- 根据 `HostId` 路由请求、响应和 Agent 事件。
- 维护请求相关性、断线失败和重连状态。
- 为 Mobile 提供唯一的远程 Harness 入口。

RuoYi system/infra 中的配置能力只管理后端服务自身。Harness Profile、Settings、Credentials、
LLM/MCP Provider、Agent/Expert、Knowledge 和 Skill 均由 Desktop Harness Host 管理；Backend Relay
不把这些 payload 保存为后端配置，也不实现对应 Runtime Service。

```text
Mobile RemoteConnection
    -> Backend Harness Relay
    -> Desktop HostRelayConnection
    -> API Gateway / Runtime
```

Relay 只转发经过校验的 Harness 协议，不把 Spring Controller VO、数据库实体或服务端凭据暴露
给客户端。第一版实现位于 `backend/features/harness`，已经包含认证身份隔离的 Host 注册、连接
generation、requestId 相关性以及 request/response 转发。

Harness Relay 复用现有 RuoYi WebSocket 的认证 Session、租户上下文和 `type/content` 外层；HTTP
继续使用 `CommonResult`。Harness 只作为 `content` 内层协议存在，不平行引入新的通用网络栈。

当前注册表与 pending request 表是单后端实例内存状态。Redis 等 WebSocket sender 只能负责消息
投递，不能自动提供 Host 所有权和 requestId 原子协调；启用多实例前必须补充分布式注册表、实例
路由和过期回收。Relay event 也要等显式订阅关系确定后再开放，不能向同租户所有 Session 广播。

`backend/server` 是 RuoYi 功能模块的最终 Spring Boot 装配层；`backend/ui` 是管理后台；
数据库说明见 [database/README.md](database/README.md)。

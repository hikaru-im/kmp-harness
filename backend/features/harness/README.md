# Harness Backend Feature

`backend/features/harness` 是 RuoYi 后端中的远程 Harness Relay，不是 Agent Runtime，也不是
Desktop Host Transport。

它也不是 Harness 配置中心。Harness Settings、Credentials、LLM/MCP、Agent/Expert、Knowledge
和 Skill 的事实来源位于 Desktop Harness Host；本 feature 只负责经过认证的消息转发。

```text
Mobile RemoteConnection
    -> RuoYi /ws
    -> Harness WebSocket listeners
    -> HostConnectionRegistry / PendingRelayRegistry
    -> Desktop HostRelayConnection
```

## 当前职责

- `harness.host.register` 将 `HostDescription` 绑定到当前已认证 WebSocket Session；
- 注册键由 tenantId、userType、userId 和 HostId 共同组成；
- 同一 Session 重复注册保持 generation，新 Session 注册同一 Host 会创建新 generation；
- `harness.relay.request` 记录 client Session 与目标 Host generation 后再转发；
- `harness.relay.response` 只有来自当前 Host Session 和 generation 时才会返回客户端；
- Host 替换或断线会使用 RuoYi 数值错误码失败对应 pending 请求；
- 重复 requestId、离线 Host 和越权 Host 查找均在进入 Desktop Runtime 前拒绝。

RuoYi WebSocket framework 负责外层 `type/content`、登录态和租户上下文。Harness listener 使用 raw
content 和 kotlinx.serialization 解码 KMP contract，避免 Jackson 再次解释 value class。

## 状态所有权

当前 `DefaultHostConnectionRegistry` 与 `DefaultPendingRelayRegistry` 是单后端实例内存实现。这适合
先完成 Desktop 与 Mobile 闭环，但不是多实例完成态。多实例需要分布式 Host lease、实例地址、
generation CAS、pending request 过期和跨实例响应路由；仅切换 RuoYi WebSocket sender 为 Redis
不能满足这些一致性要求。

Relay event 暂不广播。必须先建立 stream/subscription 到客户端 Session 的明确所有权，否则按用户
或租户广播可能泄露另一个客户端的 Agent 会话事件。

## 依赖边界

该 feature 依赖 RuoYi `common/framework` 与 `contracts/harness-protocol`，不依赖 `apps/*`、Harness
Runtime、API Gateway 或具体 Agent Plugin。未来的权限共享规则应作为显式 ACL 加入 Registry 查找，
不能通过信任 payload 中的 tenantId 或 userId 实现。

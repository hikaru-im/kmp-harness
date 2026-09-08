# Session API 与事件订阅完整规格

提供 provider-neutral 的 `list`、`create`、`history`、`prompt`、`cancel` service。API 只操作 `SessionId`/`AgentId` 与可序列化 contract，不泄露 Koog、Provider、Context 内部类型。

`prompt` 通过 Agent Inbox 投递 follow-up 并返回可观察的 turn handle；`cancel` 只取消目标 Agent 的当前 driver，可选择保留未领取 Inbox。`history` 始终由 Session 日志派生；`list/create` 遵守 SessionStore 创建顺序和 owner 生命周期。

事件订阅复用 `SessionEvents`、`AgentEvents` 和 Runtime typed keys；订阅失败遵守 contained/flush 语义，不建立旁路消息缓存。Relay 只转发相同 contract，不改变事件顺序或错误码。

### Scenario: API 与 replay 一致

WHEN 通过 API create → prompt → history，THEN 返回的 history 与同一 Session 完整日志执行 `deriveMessages()` 相同；cancel 后不残留 Agent job。

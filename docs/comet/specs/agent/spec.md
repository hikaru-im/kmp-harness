# Agent 模块完整规格

## 模块边界

`modules/agent` 提供公开 Agent 句柄、注册表、可替换工厂、Inbox 和 `agent/*` typed extension points。它不实现 AgentLoop、不调用 Provider、不拥有第二份 Session message store。

依赖：`runtime`、`llm`、`session`。不依赖 `agent-loop`、apps、backend、Koog 或具体 Provider。

## Agent 与工厂

`Agent` 至少暴露：`id: SessionId`、`options: AgentOptions`、`session: Session`、`status: IDLE|RUNNING`、`inbox`、`context`、`awaitIdle()`、`cancel(cause, keepInbox)`、`send(message,target,wakeup)`、`followup(message)`、`steer(message)`、`inject(message)`。

`AgentRegistry` 管理 live Agent 和一个可替换的 `AgentFactory`。没有 factory 时 `create()` 使用稳定错误失败；factory 注册、替换、dispose 都是原子操作。创建返回带 owner identity 的 `AgentHandle`；`get()` 返回的 Agent 不能拆除其他 owner 的实例。重复 `SessionId` 不覆盖 live Agent。

创建顺序为 prepare → setup child Context → factory create → register → publish created。任一步失败都回滚已创建的 Session/effect，不发布半成品。handle dispose 只移除 identity 相同的实例，并释放 child Context、Inbox driver 和局部 listeners。

## Inbox

Inbox 有 `NEXT_TURN` 与 `NEXT_STEP` 两个队列。`followup` 进入前者；`steer`/`inject` 进入后者并在最近 step 边界合并领取。每次 insert/claim/replace/clear 先 append `agent/inbox/spliced` Session event，再更新 live projection；事件携带 Agent、队列、操作、消息身份和队列快照版本，允许从完整日志重放。

并发插入、领取、取消和 dispose 必须线性化；`keepInbox=true` 仅保留尚未领取的 durable items，driver 仍必须停止。`awaitIdle()` 返回时不存在运行 driver job 或未释放的 prepared call。

## 扩展事件

公开 typed keys：

```text
emit: agent/created, agent/disposed, agent/status
emit: agent/inbox/inserted, agent/inbox/claimed, agent/inbox/discarded
emit: agent/session-start
suspend waterfall: agent/pre-step, agent/request, agent/request-error
sequential: agent/turn-stopping
```

所有 payload 显式携带 Agent identity。Agent 局部 listener 使用 identity-filtered helper 绑定；不能假设共享 EventsService 会自动按 child Context 过滤。

## 场景

### Scenario: 没有 factory 时创建失败

WHEN `AgentRegistry.create()` 在没有有效 factory 时调用，THEN 返回稳定 `NO_FACTORY` 错误，不创建 Session、不发布 created、不残留 Agent。

### Scenario: 重复 id 和旧 handle 隔离

WHEN 同一 id 已经 live 或旧 handle 在新实例注册后执行 dispose，THEN 前者原子失败且不覆盖，后者不能移除新实例。

### Scenario: durable Inbox 与 live projection 一致

WHEN insert、claim、replace、clear 被调用，THEN Session event 先提交，随后内存队列更新；从这些事件 replay 的队列与 live 队列逐项相同。

### Scenario: 并发取消最终收敛

WHEN wakeup、cancel、awaitIdle 和 dispose 并发发生，THEN 所有 driver job 被回收，状态最终为 IDLE/Disposed，且没有重复 created/disposed 或未释放 child effect。

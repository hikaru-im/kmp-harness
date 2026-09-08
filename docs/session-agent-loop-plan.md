# Session、Agent 与 AgentLoop 实施规划

本文规划 KMP Harness 的 `modules/session`、`modules/agent` 和 `modules/agent-loop`。设计依据为
DeepSeek Harness `0a53fb55bea101816fa226bb964ae2bed71c343b`（`dsh-0.1.2-alpha.2`），但只实现当前
Desktop Host 形成最小 Agent 会话闭环所需的能力，不逐行移植 DSH 的完整产品功能。

## 目标

第一阶段完成一条可运行、可观察、可取消的纯文本 Agent 链路：

```text
Agent.followup(user message)
    -> AgentLoop 打开 turn / step
    -> Session 记录模型可见输入和请求信封
    -> LlmRuntime 发起一次 provider attempt
    -> Session 记录原始 chunk 和最终 assistant message
    -> AgentLoop 关闭 step / turn
    -> Session.deriveMessages() 可重建下一次请求历史
```

三个模块必须保持以下边界：

- `session` 是仅追加事件日志和模型历史投影，不运行 Agent 或调用模型。
- `agent` 是公开句柄、注册表、Inbox 和 `agent/*` 扩展点，不实现具体循环。
- `agent-loop` 是 `Agent` 的默认实现，通过 `AgentRegistry` 注册工厂；其他模块不直接依赖它。
- `LlmAdapter` 的一次调用仍只代表一次 Provider attempt；重试不进入 Adapter 或
  `LlmRuntime.stream()`。

## 依赖方向

```text
runtime
  ^
  |---- llm
  |       ^
  |       |
  |---- session
  |       ^
  |       |
  |---- agent
          ^
          |
      agent-loop ----> llm
```

物理模块依赖固定为：

```text
modules/session    -> modules/runtime + modules/loader + modules/llm
modules/agent      -> modules/runtime + modules/llm + modules/session
modules/agent-loop -> modules/runtime + modules/llm + modules/session + modules/agent
```

`session` 不依赖 `agent`，`agent` 不依赖 `agent-loop`，三个模块都不依赖 `apps`、`backend`、
API Gateway、Koog 或具体 Provider。

## 已完成的 Runtime 前置

Runtime 原有的 `WaterfallEventKey` 是同步 onion middleware，适合返回冷 `Flow` 的
`llm/stream`，但不能表达需要挂起等待的 `agent/pre-step`、`agent/request` 和
`agent/request-error`。现已补齐两种 Runtime 事件能力：

1. `SuspendWaterfallEventKey<T, R>`：listener 和 `next()` 都可挂起，保持注册顺序、短路和 once
   语义；不修改现有同步 `WaterfallEventKey`。
2. `SequentialEventKey<T>`：按注册顺序等待所有返回 `Unit` 的 listener，用于
   `agent/turn-stopping`；现有 `SerialEventKey<T, R>` 会在首个非 null 结果处停止，不能用 `Unit`
   模拟顺序广播。

两项 Runtime 单元测试已覆盖顺序、短路、异常、取消、once 和 dispose 行为，S1 可以直接使用这些
typed key，不需要在 Session、Agent 或 AgentLoop 内重复实现事件编排。

## 阶段 S1：Session

创建 `modules/session`，Plugin 名称为 `session`，提供 `SessionKey` / `SessionStore`。
详细的数据模型、提交边界、Store 事务、KMP 并发差异、任务拆分与验收以
[Session S1 对齐 DSH 实施规划](session-s1-plan.md) 为准。

### 标识与信封

- `SessionId` 使用可序列化 value class，不与 `MessageId`、`CallId` 或 `LlmSessionId` 混用。
- `SessionHeader` 首版固定 DSH 的 `version`、`id`、`createdAt`、可选 `cwd`、lineage、subagent
  delegation 和 `agentPreset` 字段；S1 创建路径只使用 `cwd`，其余字段为后续 restore/fork 保留。
- 每条 `SessionEventEnvelope` 包含 `type`、连续递增的 `seq`、epoch 毫秒 `time`、JSON `data`、
  可选 `ignorable`、可选 `sourceEventSeqs` 和 surface 事件必需的 `surfaceOp = append`。
- `SessionEventKey<T>` 持有稳定事件名和 `KSerializer<T>`；`append(key, data)` 立即序列化，日志保存
  JSON 快照而不是调用方的可变对象引用。
- Plugin 可以在自己的模块声明事件 key，例如未来 `agent/inbox/spliced` 与 `llm/retry`，无需修改
  `session` 的 sealed class。

首版只创建新会话，不加载磁盘记录。因此未知 required event 的拒绝、事件 codec registry 和格式升级
链留给独立 `session-persistence` 阶段；信封从第一版保留 `version` / `ignorable` 字段，避免把扩展事件
做成 Session 核心枚举。

### 首版事件词汇

```text
turn/start
turn/end
step/start
step/end
user/message
assistant/chunk
assistant/message
request/header
request/context
```

`turn/end` 使用可序列化的原因联合：`completed`、`max-tokens`、`aborted`、`blocked`、`error`、
`interrupted`；其中 `interrupted` 仅供后续 crash repair。
`assistant/message` 同时保存 `turn`、`step`、组装后的 `Message`、可选 `TokenUsage` 和
`interrupted`。`request/header` 保存精确 `LlmCallConfig`、adapter defaults、system 和 tools；即使
首版 system/tools 为空，也必须记录实际发送值。

### 历史投影

`deriveMessages()` 只读取模型可见事件：

- `user/message` 原样成为 user history；
- `assistant/message` 原样成为 assistant history；
- `assistant/chunk`、turn/step 边界、失败和 request metadata 不进入 history。

首版 surface 只支持尾部追加。Compaction 所需的 range replacement、fork、seed、崩溃修复和 chunk
压缩不在 S1 中；其中 range replacement 必须与 provenance 验证、重放和格式版本决策一起实现。

### Session 验收

- append 后 payload 与调用方后续修改隔离，返回事件也不能改写日志。
- `seq` 从零连续增长；失败的序列化不消耗 seq，也不发送 `session/event`。
- `deriveMessages()` 能从日志重新构造相同的消息列表，不存在第二份权威 message store。
- `SessionStore.create/get/list` 保持创建顺序、拒绝重复 id；identity-bound detach 不删除同 id 的新对象。
- `session/created`、`session/event`、`session/disposed` 只在提交点发出。
- `session/event` 与 `session/disposed` listener 失败逐个隔离；`session/flush` 等待全部 persistence
  listener settle。
- 自定义测试事件可由外部 `SessionEventKey` 追加和读取，证明未来 `llm-retry` 无需反向依赖。

## 阶段 A1：Agent

创建 `modules/agent`，Plugin 名称为 `agent`，提供 `AgentKey` / `AgentRegistry`。

### 公开接口

`Agent` 首版包含：

```text
id: SessionId
options: AgentOptions
session: Session
status: IDLE | RUNNING
inbox: Inbox
context: Context
awaitIdle()
cancel(cause, keepInbox = false)
send(message, target, wakeup)
followup(message)
steer(message)
inject(message)
```

`AgentRegistry` 管理 live Agent 和一个可替换的 `AgentFactory`。消费方只依赖 `agent` 并调用
`create()`；`agent-loop` 通过注册句柄提供默认工厂。创建返回 `AgentHandle`，只有 handle/所有者生命周期
拥有 dispose 能力，`get()` 返回的裸 `Agent` 不能拆除其他调用方创建的实例。

### Inbox

Inbox 有两个队列：

- `NEXT_TURN`：每条 follow-up 独占一个普通 turn；
- `NEXT_STEP`：steering 和 injected context 在最近的 step 边界合并领取。

每次插入、领取、替换或清空先追加 `agent/inbox/spliced` Session 事件，再修改内存投影；这使未来
Session 恢复可以重建尚未消费的工作。Agent 模块拥有该事件 key，Session 不认识其业务 payload。

### Agent 扩展点

首版提供以下 typed keys：

```text
emit:              agent/created, agent/disposed, agent/status
emit:              agent/inbox/inserted, claimed, discarded
emit:              agent/session-start
suspend waterfall: agent/pre-step, agent/request, agent/request-error
sequential:        agent/turn-stopping
```

所有 payload 显式携带 `agent`。每个 Agent 拥有 child `Context` 来约束 effect 生命周期；在通用 scoped
event filtering 落地前，Agent 局部 listener 通过 Agent 模块提供的 identity-filtered 注册 helper 绑定，
不能假定 child Context 会自动过滤共享 `EventsService`。

### Agent 验收

- 没有 factory 时 `create()` 明确失败；factory dispose 后立即恢复为无 factory 状态。
- create/setup/register 任一步失败都不发布半个 Agent 或 Session。
- 同 id 注册原子失败；旧 handle 不移除后来替换的同 id Agent。
- Inbox 的 durable splice 与内存列表严格一致，可从 Session events 重放。
- `cancel()`、`awaitIdle()` 和 dispose 在并发 wakeup 下最终收敛，不遗留运行 Job。
- Agent child Context 在 handle dispose 时释放全部局部 effect。

## 阶段 L1：AgentLoop 文本闭环

创建 `modules/agent-loop`，Plugin 名称为 `agent-loop`。它不向调用方暴露具体 Agent 类型，只向
`AgentRegistry` 注册 `AgentFactory`。

首版不从 Plugin 配置自动启动 Agent；Desktop 的 Session API 将是创建入口。首版也不引入
`maxParallelToolCalls`，因为尚无 Tools Service Consumer。

### Turn 与 Step

一个 turn 包含零个或多个 step；首版没有工具执行，因此正常文本响应只产生一个 step。事件顺序固定为：

```text
turn/start
  agent/pre-step
  step/start
    user/message*
    agent/request
    request/header
    request/context?
    assistant/chunk*
    assistant/message
  step/end
  agent/turn-stopping
turn/end
```

每个模型请求必须使用 `LlmRuntime.prepareCall()`：同一份 adapter registration 的 resolved model、adapter
defaults、retry policy 和 stream 必须绑定在一起。`request/header` 提交后才允许发起 Provider I/O。

### 失败、重试扩展点与取消

- `ErrorFinishReason` / `AbortedFinishReason` 不生成正常 `assistant/message`，而是进入
  `agent/request-error`。
- listener 返回 `Retry` 时，AgentLoop 在同一个 turn/step 中重新准备并发起一次请求；失败 attempt 的
  chunk 保留为日志事实，但不进入派生 history。
- listener 返回 `Unhandled` 时，`step/end` 仍在 finally 中写入，`turn/end` 记录结构化 error。
- caller cancellation 不进入 retry listener；如果用户已经收到文本/reasoning 前缀，则记录
  `assistant/message(interrupted = true)`，随后以 aborted 关闭 turn。
- dispose 必须取消并排空 driver，之后撤销 Agent 注册，再撤销 Session 注册和 child Context。

`agent/request-error` 只建立执行插槽，不在 L1 内实现退避。直接调用 `LlmRuntime.stream()` 继续保持单次
attempt。

### 暂不支持的工具结束

L1 发送 `tools = null`。若 Provider 仍返回 `ToolCallBlock` 或 `ToolCallsFinishReason`，AgentLoop 使用稳定的
`TOOLS_NOT_AVAILABLE` 失败关闭 turn，不能提交一个下一步无法履行的 assistant tool call。完整工具闭环
必须等待独立 `system-prompt` 和 `tools` Service 落地，再扩展 AgentLoop，而不是把工具注册表塞进
`agent-loop`。

### AgentLoop 验收

- scripted Adapter 的一次 follow-up 产生精确事件顺序和一条可派生 assistant message。
- 两条 follow-up 串行产生两个 turn，不会并行驱动同一个 Agent。
- 第一次返回 `SERVER`、测试 listener 返回 `Retry`、第二次成功时，Provider attempt 为两次但只有一个
  turn、一个 step 和一个 assistant surface message。
- 没有 recovery listener 时，结构化 `LlmFailure` 原样进入 `turn/end`。
- 流中取消会终止 Provider Flow，并按是否已有可见前缀记录 interrupted message。
- Agent/Session/Provider/Host dispose 后没有活动 Job、未释放 prepared call 或重复事件。
- JVM、Android 和 iOS Simulator 的模块测试通过；Desktop fixture 验证真实 Runtime/Loader 组合。

## 后续顺序

三个模块的文本闭环完成后，按以下顺序继续：

1. Session API 与事件订阅：实现 `list/create/history/prompt/cancel`，接入 LocalConnection，形成第一个可由
   Desktop Client 使用的文本会话闭环；Relay 只转发相同 contract。
2. `system-prompt` 与 `tools`：补齐 tool schema、tool call/result、并行安全与独占调度，完成多 step turn。
3. `session-persistence` 与首个 file provider：加载、flush、恢复、未知 required event 拒绝和崩溃修复。
4. `llm-retry`：监听已经存在的 `agent/request-error`，在等待前追加 `llm/retry`，等待完成后追加
   `llm/retry-started`，再返回 `Retry`。

实现 `llm-retry` 时再决定是否把当前 `NormalRetryPolicy.maxRetries = 2` 对齐 DSH 的 `5`；策略默认值与
执行器必须在同一个验收阶段确认，不能只改常量。

## 明确不在本轮规划内

- SQLite/JSONL 持久化、迁移、fork、compaction、projection registry 和 chunk row compression。
- Tool registry、工具执行、审批、MCP、Skill、Subagent、Goal 和多 Agent 协调。
- Session/Agent 的 Gateway、wire contract、Repository、ViewModel 或 Compose UI。
- 声明式自动启动 Agent、Agent preset、动态 persona 与跨进程 initiator scope。
- 在 Koog、Provider Adapter 或 `LlmRuntime.stream()` 内执行自动重试。

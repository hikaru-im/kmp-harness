# Outcome

在现有 Runtime、LLM 和 Session 基础上，对齐 DSH/Cordis 的 Agent 运行边界，形成一条可取消、可观察、可恢复的文本 Agent 闭环，并按依赖顺序补齐工具、多步会话、Session 持久化和真正持久化的 `llm-retry`。

最终调用链：

```text
Agent.followup
  -> Inbox
  -> AgentLoop turn/step
  -> Session append/event projection
  -> LlmRuntime.prepareCall + one provider attempt
  -> assistant chunks/message
  -> optional tool step
  -> persistent retry policy/event log
```

# Scope

本 change 使用单会话、单条主线按以下依赖顺序落地：

1. **Stage A1：Agent 基础** — `modules/agent`，包括 `Agent` 接口、状态、`AgentRegistry`、可替换 `AgentFactory`、`AgentHandle`、双队列 Inbox、身份绑定 dispose 和 typed 扩展事件。
2. **Stage L1：AgentLoop 文本闭环** — `modules/agent-loop`，注册默认 Agent 工厂，实现无工具文本 turn/step、取消、`request-error` 的 Retry/Unhandled 插槽和精确 Session 事件顺序。
3. **Session API 与事件订阅** — 在现有 Session API 之上提供 `list/create/history/prompt/cancel` 的 provider-neutral service/contract；事件订阅只复用 Runtime/Session typed events，不复制第二份消息状态。
4. **Tools** — 独立 `system-prompt` / `tools` 扩展点，工具 schema、tool call/result、并行安全和独占调度；AgentLoop 扩展为多 step turn。
5. **Session Persistence** — 独立 persistence contract 与首个 file-backed provider，支持 load/flush/recover、未知 required event 拒绝和崩溃修复；路径由平台启动器注入，不硬编码操作系统目录。
6. **真正持久化的 `llm-retry`** — 独立 Plugin 监听 `agent/request-error`，在等待前后写入 retry 事件，按已绑定的 `RetryPolicy` 执行退避/上限/取消，并通过 Session Persistence 在重启后恢复 retry 状态。

物理模块依赖保持单向：

```text
runtime <- llm <- session <- agent <- agent-loop
                         \-> session-api
agent-loop + session-api + tools -> session-persistence -> llm-retry
```

所有模块不得依赖 `apps`、`backend`、Gateway、Compose UI、Koog 或具体 Provider；Provider 仍只执行一次 attempt。

# Non-goals

- 不把重试塞进 `LlmAdapter`、Koog 或 `LlmRuntime.stream()`；不修改一次 attempt 的语义。
- 不实现 fork、compaction、range replacement、projection registry、chunk row compression 或格式迁移 UI。
- 不实现 MCP/Skill/Subagent/Goal、多 Agent 协调、审批工作流和声明式自动启动 Agent。
- 不在本 change 引入数据库；Persistence 的首个 provider 只要求平台注入的 file-backed append/flush，数据库 provider 留给后续 change。
- 不改变已归档 Session S1 的核心事件语义；Agent、Tools、Retry 通过自有 `SessionEventKey` 扩展日志。

# Acceptance examples

- **A1 — Agent 基础接口与注册**：安装 `agent` Plugin 后，没有 factory 的 `create()` 明确失败；注册 factory 后可创建 Agent，`get/list` 按创建顺序返回，重复 id 原子失败；factory/handle dispose 后不残留 Agent。
- **A2 — Agent 生命周期与扩展事件**：create/setup/register 任一步失败都不发布半个 Agent；`agent/created`、`agent/status`、`agent/disposed` 以及 session-start 事件只发布一次且 payload 携带 Agent 身份；Agent child Context 的 effects 在 handle dispose 时全部释放。
- **A3 — Inbox durable splice**：`followup` 进入 `NEXT_TURN`，`steer/inject` 进入 `NEXT_STEP`；插入、领取、替换、清空先追加 `agent/inbox/spliced`，再更新内存投影；从 Session 事件重放出的队列与 live 队列一致。
- **A4 — Agent 并发收敛**：并发 wakeup、`cancel(keepInbox)`、`awaitIdle()` 和 dispose 最终不遗留 Job；旧 handle 不能 detach 同 id 的新 Agent，裸 `get()` 不能拆除其他 owner 的实例。
- **A5 — 无工具文本闭环**：一次 scripted adapter follow-up 严格产生 turn/start → pre-step → step/start → user/message → request/header/context → assistant/chunk/message → step/end → turn-stopping → turn/end，并可由 Session 派生一条 assistant 历史。
- **A6 — Follow-up 串行化**：两条 follow-up 形成两个顺序 turn；同一 Agent 不并行驱动两个 provider attempt，且每次请求都绑定同一 prepared call 的 model/defaults/retry policy/stream。
- **A7 — request-error 插槽**：首次 attempt 返回 `SERVER`、listener 返回 `Retry`、第二次成功时只有一个 turn/step 和一个 assistant surface message；返回 `Unhandled` 时 finally 仍写 step/end，结构化 failure 原样进入 turn/end；无 recovery listener 时不隐式重试。
- **A8 — 取消与释放**：caller cancellation 不进入 retry listener；已有可见前缀时写入 `assistant/message(interrupted=true)`，随后以 aborted 关闭 turn；Agent/Session/Provider/Host dispose 后无活动 Job、prepared call 或重复事件。
- **A9 — Session API 与订阅**：provider-neutral service 能执行 `list/create/history/prompt/cancel`；事件订阅使用 Session/Agent typed events，live projection 与从日志 replay 相同，Relay 只转发相同 contract。
- **A10 — Tools 多 step**：工具 schema 与调用/结果事件可扩展注册；tool call 必须有权威 CallId、配对 result 和独占调度，工具步骤完成后继续同一 turn，无法执行的工具以稳定错误结束而不伪造 assistant tool history。
- **A11 — Session Persistence**：file-backed provider 原子 flush/load，拒绝未知 required event，保留 ignorable event，能恢复未完成 turn/step 为结构化 interrupted/crash-repair 状态；恢复后的 `deriveMessages`、Inbox 和 request fold 与重启前一致。
- **A12 — 持久化 llm-retry 与验证**：`llm-retry` 监听 request-error，在等待前写 `llm/retry`、开始新 attempt 前写 `llm/retry-started`，遵守 resolved RetryPolicy 的上限/退避/取消；重启后不重复已提交 attempt。JVM、Android、iOS Simulator 模块测试、Desktop Runtime/Loader fixture、整仓 build、diff-check 和依赖审计通过；平台无法执行的测试必须记录风险。

# Constraints and invariants

- Session 日志是唯一事实源；Agent/Inbox/Tools/Retry 的内存 projection 都必须可由 Session events 重建，不得建立独立权威 message store。
- 所有跨模块事件 payload 必须可序列化、稳定命名、身份明确；append 失败不能消耗 seq、写日志或发布事件。
- Runtime 的 `SuspendWaterfallEventKey`、`SequentialEventKey` 和 Context dispose 是唯一生命周期编排入口；模块不得自建全局协程或绕过 owner Context。
- `LlmRuntime.prepareCall()` 返回的一次调用只能 dispatch 一次 attempt；route replacement 不得切换正在运行的调用。
- Retry policy 在 request 开始时绑定；Retry listener 只能决定是否重试，退避与持久化由 `llm-retry` Plugin 负责。
- 平台目录由 launcher 传入；file provider 不读取环境变量，也不把 `$DSH_HOME` 或固定操作系统路径写入模块。
- 取消优先级高于 retry；observer/listener 失败逐个隔离，但 flush/aggregate 的失败必须向调用方传播。

# Decisions

- 采用单会话、顺序实现，不创建 Supervisor/Child changes；这些模块共享 Session/Runtime 核心且验收存在严格先后依赖。
- Agent 不在 Plugin 配置中自动启动；由显式 `AgentRegistry.create()`/Session API 创建。
- Inbox 分为 `NEXT_TURN` 和 `NEXT_STEP` 两个语义队列；steer/inject 不抢占已经提交的当前 provider attempt。
- L1 首版 `tools = null`，收到 ToolCall/ToolCalls finish 时使用稳定 `TOOLS_NOT_AVAILABLE` 失败；Tools 阶段再扩展多 step。
- Persistence 首个 provider 采用平台注入路径的 file-backed append/flush 抽象；具体桌面目录由 launcher/home 决定，KMP 核心不硬编码。
- `llm-retry` 的策略默认值继续沿用当前 `NormalRetryPolicy`，是否把最大重试次数从 2 改为 DSH 的 5，必须在 Retry 阶段以验收结果确认，不提前改常量。

# Open questions

- [blocking] CONFIRM: 请确认以上单会话、六阶段顺序、模块边界、file-backed persistence（路径由 launcher 注入）以及 L1 无工具行为，确认后进入 Build。

# Verification expectations

- 每个阶段都先补对应模块的 JVM/Android/iOS Simulator 单元和 contract tests，再执行整仓 build。
- Verify 必须逐项检查 A1-A12；必要时由 Runtime 执行补充命令。iOS 在 Linux 主机只能做 source/test compile-only 时，报告中明确标记为未执行的程序级风险。
- 依赖审计必须证明 `session` 不依赖 `agent`，`agent` 不依赖 `agent-loop`，Provider/Koog 不被上层模块直接引用。

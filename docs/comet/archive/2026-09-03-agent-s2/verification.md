---
generated_from_state_version: 19
---

# 验证

## 当前结果

- 结果: **已归档**
- 验证情况: **已完成检查，验证结果已确认**
- 目标周期: 1
- 迭代: 3
- 验证器尝试次数: 3
- 完成时间: 2026-09-03T13:39:28.096Z
- 摘要: Independent read-only verification passes all 25 acceptance items. Source review confirms the A4/A6/A18 queue-finalization race is addressed by Agent-mutex reconciliation, while target JVM/Android suites, iOS production compilation, persistence recovery, Desktop/Loader fixtures, dependency boundaries, full build, and diff-check all succeeded. Remaining limitations are platform execution, live-network coverage, JVM-only file persistence, and non-exhaustive scheduler exploration.

## 验收

| 编号 | 结果 | 来源 | 验收项 | 原因 |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | **A1 — Agent 基础接口与注册**：安装 `agent` Plugin 后，没有 factory 的 `create()` 明确失败；注册 factory 后可创建 Agent，`get/list` 按创建顺序返回，重复 id 原子失败；factory/handle dispose 后不残留 Agent。 | AgentRegistry implements stable NO_FACTORY failure, factory registration/replacement, ordered get/list, atomic duplicate-id rejection, and identity-bound factory/Agent handles; Agent JVM/Android contracts passed. |
| A2 | passed | brief.md | **A2 — Agent 生命周期与扩展事件**：create/setup/register 任一步失败都不发布半个 Agent；`agent/created`、`agent/status`、`agent/disposed` 以及 session-start 事件只发布一次且 payload 携带 Agent 身份；Agent child Context 的 effects 在 handle dispose 时全部释放。 | Agent creation prepares the child Context and Session before registration, rolls back factory/setup failures without publishing created, emits identity-bearing lifecycle events from single points, and releases child effects through handle disposal; failure and restore-ownership tests passed. |
| A3 | passed | brief.md | **A3 — Inbox durable splice**：`followup` 进入 `NEXT_TURN`，`steer/inject` 进入 `NEXT_STEP`；插入、领取、替换、清空先追加 `agent/inbox/spliced`，再更新内存投影；从 Session 事件重放出的队列与 live 队列一致。 | Inbox serializes NEXT_TURN/NEXT_STEP operations and appends agent/inbox/spliced before changing its live projection; insert, claim, replace, clear, typed notices, contiguous versions, and replay equality are implemented and tested. |
| A4 | passed | brief.md | **A4 — Agent 并发收敛**：并发 wakeup、`cancel(keepInbox)`、`awaitIdle()` 和 dispose 最终不遗留 Job；旧 handle 不能 detach 同 id 的新 Agent，裸 `get()` 不能拆除其他 owner 的实例。 | The finishing-driver race is closed: runDriver keeps the current driver registered while rechecking durable NEXT_TURN under the Agent mutex, and cancel reconciles late insertion before releasing stopping. The 32-cycle idle-boundary regression, cancellation paths, and identity-bound detach checks passed on JVM and Android. |
| A5 | passed | brief.md | **A5 — 无工具文本闭环**：一次 scripted adapter follow-up 严格产生 turn/start → pre-step → step/start → user/message → request/header/context → assistant/chunk/message → step/end → turn-stopping → turn/end，并可由 Session 派生一条 assistant 历史。 | The scripted text contract confirms the required turn/pre-step/step/user/request/header/context/chunk/message/step-end/turn-stopping/turn-end ordering and replay-derived user plus assistant history. |
| A6 | passed | brief.md | **A6 — Follow-up 串行化**：两条 follow-up 形成两个顺序 turn；同一 Agent 不并行驱动两个 provider attempt，且每次请求都绑定同一 prepared call 的 model/defaults/retry policy/stream。 | Only one driver is registered per Agent, NEXT_TURN items are claimed serially, every attempt uses one prepared call and disposes it in finally, and the atomic final queue check prevents a second follow-up from being stranded; ordered-turn tests passed. |
| A7 | passed | brief.md | **A7 — request-error 插槽**：首次 attempt 返回 `SERVER`、listener 返回 `Retry`、第二次成功时只有一个 turn/step 和一个 assistant surface message；返回 `Unhandled` 时 finally 仍写 step/end，结构化 failure 原样进入 turn/end；无 recovery listener 时不隐式重试。 | Provider Error and Aborted finishes enter the suspend request-error waterfall; Retry re-prepares within the same turn and step, while Unhandled preserves the structured failure and NonCancellable finalization writes step/end and turn/end. No listener means no implicit retry. |
| A8 | passed | brief.md | **A8 — 取消与释放**：caller cancellation 不进入 retry listener；已有可见前缀时写入 `assistant/message(interrupted=true)`，随后以 aborted 关闭 turn；Agent/Session/Provider/Host dispose 后无活动 Job、prepared call 或重复事件。 | Caller cancellation bypasses request-error handling, cancels flow collection, persists visible output as assistant/message with interrupted=true, closes the turn as aborted, disposes prepared calls, and joins Context-owned driver jobs; cancellation contracts passed. |
| A9 | passed | brief.md | **A9 — Session API 与订阅**：provider-neutral service 能执行 `list/create/history/prompt/cancel`；事件订阅使用 Session/Agent typed events，live projection 与从日志 replay 相同，Relay 只转发相同 contract。 | SessionApi exposes provider-neutral list/create/history/prompt/cancel contracts over Agent and Session identities; history comes directly from the Session log projection, prompt returns an observable idle handle, and the API contract passed. |
| A10 | passed | brief.md | **A10 — Tools 多 step**：工具 schema 与调用/结果事件可扩展注册；tool call 必须有权威 CallId、配对 result 和独占调度，工具步骤完成后继续同一 turn，无法执行的工具以稳定错误结束而不伪造 assistant tool history。 | Tools provides schema registration, session-scoped authoritative CallId exclusivity, semaphore scheduling, paired tool/call and tool/result events, same-turn multi-step continuation, and stable rejection of unavailable or invalid calls without fabricated assistant tool history. |
| A11 | passed | brief.md | **A11 — Session Persistence**：file-backed provider 原子 flush/load，拒绝未知 required event，保留 ignorable event，能恢复未完成 turn/step 为结构化 interrupted/crash-repair 状态；恢复后的 `deriveMessages`、Inbox 和 request fold 与重启前一致。 | FileSessionPersistence uses temporary-file replacement, validates envelope metadata and known serializers, rejects unknown required events before SessionStore mutation, preserves unknown ignorable events, repairs unfinished turns once, and restores messages, request folds, and Inbox projection; all five JVM persistence contracts passed. |
| A12 | passed | brief.md | **A12 — 持久化 llm-retry 与验证**：`llm-retry` 监听 request-error，在等待前写 `llm/retry`、开始新 attempt 前写 `llm/retry-started`，遵守 resolved RetryPolicy 的上限/退避/取消；重启后不重复已提交 attempt。JVM、Android、iOS Simulator 模块测试、Desktop Runtime/Loader fixture、整仓 build、diff-check 和依赖审计通过；平台无法执行的测试必须记录风险。 | Independent verification passed the six target JVM modules (25/25), shared Android Debug and Release modules (20/20 each), iOS Simulator Arm64 Debug/Release production compilation, Desktop fixtures (6/6), Runtime/Loader/Include/Profile/Boot fixtures (133/133), full repository build, diff-check, and dependency audit. |
| A13 | passed | specs/agent/spec.md | 没有 factory 时创建失败 WHEN `AgentRegistry.create()` 在没有有效 factory 时调用，THEN 返回稳定 `NO_FACTORY` 错误，不创建 Session、不发布 created、不残留 Agent。 | Creating without a factory throws AgentErrorCode.NO_FACTORY before Session creation and leaves no created event or registry entry. |
| A14 | passed | specs/agent/spec.md | 重复 id 和旧 handle 隔离 WHEN 同一 id 已经 live 或旧 handle 在新实例注册后执行 dispose，THEN 前者原子失败且不覆盖，后者不能移除新实例。 | Creating and live entries atomically reserve Agent ids, duplicate ids cannot overwrite a live Agent, and detach compares owner identity so an old handle cannot remove a replacement. |
| A15 | passed | specs/agent/spec.md | durable Inbox 与 live projection 一致 WHEN insert、claim、replace、clear 被调用，THEN Session event 先提交，随后内存队列更新；从这些事件 replay 的队列与 live 队列逐项相同。 | All four Inbox splice operations are durable-first, preserve version continuity, and replay to the same NEXT_TURN and NEXT_STEP contents as the live snapshot. |
| A16 | passed | specs/agent/spec.md | 并发取消最终收敛 WHEN wakeup、cancel、awaitIdle 和 dispose 并发发生，THEN 所有 driver job 被回收，状态最终为 IDLE/Disposed，且没有重复 created/disposed 或未释放 child effect。 | Cancellation marks stopping before cancelling and joining the driver, shutdown marks disposed and joins the managed scope, and final queue reconciliation handles concurrent wakeups without orphaned work; keepInbox, awaitIdle, registry disposal, and child cleanup paths passed. |
| A17 | passed | specs/agent-loop/spec.md | scripted 文本响应 WHEN Agent 收到一条 follow-up 且 adapter 输出文本流，THEN 事件严格符合 turn/step 顺序，Session 可派生唯一 assistant history，Agent 最终回到 IDLE。 | The scripted text scenario produces one complete ordered turn, one assistant surface message, replayable history, and a final IDLE Agent; its contract passed on JVM and Android. |
| A18 | passed | specs/agent-loop/spec.md | 两条 follow-up 串行 WHEN 同一 Agent 连续收到两条 follow-up，THEN 产生两个顺序 turn，不并行驱动同一 Agent，第二条在第一条 step/turn 完成后领取。 | Two follow-ups are processed by one driver as sequential turns. The prior finalization interleaving is covered by the repeated 32-boundary insertion regression, which finishes every turn and leaves NEXT_TURN empty. |
| A19 | passed | specs/agent-loop/spec.md | request-error Retry WHEN 首次 attempt 返回 `SERVER` 且 request-error listener 返回 Retry，THEN 同一 turn/step 只有第二次成功 attempt 产生 assistant surface message；失败 chunks 仍可审计。 | A retryable SERVER failure followed by Retry executes a second attempt inside the same turn and step; failed chunks remain auditable but only the successful attempt contributes an assistant surface message. |
| A20 | passed | specs/agent-loop/spec.md | 未处理错误 WHEN request-error 没有 listener 或 listener 返回 Unhandled，THEN 不发生隐式重试，step/end 必须写入，turn/end 包含原始结构化 failure。 | With no recovery decision, provider Error or Aborted finishes do not loop implicitly; the original LlmFailure is retained in ErrorTurnEndReason and step/end plus turn/end are always appended. |
| A21 | passed | specs/agent-loop/spec.md | 流中取消 WHEN Provider Flow 在已有可见前缀后被 caller 取消，THEN 上游 Flow 被取消，写入 interrupted assistant message，turn 以 aborted 关闭，retry listener 不被调用。 | Cancellation after visible provider output emits exactly one interrupted assistant message and an aborted turn while never invoking the request-error retry waterfall; the dedicated contract passed. |
| A22 | passed | specs/llm-retry/spec.md | 持久化重试 WHEN 第一次 attempt 返回可重试 `SERVER`，THEN 写入 retry、执行退避、写入 retry-started、重新准备并执行下一次 attempt；重启后从 retry 事件恢复剩余次数，成功后不再产生额外 attempt。 | The cross-Runtime persistence contract confirms llm/retry is written before waiting, llm/retry-started precedes the resumed request, restart executes exactly the remaining attempt without duplicating committed request/header, writes a terminal event, and preserves final history across another load. |
| A23 | passed | specs/session-api/spec.md | API 与 replay 一致 WHEN 通过 API create → prompt → history，THEN 返回的 history 与同一 Session 完整日志执行 `deriveMessages()` 相同；cancel 后不残留 Agent job。 | SessionApi create→prompt→history returns the same projection as Session.deriveMessages, preserves created ordering, and cancel followed by idle convergence leaves no active Agent driver. |
| A24 | passed | specs/session-persistence/spec.md | flush/load/recover WHEN Session append 多条事件并 flush，随后重新创建 Runtime/Plugin 并 load，THEN events、messages、request header/context 和 Inbox replay 与原实例一致；未知 required event 被拒绝且不产生半个 Session。 | Persistence contracts cover flush/load event and message equality, request-header restoration, Inbox replay, one-time crash repair, known-extension validation, unknown-required rejection without a half-loaded Session, and ignorable-event round trip. |
| A25 | passed | specs/tools/spec.md | 多 step 工具闭环 WHEN adapter 输出一个权威 tool call 且 Tools service 返回结果，THEN 同一 turn 追加配对 tool result 并进入下一 step，最终 assistant message 可从日志 replay；重复 result 或缺失 call id 原子失败。 | The tool-loop contract confirms authoritative CallId handling, exactly paired call/result events, continuation into the next step of the same turn, replayable final assistant output, duplicate-result rejection, and stable unavailable-tool failure. |

## 检查

_没有记录 Runtime 检查。_

## 阻塞项

_无。_

## 风险与跳过的工作

- Linux cannot link and execute the iosSimulatorArm64 test program; only Debug and Release production source compilation was verified.
- HARNESS_OPENAI_LIVE_API_KEY is unset, so external OpenAI live-network acceptance was not executed.
- FileSessionPersistence is JVM-only by design; Android verification covers shared Agent, AgentLoop, SessionApi, Tools, and retry contracts rather than file I/O.
- The idle-finalization regression exercises repeated deterministic boundary insertions, but arbitrary scheduler interleavings are not exhaustively model-checked.
- The candidate remains in a broadly dirty working tree with the new modules untracked; verification was read-only and did not alter or commit files.

## 之前的迭代

| 目标周期 | 迭代 | 尝试 | 结果 | 未解决项 | 摘要 | 完成时间 |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | fail | A11, A12, A22, A24 | 独立只读复核确认取消竞态、Context/Session 生命周期、已知扩展事件反序列化校验和即时 retry 去重修复有效，8 项 Runtime 检查全部通过；但未知 ignorable 持久化事件被错误拒绝，且 llm-retry 尚无跨重启继续同一 retry attempt 的闭环或测试，因此 A11、A12、A22、A24 未通过。 | 2026-09-03T11:38:21.071Z |
| 1 | 2 | 1 | recovery | — | Repair verification passed for A11, A12, A22, A24; final full verification is required. | 2026-09-03T12:22:18.349Z |
| 1 | 2 | 2 | fail | A4, A6, A18 | Independent readonly review found 22 passed and 3 failed acceptance scenarios. The sole blocking defect is a concrete AgentLoop race that can strand a queued followup and make awaitIdle return while work remains; fix and rerun full Verify. | 2026-09-03T12:35:27.750Z |
| 1 | 3 | 1 | execution-error | — | Native Verifier response was invalid: Native Verifier acceptance coverage is invalid (duplicate: none; unknown: A1, A2, A3, A5, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A19, A20, A21, A22, A23, A24, A25; missing: none) | 2026-09-03T13:06:02.798Z |
| 1 | 3 | 2 | recovery | — | Repair verification passed for A4, A6, A18; final full verification is required. | 2026-09-03T13:18:58.818Z |
| 1 | 3 | 3 | pass | — | Independent read-only verification passes all 25 acceptance items. Source review confirms the A4/A6/A18 queue-finalization race is addressed by Agent-mutex reconciliation, while target JVM/Android suites, iOS production compilation, persistence recovery, Desktop/Loader fixtures, dependency boundaries, full build, and diff-check all succeeded. Remaining limitations are platform execution, live-network coverage, JVM-only file persistence, and non-exhaustive scheduler exploration. | 2026-09-03T13:39:28.096Z |



## 结论

Independent read-only verification passes all 25 acceptance items. Source review confirms the A4/A6/A18 queue-finalization race is addressed by Agent-mutex reconciliation, while target JVM/Android suites, iOS production compilation, persistence recovery, Desktop/Loader fixtures, dependency boundaries, full build, and diff-check all succeeded. Remaining limitations are platform execution, live-network coverage, JVM-only file persistence, and non-exhaustive scheduler exploration.

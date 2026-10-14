# Worktrees 聊天归档 K：静止恢复能力（entry-k1）

日期 2026-10-10。承接 I（提交后 quiesce）与 J（页面归档按钮）。归档方向已经贯通到页面，但归档只写元数据并停止 Agent，没有恢复消费链：取消归档只删除持久记录，聊天不会重新可读，而且默认 `SessionPersistence.load` 会修复开放回合并 flush、`AgentRegistry.restore` 会触发 `LlmRetryPlugin` 的 retry 续跑。本批按分析文档第 4 项与父线程约束 5、6 建立**显式静止恢复能力**：从 durable 历史恢复精确会话与 Agent，不修复、不 flush、不排队、不发送任何模型请求。

## 写集（6 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | modules/session-persistence/.../RoomSessionPersistence.kt | 新增 `SessionQuiescentRejection`、`SessionQuiescentLoad` 与 `SessionPersistence.loadQuiescent(id)`；Room 实现按快照做只读判定并复用 `store.load`，不调用 `repairOpenTurn`/`flush` |
| 2 | modules/agent/.../Agent.kt | 新增 `QuiescentRestorableFactory` 声明接口 |
| 3 | modules/agent/.../AgentRegistry.kt | 新增 `AgentQuiescentRestore` 与 `restoreQuiescent(session, options)`：未声明工厂直接拒绝，声明工厂复用既有 `restore` |
| 4 | modules/agent-loop/.../AgentLoopPlugin.kt | 默认 loop 工厂改由 `LoopAgentFactory : QuiescentRestorableFactory` 注册（Agent 只在显式 send/resume 时启动 driver） |
| 5 | modules/session-persistence/test/.../SessionQuiescentRestoreContractTest.kt（新增） | 5 方法 |
| 6 | modules/agent/test/.../AgentQuiescentRestoreContractTest.kt（新增） | 2 方法 |

档案 SHA 见同目录 `files.sha256`。

## 行为

- **拒绝而非修复。** `loadQuiescent` 先读快照并做与 `load` 相同的结构性校验（不合规即 `INVALID`），随后在任何修复之前判定：存在 pending llm retry → `PENDING_RETRY`；存在开放回合（有 TURN_START 无 TURN_END）→ `OPEN_TURN`；Inbox 重放后仍有 NEXT_TURN/NEXT_STEP 项 → `PENDING_INBOX`；行不存在 → `NOT_FOUND`。全部通过才 `SessionStore.load` 发布 live Session。与默认 `load` 的关键差别是**不调用 `repairOpenTurn`、不 flush、不清空 Inbox、不触发 retry**，所以不满足条件的会话不会被“修复成可恢复”。
- **声明式工厂门槛。** `QuiescentRestorableFactory` 表示该工厂的 Agent 在被挂接到已恢复 Session 时不自主工作。`AgentRegistry.restoreQuiescent` 在同一 mutex 决策内检查当前工厂是否声明该能力，未声明直接返回 `Rejected`，不做任何挂接；声明则复用既有 `restore`（不新增事件、不启动 driver）。默认 `AgentLoopPlugin` 注册的 `LoopAgentFactory` 实现了该声明，因为 loop Agent 只在显式 `send` 或尝试续跑时才启动 driver。
- **非破坏性接线。** `loadQuiescent` 默认实现返回 `UNSUPPORTED`，未实现该能力的持久化后端不会被静默当作支持；既有 `load`/`restore` 语义逐字节保留，普通恢复路径仍会修复开放回合并续跑 pending retry。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| SessionQuiescentRestoreContractTest | quiescentLoadRestoresExactHistoryAndAttachesADeclaredLoopAgentWithoutRequests / openTurnIsRejectedInsteadOfBeingRepaired / pendingInboxIsRejectedInsteadOfBeingCleared / pendingRetryIsRejectedInsteadOfResuming / missingSessionIsRejectedAsNotFound | 5/5 |
| AgentQuiescentRestoreContractTest | undeclaredFactoryIsRejectedForQuiescentRestoreButStillRestoresNormally / declaredFactoryIsGrantedWithoutAutonomousWorkOrNewEvents | 2/2 |

关键证据：恢复一条完整聊天后 `calls` 仍为 1（0 新模型请求），恢复会话事件与 durable 事件逐元素相等，`persistence.inbox` 两个队列为空，重开 Room 后事件行数与恢复前一致；开放回合被拒后 durable 行里没有出现补写的 TURN_END（未被修复）；pending retry 被拒后 `calls` 仍为 1 且没有新 live 会话。

回归（0 失败）：SessionPersistenceContractTest 7/7（session-persistence 合计 12/12）；agent 26/26（含 AgentArchiveContractTest 4/4、AgentRegistryAdmissionContractTest 6/6、InboxAdmissionContractTest 8/8、AgentContractTest）；jvm-app 24/24（DesktopWorktreeChatArchiveIntegrationTest 10/10、DesktopWorktreePinIntegrationTest 6/6、DesktopManagedWorktreeIntegrationTest 8/8）。完整 jvm-app JVM build：exit 0。

agent-loop 按类运行干净：AgentLoopContractTest 10/10、AgentInstructionsContractTest 7/7、AgentLoopContextualToolsTest 1/1。整模块连跑时出现 1 个 `UncaughtExceptionsBeforeTest`，且失败在 `AgentInstructionsContractTest` 与 `AgentLoopContextualToolsTest` 之间漂移、单独运行均通过，与 H 批次记录的后台 driver 异常泄漏一致，属既有现象而非本批引入。

## 边界

本批提供模块级静止恢复能力并接入声明的 loop 工厂，但尚未接线到 SessionApi/Host API 与页面：取消归档仍只删除持久记录，Recent/当前聊天仍未消费归档过滤。恢复只证明同一 Runtime 内不产生模型请求与事件；跨进程、Room/process/PTY 静止、显式“静止可读”状态的页面表达、Recent 同步与删除保护仍待后续。无截图、原生窗口或像素验收。各批证据独立，整体目标 active。

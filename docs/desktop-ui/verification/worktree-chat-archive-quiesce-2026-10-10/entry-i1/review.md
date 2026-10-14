# Worktrees 聊天归档 I：提交后 quiesce 与 Agent 支撑归档（entry-i1）

日期 2026-10-10。承接 H（entry-h1：入口层门禁与模块级独占归档能力）。H 已经提供 `AgentRegistry.beginArchive`，但 App 层 `setArchived` 仍走 F/G 的 reservation 路径，尚未真正停止一个 Agent 支撑的聊天。本批把 `setArchived` 切到 `beginArchive`，并在**durable 提交之后**对真实 Agent 做 best-effort quiesce：停止 Agent、detach Session、移除 registry 记录。归档按钮仍未启用。

## 写集（5 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt | `beginArchive` 的 commit 从 `detach(...)` 改为新的私有 `quiesceArchivedAgent(...)`：dispose owner Context（停 Agent loop、Session detach）、移除 registry 记录、发 Dispose 通知；不再调用 `Agent.cancel` |
| 2 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt | 用 `ArchiveFence`（Fenced/Busy/Rejected/NotLive）替换 `ArchiveReservation`/`reserveArchive`，改走模块 `SessionAdmission.acquireArchive` |
| 3 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt | 新增 `archiveAgents` 构造参数与 `LiveFence`（`AgentFence`/`SessionFence`）；`setArchived` 优先走 `beginArchive`，提交后 NonCancellable 内 `commit()` quiesce |
| 4 | apps/jvm-app/src/main.kt | 注入 `archiveAgents = { id -> AgentKey.beginArchive(AgentId(id)) }` |
| 5 | apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreeChatArchiveIntegrationTest.kt | 新增 2 方法（合计 10）；fixture 增加可选 `archiveAgents` |

档案 SHA 见同目录 `files.sha256`。

## 行为

- **提交后 quiesce 不再经过 `Agent.cancel`。** 归档门禁已关闭该 Session 的全部 operation 许可，`AgentLoopAgent.cancel` 一进入就调用 `session.acquireOperationPermit()` 并被拒绝，因此旧实现里 `detach` 的 cancel 只能以异常收场，被计为 quiesce warning。新的 `quiesceArchivedAgent` 改为 dispose owner Context——`AgentLoopAgent.shutdown()` 取消 driver job 与 scope，效果覆盖 cancel 的停止语义——随后移除 registry 记录并发出 Dispose 通知。Session detach 走的是 catalog 级许可，正是门禁刻意保留给 owner 的通道。
- **App 层优先真实 Agent 门禁。** `setArchived(desiredArchived=true)` 且 `archiveAgents != null` 时调用 `beginArchive`：Granted → `AgentFence`；Busy → Rejected（“仍有在途操作”）；Rejected → 对应 reason；NotRegistered（仅持久、无 live 实例）→ 无 fence，直接提交。`archiveAgents == null` 时回落到 observer 的 `acquireArchive`（SessionFence）。取消归档不取 fence。
- **顺序与报告分离。** 最终确认与文件 CAS 与 quiesce 都在 `NonCancellable` 内；durable 提交先成功，再执行 `fence.commit()`，最后 `finally { fence.dispose() }` 释放资格。quiesce 失败返回 warning，与目录 fsync 的 durability warning 分开报告；durable 提交仍然是 Applied。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| AgentArchiveContractTest | archiveStopsAgentAndDetachesSessionAndReleasesTheGate / archiveIsBusyInsteadOfQueueingWhileActivityIsInFlight / disposingAnArchivePreparationWithoutCommitKeepsTheAgent / archiveOfUnknownAgentIsNotRegistered | 4/4 |
| SessionArchiveContractTest | archiveGateFencesLoadAndAttachAtTheEntryLayer / withoutAGateTheEntryLayerIsUnchanged / archiveOwnershipClosesOperationPermitsButKeepsCatalogAvailable / archiveOwnershipIsBusyInsteadOfQueueingAndIsExclusive / stoppedStoreRejectsArchiveOwnership | 5/5 |
| DesktopWorktreeChatArchiveIntegrationTest | 含 archivingALiveAgentChatStopsAndDetachesIt（真实 Agent 支撑聊天归档：warning 为 null、registry 记录移除、Session detach、文件写入正确）与 agentBackedArchiveIsBusyWhileTheAgentHasInFlightActivity（持牌在途时 Busy 拒绝、不写文件、Agent 保留） | 10/10 |

回归（0 失败）：AgentRegistryAdmissionContractTest 6/6、InboxAdmissionContractTest 8/8（agent 模块合计 18/18）；SessionAdmissionContractTest 8/8（session 模块合计 13/13）；DesktopWorktreeSessionObserverTest 11/11、DesktopWorktreePinIntegrationTest 6/6、DesktopManagedWorktreeIntegrationTest 8/8、WorktreePinInteractionTest 3/3（jvm-app 合计 38/38）。完整 jvm-app JVM build：exit 0（Build successful）。日志见 `logs/`。

## 边界

本批让 `setArchived` 在 durable 提交后真正停止一个 Agent 支撑的聊天，并把 warning 与 durable 结果分开报告；这只证明**同一 Runtime 内**的 Agent 停止与 Session detach。独立 JSON 的 CAS 不证明另一进程 live Agent 已静止；Room/process/PTY 静止、显式静止恢复（0 模型请求）、Worktrees 与 Recent/当前聊天接线、移除保护（删除）仍待后续。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。各批证据独立，整体目标 active。

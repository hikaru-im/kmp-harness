# Worktrees 聊天归档 H：入口层持久门禁与独占归档能力（entry-h1）

日期 2026-10-10。承接 chain-f1/chain-f2（App 层归档链与忙碌门禁）。本批把归档检查下沉到**拥有 Session/Agent 入口点的模块层**，并提供模块级独占归档能力。

## 写集（9 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | modules/session/src/im/hikaru/harness/session/SessionError.kt | 新增 SessionErrorCode.ARCHIVED |
| 2 | modules/session/src/im/hikaru/harness/session/SessionArchive.kt（新增） | SessionArchiveGate（可选持久归档身份）+ SessionArchiveKey |
| 3 | modules/session/src/im/hikaru/harness/session/SessionAdmission.kt | 新增 SessionArchiveToken/SessionArchiveAcquire 与 acquireArchive/releaseArchive；独占归档只关闭目标 Session 的 operation 许可，保留 catalog/detach |
| 4 | modules/session/src/im/hikaru/harness/session/SessionStore.kt | load 与 attach 在入口层查询 SessionArchiveGate，已归档身份抛 SessionException(ARCHIVED) |
| 5 | modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt | 新增 beginArchive(id)：按完整身份取独占归档资格，返回 AgentArchivePreparation |
| 6 | apps/jvm-app/.../desktop/git/DesktopWorktreeChatArchiveGate.kt（新增） | 生产门禁实现：按完整 header 指纹查询归档存储；读取失败 fail closed |
| 7 | apps/jvm-app/src/main.kt | 正式 bootstrap 注入 DesktopWorktreeChatArchiveGate 到 SessionArchiveKey |
| 8 | modules/session/test/.../SessionArchiveContractTest.kt（新增） | 5 方法 |
| 9 | modules/agent/test/.../AgentArchiveContractTest.kt（新增） | 4 方法 |

## 行为

- **入口层门禁**：SessionStore.load/attach 在取得许可前查询可选 SessionArchiveGate；未提供 gate 时行为与之前逐字节一致（既有测试不提供 gate）。门禁实现按 observer 的完整 header 指纹比对归档键（id + headerFingerprint），坏文档向上升级为拒绝加载（fail closed）。
- **独占归档资格**：SessionAdmission.acquireArchive 在同一 mutex 决策内关闭目标 Session 的新 operation 许可并在有在途活动/既有 reservation/catalog 活动时返回 Busy，不排队；catalog 与 detach 许可保持可用，使 owner 仍能停止 Agent。stopOwner 清空归档资格。
- **Agent 归档**：AgentRegistry.beginArchive(id) 按 id + 实例 identity 取独占资格；commit() 复用既有 detach（取消 Agent、释放 owner Context、dispose → Session detach），失败返回 warning 而不抛出；dispose() 只释放资格、绝不取消。durable 提交与 detach 分开报告。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| SessionArchiveContractTest | archiveGateFencesLoadAndAttachAtTheEntryLayer / withoutAGateTheEntryLayerIsUnchanged / archiveOwnershipClosesOperationPermitsButKeepsCatalogAvailable / archiveOwnershipIsBusyInsteadOfQueueingAndIsExclusive / stoppedStoreRejectsArchiveOwnership | 5/5 |
| AgentArchiveContractTest | archiveStopsAgentAndDetachesSessionAndReleasesTheGate / archiveIsBusyInsteadOfQueueingWhileActivityIsInFlight / disposingAnArchivePreparationWithoutCommitKeepsTheAgent / archiveOfUnknownAgentIsNotRegistered | 4/4 |

回归（0 失败）：SessionAdmissionContractTest 8/8；AgentRegistryAdmissionContractTest 6/6；InboxAdmissionContractTest 8/8；AgentDriverAdmissionContractTest 9/9；DesktopWorktreeChatArchiveIntegrationTest 8/8；DesktopWorktreeSessionObserverTest 11/11。

完整 JVM app build：exit 0（Build successful）。日志见 logs/。

## 边界

本批提供并激活**入口层**门禁（load/attach 拒绝已归档身份）与模块级独占归档能力；App 层的 setArchived 仍用 chain-f2 的 reservation 路径，尚未切换到 beginArchive 做「提交后 quiesce」。Room/process/PTY 静止、显式静止恢复（0 模型请求）、Worktrees 与 Recent/当前聊天接线仍待后续。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。

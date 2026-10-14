# Worktrees 聊天归档 L：取消归档恢复接线（entry-l1）

日期 2026-10-10。承接 K（模块级静止恢复能力）。K 提供了 `loadQuiescent` 与 `restoreQuiescent`，但没有任何调用者：取消归档只删除持久记录，被归档停止的聊天不会重新出现，Recent 也只列 live Agent。本批把静止恢复接到生产路径：取消归档在 durable 提交成功后按该聊天自己的历史模型重新挂接 Agent，并把这个结果与持久提交分开报告。

## 写集（7 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | modules/session-persistence/.../RoomSessionPersistence.kt | `SessionQuiescentLoad.Loaded` 改为携带 `SessionHandle`（保留 `session` 便捷属性），使恢复被拒时可以释放刚发布的 live Session |
| 2 | apps/shared/.../client/git/ManagedWorktreeModels.kt | 新增 `ManagedWorktreeChatRestore`；`Applied` 增加默认空值字段 `restore` |
| 3 | apps/shared/.../client/git/GitPages.kt | 归档回执按 `restore` 结果渲染取消归档后的恢复结论 |
| 4 | apps/jvm-app/.../desktop/git/DesktopWorktreeChatRestoreService.kt（新增） | 生产恢复服务：AlreadyLive → loadQuiescent → 用聊天自身的 requestContext 取 provider/model → restoreQuiescent；被拒时释放 handle |
| 5 | apps/jvm-app/.../desktop/git/DesktopManagedWorktreeRepository.kt | 新增 `restoreChat` 参数（放在 `archiveStore` 之前，避免尾随 lambda 重载歧义）；取消归档在 NonCancellable 内调用并写入回执 |
| 6 | apps/jvm-app/src/main.kt | 注入 `DesktopWorktreeChatRestoreService(context)::restore` |
| 7 | apps/jvm-app/test/.../DesktopWorktreeChatArchiveIntegrationTest.kt | fixture 增加 `restoreChat`；新增 3 方法（合计 13） |

档案 SHA 见同目录 `files.sha256`。

## 行为

- **恢复顺序在持久提交之后。** `setArchived(desiredArchived=false)` 先完成最终核验与文件 CAS，再在 `NonCancellable` 内调用 `restoreChat`，最后把结果放进 `Applied.restore`。恢复失败不会把已提交的取消归档改写为失败，也不会重投；回执里恢复结论与 `durabilityWarning` 是两条独立信息。
- **按聊天自己的模型恢复。** 恢复服务先看该 id 是否已经 live（`AlreadyLive`），否则 `loadQuiescent` 只接受无开放回合、无待执行 Inbox、无 pending retry 的快照（`NoHistory` 表示没有持久历史，其余拒绝映射为 `Failed(reason)`）。provider/model 取自会话自身的 `requestContext()`，也就是这条聊天上一次真正用过的模型，而不是设置页当前选择。
- **拒绝时不留下半成品。** `restoreQuiescent` 需要工厂声明 `QuiescentRestorableFactory`（默认 loop 工厂已声明）。若挂接被拒（例如该聊天从未产生模型请求、拿不到 provider/model），服务会 dispose 刚由 `loadQuiescent` 发布的 Session，避免留下“live 但没有 Agent”的泄漏态，`list()` 与 Recent 都不会出现它。
- **尾随 lambda 兼容性。** 新参数插在 `archiveStore` 之前，保持最后一个构造参数不是函数类型，否则 `DesktopManagedWorktreeRepository(backend, store) { owner }` 会在主构造与保留的 owner-validator 次构造之间产生重载歧义（本次实测复现并修正）。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| DesktopWorktreeChatArchiveIntegrationTest | unarchivingRestoresTheChatWithoutRepairOrANewModelRequest / unarchivingWithoutARestoreServiceStillCommitsAndReportsNoRestore / restoreWithoutAModelOriginIsReportedFailedAndLeavesNoAgentlessSession（另有既有 10 类） | 13/13 |

关键证据：真实 Agent 聊天归档后 `registry.list()` 与 live Session 都不再包含该 id；取消归档后 `applied.restore == Restored`、Agent 与 Session 重新存在、归档集合清空，且恢复后的事件与归档前的 durable 事件逐元素相等（证明没有修复回合、没有 flush、没有新增事件），`requestContext().provider` 仍为该聊天历史模型。未接线恢复服务时回执 `restore` 为 null 且 durable 记录照常删除。无模型请求来源的聊天恢复被报为 Failed，并断言没有留下 agentless live Session。

回归（0 失败）：jvm-app 32/32（WorktreePinInteractionTest 4/4、ManagedWorktreeInteractionTest 3/3、DesktopWorktreePinIntegrationTest 6/6、DesktopManagedWorktreeIntegrationTest 8/8、DesktopWorktreeSessionObserverTest 11/11）、WorktreeSessionObservationInteractionTest 4/4、session-persistence 12/12、shared 10/10（归档 controller 5/5 + 固定 controller 5/5）。完整 jvm-app JVM build：exit 0。

## 边界

本批让取消归档真正把聊天恢复成可运行会话，因此它会在下一次 Recent 刷新时自动重新出现（Recent 列 live Agent）；但**隐藏已归档聊天**这一侧仍未做，Recent/主会话仍未消费归档过滤，独立归档管理页不在范围。需要模型来源的聊天才能恢复为可运行；纯文本且从未发起模型请求的聊天会如实报 Failed。跨进程、Room/process/PTY 静止、删除保护仍待。本批修改了 `RoomSessionPersistence.kt`，K 批次为该文件记录的 SHA 已被本批取代（K 的其余文件与结论不变）。无截图、原生窗口或像素验收。各批证据独立，整体目标 active。

# Worktrees 聊天归档 G：忙碌拒绝门禁验收（chain-f2）

日期 2026-10-10。承接本目录 chain-f1（归档持久链 F）。本批在 F 的持久提交前加入同 Runtime 的忙碌拒绝门禁，未启用任何页面按钮。

## 写集（3 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt | 新增内部 ArchiveReservation 结果类型与 reserveArchive（使用模块 SessionAdmission.reserve） |
| 2 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt | setArchived 提交前取精确 live 会话的独占 reservation；Busy/Rejected 直接 Rejected，提交后 finally 释放 |
| 3 | apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreeChatArchiveIntegrationTest.kt | 新增 liveChatWithInFlightOperationRejectsArchiveAsBusy、archiveReservationIsReleasedAfterCommitAndNeverBlocksLaterWork（合计 8 方法） |

chain-f1 的 DesktopManagedWorktreeRepository.kt 与 DesktopWorktreeChatArchiveIntegrationTest.kt 的 SHA 已被本批取代；本目录 files.sha256 记录最终值。DesktopWorktreeSessionObserver.kt 在 chain-f1 未改，本批首次修改。

## 行为

- reserveArchive 先做与 observe 相同的 binding/owner/host/home/服务 fence，再按 id 取精确 live Session，并要求其 header 指纹等于归档键；随后调用模块已有的 SessionAdmission.reserve。
- 结果映射：Reserved 持牌提交；Busy 直接返回 Rejected（"仍有在途操作"）；Rejected 返回对应 issue reason；NotLive（durable-only 聊天）无 live 实例，无在途活动，直接提交不持牌。
- reservation 仅在最终确认与文件 CAS 期间持有；Git/observer 复核在持牌之前，模块 admission 状态锁不跨 IO、事件通知或 join 持有。提交始终在 finally 中释放，取消路径也释放。
- 不排队、不代替用户 cancel、不清 inbox、不停进程；不 load/flush/repair。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| DesktopWorktreeChatArchiveIntegrationTest | 既有 6 方法 + liveChatWithInFlightOperationRejectsArchiveAsBusy（持牌 live 会话归档被拒且不写文件；释放后可归档）+ archiveReservationIsReleasedAfterCommitAndNeverBlocksLaterWork（提交后门禁释放，可再取活动许可并取消归档到 revision 2） | 8/8 |

回归（0 失败）：ManagedWorktreePinControllerTest 5/5；ManagedWorktreeChatArchiveControllerTest 5/5；DesktopWorktreeSessionObserverTest 11/11；DesktopWorktreePinIntegrationTest 6/6；DesktopManagedWorktreeIntegrationTest 8/8；DesktopWorktreeChatArchiveStoreTest 4/4；WorktreePinInteractionTest 3/3。

完整 JVM app build：exit 0（Build successful）。日志见 logs/。

## 边界

本批只证明**同一 Runtime 内**的忙碌拒绝与身份有效性：归档/取消归档会在持牌窗口内关闭该会话的新准入并拒绝在途活动。独立 JSON 的 CAS/文件锁不证明另一进程的 live Agent 已静止；已归档身份的**持久入口检查**（SessionStore.load / AgentRegistry.create/restore 主动拒绝已归档 key）仍未完成，因此直接模块调用仍可重新接入——这属于后续模块层批次。Room/process/PTY 静止、显式静止恢复（0 模型请求）、Worktrees 与 Recent/当前聊天接线仍待后续。归档按钮未启用；无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。

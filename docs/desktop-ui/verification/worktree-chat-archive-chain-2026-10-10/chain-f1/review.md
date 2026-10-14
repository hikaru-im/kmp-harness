# Worktrees 聊天归档 F：归档持久链验收（chain-f1）

日期 2026-10-10。承接批次 E（独立归档存储 DesktopWorktreeChatArchiveStore）与批次 D（Runtime 生命周期等待边界）。本批把归档元数据接入与 Pin 相同的读取/写入链，未启用任何页面按钮。

## 写集（6 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt | 接口新增 setArchived 默认不可用；ManagedWorktreeInventory.archiveRead；ManagedWorktreeState.pendingArchive/archiveReceipt/archiveBusy/canArchive |
| 2 | apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt | 新增 setArchived；refresh 保留 pendingArchive/archiveReceipt |
| 3 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt | 新增 archiveStore、setArchived、readArchives；inventory 填充 archiveRead |
| 4 | apps/jvm-app/src/main.kt | 注入真实 managed-chat-archive.json |
| 5 | apps/shared/test/im/hikaru/harness/client/git/ManagedWorktreeChatArchiveControllerTest.kt（新增） | 5 方法 |
| 6 | apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreeChatArchiveIntegrationTest.kt（新增） | 6 方法 |

apps/*/…/git/ 整包在此前未纳入 git（git status 显示为未跟踪目录），因此本批只记录 after SHA，无 before 基线；apps/jvm-app/src/main.kt 的其余 diff 为本批之前的既有改动。逐文件 SHA 见 files.sha256。

## 行为

- setArchived 与 setPinned 同构：提交前核验真实本地 owner、绑定 identity == null、仓库指纹、目标工作树非 missing/prunable/bare、expectedRevision，以及观察者给出的精确 header 身份（id + headerFingerprint）；Git 复核后再次核验 header 身份与 owner。
- 仅有限的最终确认与文件 CAS 位于 NonCancellable；提交后目录 fsync 失败仍返回 Applied 并带 durability warning。Rejected 覆盖核验失败与存储冲突，其余为 Unknown；错误/未知只允许显式刷新，不自动重投。
- 控制器沿用 read epoch 与 binding/operation ticket 分离、busy 重拒（archiveBusy）、回执归属核对、成功后重读磁盘发布状态，不本地翻转归档位。
- 归档键为 ChatArchiveKey(id, headerFingerprint)，与 PinKey 身份同源但类型独立；不写入 SessionHeader，不改变 PinKey。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| ManagedWorktreeChatArchiveControllerTest | archiveWithExactObservedKeyDispatchesOnceAndRefreshesAfterRevision / unavailableUnknownStaleAndDuplicateClicksNeverDispatch / lateReceiptCannotLeakAcrossBindingOrClose / revisionRefreshDuringAcceptedArchiveKeepsReceiptAndRefreshes / mismatchedOwnerRepositoryOrKeyReceiptNeverPublishesSuccess | 5/5 |
| DesktopWorktreeChatArchiveIntegrationTest | realLiveAndDurableArchiveRoundTripWithoutSessionOrGitWrites / freshBootstrapReadsDurableArchiveWithoutLoadingHistory / staleRevisionAndChangedHeaderRejectBeforeWrite / ownerOrCapturedServiceReplacementCannotWrite / diskConflictAndMalformedStoreRetainBytes / directorySyncFailureReturnsCommittedWarning | 6/6 |

回归（0 失败）：ManagedWorktreePinControllerTest 5/5；DesktopWorktreeChatArchiveStoreTest 4/4；DesktopWorktreePinStoreTest 7/7；DesktopWorktreePinIntegrationTest 6/6；DesktopManagedWorktreeIntegrationTest 8/8；DesktopManagedWorktreeStoreTest 5/5；DesktopWorktreeSessionObserverTest 11/11；WorktreePinInteractionTest 3/3。

完整 JVM app build：./kotlin build -p jvm -m jvm-app exit 0（Build successful）。日志见 logs/。

integration 用真实临时 Runtime/Room/Git 与正式重开 bootstrap：断言 Room 行/事件、live session、managed 登记与 Git 跟踪文件未变；归档/取消归档不产生模型请求或 Git 写。

## 边界

本批只接持久元数据链。尚不改 Session/Agent/Room 生命周期，不持有共享准入门槛，不做恢复路径；归档按钮未启用，页面无归档入口。生命周期门禁独占（Busy 拒绝）、静止恢复（0 模型请求）、Room/process/PTY 静止、Worktrees 与 Recent/当前聊天接线均待后续批次。无截图、原生窗口或像素验收；不增加独立归档页、删除或清理。

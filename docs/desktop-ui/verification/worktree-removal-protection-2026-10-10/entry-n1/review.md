# Worktrees 移除保护 N：受保护删除与守卫式工作树移除（entry-n1）

日期 2026-10-10。承接 J（归档按钮）与 L（取消归档恢复）。此前 Worktrees 页的 managed 卡片只显示一句"删除与清理不可用"，没有任何删除入口。reverse-engineering 里冻结的删除契约要求：删除前**重新核验全部保护维度** → 产出 typed 回执 → 重扫；不得 force、不得 prune；不得用 `Known(empty)` 走捷径（`git-worktrees-implementation-contract-2026-10-07.md` §3 W03/W05/W09–W12、§5 Delete plan/receipt、§6；`managed-worktree-protection-implementation-2026-10-07.md` W12/W14）。本批实现该契约，并在真实服务齐备时启用"删除工作树"按钮。

## 写集（8 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | apps/shared/src/im/hikaru/harness/client/git/GitModels.kt | 新增 `GitWorktreeContent` / `GitWorktreeContentProbe`；`GitRemoveWorktreeRequest`（含 `expectedBranch`）/ `GitRemoveWorktreeReceipt` / `GitRemoveWorktreeResult`；`GitWorktreeRemover` |
| 2 | apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt | 新增 `ManagedRemovalBlockKind`（含本批新增 `Detached`）、`ManagedRemovalBlock`、`ManagedWorktreeRemovalPlan/Read/Request/Receipt`；`ManagedWorktreeEntry.removal`；`ManagedWorktreeState.pendingRemoval/removalReceipt/removalBusy/canRemove` |
| 3 | apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt | 新增 `remove(plan, pageReady)`：binding/operation ticket、busy 重拒、回执不匹配降级为 `UnknownOutcome`、仅 `Removed` 触发重读；refresh 按 repository+owner 保留回执 |
| 4 | apps/shared/src/im/hikaru/harness/client/git/GitPages.kt | 卡片末尾渲染 `managed-removal-status` 与 `managed-remove`；`managed-remove-dialog` 确认对话框；页脚 `managed-destructive-unavailable` 仅在完全无可用删除证据时显示 |
| 5 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt | 实现 `GitWorktreeContentProbe` / `GitWorktreeRemover`：真实内容探测与守卫式删除（无 force、无 prune） |
| 6 | apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt | `removalEvidence` / `computeRemovalBlocks`；`removeWorktree` 全维度复查 + 分发前重读指纹 + 成功后回收 intent |
| 7 | apps/shared/test/im/hikaru/harness/client/git/ManagedWorktreeControllerTest.kt | 新增 2 个契约用例（合计 6） |
| 8 | apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreeRemovalIntegrationTest.kt（新增） | 6 个真实 Git/Room/登记的删除用例 |

档案 SHA 见同目录 `files.sha256`。`apps/*/…/git/` 整包此前未纳入 git，故按 SHA 记录 after 值。

## 契约与顺序

- **计划即请求。** `read()` 对每个条目计算 `removalEvidence`：缺 remover/prober/sessionObserver/pinStore/archiveStore 任一即 `Unavailable`（按钮不出现）；否则给出 `Available(plan)`，plan 携带 owner/repository/root/headOid/branch 与 `blocks`，`eligible = blocks.isEmpty()`。
- **全维度复查。** `computeRemovalBlocks` 依次核验：归属、Git 列表存在、bare/prunable/locked、**detached（无分支指纹）**、主工作树、与另一 linked worktree 嵌套、会话观察可用（`SessionsUnavailable`）、运行中 Agent（`RunningAgent`）、持久历史（`DurableSessions`）、固定聊天（`PinnedChats`）/固定服务未知、归档聊天（`ArchivedChats`）/归档服务未知；只有**不含任何前置阻断项**的候选才会做最昂贵的内容探测（`ContentUnavailable`/`ContentScanIncomplete`/`UncommittedChanges`/`Submodules`/`NestedRepositories`/`IgnoredContent`）。
- **分发前再核验。** `DesktopManagedWorktreeRepository.removeWorktree` 重验 owner、`plan.blocks.isEmpty()`、仓库指纹；重算 blocks（变化即 `Blocked`）；再读一次 HEAD/分支指纹（变化即 `HeadChanged`/`Missing`）；之后才调用 backend。成功且 Git 列表不再包含该路径后，才在 `NonCancellable` 内回收 managed intent。
- **backend 是最后一道闸。** `removeWorktree` 复核 binding/路径/worktree 存在/非主/非 bare/非 prunable/非 locked/非 detached 且 HEAD+分支指纹一致，然后执行 `git worktree remove -- <path>`（**没有 --force，没有 prune**）；只有重新读取的工作树列表不再含目标路径才发出 `Removed` 回执。
- **控制器不本地翻转。** `remove` 沿用 read epoch 与 binding/operation ticket 分离；busy 期间重拒；回执与请求不匹配降级为 `UnknownOutcome`；只有匹配的 `Removed` 才触发一次新的读取来发布状态，绝不把路径标记为"已消失"。
- **detached 明确拒绝。** plan 构建与 backend dispatch 两处都要求存在可复核的分支指纹：`null == null` 不算核验通过。

## 本批发现并修复的两个真实缺陷

内容探测与删除回执是本批新写、此前无任何测试覆盖的代码，集成测试首次运行即暴露两个缺陷（`logs/harness-n1-removal-first-failure.log`）：

1. **内容探测恒失败。** `git status --porcelain=v1 -z --ignored=matching --untracked-files=no` 在 git 2.50 是非法组合（exit 128，"Unsupported combination of ignored and untracked-files arguments"），被 `runCatching` 吞成 `ContentUnavailable`。后果是**任何**工作树都永远"无法核验内容"，删除在真实产品里永远被拒。修复为只带 `--ignored=matching`，仍然只统计 `!!` 记录。修复前 3 个用例失败于 `ContentUnavailable`，修复后通过。
2. **成功删除被误报为未知结果。** 删除后目标目录已不存在，而回执检查用 `canonicalPath(tree.path)`（`Path.toRealPath()`）解析该路径，抛 `NoSuchFileException`，被吞成 `UnknownOutcome`，managed intent 永不回收。修复为 `canonicalIfResolvable`：能解析就用真实路径，已删除则退化为规范化绝对路径。修复前 `cleanManagedLinkedWorktree...` 失败于 `Removed` vs `UnknownOutcome`，修复后通过。

这两个前后对照即是本批的自然反向控制：用例在缺陷存在时确实为红、在修复后为绿。

## 布局回归修复

把删除块放在会话观察标签内部、观察边界（boundary）之前，会拉长"父会话标记 → 边界"的跨度；观察交互用例（`capturesFourRealCatalogLayoutsWithWheelReachableTailAndCompleteEndMarker`）要求该跨度能整体落进固定高度列表视口，因此裁剪 6px 失败（`logs/harness-n1-layout-diagnosis.log`）。J 批次已记录过同类约束，本批据此把删除块移到**卡片最后一个元素**（所有保护标签之后）。跨度和用例此前继续成立，回归通过。

## 验收

| 命令 | 类 | 结果 |
| --- | --- | --- |
| kotlin test -m shared | ManagedWorktreeControllerTest（含 removalIsSingleFlightAndRepublishesOnlyThroughAFreshReadAfterRemoved、mismatchedRemovalReceiptBecomesUnknownOutcomeWithoutRepublishingInventory） | 6/6 |
| kotlin test -m jvm-app | DesktopWorktreeRemovalIntegrationTest（6 用例） | 6/6 |
| kotlin test -m jvm-app（4 类真实链路） | RemovalIntegration 6、PinIntegration 6、ChatArchiveIntegration 13、SessionObserver 11 | 36/36，0 失败 |
| kotlin test -m jvm-app（8 类定向回归） | WorktreePinInteraction、WorktreeSessionObservation、ManagedWorktree、WorktreePage、WorktreeCreation、WorktreeArchiveRecentSync、GitSettingsRoute、GitWorktreesScreenshot | 34/34，0 失败 |
| kotlin test -m shared（4 类） | ConversationController 33、ChatArchiveController 5、PinController 5、ManagedWorktreeController 6 | 49/49，0 失败 |
| kotlin build -m jvm-app -p jvm | 完整 jvm-app JVM 构建 | Build successful，exit 0 |

关键证据：

- **干净 linked worktree 真删除**：plan 无 blocks；`removeWorktree` 返回 `Removed`；目标目录消失；`git worktree list` 不再包含该路径；重读登记后该 root 不再作为 owned 或 missing 条目出现（intent 已回收）。
- **陈旧计划的写入安全（无 force）**：read 之后才新增未提交文件，dispatch 时重算 blocks 得到 `UncommittedChanges` 并返回 `Blocked`，文件与工作树都在。
- **主工作树 / 运行中会话 / 忽略与嵌套内容 / detached**：分别为 `PrimaryWorktree`、`RunningAgent`、`IgnoredContent`+（`NestedRepositories`）、`InvalidRequest`（backend 直接拒绝 `expectedBranch=null`）阻断，且目录都保留。
- **内容扫描边界**：嵌套仓库扫描预算 4000 条目 / 深度 4，超出记 `ContentScanIncomplete`（`complete=false`）而不是"没有内容"；`git submodule status` 非零退出按失败上报，不当作"没有子模块"。

## 边界

- 只在 `LocalReadOnly` 绑定上提供删除；remote/其他 host 的 `canRemoveWorktree` 为 false，plan 直接 `Unavailable`。
- 判定依赖 F–L 的会话/固定/归档服务；任一缺失即 `Unavailable`，卡片不出现删除块，页脚显示"删除与清理不可用"。服务齐备但某一维度未知时，plan 如实带上对应 block，按钮禁用并显示首个阻断原因。
- 删除始终不带 `--force`/`--prune`，"没有 tracked/untracked/ignored/子模块/嵌套内容且扫描完整"是**我们**的前置条件，Git 自身仍必须接受删除；两者都由回执证明。
- 分发前会重读一次指纹，但最后一次读取与 Git 进程启动之间仍有窄的 TOCTOU 窗口（此窗口内出现的改动由 Git 自身的拒绝兜底；Git 允许时可能丢失窗口内新增的**已忽略**内容）。此限制如实记录，不声称原子。
- 本批**没有**原生窗口、同尺寸像素或截图验收；`docs/desktop-ui/captures/` 未被 git 跟踪，截图测试重新生成的图片不产生受跟踪差异。
- 未实现：snapshot/auto-cleanup（契约要求 snapshot/archive/restore/pin/owner/lifecycle 齐备前不自动生效）、批量删除、force 解锁路径、跨进程静止。
- 本批修改了 `GitPages.kt`（J 批次记录过 SHA）、`DesktopManagedWorktreeRepository.kt`（L 批次记录过 SHA）、`ManagedWorktreeModels.kt`、`ManagedWorktreeController.kt`、`DesktopGitBackend.kt`、`GitModels.kt`，使这些文件此前记录的 SHA 失效；各批各自结论与其余文件不变。
- 各批证据独立保留，不累加作覆盖率；整体目标 active。


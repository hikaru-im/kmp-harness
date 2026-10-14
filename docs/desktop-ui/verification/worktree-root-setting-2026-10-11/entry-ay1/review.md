# Worktrees「工作树根目录」设置验收 AY

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-ay1`（目录先建为 `entry-az1`，本批归档前统一改名为 `entry-ay1`，工作树内已无 `entry-az1` 残留）。

## 1. 本批目标与依据

AW/AX 补齐了 Git diff 的富文本预览与逐行着色，但 Worktrees 页一直没有原版首行「工作树根目录」（`settings.git.worktreeRoot.*`）设置。本批实现该 Host 级字符串设置，并把它真正用起来——新工作树的默认父目录取自根目录。不新增协议、不伪造成功。完整边界见 `../../../reverse-engineering/worktree-root-setting-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- R01 `webview/assets/worktrees-settings-page-ad5cbb37d745.js`：页首是「General」小节里的 `Worktree root` 行；当前值取自 `re(kt,r)?.worktreesRoot`；提交走 `Nt(i, r, De.worktreeRoot, e)`（按 host 的设置变更）；成功后 `settings.git.worktreeRoot.save.success` toast，失败走 `.save.error` toast。
- R02 同上：字段是受控输入（草稿 `u`），仅 `u != null && u !== saved` 时才允许提交；提交逻辑 `if (!changed || pending) return; const e = draft.trim(); (e === '' || isSafePath(e) && fs.isAbsolute(e)) && mutate(e)`——即**空串合法**（回到默认位置），否则必须是**绝对路径**。
- R03 同上：提交绑定 `CmdOrCtrl+S`（`enabled = changed && !pending`），输入失焦触发同一提交；`ariaLabel` 与 `label` 同为 `settings.git.worktreeRoot.*`。
- R04 `webview/assets/zh-CN-b05cfd55c534.js`：`settings.git.worktreeRoot.label`「工作树根目录」、`.description`「ChatGPT 创建托管工作树的目录。留空则使用默认位置」、`.save.success`「已保存工作树根目录」、`.save.error`「未成功保存工作树根目录」。

## 2. 写入集

生产（6）：

- `apps/shared/src/im/hikaru/harness/client/settings/WorktreeSettings.kt`（新增：`@Serializable WorktreeSettings(root)`、`configuredRoot`、纯函数 `worktreeRootValue`/`worktreeRootError`/`worktreeDefaultPath`/`branchSlug`）
- `apps/shared/src/im/hikaru/harness/client/settings/WorktreeSettingsRepository.kt`（新增：`WorktreeSettingsStore`/`MemoryWorktreeSettingsStore`/`WorktreeSettingsState`/`WorktreeSettingsRepository`）
- `apps/shared/src@jvm/im/hikaru/harness/client/settings/DesktopWorktreeSettingsStore.kt`（新增：落盘 `client-worktrees.json`，临时文件 + 原子替换）
- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`（WorktreesPage 顶部新增「工作树根目录」小节；`WorktreeCreationForm` 增 `worktreeRoot` 参数）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（新参数 `worktreeSettingsRepository`，加载 + 透传到 WorktreesPage）
- `apps/jvm-app/src/main.kt`（构造并注入 `WorktreeSettingsRepository(DesktopWorktreeSettingsStore(home/client-worktrees.json))`）

测试（4）：

- `apps/shared/test/im/hikaru/harness/client/settings/WorktreeSettingsTest.kt`（新增 5 项）
- `apps/shared/test/im/hikaru/harness/client/settings/WorktreeSettingsRepositoryTest.kt`（新增 4 项）
- `apps/shared/test@jvm/im/hikaru/harness/client/settings/DesktopWorktreeSettingsStoreTest.kt`（新增 2 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeRootSettingInteractionTest.kt`（新增 2 项）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/worktree-root-setting-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 语义层与原版对齐：`worktreeRootValue` 做 trim 并归一化反斜杠；`worktreeRootError` 判定空串合法（= 默认位置）、绝对路径合法、含 NUL 或相对路径报错。这与 R02 的「空串或（安全且绝对）」分流一致。
- 仓库层镜像既有 `GeneralSettingsRepository`：`load`/`save`/`retry`，失败写不替换在用值；`canEdit` 仅在存储可写且已加载时为真；`DesktopWorktreeSettingsStore` 用临时文件 + 原子替换写 `client-worktrees.json`。
- 页面层：WorktreesPage 顶部新增「工作树根目录」小节（受控输入 + 行内错误 + 保存按钮 + 成功/失败/加载失败提示）；`WorktreeCreationForm` 在根目录已配置且分支是真名时，把「新工作树绝对路径」默认填成 `<root>/<slug>`，用户一旦编辑（`pathTouched`）即不再覆盖。
- 应用层：`HarnessApp` 新参数 `worktreeSettingsRepository`，与 `GeneralSettings` 同层加载一次并透传；`main.kt` 注入 `home/client-worktrees.json` 上的真实存储。

与原版的已记录差异：本仓用显式保存按钮 + 行内错误，未复刻原版 `CmdOrCtrl+S` 快捷键与失焦提交（原版失焦非法时静默不提交，本仓改为可见行内错误）。

## 4. 问题

- 原版「环境」小节内与 root 同页的「自动删除旧工作树」「自动删除限制」「创建前获取上游更新」三行本批不实现；其中自动删除是**破坏性**操作，本批不接线（已记录在契约 3.1）。
- 未观察到 `worktreesRoot` 的 host 存储位置与默认值来源、`isSafePath` 的全部规则，故本批只实现可观察的一层。
- 全量 `jvm-app` 本批命中 2 个与本批无关的真实 git 时序抖动（见 §5.2），已隔离重跑分类。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **524 found / 0 skipped / 524 started / 524 passed / 0 failed**，exit 0 | `logs/shared.log` |
| shared settings 定向 | `im.hikaru.harness.client.settings.*` | **52 found / 0 skipped / 52 started / 52 passed / 0 failed**，exit 0 | `logs/shared-settings-targeted.log` |
| jvm-app 定向 | `WorktreeRootSettingInteractionTest` | **2 found / 0 skipped / 2 started / 2 passed / 0 failed**，exit 0 | `logs/jvm-app-targeted.log` |
| jvm-app 回归 6 类 | `WorktreePageInteractionTest` + `ManagedWorktreeInteractionTest` + `WorktreePinInteractionTest` + `WorktreeSessionObservationInteractionTest` + `GitPageInteractionTest` + `GitDiffViewInteractionTest` | **27 found / 0 skipped / 27 started / 26 passed / 1 failed**（`WorktreePinInteractionTest` 时序抖动，见 §5.2） | `logs/jvm-app-regression.log` |
| jvm-app pin 抖动重跑 | `WorktreePinInteractionTest`（两次） | 第 1 次 **4 found / 2 passed / 2 failed**，第 2 次 **4 found / 4 passed / 0 failed**，exit 0 | `logs/pin-flake-rerun-1.log`、`logs/pin-flake-rerun-2.log` |
| 全量 jvm-app（本批门槛） | 全部 | **679 found / 5 skipped / 674 started / 672 passed / 2 failed**，exit 1（2 个超时抖动，见 §5.2） | `logs/full-jvm-app.txt` |
| 全量抖动重跑 | `GitCommitInteractionTest` + `git.DesktopGitCommitTest` | **13 found / 0 skipped / 13 started / 13 passed / 0 failed**，exit 0 | `logs/flake-rerun.log` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- `WorktreeSettingsTest`（+5）：`blankRootIsValidAndMeansDefaultLocation`（空/纯空白合法且值为空）；`relativeRootIsRejectedAndSurfacedInlineOnly`（相对路径报行内错误，不加前缀）；`absoluteRootIsTrimmedAndBackslashesNormalized`；`defaultPathUsesRootAndBranchSlug`（`worktreeDefaultPath(root, branch)` = `<root>/<slug>`）；`branchSlugIsFilesystemSafe`（分支名里的 `/`、空白与非法字符被折叠）。
- `WorktreeSettingsRepositoryTest`（+4）：加载初值、保存替换、失败写不替换在用值、`canEdit` 只读/未加载时为假。
- `DesktopWorktreeSettingsStoreTest`（+2）：round-trip 落盘 `client-worktrees.json`、损坏文件回退默认。
- `WorktreeRootSettingInteractionTest`（+2）：`rootValidatesSavesAndSeedsTheCreationPath`（输入相对路径见行内错误 → 输入绝对路径保存 → 新工作树表单路径默认填成 `<root>/<slug>`）；`readOnlyStoreKeepsTheFieldAndSaveDisabled`（只读存储时字段保留但保存禁用）。

### 5.2 失败归因与抖动判定

本批全部失败均为真实进程/PTY 的既有实时序抖动，与本批（新增设置字段、仓库、页面小节与创建路径默认值）无关：

1. 全量 `jvm-app` **679 found / 5 skipped / 674 started / 672 passed / 2 failed**（exit 1）：
   - `GitCommitInteractionTest.includeUnstagedCheckboxStagesWorktreeAndUntrackedThenCommitsThem`（真实 worktree 暂存/提交 15s 超时，`ComposeTimeoutException`，栈指向 `GitCommitInteractionTest.kt:94`）。
   - `git.DesktopGitCommitTest.includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified`（真实 git 15s 超时，`TimeoutCancellationException`）。
   隔离重跑这两类为 **13/13 全绿**（exit 0，`logs/flake-rerun.log`），与 AW/AX 记录的同一组 `GitCommitInteractionTest + git.DesktopGitCommitTest` 抖动一致。
2. 回归 6 类 **27 found / 26 passed / 1 failed**：
   - `WorktreePinInteractionTest.realMouseArchiveAndUnarchivePersistToFreshReader`（Compose 10s 超时）。该类两次隔离重跑：第 1 次 **2/4**、第 2 次 **4/4 全绿**（`logs/pin-flake-rerun-1.log`、`logs/pin-flake-rerun-2.log`），确认为既有抖动类。

因此本批不主张全量 0 失败，但改动范围内的套件（shared 全模块、shared settings 定向、jvm-app 定向）均 0 失败。

### 5.3 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它（跑后为 `4f387960367fa746579d440583d0e5e2d54e802e`），随后按 AE/AU/AV/AW/AX 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：`CmdOrCtrl+S` 快捷键与失焦提交、原版 host 级存储与 `worktreesRoot` 默认值来源、`isSafePath` 完整规则、同页「环境」小节的自动删除/保留数量/上游刷新三行（含破坏性的自动删除）。
- 各批证据独立，整体目标 active。

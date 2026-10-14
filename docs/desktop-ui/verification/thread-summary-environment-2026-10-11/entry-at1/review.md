# Thread 摘要面板「环境」小节验收 AT

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-at1`。

## 1. 本批目标与依据

Codex 桌面右侧有一个独立于 tab 面板的「摘要」island（`thread-summary-panel-layout-*.js`：默认宽 300，`overlay`/`shift`/`gutter` 三种宽度策略），其「Environment」小节只在当前 cwd 是 git 检出时渲染，依次显示 Changes（真实 `+additions/−deletions`，点击打开该 checkout 的本地变更）、当前分支（可复制分支名）、未提交文件数。本仓此前完全没有该面板。本批补上前端可视结构与本机 Git 只读投影，不发明任何数字。完整边界见 `../../../reverse-engineering/thread-summary-environment-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 §4 `logs/asar-summary-panel-evidence.txt`）：S01 布局函数与默认宽度；S02 `Environment` 标题与「仅 git 检出渲染」条件；S03 Changes/分支/未提交三行与其 i18n key；S04 `diffStats={additions,deletions}` 的来源派生 store。

## 2. 写入集

生产（7）：

- `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`（新增 `GitDiffStats`；`GitState.summaryStats` 独立读取）
- `apps/shared/src/im/hikaru/harness/client/git/GitDiffFiles.kt`（新增 `gitDiffStats(patch)`）
- `apps/shared/src/im/hikaru/harness/client/git/GitController.kt`（`Query.SummaryStats` + `refreshSummaryStats()`；仓库切换复位）
- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryModels.kt`（新增）
- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryPanel.kt`（新增）
- `apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt`（`summaryPanel`/`summaryPanelWidth`/`onDismissSummaryPanel` 参数）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（「摘要」开关 + 面板接线 + 打开时才触发一次统计读取）

测试（5）：

- `apps/shared/test/im/hikaru/harness/client/summary/ThreadSummaryModelsTest.kt`（新增，7 项）
- `apps/shared/test/im/hikaru/harness/client/git/GitSummaryStatsControllerTest.kt`（新增，4 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/ThreadSummaryPanelInteractionTest.kt`（新增，3 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopGitProcessIntegrationTest.kt`（真实仓库追加 `gitDiffStats == GitDiffStats(1,1,1)`，untracked 不计入）
- `apps/jvm-app/test/im/hikaru/harness/desktop/HarnessAppThreadPanelsShellTest.kt`（`scenario()` 追加摘要开合 + `thread-summary-unavailable`）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/thread-summary-environment-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 数据来源与页面 diff 分离：`GitState.summaryStats` 是独立读取；`refreshSummaryStats()` 复用只读 `diff`（`GitDiffRequest(BaseBranch, "HEAD")`），把 patch 折算成 `GitDiffStats`；unborn HEAD 不读取、置 `Unselected` 而非伪造 0；在 Git 页面 `selectDiff` 不会改动该读数，反之亦然。
- 纯投影：`threadSummaryEnvironment(state)` 把 `repository`/`status` 的 Ready/Failed/Loading/Unselected 映射成 `Unavailable`/`Loading`/`Ready`；`uncommittedTrackedCount` 只数 `!it.untracked`；`threadBranchDisplayName` 只剥 `refs/heads/`。
- UI 岛：`ThreadSummaryPanel` 渲染标题、「环境」、Changes 行（`+n/−m` 或加载省略号）、分支行（可复制）、未提交行；不可用/加载显示真实状态 tag；无消费者的行不画。
- 壳层：`AppShell` 宽度足够（mainArea ≥ 672dp）时作独立 300dp 右列，窄时作带 scrim 的右侧浮层；`summaryPanel == null` 时既有布局零影响。
- `HarnessApp`：Thread 页标题栏「摘要」开关（`thread-summary-toggle`）；打开且 repo+status 就绪时 `LaunchedEffect` 触发一次 `refreshSummaryStats()`；Changes 行跳 `PageId.Settings(Git)`（本仓无原版 Review panel，属 Harness 取舍）。

## 4. 问题

- 定向与 shared 套件全绿；全量 `jvm-app` 命中 3 个**真实进程/PTY 负载超时**（见 §5），均与本批改动路径无关。
- 全量运行重新生成了未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；按 AE 先例从批次前备份 `/tmp/at-sched-Ihv4/orig` 还原，`diff -r` 一致，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据；截图套件随全量重跑生成，本批未新增像素主张。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **482 found / 0 skipped / 482 started / 482 passed / 0 failed**，exit 0 | `logs/shared.log` |
| jvm-app 定向 | `ThreadSummaryPanelInteractionTest`(3) + `GitDiffViewInteractionTest`(3) + `HarnessAppThreadPanelsShellTest`(8) + `git.DesktopGitBackendTest`(6) + `git.DesktopGitProcessIntegrationTest`(1) | **21/21**，exit 0 | `logs/desktop-summary.log` |
| 全量 jvm-app（本批门槛） | 全部 | **667 found / 5 skipped / 662 started / 659 passed / 3 failed**，exit 1（3 个超时抖动，见 §5.2） | `logs/full-jvm-app.txt` |
| 抖动重跑 | `GitCommitInteractionTest` + `git.DesktopGitCommitTest` + `PtyTerminalFontInteractionTest` | 其中 2 个 Git 类 **13/13 通过**；PTY 字体复现 1 次 30s 超时 | `logs/flake-rerun.log` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 模型：无服务/非仓库 → `Unavailable`；Loading/Unselected → `Loading`；Ready → 真实分支名（剥 `refs/heads/`）、detached/unborn、root、统计、`uncommittedTrackedCount` 只含已跟踪。
- 控制器：`refreshSummaryStats()` 写入 `summaryStats` 且不改动页面 `diff`；unborn HEAD 置 `Unselected` 且 0 次读取；仓库切换复位。
- 真实仓库：`gitDiffStats(HEAD)` 得 `GitDiffStats(fileCount=1, additions=1, deletions=1)`，untracked 不计。
- 页面：默认摘要开关存在；打开后出现摘要面板；无 Git 服务时显示 `thread-summary-unavailable`；关闭后消失。
- 岛 UI：Changes 行 meta `+n/−m`、加载态省略号、分支行可复制、未提交行仅在计数 > 0 时渲染。

### 5.2 全量失败归因（不全绿）

全量 `jvm-app` 3 个失败全部是**真实进程/PTY 的 Compose 超时**，非本批代码：

- `GitCommitInteractionTest.includeUnstagedCheckboxStagesWorktreeAndUntrackedThenCommitsThem`（真实 `git add`/`git commit` 15s 超时）
- `git.DesktopGitCommitTest.includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified`（真实 `git add`/`git commit` 15s 超时）
- `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（30s PTY 就绪超时，AE/AF/AM/AO/AQ 一贯记录）

隔离重跑：`GitCommitInteractionTest` **3/3**；`git.DesktopGitCommitTest + PtyTerminalFontInteractionTest` 一次 **13/13**（PTY 通过）；随后三合一重跑 `flake-rerun.log` 中两个 Git 类仍 **13/13**、仅 PTY 复现 1 次 30s 超时。本批改动只在 Git 只读统计/摘要投影/壳层面板路径，不经 `git add`/`commit`/PTY。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：原版 Changes 行打开的 Review panel 内部、摘要面板 Sources/Artifacts/Usage/Scheduled/Outputs 小节、overlay/shift/gutter 精确位移曲线、`metrics.bytesEstimate` 等未观察字段。
- 各批证据独立，整体目标 active。

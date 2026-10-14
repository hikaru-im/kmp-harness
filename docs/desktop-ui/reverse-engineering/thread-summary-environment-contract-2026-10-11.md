# Thread 摘要面板「环境」小节：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包：

| 编号 | 观察到的内容 |
| --- | --- |
| S01 | 右侧摘要面板是独立于底部/右侧 tab 面板的 island。`thread-summary-panel-layout-*.js` 导出 `layout(mainWidth, panelWidth=300)`：`(mainWidth-736)/2 < 180 + (t-300)/2` 时为 `overlay`，否则 `< 400 + (t-300)` 为 `shift`，再否则为 `gutter`；面板宽度默认 300。`toggle-thread-summary-panel-*.js` 与 `use-thread-summary-panel-*.js` 管理 pin/popover 与内容位移。 |
| S02 | 摘要面板的「Environment」小节：`Qt()` 在 `n.cwd == null \|\| !isGitHost(n.gitHostConfig.id)` 时 `return null`，并在 `n.kind !== 'git'` 时 `return null`（只有 git 检出才渲染）。小节标题 `codex.localConversation.environmentSummary.title` = `Environment`。 |
| S03 | 小节内容依次是：Changes 行（`git-summary-branch-changes-row-*.js`，`codex.localConversation.gitSummary.branchChangesLabel`=`Changes`；有 `onOpenReviewTab` 时 `codex.localConversation.gitSummary.reviewChanges` 的 tooltip 为「opens its local changes in the Review panel」，`itemMeta` 显示 `±diffStats`，加载时显示 spinner）、当前分支行（`codex.localConversation.environmentSummary.copyBranchName`=`Copy branch name` 的右键菜单）、未提交行（`composer.footer.branchSwitch.uncommittedSummaryPrefix`=`Uncommitted: {fileCount, plural, one {# file} other {# files}}`，仅 `d > 0` 时渲染）。 |
| S04 | Changes 行的 `diffStats` 形如 `{additions, deletions}`（`git-summary-branch-changes-row` 里 `s.additions`/`s.deletions` 直接渲染）。其来源 `nNa` 派生 store 返回 `{isLoading, metrics}`，`metrics = XMa(additions, deletions, fileCount, bytesEstimate)`；`reviewSummary.type==='success'` 时按 `files[].additions/deletions` 求和。 |

固定证据：本批不重新抓包，结论来自已抽取的 webview chunk（文件级可复现，见 §4 证据目录的日志）。

## 2. 已观察到与未观察到的语义

- **已观察**：摘要面板是独立 island、默认 300 宽、有 overlay/shift/gutter 三种宽度策略；Environment 小节只在 cwd 是 git 检出时渲染；Changes 行显示真实 `+additions/−deletions`，点击打开该 checkout 的本地变更；分支行可复制分支名；未提交行按文件数渲染。
- **未观察到**：Changes 行打开的具体「Review panel」内部结构（本仓无该面板）；`metrics.bytesEstimate` 的口径；摘要面板其余小节（Sources、Artifacts、Usage、Scheduled、Outputs）的本批范围；原版同状态的像素值。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitModels`（shared） | 新增 `GitDiffStats(fileCount, additions, deletions)`（含 `lineCount`）；`GitState` 新增独立的 `summaryStats: GitRead<GitDiffStats>`，与页面 `diff` 分离。 |
| `GitDiffFiles`（shared） | 新增 `gitDiffStats(patch)`，复用 diff 卡片同一套计数（只统计 patch 文本，不发明内容）。 |
| `GitController`（shared） | 新增 `Query.SummaryStats` 与 `refreshSummaryStats()`：以已解析的 `HEAD` 提交为基准复用只读 `diff`（`GitDiffRequest(BaseBranch, "HEAD")`），把 patch 折算成 `GitDiffStats`；无 HEAD（unborn）时不读取、置 `Unselected` 而非伪造 0；与页面 `selectDiff` 互不覆盖。仓库切换时 `summaryStats` 随新 `GitState` 复位。 |
| `summary/ThreadSummaryModels`（新增，shared） | `threadBranchDisplayName` 只去掉 `refs/heads/` 前缀；`ThreadSummaryEnvironment` 投影分支名/detached/unborn/root/真实统计/加载态/已跟踪未提交数（排除 untracked）；`ThreadSummaryLoad{Unavailable,Loading,Ready}`。 |
| `summary/ThreadSummaryPanel`（新增，shared） | 摘要 island UI：标题「摘要」+ 刷新/关闭；「环境」小节含 Changes 行（`thread-summary-changes`，meta 为 `+n/−m` 或加载省略号）、分支行（`thread-summary-branch`，可复制分支名）、未提交行（`thread-summary-uncommitted`）；不可用时显示真实原因（`thread-summary-unavailable`）。 |
| `AppShell`（shared） | 新增 `summaryPanel`/`summaryPanelWidth`/`onDismissSummaryPanel` 参数；宽度足够时作为独立右侧列（300dp）渲染，窗口窄（mainArea < 672dp）时改为带 scrim 的右侧浮层（`thread-summary-overlay`/`thread-summary-scrim`）。默认 `null` 时不影响既有布局。 |
| `HarnessApp`（shared） | Thread 页标题栏新增「摘要」开关（`thread-summary-toggle`）；打开时渲染摘要 panel；`LaunchedEffect` 仅在打开且 repo+status 就绪时触发一次 `refreshSummaryStats()`；Changes 行跳转到 Git 设置页（本仓无原版 Review panel，属 Harness 取舍）。 |

### 3.1 未实现（明确不主张）

- 原版 Changes 行打开的 **Review panel** 内部（本仓仍跳到 Git 设置页）。
- 摘要面板的 **Sources / Artifacts / Usage / Scheduled / Outputs** 小节。
- 原版 **overlay/shift/gutter 的精确位移曲线**（本批只做「宽则列、窄则浮层」两种）。
- 原版**像素一致**：本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口证据。
- `metrics.bytesEstimate` 等未观察字段。

## 4. 证据

- 验收：`docs/desktop-ui/verification/thread-summary-environment-2026-10-11/entry-at1/`
- 抽取快照与检索日志：同上 `logs/asar-summary-panel-evidence.txt`。

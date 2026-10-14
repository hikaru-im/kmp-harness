# Diff「刷新」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动原包、未联网。抄录见
`../verification/git-refresh-2026-10-12/entry-cg1/logs/asar-refresh.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | `"codex.review.refreshGitQueries":"刷新"`，紧邻 `"codex.review.refreshGitQueries.inProgress":"正在刷新更改"`。 |
| E02 | `pull-request-code-review-be17c7c34414.js` | 选项菜单里该项文案取自 `codex.review.refreshGitQueries`，`defaultMessage: 'Refresh'`，`description: 'Menu item to refresh git queries for the current repository'`。 |
| E03 | `pull-request-code-review-be17c7c34414.js` | 菜单组件 `Er(...)` 的 props：`onRefreshGitQueries`（回调）、`refreshGitQueriesDisabled`（禁用）、`showRefreshGitQueries`（可见性）。与同一菜单里的 `onToggle*`（勾选项）不同，它是**普通动作项**。 |

（本批为「变更选项」菜单 6 项里的最后一项；批 CE 已抄录菜单整体顺序 `loadFullFiles → richPreview → wordDiffs → hideWhitespace → hideImports → copyGitApplyCommand`，`refresh` 在菜单顶部区域。）

## 2. 已观察到与未观察到的语义

- **已观察**：选项菜单里有一个普通动作项「刷新」，语义是 `refresh git queries for the current repository`；读取进行中该项禁用（`refreshGitQueriesDisabled`）。
- **已观察**：另有一条进行中文案 `codex.review.refreshGitQueries.inProgress`=「正在刷新更改」，说明刷新期间原版会给出进行中提示。
- **未观察**：刷新覆盖哪些查询（状态/分支/工作树/diff 全量还是部分）、进行中文案显示在何处（菜单项自身标签？外层 header？替换某个区域？）均不可见；原版也没有留下同状态像素可比对。
- **未观察**：刷新是否保留旧内容、是否短暂清空视图。ASAR 只有「进行中」文案，没有对应的渲染结构证据。

因此本批实现**可观察的那一层**：在「变更选项」菜单中新增「刷新」动作项，点击触发一次只读刷新，读取进行中该项禁用；并把「旧 diff 在刷新期间保持可见」作为 Harness 取值实现并写明。

### 2.1 Harness 取值（明确记录）

| 维度 | ASAR | 本仓 CG |
| --- | --- | --- |
| 触发面 | 选项菜单普通项 | 选项菜单普通项（第 7 项） |
| 刷新范围 | 未观察到 | 复用既有 `GitController.refresh()`：一次只读刷新（状态/分支/工作树/提交快照/diff） |
| 进行中 | 该项 `refreshGitQueriesDisabled` | 该项 `enabled = !refreshDisabled`，而 `refreshDisabled = !state.pageCanRead`（读取中为 false） |
| 进行中提示 | 有 `...inProgress`=「正在刷新更改」 | **未实现**该文案（只做禁用，不新增进度 UI） |
| 旧内容 | 未观察到 | 刷新期间保留上一次完成的 patch 可见，避免闪现「尚未读取 Diff。」 |

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `client/git/GitPageComponents.kt`（shared） | `GitPatch` 新增 `onRefresh: () -> Unit = {}` 与 `refreshDisabled: Boolean = false`（默认值不破坏其它调用点）；`GitDiffView` 同名透传；私有 `GitDiffOptionsMenu` 新增同两名参数，entries 追加第 7 项 `HarnessMenuEntry.Item(key = "refresh", text = "刷新", enabled = !refreshDisabled)`，`onSelect` 增 `"refresh" -> onRefresh()`；KDoc 更新。 |
| `client/git/GitPages.kt`（shared） | `GitPatch(...)` 两处调用点接线 `onRefresh = controller::refresh`、`refreshDisabled = !state.pageCanRead`；新增 `lastReadyDiff`（`remember(state.binding, state.selectedCwd, state.diffRequest)` + `SideEffect`）使刷新期间保留上一次完成的 patch 可见。 |

刷新走既有 `GitController.refresh()`（`GitController.kt:397`）：只做读，不取消创建、不释放准入槽；`GitState.pageCanRead = unavailableReason == null && !pageMutationBusy && !pageReading`。

### 3.1 未实现（明确不主张）

- **「正在刷新更改」进行中文案**：原版 `codex.review.refreshGitQueries.inProgress` 未接入（只有禁用态）。
- **刷新范围的门控**：未实现原版是否区分「只刷新 diff」和「刷新全部查询」的差别。
- **菜单顺序**：本仓把 `refresh` 放在菜单最后一项，与 ASAR 菜单顶部区域的位置不同（ASAR 菜单顺序见批 CE 抄录）。
- **`richPreview` 移入菜单、复制 `git apply` 命令**：仍未实现（`richPreview` 目前是工具栏独立按钮）。
- **原版同尺寸像素与原生窗口证据**（未做）。

## 4. 验收

见 `../verification/git-refresh-2026-10-12/entry-cg1/review.md`。各批证据独立，不累加为总体覆盖率。

# Batch CG：Diff「刷新」（2026-10-12）

## §1 本批目标

补齐 diff「变更选项」菜单里 ASAR 明确存在、此前未实现的最后一项普通动作 `codex.review.refreshGitQueries`（「刷新」）。
ASAR 只读证据（`logs/asar-refresh.txt`，来源 `/tmp/asar-new/webview/assets/`）：

- 文案：`zh-CN-b05cfd55c534.js` 的 `"codex.review.refreshGitQueries":"刷新"`，紧邻 `"codex.review.refreshGitQueries.inProgress":"正在刷新更改"`。
- 定义：`pull-request-code-review-be17c7c34414.js` 里 `defaultMessage: 'Refresh'`、`description: 'Menu item to refresh git queries for the current repository'`。
- 形态：菜单组件 `Er(...)` 的 props 为 `onRefreshGitQueries` / `refreshGitQueriesDisabled` / `showRefreshGitQueries`——它是**普通动作项**（不是 `onToggle*` 勾选项），读取进行中禁用。

因此本批实现可观察的那一层（菜单动作项 + 点击触发只读刷新 + 进行中禁用），Harness 取值记入契约 §2.1。

## §2 生产改动

（2 个文件，均在 shared）

| 文件 | 改动 |
| --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt` | `GitPatch` 新增 `onRefresh: () -> Unit = {}`、`refreshDisabled: Boolean = false`；`GitDiffView` 同名透传；私有 `GitDiffOptionsMenu` 新增同两名参数，entries 追加第 7 项 `HarnessMenuEntry.Item(key = "refresh", text = "刷新", enabled = !refreshDisabled)`，`onSelect` 增 `"refresh" -> onRefresh()`；KDoc 更新（不再把 refresh 列为未做）。 |
| `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt` | 两处 `GitPatch(...)` 调用点接线 `onRefresh = controller::refresh`、`refreshDisabled = !state.pageCanRead`；新增 `lastReadyDiff`（`remember(state.binding, state.selectedCwd, state.diffRequest)` + `SideEffect`），使刷新期间保留上一次完成的 patch 可见。 |

刷新走既有 `GitController.refresh()`（`GitController.kt:397`，**只做读**：不取消创建、不释放准入槽）。`GitState.pageCanRead = unavailableReason == null && !pageMutationBusy && !pageReading`，所以读取中刷新项自动禁用。

## §3 测试改动

（1 个文件）

- `apps/jvm-app/test/.../desktop/GitDiffViewInteractionTest.kt`：新增 `diffOptionsMenuRefreshRereadsAndDisablesWhileReadIsInProgress`——断言菜单项文案「刷新」且可用；点击后 Host 侧 diff 读取次数 +1；读取未完成时该项 disabled 且点击不再增加读取次数；放行后页面显示刷新后的新 patch（`1 个文件变更 · +1 −0`）。用例复用既有 `f.backend.diffGate`（`GitWorktreesPageTestSupport.kt:161`，CG 未改动该文件）。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-cg-build -p jvm -m shared`（全量） | **580 found / 580 started / 580 passed / 0 failed**（`logs/shared-full.txt`） |
| 同 `-m shared --include-classes=…GitControllerTest` | 11 found / 11 passed / 0 failed（`logs/GitControllerTest.txt`） |
| 同 `-m jvm-app --include-classes=…GitDiffViewInteractionTest` | 9 found / 9 passed / 0 failed（新增 1，上批 8）（`logs/GitDiffViewInteractionTest.txt`） |
| 同 `-m jvm-app --include-classes=…GitWorktreesScreenshotTest`（输出重定向 `/tmp/cg-captures`） | 9 found / 9 passed / 0 failed（`logs/screenshot-test.txt`） |
| 同 `-m jvm-app`（全量） | **712 found / 5 skipped / 707 started / 707 passed / 0 failed**（exit 0）（`logs/jvm-app-full.txt`） |

全量 712 found 比 CF（711）多 1，正是本批新增的交互用例。本批全量 **0 失败**（CF 的 PTY 字体抖动未复现）。

## §5 截图产物核对

- **没有新增截图目录**（本批未加截图用例）。
- **既有截图的结构性变化**：`git-word-level-diff-*` / `git-hide-imports-*` / `git-load-full-files-*` 三组 `options-menu-*` 截图（24 PNG + 24 JSON）——弹出菜单多了第 7 项「刷新」，属真实结构变化，保留重生成版本。JSON 证据：切换后出现 `"menu:refresh"`，切换前不存在。
- **与批次前快照** `/tmp/cg-captures-before-1791799682` 逐文件比较：还原前 `new 0 / changed 328 / removed 0`；其余 **280** 个全量运行重写文件（20 个与本批无关的 `scheduled-execution/*.png` 历史冻结基线，早于 `ScheduledLocalExecutionModel` 披露文案；260 个仅 Compose 节点 id 变化、去数字后完全一致的 `.json`）按先例从快照**逐字节还原**，还原后为 `new 0 / changed 48 / removed 0`（48 = 24 PNG + 24 JSON）。
- 隔离重跑 `GitWorktreesScreenshotTest`（`HARNESS_GIT_WORKTREES_CAPTURE_DIR=/tmp/cg-captures`）后：`docs/desktop-ui/captures/` 未被增删（仍是与快照差 48 个文件）；这 48 个变化文件中 **24 个 PNG 与隔离运行逐字节一致（identical=24 / differing=0）**，24 个 JSON 仅节点 id 抖动（`logs/captures-reproducibility.txt`）。

### §5.1 菜单确实多了一行（像素证据）

`logs/menu-pixels.txt`：把菜单弹窗截图转 BMP 后比较，

- 四个抽样菜单图（word-diffs dark 1440、word-diffs light 600、hide-imports dark 1440、load-full-files light 1440）**尺寸一致地由 200x224 变为 200x260**（`height_delta = 36px`，正好一行菜单项）。
- 新增的那一行（after 的 `y 224..259`）有 **607** 个非背景像素（同一图最后一行的既有项为 845），即确实渲染出一个菜单行，而不是空白/padding 抖动。

## §6 边界（明确不主张）

- **「正在刷新更改」进行中文案**未接入（只有禁用态），原版 `codex.review.refreshGitQueries.inProgress` 未呈现。
- 刷新范围复用既有 `GitController.refresh()`（状态/分支/工作树/提交快照/diff 一起刷新）；原版是否区分「只刷新 diff」与「刷新全部查询」未观察到。
- **菜单顺序**：本仓把 `refresh` 放在菜单最后一项；ASAR 菜单顺序见批 CE 抄录（`refresh` 在顶部区域），两者不同。
- 「刷新期间保留旧 patch 可见」是 Harness 取值（ASAR 未观察到刷新时是否清空视图）。
- `richPreview` 移入菜单、复制 `git apply` 命令、`autoSwitchToUnified` 仍未做。
- 行内评论、sticky 吸附、上下文展开、远端 Diff、图片/SVG/PDF 预览仍未做；定时任务后台执行、附件协议、MCP/Hooks/Scripts 真实连接仍未做。
- 原版同尺寸像素与原生窗口证据（未做）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-cg-build -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-cg-build -p jvm -m shared --include-classes='im.hikaru.harness.client.git.GitControllerTest'
./kotlin test --build-dir=/tmp/harness-cg-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitDiffViewInteractionTest'
HARNESS_GIT_WORKTREES_CAPTURE_DIR=/tmp/cg-captures ./kotlin test --build-dir=/tmp/harness-cg-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitWorktreesScreenshotTest'
./kotlin test --build-dir=/tmp/harness-cg-build -p jvm -m jvm-app
```

截图重写只影响 `docs/desktop-ui/captures/`；`HARNESS_GIT_WORKTREES_CAPTURE_DIR` 把输出改到临时目录。

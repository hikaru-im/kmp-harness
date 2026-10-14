# BV：Diff「变更选项」新增隐藏空白字符（2026-10-12）

## §1 目标与依据

目标：把 Codex Diff 头部「变更选项」菜单里的 `codex.review.whitespace.hide`（中文「隐藏空白字符」，英文
defaultMessage `Hide white space`，description `Menu item to hide whitespace-only diff changes`）接到本仓的 Diff。
关键契约判断：Codex 把该状态作为**Diff 输入**（`review-diff` 请求参数 `hideWhitespace`）发给 Host，由 Host 计算
忽略空白后的补丁，而不是对已返回文本做客户端渲染过滤。本仓沿用同一架构。

依据（`logs/asar-hide-whitespace-evidence.txt`，来源 `/tmp/asar-new/webview/assets/` 的
`zh-CN-b05cfd55c534.js`、`pull-request-code-review-be17c7c34414.js`、`app-initial-6350d04672d0.js`，只读、未联网）：

- zh-CN 文案：`codex.review.whitespace.hide` = 隐藏空白字符。
- 英文 defaultMessage `Hide white space`；description `Menu item to hide whitespace-only diff changes`。
- 架构：菜单项状态进入 `method:'review-diff', params:{... hideWhitespace ...}`（`hideWhitespace:t.hideWhitespace`），
  即 Host 端 Diff 输入。
- 持久化：独立偏好键 `hideDiffWhitespace`（本批未接偏好持久化）。
- 同菜单其它项（词级差异 / 隐藏导入 / 加载完整文件 / Refresh / Diff 模式）不在本批范围。

## §2 写入集合

生产（3 个文件）：

- `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`：`GitDiffRequest` 新增
  `hideWhitespace: Boolean = false`（带默认值，`GitDiffRequest()` 与两参构造仍可用，`GitDiffSnapshot.request` 原样回显）。
- `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt`：`diff()` 在 `request.hideWhitespace`
  为真时于 `--` 之前追加 `--ignore-all-space`。
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`：`GitPatch` / `GitDiffView` 接收
  `hideWhitespace` 与 `onToggleHideWhitespace`；`GitDiffOptionsMenu` 新增第三项（`key = "hide-whitespace"`、
  文案「隐藏空白字符」、`selected` 反映当前值）。
- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`：切换时
  `controller.selectDiff(state.diffRequest.copy(hideWhitespace = it))`，把状态透传给 `GitPatch`。

测试（2 个文件）：

- `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopGitBackendTest.kt`：新增
  `hideWhitespaceAddsIgnoreAllSpaceBeforeThePathspecAndStaysOffByDefault`（默认无该参数；开关打开时参数落在 `--` 之前）。
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitPageInteractionTest.kt`：新增
  `diffOptionsHideWhitespaceReReadsThePatchWithWhitespaceIgnored`（点菜单项后 `diffRequest.hideWhitespace` 置位并触发重读，
  再点一次复位）。

文档：`docs/desktop-ui/reverse-engineering/diff-hide-whitespace-contract-2026-10-12.md`；本目录 `review.md`、
`files.sha256`、`logs/`。

截图产物：本批未新增截图；`docs/desktop-ui/captures/git-worktrees/` 由全量 `jvm-app` 的截图用例按既有机制重写
（见 §5），`docs/desktop-ui/captures/scheduled-execution/` 跑后从批次前字节还原。

## §3 实现

- **开关是 Diff 输入而非渲染过滤**：菜单项不改变已解析的补丁文本，而是触发
  `controller.selectDiff(request.copy(hideWhitespace = ...))` 让控制器重读 Diff；这与 ASAR 里
  `hideWhitespace` 进入 `review-diff` 参数的证据一致。
- **Host 端忽略空白**：`DesktopGitBackend.diff()` 在标准参数（`--no-ext-diff --no-textconv --no-color --submodule=short`）
  与可选的基准 commit 之后、`--` 之前追加 `--ignore-all-space`，因此只影响补丁计算，不影响任何写入或 `.git` 状态。
- **默认关闭且向后兼容**：新增字段有默认值 `false`，既有请求相等断言与两参构造不变；`GitDiffSnapshot.request`
  原样回显请求，快照的 `headOid` 语义不变。
- **菜单项可观察**：`GitDiffOptionsMenu` 的「隐藏空白字符」条目带 `key = "hide-whitespace"`，`selected` 由
  `hideWhitespace` 驱动，菜单内点击即回调 `onToggleHideWhitespace(!hideWhitespace)`；同一菜单的自动换行、
  展开 / 折叠全部项行为不变。

## §4 问题

- 本批全量 `jvm-app` 未命中历史抖动类（`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`
  与 `DesktopGitCommitTest.includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified` 本次均通过）。
- `--ignore-all-space`（等价 `-w`）是 git 忽略空白差异的标准开关，但原版 Host 是否另用 `-b`
  （`--ignore-space-change`）而非 `-w` 未取证，本批取 `-w` 并在契约 §3 边界记录。

## §5 验收

命令行：`./kotlin test --build-dir=/tmp/ap-build -p jvm -m <module> [--include-classes=…]`。

- `jvm-app` 定向 `DesktopGitBackendTest` + `GitPageInteractionTest` + `GitDiffViewInteractionTest`：
  **21 found / 21 passed / 0 failed**，exit 0（`logs/jvm-app-diff-hide-whitespace-targeted.log`；本批新增 2）。
- `shared` 全模块：**557 found / 557 passed / 0 failed**，exit 0
  （`logs/shared-module-full.log`；与上批持平，本批无 shared 单测改动）。
- `jvm-app` 全量：**697 found / 5 skipped / 692 started / 692 passed / 0 failed**，exit code **0**
  （`logs/jvm-app-module-full.log`；found 较上批 695 增 2，即新增的两个用例；本批未命中历史抖动类）。
- 冻结资产还原：全量 `jvm-app` 重写 `docs/desktop-ui/captures/scheduled-execution/`（截图用例固有行为），跑后从
  批次前字节还原，`diff -r` 无差异，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5` 与批次前一致。
- `docs/desktop-ui/captures/git-worktrees/`：由截图用例按既有机制重写（本批沿用 BF/BG/BQ 先例，保留重生成版本），
  本批修改未触及该截图测试在 BV 中捕获的界面状态（菜单本身未被截图捕获），重生成属机制性重写而非本批新增画面。
- `git diff --check` 通过；新增 / 修改文件均为 LF、无尾随空白。

## §6 未验证边界

- 仅实现隐藏空白字符；同一菜单里的词级差异（`codex.review.wordDiffs.label`）、隐藏导入（`codex.review.imports.hide`）、
  加载完整文件（`loadFullFiles`）、Refresh 与 Diff 模式项仍未实现。
- 未接 Codex 的 `hideDiffWhitespace` 偏好持久化：开关状态保存在页面级 `GitState.diffRequest`，重开页面回到关闭。
- 只在本地 Git 后端实现；远端 / Relay Host 未定义该参数（本批未接远端 Diff）。
- `--ignore-all-space` 与 Codex 实际送往 Host 的开关是否逐字一致未取证（原版是否用 `-b` 未证实）。
- 本批只有 offscreen（`runSkikoComposeUiTest`）证据；**无原生窗口与像素证据**（本机 `screencapture` 仍返回
  `could not create image from display`）。

# Batch CH：Diff「复制 git apply 命令」（2026-10-12）

## §1 本批目标

补齐 diff「变更选项」菜单里 ASAR 明确存在、此前未实现的普通动作 `codex.review.copyGitApplyCommand`（「复制 git apply 命令」）。
ASAR 只读证据（`logs/asar-copy-apply.txt`，来源 `/tmp/asar-new/webview/assets/`）：

- 文案：`zh-CN-b05cfd55c534.js` 三条相邻——`"codex.review.copyGitApplyCommand":"复制 git apply 命令"`、`"…patchError":"未能生成 git apply 命令"`、`"…toast":"已将 git apply 命令复制到剪贴板"`。
- 形态：`pull-request-code-review-be17c7c34414.js` 的菜单组件 `jr(...)` props 为 `onClickCopyGitApplyCommand` / `copyGitApplyCommandDisabled` / `showCopyGitApplyCommand`——**普通动作项**（不是 `onToggle*` 勾选项）。同菜单的 `richPreview` 是 `be.CheckboxItem`，本批不动它。

**未观察到**：命令的具体形式、失败判定条件、toast 渲染位置。本批只实现可观察的那一层（菜单动作项 + 生成命令 + 写剪贴板 + 成功/失败文案），其余按 Harness 取值并在契约 §2.1 写明。

## §2 生产改动

（3 个文件）

| 文件 | 改动 |
| --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitDiffApply.kt`（新增） | `public fun gitApplyCommand(patch: String): String?`——quoted heredoc（`git apply <<'HARNESS_GIT_APPLY_EOF'` + 完整 patch + 分隔符，冲突时加 `_1`/`_2`…）；patch 为空/全空白或无 `diff --git ` 行时返回 `null`（Harness 取值）。 |
| `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt` | `GitPatch` 新增 `copyGitApplyCommandDisabled: Boolean = false`，内部 `LocalClipboardManager` + `remember(patch) { gitApplyCommand(patch) }` 计算命令与反馈文案，并把 `copyFeedback` 透传进 `GitDiffView`；`GitDiffView` 在自身末尾渲染反馈（`testTag = git-diff-copy-git-apply-feedback`）；`GitDiffOptionsMenu` 追加第 8 项 `HarnessMenuEntry.Item(key = "copy-git-apply", text = "复制 git apply 命令", enabled = !copyGitApplyCommandDisabled)`，`onSelect` 增 `"copy-git-apply" -> onCopyGitApplyCommand()`。 |
| `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt` | 两处 `GitPatch(...)` 调用点接线 `copyGitApplyCommandDisabled = !state.pageCanRead`。 |

实现要点：反馈文本**不**用外层 `Column` 包裹 diff——那样会把 `GitDiffView` 原本贡献给调用方布局作用域的子节点（提示语、工具栏 `Row`、视口 `Box`）重新挂到新 `Column` 下，把它们的间距从 `SettingsFormSection` 的 16dp 变成 8dp，造成与 ASAR 无关的整页位移。改为在 `GitDiffView` 末尾渲染：无反馈时子节点集合与该功能加入前逐字节一致。

## §3 测试改动

（3 个文件）

- `apps/shared/test/.../client/git/GitDiffApplyTest.kt`（新增）：4 个用例——多文件 patch 生成 heredoc 且含 `diff --git ` 全量、`EOF` 冲突时改用 `_1`、空/全空白返回 `null`、无 `diff --git ` 行返回 `null`。
- `apps/jvm-app/test/.../desktop/GitDiffViewInteractionTest.kt`（+1）：`diffOptionsMenuCopyGitApplyCommandWritesClipboardAndReportsFailure`——断言页面加载时**没有**反馈节点（不预先报错）、菜单项可点、点击后剪贴板等于 `gitApplyCommand(patch)` 且出现成功文案；切换到无法生成命令的 patch 后反馈消失、项仍可点、点击后出现「未能生成 git apply 命令」。
- `apps/jvm-app/test/.../desktop/GitWorktreesScreenshotTest.kt`：在每例最终 `focus(...)` 截图前调用 `parkGitPointer()`。原因见 §5.1——菜单多一行后弹层变高，测试合成指针的落点改变，`git-hide-imports` 两例 600x800 视口截图会带上 Diff 来源选择控件的悬停底色。归位后这两张图与该功能加入前逐字节一致。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-ch-build -p jvm -m shared`（全量） | **584 found / 584 started / 584 passed / 0 failed**（`logs/shared-full.txt`） |
| 同 `-m shared --include-classes=…GitDiffApplyTest` | 4 found / 4 passed / 0 failed（`logs/GitDiffApplyTest.txt`） |
| 同 `-m jvm-app --include-classes=…GitDiffViewInteractionTest` | 10 found / 10 passed / 0 failed（新增 1，上批 9）（`logs/GitDiffViewInteractionTest.txt`） |
| 同 `-m jvm-app --include-classes=…GitWorktreesScreenshotTest`（输出重定向 `/tmp/ch-captures-park`） | 9 found / 9 passed / 0 failed |
| 同 `-m jvm-app`（全量） | **713 found / 5 skipped / 708 started / 708 passed / 0 failed**（exit 0）（`logs/jvm-app-full.txt`） |

全量 `shared` 584 比 CG（580）多 4、`jvm-app` 713 比 CG（712）多 1，正是本批新增的 `GitDiffApplyTest`（4）与交互用例（1）。本批两处全量均 **0 失败**（CF 的 PTY 字体抖动、CG 记录过的 `GitCommitInteractionTest` 超时均未复现，故本批无需抖动归属说明）。

## §5 截图产物核对

基线 = `/tmp/ch-captures-before-1791803364`（CE/CF/CG 之后、CH 之前的 `docs/desktop-ui/captures/` 快照）。全量 `jvm-app` 会重写 `docs/desktop-ui/captures/`，与基线比较分类见 `logs/captures-classification.txt`：

- 还原前 `changed 328 / new 0 / removed 0`：`json 284`（其中 **260 个去数字后完全一致**，仅 Compose 节点 id 抖动；**24 个实质变化**，全在 `git-worktrees`）、`png 44`（**24 个尺寸变化** 200x260→200x296，见 §5.2；**20 个像素不同**，全在 `scheduled-execution`，是早于 `ScheduledLocalExecutionModel` 披露文案的**历史冻结基线**，与本批无关）。
- 把 260 个仅数字抖动的 `.json` 与 20 张 `scheduled-execution/*.png` 按先例从快照**逐字节还原**后，与基线差 **`changed 48 / new 0 / removed 0`**（48 = 24 PNG + 24 JSON，全部是 `git-worktrees` 三组 `options-menu-*`），见 `logs/captures-verify.txt`。
- 隔离重跑 `GitWorktreesScreenshotTest` 两次（`/tmp/ch-captures-fix`、`/tmp/ch-captures-fix2`，同一最终代码）`diff -rq` **EXIT=0，完全逐字节一致**（`logs/captures-reproducibility.txt`）。

### §5.1 两次 600x800 视口截图的悬停底色（定位与消除）

未归位指针时，`git-hide-imports-{dark,light}-600x800-focused-viewport.png` 与基线不同：差异 bbox = `rows 343..382 / cols 16..583`，落在那 40px 满宽的 **Diff 来源选择控件**「未暂存（工作树与暂存区）」上，底色由 `RGB(40,40,40)` 变成 `RGB(55,55,55)`。这与 `HarnessSelectTrigger` 的 `hovered/pressed -> SurfaceSelected`（否则 `SurfaceElevated`）一致（`HarnessActionControls.kt:176-180`），即**悬停底色**；把旧图整体上下平移 -6..+6 行相关，diff 在 0 处最小，排除滚动位移。成因是菜单由 7 行增至 8 行后弹层变高，测试合成指针在关闭菜单后的落点改变。在该截图前调用既有 `parkGitPointer()` 后，两张图回到基线 `4b8ab6943c4b` / `c0ae5ab172c1`，且 `diff -rq` 显示该改动**仅影响这 2 张图**（`logs/park-pointer.txt`）。

### §5.2 菜单确实多了一行（像素证据）

`logs/menu-pixels.txt`：选项菜单弹窗（dark/light 各一例转像素比较）尺寸一致地由 **200x260 变为 200x296**（`+36px`，正好一行菜单项）；新增带 `rows 260..295` 有 1171/1175 个非背景像素（文本带在 `rows 267..281`，`row 295` 为弹层底边框），即确实多渲染一个菜单行。目视 `menu-dark-stacked.png`（上=基线、下=本批）：前七项「自动换行 / 折叠全部差异 / 加载完整文件 / 隐藏空白字符 / ✓ 词级差异 / 隐藏导入内容 / 刷新」逐行一致，本批在底部多出「复制 git apply 命令」。

## §6 边界（明确不主张）

- **toast 载体**：原版用 toast（`…toast` 文案），本仓没有全局通知组件，改为 diff 区域内联 `HarnessText`（成功/失败文案与原文一致，位置不同）。
- **命令形式与失败规则的原版语义**：未观察到，均为 Harness 取值（heredoc 形式、`diff --git ` 存在性判定）。
- **未在真实仓库执行生成的命令**：只验证命令格式与 patch 完整保留，没有 `git apply` 真跑证据。
- **提示重置**：切换 patch / 重开页面后提示消失（`remember(patch)`），未复刻原版 toast 的自动消失时长。
- `richPreview` 移入菜单（ASAR 里是菜单内 `CheckboxItem`）仍未做；`autoSwitchToUnified`、行内评论、sticky 吸附、上下文展开、远端 Diff、图片/SVG/PDF 预览仍未做。
- 定时任务后台执行、附件协议、MCP/Hooks/Scripts 真实连接仍未做。
- **原版同尺寸像素与原生窗口证据**（未做）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-ch-build -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-ch-build -p jvm -m shared --include-classes='im.hikaru.harness.client.git.GitDiffApplyTest'
./kotlin test --build-dir=/tmp/harness-ch-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitDiffViewInteractionTest'
HARNESS_GIT_WORKTREES_CAPTURE_DIR=/tmp/ch-captures-park ./kotlin test --build-dir=/tmp/harness-ch-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitWorktreesScreenshotTest'
./kotlin test --build-dir=/tmp/harness-ch-build -p jvm -m jvm-app
```

截图重写只影响 `docs/desktop-ui/captures/`；`HARNESS_GIT_WORKTREES_CAPTURE_DIR` 把输出改到临时目录。

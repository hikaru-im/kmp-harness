# Diff「复制 git apply 命令」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动原包、未联网。抄录见
`../verification/git-apply-command-2026-10-12/entry-ch1/logs/asar-copy-apply.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | `"codex.review.copyGitApplyCommand":"复制 git apply 命令"`、`"codex.review.copyGitApplyCommand.patchError":"未能生成 git apply 命令"`、`"codex.review.copyGitApplyCommand.toast":"已将 git apply 命令复制到剪贴板"`（三条相邻）。 |
| E02 | `pull-request-code-review-be17c7c34414.js` | 选项菜单组件 `jr(...)` 的 props：`onClickCopyGitApplyCommand`（回调）、`copyGitApplyCommandDisabled`（禁用）、`showCopyGitApplyCommand`（可见性）。与同菜单的 `onToggle*` 勾选项不同，它是**普通动作项**。 |
| E03 | `pull-request-code-review-be17c7c34414.js` | 同菜单里 `richPreview` 是 `be.CheckboxItem`（`onToggleRichPreview`），`copyGitApplyCommand` 是动作项——两者形态不同，本批只做后者。 |

（菜单整体：`wrap` / `expand` / `loadFullFiles` / `wordDiffs` / `hideWhitespace` / `hideImports` / `refresh` / `copyGitApplyCommand` 与 `richPreview`；批 CE/CF/CG 已抄录前七项。）

## 2. 已观察到与未观察到的语义

- **已观察**：菜单里有一个动作项「复制 git apply 命令」，点击后把生成的 `git apply` 命令写入剪贴板；成功有
  「已将 git apply 命令复制到剪贴板」提示，失败有「未能生成 git apply 命令」错误；该项有独立 disabled prop。
- **未观察**：命令的**具体形式**（heredoc / 临时文件 / `git apply -` / 是否带 `--3way` 等参数）在 bundle 中不可见。
- **未观察**：「无法生成」的判定条件不可见（例如空 patch、二进制、非文本、缺 `diff --git`）。
- **未观察**：toast 的渲染位置、生命周期与样式（bundle 中只有文案，没有该提示的渲染结构证据）。

因此本批实现**可观察的那一层**：菜单动作项 + 生成命令 + 写剪贴板 + 成功/失败文案；命令形式、失败判定与提示载体
按 Harness 取值实现并写明（§2.1）。

### 2.1 Harness 取值（明确记录）

| 维度 | ASAR | 本仓 CH |
| --- | --- | --- |
| 命令形式 | 未观察到 | quoted heredoc：`git apply <<'<分隔符>'` + 完整 patch + 分隔符；分隔符以 `HARNESS_GIT_APPLY_EOF` 为基准，若 patch 中出现同名整行则加 `_1`、`_2`… 直到不冲突 |
| 失败判定 | 未观察到 | patch 为空/全空白，或**没有任何以 `diff --git ` 开头的行**时返回 `null`（无法生成） |
| 成功提示 | toast「已将 git apply 命令复制到剪贴板」 | diff 区域内联 `HarnessText`（`testTag = git-diff-copy-git-apply-feedback`），文案与原版一致 |
| 失败提示 | `patchError`「未能生成 git apply 命令」 | 同一内联文本，红色；**只在用户点击后出现**，不在页面加载时预先显示 |
| 禁用条件 | 有独立 disabled prop | 页面在读取中（`!state.pageCanRead`）时禁用；patch 无法生成命令时**保持可点**，点后给出失败提示（避免出现「禁用 + 已报错」的矛盾态） |

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `client/git/GitDiffApply.kt`（新增，shared） | `public fun gitApplyCommand(patch: String): String?`——按 §2.1 生成 heredoc 命令；无法生成时返回 `null`。KDoc 说明原版命令形式与失败规则未观察到。 |
| `client/git/GitPageComponents.kt`（shared） | `GitPatch` 新增 `copyGitApplyCommandDisabled: Boolean = false`；内部用 `LocalClipboardManager` + `remember(patch) { gitApplyCommand(patch) }` 计算命令与反馈文案，透传 `copyFeedback` 给 `GitDiffView`；`GitDiffView` 在自身末尾渲染该反馈文本（`testTag = git-diff-copy-git-apply-feedback`），使 `GitPatch` 向调用方贡献的子节点与加入本功能前完全一致（不新增外层 `Column`，否则会重新挂载 Diff 的提示/工具栏/视口并改变其间距）；`GitDiffOptionsMenu` 菜单追加第 8 项 `HarnessMenuEntry.Item(key = "copy-git-apply", text = "复制 git apply 命令", enabled = !copyGitApplyCommandDisabled)`，`onSelect` 增 `"copy-git-apply" -> onCopyGitApplyCommand()`。 |
| `client/git/GitPages.kt`（shared） | 两处 `GitPatch(...)` 调用点接线 `copyGitApplyCommandDisabled = !state.pageCanRead`。 |
| `apps/jvm-app/test/.../GitWorktreesScreenshotTest.kt`（测试） | 在每例最终 `focus(...)` 截图前调用 `parkGitPointer()`。原因：菜单多一行后弹层变高，测试合成指针在关闭菜单后的落点随之改变，`git-hide-imports` 两例的 600x800 视口截图会带上 Diff 来源选择控件的悬停底色（`SurfaceSelected` 55 vs `SurfaceElevated` 40）。归位指针后这两张截图像素与该功能加入前的基线逐字节一致；此改动仅影响这 2 张截图。 |

## 3.1 未实现（明确不主张）

- **toast 载体**：原版用 toast；本仓没有全局通知组件，改成 diff 区域内联文本（成功/失败文案与原文一致，位置不同）。
- **命令形式与失败规则的原版语义**：均为 Harness 取值（未观察到）。
- **`richPreview` 移入菜单**：仍未做（目前是工具栏独立按钮；ASAR 里它是菜单内的 `CheckboxItem`）。
- **未在真实仓库执行生成的命令**：只验证了命令格式与 patch 完整保留（无 `git apply` 真跑证据）。
- **「复制 git apply 命令」的重置行为**：切换 patch / 重开页面后提示消失（`remember(patch)`），未复刻 toast 的自动消失时长。
- **原版同尺寸像素与原生窗口证据**（未做）。

## 4. 验收

见 `../verification/git-apply-command-2026-10-12/entry-ch1/review.md`。各批证据独立，不累加为总体覆盖率。

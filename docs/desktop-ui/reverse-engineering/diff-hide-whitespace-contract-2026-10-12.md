# 契约：Diff「变更选项」隐藏空白字符（2026-10-12）

范围：Diff 的「变更选项」菜单（Codex `codex.review.header.moreOptions`）新增 Codex 的「隐藏空白字符」开关。
该开关是 **Diff 输入**而不是客户端渲染过滤器：Codex 把它作为 `review-diff` 请求参数发给 Host。本仓沿用同一
架构——`GitDiffRequest` 增加 `hideWhitespace`，本机 Git 后端按 git 的忽略空白开关计算补丁；不改变任何写入语义。

## §1 只读依据

来源（只读，未联网）：`/tmp/asar-new/webview/assets/` 下的 `zh-CN-b05cfd55c534.js`、
`pull-request-code-review-be17c7c34414.js`、`app-initial-6350d04672d0.js`。取证见
`../verification/diff-hide-whitespace-2026-10-12/entry-bv1/logs/asar-hide-whitespace-evidence.txt`。

| 项 | 内容 |
| --- | --- |
| zh-CN 文案 | `codex.review.whitespace.hide` = 隐藏空白字符 |
| 英文默认 | defaultMessage = `Hide white space`；description = `Menu item to hide whitespace-only diff changes` |
| 架构 | 菜单把它传给 Host：`ns('git').request({method:'review-diff', params:{... hideWhitespace, source, baseBranch, commitSha ...}})` —— 是 Diff 的**输入**，不是对已返回文本的过滤 |
| 持久化 | 独立偏好键 `hideDiffWhitespace`（本批未接偏好持久化） |
| 同菜单其它项 | `codex.review.wordDiffs.label`（词级差异）、`codex.review.imports.hide`（隐藏导入）、`loadFullFiles` 等仍待；`codex.review.wrap.label`（自动换行）与 `codex.review.expandOrCollapseDiffMenu.*` 已在 BQ 批次实现 |

## §2 本仓映射

| 位置 | 变化 | 依据 |
| --- | --- | --- |
| `GitDiffRequest` | 新增 `hideWhitespace: Boolean = false` | 与 `review-diff` 参数同形 |
| `DesktopGitBackend.diff` | `hideWhitespace` 为真时，在 `--` 前加 `--ignore-all-space` | Host 端计算空白无关补丁 |
| `GitDiffOptionsMenu` | 新增菜单项「隐藏空白字符」（key `hide-whitespace`，`selected` 反映当前值） | `codex.review.whitespace.hide` |
| `GitPatch` / `GitDiffView` | 接收 `hideWhitespace` 与 `onToggleHideWhitespace` | 承载开关状态 |
| `GitPages` | 切换时 `controller.selectDiff(request.copy(hideWhitespace = ...))`，触发重读 | 开关是 Diff 输入，必须重读 |

说明：`--ignore-all-space` 是 git 对「忽略空白差异」的标准开关（等价 `-w`），与 Codex 送往 Host 的
`hideWhitespace` 语义一致（description 原文 "hide whitespace-only diff changes"）。原版是否另用 `-b`
（`--ignore-space-change`）而非 `-w` 未取证，本仓取 `-w` 并在 review 边界记录。

## §3 不变式

- 新增字段有默认值，`GitDiffRequest()` / 两参构造仍可用，既有请求相等断言不变（`GitDiffSnapshot.request` 原样回显）。
- 开关不改变任何写入行为：仍只读 `git diff`；`--cached` / 基准 commit 解析 / `--no-ext-diff` 等其它参数与顺序不变，
  `--ignore-all-space` 只加在 `--` 之前。
- 开关状态保存在 `GitState.diffRequest`（页面级），不写入仓库、不改 `.git`。
- `GitDiffOptionsMenu` 的其它行为（自动换行、展开 / 折叠全部）不变。

## §4 未验证边界

- 未接 Codex 的 `hideDiffWhitespace` 偏好持久化（重开页面回到关闭）。
- 只在本地 Git 后端实现；远端 / Relay Host 未定义该参数（本批未接远端 Diff）。
- 未复刻原版菜单的刷新、词级差异、隐藏导入、加载完整文件等其余项。
- 无原生窗口 / 像素证据。

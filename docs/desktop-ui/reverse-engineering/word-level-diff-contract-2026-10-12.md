# Diff「词级差异」强调：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动原包、未联网。抄录见
`../verification/word-level-diff-2026-10-12/entry-cd1/logs/asar-worddiffs.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `app-initial-6350d04672d0.js` | 设置键 `sja = 'wordDiffsEnabled.2'`；默认值 `mja = Fu(sja, !0)`（即默认开启）。同区的 `wrapCodeDiff.2`、`hideDiffWhitespace`、`diffRichPreview` 默认均为 `!1`（关闭），说明只有词级差异是默认开启的。 |
| E02 | `pull-request-code-review-be17c7c34414.js` | Diff 选项菜单组件 `jr({onClose, wordDiffsEnabled, showCopyGitApplyCommand, showHideImports, showHideWhitespace, showLoadFullFiles, …, onToggleWordDiffs, …})`；菜单里该开关是 `be.CheckboxItem`，文案 `<Z id="codex.review.wordDiffs.label" defaultMessage="Word diffs" description="Toggle for highlighting individual changed words in the diff view" />`。 |
| E03 | `zh-CN-b05cfd55c534.js` | `"codex.review.wordDiffs.label":"词级差异"`（同文件其余语言包键名一致）。 |
| E04 | `pull-request-code-review-be17c7c34414.js` | 会话组件把状态透传为 `{…, wordDiffsEnabled: E, onToggleWordDiffs: ge, …}`，即开关状态由 diff 视图外部持有并以布尔回调切换。 |

## 2. 已观察到与未观察到的语义

- **已观察**：diff 视图有一个「词级差异」开关，默认开启，位于「变更选项」菜单内，勾选框形式；语义是
  `highlighting individual changed words in the diff view`（强调一行内**个别被改动的词**，而不是整行）。
- **未观察**：原版用于强调的具体颜色 token（`app-shared` 里未观察到对应 `--diff-*-word` 变量）、分词与配对算法
  （是否按词边界、是否做相似度匹配、配对数量不等时如何取舍）、以及开关关闭后是否连行背景一起变化。原版也没有
  留下同状态的像素值可供比对。

因此本批实现**可观察的那一层**：在「变更选项」菜单中新增「词级差异」开关（默认开），开启时在配对的
删除/新增行内，把真正变化的词段用更强的一层底色强调；关闭后整行只保留原有的行背景。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `client/git/GitWordDiff.kt`（新增，shared） | 新增 `GitWordSpan(start, end)`、`GitWordDiff(removed, added)`；`gitWordTokens` 把一行切成词 / 空白 / 单字符三类 run；`gitWordDiff(old, new)` 用「公共词前缀 + 公共词后缀 trim，中间整体标为变化」算法得到两侧 span（相等则空，整行不同则整行标），相邻 span 合并；`gitUnifiedWordDiff(lines)` 只对同一 hunk 内配对的 `-`/`+` 行按下标配对求 diff（遇到 context/header/meta 行 flush，未配对的尾部行不产生行内 span，因为整行已有背景）；`gitSplitWordDiff(rows)` 只对 `Change` 行按左右文本求 diff；`appendWordSpans` 按 span 追加，未标段原样追加，**每个字符仍然只出现一次**。强调层 alpha `WORD_DIFF_EMPHASIS_ALPHA = 0.35f`（Harness 取值，比行背景 `0x1A`/`0x2E` 更强）。 |
| `client/git/GitDiffLines.kt`（shared） | `gitDiffSplitPaneText(...)` 新增可选 `wordDiff` / `wordAddedBackground` / `wordRemovedBackground`：在原有整行背景**之内**叠加左右的词级强调，默认参数保持旧行为。 |
| `client/git/GitPageComponents.kt`（shared） | `GitDiffView` 新增 `wordDiffs` 状态（默认 `true`，对应 E01）；`GitDiffOptionsMenu` 在 `wrap`/`expand`/`hide-whitespace` 之后新增第 4 项 `HarnessMenuEntry.Item(key = "word-diffs", text = "词级差异", selected = wordDiffs)`，选中态沿用既有菜单的 `✓` 写法；`GitDiffFileCard` / `GitUnifiedDiff` / `GitUnifiedDiffScroll` / `GitWrappedUnifiedDiff` / `GitDiffSplitView` 全部接受 `wordDiffs` 并把 `gitUnifiedWordDiff`/`gitSplitWordDiff` 的结果按行/行对下标喂给渲染；新增 `gitDiffLineMarker` + `appendWordDiffLine`，把整行拆成「marker（无强调）+ 文本（可含强调）」，可选中文本与拆分前逐字符一致。 |

### 3.1 未实现（明确不主张）

- **原版算法与配色**：强调色、分词边界、配对策略均为 Harness 取值；未观察到原版 token，也未做像素比对。
- **勾选框控件形态**：原版是 `CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓` 选中写法（该菜单的其余三项同样如此）。
- **「变更选项」菜单其余项**：`showCopyGitApplyCommand`（复制 `git apply` 命令）、`showHideImports`（隐藏 import）、
  `showLoadFullFiles`（加载完整文件）、`refresh` 仍未实现。
- **行内评论 / sticky / 折叠上下文**：与本批无关，仍未做。
- **持久化**：原版把开关写进设置（`wordDiffsEnabled.2`）；本仓只保存在页面组合状态里，未落盘。
- **原版同尺寸像素与原生窗口证据**（未做）。

## 4. 验收

见 `../verification/word-level-diff-2026-10-12/entry-cd1/review.md`。各批证据独立，不累加为总体覆盖率。

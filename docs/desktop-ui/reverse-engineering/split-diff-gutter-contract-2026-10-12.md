# 分栏 Diff 行号 Gutter：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

本批只读复核当前安装包 webview 固定资产，未抓包；抄录见
`verification/split-diff-gutter-2026-10-12/entry-bg1/logs/asar-evidence.txt`。
BF 批已完成统一视图的单行号列；本批处理同一契约里剩下的「分栏视图两侧行号列」。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| H01 | `webview/assets/app-initial-6350d04672d0.js` | 分栏渲染器为每个 pane 元素（`[data-deletions]` / `[data-additions]`）生成**恰好两个子元素** `[gutter, content]`，并断言 `gutter.children.length === content.children.length`，否则抛 `gutter and content children dont match`；即**每个内容行在自己那一行里有一个 gutter 单元格** |
| H02 | 同上 | `DTt({split})` 在 `pre` 上写 `data-diff-type: 'split'`；`eTt` 由行类型映射 pane（`change-deletion`→`deletions`、`change-addition`→`additions`） |
| H03 | `webview/assets/codex-diff-css-a6773575cd64.js` | 分栏 hunk 分隔行被**复制到两侧的 gutter 与 content 单元格**（注释原文 `Pierre duplicates split separators into each gutter and content cell.`）：`[data-deletions] [data-gutter] [data-separator='line-info'] [data-separator-wrapper]` 与 `[data-additions] …` 各有一条规则 |
| H04 | 同上 | 文件内只有一处 `--diffs-column-number-width` 引用，统一视图 `grid-template-columns: var(--diffs-column-number-width) minmax(0, 1fr)`；分栏两 pane 共用同一列宽变量 |
| H05 | 同上 | 行级底色按类型分配到「正文单元格」与「行号单元格」两组：`[data-line-type='change-addition']:is([data-line],[data-no-newline]) → --diffs-bg-addition`，`:is([data-column-number],[data-gutter-buffer]) → --codex-diffs-addition-number`；删除侧同构 |
| H06 | `webview/assets/app-initial-6350d04672d0.js` | 行号/缓冲构造器与统一视图相同：`Awt(type, number, lineIndex)` → `div[data-column-number]`（`number == null` 时不渲染数字 span），`Fx(type, buffer, size)` → `div[data-gutter-buffer]` |

## 2. 已观察到与未观察到的语义

- **已观察**：分栏每个 pane 是「gutter 列 + content 列」，逐行一一对应；hunk 分隔行在两侧各有自己的 gutter/content 单元格；两侧共用同一列宽变量；变更行在**正文与行号单元格上都带底色**（正文用 `--diffs-bg-*`，行号用 `--codex-diffs-*-number`）。
- **未观察到**：`--diffs-column-number-width` 的像素值；`--codex-diffs-*-number`（`color-mix` 合成）的实际字面色值；分栏 gutter 的 `padding-inline` 精确内缩；两侧 pane 的宽度分配比例是否严格 50/50；分栏行号的字号。

本批只实现**可观察的那一层**：给分栏两侧各画一个不可选中的行号列，行号由本仓解析器从 `@@` 头推出的旧/新文件行号；变更行的 gutter 单元格与 pane 正文一起用本仓既有底色着色；不发明 CSS 片段里没有的色值。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitDiffLayout`（shared） | `GitDiffSplitRow` 新增 `oldNumber`/`newNumber`（旧文件行号落在左 pane，新文件行号落在右 pane，被补空的一侧为 null）；`splitDiffBody` 按 `@@ -old +new @@` 头重置两个计数器，删除行推进旧号、新增行推进新号、上下文行同时推进两者；`HUNK_HEADER` 由 `GitDiffLines` 的私有正则提为 `internal` 供两个解析器共用 |
| `GitDiffLines`（shared） | 新增纯函数 `gitDiffSplitGutterNumber(row,left)`、`gitDiffSplitGutterWidth(rows)`（两 pane 共用宽度，对应 H04）、`gitDiffSplitGutterText(row,width,left)`、`gitDiffSplitGutterAnnotatedString(row,width,left,…)`（单侧单元格 + 逐行底色，被补空的一侧留同宽空白缓冲）、`gitDiffSplitPaneText(row,left,…)`（pane 正文，变更侧加整行底色；字符不变，语义串不变） |
| `GitPageComponents`（shared） | 分栏正文由「外层 `SelectionContainer` 包住整块」改为每行 `Row { 左行号(不可选中) ; SelectionContainer{左正文} ; 右行号(不可选中) ; SelectionContainer{右正文} }`，hunk 头也各自包一个 `SelectionContainer`；行号列落在选区之外（复制 pane 正文不含行号），且与所属行同一 `Row`，长行折行时行号仍停在该行首行 |

### 3.1 未实现（明确不主张）

- 原版 `--codex-diffs-*-number`（`color-mix` 合成的行号底色/字色）与 `--diffs-column-number-width` 精确列宽/`padding-inline` 内缩：本批行号字色沿用 `TextMuted`，列宽由最大位数 + 固定间隙推出，底色沿用 `DiffAddedBackground`/`DiffRemovedBackground`（与正文同色，非 `color-mix` 结果）。
- 分栏 hunk 分隔行的展开按钮（`data-expand-up/down`）、分隔行在两 pane 的圆角/内缩细节。
- 上下文行在 pierre/diffs 里旧号与新号的确切取用：本批两侧都按各自文件的行号推进（左旧号、右新号），未逐像素比对。
- 分栏 pane 底色只覆盖正文文本与行号单元格（与统一视图一致），未铺满整条 pane 宽度的色带。
- 无原生窗口与同尺寸原版像素证据。

## 4. 验收

见 `verification/split-diff-gutter-2026-10-12/entry-bg1/review.md`。各批证据独立，不累加为总体覆盖率。

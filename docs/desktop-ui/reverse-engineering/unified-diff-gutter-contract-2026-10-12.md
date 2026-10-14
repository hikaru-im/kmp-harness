# 统一 Diff 行号 Gutter：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

本批只读复核当前安装包 webview 固定资产，未抓包；抄录见
`verification/unified-diff-gutter-2026-10-12/entry-bf1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| G01 | `webview/assets/app-initial-6350d04672d0.js` | gutter 单元格构造器 `Awt(type, number, lineIndex)` 输出 `div[data-line-type][data-column-number][data-line-index]`，其内是 `span[data-line-number-content]` 包住 `${number}`；`number == null` 时不渲染 `span`（即空白缓冲行） |
| G02 | 同上 | 缓冲构造器 `Fx(type, buffer, size)` 输出 `div[data-gutter-buffer][data-buffer-size][data-line-type]`，`grid-row: span N; min-height: calc(N * 1lh)`，用于没有行号的一侧占位对齐 |
| G03 | `webview/assets/codex-diff-css-a6773575cd64.js` | 统一视图 hunk 分隔行 `[data-separator='line-info'] [data-separator-wrapper]` 的 `grid-template-columns: var(--diffs-column-number-width) minmax(0, 1fr)`，即「行号列 + 内容列」；`[data-column-number]` 有独立内缩规则 |
| G04 | 同上 | `[data-column-number], [data-gutter-buffer]` 命中行级背景合成（`--codex-diffs-addition-number` / `--codex-diffs-deletion-number`，由 `--diffs-addition-base`/`--diffs-deletion-base` 与原/暗底色 `color-mix` 得出） |

## 2. 已观察到与未观察到的语义

- **已观察**：统一视图每行有一个独立的行号单元格（`data-column-number`），无行号处用缓冲单元格（`data-gutter-buffer`）；hunk 分隔行按「行号列 + 内容列」两列排布；行号单元格参与行级底色合成。
- **未观察到**：`--diffs-column-number-width` 的具体像素/字宽；hunk 分隔行的 `data-expand-up`/`data-expand-down` 展开按钮在 Codex 主题下的显隐；行号字号、`--diffs-gap-inline` 精确内缩；`color-mix` 合成后的字面色值；分栏视图两侧各自的行号列排布细节。

本批只实现**可观察的那一层**：给统一 Diff 的每一行画一个不可选中的行号列，按本仓解析器已算出的行号渲染；不发明 CSS 片段里没有的色值或展开按钮。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitDiffLines`（shared） | 新增纯函数 `gitDiffGutterNumber(line)`：新增/上下文行取新文件行号，删除行取旧文件行号，hunk 头/`\ No newline`/其它 meta 取 null（空白缓冲）；新增纯函数 `gitDiffGutterText(lines)`：逐行输出右对齐（左侧空格补齐到公共宽度）的行号，行数与 `parseGitDiffLines` 一致，空 body 返回空串 |
| `GitDiffLines`（shared） | 新增纯函数 `gitDiffGutterAnnotatedString(lines, added, removed, separator)`：单个 `AnnotatedString`，每行单元格（编号 + 间隙）按 `Added`/`Removed`/`HunkHeader` 加 `SpanStyle(background=…)`，其余不着色；单元格填充用非断行空格（U+00A0，行尾不被裁剪），使 gutter 色带连续到正文并与 `gitDiffGutterText` 的文本一致 |
| `GitPageComponents`（shared） | `GitUnifiedDiff` 从「整块 `Text(annotated)`」改为 `Row { 行号 Text(不可选中) ; SelectionContainer { 补丁 Text } }`：行号列在 `SelectionContainer` 之外（复制不含行号）且带逐行底色（`[data-column-number]`/`[data-gutter-buffer]` 参与行级底色合成），补丁正文仍在 `SelectionContainer` 内，逐字符保留 `+`/`-`/空格标记，`git-diff-file-patch` 标签与横向滚动语义不变 |
| `GitPageComponents`（shared） | 去掉视口级 `SelectionContainer`，改为各可选区自带：文件卡头部列、富文本预览、预览回退提示、分栏正文各自包 `SelectionContainer`；这样统一 Diff 的行号列不会被外层容器纳入选区 |

### 3.1 未实现（明确不主张）

- 原版 `color-mix` 合成出的行号**字色**（`--codex-diffs-*-number`）与 `--diffs-column-number-width` 精确列宽：本批行号字色沿用 `TextMuted`，列宽由最大位数 + 固定间隙推出（底色已按行类型与正文同色）。
- hunk 分隔行上的展开按钮、`hunkSeparators:'line-info'` 的精确内缩、分栏视图两侧行号列。
- 上下文行显示旧号还是新号在 pierre/diffs 里的确切选择：本批按统一 Diff 惯例取新号（新增/上下文）与旧号（删除），未逐像素比对。
- 原版同尺寸像素、原生窗口；本批只有生产解析器/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/unified-diff-gutter-2026-10-12/entry-bf1/review.md`。各批证据独立，不累加为总体覆盖率。

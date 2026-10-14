# 统一 Diff 逐行着色：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

本批重新只读抽取了当前安装包的固定资产，不重新抓包；证据抄录见
`verification/unified-diff-lines-2026-10-11/entry-ax1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| L01 | `webview/assets/codex-diff-css-a6773575cd64.js` | 行级规则：`[data-line-type='change-addition']:is([data-line], [data-no-newline]) { --diffs-computed-diff-line-bg: var(--diffs-bg-addition) }`，删除行对称使用 `--diffs-bg-deletion`；行号单元格 `[data-column-number]`/`[data-gutter-buffer]` 用 `--codex-diffs-addition-number`/`--codex-diffs-deletion-number`（由 `--diffs-addition-base`/`--diffs-deletion-base` 与原/暗底色 `color-mix` 得出） |
| L02 | 同上 | hunk 头是独立分隔行：`[data-separator='line-info']`，其 `[data-separator-wrapper]` 在统一视图下 `grid-template-columns: var(--diffs-column-number-width) minmax(0, 1fr)`，即「行号列 + 内容列」；分隔面用 `--codex-diffs-separator-surface`（`color-mix(surface 94%, color-text-info)`） |
| L03 | 同上 | `data-no-newline` 与 `[data-line]` 同属行级背景规则，即 `\ No newline at end of file` 也按行渲染 |
| L04 | `webview/assets/code-diff-38f339f0a601.js` | 文件块渲染透传 `hunkSeparators`（调用点见 `editor-diff-page` 的 `hunkSeparators:'line-info'`），确认统一/分栏共用同一分隔策略 |
| L05 | `webview/assets/zh-CN-b05cfd55c534.js` | 工具栏既有文案：`codex.diffView.switchToUnified`「切换至统一差异视图」、`codex.diffView.auto`「自动」、`codex.diffView.richPreviewToggle`「切换富文本预览」、`codex.diffView.filesChanged`「{fileCount, plural, other {# 个文件已更改}}」 |

## 2. 已观察到与未观察到的语义

- **已观察**：统一视图逐行区分新增/删除/上下文，新增与删除行各有独立行背景；`@@` hunk 头单独成行作为分隔，带「行号列 + 内容列」两列网格；`\ No newline` 行同样参与行级着色。
- **未观察到**：`--diffs-addition-base`/`--diffs-deletion-base` 的具体色值（由主题变量合成，CSS 片段里是 `color-mix` 表达式而非字面量）；hunk 分隔行上的展开按钮（`data-expand-up`/`data-expand-down`）在 Codex 主题里的显隐策略；行号的字宽与对齐像素；语法高亮的 token 规则。

因此本批只实现**可观察的那一层**：把后端已返回的 patch 文本按行分类为新增/删除/上下文/`@@` 头/`\ No newline`/其它，并对新增行、删除行、`@@` 头分别着色；不发明 CSS 片段里没有的色值（使用本仓 `HarnessTokens` 既有的 `DiffAddedBackground`/`DiffRemovedBackground`/`BorderSubtle`）。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitDiffLines`（新增，shared） | `GitDiffLineKind{Context,Added,Removed,HunkHeader,NoNewline,Meta}`、`GitDiffLine(kind,text,oldNumber?,newNumber?)`、纯函数 `parseGitDiffLines(body)`：`@@ -a,b +c,d @@` 头解析并重置两侧行号；hunk 内每行按自身首字符分类，`+`/`-`/空格剥掉一个标记，`\` 开头记为 `NoNewline`，其余（如 `Binary files … differ`）记为 `Meta`；绝不丢字 |
| `GitPageComponents`（shared） | 统一视图从「整块 `HarnessText(file.body)`」改为 `GitUnifiedDiff(body)`：用 `gitUnifiedDiffAnnotatedString` 逐行着色（新增 `DiffAddedBackground`、删除 `DiffRemovedBackground`、`@@` 头 `BorderSubtle` 底 + `TextMuted` 字、`\ No newline`/`Meta` 只置灰），节点/`testTag`/滚动语义保持不变 |
| `gitUnifiedDiffAnnotatedString`（shared） | 纯构建：逐行 append，`AnnotatedString.text` 与输入 body 逐字符相等（标记与换行都保留），因此可选中文本不变 |

### 3.1 未实现（明确不主张）

- 原版独立的、不可选中的行号 gutter 列：本仓把 body 渲染为**单个可选中文本块**，不新增行号列；分隔行也没有 `data-expand-up/down` 展开按钮与本仓之外的分隔面配色。
- 语法高亮 token、sticky header 吸附、行内评论（`enableComments`）、分栏与统一共用的精确列宽。
- 原版同尺寸像素、原生窗口；本批只有生产解析器/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/unified-diff-lines-2026-10-11/entry-ax1/review.md`。各批证据独立，不累加为总体覆盖率。

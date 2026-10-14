# BG：分栏 Diff 两侧行号 Gutter（不可选中 + 变更侧同色）（2026-10-12）

## §1 目标与依据

目标：补齐同一份 Diff 契约里剩下的**分栏视图两侧行号列**——左 pane 印旧文件行号、右 pane 印新文件行号，两侧行号都**不参与文本选中**（复制 pane 正文不带行号），行号单元格**与所属行同一 `Row`**（长行折行时行号停在该行首行），变更侧的行号单元格与 pane 正文一起着上本仓既有底色。

ASAR 只读依据（`logs/asar-evidence.txt`，来源 `/tmp/asar-new/webview/assets`）：

- `app-initial-6350d04672d0.js`：分栏每个 pane（`[data-deletions]`/`[data-additions]`）只有 `[gutter, content]` 两个子元素，并断言 `gutter.children.length === content.children.length`（H01）——**每行一个 gutter 单元格**；`pre` 上写 `data-diff-type: 'split'`（H02）。
- `codex-diff-css-a6773575cd64.js`：注释原文 `Pierre duplicates split separators into each gutter and content cell.`，分栏 hunk 分隔行在两侧 gutter 与 content 各有规则（H03）；全文件只有一处 `--diffs-column-number-width`，两 pane 共用同一列宽变量（H04）；变更行的底色分别落在正文单元格（`--diffs-bg-addition/deletion`）与行号单元格（`--codex-diffs-*-number`）（H05）。

## §2 写入集合

生产（3 个文件）：

- `apps/shared/src/im/hikaru/harness/client/git/GitDiffLayout.kt`：`GitDiffSplitRow` 新增 `oldNumber`/`newNumber`；`splitDiffBody` 按 `@@` 头重置并推进两个计数器；`HUNK_HEADER` 由 `GitDiffLines` 的私有正则提为 `internal` 供两个解析器共用。
- `apps/shared/src/im/hikaru/harness/client/git/GitDiffLines.kt`：新增 `gitDiffSplitGutterNumber` / `gitDiffSplitGutterWidth` / `gitDiffSplitGutterText` / `gitDiffSplitGutterAnnotatedString` / `gitDiffSplitPaneText` 与共享的 `gitDiffSplitSideBackground`。
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`：`GitDiffSplitView` 改为逐行 `Row { 左行号 ; SelectionContainer{左正文} ; 右行号 ; SelectionContainer{右正文} }`，hunk 头各自包 `SelectionContainer`；删除原先包住整块分栏正文的外层 `SelectionContainer`。

测试（3 个文件）：

- `apps/shared/test/im/hikaru/harness/client/git/GitDiffLayoutTest.kt`：4 个既有期望补上旧/新行号，新增 1 个「每个 hunk 用自身头重置两个计数器」。
- `apps/shared/test/im/hikaru/harness/client/git/GitDiffLinesTest.kt`：新增 5 个（左右行号与列宽、被补空的一侧留同宽空白缓冲、变更侧着色与上下文不着色、单侧变更只着该侧、pane 正文着色且字符不变）。
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffViewInteractionTest.kt`：分栏用例新增 `git-diff-split-left-number`/`git-diff-split-right-number` 计数与逐格文本断言。

文档（新增）：`docs/desktop-ui/reverse-engineering/split-diff-gutter-contract-2026-10-12.md`；本目录 `review.md`、`files.sha256`、`logs/`。

截图产物：`docs/desktop-ui/captures/git-worktrees/git-*-diff-split*` 与其整页图由 `GitWorktreesScreenshotTest` 按新渲染重生成。

## §3 实现

- 行号来源：`splitDiffBody` 从 `@@ -oldStart +newStart @@` 取两个起点，删除行推进旧号、新增行推进新号、上下文行同时推进两者；配对的 Change 行左取旧号、右取新号，单侧行被补空的一侧为 null（对应 H06 的 `data-gutter-buffer`）。
- 两 pane 共用一个列宽（`gitDiffSplitGutterWidth` 取两侧最大位数），对应 H04 的单一 `--diffs-column-number-width`。
- 单元格填充用非断行空格（U+00A0，同 BF），空白缓冲与数字单元格等宽，`gitDiffSplitGutterText`（纯文本）与 `gitDiffSplitGutterAnnotatedString`（带底色）文本一致。
- 不可选中：两个行号 `Text` 都是所在 `Row` 里 `SelectionContainer` 的兄弟节点，复制 pane 正文不含行号；行号与正文同属一个 `Row`，折行时行号随该行首行（对应 H01 的每行一对 gutter/content）。
- 变更侧着色：`gitDiffSplitSideBackground` 一处判定（左=Deletion/Change 用删除色、右=Addition/Change 用新增色），行号单元格与 pane 正文（`gitDiffSplitPaneText`）共用同一判定；pane 正文逐字符保留，语义串不变（`git-diff-split-left/right` 仍是 `old`/`new`）。
- 与统一视图一致：统一视图的着色是「行号单元格 + 正文文本范围」（BF），本批分栏按同一方式着色，不铺满整条 pane 宽度的色带。

## §4 问题

- 分栏 pane 正文首次着色后，原先「只有行号变色、正文仍是白底」的不一致消失；这是本批中途实测像素（`logs/pixel-check.txt`）发现并补齐的：先只有 `right-gutter=511` 像素着色，补 `gitDiffSplitPaneText` 后 `right-pane=43730`、`right-gutter=511`，边界 x≈712 连续。
- 选区粒度变化：分栏正文原来由外层一个 `SelectionContainer` 包住整块，现改为每行每侧各自包一个（行号必须在选区之外）。原版每行也是独立 gutter/content 节点，但**跨行拖选**行为本批未逐像素核对。
- 本机 `screencapture` 仍不可用（无原生窗口证据）。

## §5 验收

| 套件 | 结果 | 日志 |
| --- | --- | --- |
| `shared` 定向 `GitDiffLayoutTest` + `GitDiffLinesTest` | 21 found / 21 passed / 0 failed（新增 6），exit 0 | `logs/shared-targeted.log` |
| `shared` 全模块 | 552 found / 0 skipped / 552 passed / 0 failed（上批 546，+6），exit 0 | `logs/shared-full.log` |
| `jvm-app` 定向 `GitDiffViewInteractionTest` + `GitDiffRichPreviewInteractionTest` | 6 found / 6 passed / 0 failed，exit 0 | `logs/jvm-app-targeted.log` |
| `jvm-app` 定向 `GitWorktreesScreenshotTest` | 6 found / 6 passed / 0 failed，exit 0（重生成 git-worktrees 截图） | `logs/jvm-app-screenshot.log` |
| `jvm-app` 全量 | 692 found / 5 skipped / 687 started / 687 passed / 0 failed，exit 0（本批门槛以此为准；本批未命中历史 PTY 抖动） | `logs/jvm-app-full-r1.log` |

像素核验（`logs/pixel-check.txt`，`git-bounded-large-diff-light-1440x900-diff-split.png`，1440×900，单侧新增文件）：可见 Diff 视口内新增色 `(229,246,236)` 的像素分布为 左行号 0 / 左正文 0 / 右行号 511 / 右正文 43730；在 y=712 的 x 扫描里色带从 x=712（行号格）连续进入 x≥752（正文），中间无白缝。行级几何（`logs/split-geometry.txt`）：`git-diff-split-left-number`=370..407（38px）、`git-diff-split-left`=408..711、`git-diff-split-right-number`=712..749（38px）、`git-diff-split-right`=750..1053，两侧 pane 正文等宽。

冻结夹具还原：全量运行会重写未跟踪目录 `docs/desktop-ui/captures/scheduled-execution/`（与本批无关），按先例从批次前字节还原为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`，`diff -r` 一致。

## §6 未验证边界

- 原版 `--codex-diffs-*-number`（`color-mix` 合成的行号底色/字色）与 `--diffs-column-number-width` 精确列宽/`padding-inline` 内缩未复刻；本批行号字色沿用 `TextMuted`，列宽由最大位数 + 固定间隙推出，底色沿用正文同色。
- 分栏 hunk 分隔行的展开按钮（`data-expand-up/down`）、分隔行在两 pane 的圆角/内缩细节未做。
- 上下文行旧号/新号的确切取用在原版未逐像素比对；本批左旧号、右新号。
- 跨行拖选的选区粒度与原版差异未核对（见 §4）。
- 无原生窗口与同尺寸原版像素证据。各批证据独立，不累加为总体覆盖率；整体目标 active。

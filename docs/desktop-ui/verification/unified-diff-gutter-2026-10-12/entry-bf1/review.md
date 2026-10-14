# BF：统一 Diff 行号 Gutter（不可选中 + 逐行底色）（2026-10-12）

## §1 目标与依据

目标：给统一 Diff 的每个文件正文补上与 ASAR 契约一致的**行号列**——该列**不参与文本选中**（复制正文不带行号），且细胞底色**与正文同一行背景**（新增/删除/`@@` 分隔跨 gutter 与正文连成一条色带）；同时保持补丁正文逐字符不变、仍可选中。

ASAR 只读依据（`logs/asar-evidence.txt`，来源 `/tmp/asar-new/webview/assets`）：

- `app-initial-6350d04672d0.js`：gutter 单元格 `div[data-line-type][data-column-number][data-line-index]` 内含 `span[data-line-number-content]`；无行号处用 `div[data-gutter-buffer][data-buffer-size]` 占位。
- `codex-diff-css-a6773575cd64.js`：统一视图 hunk 分隔行网格为「行号列 + 内容列」（`grid-template-columns: var(--diffs-column-number-width) minmax(0, 1fr)`）；`[data-column-number]`/`[data-gutter-buffer]` 参与行级底色合成（`--codex-diffs-addition-number`/`--codex-diffs-deletion-number`）。

## §2 写入集合

生产（2 个文件）：

- `apps/shared/src/im/hikaru/harness/client/git/GitDiffLines.kt`：新增 `gitDiffGutterNumber(line)`（新增/上下文取新行号、删除取旧行号、hunk 头/`\ No newline`/meta 为空白缓冲）、`gitDiffGutterText(lines)`（纯文本投影）与 `gitDiffGutterAnnotatedString(lines, added, removed, separator)`（逐行底色）；单元格用非断行空格（U+00A0）填充列宽与间隙。
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`：`GitUnifiedDiff` 改为 `Row { 行号 Text（SelectionContainer 之外，带逐行底色） ; SelectionContainer { 补丁 Text } }`；`GitDiffView` 去掉视口级 `SelectionContainer`，下移到文件卡头部列/富文本预览/回退提示/分栏正文。

测试（2 个文件）：

- `apps/shared/test/im/hikaru/harness/client/git/GitDiffLinesTest.kt`：新增 4 个纯函数用例（逐行行号 + 缓冲、宽度补齐、映射、逐行底色与纯文本一致）。
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffViewInteractionTest.kt`：多文件用例新增断言——两文件各一个 `git-diff-gutter`，首个 gutter 文本含行号。

文档（新增）：`docs/desktop-ui/reverse-engineering/unified-diff-gutter-contract-2026-10-12.md`；本目录 `review.md`、`files.sha256`、`logs/`。

截图产物：`docs/desktop-ui/captures/git-worktrees/` 中的 `git-*-diff*` 与相关整页图由 `GitWorktreesScreenshotTest` 按新渲染重生成。

## §3 实现

- 行号来源：`GitDiffLine.oldNumber/newNumber`（AX 批已解析），本批仅投影为单列行号，不发明内容。
- 不可选中：视口级 `SelectionContainer` 下移到各类可选文本；行号 `Text` 是补丁 `Text` 的兄弟节点且不在任何 `SelectionContainer` 内，因此复制范围不含行号。
- 逐行底色：行号用一个 `AnnotatedString`，每行的单元格（编号 + 间隙）加 `SpanStyle(background=…)`——新增/删除用正文同色，`@@` 头用分隔面色，上下文/`\ No newline`/meta 不着色。因为只有一个 `Text`，行数与该样式与正文逐行对齐。
- 连续色带：单元格间与右侧间隙用非断行空格（U+00A0）。普通空格会被当作行尾空白裁掉，导致 gutter 色带止于最后一位数字、与正文之间留白缝；实测像素（见 §5）确认换成 U+00A0 后色带连续到正文，且等宽字距下仍右对齐。
- 正文不变：`gitUnifiedDiffAnnotatedString` 未改，补丁 `Text` 仍逐字符等于原始 body（含 `+`/`-`/空格标记），`git-diff-file-patch` 标签与横向滚动语义不变。

## §4 问题

- 首次全量 `jvm-app`（r1）唯一失败为历史真实进程抖动类 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（`awaitGrid` 30s 门超时，`PtyTerminalFontInteractionTest.kt:362`），与本批 Diff 改动无关；该类隔离重跑 3/3 通过（`logs/pty-font-isolated.log`）。
- 普通空格会被行尾裁剪、不能用于铺满 gutter 色带（见 §3）；这是本批从「单色行号列」推进到「逐行底色」时实测发现并修正的问题。
- 本机 `screencapture` 仍不可用（无原生窗口证据）。

## §5 验收

| 套件 | 结果 | 日志 |
| --- | --- | --- |
| `shared` 定向 `GitDiffLinesTest` | 11 found / 11 passed / 0 failed（新增 4），exit 0 | `logs/shared-targeted.log` |
| `shared` 全模块 | 546 found / 0 skipped / 546 passed / 0 failed（上批 542，+4），exit 0 | `logs/shared-full.log` |
| `jvm-app` 定向 `GitDiffViewInteractionTest` + `GitDiffRichPreviewInteractionTest` | 6 found / 6 passed / 0 failed，exit 0 | `logs/jvm-app-targeted.log` |
| `jvm-app` 定向 `GitWorktreesScreenshotTest` | 6 found / 6 passed / 0 failed，exit 0（重生成 git-worktrees 截图） | `logs/jvm-app-screenshot.log` |
| `jvm-app` 全量 r1 | 692 found / 5 skipped / 687 started / 686 passed / **1 failed（历史 PTY 抖动）**，exit 1 | `logs/jvm-app-full-r1.log` |
| `PtyTerminalFontInteractionTest` 隔离重跑 | 3 found / 3 passed / 0 failed，exit 0 | `logs/pty-font-isolated.log` |
| `jvm-app` 全量 r2（单色行号列版本） | 692 found / 5 skipped / 687 started / 687 passed / 0 failed，exit 0 | `logs/jvm-app-full-r2.log` |
| `jvm-app` 全量 r3（**最终逐行底色版本**） | 692 found / 5 skipped / 687 started / 687 passed / 0 failed，exit 0（本批门槛以此为准） | `logs/jvm-app-full-r3.log` |

像素核验（`git-bounded-large-diff-light-1440x900-diff-after-independent-scroll.png`）：一次性像素图显示行号列与正文同色，色带从 x≈368 连续到右侧边界，无中间白缝；编号在等宽字距下右对齐。

冻结夹具还原：全量运行会重写未跟踪目录 `docs/desktop-ui/captures/scheduled-execution/`（与本批无关），按先例从批次前字节还原为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`，`diff -r` 一致。

## §6 未验证边界

- 原版 `--diffs-column-number-width` 精确列宽与 `--diffs-gap-inline` 内缩未逐像素复刻；本批列宽由最大位数 + 固定间隙推出（不主张与原版像素一致）。
- 原版 `color-mix` 合成出的行号字面色值未复刻；本批行号沿用正文 `TextMuted`，底色沿用 `DiffAddedBackground`/`DiffRemovedBackground`/`BorderSubtle`。
- hunk 分隔行的 `data-expand-up/down` 展开按钮、分栏视图两侧行号列未做。
- 上下文行显示旧号还是新号未逐像素比对（本批按统一 Diff 惯例取新号）。
- 无原生窗口与同尺寸原版像素证据。各批证据独立，不累加为总体覆盖率；整体目标 active。

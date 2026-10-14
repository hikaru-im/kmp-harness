# 统一 Diff 逐行着色验收 AX

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-ax1`。

## 1. 本批目标与依据

AR/AQ/AW 补齐了 diff 工具栏的布局选择器、逐文件卡片与富文本预览，但统一视图一直把整个补丁正文当成一块无着色 `HarnessText`。本批按原版逐行规则把新增行、删除行与 `@@` 分隔头分别着色，不新增协议、不伪造成功。完整边界见 `../../../reverse-engineering/unified-diff-lines-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：L01 `codex-diff-css` 的 `[data-line-type='change-addition'|'change-deletion'] { --diffs-computed-diff-line-bg: var(--diffs-bg-addition|deletion) }`（`data-column-number`/`data-gutter-buffer` 另有数字底色）；L02 `@@` 头是 `[data-separator='line-info']` 分隔行（统一视图 `grid-template-columns: var(--diffs-column-number-width) minmax(0, 1fr)`，分隔面用 `--codex-diffs-separator-surface`）；L03 `data-no-newline` 也参与行级背景；L04 `code-diff` 透传 `hunkSeparators`；L05 zh-CN 既有文案。

## 2. 写入集

生产（2）：

- `apps/shared/src/im/hikaru/harness/client/git/GitDiffLines.kt`（新增：行分类纯函数）
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`（统一视图改用逐行着色）

测试（2）：

- `apps/shared/test/im/hikaru/harness/client/git/GitDiffLinesTest.kt`（新增 7 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffViewInteractionTest.kt`（既有 `multiFileDiff…` 用例追加 2 条正文保留断言）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/unified-diff-lines-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 分类层与原版一致：`parseGitDiffLines(body)` 用 `@@ -a,b +c,d @@` 头重置两侧行号；hunk 内每行按自身首字符分类（`+`→`Added`、`-`→`Removed`、空格→`Context`），剥掉一个标记字符但**绝不丢字**；`\` 开头记 `NoNewline`，其余（如 `Binary files … differ`）记 `Meta`。空 body 返回空列表。
- 渲染层保持节点契约：统一视图仍是一个 `testTag("git-diff-file-patch")` 的单文本块，`horizontalScroll` 语义不变（`GitWorktreesScreenshotTest.GitLargeDiff` 的 `ScrollBy(500f,0f)` 与两侧独立滚动断言继续成立）；新增行背景用 `HarnessTokens.Colors.DiffAddedBackground`、删除行用 `DiffRemovedBackground`、`@@` 头用 `BorderSubtle` 底 + `TextMuted` 字、`\ No newline`/`Meta` 仅置灰。
- 着色不改变文本：`gitUnifiedDiffAnnotatedString` 逐行 append，`AnnotatedString.text` 与输入 body 逐字符相等，因此可选中复制的内容与原来完全一致（新增回归 `annotatedBodyPreservesEveryCharacterInOrder` 断言该等式）。
- 分隔行只做视觉：`@@` 头作为独立行着色，未实现原版 `data-expand-up/down` 展开按钮，也不合成 CSS 里没有的色值。

## 4. 问题

- 与原版的已知差异（已记录在契约 3.1）：原版有独立的、不可选中的行号 gutter 列，本仓把 body 作为单文本块渲染，不新增行号列；`@@` 分隔行没有上下文展开按钮；无语法高亮 token。
- 色值取自本仓既有 `HarnessTokens`（`DiffAddedBackground`/`DiffRemovedBackground`/`BorderSubtle`），非原版 `--diffs-addition-base`/`--diffs-deletion-base` 的字面量（原版是 `color-mix` 表达式，无法观察到确定的字面值）。
- 全量 `jvm-app` 本批命中 3 个与本批无关的真实进程时序抖动（见 §5.2），已隔离重跑分类。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **513 found / 0 skipped / 513 started / 513 passed / 0 failed**，exit 0 | `logs/shared.log` |
| shared Git 定向 | `im.hikaru.harness.client.git.*` | **94 found / 0 skipped / 94 started / 94 passed / 0 failed**，exit 0 | `logs/shared-git-targeted.log` |
| jvm-app 定向 | `GitDiffViewInteractionTest` + `GitDiffRichPreviewInteractionTest` + `GitPageInteractionTest` | **12 found / 0 skipped / 12 started / 12 passed / 0 failed**，exit 0 | `logs/jvm-app-targeted.log` |
| 全量 jvm-app（本批门槛） | 全部 | **677 found / 5 skipped / 672 started / 669 passed / 3 failed**，exit 1（3 个超时抖动，见 §5.2） | `logs/full-jvm-app.txt` |
| 抖动重跑 | `GitCommitInteractionTest` + `git.DesktopGitCommitTest` + `PtyTerminalFontInteractionTest` | **16 found / 0 skipped / 16 started / 16 passed / 0 failed**，exit 0 | `logs/flake-rerun.log` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 纯分类（`GitDiffLinesTest`，+7）：`classifiesHunkLinesAndTracksBothLineNumbers`（hunk/上下文/删/增/增/上下文 六行 + 两侧行号）；`eachHunkRestartsNumberingFromItsHeader`（多 hunk 各自重置行号）；`noNewlineMarkerAndBinaryMetaStayUnnumbered`（`\ No newline` 与 `Binary files … differ` 均无行号）；`contentThatStartsWithItsOwnMarkerIsClassifiedByTheFirstCharacter`（`++plus` 按首字符判为新增，正文 `+plus`）；`emptyBodyYieldsNoLines`；`annotatedBodyPreservesEveryCharacterInOrder`（`AnnotatedString.text == body`）；`annotatedBodyTintsAddedAndRemovedSpans`（新增/删除 span 的背景色分别等于传入的 `DiffAddedBackground`/`DiffRemovedBackground`，且偏移落在正确行）。
- 页面（`GitDiffViewInteractionTest`，既有用例 +2 断言）：着色后 `git-diff-file-patch` 正文仍逐字包含 `@@ -1,2 +1,2 @@` 与 `+new`，证明着色未吞字或改序；该用例同时保留既有的文件级折叠/计数断言。

### 5.2 全量失败归因与抖动判定

全量 `jvm-app` 为 **677 found / 5 skipped / 672 started / 669 passed / 3 failed**（exit 1），3 个失败全部是真实进程/PTY 的既有时序抖动，与本批（只改 diff 解析与统一视图着色）无关：

1. `GitCommitInteractionTest.includeUnstagedCheckboxStagesWorktreeAndUntrackedThenCommitsThem`（真实 worktree 暂存/提交 15s 超时，`ComposeTimeoutException`，栈指向 `GitCommitInteractionTest.kt:94`）。
2. `git.DesktopGitCommitTest.includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified`（真实 git 15s 超时，栈指向 `DesktopGitCommitTest.kt:53`）。
3. `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（AE/AF/AM/AO/AQ/AT 记录的 PTY 就绪超时）。

隔离重跑这三个类为 **16/16 全绿**（exit 0，`logs/flake-rerun.log`），与 AT 批次记录的同一组 `GitCommitInteractionTest + git.DesktopGitCommitTest + PtyTerminalFontInteractionTest` 抖动一致。因此本批不主张全量 0 失败，但改动范围内的套件（shared 全模块、shared Git 定向、jvm-app 定向）均 0 失败。

### 5.3 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它（跑后为 `4f387960367fa746579d440583d0e5e2d54e802e`），随后按 AE/AU/AV/AW 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：独立行号 gutter 列、`@@` 分隔行的上下文展开按钮、语法高亮 token、sticky header 吸附、行内评论、分栏与统一共用的精确列宽；色值为本仓既有 token 而非原版 `color-mix` 字面值。
- 各批证据独立，整体目标 active。

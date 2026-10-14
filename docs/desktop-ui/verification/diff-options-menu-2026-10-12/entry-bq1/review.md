# BQ：Diff「变更选项」菜单（展开/折叠全部 + 自动换行）（2026-10-12）

## §1 目标与依据

目标：补齐 Diff 头部同一份契约里剩下的两个可观察控件——**展开全部差异 / 折叠全部差异**（一次开合所有文件卡片，标签描述点击后的动作）与**自动换行**（长行折行而不是横向滚动），二者都放进原版那个「变更选项」菜单。

ASAR 只读依据（`logs/asar-evidence.txt`，来源 `/tmp/asar-new/webview/assets`，本批未抓包）：

- E01：`pull-request-code-review-be17c7c34414.js` 的本地 Diff 头部菜单（`aria-label = codex.review.header.moreOptions`）依次是 Refresh、**Word wrap 复选项**（`CheckboxItem{checked: i.wrap, closeOnSelect: true, onCheckedChange: i.onToggleWrap}`）、Diff 模式项、**展开/折叠全部项**（`onSelect: e => { e.preventDefault(); i.onToggleExpanded(); ne() }`）、`Separator`。
- E02：展开/折叠项的**文字由同一个 `expanded` 状态决定**——`i.expanded ? collapse（折叠全部差异）: expand（展开全部差异）`，图标随之切换（`LeftIcon: i.expanded ? Be : ur`）。标签描述的是点击后的动作，与实际开合同源。
- E03：同一头部另有独立按钮：换行按钮 `Me({aria-label: N, aria-pressed: i.wrap, variant: i.wrap ? 'selected' : 'default', onClick: i.onToggleWrap})`（`N` = `codex.review.wrap.label`），展开/折叠按钮 `Ar({Icon: i.expanded ? Be : ur, label: I, onClick: i.onToggleExpanded})`。即换行开关本身带选中态。
- E04：菜单后半段另有一组复选项（`hideImports` / `hideWhitespace` / `loadFullFiles` / `richPreview` / `wordDiffs`），本批不在范围。
- E05：`inline-file-diff-review-76525498732c.js` 的内联文件 Diff 操作行也有换行开关 `A({onToggleWrap, wrapLines})`，提示文字随状态在 `wrap.disable`(禁用自动换行) / `wrap.enable`(启用自动换行) 间切换——原版换行是**一处状态、两处入口**。
- E06：`zh-CN-b05cfd55c534.js` 字面——展开全部差异 / 折叠全部差异 / 自动换行 / 启用自动换行 / 禁用自动换行 / 词级差异 / 隐藏空白字符 / 渲染预览 / 「“变更”选项」。

## §2 写入集合

生产（2 个文件）：

- `apps/shared/src/im/hikaru/harness/client/git/GitDiffLines.kt`：`gitDiffGutterColumnWidth` 提为 `internal gitDiffGutterWidth(lines)`（逐行渲染共用同一列宽）；新增 `gitDiffGutterLineAnnotatedString(line, width, …)`（单个行号单元格 + 逐行底色），`gitDiffGutterAnnotatedString` 改为复用同一底色判定 `gitDiffGutterCellBackground`。
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`：`GitDiffView` 新增 `wrap` 状态并把每个卡片的开合**上提**为 `expandedHeaders: Set<String>`（`allExpanded` 直接驱动菜单标签）；`GitDiffFileCard` 由内部 `remember(initiallyExpanded)` 改为受控 `expanded` + `onExpandedChange`；新增 `GitDiffOptionsMenu`（按钮 tag `git-diff-options`，popup tag `git-diff-options-menu`，项 tag `menu:wrap` / `menu:expand`）；`GitUnifiedDiff(body, wrap)` 在换行开时改走逐行 `GitWrappedUnifiedDiff`；抽出 `gitDiffLineText` / `gitDiffLineSpan` / `gitDiffUnifiedLineAnnotatedString` 供整块与逐行两条渲染共用。

测试（3 个文件）：

- `apps/shared/test/im/hikaru/harness/client/git/GitDiffLinesTest.kt`：新增 5 个（行文本带标记、逐行正文拼回整块正文（含 span 偏移）、逐行列号拼回整块 gutter、共用列宽与 NBSP 填充、逐格底色）。
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffViewInteractionTest.kt`：新增 2 个（选项菜单整体折叠再整体展开且标签随之切换；换行开关切到逐行渲染、去掉横向滚动轴、再切回）。
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitWorktreesScreenshotTest.kt`：`Case.GitLargeDiff` 在原有滚动断言之后新增换行步骤与截图 `diff-wrapped`，并断言换行正文没有横向滚动轴、`git-diff-options` 在 600px 视口内不越界。

文档：`docs/desktop-ui/reverse-engineering/diff-options-menu-contract-2026-10-12.md`；本目录 `review.md`、`files.sha256`、`logs/`。

截图产物：`docs/desktop-ui/captures/git-worktrees/git-bounded-large-diff-*-diff-wrapped.{png,json}`（新，8 个）与同目录其它由截图测试按既有机制重生成的图。

## §3 实现

- **单一 `expanded` 语义（对应 E02）**：开合集合上提到 `GitDiffView`，`expandedHeaders` 初始化为「按本仓既有阈值默认打开、且不是删除」的文件头集合；`allExpanded = 所有文件头都在集合里`。菜单标签由 `allExpanded` 决定，点击时整体置空或整体填满，因此**标签与实际卡片状态不可能不一致**（这也是把状态上提、而不是各卡片自己 `remember` 的原因）。
- **换行（对应 E01/E03/E05）**：`wrap` 是视图级布尔；菜单项 `selected = wrap`（对应原版 `checked`/`aria-pressed`）。开启后统一视图改走 `GitWrappedUnifiedDiff`——每行一个 `Row{gutter(不可选中); SelectionContainer{正文}}`，与 BG 批分栏视图同构，因此折行时行号仍停在该行首行，且不再有整块横向滚动。
- **两条渲染不漂移**：整块渲染（`gitUnifiedDiffAnnotatedString`）与逐行渲染（`gitDiffUnifiedLineAnnotatedString`）共用 `gitDiffLineText`（`+`/`-`/空格标记）与 `gitDiffLineSpan`（新增/删除底色、`@@` 分隔底色、meta 字色）；`GitDiffLinesTest` 用「逐行拼回整块」的等式（正文文本 + 平移后的 span）把这条不变式钉住。行号侧同理：`gitDiffGutterLineAnnotatedString` 与 `gitDiffGutterAnnotatedString` 共用列宽与底色判定。
- **列宽共用**：`gitDiffGutterWidth` 取最大位数，逐行单元格按同一宽度用 U+00A0 补齐（沿用 BF/BG 的非断行空格取法，避免行尾裁剪）。
- **工具条布局**：原版在工具条上另有换行与展开/折叠两个独立按钮（E03），本仓把两者折进单个紧凑的「变更选项」按钮，使工具条在 600px 视口仍放得下（该约束由截图用例断言，见 §5）。菜单用既有 `HarnessMenu`（`closeOnSelect` 行为与既有页面一致）。

## §4 问题

- 首轮实现把展开/折叠做成独立的 `expandAll: Boolean?` 开关（与卡片状态无关），测试立刻暴露标签与实际不一致（文件默认已展开却显示「展开全部差异」）。改为把开合集合上提到视图后才自洽；这是一处真实的设计修正，不是测试改期望。
- 首轮把 `Modifier.semantics { testTag = … }` 用在 popup 上，因本文件只 import 了 `platform.testTag` 而编译失败；改用等价的 `Modifier.testTag(…)`。
- 首轮换行测试用 `onNodeWithTag("git-diff-wrap")` 期望单节点，实际两个文件各一个容器（2 节点）；改为 `onAllNodesWithTag(...)[0]`。
- 共享样例中我一度把行号补齐写成普通空格，实际是 U+00A0；已按实现修正期望。
- 本批未触发历史抖动类 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（本次全量直接全绿）。

## §5 验收

命令行：`./kotlin test --build-dir=/tmp/ap-build -p jvm -m <module> [--include-classes=…]`。

- `shared` 定向 `GitDiffLinesTest`+`GitDiffLayoutTest`：**26 found / 26 passed / 0 failed**（`logs/shared-git-diff-targeted.log`；新增 5）。
- `shared` 全模块：**557 found / 557 passed / 0 failed**（`logs/shared-module-full.log`；上批 552，+5）。
- `jvm-app` 定向 `GitDiffViewInteractionTest`+`GitDiffRichPreviewInteractionTest`：**8 found / 8 passed / 0 failed**（`logs/git-diff-interaction-targeted.log`；新增 2）。
- `jvm-app` 定向 `GitPageInteractionTest`+`GitSettingsRouteInteractionTest`：**13 found / 13 passed / 0 failed**（`logs/git-page-settings-route-targeted.log`）。
- `jvm-app` 定向 `GitWorktreesScreenshotTest`：**6 found / 6 passed / 0 failed**（`logs/git-worktrees-screenshot-targeted.log`；生成 `diff-wrapped` 并断言 600px 视口内 `git-diff-options` 不越界、换行正文无横向滚动轴）。
- `jvm-app` 全量：**694 found / 5 skipped / 689 started / 689 passed / 0 failed**，exit code 0（`logs/jvm-app-module-full.log`；上批 692 found，+2）。
- 冻结资产还原：全量 `jvm-app` 会重写 `docs/desktop-ui/captures/scheduled-execution/`（截图用例固有行为）；跑后按先例从批次前字节还原，`diff -rq` 无差异，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5` 与批次前一致。
- `docs/desktop-ui/captures/git-worktrees/` 全部由 `GitWorktreesScreenshotTest` 重生成（含新增 8 个 `diff-wrapped`）。

## §6 未验证边界

- 词级差异（`wordDiffs`）与隐藏空白字符（`whitespace.hide`）未实现（需要词级比对与按空白过滤），菜单里只有换行与展开/折叠两项。
- 原版工具条上那两个独立按钮（E03）、菜单里的 Refresh 与 Diff 模式项未按原版复刻：本仓分别是菜单项与工具条上的「统一/分栏/自动」按钮。
- 换行只作用于统一视图；分栏视图本身按 pane 宽度折行，未加开关。换行模式下正文选区由「整块」变为每行一个 `SelectionContainer`（与 BG 批分栏同一取舍），跨行拖选与原版未比对。
- 页面顶部那句 Harness 提示（「可在 Diff 内分别上下与左右滚动；文本可选中复制。」）是静态文案，换行开启后仍照默认模式撰写、未随状态改写；这是本批已知的一处文案不一致（改它需要重跑全量门槛，故留在本批）。
- 原版 `i.expanded` 在宿主里如何由各文件开合推导、折行后的精确缩进/列宽、`white-space` 相关 CSS 未逐条抄录比对；本批按「集合为空即全折叠」推导，未复刻隐藏按钮/容器查询断点（`@container_review-header_(min-width:625px)`）。
- 无原生窗口与同尺寸原版像素证据（本机 `screencapture` 仍返回 `could not create image from display`）；本批视觉证据来自离屏自渲染截图（`diff-wrapped`）与语义断言，非像素比对。

各批证据独立，不累加为总体覆盖率。

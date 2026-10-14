# Diff「变更选项」菜单：展开/折叠全部与自动换行（2026-10-12）

## 1. 本批依据（ASAR 只读）

本批只读复核当前安装包 webview 固定资产，未抓包；抄录见
`verification/diff-options-menu-2026-10-12/entry-bq1/logs/asar-evidence.txt`。
BG 批已完成统一/分栏两个视图的行号列；本批处理同一契约里 Diff 头部那组「变更选项」控件。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `webview/assets/pull-request-code-review-be17c7c34414.js` | 本地 Diff 头部有一个「变更选项」菜单（`aria-label = codex.review.header.moreOptions`）。菜单项依次为：Refresh（`showRefreshGitQueries` 为真才渲染）、**Word wrap 复选项**（`CheckboxItem {checked: i.wrap, closeOnSelect: true, onCheckedChange: i.onToggleWrap}`）、Diff 模式项（`onSelectDiffMode`，标签 `kr(y, i.diffMode)`）、**展开/折叠全部项**（`onSelect: e => { e.preventDefault(); i.onToggleExpanded(); ne() }`，`children: I`）、`Separator` |
| E02 | 同上 | 展开/折叠项的文字由同一个 `expanded` 状态决定：`F = i.expanded ? formatMessage(codex.review.expandOrCollapseDiffMenu.collapse) : formatMessage(codex.review.expandOrCollapseDiffMenu.expand)`；图标也随之切换（`LeftIcon: i.expanded ? Be : ur`）。即**标签描述的是点击后的动作**，文本与卡片实际开合状态同源 |
| E03 | 同上 | 同一头部除菜单外还有两个独立按钮：自动换行按钮 `Me({aria-label: N, aria-pressed: i.wrap, variant: i.wrap ? 'selected' : 'default', onClick: i.onToggleWrap})`（`N` 即 `codex.review.wrap.label`），以及展开/折叠全部按钮 `Ar({Icon: i.expanded ? Be : ur, label: I, onClick: i.onToggleExpanded})`。换行按钮带 `aria-pressed`，即按钮本身就是选中态开关 |
| E04 | 同上 | 菜单后半段是另一组复选项（`jr`）：`hideImports`、`hideWhitespace`、`loadFullFiles`、`richPreview`（`codex.review.richPreview.label`）、`wordDiffs`（`codex.review.wordDiffs.label`），各自 `CheckboxItem`+`onCheckedChange`。本批只处理换行与展开/折叠，这组复选框不在本批范围 |
| E05 | `webview/assets/inline-file-diff-review-76525498732c.js` | 另一处（内联文件 Diff 的操作行）也有换行开关 `A({onToggleWrap, wrapLines})`，其提示文字随状态切换：`wrapLines ? codex.review.wrap.disable : codex.review.wrap.enable`。即换行开关在原版有两处入口、同一状态 |
| E06 | `webview/assets/zh-CN-b05cfd55c534.js` | 中文字面：`codex.review.wrap.label`=「自动换行」、`.enable`=「启用自动换行」、`.disable`=「禁用自动换行」、`codex.review.expandOrCollapseDiffMenu.expand`=「展开全部差异」、`.collapse`=「折叠全部差异」、`codex.review.header.moreOptions`=「“变更”选项」、`codex.review.wordDiffs.label`=「词级差异」、`codex.review.whitespace.hide`=「隐藏空白字符」、`codex.review.richPreview.label`=「渲染预览」 |

## 2. 已观察到与未观察到的语义

- **已观察**：换行是一个布尔开关（复选项 `checked: i.wrap`，工具条按钮另有 `aria-pressed: i.wrap`）；展开/折叠全部是一个动作，标签在「展开全部差异 / 折叠全部差异」之间二选一，且由**同一个 `expanded` 状态**驱动；菜单与工具条按钮共享状态；菜单项 `closeOnSelect: true`（点完即关）。
- **未观察到**：`i.expanded` 在宿主里如何由各文件的开合集合推导（只看得到它是单一布尔）；换行后长行的具体折行宽度/缩进行为（CSS 资产里 `white-space` 相关规则未逐条抄录）；`wordDiffs`/`hideWhitespace` 的实际渲染实现（本批不实现）；菜单的键盘/子菜单弹出细节。

本批只实现**可观察的那一层**：一个带选中态的自动换行开关，和一个标签随当前开合状态变化、点击后整体开合的动作；不发明 CSS/资产里没有的颜色或宽度。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitDiffLines`（shared） | 新增 `gitDiffGutterWidth(lines)`（把原先私有的列宽计算提为 `internal`，供逐行渲染共用同一列宽，对应 E02 的单 `expanded`→单一列宽语义）、`gitDiffGutterLineAnnotatedString(line,width,…)`（单个行号单元格 + 逐行底色）；`gitDiffGutterAnnotatedString` 改为复用它，两者输出保持一致 |
| `GitPageComponents`（shared） | `GitDiffView` 新增 `wrap` 状态，并把每个 Diff 卡片的开合状态**上提到视图**（`expandedHeaders: Set<String>`）：`allExpanded = 所有文件头都在集合里`，直接驱动菜单标签；`GitDiffFileCard` 由「自己 `remember` 一个 `initiallyExpanded`」改为受控的 `expanded` + `onExpandedChange`，默认开合仍按本仓既有阈值 |
| `GitPageComponents`（shared） | 新增 `GitDiffOptionsMenu`：单个紧凑触发按钮「变更选项」（tag `git-diff-options`）+ `HarnessMenu`（popup tag `git-diff-options-menu`），两项为「自动换行」（`selected = wrap`）与「展开全部差异 / 折叠全部差异」（标签由 `allExpanded` 决定）。原版该项在菜单里，这里的标签与选中态与 E01/E02/E03 一致 |
| `GitPageComponents`（shared） | 新增 `GitDiffUnifiedLineAnnotatedString(line,…)` 与 `gitDiffLineText(line)`/`gitDiffLineSpan(...)` 提取（整块渲染与逐行渲染共用同一套字色/底色判定，避免两处漂移）；`GitUnifiedDiff(body, wrap)` 在换行开时改走逐行 `GitWrappedUnifiedDiff`（每行一个 `Row{gutter; SelectionContainer{正文}}`，与 `GitDiffSplitView` 同构），此时不再带 `horizontalScroll` |

### 3.1 未实现（明确不主张）

- 「词级差异」（`wordDiffs`）与「隐藏空白字符」（`whitespace.hide`）两个复选项未实现：前者需要词级比对算法，后者需要按空白过滤行内容，本仓均无；菜单里只有换行与展开/折叠两项。
- 原版在工具条上**另外**有两个独立按钮（换行、展开/折叠，见 E03），本仓把它们折进单个「变更选项」菜单，以便在 600px 视口下仍能放下（原版那组按钮在窄容器里还会隐入菜单）。原版菜单里的 Refresh 与 Diff 模式项在本仓分别是页面其它位置的「刷新」与工具条上的「统一/分栏/自动」按钮。
- 换行只作用于**统一视图**；分栏视图本来就是按 pane 宽度折行，未额外加开关。
- 换行模式删掉了「整块横向滚动」这一层：原版折行时行号列仍与内容同列；本仓用逐行 `Row` 达成同样效果，代价是正文选区由「一整块」变为每行一个 `SelectionContainer`（与 BG 批分栏视图同一取舍）。
- 页面顶部 Harness 提示文案（「可在 Diff 内分别上下与左右滚动；文本可选中复制。」）未随换行状态改写，换行开启时该句对横向滚动的描述不再适用（已知文案不一致，本批保留）。
- 无原生窗口与同尺寸原版像素证据（本机 `screencapture` 仍返回 `could not create image from display`）。

## 4. 验收

见 `verification/diff-options-menu-2026-10-12/entry-bq1/review.md`。各批证据独立，不累加为总体覆盖率。

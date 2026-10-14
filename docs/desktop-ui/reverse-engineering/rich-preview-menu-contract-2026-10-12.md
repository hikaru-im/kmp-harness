# Diff「变更选项」菜单新增「渲染预览」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动原包、未联网。抄录见
`../verification/rich-preview-menu-2026-10-12/entry-ci1/logs/asar-rich-preview.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | `"codex.review.richPreview.label":"渲染预览"`（与 `codex.review.*` 一组）；另有 `"codex.diffView.richPreviewToggle":"切换富文本预览"`、`"codex.diffView.richPreviewLoading":"正在加载预览…"`（与 `codex.diffView.*` 一组）。 |
| E02 | `pull-request-code-review-be17c7c34414.js` | 选项菜单 `jr(...)` 里有一个 **`be.CheckboxItem`**：`{checked: richPreviewEnabled, closeOnSelect: !0, onCheckedChange: onToggleRichPreview, leftIcon: <icon>, children: <FormattedMessage id="codex.review.richPreview.label" defaultMessage="Rich preview" description="Toggle for rendered file previews in the diff view"/>}`，整项由 `showRichPreview` 门控（为假时渲染 `null`）。 |
| E03 | `editor-diff-page-86fa0f0062f3.js` | diff 页工具栏另有一个控件，其 tooltip 为 `codex.diffView.richPreviewToggle`（defaultMessage `Toggle rich preview`，description `Tooltip to toggle rich previews in the diff view`）。 |

**结论**：原版**同时**存在两个入口——PR review 的「变更选项」菜单勾选项（E02）和 editor diff 页工具栏开关（E03）。本仓既有工具栏按钮（`GitDiffRichPreviewToggle`）对应 E03，本批补 E02。

## 2. 已观察到与未观察到的语义

- **已观察**：菜单里有一个勾选项控制「rendered file previews」，文案「渲染预览」（英文 `Rich preview`）；勾选状态绑 `richPreviewEnabled`，切换回调 `onToggleRichPreview`；可见性由 `showRichPreview` 决定；该项带 `leftIcon` 且 `closeOnSelect`。
- **未观察**：`showRichPreview` 的具体判定条件（何时为假）；`leftIcon` 的图形；菜单项与工具栏开关是否共享同一持久化存储。
- **未观察**：勾选后具体渲染哪些类型（图片/SVG/PDF 的分支在 `code-diff`/`app-shared` 中另有实现，本仓未接）。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt` | `GitDiffOptionsMenu` 新增参数 `richPreview: Boolean`、`onToggleRichPreview: (Boolean) -> Unit`；entries 在 `load-full-files` 之后插入一项 `HarnessMenuEntry.Item(key = "rich-preview", text = "渲染预览", selected = richPreview)`；`onSelect` 增 `"rich-preview" -> onToggleRichPreview(!richPreview)`；`GitDiffView` 把既有 `richPreview` 状态与 `{ richPreview = it }` 传给菜单；更新 KDoc。 |

既有工具栏按钮 `GitDiffRichPreviewToggle`（对应 E03）**保留**，与菜单项读写同一个 `richPreview` 组合状态。

## 3.1 Harness 取值（明确记录）

| 维度 | ASAR | 本仓 |
| --- | --- | --- |
| 勾选样式 | `CheckboxItem` + `leftIcon` | 沿用本仓「变更选项」菜单既有勾选写法 `selected = richPreview`（渲染 `✓`），不实现 `leftIcon` |
| 可见性 | `showRichPreview` 门控 | 始终可见（本仓无该门控数据源） |
| 入口数量 | 菜单项 + 工具栏开关两个入口 | 两个入口都保留，共享同一页面状态 |
| 状态持久化 | 未观察到 | 页面组合状态（`remember`），重开复位 |
| 菜单顺序 | `loadFullFiles → richPreview → wordDiffs → …` | 插在 `加载完整文件` 之后（本仓既有顺序已与 ASAR 有出入） |

## 3.2 未实现（明确不主张）

- 菜单项的 `leftIcon` 图标。
- `showRichPreview` 可见性判定。
- 图片 / SVG / PDF 富预览（本仓只有 markdown 富预览）。
- 富预览开关的持久化。
- 原版同尺寸像素与原生窗口证据。

## 4. 验收

见 `../verification/rich-preview-menu-2026-10-12/entry-ci1/review.md`。各批证据独立，不累加为总体覆盖率。

注意：菜单多一项后弹层高度再 +36px，既有三组 `options-menu-*` 截图（24 PNG + 24 JSON）会再次发生**尺寸变化**，属预期；`GitWorktreesScreenshotTest` 已在 `focus()` 截图前调用 `parkGitPointer()`，指针归位不再受弹层高度影响。

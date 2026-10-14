# Diff 逐文件卡片：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

复用既有固定证据快照 `reverse-engineering/git-worktrees-implementation-contract-2026-10-07.evidence.json` 的 `editor-diff-page` 三条，不重新抓包：

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| D01 | `webview/assets/*` diff view 主组件 | 头部标签 `codex.diffView.filesChanged`（`{fileCount, plural, …}`）+ 行数徽标；`D = fileCount w <= Ae && linesAdded T + linesDeleted E <= je`；`O`/`k` 组成 `flex items-center gap-2 text-sm` 计数行 |
| D02 | 同上，逐文件渲染 | 工具栏 `flex items-center justify-between py-2 ps-6 pe-2`；每个文件块 `ke` 传 `hunkSeparators:'line-info'`、`stickyHeader:!0`、`diffViewWrap:!1`、`initialOpen: D && e.metadata.type !== 'deleted'` |
| D03 | 同上，`ke` 包装 | `({initialOpen:n, ...r}) => { const [i,a] = useState(n); return <w {...r} open={i} onOpenChange={a}/> }`，即每个文件块是可折叠的，默认展开态由 `initialOpen` 决定 |

证据快照：archive `546868793` bytes / SHA-256 `22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`（见 evidence `archive` 段；该快照与当前安装包可能不同版本，本文件只声明该快照可观察到的字段）。

## 2. 已观察到的行为与未观察到的语义

- **已观察**：Diff 不是一整块文本，而是「头部计数行 + 逐文件块」；每个文件块自带状态（`metadata.type`，含 `deleted`）与 +/- 行数，可折叠，默认展开态 `initialOpen = D && type !== 'deleted'`（即删除型文件从不自动展开）；计数行左侧是文件数（复数文案）与行数徽标。
- **未观察到**：阈值 `Ae`（文件数上限）与 `je`（增删行数上限）的具体数值；`metadata.type` 的完整取值集合（本批只识别 `deleted` 这一被显式引用的值）；状态徽标的文案/配色；工具栏的统一/分栏/自动布局选择器与 rich preview 开关的交互语义。

因此本批只做**可观察的那一层**：把后端已返回的 patch 切成逐文件块，渲染状态与 +/- 计数、默认展开规则、可折叠交互；不实现布局选择器与 rich preview，也不发明 `Ae`/`je` 的具体数值（用 Harness 自定阈值并显式声明）。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitDiffFiles`（新增，shared） | 纯文本解析：`GitDiffFileStatus`、`GitDiffFile`、`GitDiffDocument`、`parseGitDiff(patch)`。按 `diff --git ` 切块，统计 hunk 内 `+`/`-` 行（排除 `+++`/`---`/`\ No newline`），按 `new file mode`/`deleted file mode`/`rename from`/`copy from` 归类 Added/Deleted/Renamed/Copied/Modified，解 Git 引号转义（含八进制 → UTF-8 字节）的路径；无任何平台依赖，不发明 patch 中不存在的内容 |
| `GitPageComponents` | `GitPatch(patch)` 改为解析后调用新的 `GitDiffView(document)`：新增 `git-diff-summary` 计数行、`git-diff-leading`（补丁开头存在非文件文本时提示）、逐文件卡片 `git-diff-file`（标题 `git-diff-file-title`、明细 `git-diff-file-detail`、正文 `git-diff-file-patch`）；保留 `git-diff-viewport`/`git-diff-vertical` 与 288dp 高度 |

### 3.1 默认展开与折叠

删除型文件永不自动展开（对齐 `initialOpen && type !== 'deleted'`）。其余文件用 Harness 阈值 `files.size <= 10 && added + deleted <= 400` 决定默认展开态，并在代码注释中声明「原版阈值不可观察」。折叠钮整块头部 `clickable(role = Button)`，明细文案在「展开/收起」之间切换，并始终显示 `+N −M`（二进制文件追加说明）。

### 3.2 滚动与选择

正文放在同一 `verticalScroll` 容器内，单文件正文再包一层 `horizontalScroll`，因此可分别上下、左右滚动；整体包在 `SelectionContainer` 内，文本可选中复制（与页面既有说明一致）。用户可见的「文本可选中复制」提示本批开始生效。

### 3.3 未实现（明确不主张）

- 工具栏的统一/分栏/自动布局选择器、rich preview 开关、行内评论（`enableComments`/`comments`）、sticky header 的吸附行为。
- 原版同尺寸像素、原生窗口；本批只有生产解析器/控制器/页面证据与 Harness 自渲染截图。
- 阈值 `Ae`/`je`、`metadata.type` 其余取值、状态徽标视觉。

## 4. 验收

见 `verification/git-diff-file-view-2026-10-11/entry-aq1/review.md`：shared 解析器、生产页面交互、截图回归三层证据，另有全量 `jvm-app` 结果与退出码。各批证据独立，不累加为总体覆盖率。

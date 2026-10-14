# Diff 布局选择器与分栏渲染：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

复用既有固定证据快照 `git-worktrees-implementation-contract-2026-10-07.evidence.json` 的 `editor-diff-page`（D01/D02），不重新抓包：

| 编号 | 观察到的内容 |
| --- | --- |
| D01 | diff 工具栏有一个三选一布局选择器：`options=[{id:'unified',ariaLabel:'Switch to unified diff'},{id:'split',ariaLabel:'Switch to split diff'},{id:'auto',ariaLabel:'Auto',tooltipContent:'Auto: split for additions and removals, inline for narrow panes or one-sided changes'}]`，`selectedId` 来自持久 store（`ge(ye)`/`c.set(ye,e)`），以 `size:'toolbar'` 渲染；同排还有 rich preview 开关（`codex.diffView.richPreviewToggle`） |
| D02 | 工具栏容器 `div.flex.items-center.justify-between.py-2.ps-6.pe-2`：左侧是文件数/行数计数（`A`），右侧是选择器与开关（`X`）；每个文件块按 `viewType:h`（当前选中布局）渲染 |

证据快照：archive `546868793` bytes / SHA-256 `22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`（本文件只声明该快照可观察到的字段；本机安装包已是更晚的版本，SHA-256 `c5b273322a833ce5165429d468021cc6289a11cc68d075b80ae773bd7fb6731f`，未重新抓包）。

## 2. 已观察到与未观察到的语义

- **已观察**：存在 unified/split/auto 三态；auto 的语义由原版 tooltip 明确写出（两侧都有增删且面板够宽才分栏，否则 inline）；布局状态是**页面级**（选中值同时作用于所有文件块）；rich preview 是同排的独立开关。
- **未观察到**：store 的**持久默认值**（`ye` 初值未出现在片段里）；分栏的具体列宽/对齐实现（`hunkSeparators:'line-info'` 的行信息在分栏中的排布）；图标字形（只看到 `ariaLabel`/tooltip 文案）；rich preview 的实际渲染内容。

因此本批只实现**可观察的那一层**：页面级 unified/split/auto 选择器，auto 按原版 tooltip 语义解析，分栏用「旧侧在左、新侧在右、逐行对齐」的渲染。默认保持 unified（原版持久默认不可观察，保留本仓已验收的默认行为）。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitDiffLayout`（新增，shared） | `GitDiffLayout{Unified,Split,Auto}`、`GitDiffSplitRowKind`、`GitDiffSplitRow`、`GitDiffSplitHunk`、纯函数 `splitDiffBody(body)`（把 hunk 正文按删除run/新增run 配对成左右行，上下文行两侧同文，去掉行首标记，忽略 `\ No newline`）与 `effectiveGitDiffLayout(selected,file,narrowPane)`（auto = 宽面板且两侧都有变化才 Split，否则 Unified） |
| `GitPageComponents` | `GitDiffView` 顶部新增工具栏 `git-diff-toolbar`：左侧计数（保留 `git-diff-summary` tag），右侧选择器 `git-diff-layout`（`git-diff-layout-unified/split/auto`）；`BoxWithConstraints` 提供面板宽度（`< 520.dp` 视为窄）；每个文件卡片按 `effectiveGitDiffLayout` 选择渲染：Unified 走既有 `git-diff-file-patch`，Split 走 `git-diff-split`（`git-diff-split-hunk`、`git-diff-split-row`、`git-diff-split-left/right`） |
| `GitDiffFiles` | 计数循环去掉 `+++`/`---` 前缀排除：hunk 内每行都自带 `+`/`-`/空格标记，内容以 `++`/`--` 开头的增删行此前被漏计，现已按自身标记计入（回归测试 `hunkLinesThatThemselvesStartWithDoubleMarkersAreCounted`） |

### 3.1 未实现（明确不主张）

- rich preview 开关与其渲染；分栏的行内评论/吸附头；原版图标字形与持久默认；`hunkSeparators:'line-info'` 在分栏中的精确排布。
- 原版同尺寸像素、原生窗口；本批只有生产解析器/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/git-diff-layout-2026-10-11/entry-ar1/review.md`。各批证据独立，不累加为总体覆盖率。

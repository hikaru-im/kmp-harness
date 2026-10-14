# Diff「隐藏导入内容」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动原包、未联网。抄录见
`../verification/hide-imports-2026-10-12/entry-ce1/logs/asar-hide-imports.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `app-initial-6350d04672d0.js` | 设置键 `rja = 'hideDiffImports'`，默认值 `pja = Fu(rja, !1)`（即默认关闭）。同区 `hideDiffWhitespace`、`wrapCodeDiff.2`、`diffRichPreview` 也是 `!1`，只有 `wordDiffsEnabled.2` 是 `!0`。 |
| E02 | `pull-request-code-review-be17c7c34414.js` | diff 选项菜单里该项是 `be.CheckboxItem`：`children: <Z id="codex.review.imports.hide" defaultMessage="Hide imports" description="Menu item to hide top-level import-only diff changes" />`，`checked`/`onCheckedChange` 绑定 `hideImports`/`onToggleHideImports`。紧邻的「隐藏空白字符」项描述为 `Menu item to hide whitespace-only diff changes`。 |
| E02b | `pull-request-code-review-be17c7c34414.js` | 会话组件把状态透传：`{…, hideWhitespace: i.hideWhitespace, onToggleHideWhitespace: i.onToggleHideWhitespace, hideImports: i.hideImports, onToggleHideImports: i.onToggleHideImports, …}`——状态由 diff 视图外部持有，用布尔回调切换。 |
| E03 | `zh-CN-b05cfd55c534.js` | `"codex.review.imports.hide":"隐藏导入内容"`。 |
| E04 | `thread-side-panel-tab-content-e9ce64182d53.js` | 会话侧栏的 diff 面板把同一对 `hideImports`/`onToggleHideImports` 透传给它渲染的 diff 视图。 |

## 2. 已观察到与未观察到的语义

- **已观察**：diff 视图的「变更选项」菜单里有一个「隐藏导入内容」勾选项，默认**关闭**，语义是
  `hide top-level import-only diff changes`（隐藏**只**由顶层 import 组成的改动）。
- **未观察**：原版用来判定「哪些改动只是 import」的分类器。对整个解包 bundle 逐个 asset 扫描，`hideImports`
  只出现两类用法——设置键 `hideDiffImports`，以及透传给 diff 视图的布尔 prop——**没有**任何 import 正则、行分类
  函数或后端参数；因此「import 行」的判定规则在原版里不可见。
- **未观察**：开关关闭后是否连行号栏一起变、是否保留 hunk 头、是否对不缩进以外的情形也生效。原版也没有留下
  同状态像素值可比对。

因此本批实现**可观察的那一层**：在「变更选项」菜单中新增「隐藏导入内容」开关（默认关），开启时把「顶层 import」
改动行从 diff 中省略；判定规则按 Harness 取值实现并在 §3 写明。

> 附带观察（不在本批范围）：ASAR 里「隐藏空白字符」的描述是 `hide whitespace-only diff changes`，与本仓现有实现
> （把开关变成 host 侧 `--ignore-all-space` 的一次重读，见 `DesktopGitBackend.kt:688`）在「视图过滤 vs 后端重算」
> 上并不一致。本批不改动既有实现，仅记录待后续批次核对。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `client/git/GitDiffImports.kt`（新增，shared） | 顶层 import 判定与过滤的纯函数：`gitDiffIsImportOnlyContent(text)`——把已去 marker 的行文本按「无前导缩进（顶层）」+ 命中 `import …`（Kotlin/Java/Go/TS/JS/Swift…）、`from … import …`（Python）、`#include …`（C/C++）、`use …`（Rust）、`using …`（C#）、`require(…)`（CommonJS）、`@import …`（Sass）之一来判定；`gitDiffHiddenImportLines(lines)` 丢掉 `Added`/`Removed` 且为顶层 import 的行，保留 context、hunk 头与开头 meta；`gitDiffHiddenImportRows(rows)` 对分栏行做同样处理（`Change` 行只有**两侧都是** import 才算 import-only）；`gitDiffHiddenImportHunks(hunks)` 逐 hunk 应用并保留 hunk 头。 |
| `client/git/GitPageComponents.kt`（shared） | `GitDiffView` 新增 `hideImports` 状态（默认 `false`，对应 E01）；`GitDiffOptionsMenu` 在 `wrap`/`expand`/`hide-whitespace`/`word-diffs` 之后新增第 5 项 `HarnessMenuEntry.Item(key = "hide-imports", text = "隐藏导入内容", selected = hideImports)`；`GitDiffFileCard` / `GitUnifiedDiff` / `GitUnifiedDiffScroll` / `GitWrappedUnifiedDiff` / `GitDiffSplitView` 全链路接受 `hideImports`；`gitUnifiedDiffAnnotatedString(...)` 末尾新增带默认值的 `hideImports` 参数，开启时先过滤行再绘制。 |

过滤是**逐行省略**：被隐藏的 import 行不出现在可选中文本里，也不出现在行号栏里；幸存行各自保留原记录的
老/新行号（`GitDiffLine.oldNumber`/`newNumber`），所以行号栏不会因此错位。hunk 头始终保留（与分栏视图一致），
即使某个 hunk 的改动全部被隐藏。

### 3.1 未实现（明确不主张）

- **原版分类器**：import 行判定规则、是否按语言分别处理、是否只处理文件头部区域，均为 Harness 取值（原版未观察到）。
- **控件形态**：原版是 `CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓` 选中写法（该菜单其余四项同样如此）。
- **持久化**：原版把开关写进设置（`hideDiffImports`）；本仓只保存在页面组合状态里，未落盘。
- **文件头之外**：本仓不区分「文件是否处于 import 区」，只有「无缩进的 import 形式」才隐藏。
- **「变更选项」菜单其余项**：`refresh`、`loadFullFiles`（加载完整文件）、复制 `git apply` 命令仍未实现。
- **「隐藏空白字符」描述核对**（见 §2 附带观察）：本批不改。
- **原版同尺寸像素与原生窗口证据**（未做）。

## 4. 验收

见 `../verification/hide-imports-2026-10-12/entry-ce1/review.md`。各批证据独立，不累加为总体覆盖率。

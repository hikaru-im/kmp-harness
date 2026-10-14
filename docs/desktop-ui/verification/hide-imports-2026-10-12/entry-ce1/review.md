# Batch CE：Diff「隐藏导入内容」（2026-10-12）

## §1 本批目标

补齐 diff「变更选项」菜单里 ASAR 明确存在、此前未实现的 `codex.review.imports.hide`（「隐藏导入内容」）。
ASAR 只读证据（`logs/asar-hide-imports.txt`，来源 `/tmp/asar-new/webview/assets/`）：

- 设置键与默认值：`app-initial-6350d04672d0.js` 里 `rja = 'hideDiffImports'`、`pja = Fu(rja, !1)`——默认**关闭**；同区
  `hideDiffWhitespace`/`wrapCodeDiff.2`/`diffRichPreview` 也都是 `!1`，只有 `wordDiffsEnabled.2` 是 `!0`。
- 控件：`pull-request-code-review-be17c7c34414.js` 的 diff 选项菜单用 `be.CheckboxItem` 呈现它，文案
  `codex.review.imports.hide`、`defaultMessage: 'Hide imports'`、`description: 'Menu item to hide top-level import-only diff changes'`，
  `checked`/`onCheckedChange` 绑 `hideImports`/`onToggleHideImports`。
- 中文文案：`zh-CN-b05cfd55c534.js` 的 `"codex.review.imports.hide":"隐藏导入内容"`。
- 同一对 prop 也出现在 `thread-side-panel-tab-content-e9ce64182d53.js`（会话侧栏 diff 面板）。
- **负结果**：逐 asset 扫描整个解包 bundle，`hideImports` 只出现「设置键」与「透传 prop」两类用法，**没有**任何 import
  正则 / 行分类函数 / 后端参数；判定规则在原版不可见。

因此本批实现可观察的那一层（菜单开关 + 顶层 import 改动的隐藏），判定规则按 Harness 取值实现并记录在契约里
（`../../reverse-engineering/hide-imports-contract-2026-10-12.md`）。

## §2 生产改动

（2 个文件，均在 `apps/shared/src/im/hikaru/harness/client/git/`）

| 文件 | 改动 |
| --- | --- |
| `GitDiffImports.kt`（新增） | `gitDiffIsImportOnlyContent(text)`——已去 marker 的行文本，要求**无前导缩进（顶层）**且命中 `import …` / `from … import …` / `#include …` / `use …` / `using …` / `require(…)` / `@import …`；`gitDiffHiddenImportLines(lines)` 丢掉 `Added`/`Removed` 且为顶层 import 的行（保留 context/hunk 头/meta）；`gitDiffHiddenImportRows(rows)` 对分栏行同规则（`Change` 行须**两侧都是** import）；`gitDiffHiddenImportHunks(hunks)` 逐 hunk 应用并保留 hunk 头。 |
| `GitPageComponents.kt` | `GitDiffView` 新增 `hideImports`（默认 `false`）；`GitDiffOptionsMenu` 第 5 项 `HarnessMenuEntry.Item(key = "hide-imports", text = "隐藏导入内容", selected = hideImports)`；`GitDiffFileCard` / `GitUnifiedDiff` / `GitUnifiedDiffScroll` / `GitWrappedUnifiedDiff` / `GitDiffSplitView` 全链路传参；`gitUnifiedDiffAnnotatedString(...)` 末尾新增带默认值的 `hideImports`，开启时先过滤行再绘制。 |

过滤是**逐行省略**：被隐藏行既不出现在可选中文本，也不出现在行号栏；幸存行各自保留原记录的 `oldNumber`/`newNumber`，
所以行号栏不会错位。hunk 头始终保留（统一/分栏一致）。

## §3 测试改动

（3 个文件）

- `apps/shared/test/.../git/GitDiffImportsTest.kt`（新增，7 用例）：跨语言 import 形式识别、缩进/近似关键字/空串不误判、
  统一视图丢改动留 context/头/meta、import 形式的 context 行不被丢、同 hunk 非 import 改动保留、分栏只丢「纯 import」行
  而混合 `Change` 行保留、全行被隐藏时 hunk 头保留。
- `apps/jvm-app/test/.../GitDiffViewInteractionTest.kt`：新增 `diffOptionsMenuHidesTopLevelImportsAndKeepsOrdinaryChanges`——
  默认可见、菜单项文案与未选中态、切换后**可选中文本与行号栏都精确等于过滤后的行**（`assertTextEquals`，含 NBSP 对齐）、
  再切回恢复原文本。
- `apps/jvm-app/test/.../GitWorktreesPageTestSupport.kt`：新增夹具 `importPatch`（一个 hunk：共享 import + 一个被替换的
  import + 共享语句 + 一个真实新增）。
- `apps/jvm-app/test/.../GitWorktreesScreenshotTest.kt`：新增截图用例 `GitHideImports`（`captureGitHideImports`）——拍摄
  `imports-shown`/`imports-hidden` 对照与开/关两份菜单，并断言隐藏前后文本差异只落在 import 行。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-ce-build -p jvm -m shared --include-classes=…GitDiffImportsTest` | 7 / 7（`logs/shared-imports-test.txt`） |
| 同 `-p jvm -m shared`（全量） | 580 found / 580 passed（CD 573 增 7）（`logs/shared-full.txt`） |
| 同 `-p jvm -m jvm-app --include-classes=…GitDiffViewInteractionTest` | 7 / 7（新增 1）（`logs/interaction-test.txt`） |
| 同 `-p jvm -m jvm-app --include-classes=…GitWorktreesScreenshotTest` | 8 / 8（新增 1）（`logs/screenshot-test.txt`） |
| 同 `-p jvm -m jvm-app`（全量） | 708 found / 5 skipped / 703 started / 702 passed / **1 failed**（exit 1）（`logs/jvm-app-full.txt`） |
| 隔离 7 次 `-m jvm-app --include-classes=…WorktreePinInteractionTest` | 6/7 次 4/4 通过，run 4 失败 1/4（`logs/pin-rerun-1..7.txt`、`logs/pin-rerun-summary.txt`） |

全量 708 found 比 CD（706）多 2，正是本批新增的两条 jvm-app 用例（交互 1 + 截图 1）。

### §4.1 唯一失败项的归属（不主张为环境抖动）

唯一失败是 `WorktreePinInteractionTest.realMousePinAndUnpinPersistToFreshReader`，落在 `awaitPin`
（`WorktreePinInteractionTest.kt:242`）的 10s `waitUntil` 超时——这是 Managed 工作树页的**真实鼠标**交互用例，与本批改动的
git diff 视图无关。证据：

- 隔离重跑 7 次里 6 次全绿、1 次（run 4）失败，且 **run 4 失败的是同族另一条**
  `realMouseArchiveAndUnarchivePersistToFreshReader`——失败者身份每次运行都可能不同，说明是时序抖动而非确定性回归。
- 本批改动的符号只被 git 页面/测试引用（`rg -l "gitDiffIsImportOnlyContent|gitDiffHiddenImport|gitUnifiedDiffAnnotatedString|GitDiffView|GitPatch\(" apps modules`
  只命中 `apps/shared/.../client/git/` 与两条 git 测试），失败断言所在的 Managed 工作树路径不经过它们。

该用例与 CD 的 `PtyTerminalFontInteractionTest` 抖动一样，仍属本批之外的未决项。

## §5 截图产物核对

- **本批新增**：`docs/desktop-ui/captures/git-worktrees/` 新增 **48** 个文件（24 PNG + 24 JSON），全部属于新用例
  `git-hide-imports-*`（light/dark × 1440x900/600x800，各 `page-viewport`、`imports-shown`、`imports-hidden`、
  `options-menu-hide-imports-on`、`options-menu-hide-imports-off`、`focused-viewport`）。
- **既有截图的实质变化只有一处**：`git-word-level-diff-*-options-menu-word-diffs-{on,off}`（8 PNG + 8 JSON）——该弹出菜单
  多了第 5 项「隐藏导入内容」，属真实结构变化，保留重生成版本。
- **与批次前快照** `/tmp/ce-captures-before-1791792006` 逐文件比较：`new 48 / changed 16 / removed 0`；其余 252 个全量运行重写
  文件（20 个与本批无关的 `scheduled-execution/*.png` + 232 个仅 Compose 节点 id 变化的 `.json`）已按先例从快照**逐字节还原**
  （还原前 268 个差异→还原后 16 个）。分类依据：JSON 去掉全部数字后比对，232 个完全一致（纯 id 抖动），8 个有非 id 差异
  （即新菜单节点）。
- 明细见 `logs/captures-diff-raw.txt`（还原前）、`logs/captures-verify.txt`（还原后）与 `logs/captures-reproducibility.txt`
  （还原分类 + 新用例 PNG 在隔离/全量两次运行间逐字节一致：`identical=24 / differing=0`）。另注：批次前快照的 `rsync` 在该卷
  `captures/rail/` 报 2 个无法 stat 的陈旧目录项 `.ghost-0000000000000000509{3,4}`（既有文件系统残留，非本批产物）。

### §5.1 隐藏确实发生在正确的行上（像素证据）

`logs/hide-imports-pixels.txt`：把前后两张图转成 BMP 后逐像素比较，

- 正文：light 1440x900 差异全部落在 diff 视口内 `x 360..1075, y 568..802`（12480 像素）；dark 同 bbox（12531 像素）；
  light 600x800 为 `x 28..583, y 468..702`（12160 像素）。即只有 diff 正文区变化，其余页面不动。
- 菜单：`options-menu-hide-imports-off` vs `-on` 为 200x188 菜单整体差异（13678 像素），即勾选态那行的变化。

## §6 边界（明确不主张）

- **import 判定规则**是 Harness 取值（原版分类器未观察到）：只认「无缩进的 import 形式」，不区分文件头部区域，也不按语言分支。
- 原版是 `CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓` 选中写法。
- 开关**未持久化**（原版写 `hideDiffImports`），只存在页面组合状态；重开页面复位。
- 「变更选项」菜单其余项（`refresh`、加载完整文件、复制 `git apply` 命令）仍未做。
- 本批**不改**「隐藏空白字符」的既有实现；顺带观察到 ASAR 对它的描述是 `hide whitespace-only diff changes`，与本仓
  「host 侧 `--ignore-all-space` 重读」的口径不一致，已记入契约待后续核对。
- 行内评论、sticky 吸附、上下文展开、远端 Diff、图片/PDF 预览仍未做；定时任务后台执行、附件协议、MCP/Hooks/Scripts 真实
  连接仍未做。
- 原版同尺寸像素与原生窗口证据（未做）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-ce-build -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-ce-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitDiffViewInteractionTest'
./kotlin test --build-dir=/tmp/harness-ce-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitWorktreesScreenshotTest'
./kotlin test --build-dir=/tmp/harness-ce-build -p jvm -m jvm-app
```

截图重写只影响 `docs/desktop-ui/captures/`；`HARNESS_GIT_WORKTREES_CAPTURE_DIR` 可把输出改到临时目录（本批隔离类运行即如此）。

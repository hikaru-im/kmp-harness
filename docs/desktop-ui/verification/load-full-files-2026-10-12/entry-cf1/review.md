# Batch CF：Diff「加载完整文件」（2026-10-12）

## §1 本批目标

补齐 diff「变更选项」菜单里 ASAR 明确存在、此前未实现的 `codex.review.loadFullFiles.enable`（「加载完整文件」）。
ASAR 只读证据（`logs/asar-load-full-files.txt`，来源 `/tmp/asar-new/webview/assets/`）：

- 文案：`zh-CN-b05cfd55c534.js` 的 `"codex.review.loadFullFiles.enable":"加载完整文件"`；`pull-request-code-review-be17c7c34414.js`
  里 `defaultMessage: 'Load full files'`、`description: 'Menu item to load full file contents for partial diffs'`。
- 控件：diff 选项菜单用 `be.CheckboxItem` 呈现，`checked`/`onCheckedChange` 绑 `loadFullFilesEnabled`/`onToggleLoadFullFiles`。
- 机制：`code-diff-38f339f0a601.js` 的 `Gt({…, loadFullFilesEnabled, …})` 把该开关**编入 diff cache key**；升级门控
  `qe = … && diff.metadata.isPartial && !isBinary && !isGitlink && !type-new && !type-deleted && !renamePure && …`
  说明它**只对被截断的 partial 文件**尝试加载整文件内容。
- **负结果**：逐 asset 扫描整个解包 bundle，`loadFullFiles` 只出现「组件 prop」「cache key」两类用法；原版「取 per-file
  OID → 客户端 differ → 重算」这条路径所依赖的 per-file OID 与客户端行级 differ，**本仓不存在**，因此无法照抄。

因此本批实现可观察的那一层（菜单开关 + 开启后该 diff 的整文件上下文），机制差异如实记入契约 §2.1。

## §2 生产改动

（4 个文件）

| 文件 | 改动 |
| --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt` | `GitDiffRequest` 新增 `loadFullFiles: Boolean = false`（带 ASAR 依据注释）。 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt` | `diff()` 在 `--ignore-all-space` 之后、路径分隔 `--` 之前插入 `if (request.loadFullFiles) args.add("--unified=$FULL_FILE_CONTEXT_LINES")`；文件末尾新增 `private const val FULL_FILE_CONTEXT_LINES = 100_000`。 |
| `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt` | `GitPatch` / `GitDiffView` 新增 `loadFullFiles` + `onToggleLoadFullFiles`（默认 `false`）；`GitDiffOptionsMenu` 在「展开」之后、「隐藏空白字符」之前插入第 3 项 `HarnessMenuEntry.Item(key = "load-full-files", text = "加载完整文件", selected = loadFullFiles)`；`onSelect` 增 `"load-full-files" -> onToggleLoadFullFiles(!loadFullFiles)`。 |
| `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt` | `GitPatch(...)` 接线：`loadFullFiles = state.diffRequest.loadFullFiles, onToggleLoadFullFiles = { controller.selectDiff(state.diffRequest.copy(loadFullFiles = it)) }`。 |

开关是**请求级**的：切换后控制器用新的 `GitDiffRequest` 重读一次快照，Host 以 `--unified=100000`（大于任何现实文件行数）
把 hunk 上下文补满，从而展开为整文件上下文。这不改变「未更改文件不出现」等既有语义。

## §3 测试改动

（4 个文件）

- `apps/jvm-app/test/.../desktop/git/DesktopGitBackendTest.kt`：新增
  `loadFullFilesWidensContextBeforeThePathspecAndStaysOffByDefault`——断言默认不带 `--unified=100000`、打开时该参数出现在
  `--ignore-all-space` 之后且位于路径分隔 `--` 之前。
- `apps/jvm-app/test/.../desktop/GitDiffViewInteractionTest.kt`：新增
  `diffOptionsMenuLoadFullFilesRereadsThePatchWithFullContext`——菜单项文案与未选中态、点击后 Host 收到 `loadFullFiles=true`
  且可选中文本包含整文件尾行、再点回退。
- `apps/jvm-app/test/.../desktop/GitWorktreesPageTestSupport.kt`：adapter 新增 `var fullPatch: String? = null`，`diff()` 在
  `request.loadFullFiles` 时返回 `fullPatch ?: patch`；夹具新增 `partialPatch`/`fullFilePatch`。
- `apps/jvm-app/test/.../desktop/GitWorktreesScreenshotTest.kt`：新增用例 `GitLoadFullFiles`（`captureGitLoadFullFiles`）——
  拍摄 `diff-partial` / `diff-full-file` 对照与开/关两份菜单，并断言开关前后文本差异只落在新增的尾行。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-cf-build -p jvm -m shared`（全量） | **580 found / 580 passed**（`logs/shared-full.txt`） |
| 同 `-p jvm -m jvm-app --include-classes=…DesktopGitBackendTest` | 10 / 10（新增 1）（`logs/DesktopGitBackendTest.txt`） |
| 同 `-p jvm -m jvm-app --include-classes=…GitDiffViewInteractionTest` | 8 / 8（新增 1）（`logs/GitDiffViewInteractionTest.txt`） |
| 同 `-p jvm -m jvm-app --include-classes=…GitWorktreesScreenshotTest` | 9 / 9（新增 1）（`logs/screenshot-test.txt`） |
| 同 `-p jvm -m jvm-app`（全量） | **711 found / 5 skipped / 706 started / 705 passed / 1 failed**（exit 1）（`logs/jvm-app-full.txt`） |
| 隔离 `--include-classes=…PtyTerminalFontInteractionTest` | 3 / 3 通过（`logs/pty-rerun.txt`） |

全量 711 found 比 CE（708）多 3，正是本批新增的三条 jvm-app 用例（后端 1 + 交互 1 + 截图 1）。

### §4.1 唯一失败项的归属

唯一失败是 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`，落在
`PtyTerminalFontInteractionTest.kt:177` 的 30s `waitUntil` 超时（`ComposeTimeoutException`）——这是**已知的 PTY 字体
时序抖动**，与本批改动的 git diff 视图无关：

- 先例：BZ 批（`plugin-error-localization-2026-10-12/entry-bz1`）全量的唯一失败就是**同一条**用例，且该批隔离重跑
  `PtyTerminal*Test` 为 10 found / 10 passed / 0 failed（`entry-bz1/logs/pty-flake-rerun.txt`），当时即判定为环境相关的 PTY 抖动。
- 本批隔离重跑 `PtyTerminalFontInteractionTest` 3/3 通过（`logs/pty-rerun.txt`）。
- 另一条已知抖动是 CE 批全量的唯一失败 `WorktreePinInteractionTest.realMousePinAndUnpinPersistToFreshReader`
  （10s `awaitPin` 超时），本批未复现。
- 本批改动的符号只被 git 页面/测试引用（`GitDiffRequest.loadFullFiles`、`FULL_FILE_CONTEXT_LINES`、`GitDiffOptionsMenu`
  的 `load-full-files` 分支），失败用例不经过它们。

## §5 截图产物核对

- **本批新增**：`docs/desktop-ui/captures/git-worktrees/` 新增 **48** 个文件（24 PNG + 24 JSON），全部属于新用例
  `git-load-full-files-*`（light/dark × 1440x900/600x800，各 `page-viewport`、`diff-partial`、`diff-full-file`、
  `options-menu-load-full-files-on`、`options-menu-load-full-files-off`、`focused-viewport`）。
- **既有截图的实质变化只有一处**：`git-word-level-diff-*-options-menu-word-diffs-{on,off}` 与
  `git-hide-imports-*-options-menu-hide-imports-{on,off}`（16 PNG + 16 JSON）——该弹出菜单多了第 3 项「加载完整文件」，
  属真实结构变化，保留重生成版本（`logs/captures-verify.txt` 中 `menu:load-full-files` 节点在切换后出现，切换前不存在）。
- **与批次前快照** `/tmp/cf-captures-before-1791795676` 逐文件比较：`new 48 / changed 32 / removed 0`；其余 264 个全量运行
  重写文件（20 个与本批无关的 `scheduled-execution/*.png` + 244 个仅 Compose 节点 id 变化的 `.json`）已按先例从快照
  **逐字节还原**（还原前 296 changed → 还原后 32 changed）。分类依据：JSON 去掉全部数字后比对（244 完全一致 = 纯 id 抖动）。
- 明细见 `logs/captures-diff-raw.txt`（还原前）、`logs/captures-verify.txt`（还原后）与 `logs/captures-reproducibility.txt`
  （新用例 24 PNG 在隔离/全量两次运行间逐字节一致 `identical=24 / differing=0`；24 JSON 仅节点 id 抖动）。

### §5.1 「加载完整文件」确实补上了整文件上下文（像素证据）

`logs/load-full-files-pixels.txt`：把开关前后两张图转成 BMP 后逐像素比较，

- dark 1440x900：`diff-partial` vs `diff-full-file` 差异全部落在 diff 正文区 `x 360..1075, y 568..802`（3544 像素）。
- light 1440x900：同 bbox（3553 像素）；dark 600x800：`x 28..583, y 468..702`（3224 像素）。

即只有 diff 正文区多出内容行，页面其余部分不动。

### §5.2 已知既有现象：`scheduled-execution/*.png` 与本批无关

批次前快照里 `docs/desktop-ui/captures/scheduled-execution/*.png` 与**当前代码**渲染结果不一致（20/20 文件差异）。隔离重复运行
`ScheduledManagementScreenshotTest` 两次输出**逐字节一致**（run1 vs run2 = 0 差异），说明这不是运行不确定性，而是该目录的
既有截图相对当前代码**过期**：计划文档（CA 小节）已将其记为**故意冻结的历史基线**，早于 `ScheduledLocalExecutionModel`
披露文案（`apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledModels.kt:132`，展示于
`ScheduledManagementPage.kt:113`），所以每次全量都会重写、每次按先例还原。CE 全量也曾出现同一组 20 个文件
（`/tmp/ce-logs/captures-diff.txt`）并按同一先例还原。本批沿用先例还原，不在本批范围内修改。

## §6 边界（明确不主张）

- **per-file OID + 客户端 differ**：原版「按 OID 取整对象 + 客户端重算」路径未实现；本仓用 Host 全上下文重算代替（机制不同，
  结果可观察一致）。
- **`isPartial` 门控**：本仓没有 partial 标记，开关对请求内所有文件统一加宽，不区分是否被截断。
- **`maxObjectBytes` / `too-large` 回退**：原版逻辑未实现。
- 开关**未持久化**：只存在页面组合状态，重开复位。
- **菜单顺序**与 ASAR 略有出入（沿用既有菜单次序）。
- 「变更选项」菜单其余项 `refresh`、`richPreview`、复制 `git apply` 命令仍未做。
- 行内评论、sticky 吸附、上下文展开、远端 Diff、图片/PDF 预览仍未做；定时任务后台执行、附件协议、MCP/Hooks/Scripts 真实
  连接仍未做。
- 原版同尺寸像素与原生窗口证据（未做）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-cf-build -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-cf-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.git.DesktopGitBackendTest'
./kotlin test --build-dir=/tmp/harness-cf-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitDiffViewInteractionTest'
./kotlin test --build-dir=/tmp/harness-cf-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitWorktreesScreenshotTest'
./kotlin test --build-dir=/tmp/harness-cf-build -p jvm -m jvm-app
```

截图重写只影响 `docs/desktop-ui/captures/`；`HARNESS_GIT_WORKTREES_CAPTURE_DIR` 可把输出改到临时目录（本批隔离类运行即如此）。

# Batch CD：Diff「词级差异」强调（2026-10-12）

## §1 本批目标

补齐 diff 视图里最后一项**默认开启**的显示开关 `codex.review.wordDiffs.label`（「词级差异」）。ASAR 只读证据
（`logs/asar-worddiffs.txt`，来源 `/tmp/asar-new/webview/assets/`）：

- 设置键与默认值：`app-initial-6350d04672d0.js` 里 `sja = 'wordDiffsEnabled.2'`、`mja = Fu(sja, !0)`——同区
  `wrapCodeDiff.2`/`hideDiffWhitespace`/`diffRichPreview` 都是 `!1`，只有词级差异默认开启。
- 控件：`pull-request-code-review-be17c7c34414.js` 的 diff 选项菜单组件 `jr({…, wordDiffsEnabled, onToggleWordDiffs})`
  用 `be.CheckboxItem` 呈现该开关，文案 `codex.review.wordDiffs.label`，`defaultMessage: 'Word diffs'`，
  `description: 'Toggle for highlighting individual changed words in the diff view'`。
- 中文文案：`zh-CN-b05cfd55c534.js` 的 `"codex.review.wordDiffs.label":"词级差异"`。

未观察到强调色 token、分词与配对算法；这些按 Harness 取值实现并记录在契约里
（`../../reverse-engineering/word-level-diff-contract-2026-10-12.md`）。

## §2 生产改动

（3 个文件，均在 `apps/shared/src/im/hikaru/harness/client/git/`）

| 文件 | 改动 |
| --- | --- |
| `GitWordDiff.kt`（新增） | `GitWordSpan(start, end)` / `GitWordDiff(removed, added)`；`gitWordTokens` 把一行切成词、空白、单字符三类 run；`gitWordDiff(old, new)` 用公共词前缀 + 公共词后缀 trim，中间整体标为变化（相等→空，整行不同→整行），相邻 span 合并；`gitUnifiedWordDiff(lines)` 只在同一 hunk 内按序配对 `-`/`+` 行（context/header/meta flush；未配对尾部行不产生行内 span）；`gitSplitWordDiff(rows)` 只处理 `Change` 行；`appendWordSpans` 逐字符追加，强调段用 `SpanStyle(background = emphasis)`，**每个字符恰好出现一次**。强调 alpha `WORD_DIFF_EMPHASIS_ALPHA = 0.35f`。 |
| `GitDiffLines.kt` | `gitDiffSplitPaneText(...)` 新增可选 `wordDiff` / `wordAddedBackground` / `wordRemovedBackground`，在既有整行背景之内叠加词级强调；默认参数保持旧行为（21 个既有用例不改）。 |
| `GitPageComponents.kt` | `GitDiffView` 新增 `wordDiffs`（默认 `true`）；`GitDiffOptionsMenu` 第 4 项 `HarnessMenuEntry.Item(key = "word-diffs", text = "词级差异", selected = wordDiffs)`；`GitDiffFileCard` / `GitUnifiedDiff` / `GitUnifiedDiffScroll` / `GitWrappedUnifiedDiff` / `GitDiffSplitView` 全链路传参；新增 `gitDiffLineMarker` + `appendWordDiffLine` 把整行拆成「marker（不强调）+ 文本（可强调）」，可选中文本与拆分前逐字符一致。 |

## §3 测试改动

（4 个文件）

- `apps/shared/test/.../git/GitWordDiffTest.kt`（新增，10 用例）：相等/中间词/空白/尾部追加/整行不同/统一配对与未配对/上下文分隔/分栏只取 `Change` 行/统一与分栏强调都保留全部字符并叠加更强 tint。
- `apps/jvm-app/test/.../GitDiffViewInteractionTest.kt`：新增 `diffOptionsMenuTogglesWordLevelEmphasisOnByDefault` —— 默认 `selected`，切换 off/on 后标记随之变化，且 `+new` 文本始终在。
- `apps/jvm-app/test/.../GitWorktreesPageTestSupport.kt`：新增夹具 `wordLevelPatch`（一行只插入一个词，词级差异恰好只有一段 `Codex `）。
- `apps/jvm-app/test/.../GitWorktreesScreenshotTest.kt`：新增截图用例 `GitWordLevelDiff`（`captureGitWordLevelEmphasis`）——断言默认强调段恰为 `["Codex "]`、关闭后为 0 段且整行文本不变、菜单项 `selected` 与 `✓` 随开关变化；两张对比图都在同一次菜单交互之后拍摄（避免把锚点焦点色算进差异），并在拍摄前把指针移开。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-cd-build -p jvm -m shared`（全量） | 573 found / 573 passed（比 CC 的 563 多 10，正是新增 `GitWordDiffTest`）（`logs/shared-full.txt`） |
| 同 `-m jvm-app --include-classes=…GitWorktreesScreenshotTest` | 7 / 7（+1 新增用例）（`logs/screenshot-word-level.txt`） |
| 同 `-m jvm-app`（全量） | 706 found / 5 skipped / 700 passed / **1 failed**（exit 1）（`logs/jvm-app-full.txt`） |
| 隔离重跑 `--include-classes=…PtyTerminalFontInteractionTest` | 7 次中 1 次 3/3 通过、6 次 2/3（`logs/pty-isolate-rerun-pass.txt`、`logs/pty-isolate-rerun-fail.txt`） |
| 同 `-m jvm-app --include-classes=…terminal.PtyTerminalConnectionIntegrationTest` | 4 / 4 通过（真实 PTY 集成链路正常）（`logs/pty-connection-integration.txt`） |

全量 706 found 比 CC（704）多 2，正是本批新增的两条 jvm-app 用例（交互 1 + 截图 1）。

### §4.1 唯一失败项的归属（不主张为环境抖动）

唯一失败为 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（`PtyTerminalFontInteractionTest.kt:177`
的 `waitUntil` 超时：改字号后输入 `r`，等待 `size-trigger.hex == "72"` 与 `kernel-size == "<rows> <cols>"`）。本批**没有**把它记成环境抖动，
证据是：

- 隔离重跑并不稳定：7 次里只有 1 次 3/3 通过，其余 6 次都在同一断言上超时；同一份代码既能过也能挂，说明它是**间歇性**而非确定性回归，也不能反过来说「反复失败=本批回归」。
- 本批改动的三个文件只被 git 相关文件引用（`rg -l "GitDiffLines|GitPageComponents|GitWordDiff" apps modules` 只命中 git 页面与 git 测试），失败断言所在的 PTY 路径不经过它们。
- 同一夹具的真实 PTY 集成类 `PtyTerminalConnectionIntegrationTest` 4/4 通过，说明终端栈本身可用。

该用例与 CB（同级 PTY 抖动）是同一条，仍未定位根因，属于本批之外的未决项。

## §5 截图产物核对

- **本批新增**：`docs/desktop-ui/captures/git-worktrees/` 新增 **48** 个文件（24 PNG + 24 JSON），全部属于新用例
  `git-word-level-diff-*`（light/dark × 1440x900/600x800，各 `page-viewport`、`word-emphasis-on`、`word-emphasis-off`、
  `options-menu-word-diffs-on`、`options-menu-word-diffs-off`、`focused-viewport`）。
- **没有改动任何既有截图**：与批次前快照 `/tmp/cd-captures-before-Z1ulD1` 逐文件 sha256 比较，`added 48 / removed 0 / changed 0`。
- **全量运行的重写已还原**：全量 `jvm-app` 会重写它负责的所有截图目录，跑前先快照 `/tmp/cd-full-before-0XfOfM`，跑后
  `rsync -a` 还原并重新放回本批 24 个 PNG/JSON；结果是「与跑前快照相比只有新用例的 24 个 JSON 节点 id 变化，0 个 PNG 变化」。
- **可复现**：新用例的 24 个 PNG 在隔离类运行、第四次隔离重跑、全量运行三次之间**逐字节相同**。
- 明细见 `logs/captures-verify.txt`。另注：全量运行在该卷的 `captures/rail/` 留下了 2 个无法 stat 的陈旧目录项
  `.ghost-0000000000000000509{3,4}`（文件系统层面的残留，不是本批产物，也无法用 unlink 清除），已记录在日志里。

### §5.1 强调确实画出来了（像素证据）

`logs/word-emphasis-pixels.txt`：把 on/off 两张图转成 BMP 后逐像素比较，四处组合的差异**只有一个色带**，正好落在新增行里被
改动的词上（例：light 1440x900 为 `x 704..749, y 730..746`，共 758 像素），颜色由行背景 `rgb(229,246,236)` 变为强调
`rgb(166,223,188)`；dark 为 `rgb(58,70,62)` → `rgb(76,100,85)`。这既证明强调生效，也证明开启/关闭**只**改这一处底色、
不改文本。

## §6 边界（明确不主张）

- 强调色、分词与配对算法是 Harness 取值（原版 token/算法未观察到）；未做像素比对，也未复刻 `CheckboxItem` 控件形态（沿用既有菜单的 `✓`）。
- 「变更选项」菜单其余项（复制 `git apply` 命令、隐藏 import、加载完整文件、refresh）未实现。
- 开关未持久化（原版写 `wordDiffsEnabled.2`）；本仓只存在页面组合状态里。
- 行内评论、sticky 吸附、上下文展开、远端 Diff、图片/PDF 预览仍未做。
- 定时任务「应用退出后后台执行」、附件协议、MCP/Hooks/Scripts 真实连接仍未做。
- 原版同尺寸像素与原生窗口证据（未做）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-cd-build -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-cd-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitWorktreesScreenshotTest'
./kotlin test --build-dir=/tmp/harness-cd-build -p jvm -m jvm-app
```

截图重写只影响 `docs/desktop-ui/captures/`；如需不落盘，可用 `HARNESS_GIT_WORKTREES_CAPTURE_DIR` 指向临时目录
（本批的隔离类运行就是这么拍的）。

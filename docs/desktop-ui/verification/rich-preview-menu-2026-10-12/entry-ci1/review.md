# Batch CI：Diff「变更选项」菜单「渲染预览」（2026-10-12）

## §1 本批目标

补齐 diff「变更选项」菜单里 ASAR 明确存在、此前未实现的勾选项 `codex.review.richPreview.label`（「渲染预览」）。
ASAR 只读证据（`logs/asar-rich-preview.txt`，来源 `/tmp/asar-new/webview/assets/`）：

- 文案：`zh-CN-b05cfd55c534.js` 的 `"codex.review.richPreview.label":"渲染预览"`，同区两条相关文案 `"codex.diffView.richPreviewToggle":"切换富文本预览"`、`"codex.diffView.richPreviewLoading":"正在加载预览…"`。
- 菜单形态：`pull-request-code-review-be17c7c34414.js` 的菜单组件里 `be.CheckboxItem{checked:f,closeOnSelect:!0,onCheckedChange:p,leftIcon:…}`，`children` 引用 `codex.review.richPreview.label`，由 `showRichPreview` 门控——是**勾选项**（对应 `checked`/`onCheckedChange`）。
- 第二入口：`editor-diff-page-86fa0f0062f3.js` 的 diff 页工具栏另有 `codex.diffView.richPreviewToggle` 开关（tooltip「切换富文本预览」）。即**原版工具栏与菜单两个入口都有**。
- 备注：`richPreviewEnabled` 出现在 `app-shared`、`app-initial`、`code-diff`、`editor-diff-page`、`pull-request-code-review`、`thread-side-panel-tab-content` 多个 asset。

本仓在 AW 批已实现 diff 工具栏按钮 `GitDiffRichPreviewToggle`（保留不动）。本批把同一开关接入「变更选项」菜单。

## §2 生产改动

（1 个文件）

| 文件 | 改动 |
| --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt` | 私有 `GitDiffOptionsMenu` 新增 `richPreview: Boolean` 与 `onToggleRichPreview: (Boolean) -> Unit`；`entries` 在 `load-full-files` 之后插入第 4 项 `HarnessMenuEntry.Item(key = "rich-preview", text = "渲染预览", selected = richPreview)`；`onSelect` 增 `"rich-preview" -> onToggleRichPreview(!richPreview)`；`GitDiffView` 调用处传 `richPreview = richPreview, onToggleRichPreview = { richPreview = it }`，与工具栏按钮共用 AW 批已有的 `richPreview` 状态；KDoc 更新。 |

实现要点：与 CH 批同样的教训——反馈/新增节点**不**用外层 `Column` 包 diff，避免把 `GitDiffView` 贡献给调用方布局作用域的子节点重新挂到新 `Column` 下、改变 `SettingsFormSection` 的 16dp 间距。本批只在既有菜单 `entries` 列表里插入一项，无布局结构变化。保留既有工具栏按钮 `GitDiffRichPreviewToggle`（原版两入口都有）。

## §3 测试改动

（2 个文件）

- `apps/jvm-app/test/.../desktop/GitDiffRichPreviewInteractionTest.kt`（+1 用例）：`richPreviewMenuItemSharesStateWithToolbarToggle`——初始打开菜单，`menu:rich-preview` 文案为「渲染预览」且**未选中**；点菜单项后 markdown 富预览节点出现（`git-diff-rich-preview-docs/readme.md` 与 heading 各 1）；重开菜单该项**已选中**；改用工具栏按钮关闭后富预览消失、重开菜单该项**同步未选中**。
- `apps/jvm-app/test/.../desktop/GitWorktreesScreenshotTest.kt`：`saveMenu` 的弹层高度上界由 `321` 调整为 `380`（原因见 §5.1）。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-ci-build -p jvm -m shared`（全量） | **584 found / 584 started / 584 passed / 0 failed**（`logs/shared-full.txt`；本批生产改动在 `apps/shared`，此数为最终生产代码结果） |
| 同 `-m jvm-app --include-classes=…GitDiffRichPreviewInteractionTest` | 4 found / 4 passed / 0 failed（新增 1，上批 3）（`logs/GitDiffRichPreviewInteractionTest.txt`） |
| 同 `-m jvm-app --include-classes=…GitDiffViewInteractionTest` | 10 found / 10 passed / 0 failed（`logs/GitDiffViewInteractionTest.txt`） |
| 同 `-m jvm-app --include-classes=…GitWorktreesScreenshotTest`（输出重定向 `/tmp/ci-captures-park`） | 9 found / 9 passed / 0 failed（`logs/GitWorktreesScreenshotTest.txt`） |
| 同 `-m jvm-app`（全量，写 `docs/desktop-ui/captures/`） | **714 found / 5 skipped / 709 started / 709 passed / 0 failed**（exit 0）（`logs/jvm-app-full.txt`） |

全量 `shared` 584 与 CH 持平；`jvm-app` 714 found 与 CH（713）多 1，正是本批新增的交互用例。全量 `jvm-app` **0 失败**。

说明（修复前的一次全量）：在调高 `saveMenu` 上界之前，本批代码的全量 `jvm-app` 为 `714 found / 5 skipped / 709 started / 705 passed / 4 failed`：其中 **3** 个是本批必须修的弹层上界断言（`captureGitHideImports`/`captureGitLoadFullFiles`/`captureGitWordLevelEmphasis`，见 §5.1），第 **4** 个是历史 PTY 字体抖动 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds()`（`ComposeTimeoutException: Condition still not satisfied after 30000 ms`）。修复上界后的全量（上表）该 PTY 用例在本机全量运行中**通过**（`logs/jvm-app-full.txt`：`Passed savedFontChangeRefitsSameRunningPtyAtFixedBounds()`），故本批全量为 0 失败。

补充（隔离归属，非本批回归）：把同一代码单独跑 `PtyTerminalFontInteractionTest` 3 次，每次都是 **2/3**（同一条 30s 超时；见 `logs/PtyTerminalFontInteractionTest.txt` 与 `logs/PtyTerminalFontInteractionTest-extra.txt`）。即该 PTY 用例对运行环境/顺序敏感（全量中通过、单跑超时），与 CD/CE/CF 记录的同族抖动一致，且本批生产符号（`GitPageComponents` 菜单项）与终端栈无关。

## §5 截图产物核对

基线 = `/tmp/ci-captures-before`（CH 终态 `docs/desktop-ui/captures/`，4150 文件）。全量 `jvm-app` 会重写 `docs/desktop-ui/captures/`，与基线比较分类见 `logs/captures-classification.txt`：

- 还原前 `added 0 / removed 0 / changed 596`：`json 552`（其中 **528 个去数字后完全一致**，仅 Compose 节点 id 抖动——`git-worktrees` 464、`plugins-page-2026-10-08` 64；**24 个实质变化**，全在 `git-worktrees`）、`png 44`（**24 个尺寸变化** `200x296 → 200x332`，全在 `git-worktrees` 三组 `options-menu-*`；**20 个像素不同**，全在 `scheduled-execution`，是早于现行代码的**历史冻结基线**，与本批无关）。
- 把 528 个仅数字抖动的 `.json` 与 20 张 `scheduled-execution/*.png` 按先例从基线**逐字节还原**（`logs/captures-restore.txt`：`restored json-jitter = 528, scheduled-execution png = 20`）后，与基线差 **`added 0 / removed 0 / changed 48`**（48 = 24 PNG + 24 JSON，全部是 `git-worktrees` 三组 `options-menu-*`），见 `logs/captures-verify.txt`。
- 隔离重跑 `GitWorktreesScreenshotTest` 两次（`/tmp/ci-captures-park`、`/tmp/ci-captures-park2`，同一最终代码）`diff -rq` **EXIT=0，完全逐字节一致**；且这 24 个 `options-menu-*.png` 在两次隔离运行与全量运行（`docs/desktop-ui/captures/`）之间**逐字节一致**（`identical=24 / differing=0`），见 `logs/captures-reproducibility.txt`。

### §5.1 `saveMenu` 弹层上界 321 → 380

本批给菜单加了第 9 项，弹层由 `200x296` 变为 `200x332`（+36px，正好一行），超过 `GitWorktreesScreenshotTest.saveMenu` 里硬编码的上界 `assertTrue(bitmap.height in 1..321)`，导致 3 个打开「变更选项」菜单的截图用例（`captureGitHideImports`/`captureGitLoadFullFiles`/`captureGitWordLevelEmphasis`）在断言处失败。将上界提高到 `380`（仍能捕获失控增长，如整屏高度）后 9/9 通过。另有一处 `git-checkout-branch-menu` 的高度断言 `menu.height <= 321f`（`Case.GitBranchMenu`）**未受影响**（分支菜单项未变），保持不变。

### §5.2 菜单确实多出一行、且是中段插入（像素证据）

`logs/menu-pixels.txt` 与 `logs/*-stacked.png`（上=基线 8 项、下=本批 9 项）：

- 四个抽样菜单弹窗（word-level-diff dark/light × 1440/600，`word-diffs-on`）一致地由 `200x296` 变为 `200x332`（`dh=36`）。
- 新项插在**第 4 位**（「加载完整文件」之后、「隐藏空白字符」之前，与 ASAR 菜单顺序 `loadFullFiles → richPreview` 一致），故该项以下内容整体下移一行：最佳对齐为「new = base 下移 36px」（228/296 行匹配），顶部 `rows 0..122` 不变。
- 目视 stacked 图：上（基线）为 自动换行 / 折叠全部差异 / 加载完整文件 / 隐藏空白字符 / ✓词级差异 / 隐藏导入内容 / 刷新 / 复制 git apply 命令；下（本批）在「加载完整文件」之后多出「渲染预览」，其余逐项一致。

## §6 边界（明确不主张）

- **控件形态**：原版是 `be.CheckboxItem`，本仓沿用既有「变更选项」菜单的 `✓ selected` 写法（与 CD/CE/CF/CG/CH 一致）。
- **开关持久化**：本仓 `richPreview` 只存在页面组合状态（`remember`），未持久化；原版键名未确认（`richPreviewEnabled` 出现在多个 asset，未逐一定位存储层）。
- **菜单顺序**：`richPreview` 已放到 ASAR 顺序对应的位置（`loadFullFiles` 之后）；本仓既有的 `hide-whitespace`/`word-diffs` 相对顺序与 ASAR 仍有出入、`refresh` 置于末段，均为先前批次遗留，本批不改。
- 「变更选项」菜单其余项（图片/SVG/PDF 预览、行内评论、sticky 吸附、上下文展开、`autoSwitchToUnified`、远端 Diff、大 diff 自动折叠的实际执行、工作树列表/移除、上游 fetch）仍未做。
- 定时任务后台执行、附件协议、MCP/Hooks/Scripts 真实连接、目录型 Apps 仍未做。
- **原版同尺寸像素与原生窗口证据**（未做）：`docs/desktop-ui/captures/` 只覆盖本仓渲染，无与原版「同状态」的像素对照。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-ci-build -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-ci-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitDiffRichPreviewInteractionTest'
./kotlin test --build-dir=/tmp/harness-ci-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitDiffViewInteractionTest'
HARNESS_GIT_WORKTREES_CAPTURE_DIR=/tmp/ci-captures-park ./kotlin test --build-dir=/tmp/harness-ci-build -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitWorktreesScreenshotTest'
./kotlin test --build-dir=/tmp/harness-ci-build -p jvm -m jvm-app
```

截图重写只影响 `docs/desktop-ui/captures/`；`HARNESS_GIT_WORKTREES_CAPTURE_DIR` 把输出改到临时目录。截图分类脚本见 `/tmp/ce-logs/ci_classify.py`（纯 python zlib 解码比对像素，无 PIL）。

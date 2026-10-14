# Git diff 富文本预览验收 AW

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-aw1`。

## 1. 本批目标与依据

AR（diff 布局选择器）验收时明确把「rich preview 开关与其渲染」列为**未实现**。本批补上这个开关与可验证的 markdown 预览分支，不新增协议、不伪造成功。完整边界见 `../../../reverse-engineering/git-rich-preview-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：E01 工具栏 pressed 图标按钮（`codex.diffView.richPreviewToggle`、`aria-pressed`、`ghost/ghostActive`、`onClick set(ve,!g)`）；E02 `initialOpen: O && type !== 'deleted'`；E03 `fOr` 的 markdown/image/pdf/diff 判定；E04/E05 扩展名集合与 `D1` basename 规则（含末尾点）；E06 markdown 分支渲染 `Markdown{path, fallback: diffView}`；E07 zh-CN 文案（「切换富文本预览」「正在加载预览…」「无法渲染 PDF 预览」）。

## 2. 写入集

生产（7）：

- `apps/shared/src/im/hikaru/harness/client/git/GitRichPreview.kt`（新增：扩展名/预览种类/路径安全/上限）
- `apps/shared/src/im/hikaru/harness/client/git/GitMarkdown.kt`（新增：小小子集 markdown 解析与行内 run）
- `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`（`GitPreviewFile`、`GitState.previewFiles`、`GitBackend.readPreviewFile` 默认回退）
- `apps/shared/src/im/hikaru/harness/client/git/GitController.kt`（`previewJobs` + `refreshPreviewFiles` + 补丁变化时清缓存）
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`（工具栏开关、预览渲染、回退 notice）
- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`（接线）
- `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt`（`readPreviewFile` 本机实现）

测试（5）：

- `apps/shared/test/im/hikaru/harness/client/git/GitRichPreviewTest.kt`（新增 7 项）
- `apps/shared/test/im/hikaru/harness/client/git/GitControllerTest.kt`（新增 3 项 + `FakeGit` 增加 `readPreviewFile`）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitWorktreesPageTestSupport.kt`（适配器增加 `readPreviewFile` + `previewFiles`/`previewGate`/`previewReads`）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffRichPreviewInteractionTest.kt`（新增 3 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopGitBackendTest.kt`（新增 2 项 + 临时目录清理 helper）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/git-rich-preview-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 判定层与原版一致：`gitFileExtension` 逐字镜像 `D1`（小写化、取最后一个 `/` 或 `\` 后的 basename、`i > 0 && i < len-1`）；`gitDiffPreviewKind` 只保留本项目接得上的 markdown 分支，「开关关 ∧ 非删除 ∧ markdown 扩展名」是唯一进入预览的路径。
- 读取层按路径缓存：`refreshPreviewFiles(paths)` 先 `distinct`，`Ready`/`Unavailable` 都是终态，同一 incarnation 内不重读；`Ready`/`Unavailable` 与其余查询共用 `epoch` 围栏，`invalidate()`/`close()` 取消在途任务并清空 map。读取**不调用 git 命令**，只读工作树文件。
- 陈旧防护：`refreshDiff()` 记录刷新前的补丁文本，只有**文本真的变化**才清空 `previewFiles`；`GitDiffView` 的 `LaunchedEffect(document, previewPaths)` 在 diff 变化时重新请求，因此文本变化后不会继续显示旧内容，而文本未变时缓存保留、不重复读盘。
- 安全与稳健：`GitBackend` 默认 `readPreviewFile` 返回 `Unavailable`，所以不具备文件读取能力的后端自动回退到 diff 文本；本机实现先校验本机只读 binding，再校验路径安全（非空、无 NUL/反斜杠、非绝对、无盘符、无空段/`.`/`..`），再用 `Files.isRegularFile(..., NOFOLLOW_LINKS)`（不跟随符号链接）、有界读取（超过 `GitPreviewTextLimit = 256 KiB` 直接回退）与 `throwOnInvalidSequence` 解码（非 UTF-8 直接回退）。
- 回退可见：markdown 但未 `Ready` 时渲染 `git-diff-rich-preview-notice-<path>` 并**继续显示 diff 文本**（原文语义是静默回退到 diff；本仓把原因显式写出，属于已记录的差异）。
- 渲染器是**子集**：块级覆盖标题/段落/围栏代码/引用/无序与有序列表/分隔线，行内覆盖粗体/斜体/行内代码/链接；不匹配的标记原样保留，绝不静默丢字。

## 4. 问题

- 与原版的已知差异（已在契约 3.1 记录）：只做 markdown，图片/SVG/PDF 保留 diff 文本；markdown 渲染器是子集而非 CommonMark；markdown 失败时用可见 notice 代替原版的静默 diff 回退；开关文案是「富文本预览」（原 tooltip 为「切换富文本预览」），图标字形未复刻。
- `GitPreviewTextLimit`、`GitDiffRichPreviewToggle` 的「选中」样式、notice 文案是本仓选择，原版阈值/字形/静默回退不可观察。
- 全量运行会重新生成未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；跑前该目录为 `ac8ebb54…`，跑后按 AE/AV 先例还原（见 §5.4）。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **506 found / 0 skipped / 506 started / 506 passed / 0 failed**，exit 0 | `logs/shared.log` |
| shared Git 定向 | `im.hikaru.harness.client.git.*` | **87 found / 0 skipped / 87 started / 87 passed / 0 failed**，exit 0 | `logs/shared-git-targeted.log` |
| jvm-app 定向 | `GitDiffRichPreviewInteractionTest` + `DesktopGitBackendTest` + `GitDiffViewInteractionTest` + `GitPageInteractionTest` | **20 found / 0 skipped / 20 started / 20 passed / 0 failed**，exit 0 | `logs/jvm-app-targeted.log` |
| 全量 jvm-app（本批门槛） | 全部 | **677 found / 5 skipped / 672 started / 672 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 纯函数（`GitRichPreviewTest`，+7）：`gitFileExtension` 覆盖大写/多级/反斜杠/无扩展名/前导点/末尾点；`gitDiffPreviewKind` 覆盖每个 markdown 扩展名 + 开关关/删除/图片/SVG/PDF/无扩展名；路径安全拒绝空/空白/绝对/`..`/`.`/双斜杠/盘符/反斜杠/NUL；块解析覆盖标题/段落/围栏代码/引用/列表/分隔线与 Windows 换行、空输入、未闭合围栏；行内覆盖粗体/斜体/行内代码/链接/下划线粗体、未匹配标记原样、空 label 链接回退 href；`markdownAnnotatedString` 逐字保留并着色。
- 控制器（`GitControllerTest`，+3）：未选仓库时不请求；同批 `distinct` 且卡片立即看到 `Loading`；在途重复请求不增读；重绑定丢弃在途结果；`Ready`/`Unavailable` 终态不重读；补丁未变保留缓存且不重读；补丁变化清缓存并重读；读取异常变 `Unavailable` 且不泄露原始 stderr；关闭后忽略请求。
- 页面（`GitDiffRichPreviewInteractionTest`，+3）：开关默认关闭只显示 diff 文本且不读盘；打开后只有 `.md` 渲染 `git-diff-rich-preview-<path>`（标题 tag 命中），`.kt` 保留 diff 文本；关-开复用缓存（读盘仍为 1 次）；删除的 `.md` 永不被请求、缺失预览显示 notice 且 diff 文本仍在；在途 gate 下显示 notice 与 diff 文本，完成后切换为预览。
- 后端（`DesktopGitBackendTest`，+2）：真实临时目录下 `Ready` 读回原文；缺失/目录/非 UTF-8/超限/符号链接均 `Unavailable`；`../`、绝对、盘符、反斜杠、`.`、NUL 等不安全路径全部 `Unavailable`；读取过程零 git 命令；远端/断开/异机的 binding 抛 `Unavailable`。

### 5.2 全量失败归因

全量 `jvm-app` **exit 0，0 failed**（677 found / 5 skipped / 672 started / 672 passed），本批未命中 AE/AF/AM/AO/AQ/AT/AU 记录过的真实 worktree 注册 / PTY 就绪超时抖动，因此无需抖动重跑。相比 AV 的 672 found，本批新增 3 个交互用例 + 2 个后端用例（共 +5），found 由 672 升至 677。

### 5.3 定向回归

`logs/jvm-app-targeted.log` 与 `logs/shared-git-targeted.log` 全绿（§5 表），未出现真实进程/PTY 抖动。

### 5.4 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它（跑后为 `4f387960367fa746579d440583d0e5e2d54e802e`），随后按 AE/AV 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：图片/SVG/PDF 预览、`imagePreviewMode` 的持久来源与设置入口、开关的持久 store 初值、原版 `Markdown` 组件本体、图标字形、markdown 子集之外的 CommonMark 语法。
- `GitPreviewTextLimit`、预览 notice 文案、开关选中样式是本仓选择，非原版可观察值。
- 各批证据独立，整体目标 active。

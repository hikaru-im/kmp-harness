# Diff 逐文件卡片验收 AQ

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-aq1`。

## 1. 本批目标与依据

此前 Git 页面把后端返回的 patch 当作**一整块原文**渲染（旧 `GitPatch` 直接 `HarnessText(patch)`），而 ASAR 证据 D01/D02/D03 显示原版是「头部计数行 + 逐文件可折叠块」，每块带 `metadata.type` 与 +/- 统计，默认展开态 `initialOpen = D && type !== 'deleted'`。本批把已返回的 patch 切成逐文件卡片并渲染状态、计数与折叠交互；不发明 patch 中不存在的内容，不实现工具栏布局选择器与 rich preview。完整边界见 `../../../reverse-engineering/git-diff-file-view-contract-2026-10-11.md`。

## 2. 写入集

生产（2）：

- `apps/shared/src/im/hikaru/harness/client/git/GitDiffFiles.kt`（新增）
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`（`GitPatch` 改为解析后调用 `GitDiffView`）

测试（3）：

- `apps/shared/test/im/hikaru/harness/client/git/GitDiffFilesTest.kt`（新增）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffViewInteractionTest.kt`（新增）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitWorktreesScreenshotTest.kt`（`GitLargeDiff` 用例改为断言逐文件卡片）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/git-diff-file-view-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- `GitDiffFiles`（纯文本，无平台依赖）：按 `diff --git ` 切块；hunk 内统计 `+`/`-`（排除 `+++`/`---`/`\ No newline`）；按 `new file mode`/`deleted file mode`/`rename from`/`copy from` 归类 Added/Deleted/Renamed/Copied/Modified；从 `---`/`+++`/`rename`/`copy`/首行 `a/… b/…` 解析路径，`diff --git` 与各 header 的引号路径解 Git 转义（八进制 → UTF-8 字节再解码）；补丁开头存在非文件文本时置 `leadingContent`，不据以发明文件。
- `GitPageComponents`：`GitDiffView(document)` 渲染 `git-diff-summary` 计数行（`N 个文件变更 · +A −D`）、`git-diff-leading` 提示、`git-diff-file` 卡片（标题 `git-diff-file-title` 显示 `原路径 → 新路径 · 状态`、明细 `git-diff-file-detail` 显示展开/收起与 `+N −M`、正文 `git-diff-file-patch`）；删除型文件永不自动展开，其余用 Harness 阈值 `files.size <= 10 && added+deleted <= 400`；保留 `git-diff-viewport`/`git-diff-vertical` 与 288dp 高度与 `SelectionContainer`（文本可选中复制）。

## 4. 问题

- 全部生产/测试套件通过；全量 `jvm-app` 命中历史上偶发的 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 真实 PTY 就绪 30s 超时（与本批无关，见下方判定）。
- 全量运行重新生成了未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；按 AE/AJ/AP 先例从批次前备份还原，`diff -r` 一致，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`。
- 全量运行同时重新生成了 Git 页面截图（逐文件卡片与折叠属预期变化），未做像素对照。`docs/desktop-ui/captures/git-worktrees/` 是累积式截图目录：本批把旧 `diff-before-scroll` 命名改为 `diff-collapsed`/`diff-after-independent-scroll`，故目录中仍留有 8 个上一批（AP，14:32）的 `diff-before-scroll` 旧产物；未删除（目录按先例只增不减），它们不对应任何现行用例。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 解析器 + git 包 | `im.hikaru.harness.client.git.*`（含新增 `GitDiffFilesTest` 5 项） | **68/68**，0 失败/错误/跳过，exit 0 | `logs/shared-git-diff.log` |
| 生产页面交互 + 截图回归 | `GitDiffViewInteractionTest` + `GitWorktreesScreenshotTest` | **8/8**，exit 0 | `logs/desktop-diff-view.log` |
| 全量 jvm-app（本批门槛） | 全部 | **663 found / 5 skipped / 658 started / 657 passed / 1 failed**，exit 1（唯一失败为下方已知无关 PTY 抖动，见 §5.2） | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。截图写入 `/tmp/aq-captures`（`HARNESS_GIT_WORKTREES_CAPTURE_DIR`），全量运行再按默认目录重生成仓库内 Git 截图。

### 5.1 新增断言（关键）

- 解析器：多文件切块互不串味（`body` 不跨越文件边界）；Added/Deleted/Renamed/Copied/Binary 分类与计数；`git-quoted` UTF-8 路径（`\346\226\207`）与含空格的未引号路径均可解；空 patch 与开头杂散文本不发明文件。
- 生产页面：两文件小 diff 默认展开、逐个独立折叠/展开，计数 `2 个文件变更 · +2 −1`；500 行大 diff 默认折叠（`+500 −0`），点击头部后展开；切到空 diff 时逐文件卡片消失、`git-diff-empty` 出现且不再显示计数。
- 截图：`GitLargeDiff` 用例断言 `1 个文件变更 · +500 −0`、默认折叠、点击后展开、垂直与水平方向可分别滚动且视口边界不变。

### 5.2 已知无关抖动判定

`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 在本机当前负载下**稳定命中**（全量两次均为 663/5/658/657·1，隔离三次均 2/3；全部为 PTY 就绪 30s 超时），与 AE/AF/AM/AO 记录的同一抖动一致；本批改动只在 Git diff 解析与渲染路径，不经 PTY/字体（该抖动在 AP 全量曾未命中、在 AO 首次全量命中，属负载相关的既有时序问题）。故记为环境抖动，不计入本批失败，但**不主张全量为 0 失败**。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口；本机 Screen Recording 权限当前不可用（`screencapture` 返回 `could not create image from display`），本批无原生捕获。
- 未实现/未验证：工具栏统一/分栏/自动布局选择器、rich preview、行内评论、sticky header 吸附；阈值 `Ae`/`je` 与 `metadata.type` 其余取值未观察，Harness 阈值属自定。
- 各批证据独立，整体目标 active。

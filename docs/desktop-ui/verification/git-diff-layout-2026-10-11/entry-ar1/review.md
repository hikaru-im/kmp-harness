# Diff 布局选择器与分栏渲染验收 AR

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-ar1`。

## 1. 本批目标与依据

ASAR 证据 D01/D02 显示 diff 工具栏有一个页面级 unified/split/auto 三选一布局选择器（`codex.diffView.switchToUnified/switchToSplit/auto`，auto tooltip 明确「split for additions and removals, inline for narrow panes or one-sided changes」），而本仓 diff 一直只有单栏统一渲染。本批补上这一层：页面级选择器 + 按 auto 语义解析 + 真实的左右分栏渲染。完整边界见 `../../../reverse-engineering/git-diff-layout-contract-2026-10-11.md`。

## 2. 写入集

生产（3）：

- `apps/shared/src/im/hikaru/harness/client/git/GitDiffLayout.kt`（新增）
- `apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt`（工具栏 + 选择器 + 分栏渲染）
- `apps/shared/src/im/hikaru/harness/client/git/GitDiffFiles.kt`（hunk 计数去掉 `+++`/`---` 前缀排除）

测试（3）：

- `apps/shared/test/im/hikaru/harness/client/git/GitDiffLayoutTest.kt`（新增，4 项）
- `apps/shared/test/im/hikaru/harness/client/git/GitDiffFilesTest.kt`（新增 `hunkLinesThatThemselvesStartWithDoubleMarkersAreCounted`）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitDiffViewInteractionTest.kt`（新增 `layoutSelectorSwitchesBetweenUnifiedSplitAndAuto`）
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitWorktreesScreenshotTest.kt`（`GitLargeDiff` 追加 `diff-split` 截图）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/git-diff-layout-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- `GitDiffLayout`（纯逻辑，无平台依赖）：`splitDiffBody` 把 hunk 正文的删除 run 与新增 run 按序配对成左右行（上下文行两侧同文、缺侧留空、去掉行首标记、忽略 `\ No newline`）；`effectiveGitDiffLayout` 实现 auto = 面板宽度 `>= 520.dp` 且文件两侧都有变化才 Split，否则 Unified；显式 Unified/Split 原样透传。
- `GitPageComponents`：工具栏 `git-diff-toolbar`（左计数 `git-diff-summary`、右选择器 `git-diff-layout`）；`BoxWithConstraints` 取面板宽度判定窄面板；文件卡片按有效布局渲染统一（`git-diff-file-patch`）或分栏（`git-diff-split`/`-hunk`/`-row`/`-left`/`-right`）。
- `GitDiffFiles`：hunk 内每行都自带标记，故内容以 `++`/`--` 开头的增删行此前被漏计；改为只按自身标记计数。

## 4. 问题

- 全部定向套件通过；全量 `jvm-app` 结果见 §5（含历史 PTY 字体抖动判定）。
- 全量运行重新生成了未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；按先例从批次前备份还原，`diff -r` 一致，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`。
- 默认布局保持 unified（原版持久默认不可观察）；选择器用「统一/分栏/自动」文字标签代替原版图标字形，属 Harness 取舍。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 解析/布局 + git 包 | `im.hikaru.harness.client.git.*`（含新增 `GitDiffLayoutTest` 4 项、计数回归 1 项） | **73/73**，exit 0 | `logs/shared-git-layout.log` |
| 生产页面交互 + 截图回归 | `GitDiffViewInteractionTest` + `GitWorktreesScreenshotTest` | **9/9**，exit 0 | `logs/desktop-diff-layout.log` |
| 全量 jvm-app（本批门槛） | 全部 | **664 found / 5 skipped / 659 started / 659 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。截图写入 `/tmp/ar-captures`。

### 5.1 新增断言（关键）

- 布局纯函数：删除 run 与新增 run 配对、上下文行两侧同文、缺侧留空、跨多个 hunk、内容以 `++`/`--` 开头仍按自身标记分类、`\ No newline` 忽略。
- auto：宽面板 + 两侧变化 → Split；窄面板或单侧变化 → Unified；显式 Unified/Split 不受宽度影响。
- 生产页面：默认仍 Unified（`git-diff-file-patch` 计数 2）；选「分栏」后两文件都分栏（`git-diff-split` 计数 2）且首行左右为 `old`/`new`、`git-diff-file-patch` 计数 0；选「自动」后两文件（重命名有增有删、单侧仅新增）中只有前者分栏（`git-diff-split` 计数 1、`git-diff-file-patch` 计数 1）。
- 截图：`GitLargeDiff` 追加 `diff-split`（1 个文件 +500 −0，分栏下左侧空白、右侧 500 行新增）。

### 5.2 历史抖动

`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 是 AE/AF/AM/AO/AQ 记录过的真实 PTY 就绪 30s 超时抖动；本批全量**未命中**（659/659 全绿）。本批改动只在 Git diff 解析/布局/渲染路径，不经 PTY/字体。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：rich preview 开关、分栏行内评论/吸附头、原版图标字形与持久默认、`hunkSeparators:'line-info'` 在分栏中的精确排布。
- 各批证据独立，整体目标 active。

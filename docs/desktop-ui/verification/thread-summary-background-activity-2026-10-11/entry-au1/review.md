# Thread 摘要面板「背景活动」小节验收 AU

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-au1`。

## 1. 本批目标与依据

AT 已补上右侧摘要 island 的「环境」小节。原版摘要 island 另有两条彼此独立、只在有真实数据时才渲染的后台活动小节：`sectionKey:'background-subagents'`（标题「子智能体」）与 `sectionKey:'background-tasks'`（标题「后台进程」）。二者共用同一行渲染器 `$b`：子 Agent 行 = 名称 + 活跃时的 meta「运行中」（+ 可选 `diffStats` 徽标），点击打开该子 Agent；后台终端行 = 命令首个非空行（命令不可用时回退「后台终端」），点击打开后台终端，另带一个「停止所有后台终端」批量操作；每条小节带 `SectionCount`；列表为空时整节返回 `null`。本批只补前端可视结构与**真实只读投影**，不发明行、不新增后台进程或子 Agent。完整边界见 `../../../reverse-engineering/thread-summary-background-activity-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-summary-activity-evidence.txt`）：B01 `background-subagents` 小节（`autoCollapse`、`SectionCount`、空列表返回 `null`）；B02 `background-tasks` 小节（终端清单、`SectionCount`）；B03 共用行渲染器 `$b`（agent / terminal 两类行的字段与点击）；B04 摘要面板全部 `sectionKey` 清单与 zh-CN 文案。

## 2. 写入集

生产（4）：

- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivity.kt`（新增）
- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivityController.kt`（新增）
- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryPanel.kt`（新增 `subagents`/`processes`/`onOpenActivity` 参数与两个小节）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（控制器接线 + 打开摘要时的一次只读刷新 + 行点击）

测试（4）：

- `apps/shared/test/im/hikaru/harness/client/summary/ThreadSummaryActivityTest.kt`（新增，5 项）
- `apps/shared/test/im/hikaru/harness/client/summary/ThreadSummaryActivityControllerTest.kt`（新增，5 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/ThreadSummaryActivityPanelInteractionTest.kt`（新增，2 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/HarnessAppThreadPanelsShellTest.kt`（追加子智能体/后台进程小节断言）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/thread-summary-background-activity-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 投影（纯函数）：`threadSummarySubagents(PanelLoad<SubagentOverview>)` 只取 `Ready`，运行中（Active/Waiting）在前、再按 recency；label = 子 Agent 显示名，meta = 运行中时「运行中」。`threadSummaryProcesses(List<BackgroundTerminalSummary>)` 的 label = 命令首个非空行（截断 120 字），命令不可用时回退「后台终端」，running = lifecycle 为 Running。非 `Ready`/空 → 空列表，整节不画。
- 控制器（只读）：`ThreadSummaryActivityController` 复用 `ThreadPanelsRepository.readOverview` 与 `readTerminalSelection(owner, null).inventory`；每个已发布快照按 owner 身份 + 请求代数双重围栏；提供 `bind`/`isBoundTo`/`refresh`/`close`。打开摘要不会打开 tab、不会创建进程、不会 spawn agent；读取失败只置空或错误，不伪造行。
- UI 岛：`ThreadSummaryPanel` 新增 `subagents`/`processes`/`onOpenActivity`；渲染 `thread-summary-section-subagents`/`-processes`（合并语义标题 + `thread-summary-section-<slug>-count`）与行 `thread-summary-subagent-<id>`/`thread-summary-process-<id>`（可点击，运行中 meta 用 Success 色）。列表为空时不画小节。
- 壳层：`HarnessApp` 用 `remember(conversationScope)` 建控制器，`DisposableEffect` 关闭、`LaunchedEffect` 绑定 owner；摘要面板打开且处于 Thread 页且 owner 非空时只读刷新一次；行点击打开对应 tab（`OpenTab(Subagents|BackgroundOutput)`），并在面板已持有该行时尽力深选 `SelectChild`/`SelectTerminal`，否则安全空操作。

## 4. 问题

- 定向与 shared 套件全绿；全量 `jvm-app` 命中 5 个**真实进程/PTY 负载超时**（见 §5），全部落在本批未触碰的 worktree/PTY 路径。
- 全量运行重新生成了未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；按 AE/AT 先例从批次前备份 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`。
- 还原时复核发现：该目录批次前字节（07:54）早于 `ScheduledModels.kt` 09:47 引入的「本机定时任务只在本应用运行期间执行……」披露文案，因此按先例还原后的结构与当前代码在这一点上并不一致。本批不加像素主张，仅按既有约定保留该未跟踪目录的批次前字节，在此如实标注。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **492 found / 0 skipped / 492 started / 492 passed / 0 failed**，exit 0 | `logs/shared.log` |
| jvm-app 定向（面板回归） | `ThreadPanelsInteractionTest` + `ThreadPanelsScreenshotTest` + `SubagentHistoryInteractionTest` + `TerminalReadingInteractionTest` + `PtyTerminalPageInteractionTest` + `ThreadSummaryPanelInteractionTest` + `ThreadSummaryActivityPanelInteractionTest` + `HarnessAppThreadPanelsShellTest` | **53 found / 53 started / 52 passed / 1 failed**，exit 1（1 个真实 PTY 就绪 30s 超时，见下） | `logs/desktop-panels.log` |
| 该 PTY 类隔离重跑 | `PtyTerminalPageInteractionTest` | **7 found / 7 started / 7 passed / 0 failed**，exit 0 | `logs/pty-page-isolated.log` |
| 全量 jvm-app（本批门槛） | 全部 | **669 found / 5 skipped / 664 started / 659 passed / 5 failed**，exit 1（5 个超时抖动，见 §5.2） | `logs/full-jvm-app.txt` |
| 抖动重跑 | `WorktreeSessionObservationInteractionTest` + `WorktreeCreationInteractionTest` + `WorktreePinInteractionTest` + `PtyTerminalFontInteractionTest` | **13 found / 13 started / 12 passed / 1 failed**，exit 1（3 个 Worktree 类全过；仅 PTY 字体复现） | `logs/flake-rerun.log` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 模型（`ThreadSummaryActivityTest`，5）：非 `Ready`/空 overview → 无子 Agent 行；运行中优先 + 按 recency 排序 + 真实 label；label 回退；后台终端取命令首行、无命令回退「后台终端」、running 判定；空 id/label 拒绝。
- 控制器（`ThreadSummaryActivityControllerTest`，5）：未绑定/已关闭时 `refresh` 空操作；只读发布行；子 Agent 读取失败时仍保留进程行；重绑定时晚期 overview 被围栏丢弃；同 owner 不重绑、新 incarnation 复位。
- 岛交互（`ThreadSummaryActivityPanelInteractionTest`，2）：真实行渲染出两个小节（运行中 meta、count 数字、点击回报被选行）；空列表不画小节。
- 壳层（`HarnessAppThreadPanelsShellTest` 追加）：摘要 island 出现后，子智能体 count = 1 且存在 `thread-summary-subagent-<child>`，后台进程 count = 2 且存在 `thread-summary-process-<running>`/`-<exited>`。

### 5.2 全量失败归因（不全绿）

全量 `jvm-app` 5 个失败全部是**真实进程/PTY 的 Compose `waitUntil` 超时**，与 AE/AF/AM/AO/AQ/AT 一贯记录同类，非本批代码：

- `WorktreeSessionObservationInteractionTest.realRegistryFailureRetainsExpiredObservationAndRetrySelectsActualLinkedWorktree`（10s）
- `WorktreeCreationInteractionTest.relayDisablesLocalWorktreeCreationControlsWithoutCreatingDirectories`（10s）
- `WorktreePinInteractionTest.unknownUnavailableAndScopeChangeDisableAndHidePins`（10s）
- `WorktreePinInteractionTest.capturesWideCompactLightDarkRealPins`（10s）
- `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（30s）

隔离重跑（`flake-rerun.log`）：3 个 Worktree 类全通过，仅 `PtyTerminalFontInteractionTest` 复现 1 次 30s 超时（12/13）。本批改动只在摘要投影/岛 UI/壳层面板路径，不经 worktree 注册或 PTY 就绪。

### 5.3 定向 PTY 抖动

`logs/desktop-panels.log` 中 1 个失败为 `PtyTerminalPageInteractionTest.wideAndCompactLightDarkMappedGridRemainUsable`，栈指向 `PtyTerminalPageInteractionTest.awaitGrid` 的真实 PTY 输出等待 30s 超时。隔离重跑该类 **7/7 通过**（`logs/pty-page-isolated.log`，exit 0），确认是真实 PTY 负载抖动而非本批代码。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：子 Agent 行的 `diffStats` 徽标、`showInlineActivity` 去重、`autoCollapse`（原版在无活跃项且全部完成时的「{count} 完成」折叠）、后台终端的「停止所有后台终端」批量操作、摘要面板其它小节（Sources/created-tasks/plan/pull-requests/side-chats/triggers/environment-creation；chrome-tabs/computer-use 属排除范围）、子 Agent 行的精确图标字形。
- `captures/scheduled-execution/` 已按先例还原为批次前字节，但该字节早于当前 `ScheduledModels.kt` 的披露文案（见 §4），故还原后结构与该文案不一致；本批不据此改代码或改该目录。
- 各批证据独立，整体目标 active。

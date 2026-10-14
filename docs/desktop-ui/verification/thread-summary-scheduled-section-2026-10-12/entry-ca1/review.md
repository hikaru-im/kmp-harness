# Batch CA：线程摘要岛「定时任务」小节（2026-10-12）

## §1 本批目标

复刻计划第 6 项「Thread 摘要面板其余小节」里的「定时任务」小节：把摘要岛从原型的空壳推进到
**只读显示绑定到当前会话的定时任务**，每行给出真实 cadence，读取失败时给「重试加载已安排的任务」。
ASAR 依据（小节标题、计数、逐行 `M.ItemButton`、错误重试行、`compact-cadence` 的 `variant:"summary"`
token 集）见 `../../reverse-engineering/thread-summary-scheduled-section-contract-2026-10-12.md`，抄录见
`logs/asar-evidence.txt`。

## §2 生产改动

| 文件 | 行 | 改动 |
| --- | --- | --- |
| `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledTaskSummary.kt` | 111 | 新增 `scheduledSummaryCadence(task)`：`Paused`→「已暂停」；`Hourly`（`intervalHours==1`）→「每小时」，否则「每 N 小时」；`Daily`→「每天 · HH:MM」；`Weekdays`→「周一至周五」；`Weekly`→「每周一…每周日」。含私有 `compactClock`、`ScheduledWeekday.compactWeekdayLabel`。 |
| `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivity.kt` | 50-51 | 新增 `ThreadSummaryScheduledTitle`=「定时任务」、`ThreadSummaryScheduledRetryLabel`=「重试加载已安排的任务」。 |
| `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryModels.kt` | 16 | 新增纯函数 `threadSummaryScheduledTasks(tasks, sessionId)`：只保留 `ScheduledTarget.Heartbeat` 且 `sessionId` 相等者；`sessionId==null` 或 cron 目标返回空；保持快照顺序。 |
| `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryPanel.kt` | 62-69、132、145 | 新增参数 `scheduledTasks`/`scheduledLoading`/`scheduledError`/`onOpenScheduled`/`onRetryScheduled`；新增私有 `ScheduledSection`（加载且无缓存行→「正在读取定时任务…」；否则标题行 + 计数 `thread-summary-section-scheduled-count`，逐条 `SummaryRow`（`Clock`、`thread-summary-scheduled-<id>`、trailing=cadence），错误时追加 `Refresh` 重试行 `thread-summary-scheduled-retry`），仅在「有条目或加载中或出错」时渲染。 |
| `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt` | 106、707-710、1161-1171 | 由真实 `scheduledState.effective.tasks` + 当前 Thread 的 `SessionId` 过滤出 `threadScheduledTasks`；`ThreadSummaryPanel` 传入它、`scheduledState.phase` 派生的 `scheduledLoading`/`scheduledError`，以及 `onOpenScheduled`（`selectTask`+`selectPage(PageId.ScheduledTasks())`）与 `onRetryScheduled`（`scheduledRepository.load()`），回调仅在 `scheduledBindingReady` 时非 null。 |

设计边界：行只读、只投影真实存储；`onOpenScheduled`/`onRetryScheduled` 在绑定未就绪时为 null（不画死控件）。

## §3 测试改动

| 文件 | 改动 |
| --- | --- |
| `apps/shared/test/.../summary/ThreadSummaryModelsTest.kt` | 新增 `scheduledSectionKeepsOnlyTasksBoundToTheOpenConversation`（Heartbeat 绑定过滤、cron/null 返回空、顺序保持） |
| `apps/shared/test/.../scheduling/ScheduledTaskSummaryTest.kt` | 新增 `compactCadenceCoversPausedHourlyDailyWeekdayAndWeeklyTokens` |
| `apps/jvm-app/test/.../ThreadSummaryPanelInteractionTest.kt` | 新增 `scheduledSectionListsBoundCadencesAndOpensTheTask`、`scheduledSectionShowsTheReloadActionOnReadError`（标题断言用 `assertTextContains`，因小节是含计数的 merged 节点） |

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `-p jvm -m shared --include-classes=…ThreadSummaryModelsTest` | 8 found / 8 passed / 0 skipped / 0 failed（`logs/targeted-shared-thread-summary-models.txt`） |
| `-p jvm -m shared --include-classes=…ScheduledTaskSummaryTest` | 16 found / 16 passed / 0 skipped / 0 failed（`logs/targeted-shared-scheduled-task-summary.txt`） |
| `-p jvm -m shared`（全量） | 563 found / 563 passed / 0 skipped / 0 failed（`logs/shared-full.txt`，上批 561 增 2） |
| `-p jvm -m jvm-app --include-classes=…ThreadSummaryPanelInteractionTest` | 5 found / 5 passed / 0 failed（`logs/targeted-thread-summary-panel-interaction.txt`） |
| `-p jvm -m jvm-app`（全量） | 701 found / 5 skipped / 696 started / 695 passed / **1 failed**（`logs/full-jvm-app.txt`，上批 699 found 增 2） |
| `-p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.*PtyTerminal*Test'`（隔离重跑） | 14 found / 14 passed / 0 failed（`logs/pty-isolate-rerun.txt`） |

全量 `jvm-app` 的唯一失败是历史 PTY 字体抖动
`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`
（`ComposeTimeoutException: Condition still not satisfied after 30000 ms`）。该类族的隔离重跑 **14/14** 通过。
本批未改动终端/外观代码，判定为环境相关抖动（同族在 BW/BX/BZ 均出现过）。

## §5 截图产物核对与还原

全量运行重写了 `docs/desktop-ui/captures/`。与批次内快照逐文件 sha256 对比：仅 84 个文件字节变化，
分布在 `plugins-page-2026-10-08`（64）与 `scheduled-execution`（20）。前者是运行间非确定 id 抖动
（JSON 只差 `captureRootId`/`id`/`rootId`），后者与本批无关——它是**故意冻结的历史基线**（早于
`ScheduledModels.kt` 的 `ScheduledLocalExecutionModel` 披露文案，计划文档 2026-10-12 第 1379 行已记录），
披露文案自己的视觉在 `captures/scheduled-execution-model-2026-10-11/`。已把这 84 个文件还原为快照字节，
还原后 `diff -rq` 与快照**完全一致（0 行差异）**。本批未新增截图目录（摘要岛没有独立的截图用例，
只在 `ThreadSummaryPanelInteractionTest` 内做节点断言）。冻结基线组合 sha：
`scheduled-execution` = `ac8ebb54…`（与原记录一致）、`plugins-page-2026-10-08` = `7470d840…`
（`logs/captures-restore.txt`）。

## §6 边界（明确不主张）

- 计数是**绑定到该会话的定时任务数**，不是原版「已排程条目 + local automations」，也无 `showCollapsedCount` 折叠。
- 未实现原版的 `display_schedule` 透传，以及 `everyMinutes`/`monthly`/`weekends`/`monitoring`/`completed`、一次性 `date` 等 token（本仓 `ScheduledRule` 只有 `Hourly`/`Daily`/`Weekdays`/`Weekly`）。
- 行内图标用既有 `HarnessIconName.Clock`，不复刻原版字形；点击打开本仓「定时任务」页（无原版侧边面板）；重试重载整个存储（原版逐查询 `refetch`）；任何读取错误都显示重试（原版区分 404）。
- 未做原版同状态像素与原生窗口组合证据；本批是结构/文案对齐，不是像素一致。

## §7 复现

```sh
./kotlin test --build-dir=/tmp/harness-ca-build-20261012 -p jvm -m shared   --include-classes='im.hikaru.harness.client.summary.ThreadSummaryModelsTest'
./kotlin test --build-dir=/tmp/harness-ca-build-20261012 -p jvm -m shared   --include-classes='im.hikaru.harness.client.scheduling.ScheduledTaskSummaryTest'
./kotlin test --build-dir=/tmp/harness-ca-build-20261012 -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-ca-build-20261012 -p jvm -m jvm-app  --include-classes='im.hikaru.harness.desktop.ThreadSummaryPanelInteractionTest'
./kotlin test --build-dir=/tmp/harness-ca-build-20261012 -p jvm -m jvm-app
./kotlin test --build-dir=/tmp/harness-ca-build-20261012 -p jvm -m jvm-app  --include-classes='im.hikaru.harness.desktop.*PtyTerminal*Test'
```

# 定时任务详情「上次运行」时机行验收 BB

日期：2026-10-12（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-bb1`。

## 1. 本批目标与依据

BA 把定时任务列表行补成「节奏 + 下次运行」。原版**详情**页还有一条时机行：最近一次运行的时间（`automations.scheduled.detailRunTiming`）。本仓详情页此前只列运行历史卡片，没有这条「上次运行于 …」时机行。本批只实现**可观察的那一层**：由真实运行账本投影「上次运行于 …」，时间格式化放在平台日历适配器里；不发明时间、不主张未观察到的分支。完整边界见 `../../../reverse-engineering/scheduled-last-run-contract-2026-10-12.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- BB01 `webview/assets/update-content-bc3a4545935a.js`（函数 `A`）：取 `automation.last_run_time`；先算相对标签 `M(lastRun, now)`——非 null → `recent`（失败 `failedRecent`）；否则算日差 `D(now, lastRun)`——0/1 → `at`（今天/昨天 + 时钟），2..7 → `on`（星期几），>7 → `on`（含年份日期）；渲染 `automations.scheduled.detailRunTiming`，该 footer 调用 `nextKind=none`、`separator` 为空串。
- BB02 同文件（函数 `M`）：`delta<0` 或 `≥86400000` → null；`<3600000` → 分钟（`max(1, round(delta/60000))`）；否则 → 小时。
- BB03 同文件：日差分支的 今天/昨天、星期几、含年份日期三条形态。
- BB04 `webview/assets/zh-CN-b05cfd55c534.js`：`automations.scheduled.detailRunTiming`「上次运行于 {lastTime}」/「上次运行于 {lastDay} {lastTime}」、`automations.runs.lastRun`「上次运行」、`automations.editor.latestUpdate.lastRunFailed`「上次运行未成功」。

## 2. 写入集

生产（5）：

- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduleEngine.kt`（`ScheduleCalendar` 增带默认实现的 `relativeRunLabel` 与 `runDayLabel`）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledTaskSummary.kt`（新增 `ScheduledLastRunPrefix`、`lastRunInstant`、`scheduledLastRunLabel`）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementPage.kt`（页面与详情新增可选 `calendar`/`nowEpochMillis`；详情渲染 `scheduled-detail-last-run`）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（`scheduledNow` 上提到调度状态层并同时传给导航行与详情页）
- `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduleCalendar.kt`（用 `java.time` 实现 `relativeRunLabel`/`runDayLabel`）

测试（3）：

- `apps/shared/test/im/hikaru/harness/client/scheduling/ScheduledTaskSummaryTest.kt`（新增 6 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduleEngineTest.kt`（新增 2 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledManagementInteractionTest.kt`（新增 1 项）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/scheduled-last-run-contract-2026-10-12.md`
- 本文件

## 3. 实现要点

- **平台无关投影**：`lastRunInstant(runs)` = 最近终态证据的 `terminal.endedAt`，无终态时退化到最近一次运行的 `claimedAt`，无运行返回 null（不发明时间）；`scheduledLastRunLabel(lastRunAt, now, calendar)` = 相对分支（`relativeRunLabel`）优先，否则日分支（`runDayLabel`），无运行/无日历/无查看者时区/无法格式化 → null。
- **日历接口向后兼容**：`ScheduleCalendar` 的两处新增都带默认实现（返回 null），既有测试替身无需改动，语义即「不显示上次运行」。因此截图测试的日历替身（不实现这两个方法）不产生新行，`captures/scheduled-execution/` 内容不变。
- **页面接线**：`ScheduledManagementPage`/`ScheduledPageDetail` 新增可选 `calendar`/`nowEpochMillis`；详情页在「已保存配置 · {status}」下、仅当 label 非空时渲染 `scheduled-detail-last-run`。`HarnessApp` 把一次快照 `revision`/阶段重算的 `scheduledNow` 上提共用，详情页传 `calendar = scheduleCalendar`（不可用时 null）。
- **平台适配**：`DesktopScheduleCalendar` 用 `java.time`——`relativeRunLabel` 未来或 ≥1 天 → null，`<1h` → `max(1, round(分钟))` 分钟前，否则小时前；`runDayLabel` 日差 0/1 → 今天/昨天 `HH:mm`，2..7 → `星期X`，>7 → 含年份日期，捕获 `DateTimeException` → null。

## 4. 问题

- 本批只投影**可观察**字段：原版同一条消息里的 `nextKind`（`running`/`next`）在该 footer 调用被固定为 `none`，故本仓只做 lastKind；失败态外层「上次运行未成功」标签未实现（见 §6）。
- 「现在」非逐秒刷新：只在一次快照 `revision`/阶段变化时读一次时钟。
- `formatRelativeTime`/`formatDate(weekday:long)` 的精确本地化未逐字对齐；本仓用 `N 分钟前`/`N 小时前`/`星期X`。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **538 found / 0 skipped / 538 started / 538 passed / 0 failed**，exit 0 | `logs/shared.log` |
| shared 调度定向 | `im.hikaru.harness.client.scheduling.*` | **95 found / 0 skipped / 95 started / 95 passed / 0 failed**，exit 0（含新增 6） | `logs/shared-scheduling-targeted.log` |
| jvm-app 定向 | `DesktopScheduleEngineTest` + `ScheduledManagementInteractionTest` | **11 found / 0 skipped / 11 started / 11 passed / 0 failed**，exit 0（含新增 3） | `logs/jvm-app-targeted.log` |
| 全量 jvm-app（本批门槛） | 全部 | **686 found / 5 skipped / 681 started / 681 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 纯投影（`ScheduledTaskSummaryTest`，+6）：`lastRunUsesTheNewestTerminalOutcomeNotTheNewestClaim`；`lastRunFallsBackToTheNewestClaimWithoutTerminalEvidence`；`lastRunIsNullWithoutAnyRun`；`lastRunLabelPrefersTheRelativeBranchWithinADay`；`lastRunLabelFallsBackToTheDayBranch`；`lastRunLabelIsNullWithoutARunCalendarOrLabel`。
- 平台适配（`DesktopScheduleEngineTest`，+2）：`relativeRunLabelCoversMinutesHoursTheDayBoundaryAndTheFuture`（20 分钟 / 1 分钟 / 2 小时 / ≥1 天 null / 未来 null）；`runDayLabelNamesTodayYesterdayWeekdaysAndDatedYears`（今天/昨天 `HH:mm`、`星期四`、`2026年10月1日`、查看者时区决定日历日、坏时区 null）。
- 页面交互（`ScheduledManagementInteractionTest.detailShowsTheRealLastRunInstantFromTerminalEvidence`，+1）：用含真实终态证据（`Succeeded` + `endedAt`）的运行账本挂载详情页，渲染出「上次运行于 22 分钟前」。

### 5.2 全量结果

全量 `jvm-app` 为 **686 found / 5 skipped / 681 started / 681 passed / 0 failed**（exit 0），本批未命中历史 worktree/PTY/git-commit 时序抖动，无需隔离重跑。

### 5.3 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它（跑后为 `4f387960367fa746579d440583d0e5e2d54e802e`），随后按 AE/AU/AV/AW/AX/AY/AZ/BA 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。因截图测试的日历替身未实现本批新增方法（默认 null），截图内容本身未变。其余截图目录由截图测试按既有机制重写，与 BA 同处理方式，本批未单独备份。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 原版同一条时机行的 `nextKind` 分支（`正在运行`/`下次运行于 …`）、失败态外层「上次运行未成功」标签未实现；`last_run_time` 的写入时机未观察（本仓用真实运行账本投影）。
- 「现在」非逐秒刷新，跨天不会自动翻转；`formatRelativeTime`/`formatDate` 精确本地化未逐字对齐。

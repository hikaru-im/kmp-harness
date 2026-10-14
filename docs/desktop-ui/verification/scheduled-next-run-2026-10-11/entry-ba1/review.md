# 定时任务列表「节奏 + 下次运行」摘要验收 BA

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-ba1`。

## 1. 本批目标与依据

原版定时任务列表的每一行由**节奏**（下次运行单元格左侧/摘要里的 cadence）与**下一次运行/状态**两部分构成。本仓此前的任务行只有标题与简短状态，没把真实数据投影成原版那两段文案。本批只实现**可观察的那一层**：由现有真实数据（任务规则、持久 cursor、阻塞运行、任务状态）投影出行摘要，时间格式化放在平台日历适配器里；不发明时间、不主张未观察到的分支。完整边界见 `../../../reverse-engineering/scheduled-next-run-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- B01 `webview/assets/table-92b84c469d71.js`（`At`）：活动任务的「下次运行」单元格——空为 `—`；与今天同日 → `automations.scheduled.table.today`「今天 {time}」，与明天同日 → `tomorrow`「明天 {time}」，否则 `formatDate(month:short, day:numeric, year, hour, minute)`；非活动任务 → `automations.scheduled.table.inactive`「{paused 已暂停 / 其它 已完成}」；云端非 scheduled 任务 → `alwaysOn`「始终开启」。
- B02 同上（`jt`）：节奏单元格——`condition_watch` → `monitoring`「监测」；`webhook` → `events`；否则 `automations.scheduled.table.frequency`「{once 一次 / hourly 每小时 / daily 每天 / weekly 每周 / monthly 每月 / yearly 每年 / custom 自定义 / other —}」。
- B03 `webview/assets/zh-CN-b05cfd55c534.js`：`automations.scheduled.table.nextRun`「下次运行」、`taskSummaryWithMinutesAndRunStatus`（`正在运行` / `下次运行：{time}` / `上次运行：{time}` 等 timing 分支）、`alwaysOn`「始终开启」。

## 2. 写入集

生产（5，均新增/改为未跟踪改动）：

- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduleEngine.kt`（`ScheduleCalendar` 增带默认实现的 `displayZoneId` 与 `nextRunLabel(next, now, zoneId): String?`）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledTaskSummary.kt`（新增纯投影）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt`（新可选参数 `calendar`/`nowEpochMillis`，行 `description` 改用摘要）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（传 `scheduleCalendar` 与随快照重算的 `now`）
- `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduleCalendar.kt`（用 `java.time` 实现 `displayZoneId`/`nextRunLabel`）

测试（3）：

- `apps/shared/test/im/hikaru/harness/client/scheduling/ScheduledTaskSummaryTest.kt`（新增 6 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduleEngineTest.kt`（新增 2 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledManagementInteractionTest.kt`（新增 1 项）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/scheduled-next-run-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- **平台无关投影**：新增 `ScheduledTaskSummary.kt`——`ScheduledRule.cadenceLabel()`（每小时/每天/工作日/每周）、`ScheduledRule.zoneIdOrNull()`（固定时长 Hourly 无时区返回 null）、常量 `ScheduledPausedLabel`「已暂停」与 `ScheduledRunningLabel`「正在运行」；`scheduledTaskSummary(task, cursor, running, now, calendar)`：暂停 →「已暂停」；活动且有阻塞运行 →「节奏 · 正在运行」；否则「节奏 · 下次运行：<平台标签>」；无 cursor / 无日历 / 无法格式化时退化为「节奏」。
- **日历接口向后兼容**：`ScheduleCalendar` 的两处新增都带默认实现（`displayZoneId` 空串、`nextRunLabel` 返回 null），因此既有测试替身无需改动，语义即「只显示节奏」，与原版「无查看者时区时退化」一致。
- **页面接线**：`ScheduledManagementNavigator` 只在提供 `calendar`/`nowEpochMillis` 时补出下次运行标签；`HarnessApp` 传入 `scheduleCalendar`（不可用时 null）与一个随调度快照 `revision`/阶段重算的 `now`（读一次系统时钟）。
- **平台适配**：`DesktopScheduleCalendar` 用 `java.time` 实现——`displayZoneId` 取系统默认时区；`nextRunLabel` 按规则时区渲染「今天 HH:mm」/「明天 HH:mm」/「M月d日 HH:mm」/含年份的日期时间，捕获 `DateTimeException` → null。

## 4. 问题

- 本批只投影**可观察**字段：`lastRun`、`starting`/`executing` 运行中动画、事件/条件型「监测」「始终开启」、云端 schedule 形态**未实现**（见 §6）。
- 「现在」不是逐秒刷新：只在一次快照 `revision`/阶段变化时读一次时钟，跨天不会自动从「今天」翻到「明天」；这是有意的性能取舍。
- `formatDate` 的精确本地化与年份省略规则未逐字对齐；本仓用中文 `M月d日`、同年省略年份。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **532 found / 0 skipped / 532 started / 532 passed / 0 failed**，exit 0 | `logs/shared.log` |
| shared 调度定向 | `im.hikaru.harness.client.scheduling.*` | **89 found / 0 skipped / 89 started / 89 passed / 0 failed**，exit 0（含新增 6） | `logs/shared-scheduling-targeted.log` |
| jvm-app 定向 | `DesktopScheduleEngineTest` + `ScheduledManagementInteractionTest` | **8 found / 0 skipped / 8 started / 8 passed / 0 failed**，exit 0（含新增 3） | `logs/jvm-app-targeted.log` |
| 全量 jvm-app（本批门槛） | 全部 | **683 found / 5 skipped / 678 started / 678 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 纯投影（`ScheduledTaskSummaryTest`，+6）：`pausedTaskShowsOnlyThePausedState`；`activeTaskUsesCadenceAndTheDurableNextInstantInTheRuleZone`；`hourlyRulesFallBackToTheViewerZoneForTheLabel`；`blockingRunWinsOverTheNextInstant`；`missingCalendarOrCursorDegradesToCadenceInsteadOfInventingATime`；`cursorForADifferentTaskIsIgnored`。
- 平台适配（`DesktopScheduleEngineTest`，+2）：`nextRunLabelUsesTheRuleZoneAndNamesTodayTomorrowAndDates`（断言今天/明天/M月d日分支）；`displayZoneIsNotBlankOnTheDesktopAdapter`。
- 页面交互（`ScheduledManagementInteractionTest.navigatorRowShowsTheRealCadenceAndDurableNextRun`，+1）：导航行 `description` 同时含真实节奏与由持久 cursor 派生的下次运行标签。

### 5.2 全量结果

全量 `jvm-app` 为 **683 found / 5 skipped / 678 started / 678 passed / 0 failed**（exit 0），本批未命中历史 worktree/PTY/git-commit 时序抖动，无需隔离重跑。

### 5.3 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它（跑后为 `4f387960367fa746579d440583d0e5e2d54e802e`），随后按 AE/AU/AV/AW/AX/AY/AZ 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。其余截图目录由截图测试按既有机制重写，与 AZ 同处理方式，本批未单独备份。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- `lastRun`（上次运行）、`starting`/`executing` 运行中动画、事件/条件型「监测」「始终开启」、云端 schedule 形态均未实现；本仓只有本机 Cron/Heartbeat。
- 「现在」非逐秒刷新，跨天不会自动翻转；`formatDate` 精确本地化格式未逐字对齐。

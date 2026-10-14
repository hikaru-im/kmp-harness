# 定时任务列表「节奏 + 下次运行」摘要：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。
原始片段见 `verification/scheduled-next-run-2026-10-11/entry-ba1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| B01 | `webview/assets/table-92b84c469d71.js`（`At`） | 活动任务的「下次运行」单元格：`kt(row)` 取下次运行瞬间（本地任务 `status===ACTIVE` 时由 `Ce(automation)` 计算）；为空显示 `—`；与今天同日 → `automations.scheduled.table.today`「今天 {time}」，与明天同日 → `tomorrow`「明天 {time}」，否则 `formatDate(month:short, day:numeric, year:…, hour, minute)`；非活动任务 → `automations.scheduled.table.inactive`「{paused 已暂停 / 其它 已完成}」 |
| B02 | 同上（`jt`） | 节奏单元格：condition_watch → `monitoring`「监测」；webhook → `events`；否则 `automations.scheduled.table.frequency`「{once 一次 / hourly 每小时 / daily 每天 / weekly 每周 / monthly 每月 / yearly 每年 / custom 自定义 / other —}」 |
| B03 | `webview/assets/zh-CN-b05cfd55c534.js` | `automations.scheduled.table.nextRun`「下次运行」、`taskSummaryWithMinutesAndRunStatus` 的 timing 分支（`正在开始运行…` / `正在运行` / `下次运行：{time}` / `上次运行：{time}` / `正在运行` / `已安排`）、`alwaysOn`「始终开启」 |

## 2. 已观察到与未观察到的语义

- **已观察**：列表行由**节奏**与**下次运行/状态**两部分组成；下次运行取自动化的 schedule 下次瞬间，按**查看者时区**渲染成「今天 HH:mm」/「明天 HH:mm」/ 日期时间；非活动任务只显示「已暂停」/「已完成」；事件/条件型显示「监测」「始终开启」。
- **未观察到**：`Ce(automation)` 的具体实现（本仓已有等价的 `ScheduleEngine.nextOccurrence` 与持久 `ScheduledCursor.nextNominalAt`）；`formatDate` 的精确占位符与本地化（本仓用 `M月d日` 中文）；原版 `last-run`/`starting`/`executing` 分支的完整触发时机；原版同状态像素。

因此本批只实现**可观察的那一层**：由现有真实数据（任务规则、持久 cursor、阻塞运行、任务状态）投影出行摘要，时间格式化放在平台日历适配器里。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `scheduling/ScheduleEngine.kt`（shared） | `ScheduleCalendar` 新增带默认实现的 `displayZoneId`（默认空串，即「无查看者时区」）与 `nextRunLabel(next, now, zoneId): String?`（默认 null，即「平台无法格式化」）。默认实现让既有测试替身无需改动即为「只显示节奏」 |
| `scheduling/ScheduledTaskSummary.kt`（shared，新增） | 纯投影：`ScheduledRule.cadenceLabel()`（每小时/每天/工作日/每周）、`ScheduledRule.zoneIdOrNull()`、常量 `ScheduledPausedLabel`「已暂停」与 `ScheduledRunningLabel`「正在运行」、`scheduledTaskSummary(task, cursor, running, now, calendar)`：暂停→「已暂停」；活动且有阻塞运行→「节奏 · 正在运行」；否则「节奏 · 下次运行：<平台标签>」，无 cursor/无日历/无法格式化时退化为「节奏」 |
| `scheduling/ScheduledManagementNavigator.kt`（shared） | 新增可选参数 `calendar`/`nowEpochMillis`；任务行 `description` 改为上面的摘要 |
| `app/HarnessApp.kt`（shared） | 传入 `scheduleCalendar`（不可用时为 null → 只显示节奏）与一个随快照 `revision`/阶段重算的 `now`（读一次系统时钟） |
| `jvm-app/.../scheduling/DesktopScheduleCalendar.kt` | 用 `java.time` 实现 `displayZoneId`（系统默认时区）与 `nextRunLabel`（今天/明天/M月d日/含年份，`HH:mm`） |

### 3.1 未实现（明确不主张）

- 原版 `lastRun`、`starting`/`executing` 运行中动画、事件/条件型「监测」「始终开启」、云端任务的 schedule 形态（本仓只有本机 Cron/Heartbeat）。
- 逐秒刷新的「现在」（本仓在一次快照 revision/阶段变化时读一次时钟；跨天不会自动从「今天」翻到「明天」）。
- 原版逐列排序（`next_run_asc` 等）、所有者/团队列、筛选。
- `formatDate` 的精确本地化格式与年份省略规则；本仓用中文 `M月d日`，同年省略年份。
- 原版同尺寸像素、原生窗口；本批只有生产投影/适配器/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/scheduled-next-run-2026-10-11/entry-ba1/review.md`。各批证据独立，不累加为总体覆盖率。

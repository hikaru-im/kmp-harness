# 定时任务详情「上次运行」时机行：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。
原始片段见 `verification/scheduled-last-run-2026-10-12/entry-bb1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| BB01 | `webview/assets/update-content-bc3a4545935a.js`（函数 `A`，导出 `a`） | 详情「上次运行」行：取 `automation.last_run_time`；显示时 `showLastRun`（默认真）。先算相对标签 `M(lastRun, now)`：非 null → `recent`（失败时 `failedRecent`）；否则算日差 `D(now, lastRun)`：0/1 → `at`（今天/昨天 + 时钟），2..7 → `on`（星期几），>7 → `on`（含年份日期）。渲染用 `automations.scheduled.detailRunTiming`，其中 `nextKind` 在该 footer 调用固定为 `none`、`separator` 为空串 |
| BB02 | 同文件（函数 `M`） | 相对阈值：`delta < 0`（未来）或 `delta >= 86400000`（≥1 天）→ null；`delta < 3600000` → 分钟（`max(1, round(delta/60000))`）；否则 → 小时（`max(1, round(delta/3600000))`） |
| BB03 | 同文件（函数 `A` 内 `D`） | 日差分支：0/1 → `at`，lastDay = 今天/昨天（`formatRelativeTime(-t,'day',{numeric:'auto'})`）+ 时钟 `HH:mm`；2..7 → `on`，星期几（`weekday:long`）；>7 → `on`，`month:short,day:numeric,year:numeric` |
| BB04 | `webview/assets/zh-CN-b05cfd55c534.js` | `automations.scheduled.detailRunTiming`「上次运行于 {lastTime}」/「上次运行于 {lastDay} {lastTime}」、`automations.runs.lastRun`「上次运行」、`automations.editor.latestUpdate.lastRunFailed`「上次运行未成功」 |

## 2. 已观察到与未观察到的语义

- **已观察**：详情页在「上次运行」行显示**单条**时机——最近一次运行在一天内用相对时间（分钟/小时），否则用「今天/昨天 + 时钟」「星期几」「含年份日期」。
- **未观察到**：`automation.last_run_time` 的写入时机（本仓用真实运行账本投影）；原版的失败态（`failedRecent` 只渲染相对时间，外层「上次运行未成功」来自编辑器上下文）；原版 `nextKind`（`running`/`next`）在同一条消息里的其它调用点（本 footer 固定 `none`）。

因此本批只实现**可观察的那一层**：由真实运行账本（最近终态证据的 `endedAt`，退化到最近一次运行的 `claimedAt`）投影「上次运行于 …」，时间格式化放在平台日历适配器里。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `scheduling/ScheduleEngine.kt`（shared） | `ScheduleCalendar` 新增带默认实现的 `relativeRunLabel(run, now, zoneId)`（默认 null）与 `runDayLabel(run, now, zoneId)`（默认 null）；默认实现让既有测试替身无需改动即「不显示上次运行」 |
| `scheduling/ScheduledTaskSummary.kt`（shared） | 新增 `ScheduledLastRunPrefix`（「上次运行于 」）、`lastRunInstant(runs)`（最近终态 `endedAt`，否则最近 `claimedAt`，无运行返回 null）、`scheduledLastRunLabel(lastRunAt, now, calendar)`（相对分支优先、否则日分支，无运行/无日历/无时区/无法格式化 → null） |
| `scheduling/ScheduledManagementPage.kt`（shared） | `ScheduledManagementPage` 与 `ScheduledPageDetail` 新增可选 `calendar`/`nowEpochMillis`；详情页在「已保存配置 · {status}」下渲染 `scheduled-detail-last-run` 行（label 非空时才渲染） |
| `app/HarnessApp.kt`（shared） | 把一次快照 `revision`/阶段重算的 `scheduledNow` 提升到调度状态层，同时传给导航行与详情页；详情页传入 `calendar = scheduleCalendar`（不可用时 null） |
| `jvm-app/.../scheduling/DesktopScheduleCalendar.kt` | 用 `java.time` 实现 `relativeRunLabel`（分钟/小时，`max(1, round(...))`，未来或 ≥1 天 → null）与 `runDayLabel`（今天/昨天 + 时钟、星期几、不含年份省略规则的含年份日期） |

### 3.1 未实现（明确不主张）

- 原版 `nextKind`（`running`/`next`）在同一时机行内的组合（本 footer 固定 `none`）；失败态外层「上次运行未成功」标签。
- 原版逐秒刷新的「现在」（本仓在一次快照 `revision`/阶段变化时读一次时钟）。
- 原版 `formatRelativeTime` 的精确本地化（本仓用 `N 分钟前`/`N 小时前`）；`formatDate(weekday:long)` 的精确本地化（本仓用 `星期X`）。
- 原版同尺寸像素、原生窗口；本批只有生产投影/适配器/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/scheduled-last-run-2026-10-12/entry-bb1/review.md`。各批证据独立，不累加为总体覆盖率。

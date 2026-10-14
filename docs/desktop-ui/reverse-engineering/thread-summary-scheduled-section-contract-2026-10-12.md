# 线程摘要岛「定时任务」小节：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动任何原包、未联网。抄录见
`../verification/thread-summary-scheduled-section-2026-10-12/entry-ca1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | 文案：`automations.scheduled.destination.label` =「定时任务」（英文 `Scheduled`，同时是「Scheduled 目的地」和小节标题）、`chatgptConversations.summaryPanel.scheduled.retry` =「重试加载已安排的任务」（英文 `Retry loading scheduled tasks`）、`settings.automations.rruleSummaryFallback` =「自定义安排」。 |
| E02 | `summary-section-258c26458664.js` | 摘要岛组件按 `kind` 渲染两个小节：`goal`（标题 `chatgptConversations.summaryPanel.goal.title`）与 `scheduled`。`scheduled` 小节用 `M.Section`，`title` 包在 `automations.scheduled.destination.label` 里，`titleSuffix` 为 `M.SectionCount{count:s.length+(scheduled?localAutomations.length:0)}`，且仅在 `showCollapsedCount` 为真时给出计数。整段在「无条目、无 local automations、无 loading、无 error」时用 early-return 哨兵整体不渲染。 |
| E03 | 同上 | 每条已排程项是 `M.ItemButton`：`M.ItemLeading`（图标 `b()`）、`M.ItemLabel`（任务名 `w(automation)`）、`M.ItemMeta`（`I` = compact-cadence 组件，`variant:"summary"`）；点击 `k({scope, automationId, title, navigationSource:"conversation"})`。读取失败（`isError` 且**非 404**）时追加一个 `M.ItemButton`，`ItemLabel` 为 `chatgptConversations.summaryPanel.scheduled.retry`，点击执行 `Promise.all(errors.map(refetch))`。 |
| E04 | `compact-cadence-c1690916d81f.js` | `variant:"summary"` 时：若 `display_schedule` 有值直接渲染；否则按 `token`（`T`）派生文案。token 与规则：paused → `paused`；hourly 且 `intervalHours===1` → `hourly`，否则 `everyHours`（带 `minutes`/`hours` 值）；daily 且 `by_hour`/`by_minute` 均为 0–59 整数 → `dailyTime`；weekly 的 `by_day` 集合 size===5 且恰为 MO, TU, WE, TH, FR → `weekdays`，size===2 且为 SA/SU → `weekends`，size===1 → 对应单 weekday token（`MO`…`SU`），其余 → `weekly`。当 `variant==="summary"` 且 token ∈ {`dailyTime`, `weekdayTime`} 时改用 `automations.scheduled.summary.compactCadenceWithTime`（`dailyTime {每天 · {time}}`），否则用 `automations.scheduled.editor.compactCadenceWithMinutes`。 |
| E05 | `zh-CN-b05cfd55c534.js` | 上表的 zh-CN token 值：`paused`=已暂停、`hourly`=每小时、`everyHours`=每 {hours} 小时、`daily`=每天、`dailyTime`=每天 {time}（summary 变体为「每天 · {time}」）、`weekdays`=周一至周五、`MO`…`SU`=每周一…每周日。 |

## 2. 已观察到与未观察到的语义

- **已观察**：摘要岛有一个标题为「定时任务」的小节，标题可带计数后缀；每行是「图标 + 任务名 + compact cadence 元信息」，点击打开该自动化；cadence 文案对暂停、每小时、每 N 小时、每天 · HH:MM、固定工作周、单个星期几各有专门 token；读取失败（非 404）时该小节追加一个「重试加载已安排的任务」行，点击重新拉取所有失败查询；没有条目也没有错误时该小节整体不出现。
- **未观察到**：小节里「已排程条目」与「local automations」两类数据的实际来源与去重、`showCollapsedCount` 的触发条件、行内图标的具体字形（`b()`）、点击后在原版是打开侧边面板还是跳转、`M.Item*` 的精确几何与像素，以及「有错误但缓存仍有条目」时行与重试行是否并存。

因此本批只实现**可观察且与本仓数据相称的那一层**：一个只读的「定时任务」小节，列出**绑定到当前会话**的定时任务，逐行显示真实 cadence，读取失败时给出重试行。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `ScheduledTaskSummary.kt`（shared，第 111 行） | 新增纯函数 `scheduledSummaryCadence(task)`：`Paused` → 已暂停；`Hourly` 且 `intervalHours==1` → 每小时，否则「每 N 小时」；`Daily` → 「每天 · HH:MM」；`Weekdays` → 周一至周五；`Weekly` → 每周一…每周日。时间用 `HH:MM`（对齐 zh-CN 的 `formatTime({hour:'numeric',minute:'2-digit'})`）。 |
| `ThreadSummaryActivity.kt`（shared，第 50-51 行） | 新增常量 `ThreadSummaryScheduledTitle` =「定时任务」、`ThreadSummaryScheduledRetryLabel` =「重试加载已安排的任务」。 |
| `ThreadSummaryModels.kt`（shared，第 16 行） | 新增纯函数 `threadSummaryScheduledTasks(tasks, sessionId)`：只保留 `ScheduledTarget.Heartbeat` 且 `sessionId` 相等者；`sessionId==null` 或 cron 目标返回空；保持快照顺序。 |
| `ThreadSummaryPanel.kt`（shared，第 62-69、132、145 行） | 新增参数 `scheduledTasks`/`scheduledLoading`/`scheduledError`/`onOpenScheduled`/`onRetryScheduled`；新增私有 `ScheduledSection`：加载且无缓存行时显示「正在读取定时任务…」；否则渲染标题行（计数 `thread-summary-section-scheduled-count`）与逐条 `SummaryRow`（`Clock` 图标、`testTag=thread-summary-scheduled-<id>`、trailing 为 `scheduledSummaryCadence`），错误时追加 `Refresh` 图标的重试行（`thread-summary-scheduled-retry`）；小节仅在「有条目或加载中或出错」时出现。 |
| `HarnessApp.kt`（shared，第 707、1161-1171 行） | `threadScheduledTasks` 由真实 `scheduledState.effective.tasks` 与当前 Thread 的 `SessionId` 过滤得到；`ThreadSummaryPanel` 传入它、`scheduledState.phase` 派生的 `scheduledLoading`/`scheduledError`，以及 `onOpenScheduled`（`scheduledRepository.selectTask(id)` + `selectPage(PageId.ScheduledTasks())`，仅绑定就绪时非 null）、`onRetryScheduled`（`scheduledRepository.load()`）。 |

### 3.1 未实现（明确不主张）

- **计数语义**：原版计数是「已排程条目 + local automations」，本仓只有绑定到该会话的定时任务，无 local automations 概念，计数即绑定任务数，且不区分原版的 `showCollapsedCount`。
- **`display_schedule` 透传**：原版若有 `display_schedule` 直接渲染；本仓无该字段。
- **未覆盖的 token**：本仓 `ScheduledRule` 只有 `Hourly`/`Daily`/`Weekdays`/`Weekly`，没有 `everyMinutes`/`monthly`/`weekends`/`monitoring`/`completed`、没有一次性 `date`，这些 token 不实现。
- **图标**：使用既有 `HarnessIconName.Clock`，不复刻原版 `b()` 的具体字形。
- **点击目标**：原版点击打开该自动化的侧边面板；本仓打开既有「定时任务」页并选中该任务，没有侧边面板。
- **重试粒度**：原版对每个失败查询单独 `refetch`；本仓 `scheduledRepository.load()` 重载整个定时任务存储。
- **错误判定**：原版区分 404（不显示重试），本仓任何读取错误都显示重试行。
- **回退文案**：`settings.automations.rruleSummaryFallback`（自定义安排）不使用——本仓是强类型规则，不解析任意 RRULE。
- 原版同状态像素与原生窗口证据。

## 4. 验收

见 `../verification/thread-summary-scheduled-section-2026-10-12/entry-ca1/review.md`。各批证据独立，不累加为总体覆盖率。

# 定时任务写入时间戳：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。
原始片段见 `verification/scheduled-task-timestamp-2026-10-12/entry-be1/logs/asar-evidence.txt`。

上一批 BD 实现了原版「筛选和排序任务」菜单里的三种排序，但原版菜单还有一项 `updated_at_desc`（最近更新），
详情页另有一行写入时间戳；两者都依赖任务的更新时间戳，本仓模型此前没有该字段。证据 BE01–BE04：

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| BE01 | `webview/assets/table-92b84c469d71.js`（函数 `_t` 的比较器） | 排序比较器按 `n.sort` 分支，`updated_at_desc` 用 `t.updatedAt - e.updatedAt`（降序，最近更新在前）；`next_run_asc` 用下次运行时间；默认在非搜索时用 `createdAt` |
| BE02 | `webview/assets/zh-CN-b05cfd55c534.js` | `automations.scheduled.table.sort`「排序方式」、`table.sortOption`「{automatic 默认 / next_run_asc 下次运行 / updated_at_desc 最近更新 / title_asc 名称 / other 相关性}」 |
| BE03 | `webview/assets/zh-CN-b05cfd55c534.js` | `automations.scheduled.detailUpdateTimestampWithLabel` = `{showLastUpdateLabel, select, yes {上次更新 · } other {}}{recent, select, yes {{relativeTime}} other {{date}{includeAt, select, yes { 于 } other { }}{time}}}` |
| BE04 | `webview/assets/update-content-bc3a4545935a.js`（函数 `j`） | `showLastUpdateLabel = a == null ? 'no' : 'yes'`，其中 `a` 是相对时间标签：**只有 recent（约一天内）分支带 `上次更新 · ` 前缀**，否则渲染 `date`（`includeAt` 决定 ` 于 ` 还是空格）与 `time`，无前缀 |

## 2. 已观察到与未观察到的语义

- **已观察**：任务带一个可排序的 `updatedAt`（原版来自服务端的 `automation.updatedAt`）；排序项 `updated_at_desc` 按它降序；详情页在原版时间戳区域按「相对 / 日期」两分支渲染，前缀 `上次更新 · ` 只出现在相对分支。
- **未观察到 / 不纳入**：原版时间戳由远端服务写入（我们只有本地存储）；原版 `relevance`（相关性，搜索时评分）、`createdAt` 默认序、owner 筛选属远端/团队范围，均不实现；原版相对/绝对文案的分支阈值与 `includeAt` 细节只按可观察结构近似（见 §3.1）。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `scheduling/ScheduledModels.kt`（shared） | `ScheduledTask` 增加可选 `updatedAt: Long? = null`（可空：旧文档与旧任务读为 null，不伪造时间）；`ScheduledTaskSort` 增加 `Updated`（原版 `updated_at_desc`） |
| `scheduling/ScheduledRepository.kt`（shared） | 构造函数新增 `clock: ScheduledClock = SystemScheduledClock`；新建/编辑保存时写入 `updatedAt = clock.nowEpochMillis()`；无执行器时的持久暂停同样写入；`beginCreate` 的 hourly 锚点改用同一 clock |
| `scheduling/ScheduledExecutor.kt`（shared） | `activate` 变更任务状态时一并写入 `updatedAt = clock.nowEpochMillis()`（有执行器时的启停路径） |
| `scheduling/ScheduledValidator.kt`（shared） | `structuralError` 增加 `updatedAt != null && updatedAt < 0` 非法校验 |
| `scheduling/ScheduledTaskSummary.kt`（shared） | 新增 `ScheduledLastUpdatedPrefix`（`上次更新 · `）与 `scheduledLastUpdatedLabel`（相对分支加前缀，否则只返回日期标签） |
| `scheduling/ScheduledManagementNavigator.kt`（shared） | 排序面板增加「最近更新」选项（tag `scheduled-sort-updated`），列表按 `updatedAt` 降序、无时间戳的最后 |
| `scheduling/ScheduledManagementPage.kt`（shared） | 详情页在配置行下渲染 `scheduled-detail-updated`（有 `updatedAt` 且有日历标签时） |
| `desktop/scheduling/DesktopScheduledStore.kt`（jvm-app） | 文档 `schemaVersion` 升到 3，读取接受 1–3；提交时统一写当前版本；v1 仍按旧严格形状解码，v2/v3 共用一个形状（v3 只多一个可选 `updatedAt`） |

### 3.1 未实现（明确不主张）

- 原版时间戳来自远端服务；本仓是本地写入时钟，写用户偏好/远端同步不涉及。
- 原版 `relevance`（相关性）与 `createdAt` 默认序、owner 筛选：属远端/团队范围，不实现。
- 原版非相对分支的 `{date}` 与 `{time}` 是分开的字段，`includeAt` 决定「 于 」或空格；本仓复用上次运行同一套日期标签（`runDayLabel`），不逐字复刻该拼接。
- 原版时间戳区域的精确几何/像素与原生窗口未验收。

## 4. 验收

见 `verification/scheduled-task-timestamp-2026-10-12/entry-be1/review.md`。各批证据独立，不累加为总体覆盖率。

# 定时任务导航器排序：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。
原始片段见 `verification/scheduled-sort-2026-10-12/entry-bd1/logs/asar-evidence.txt`。

BC 实现了原版「筛选和排序任务」菜单的状态筛选；同一菜单里还有排序子菜单。证据 BD01–BD02：

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| BD01 | `webview/assets/table-92b84c469d71.js`（函数 `_t`） | 排序与状态筛选在同一菜单、用 `Separator` 分隔；子菜单标题 `automations.scheduled.table.sort`（「排序方式」），RadioGroup value = `n.sort ?? 'automatic'`；选项顺序 `['automatic','next_run_asc','updated_at_desc','title_asc', ...(搜索时追加 'relevance')]`；`automatic` 在 onSelect 写回 `undefined`；描述明确 Default 在浏览时把 active 排在 paused/completed 之前，搜索时用相关性排序 |
| BD02 | `webview/assets/zh-CN-b05cfd55c534.js` | `table.sort`「排序方式」、`table.sortOption`「{automatic 默认 / next_run_asc 下次运行 / updated_at_desc 最近更新 / title_asc 名称 / other 相关性}」 |

## 2. 已观察到与未观察到的语义

- **已观察**：排序是同一筛选菜单里的一个子项，选项为 默认/下次运行/最近更新/名称（+搜索时的相关性）；默认（`automatic`）在浏览时把 active 排在 paused 之前。
- **未观察到 / 不纳入**：`updated_at_desc`（最近更新）需要任务更新时间戳，本仓模型暂无该字段，故不实现；`relevance`（相关性）只在搜索时出现、依赖远端检索评分，本仓没有该评分，故不实现。

因此本批只实现**可观察的那一层**：默认（active 先于 paused，组内稳定）、下次运行（按持久 cursor 的下次瞬间升序，无 cursor 的最后）、名称。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `scheduling/ScheduledModels.kt`（shared） | 新增 `ScheduledTaskSort { Automatic, NextRun, Title }` |
| `scheduling/ScheduledManagementNavigator.kt`（shared） | 新增可选 `sort`/`onSortChange`；筛选面板在原有状态行下增加「排序方式」行（默认/下次运行/名称）；列表按所选排序；筛选按钮在只传 `onSortChange` 时也会出现 |
| `app/HarnessApp.kt`（shared） | 新增 `scheduledSort` UI 状态并透传导航器 |

### 3.1 未实现（明确不主张）

- 原版 `updated_at_desc`（最近更新）与搜索时的 `relevance`（相关性）排序。
- 原版排序保存到用户偏好（本仓是每次进入页面的 UI 状态，与 BC 的状态筛选一致）。
- 原版菜单的精确弹出层/键盘导航与像素；本仓用导航器内联选项行。

## 4. 验收

见 `verification/scheduled-sort-2026-10-12/entry-bd1/review.md`。各批证据独立，不累加为总体覆盖率。

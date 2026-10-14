# 定时任务导航器状态筛选：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。
原始片段见 `verification/scheduled-status-filter-2026-10-12/entry-bc1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| BC01 | `webview/assets/table-92b84c469d71.js`（函数 `_t`） | 筛选入口是 `variant:'ghost'` 的图标按钮，`aria-label` = `automations.scheduled.table.filters`（「筛选和排序任务」），图标按 16/20px 绘制；菜单含 `SectionLabel` `table.filterBy`（「筛选条件」）与子项 `table.status`（「状态」），用 RadioItem 呈现 `['all','active','paused','finished']`；默认 `n.status ?? 'active'`（默认只看「已开启」）；另有 owner/sort 子菜单（团队/云端范围） |
| BC02 | `webview/assets/zh-CN-b05cfd55c534.js` | `table.filters`「筛选和排序任务」、`table.filterBy`「筛选条件」、`table.status`「状态」、`table.statusOption`「{active 已开启 / paused 已暂停 / finished 已完成 / other 全部}」、`table.empty`「没有符合筛选条件的任务」 |

## 2. 已观察到与未观察到的语义

- **已观察**：状态筛选是一个图标按钮 + 选项列表；选项值为 全部/已开启/已暂停/已完成；空结果文案为「没有符合筛选条件的任务」；默认选中「已开启」。
- **未观察到 / 不纳入**：owner（所有者）筛选与排序（`table.sort`/`sortOption`）属团队/云端范围，本仓不实现；`finished`（已完成）态在本仓模型里不存在（只有 Active/Paused）。

因此本批只实现**可观察的那一层**：由真实任务状态（`ScheduledTask.status`）过滤列表，选项取 全部/已启用/已暂停。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `ui-foundation/HarnessIcons.kt` | 新增 `HarnessIconName.Filter`（三条递减横杠 `filter_list`）与对应矢量绘制分支 |
| `scheduling/ScheduledManagementNavigator.kt`（shared） | 新增可选 `statusFilter`/`onStatusFilterChange`；标题行增加筛选图标按钮（仅当传入回调时渲染）；展开后一行「全部/已启用/已暂停」选项；列表按 query 与状态组合过滤；空结果文案改为 `automations.scheduled.table.empty`「没有符合筛选条件的任务」 |
| `app/HarnessApp.kt`（shared） | 新增 `scheduledStatusFilter` UI 状态并透传导航器 |

### 3.1 未实现（明确不主张）

- 原版筛选菜单里的 owner（所有者）筛选与排序子菜单（团队/云端范围）。
- 原版默认选中「已开启」（本仓默认「全部」，与既有导航器行为一致）。
- 原版筛选控件的精确几何/像素与原生窗口；本仓只做结构和本地状态。

## 4. 验收

见 `verification/scheduled-status-filter-2026-10-12/entry-bc1/review.md`。各批证据独立，不累加为总体覆盖率。

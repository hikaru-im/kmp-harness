# 定时任务导航器状态筛选验收 BC

日期：2026-10-12（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-bc1`。

## 1. 本批目标与依据

原版定时任务导航器有一个「筛选和排序任务」入口，其中「状态」子项可把列表收敛到 全部/已开启/已暂停/已完成。本仓导航器此前只有搜索，没有状态筛选。本批只实现**可观察的那一层**：按真实任务状态（`ScheduledTask.status`）过滤列表，选项取 全部/已启用/已暂停；owner 筛选与排序属团队/云端范围、`finished` 态在本仓模型里不存在，均不实现。完整边界见 `../../../reverse-engineering/scheduled-status-filter-contract-2026-10-12.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- BC01 `webview/assets/table-92b84c469d71.js`（函数 `_t`）：筛选入口是 `variant:'ghost'` 的图标按钮，`aria-label` = `automations.scheduled.table.filters`「筛选和排序任务」，图标按 16/20px 绘制；菜单含 `SectionLabel` `table.filterBy`「筛选条件」与子项 `table.status`「状态」，用 RadioItem 呈现 `['all','active','paused','finished']`；默认 `n.status ?? 'active'`；另有 owner/sort 子菜单（团队/云端范围）。
- BC02 `webview/assets/zh-CN-b05cfd55c534.js`：`table.filters`「筛选和排序任务」、`table.filterBy`「筛选条件」、`table.status`「状态」、`table.statusOption`「{active 已开启 / paused 已暂停 / finished 已完成 / other 全部}」、`table.empty`「没有符合筛选条件的任务」。

## 2. 写入集

生产（3）：

- `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessIcons.kt`（新增 `HarnessIconName.Filter` 与 `filter_list` 三条递减横杠绘制）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt`（新增可选 `statusFilter`/`onStatusFilterChange`、筛选图标按钮、状态选项行、组合过滤与空结果文案）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（新增 `scheduledStatusFilter` UI 状态并透传）

测试（1）：

- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledManagementInteractionTest.kt`（新增 2 项）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/scheduled-status-filter-contract-2026-10-12.md`
- 本文件

## 3. 实现要点

- **筛选控件**：导航器标题行在 `onStatusFilterChange != null` 时多渲染一个 `HarnessIconName.Filter` 图标按钮（`scheduled-toggle-filter`）；展开后一行「全部/已启用/已暂停」选项（`scheduled-status-filter` + `scheduled-filter-all|active|paused`），选中项用 `HarnessButtonVariant.Ghost` + `selected` 呈现。未传回调时控件完全不渲染（既有调用点零影响）。
- **组合过滤**：列表同时按 `query`（名称包含）与 `statusFilter`（`task.status == filter`）过滤，二者取交集。
- **空结果文案**：把原来的「没有匹配的任务」改成原版 `automations.scheduled.table.empty`「没有符合筛选条件的任务」（`state.tasks` 本身为空时仍显示「还没有定时任务」）。
- **新图标**：`HarnessIconName.Filter` 画 `filter_list`（三条长度递减的横杠），与既有 `Menu` 一样逐条 `drawLine`；`filledVariant` 的 `else` 分支使其无实心变体。

## 4. 问题

- 原版默认选中「已开启」（`n.status ?? 'active'`），本仓默认「全部」以保持既有导航器行为；这是有意的差异。
- 原版筛选菜单里的 owner（所有者）筛选与排序子菜单未实现（团队/云端范围）；`finished`（已完成）态本仓没有对应模型。
- 全量 `jvm-app` 前两次运行都在既有真实进程用时门 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（第 177 行 `waitUntil(30_000)`）超时——该用例与本批 UI 改动无关，隔离重跑 **3/3 exit 0**；第三次全量运行为 0 失败（见 §5.2）。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| ui-foundation 全模块（新增图标所在模块） | 全部 | **53 found / 0 skipped / 53 started / 53 passed / 0 failed**，exit 0 | `logs/ui-foundation.log` |
| shared 全模块（导航器改动所在模块） | 全部 | **538 found / 0 skipped / 538 started / 538 passed / 0 failed**，exit 0（本批未新增 shared 测试） | `logs/shared.log` |
| jvm-app 定向 | `ScheduledManagementInteractionTest` + `DesktopScheduleEngineTest` | **13 found / 0 skipped / 13 started / 13 passed / 0 failed**，exit 0（含新增 2） | `logs/jvm-app-targeted.log` |
| 全量 jvm-app（本批门槛，第 3 次） | 全部 | **688 found / 5 skipped / 683 started / 683 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |
| 全量 jvm-app 第 1 / 2 次（既有抖动） | 全部 | **688 found / 5 skipped / 683 started / 682 passed / 1 failed**，exit 1（同一 `PtyTerminalFontInteractionTest` 门超时） | `logs/full-jvm-app-flake-1.txt`、`logs/full-jvm-app-flake-2.txt` |
| `PtyTerminalFontInteractionTest` 隔离重跑 | 该类 | **3 found / 0 skipped / 3 started / 3 passed / 0 failed**，exit 0 | `logs/pty-isolated-rerun.log` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- `navigatorStatusFilterHidesTasksAndNamesTheEmptyResult`：初始渲染两个任务（启用/暂停）且 `scheduled-status-filter` 不存在 → 点 `scheduled-toggle-filter` 后选项出现 → 选「已暂停」后只剩 `scheduled-task:paused`、`scheduled-task:active` 消失 → 改选「已启用」并把搜索词设成「暂停」→ 显示「没有符合筛选条件的任务」→ 复位后又见两个任务。
- `navigatorOmitsTheFilterControlWithoutAHandler`：未传 `onStatusFilterChange` 时 `scheduled-toggle-filter` 不存在，任务行仍渲染。

### 5.2 全量结果

- 第 1 次：**688 found / 5 skipped / 683 started / 682 passed / 1 failed**（exit 1），唯一失败 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（真实进程 resize 门 30s 超时）。
- 第 2 次：同一用例再次超时（**682 passed / 1 failed**）；隔离重跑该类 **3/3 exit 0**，确认为环境/负载敏感的真实进程用时门，与本批 UI 改动无关（本批只碰导航器与一个图标）。
- 第 3 次：**688 found / 5 skipped / 683 started / 683 passed / 0 failed**（exit 0），本批门槛以此为准。

### 5.3 未跟踪捕获目录

每次全量运行都会把 `docs/desktop-ui/captures/scheduled-execution/` 重写为 `4f387960367fa746579d440583d0e5e2d54e802e`；三次运行后均按先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。本批导航器改动会让**壳层截图**（挂载 `HarnessApp` 的截图测试）多出筛选按钮，这些目录按既有机制重写、不单独备份。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 原版筛选菜单的 owner/排序子菜单、默认「已开启」、`finished` 态、精确几何/交互（RadioItem 弹出层）未实现。
- 新 `Filter` 图标是 Harness 内部绘制的 `filter_list` 形态，不主张与原版图标逐像素一致。

# 定时任务导航器排序验收 BD

日期：2026-10-12（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-bd1`。

## 1. 本批目标与依据

BC 实现了原版「筛选和排序任务」菜单的状态筛选；同一菜单里还有排序子菜单，本仓仍未做。本批只实现**可观察的那一层**：默认（active 先于 paused，组内稳定）、下次运行（按持久 cursor 的下次瞬间升序，无 cursor 最后）、名称。原版 `updated_at_desc`（最近更新）需要任务更新时间戳、`relevance`（相关性）依赖远端检索评分，二者本仓都没有，不实现。完整边界见 `../../../reverse-engineering/scheduled-sort-contract-2026-10-12.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- BD01 `webview/assets/table-92b84c469d71.js`（函数 `_t`）：排序与状态筛选在同一菜单、用 `Separator` 分隔；子菜单标题 `automations.scheduled.table.sort`「排序方式」，RadioGroup value = `n.sort ?? 'automatic'`；选项顺序 `['automatic','next_run_asc','updated_at_desc','title_asc', ...(搜索时 'relevance')]`；`automatic` 在 onSelect 写回 `undefined`；描述明确 Default 在浏览时把 active 排在 paused/completed 之前、搜索时用相关性排序。
- BD02 `webview/assets/zh-CN-b05cfd55c534.js`：`table.sort`「排序方式」、`table.sortOption`「{automatic 默认 / next_run_asc 下次运行 / updated_at_desc 最近更新 / title_asc 名称 / other 相关性}」。

## 2. 写入集

生产（3）：

- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledModels.kt`（新增 `ScheduledTaskSort { Automatic, NextRun, Title }`）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt`（筛选面板新增「排序方式」行、按所选排序、`onSortChange` 单独传入时也渲染筛选按钮）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（新增 `scheduledSort` UI 状态并透传）

测试（1）：

- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledManagementInteractionTest.kt`（新增 1 项）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/scheduled-sort-contract-2026-10-12.md`
- 本文件

## 3. 实现要点

- **排序模型**：新增 `ScheduledTaskSort`。`Automatic` 用稳定 `sortedBy`（active=0，paused=1）实现原版「active 先于 paused、组内保持存储顺序」；`NextRun` 按 `state.effective.cursors` 的 `nextNominalAt` 升序、无 cursor 的最后（用显式 `Comparator` 处理 null）；`Title` 按名称。
- **面板**：把 BC 的状态行包进一个 `Column`，在其下加「排序方式」行（默认/下次运行/名称，tag `scheduled-sort` + `scheduled-sort-*`）；状态行保留原 tag `scheduled-status-filter`。
- **按钮可见性**：筛选图标按钮现在在 `onStatusFilterChange != null || onSortChange != null` 时出现，选中态也考虑 `sort != Automatic`。
- **与搜索/筛选组合**：先按 query 与状态过滤，再排序；空结果文案不变。

## 4. 问题

- 原版 `updated_at_desc`（最近更新）与搜索时的 `relevance`（相关性）未实现（见 §6）。
- 原版把排序保存在用户偏好；本仓是每次进入页面的 UI 状态（与 BC 的状态筛选一致）。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **538 found / 0 skipped / 538 started / 538 passed / 0 failed**，exit 0（本批未新增 shared 测试） | `logs/shared.log` |
| jvm-app 定向 | `ScheduledManagementInteractionTest` | **7 found / 0 skipped / 7 started / 7 passed / 0 failed**，exit 0（含新增 1） | `logs/jvm-app-targeted.log` |
| 全量 jvm-app（本批门槛） | 全部 | **689 found / 5 skipped / 684 started / 684 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- `navigatorSortsByDefaultStatusNextRunOrName`：三个任务（alpha=暂停、beta=启用/cursor 5s、gamma=启用/cursor 1s）→ 默认下 `beta < gamma < alpha`（active 先于 paused，组内保持存储顺序）→ 点开筛选后出现 `scheduled-sort` → 选「下次运行」后 `gamma < beta < alpha`（无 cursor 的最后）→ 选「名称」后 `alpha < beta < gamma`。用行的 `positionInRoot.y` 比较顺序。

### 5.2 全量结果

全量 `jvm-app` 为 **689 found / 5 skipped / 684 started / 684 passed / 0 failed**（exit 0），本批未命中历史 worktree/PTY/git-commit 时序抖动，无需隔离重跑。

### 5.3 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行重写为 `4f387960367fa746579d440583d0e5e2d54e802e`，随后按先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`（20 个文件）。本批导航器改动会让**壳层截图**多出排序行，这些目录按既有机制重写、不单独备份。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 原版 `updated_at_desc`（最近更新，需任务更新时间戳）与搜索时的 `relevance`（相关性，依赖远端评分）排序未实现；原版菜单弹出层/键盘导航未复刻。
- 排序是页面内 UI 状态，不写入用户偏好。

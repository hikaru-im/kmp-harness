# 批次 AJ 证据：定时任务执行模型披露与 Unknown 运行的完整用户处理（生产改动）

- 日期：2026-10-11
- 写集（生产 2 个文件、测试 2 个文件）：
  - `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledModels.kt`：新增 `public const val ScheduledLocalExecutionModel`（执行模型文案）。
  - `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementPage.kt`：新增常驻披露行 `ScheduledExecutionModelNotice()`（testTag `scheduled-execution-model`），并把详情页的阻塞提示扩成完整的计数说明 `scheduled-blocked-runs`。
  - `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledPageInteractionTest.kt`、`ScheduledPageScreenshotTest.kt`：断言新节点（含文案）在各状态可见。
- 关联：W（落地栅格）、P（导航器）、R6（真实页面到终态）、O（Heartbeat）；契约 `reverse-engineering/scheduled-implementation-contract-2026-10-07.md` §7.2 与 `scheduled-cron-readiness-2026-10-07.md` 门槛 7。
- 目录：`docs/desktop-ui/verification/scheduled-execution-model-2026-10-11/entry-aj1/`

## 问题

`Scheduled` 的执行契约（§7.2）规定：本机没有 OS daemon 时，**关闭应用就停止本机调度，且必须在 UI 明示“只在应用运行期间执行”**，不能承诺退出后照常运行。把 readiness 门槛 7「仍需应用运行期间执行、无 daemon、不可恢复 Unknown 的完整用户处理与视觉验收」拆开看，当时：

- 页面**没有任何执行模型披露**（全仓 grep 无「运行期间 / 无 daemon / 退出后」字样）。
- 详情页对阻塞运行只有一句「任务有在途或 Unknown 运行；不能启用或重新投递。」，没有说明有多少条在途、多少条结果未知、以及为什么不能自动重投。

本批只补这两处 UI 缺口，不改执行器、不改调度语义、不加后台进程。

## 改动

1. `ScheduledLocalExecutionModel`：`本机定时任务只在本应用运行期间执行；关闭应用后不再运行，也没有系统后台守护进程替代执行。`
2. `ScheduledManagementPage`：在页面顶部常驻一行 `HarnessText`（`bodySmall`、`TextMuted`、左右 20dp / 上下 8dp，testTag `scheduled-execution-model`）。它在落地页、详情页、编辑器都可见，因为它是整页级的执行模型声明，不是某个状态的分支。
3. 详情页阻塞段：保留原 `scheduled-run-blocked` 提示；新增 `scheduled-blocked-runs`，按真实账本统计「进行中 N 条，结果未知 M 条」，并写明「结果未知的运行没有精确终态证据，不会自动重新投递；请先打开对应会话人工核对，在证据确认前不能启用或立即运行。」计数来自 `state.effective.runs.filter { it.task.id == task.id && it.blocksTask }` 与 `phase == Unknown`，不是固定文案。

## 实测（新捕获 vs 上一批 AE 捕获）

脚本 `measurement/measure-execution-model.py`（用 worktree 自带的 bundled Python + Pillow 运行，输出 `measurement/execution-model-measurements.txt`）：

| 项 | 批次 AE 捕获（改前） | 批次 AJ 捕获（改后） |
| --- | --- | --- |
| 披露行所在 y=4..28、x=268..1420 明暗墨迹像素 | **0** | **2495**（bbox x268..788、y11..22） |
| `scheduled-execution-model` 节点 bounds | 不存在 | x268..1420、y8..25（h17、w1152） |
| Unknown 详情 `scheduled-blocked-runs` 明暗墨迹像素 | 不存在 | **5392**（bbox x434..1238、y372..407，三行） |

结论：披露行确为**本批新增**（改前同坐标零墨迹），并且确实渲染（非零高、非空 bounds）。`PROBLEMS: 0`。

## 验收

- 定时页捕获类 `ScheduledPageScreenshotTest` **3/3**（288 文件写入 `docs/desktop-ui/captures/scheduled-execution-model-2026-10-11/`，含新节点可见性 sidecar）。
- `ScheduledPageInteractionTest` **12/12**（含本批新增断言：披露文案、阻塞计数说明、run-now/pause 仍禁用）。
- 其余定时页/壳层相关类：`ScheduledManagementInteractionTest` + `ScheduledPageRoutingInteractionTest` + `ScheduledRoutingInteractionTest` + `ScheduledPickerMigrationInteractionTest` **21/21**；`ScheduledCronPageIntegrationTest` + `ScheduledManagementScreenshotTest` **12/12**。
- 全量 `jvm-app` **647 found / 5 skipped / 642 started / 642 passed / 0 failed**（104 容器成功、0 失败，exit 0，`logs/jvm-app-full.log`）。此前多批出现的 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 本次通过，无失败。

## 边界与残留

- 这是**离屏**（ImageComposeScene）证据，没有原生窗口组合，也不主张与 Codex 原版同状态像素一致；披露文案是依据本仓执行契约的**设计决定**，不是逆向出来的原版字符串。
- 「只在应用运行期间执行」是当前架构的真实语义：`main.kt` 关闭时先 `scheduledExecutorOwner.shutdown()`（Drained 才退出），没有 LaunchAgent/系统 Cron 注册。本批只把该语义显式化，**没有**新增任何退出后后台执行机制；「应用退出后继续后台执行」仍属未做。
- `ScheduledManagementScreenshotTest` 与 `ScreenshotTest` 等把捕获写到 `docs/desktop-ui/captures/` 下（该目录未被 git 跟踪）。全量运行会刷新这些目录；本批按 AE 先例，把 `captures/scheduled-execution/` 先备份、跑完后**还原到批次前字节**（组合 sha `a842ee6a…`，20 个 PNG）。
- 不可恢复 Unknown 目前仍**没有**应用内「确认/清除」动作（执行器禁止重投，也没有注入人工裁决入口），本次只把状态与后果说清，不虚构一个不存在的解除通道。
- 各批证据独立，整体目标 active。

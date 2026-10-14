# Thread 摘要面板「停止所有后台终端」验收 AV

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-av1`。

## 1. 本批目标与依据

AU 已把摘要 island 的「子智能体」「后台进程」两个小节接上真实只读数据，但明确未做原版 terminal 行上的「停止所有后台终端」动作（当时的边界记在 AU 的契约里）。ASAR 证据显示：该动作挂在**每一个** terminal 行上，一次点击停止该会话的**所有**后台终端（`cleanBackgroundTerminals(conversationId)`），在途时不重入，失败有独立文案。本批补上这个动作与本仓的等价后端组合，不新增协议、不伪造成功。完整边界见 `../../../reverse-engineering/thread-summary-stop-all-contract-2026-10-11.md`。

ASAR 依据（只读抽取，见 `logs/asar-stop-all-evidence.txt`）：C01 `ItemActionButton`（label/`visibility:'interaction'`/`disabled`/in-flight spinner）；C02 点击处理器与 `cleanBackgroundTerminals` + `onStopError`；C03 行 trigger 仍打开该终端；C04 小节集合与 zh-CN 文案。

## 2. 写入集

生产（4）：

- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivity.kt`（新增 `ThreadSummaryStopAllLabel` 常量）
- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivityController.kt`（`stoppingProcesses`/`stopNotice` + `stopAllProcesses()`）
- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryPanel.kt`（`processesStopping`/`processesNotice`/`onStopProcesses` + 行内停止按钮 + notice 行）
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`（接线）

测试（3）：

- `apps/shared/test/im/hikaru/harness/client/summary/ThreadSummaryActivityControllerTest.kt`（新增 4 项 + 一个记录型 fake repository）
- `apps/jvm-app/test/im/hikaru/harness/desktop/ThreadSummaryActivityPanelInteractionTest.kt`（新增 3 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/HarnessAppThreadPanelsShellTest.kt`（追加 2 条断言行）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/thread-summary-stop-all-contract-2026-10-11.md`
- 本文件

## 3. 实现要点

- 只停真实 running：`stopAllProcesses()` 只取消**上一次成功读取**里 lifecycle 为 `Running` 的进程，清单来自控制器自己的 `readTerminalSelection(owner, null).inventory` 结果，不重新枚举、不猜进程。
- 收敛与去重：对每个 key 各发一次既有 `operateTerminal(owner, key, Cancel)`；`Applied`/`Unknown` 的 key 进入 `stoppedKeys`，同一 owner incarnation 内不重发；`Rejected` 保持可重试；非 `Applied` 的返回消息去重后写入 `stopNotice`（原文，不新造文案）。
- 栅栏与生命周期：`stopAllProcesses()` 在未绑定/已关闭/无 running 目标/已在途时返回 `null`；绑定或关闭会清空 `inventory` 与 `stoppedKeys` 并取消在途任务；结束（或被重绑定打断）后状态回到 `stoppingProcesses = false` 或全新 owner generation。完成后 `refresh()` 重读清单，行状态随真实数据更新。
- UI：每个 process 行右侧一个 `thread-summary-process-stop-<id>` 图标按钮（`contentDescription` =「停止所有后台终端」，在途 disabled），与行点击（打开终端）在语义上分离，因此行 tag 仍可单独点击；`processesNotice` 非空时渲染 `thread-summary-processes-notice`。

## 4. 问题

- 与原版的三处已知差异（已在契约 3.1 记录）：hover 才可见 → 始终可见；toast → 小节内原文 notice；批量 RPC → 按进程各发一次 Cancel。
- 全量运行会重新生成未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；跑前该目录为 `ac8ebb54…`，跑后按 AE/AT 先例还原（见 §5.4）。
- 本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口/像素证据。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 全模块（生产改动所在模块） | 全部 | **496 found / 0 skipped / 496 started / 496 passed / 0 failed**，exit 0 | `logs/shared.log` |
| jvm-app 定向 | `ThreadSummaryActivityPanelInteractionTest` + `ThreadSummaryPanelInteractionTest` + `HarnessAppThreadPanelsShellTest` | **16 found / 16 started / 16 passed / 0 failed**，exit 0 | `logs/desktop-summary.log` |
| 摘要模型/控制器定向 | `im.hikaru.harness.client.summary.*` | **21 found / 21 started / 21 passed / 0 failed**，exit 0 | `logs/shared-summary.log` |
| 全量 jvm-app（本批门槛） | 全部 | **672 found / 5 skipped / 667 started / 667 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 控制器（`ThreadSummaryActivityControllerTest`，+4）：停全部只对 running 进程各发一次 `Cancel`（exited 不发）、结束后 `stoppingProcesses=false` 且 `stopNotice=null` 并重读清单；`Rejected` 把服务原文写入 `stopNotice` 且可重试；`Unknown` 记为已处理，同 incarnation 内第二次调用返回 `null` 且不再发请求；未绑定/已关闭/无 running 目标时返回 `null`。
- 岛交互（`ThreadSummaryActivityPanelInteractionTest`，+3）：每个 process 行都有停止按钮、点击回报一次、subagent 行没有该按钮、有 2 个 terminal 行时共 2 个按钮；没有 consumer 时不画按钮；`processesStopping=true` 时按钮 `assertIsNotEnabled`，`processesNotice` 原文渲染。
- 壳层（`HarnessAppThreadPanelsShellTest`，+2）：running 与 exited 两个真实进程行都出现 `thread-summary-process-stop-<id>`。

### 5.2 全量失败归因

全量 `jvm-app` **exit 0，0 failed**（672 found / 5 skipped / 667 started / 667 passed），本批未命中 AE/AF/AM/AO/AQ/AT 记录过的真实 worktree 注册 / PTY 就绪超时抖动，因此无需抖动重跑。

### 5.3 定向回归

`logs/desktop-summary.log` 与 `logs/shared-summary.log` 全绿（见 §5 表），未出现真实进程/PTY 抖动。

### 5.4 未跟踪捕获目录

跑前 `docs/desktop-ui/captures/scheduled-execution/` 为 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`；全量运行确实重写了它，随后按 AE/AT 先例从批次前字节 `/tmp/au-sched-iwXA/orig` 还原，`diff -r` 一致，组合 sha 回到 `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口（本机 Screen Recording 权限不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现：subagent 行 `diffStats` 徽标、`showInlineActivity` 去重、`autoCollapse`「{count} 完成」折叠、摘要面板其余小节（Sources/created-tasks/plan/pull-requests/side-chats/triggers/environment-creation）、原版 hover-only 可见性与 toast 形态。
- 「部分成功」的合并语义未与后端对齐（本仓是按进程各发一次 Cancel），只在 notice 里按去重原文展示。
- 各批证据独立，整体目标 active。

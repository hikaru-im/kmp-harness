# BU：定时任务删除确认改为模态对话框（2026-10-12）

## §1 目标与依据

目标：把定时任务详情页「删除」的二次确认，从详情页内联的 `HarnessSurface` 卡片改成与 Codex 一致的**模态确认
对话框**，并采用 zh-CN 资源里的删除确认文案。只处理确认交互与可见文案；删除语义（只移除任务配置，不取消在途
任务、不清理会话历史）不变。

依据（`logs/asar-automation-delete-strings.txt`，来源 `/tmp/asar-new/webview/assets/zh-CN-b05cfd55c534.js`，只读、未联网）：

- E01：`inbox.automations.deleteConfirm.title` = 删除 {name}？
- E02：`inbox.automations.deleteConfirm.confirm` = 删除定时任务
- E03：`inbox.automations.deleteConfirm.descriptionNoPeriod` = 这将永久删除定时任务，并停止日后的运行
- E04：`inbox.automations.deleteConfirm.cancel` = 取消
- 反例：同资源里 `home.artifactTemplates.deleteConfirm.*`（模板删除）与 `settings.dataControls.archivedChats.deleteConfirm.*`
  （归档聊天删除）是同名但不同的命名空间，本批**不**采用。

Codex 的说明句未提在途任务与会话历史；本仓删除只移除任务配置，故在 Codex 原句后追加一句诚实补充，不宣称取消了
在途运行或清理了会话历史（详见契约 §2）。

## §2 写入集合

生产（1 个文件）：

- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementPage.kt`：
  1. 新增 `deleteDialogFence`（与 `pageFence` 同源但**不含** `mutation`），`deleteId` 与 `onDeleteRequest` 改用它，
     使删除提交期间确认框保持打开；
  2. 详情页删除确认由内联 `HarnessSurface` 卡片改为共享 `HarnessConfirmDialog`（标题「删除 {任务名}？」、
     说明「这将永久删除定时任务，并停止日后的运行；已开始的任务不会被取消，会话历史也不会被清理。」、
     确认「删除定时任务」、取消「取消」、`HarnessConfirmIntent.Destructive`、`testTag = scheduled-delete-confirmation`），
     `submitState` 由 `mutation == Delete` / `mutationError` 驱动。

测试（1 个文件）：

- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledPageInteractionTest.kt`：
  更新 `unavailableExecutionAndDeleteConfirmationDoNotCancelOrCleanHistory` 的期望（新说明文案、`...:cancel`/`...:confirm`
  tag），新增 `deleteDialogIsModalAndARejectedWriteKeepsItActionable`（断言模态容器可见、标题含任务名、确认文案；
  写入被拒后对话框保持打开并显示 `...:error`，任务列表不变）。

文档：`docs/desktop-ui/reverse-engineering/scheduled-delete-dialog-contract-2026-10-12.md`；本目录 `review.md`、
`files.sha256`、`logs/`。

截图产物：本批未新增截图；`docs/desktop-ui/captures/scheduled-execution/` 由全量 `jvm-app` 的截图用例重写，
跑后按先例从批次前字节还原。

## §3 实现

- **模态对话框**：改用共享 `HarnessConfirmDialog`（`androidx.compose.ui.window.Dialog` 层），与其他破坏性确认
  （`managed-remove-dialog`、`shortcuts-reset-dialog`）同构：破坏性意图下初始焦点落在「取消」、确认按钮为
  Destructive 变体、Esc / 点击外部在非提交态可关闭。
- **提交期不被 fence 关闭**：此前内联卡片与回调共用 `pageFence`，而 `pageFence` 含 `mutation`，删除一置位 `mutation`
  确认框就会被关闭，无法显示进度 / 失败。本批新增只差 `mutation` 的 `deleteDialogFence`：作用域 / 上下文 / 连接 /
  阶段 / 选中任务变化时仍会关闭对话框，删除提交期间则保持打开。
- **状态与错误**：`submitState` 为 `Submitting`（`mutation == ScheduledMutation.Delete`）/ `Failed(mutationError)`
  （写入被拒）/ `Ready`；失败时共享对话框保持打开并显示 `scheduled-delete-confirmation:error`。
- **删除语义不变**：`scheduled-delete` 仍 `enabled = state.canMutate`，`ScheduledRepository.delete` 未改（仍只从
  `tasks` 移除、记入 `deletedTaskIds`、清空 cursor）。

## §4 问题

- 全量 `jvm-app` 命中真实 git 进程抖动类
  `DesktopGitCommitTest.includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified`
  （`TimeoutCancellationException: Timed out waiting for 15000 ms`，`DesktopGitCommitTest.kt:53`，
  `logs/jvm-app-module-full.log`）。该用例与本批（仅改调度页确认框）无关，属本机 NTFS 卷上真实 `git` 子进程在高负载
  下超时的既有环境抖动；隔离重跑 **10/10 通过、exit 0**（`logs/jvm-app-git-commit-isolated-rerun.log`）。
- 本批全量未命中历史 PTY 抖动类（`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`）本次通过。

## §5 验收

命令行：`./kotlin test --build-dir=/tmp/ap-build -p jvm -m <module> [--include-classes=…]`。

- `jvm-app` 定向 `ScheduledPageInteractionTest`：**13 found / 13 passed / 0 failed**，exit 0
  （`logs/jvm-app-scheduled-page-targeted.log`；本批新增 1、改 1，上批该文件为 12）。
- `shared` 全模块：**557 found / 557 passed / 0 failed**，exit 0
  （`logs/shared-module-full.log`；与上批持平，本批无 shared 单测改动）。
- `jvm-app` 全量：**695 found / 5 skipped / 690 started / 689 passed / 1 failed**，exit code **1**
  （`logs/jvm-app-module-full.log`；唯一失败为上文真实 git 抖动类，隔离重跑通过；found 较上批 694 增 1，即新增交互用例）。
- `jvm-app` 定向 `DesktopGitCommitTest` 隔离重跑：**10 found / 10 passed / 0 failed**，exit 0
  （`logs/jvm-app-git-commit-isolated-rerun.log`）。
- 冻结资产还原：全量 `jvm-app` 重写 `docs/desktop-ui/captures/scheduled-execution/`（截图用例固有行为），跑后从
  批次前字节还原，`diff -r` 无差异，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5` 与批次前一致。
- `git diff --check` 通过；新增 / 修改文件均为 LF、无尾随空白。

## §6 未验证边界

- 本批只有 offscreen（`runSkikoComposeUiTest`）证据；**无原生窗口与像素对齐证据**（本机 `screencapture` 仍返回
  `could not create image from display`）。模态对话框在 skiko 测试里可被语义树与指针命中，但在真实 AWT 窗口下的
  居中 / 遮罩 / 圆角尺寸未与码版逐像素比对。
- 只覆盖定时任务详情页的删除确认；本仓其它二次确认（工作树删除、快捷键重置、窗口关闭等）沿用各自既有实现，未统一改造。
- 说明文案是 Codex `descriptionNoPeriod` 原句 + 本仓诚实补充；未逐字复刻 Codex 是否另有句号 / 换行变体。
- 删除语义未变：不取消在途运行、不清理会话历史（由 `ScheduledPageInteractionTest` 断言 `snapshot.runs` 保留、
  `deletedTaskIds` 仅记该任务、`executionCalls == 0`）；应用退出后后台执行仍未实现（属计划范围内既有缺口）。
- 未复刻 Codex 对话框在提交期间的按钮 loading 视觉细节（共享对话框以 `HarnessControlState.Loading` 呈现，未做像素比对）。

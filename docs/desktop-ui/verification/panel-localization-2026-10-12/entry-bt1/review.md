# BT：后台终端 / 子智能体面板中文化（2026-10-12）

## §1 目标与依据

目标：把会话内两个面板标签页（后台终端、子智能体）以及其中的交互终端选择器里**残留的英文可见文案**改成中文，
让面板的可见文案与其同屏的其它中文界面（含已中文化的线程摘要岛）一致。这是纯文案/展示层工作，不改变任何读取、
生命周期、授权或只读边界。

依据（`logs/asar-panel-strings.txt`，来源 `/tmp/asar-new/webview/assets/zh-CN-b05cfd55c534.js`，只读、未抓包）：

- E01：`codex.localConversation.backgroundTerminalTab.title` = 后台终端；`.noOutput` = 暂无输出；
  `backgroundTerminals.defaultLabel` = 后台终端；`.stop` = 停止所有后台终端；`.cleanError` = 无法停止后台终端。
- E02：`codex.localConversation.backgroundTasks.title.subagents` = 子智能体；`.backgroundProcesses` = 后台进程。
- E03：`subagents.panel.title` = 子智能体；`.back` = 返回子智能体；`.active` = 进行中；`.done` = 已完成 · {count}；
  `.noActive` = 没有活跃的子智能体；`subagentsPanel.done` = 完成 · {count}；`subagentsPanel.noActive` = 没有已开启的子代理。
- E04：`subagents.status.working` / `subagentsPanel.working` = 处理中；`subagents.status.waiting` = 等待中；
  `subagents.status.done` = 已完成；`subagents.status.failed` = 未成功。
- E05：`codex.localConversation.backgroundAgents.activeLabel` = 运行中；`codex.summaryPanel.card.showMore` = 显示更多。
- E06：线程摘要岛（`ThreadSummaryActivity.kt`）已按同一套 key 中文化（如 `ThreadSummaryRunningLabel` = 运行中、
  `ThreadSummarySubagentsTitle` = 子智能体），本批只是把同一约定补到面板上。

本批**没有**对应 Codex key 的字符串（只读输出、加载/失败态、面板已关闭、刷新/重试按钮等）按本仓既有中文语气书写，
并在 `logs/asar-panel-strings.txt` 与契约里显式标出「无 key」，不冒充逐字复刻。

## §2 写入集合

生产（3 个文件）：

- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt`：两个标签页的标题、`HarnessTabs` 的
  `closeLabel`（改为「关闭{标题}」）、标签容器 label（会话面板）、生命周期文案、进程选择占位、刷新/重试按钮、
  进程输入控件、后台输出各状态、子代理总览/详情各状态、分区标题与空态、更多按钮、详情返回按钮与只读提示，
  全部改为中文；`stateDescription` 与状态徽标改用 `ChildAgentStatus.label`。
- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelModels.kt`：为 `SubagentSection`、`ChildAgentStatus`、
  `ChildTranscriptRole` 增加 `label`（中文显示），`name` 仍是稳定身份。
- `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt`：交互终端选择器的生命周期文案
  （运行中 / 已退出 (N) / 未知）与退出提示。

测试（6 个文件，仅改期望，不改断言结构）：

- `apps/jvm-app/test/im/hikaru/harness/desktop/ThreadPanelsInteractionTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/SubagentHistoryInteractionTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/TerminalReadingInteractionTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/HarnessAppThreadPanelsShellTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/LiveHarnessConversationInteractionTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt`

文档：`docs/desktop-ui/reverse-engineering/panel-localization-contract-2026-10-12.md`；本目录 `review.md`、
`files.sha256`、`logs/`。

截图产物：面板截图用例默认写入 `/tmp`（`HARNESS_THREAD_PANELS_CAPTURE_DIR` / `HARNESS_APP_THREAD_SHELL_CAPTURE_DIR`
未设置时），本批未产出仓库内新截图。

## §3 实现

- **身份与显示分离**：枚举 `name`（`BackgroundOutput` / `Subagents` / `Active` / `Waiting` / `Done` / `Unknown` /
  `User` / `Assistant` / `Tool` / `System`）继续承担 state、持久化、测试与语义标签；新增的 `label` 只用于渲染。
  因此 `tab:*`、`terminal-*`、`pty-*`、`menu:*` 等 tag 与所有 key 完全不变。
- **标签页与关闭按钮**：标题走 `ThreadPanelTab.title`（后台终端 / 子智能体）；`HarnessTabs` 关闭按钮改传
  `closeLabel = { "关闭" + it.title }`，不再使用默认的 `"Close ${title}"`。这是本批唯一改变 tag 派生的两处
  （`HarnessButton`/`HarnessIconButton`/`HarnessTextField` 默认以文字作 tag），对应测试同批更新。
- **生命周期文案**：后台进程用 `BackgroundProcessLifecycle.label`（运行中 / 已退出 · 代码 N / 状态未知）；
  交互终端用 `PtyTerminalPanel` 内联的 `TerminalLifecycle` 分支（运行中 / 已退出 (N) / 未知）。两处取词一致。
- **分区标题结构保持**：本仓面板沿用 `<标题> (<计数>)`，Codex 用「已开启 · {count}」；本批只把词形换成
  进行中/已完成，**不改结构**，差异记录在契约 §1。
- **未动**：`PtyTerminalGrid` 的 `stateDescription`（结构化无障碍串，测试用正则解析）与未知回执安全提示前缀
  `Unknown：…`（本仓跨页面既有状态令牌，调度页同样使用）保持原样。

## §4 问题

- 首轮用脚本按字面替换时漏了两处：`"No messages"`（脚本的替换表缺项）与子代理兼容历史的角色断言
  `"Tool"`。首轮定向跑出 26/28（`logs/jvm-app-panels-interaction-first-attempt.log`），补齐这两处后 28/28。
- 脚本一开始把 `"Running"` 一并替换到 `PtyTerminalPageInteractionTest` 的**内部截图阶段名**（`capture(... "Running" ...)`）
  上，而该阶段名同时是生产 `PtyTerminalPanel` 未改动时的期望文本来源。已把阶段名还原为英文、只让
  `pty-terminal-select` 的**断言值**跟随生产改中文，避免测试自身命名与生产不一致。
- 全量 `jvm-app` 命中历史抖动类 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`
  （`waitUntil` 等真实 PTY 在 30s 内写入 `size-trigger.hex`/`kernel-size` 超时，`PtyTerminalFontInteractionTest.kt:177`）。
  该用例与本批只改文案无关（其断言全是网格几何与内核尺寸），隔离重跑 **3/3 通过**（`logs/jvm-app-pty-font-isolated-rerun.log`）。

## §5 验收

命令行：`./kotlin test --build-dir=/tmp/ap-build -p jvm -m <module> [--include-classes=…]`。

- `shared` 定向 `client.panels.*`：**77 found / 77 passed / 0 failed**（`logs/shared-panels-targeted.log`）。
- `shared` 全模块：**557 found / 557 passed / 0 failed**（`logs/shared-module-full.log`；与上批持平，本批不新增单测）。
- `jvm-app` 定向 `ThreadPanelsInteractionTest`+`SubagentHistoryInteractionTest`+`TerminalReadingInteractionTest`：
  **28 found / 28 passed / 0 failed**（`logs/jvm-app-panels-interaction-targeted.log`；首轮 28 found / 26 passed / 2 failed）。
- `jvm-app` 定向 `HarnessAppThreadPanelsShellTest`+`LiveHarnessConversationInteractionTest`：
  **15 found / 15 passed / 0 failed**（`logs/jvm-app-shell-live-targeted.log`）。
- `jvm-app` 定向 `PtyTerminalPageInteractionTest`（真实进程）：**7 found / 7 passed / 0 failed**（`logs/jvm-app-pty-page-targeted.log`）。
- `jvm-app` 定向 `ThreadPanelsScreenshotTest`+`ThreadSummaryActivityPanelInteractionTest`：
  **10 found / 10 passed / 0 failed**（`logs/jvm-app-panels-screenshot-targeted.log`）。
- `jvm-app` 全量：**694 found / 5 skipped / 689 started / 688 passed / 1 failed**，exit code **1**
  （`logs/jvm-app-module-full.log`；唯一失败为上文历史抖动类，隔离重跑通过）。
- 冻结资产还原：全量 `jvm-app` 会重写 `docs/desktop-ui/captures/scheduled-execution/`（截图用例固有行为）；
  跑后从批次前字节还原，`diff -r` 无差异，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5` 与批次前一致。
- `git diff --check` 通过；新增/修改文件均为 LF、无尾随空白。

## §6 未验证边界

- 本批只有 offscreen（`ImageComposeScene`/`runSkikoComposeUiTest`）证据；**无原生窗口与像素对齐证据**（本机
  `screencapture` 仍返回 `could not create image from display`）。
- Codex 的 section 标题是「已开启 · {count}」/「完成 · {count}」，本仓保持「进行中 (N)」/「已完成 (N)」的括号结构，
  属**结构性差异**，未做逐字复刻。
- 面板中仍有多条 Harness 专有文案没有 Codex key（只读输出、各加载/失败态、刷新/重试按钮、`EOF` 保留 ASCII）；
  这些按本仓语气书写，未声称与原版逐字一致。
- `PtyTerminalGrid` 的 `stateDescription` 与未知回执前缀 `Unknown：` 有意保留英文/令牌形态，见 §3。
- 枚举 `label` 未新增单测（展示由 jvm-app 交互用例间接断言）；面板与摘要岛之外是否还有同类英文文案未系统盘点
  （本批只覆盖 `client/panels` 生产文件与交互终端选择器）。

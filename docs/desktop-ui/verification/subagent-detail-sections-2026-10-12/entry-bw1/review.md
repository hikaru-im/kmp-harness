# BW：子 Agent 面板详情节与区块标题（2026-10-12）

## §1 目标与依据

目标：会话右侧「子智能体」面板向 Codex 结构收敛两处——列表区块标题（`subagents.panel.active` 进行中、
`subagents.panel.done` 已完成 · {count}）与详情视图开头的「状态」「委派的任务」小节。只对齐可见结构与文案，
不新增读取、生命周期或授权语义。

依据（`logs/asar-subagent-panel-evidence.txt`，来源 `/tmp/asar-new/webview/assets/` 的
`zh-CN-b05cfd55c534.js` 与 `chatgpt-subagents-panel-e608a4ff5e56.js`，只读、未联网）：

- 区块标题：`subagents.panel.active` = 进行中（**不带**计数）；`subagents.panel.done` = 已完成 · {count}（值 = 已完成代理数）；
  Active `visibleItemLimit:4`、Done `visibleItemLimit:10`。
- 空态：`subagents.panel.noActive` = 没有活跃的子智能体。
- 详情小节：组件 P 依序渲染 `subagents.panel.status`（状态）、`subagents.panel.delegatedTask`（委派的任务）、
  `subagents.panel.response`（最终回复）；小节结构 `F(e)` = `section { h2(text-sm font-medium text-tertiary){title};
  div(text-sm leading-6){body} }`；委派的任务体用 `whitespace-pre-wrap` 保留换行。
- 「最终回复」正文来自子代理 `statusMessage` 或专门查询（`chatgpt-subagent-final-response-query`），本仓无该数据源。

## §2 写入集合

生产（3 个文件）：

- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelModels.kt`：`SubagentSection` 新增
  `heading(count: Int)`（Active → 「进行中」，Done → 「已完成 · $count」，`require(count >= 0)`）；`label` 保留。
- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt`：`SubagentSectionView` 标题由
  `label + " (N)" (+ " · 等待中 N")` 改为 `section.heading(all.size)`（移除只服务于旧标题的 `waiting` 统计）；
  `ChildDetailPanel` 头部之下新增「状态」「委派的任务」两个 `DetailSection`（`testTag` 分别
  `subagent-detail-status` / `subagent-detail-task`）；新增私有 `DetailSection(title, tag, body)`。

测试（2 个文件）：

- `apps/shared/test/im/hikaru/harness/client/panels/ThreadPanelsStateTest.kt`：新增
  `sectionHeadingsMatchCodexCountRules`（Active 无计数、Done 带计数、负数抛 `IllegalArgumentException`）。
- `apps/jvm-app/test/im/hikaru/harness/desktop/SubagentHistoryInteractionTest.kt`：fixture 的
  `SessionDescendant` 补 `objective`（新增 `HistoryHarness.objective(child)`）；新增
  `detailLeadsWithCodexStatusAndDelegatedTaskSections`（断言两个小节存在、文案与状态标签、任务正文，
  且 lineage-checked 历史仍在）。

文档：`docs/desktop-ui/reverse-engineering/subagent-detail-sections-contract-2026-10-12.md`；本目录 `review.md`、
`files.sha256`、`logs/`。

截图产物：本批未新增截图；`docs/desktop-ui/captures/scheduled-execution/` 由全量 `jvm-app` 的截图用例重写，
跑后按先例从批次前字节还原；`docs/desktop-ui/captures/git-worktrees/` 由全量运行重写但字节未变。

## §3 实现

- **区块标题对齐 Codex 计数规则**：`SubagentSection.heading(count)` 是纯函数——Active 段标题固定「进行中」，
  Done 段「已完成 · {count}」。`SubagentSectionView` 用当前段内的实际条数 `all.size` 生成标题；Section 的
  过滤/排序、`visibleChildren` 的 4/10 上限与空态判定都未改。
- **详情节**：`DetailSection` 复刻 Codex `F(e)` 结构（muted 小标题 + 默认色正文，`heading()` 语义），
  以「状态」正文取 `ChildAgentSummary.status.label`（处理中/等待中/已完成/状态未知），「委派的任务」正文取
  `ChildAgentSummary.objective`，仅在非空白时渲染，保留原始换行。两节都只读已有 summary 字段，不触发新读取。
- **保留既有历史**：详情仍在两节下方渲染本仓 lineage-checked 的历史（typed `Message` 或兼容条目），
  只读边界与既有交互不变。

## §4 问题

- 本批全量 `jvm-app` 未命中历史抖动类（`PtyTerminalFontInteractionTest` 与 `DesktopGitCommitTest` 本次均通过）。
- 测试 fixture 新增 `objective` 会让列表行首次出现真实 preview 文案；`selectChild` 仍按会话名匹配，未受影响。

## §5 验收

命令行：`./kotlin test --build-dir=/tmp/ap-build -p jvm -m <module> [--include-classes=…]`。

- `shared` 定向 `ThreadPanelsStateTest`：**13 found / 13 passed / 0 failed**，exit 0（`logs/shared-thread-panels-state-targeted.log`；新增 1）。
- `jvm-app` 定向 `SubagentHistoryInteractionTest` + `ThreadPanelsInteractionTest` + `ThreadPanelsScreenshotTest`：
  **25 found / 25 passed / 0 failed**，exit 0（`logs/jvm-app-subagent-panels-targeted.log`；新增 1）。
- `shared` 全模块：**558 found / 558 passed / 0 failed**，exit 0（`logs/shared-module-full.log`；上批 557 增 1，即新增标题用例）。
- `jvm-app` 全量：**698 found / 5 skipped / 693 started / 693 passed / 0 failed**，exit code **0**
  （`logs/jvm-app-module-full.log`；上批 697 found / 692 started 增 1，即新增交互用例；本批未命中历史抖动）。
- 冻结资产还原：全量 `jvm-app` 重写 `docs/desktop-ui/captures/scheduled-execution/`，跑后从批次前字节还原，
  `diff -r` 无差异，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5` 与批次前一致。
- `docs/desktop-ui/captures/git-worktrees/`：全量运行重写但字节未变（组合 sha 仍
  `c55cad68c1d52c264c9e723a9ab0926d9fda0f80`），本批未改动 Diff 渲染，无需还原。
- `git diff --check` 通过；新增 / 修改文件均为 LF、无尾随空白。

## §6 未验证边界

- **「最终回复」小节未实现**：Codex 的正文来自子代理 `statusMessage` 或专门的最终回复查询
  (`chatgpt-subagent-final-response-query`)，本仓 `SessionDescendant`/`ChildHistoryResult` 没有该数据源，
  故不渲染，避免虚构完成响应。
- Codex 的详情视图只有状态/委派的任务/最终回复三个小节，没有完整会话记录；本仓在其上仍渲染 lineage-checked 历史，
  属结构性差异，未做同一滚动容器。
- 状态体在 Codex 里 `failed` 标红；本仓 `ChildAgentStatus` 无 Failed（只有 Active/Waiting/Done/Unknown），
  故不做红色态。
- 只覆盖「子智能体」面板的标题与详情节；后台终端页、线程摘要岛不改。
- 本批只有 offscreen（`runSkikoComposeUiTest`）证据；**无原生窗口与像素证据**（本机 `screencapture` 仍返回
  `could not create image from display`）。

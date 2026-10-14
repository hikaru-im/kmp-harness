# 契约：后台终端 / 子智能体面板文案（2026-10-12）

范围：会话内两个面板标签页——`ThreadPanelTab.BackgroundOutput`（后台终端）与 `ThreadPanelTab.Subagents`
（子智能体），加上其中的交互终端（PTY）选择器。只处理**可见文案**；不改变任何读取、生命周期、授权或只读边界。

## §1 只读依据

来源：`/tmp/asar-new/webview/assets/zh-CN-b05cfd55c534.js`（ASAR 已解包的 zh-CN 资源，只读；未抓包、未联网）。
本目录同级证据：`../verification/panel-localization-2026-10-12/entry-bt1/logs/asar-panel-strings.txt`。

已确认的 Codex key → 文案（原样引号内为 zh-CN 资源字面）：

| Codex key | 文案 |
| --- | --- |
| `codex.localConversation.backgroundTerminalTab.title` | 后台终端 |
| `codex.localConversation.backgroundTerminalTab.noOutput` | 暂无输出 |
| `codex.localConversation.backgroundTerminals.defaultLabel` | 后台终端 |
| `codex.localConversation.backgroundTerminals.stop` | 停止所有后台终端 |
| `codex.localConversation.backgroundTerminals.cleanError` | 无法停止后台终端 |
| `codex.localConversation.backgroundTasks.title.backgroundProcesses` | 后台进程 |
| `codex.localConversation.backgroundTasks.title.subagents` / `subagents.panel.title` / `subagentsPanel.title` | 子智能体 |
| `subagents.panel.back` / `subagentsPanel.back` | 返回子智能体 / 返回子代理列表 |
| `subagents.panel.active` | 进行中 |
| `subagents.panel.done` / `subagentsPanel.done` | 已完成 · {count} / 完成 · {count} |
| `subagents.panel.noActive` / `subagentsPanel.noActive` | 没有活跃的子智能体 / 没有已开启的子代理 |
| `subagents.status.working` / `subagentsPanel.working` | 处理中 |
| `subagents.status.waiting` / `subagentsPanel.status.waiting` | 等待中 |
| `subagents.status.done` | 已完成 |
| `subagents.status.failed` | 未成功 |
| `codex.localConversation.backgroundAgents.activeLabel` | 运行中 |
| `codex.summaryPanel.card.showMore` | 显示更多 |

说明：Codex 的 section 标题是「已开启 · {count}」/「完成 · {count}」（计数跟在词后）。本仓面板沿用既有结构
`<标题> (<计数>)`，因此只取词形（进行中 / 已完成）而保留括号计数；这是**结构性差异**，记录在此，不宣称逐字一致。

## §2 本仓映射

| 位置 | 原文案 | 新文案 | 依据 |
| --- | --- | --- | --- |
| 标签页标题（后台） | `Background output` | 后台终端 | `backgroundTerminalTab.title` |
| 标签页标题（子代理） | `Subagents` | 子智能体 | `subagents.panel.title` |
| 标签页关闭 | `Close {title}` | 关闭{标题} | 关闭类文案（`artifactTab.dismissRefreshForLatest` 等） |
| 标签容器 label | `Conversation panels` | 会话面板 | 无 Codex key（无障碍标签） |
| 生命周期（后台进程） | `Running` / `Exited · code N` / `Status unavailable` | 运行中 / 已退出 · 代码 N / 状态未知 | `backgroundAgents.activeLabel` + 既有「状态未知」用法 |
| 生命周期（PTY 终端） | `Running` / `Exited (N)` / `Unknown` | 运行中 / 已退出 (N) / 未知 | `backgroundAgents.activeLabel` + 既有用法 |
| 进程选择占位 | `Selected process unavailable` | 所选进程不可用 | 无 Codex key |
| 刷新按钮 | `Refresh output` / `Retry output` / `Refresh subagents` / `Refresh history` | 刷新输出 / 重试输出 / 刷新子智能体 / 刷新历史 | 无独立 Codex key（按钮由本仓提供） |
| 进程输入 | `Process input` / `Send line` / `Cancel process` / `EOF` | 进程输入 / 发送一行 / 取消进程 / EOF | 无 Codex key；`EOF` 为 ASCII 控制字，保留 |
| 后台输出状态 | `Loading output…` / `Unable to load output` / `Terminal unavailable` / `No background processes` / `Read-only output · plain text` / `Output buffer truncated by source` / `No output yet` | 正在加载输出… / 无法加载输出 / 终端不可用 / 没有后台进程 / 只读输出 · 纯文本 / 输出缓冲区已被来源截断 / 暂无输出 | 仅 `noOutput`=暂无输出 有 key，其余为本仓只读投影 |
| 子代理状态 | `Loading subagents…` / `Unable to load subagents` / `No subagents` / `Refreshing subagents…` / `No readable live subagents` | 正在加载子智能体… / 无法加载子智能体 / 没有子智能体 / 正在刷新子智能体… / 没有可读取的 live 子智能体 | 无对应 key |
| 子代理分区 | `Status unavailable (N)` | 状态未知 (N) | 无对应 key |
| 会话面板关闭态 | `Panels closed` | 面板已关闭 | 无对应 key |
| 分区标题 | `Active (N)` / `Done (N)`、` · Waiting N` | 进行中 (N) / 已完成 (N)、` · 等待中 N` | `subagents.panel.active` / `subagents.status.waiting` |
| 分区空态 | `No active subagents` / `No active subagents in this live snapshot` | 没有活跃的子智能体 / 此 live 快照中没有活跃的子智能体 | `subagents.panel.noActive` |
| 更多 | `Show more` | 显示更多 | `codex.summaryPanel.card.showMore` |
| 子代理详情 | `Back to subagents` / `Transcript unavailable` / `Loading subagent transcript…` / `No messages` / `Unable to load transcript` / `Read-only transcript` | 返回子智能体 / 对话记录不可用 / 正在加载子智能体对话记录… / 没有消息 / 无法加载对话记录 / 只读对话记录 | 仅 `subagents.panel.back` 有 key |
| 子代理状态徽标 | `Active`/`Waiting`/`Done`/`Unknown`（枚举名） | 处理中 / 等待中 / 已完成 / 状态未知 | `subagents.status.*` / `subagentsPanel.working` |
| 兼容历史角色 | `User`/`Assistant`/`Tool`/`System`（枚举名） | 用户 / 助手 / 工具 / 系统 | 无对应 key |

保留不变（有意为之）：

- 未知回执安全提示前缀 `Unknown：…`（`pty-create-unknown` / `pty-input-unknown`）——本仓跨页面既有状态令牌，
  调度页同样使用并被测试断言（如 `ScheduledPageInteractionTest`）。
- `PtyTerminalGrid` 的 `stateDescription`（`N columns M rows; revision R; lifecycle`）——结构化无障碍字符串，
  测试用正则解析，保持英文。

## §3 实现不变式

- 枚举 `name`（`SubagentSection` / `ChildAgentStatus` / `ChildTranscriptRole`）仍是稳定身份，用于状态、持久化、
  测试与语义标签；新增 `label` 仅承担显示。
- 标签页 `key`（`ThreadPanelTab.name`）与全部 `testTag`（`tab:*`、`terminal-*`、`pty-*`、`menu:*`）不变；
  只有 `HarnessButton`/`HarnessIconButton`/`HarnessTextField` 由文字派生的默认 tag 随文案改变（测试同批更新）。
- `HarnessTabs` 的关闭按钮改传 `closeLabel = { "关闭" + it.title }`，不再使用默认 `"Close ${title}"`。

# 契约：子 Agent 面板详情节与区块标题（2026-10-12）

范围：会话右侧「子智能体」面板的两处 Codex 结构——列表区块标题（进行中 / 已完成 · {count}）与详情视图
开头的「状态」「委派的任务」小节。只对齐可见结构与文案；不新增读取、生命周期或授权语义。

## §1 只读依据

来源（只读，未联网）：`/tmp/asar-new/webview/assets/zh-CN-b05cfd55c534.js` 与
`chatgpt-subagents-panel-e608a4ff5e56.js`。取证见
`../verification/subagent-detail-sections-2026-10-12/entry-bw1/logs/asar-subagent-panel-evidence.txt`。

| 项 | 内容 |
| --- | --- |
| 区块标题 | `subagents.panel.active` = 进行中（**不带**计数）；`subagents.panel.done` = 已完成 · {count}（值 = 已完成代理数） |
| 空态 | `subagents.panel.noActive` = 没有活跃的子智能体（仅 Active 段） |
| 列表上限 | Active `visibleItemLimit:4`，Done `visibleItemLimit:10`（本仓已一致：`visibleChildren` 取 4 / 10） |
| 详情小节 | 组件 P 依序渲染：`subagents.panel.status`（状态）、`subagents.panel.delegatedTask`（委派的任务）、`subagents.panel.response`（最终回复） |
| 小节结构 | `F(e)` = `section { h2(text-sm font-medium text-tertiary){title}; div(text-sm leading-6){body} }` |
| 状态体 | 状态色在 `status===failed` 时标红；其余为默认色 |
| 委派的任务体 | `o.prompt`，`whitespace-pre-wrap`（保留换行） |
| 最终回复体 | 可用时是 Markdown；加载中 `response.loading`（正在加载最终回复…）；不可用 `response.unavailable`（暂无最终响应。） |

## §2 本仓映射

| 位置 | 变化 | 依据 |
| --- | --- | --- |
| `SubagentSection.heading(count)` | 新增：Active → 「进行中」，Done → 「已完成 · $count」 | `subagents.panel.active` / `.done` |
| `SubagentSectionView` | 标题由 `label + " (N)" (+ 等待中 N)` 改为 `section.heading(all.size)` | 与 Codex 计数规则一致 |
| `ChildDetailPanel` | 头部之下新增「状态」「委派的任务」两个 `DetailSection` | `subagents.panel.status` / `.delegatedTask` |
| `DetailSection` | 新增私有 Composable：muted 小标题 + 默认色正文，带 `testTag` | 对齐 `F(e)` 的结构与层级 |

数据来源：

- 「状态」正文取 `ChildAgentSummary.status.label`（`ChildAgentStatus.label`）。
- 「委派的任务」正文取 `ChildAgentSummary.objective`（`SessionDescendant.objective` → `ChildAgentSummary.objective`），
  仅当非空白时渲染；保留原始换行。

## §3 不变式

- 只读：两个小节只消费已有 `SubagentOverview`/`ChildAgentSummary` 字段，不触发任何新读取、生命周期或写入。
- `SubagentOverview.children(section)` 的过滤与排序、`visibleChildren` 的 4/10 上限、Section 空态判定不变。
- 详情仍保留本仓既有的 lineage-checked 历史（`ChildTranscriptEntry`/typed `Message`）与其只读边界；
  小节只是加在其上方。
- `SubagentSection.label` 保留（仍是「进行中」/「已完成」），`heading` 只处理计数格式。

## §4 未验证边界

- **「最终回复」小节未实现**：Codex 的正文来自子代理 `statusMessage` 或专门的最终回复查询
  (`chatgpt-subagent-final-response-query`)，本仓 `SessionDescendant`/`ChildHistoryResult` 没有该数据源，
  故不渲染，避免虚构完成响应。
- Codex 的详情视图**没有**完整会话记录（只有状态/委派的任务/最终回复三个小节）；本仓在此之上仍渲染
  lineage-checked 历史，属结构性差异。
- 状态体在 Codex 里 `failed` 时标红；本仓 `ChildAgentStatus` 无 Failed（只有 Active/Waiting/Done/Unknown），
  故不做红色态；Unknown 显示「状态未知」。
- Codex 把三小节与正文放在同一滚动容器；本仓把两小节固定在头部之下、历史之上，未做同一滚动容器。
- 无原生窗口 / 像素证据。

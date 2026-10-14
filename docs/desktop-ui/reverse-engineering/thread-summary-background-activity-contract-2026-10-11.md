# Thread 摘要面板「后台活动」小节：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。原始片段见 `verification/thread-summary-background-activity-2026-10-11/entry-au1/logs/asar-summary-activity-evidence.txt`。

| 编号 | 观察到的内容 |
| --- | --- |
| B01 | 摘要面板有独立于 Environment 的两个「后台活动」小节：`sectionKey: 'background-subagents'` 与 `sectionKey: 'background-tasks'`（`local-conversation-thread-c8c5e92ea89e.js`）。两者都只在有行时渲染（`if (u.length === 0) return null` / `if (!a \|\| o.length === 0) return null`）。 |
| B02 | 两小节共用一个行渲染器 `$b`：subagent 行是 `ItemButton`（onClick → `onOpenBackgroundAgent`），leading 为头像，label 为 agent 名，`status === 'active'` 时显示 `codex.localConversation.backgroundAgents.activeLabel`（=「运行中」）的 shimmer meta；terminal 行是 `Item`（trigger onClick → `onOpenTerminal`），leading 为终端图标，label 为 `command`（为空时 `codex.localConversation.backgroundTerminals.defaultLabel` =「后台终端」），带 `stop`（「停止所有后台终端」）动作。 |
| B03 | 小节标题：`background-subagents` 用 `Yb type='subagents'`，`background-tasks` 用 `Yb type='processes'`；两小节都有 `SectionCount` 计数徽标（subagents 计数隐藏于有 inline agent 时）。对应的标题文案键是 `backgroundTasks.title.subagents`（=「子智能体」）与 `backgroundTasks.title.backgroundProcesses`（=「后台进程」）。 |
| B04 | 同文件可观察到的摘要面板小节集合：`environment`、`environment-creation-*`、`plan`、`tool-sources`、`created-tasks`、`background-subagents`、`background-tasks`、`pull-requests`、`side-chats`、`triggers`、`chrome-tabs`、`computer-use-pip`（后两者属排除项）。本批只做 `background-subagents` 与 `background-tasks`。 |

## 2. 已观察到与未观察到的语义

- **已观察**：两小节独立于 Environment；有行才显示；subagent 行显示 agent 名与「运行中」meta，点击把用户带到该后台 agent；terminal 行显示命令（空则「后台终端」），点击打开该终端输出；计数徽标；小节标题分别是「子智能体」「后台进程」。
- **未观察到**：`Yb` 图标字形的精确像素；subagent 行的 `diffStats`（`linesAdded/linesRemoved`）来源与尺寸；`autoCollapse`（全部 done 时折叠成「{count} 完成」）的精确交互；terminal 行的「停止所有后台终端」在此处的完整后端语义；`showInlineActivity`（已内联显示的 agent 不再重复）判定；原版同状态的像素值。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `summary/ThreadSummaryActivity`（新增，shared） | 纯投影：`ThreadSummaryActivity(kind,id,label,meta,running)`；`threadSummarySubagents(PanelLoad<SubagentOverview>)` 只把成功读取的子会话映射成行（运行中/等待中在前、其余按 recency），`threadSummaryProcesses(inventory)` 把有限后台进程清单映射成行（命令行首非空行，空则「后台终端」）；文案常量取自原版 i18n（「子智能体」「后台进程」「运行中」「后台终端」）。不可用/加载/空/错误读取一律返回空列表，不发明行。 |
| `summary/ThreadSummaryActivityController`（新增，shared） | 只读控制器：绑定 `ThreadPanelsOwner`，`refresh()` 复用 panels repository 的 `readOverview` 与 `readTerminalSelection(owner, null).inventory`，按 owner 身份与 request generation 栅栏后发布 `ThreadSummaryActivityState`。不打开 tab、不启动进程、不创建 agent；`readChildHistory`/`OperateTerminal` 等一律不调用。 |
| `summary/ThreadSummaryPanel`（shared） | 新增可选参数 `subagents`/`processes`/`onOpenActivity`；在 Environment 之后、仅当列表非空时渲染 `thread-summary-section-subagents` / `-processes`（标题 + 计数徽标，`thread-summary-section-<slug>-count`）与行 `thread-summary-subagent-<id>` / `thread-summary-process-<id>`（可点击，运行中 meta 用 Success 色）。空列表不画小节。 |
| `HarnessApp`（shared） | 新增 `ThreadSummaryActivityController`（`remember(conversationScope)`），随 owner 绑定/解绑；摘要 island 打开且为 Thread 页且有 owner 时触发一次 `refresh()`；行点击 → 打开对应 tab（子 Agent/终端），并在 panels 控制器已持有该行时尽力深选（`SelectChild`/`SelectTerminal`，对未知行安全 no-op）。 |

### 3.1 未实现（明确不主张）

- subagent 行的 `diffStats` 徽标、`showInlineActivity` 去重、`autoCollapse`「{count} 完成」折叠。
- terminal 行的「停止所有后台终端」批量停止动作（本仓终端控制仍是按进程的显式操作）。
- 摘要面板其余小节：`tool-sources`（Sources）、`created-tasks`、`plan`、`pull-requests`、`side-chats`、`triggers`、`environment-creation`。
- `chrome-tabs`、`computer-use-pip`（属排除范围）。
- 原版像素：本机 `screencapture` 仍返回 `could not create image from display`，无原生窗口证据。
- 原版图标的精确像素字形（本批复用既有 `User`/`ChevronRight` 图标，未新增终端字形）。

## 4. 证据

- 验收：`docs/desktop-ui/verification/thread-summary-background-activity-2026-10-11/entry-au1/`
- 抽取片段与本地化字符串：同上 `logs/asar-summary-activity-evidence.txt`。

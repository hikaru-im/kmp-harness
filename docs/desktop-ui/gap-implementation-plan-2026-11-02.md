# 缺口逐项实施计划

状态：Proposed  
规划日期：2026-11-02  
输入：[逐页面 · 逐组件 · 逐功能核对](page-component-feature-audit-2026-11-02.md)、[基于 decode-codex 还原代码的任务规划](restored-code-task-plan-2026-11-02.md)  
实现基线：`3f6648a2`（`feature/harness-25-scrollbar`）

## 一、原则

1. **一项一验收**：每个工作项是可独立合并、独立验收的交付，不把多个页面塞进同一个大改。
2. **不做空壳**：没有真实数据源或服务的 UI 保持禁用并写明原因；先补协议/数据源，再上界面。
3. **测试跟随能力**：共享逻辑放 `apps/shared/test`；Skia、窗口、全局热键等原生能力放 `apps/jvm-app/test`。
4. **复用现有基础设施**：设置走 `Store → Repository → Compose`（参照 `GeneralSettingsRepository`）；Git 走 `GitController` + 受控进程夹具；插件走 `DesktopUserPlugins`；摘要走 R1 的 `ThreadSummarySection` 原语。
5. **只提取契约**：不复制 restored 代码，只取行为、常量与官方文案。

## 二、总览

规模为粗估：S ≈ 1–2 天，M ≈ 3–5 天，L ≈ 5–10 天；不含评审与跨端联调。

| ID | 工作项 | 优先级 | 波次 | 规模 | 依赖 | 审计条目 / 现有任务 |
| --- | --- | --- | --- | --- | --- | --- |
| R4 | Git Diff 阅读能力 | P1 | 1 | M | R3 | 审计 P1-4 / Kaneo #36 |
| G1 | 布局状态持久化 | P0 | 1 | S | 无 | 审计 P0-3 |
| R2 | 子 Agent 面板最终回复与 diffStats | P1 | 1 | S | R1 | 审计 P1-12 / Kaneo #35 |
| G2 | 审批交互（协议 + UI + 运行时门控） | P0 | 2 | L | Kaneo #17 的策略决定 | 审计 P0-1 / Kaneo #17 |
| G3 | 会话内 diff 卡 | P0 | 2 | M | G2b | 审计 P0-2 |
| G4 | Git 偏好与 push/pull/PR | P1 | 3 | M | 无 | 审计 P1-8 |
| G5 | 设置导航、宿主过滤与内容搜索 | P1 | 3 | M | 无 | 审计 P1-6 |
| G6 | General 偏好接线 | P1 | 3 | S | G1 | 审计 P1-6 |
| G7 | Connections（SSH / WSL / 登录） | P1 | 3 | L | 凭据与 Host 能力核对 | 审计 P1-7 |
| G8 | 定时任务编辑器与列表 | P1 | 3 | M | 执行器 | 审计 P1-9 |
| R5 | 行内评论与 apply patch | P1 | 3 | L | R3、R4、G3 | 审计 P1-5 / Kaneo #37 |
| R6 | 插件 MCP 真实连接与授权 | P0 | 3 | L | 无 | 审计 P1-10 / Kaneo #34 |
| R7 | 插件 hooks 与 scripts 生命周期 | P1 | 3 | M | 无 | 审计 P1-10 / Kaneo #38 |
| G9 | 侧栏交互（排序 / 置顶 / hover / 渐隐） | P1 | 4 | M | G1 | 审计 P1-11 |
| G10 | 右侧面板 tab 与摘要数据接线 | P1 | 4 | M | R2、G3 | 审计 P1-12 |
| G11 | 插件市场、安装与详情 tab | P2 | 4 | L | R6、R7 | 审计 P1-10 |
| G12 | 新标签页工具与地址栏 | P2 | 4 | S | 终端 | 审计 P2-13 |
| G13 | 项目页 / 账号页 / 死代码清理 | P2 | 4 | M | 产品决定 | 审计 P2-14/15 |
| G14 | 跨页面能力（查找 / 快捷聊天 / 热键 / 通知） | P2 | 4 | L | 无 | 审计 P2-16/17 |
| G15 | 主题预设与外观高级项 | P2 | 4 | S | 无 | 审计 P2-18 |
| R8 | 终端链接、原生窗口与远端 | P1 | 4 | M | 无 | Kaneo #39 |
| R9 | 同状态像素对照与 token 偏差 | P1 | 4 | M | 全部页面 | Kaneo #40 |

## 三、既有 R 任务的增量

以下任务已在 [restored-code-task-plan-2026-11-02.md](restored-code-task-plan-2026-11-02.md) 有完整契约，这里只补审计发现的增量与验收口径。

### R2 — 子 Agent 面板最终回复与 diffStats

- 最终回复节：数据源不可用时显示显式 unavailable，不伪造内容。
- diffStats 徽标数字必须来自真实 diff stats；无 stats 时不显示徽标。
- 折叠行为复用 R1 的 `ThreadSummarySection`，不另建一套状态。
- 验收：有 typed 历史 / 无数据两条路径的 interaction test。

### R4 — Git Diff 阅读能力

- 范围：sticky 文件头、按 hunk 展开上下文、大 diff 横幅与“加载完整文件”、diff 内搜索与命中跳转。
- 大 diff 阈值取 restored `large-diff-banner.tsx` 的常量，写成可注入参数，用固定 fixture 测。
- 布局选择（`GitDiffLayout`）已存在，不重做。
- blame 只列为可选项：需要 `git blame` 解析与 gutter 渲染，若本波不做，在文档中明确移出 R4。
- 验收：每项独立 interaction test；大 diff 记录首帧与滚动指标。

### R5 — 行内评论与 apply patch

- 前置：R4 的行选择、G3 的 patch 展示。
- 模型取 restored `InlineCommentPosition` 的 path / side / startSide / startLine / line；草稿不写入工作树。
- apply 复用 `GitDiffApply.kt`，不重复实现补丁应用。
- 验收：patch 生成与应用单测；拒绝/冲突路径；评论草稿不落盘到 Git。

### R6 — 插件 MCP 真实连接与授权

- 连接、授权、工具列表/调用、超时与错误态；凭据只写不读，不进日志与 UI。
- 无授权时动作显式不可用，不静默失败。
- 验收：受控本地 MCP server 的集成测试（连接、授权、调用、断开、错误）。

### R7 — 插件 hooks 与 scripts 生命周期

- hooks 配置读写与启停；scripts 启动/停止/退出码；目录型 apps 登记。
- 同时补 `DesktopUserPlugins` 的本地卸载，供 G11 复用。
- 验收：配置落盘可回读；启停状态机测试；scripts 失败不吞错。

### R8 — 终端链接、原生窗口与远端

- 链接识别与打开动作对齐 `terminal-link-handler.ts`；原生窗口组合截图；远端链路依赖真实 Host。
- IME 已由 `TerminalTextInputBridge` 覆盖，只做回归。

### R9 — 同状态像素对照与 token 偏差

- 每个已实现页面至少一组同状态对照；token 偏差与文案偏差各一份清单。
- 验收：对照图归档 `docs/desktop-ui/captures/`；每条偏差有 restored 证据或接受理由。

## 四、新增工作项

### G1 — 布局状态持久化（P0，波次 1）

- **目标**：重启后保留 navigator 模式与宽度、pinned rail、摘要小节展开、tab 条布局。
- **现状**：`HarnessApp.kt` 的 `navigatorMode`、`navigatorWidth`、`pinnedRailDestinations`、`ThreadSummarySectionState` 都是 `remember`；`GitPageComponents.kt` 的 diff 工具栏状态同样不落盘。
- **落点**：新增 `client/shell/ShellLayoutSettings.kt`（模型）、`ShellLayoutRepository.kt`（状态机与校验）、`apps/jvm-app/src/.../settings/DesktopShellLayoutStore.kt`；`main.kt` 注入，`HarnessApp`/`AppShell` 消费。
- **步骤**：
  1. 定义 `ShellLayoutSettings(navigatorMode, navigatorWidth, pinnedRailDestinations, summarySections, tabStripLayout)`，宽度在读取时按 `NavigatorMinWidth..NavigatorMaxWidth` 夹取。
  2. 按 `GeneralSettingsRepository` 的 `Store → Repository` 模式实现；拖拽期间只在拖动结束时提交一次，失败保留上一次成功值。
  3. 写入采用临时文件 + 原子替换，损坏文件回退默认值并记录一次非阻塞错误。
  4. `HarnessApp` 提升状态来源；`summarySectionState` 接 R1 已预留的 persist 回调。
  5. diff 工具栏与页面滚动位置列为 G1b，单独提交，不阻塞 G1a。
- **验收**：重启保留四项；越界宽度被夹取；连续拖拽只写一次；损坏文件回退默认；shared 单测 + desktop interaction test。
- **风险**：NTFS 卷上的原子替换需复用现有 Desktop store 的写法，不新造文件协议。

### G2 — 审批交互（P0，波次 2）

- **状态（2026-11-02）**：G2a 已完成（协议、controller 待审批状态、审批卡、断连即拒绝）；G2b 等待 Kaneo #17 的权限策略决定，运行时门控与 gateway respond 尚未接线。
- **目标**：工具执行前可按策略请求用户审批，并在对话内呈现审批卡；拒绝、超时、断连都不执行工具。
- **现状**：`MessageContent` 只有 `TextContent / ReasoningContent / ToolCallContent / ToolResultContent`；`rg "PermissionRequest|ApprovalRequest"` 无命中；Host API 文档只为 `Interaction.respond` 保留了“审批和用户问题响应”的位置，代码未实现。
- **落点**：`client/connection/Connection.kt`（事件与内容）、`ConversationController`（待审批状态）、`client/app/ConversationComponents.kt` 或新 `ApprovalCard.kt`、`modules/tools/Tools.kt`（门控接口）、`modules/agent-loop/AgentLoopAgent.kt`（执行前调用）、`modules/api` 的 session gateway（respond 入口）。
- **步骤**：
  1. **G2a 协议与 UI**：定义 `ApprovalRequest(id, sessionId, toolName, command, reason, choices)` 与 `ApprovalDecision`；controller 维护 pending 映射；审批卡提供“允许一次 / 本会话允许 / 拒绝 / 取消”，并展示命令全文。
  2. **G2b 运行时门控**：在 `ToolsService` 执行前插入 `ToolApprovalGate`；agent-loop 在 gate 返回 pending 时挂起该工具，不阻塞同 turn 的并发安全工具。
  3. session gateway 增加 respond 方法，按请求 id 关联；断连、超时、会话取消一律按拒绝处理并记录原因。
  4. 权限档位只在“是否需要审批”层面生效；审批结果不改变会话权限模式，不做隐式持久授权。
  5. 与 Kaneo #17 的 sandbox / permission-presets 决定对齐；G2 不实现 seatbelt/bwrap 等沙箱执行器。
- **验收**：允许后仅执行一次；拒绝后工具未执行；超时/断连按拒绝；请求 id 不串会话；日志与 UI 不出现凭据；fixture interaction test + agent-loop 单测。
- **风险**：G2b 依赖 #17 对“哪些工具在哪些档位需要审批”的拍板；G2a 可先合入，避免 UI 等后端。

### G3 — 会话内 diff 卡（P0，波次 2）

- **状态（2026-11-02）**：G3a 已完成（内容类型、聚合卡片、四类 fixture、Git 跳转定位）；G3b 因运行时没有结构化文件编辑工具而暂缓，没有 producer 时不会产生卡片。
- **目标**：对话里直接看到本轮文件改动（路径、增删行数、可展开 patch），并可跳到 Git 页。
- **现状**：内容类型没有文件改动；生产运行时当前没有注册文件编辑工具，因此没有稳定 producer。
- **落点**：`Connection.kt` 新增 `FileChangeContent(path, kind, added, removed, patch, truncated)`；`ConversationComponents.kt` 新卡片；`ConversationController` 映射；`GitController` 提供“在 Git 中打开”定位。
- **步骤**：
  1. **G3a 模型与卡片**：新增内容类型与卡片（新增/修改/删除/重命名、增删行数、折叠 patch、展开、截断提示），用 fixture 覆盖四类。
  2. **G3b producer**：在文件编辑工具（`apply_patch` 类）落地时，从工具结果元数据生成 `FileChangeContent`；没有元数据时不猜、不解析自由文本。
  3. 同一消息多个文件改动聚合为一个卡片组，按路径排序；删除文件不提供“在 Git 中打开”。
  4. patch 超过上限时只显示摘要与截断说明，不加载全文。
- **验收**：四类 fixture；大 patch 截断；无 producer 时不产生卡片；点击跳转定位到对应 diff；interaction test。
- **依赖**：G3b 依赖 G2b 的工具改造或独立的文件工具任务；G3a 可先合入。

### G4 — Git 偏好与 push/pull/PR（P1，波次 3）

- **目标**：补齐 restored `settings/git-settings/` 的偏好，并让 Git 页具备推送、拉取与 PR 入口。
- **现状**：Git 设置页没有偏好组；`GitController` 只有 commit / branch / worktree / diff，没有 push/pull；没有 PR 页。
- **落点**：新增 `client/git/GitPreferences.kt` + Store/Repository；`SettingsGitPage` 增加偏好区；`GitController` 增加 push/pull；`GitPageComponents.kt` 增加 PR 入口。
- **步骤**：
  1. 偏好四项：分支前缀、commit instructions、PR instructions、worktree 自动清理保留数；分支前缀接入 worktree 创建默认值。
  2. push / pull：先检测 remote 与 upstream；push 必须显式确认，不提供 force；失败保留 stderr 摘要，不吞错。
  3. PR：优先读取当前分支已有 PR 的状态；没有 `gh` 或未登录时显示显式不可用，不自动创建 PR。
  4. 偏好写入沿用设置 Repository 模式，读取失败回退默认值。
- **验收**：偏好重启保留；分支前缀生效；push 需确认；无 upstream/remote 时给出可操作错误；进程测试覆盖成功与失败。

### G5 — 设置导航、宿主过滤与内容搜索（P1，波次 3）

- **目标**：设置导航对齐 restored 的分组结构，并支持按宿主过滤与按设置项内容搜索。
- **现状**：Kotlin 3 组 8 行；restored 4 组 27 个 slug；搜索只过滤导航标签；没有宿主下拉。
- **落点**：`shell/SettingsNavigatorModel.kt`、`shell/AppShell.kt`、新增 `client/settings/SettingsSearchIndex.kt`。
- **步骤**：
  1. 导航模型支持四组与 Archived 分组；只登记真实可打开的页面，未接入的 slug 不显示空壳。
  2. 宿主下拉：All settings / 当前 host；按 host 能力过滤可见项。
  3. 内容搜索索引覆盖页面标题、分组、设置行标题与描述；搜索结果点击直达对应行。
  4. 逐页补齐按 G7/G8/R6/R7 的真实能力推进，不预先铺 19 个空页。
- **验收**：分组与已实现页一致；搜索能命中设置行描述；宿主过滤生效；无死链。

### G6 — General 偏好接线（P1，波次 3）

- **目标**：每个 General 行要么真实生效并持久化，要么显式禁用并写明原因。
- **现状**：13 个偏好行里只有 `confirm-close` 与 `plugins` 可用。
- **落点**：`client/page/SettingsGeneralPage.kt`、`GeneralSettingsRepository.kt`，布局相关接 G1。
- **步骤**：
  1. 可接线项：`full-view`（新任务布局）、`bottom-panel`（标题栏控件）、`terminal-location`（终端位置）、`licenses`（静态许可证页）；前两项写入 G1 的布局设置。
  2. 保持禁用并写明依赖：`menu-bar`、`prevent-sleep`、`compress-history`、`prompt-suggestions`、`task-folder`、`default-file-app`、`language`（只做 zh-CN）。
  3. 禁用行提供原因文案与可访问描述，不再是无说明的灰开关。
- **验收**：可接线项重启保留；禁用项有原因；无“点了没反应”的行；interaction test。

### G7 — Connections（SSH / WSL / 登录）（P1，波次 3）

- **目标**：Connections 页能管理远端连接与登录状态，不再只有占位。
- **现状**：无 SSH/WSL、无登录/登出、无设备授权、无重连；`RemoteConnection` 已存在但未接设置页。
- **落点**：`client/page/SettingsConnectionsPage.kt`（或现有页）、`client/connection/RemoteConnection.kt`、`modules/credentials`。
- **步骤**：
  1. 先做 1 天能力核对：RemoteConnection 当前支持的传输、认证与重连语义；核对结果写回本文档再开工。
  2. SSH：主机配置增删改、连接测试、凭据只写不读；WSL 在 macOS 上显式不可用。
  3. 登录/登出/设备授权取决于认证后端；没有后端时只显示“未接入”，不做假登录。
  4. 重连：连接状态、失败原因、手动重试；不做静默重试风暴。
- **验收**：主机配置重启保留；连接测试成功/失败路径；凭据不出现在 UI 与日志；未接入项显式说明。
- **风险**：认证后端与 Host 能力可能把本项切成两部分，先交付 SSH 管理。

### G8 — 定时任务编辑器与列表（P1，波次 3）

- **目标**：保存不再强制 Paused；列表分 Current/Paused；补齐编辑器字段与下次运行时间。
- **现状**：`ScheduledRepository.save` 固定构造 `ScheduledTaskStatus.Paused`；`ScheduledTasksPage` 无调用点；编辑器没有 RRULE / heartbeat 间隔 / reasoning。
- **落点**：`client/scheduling/ScheduledRepository.kt`、`ScheduledManagementPage.kt`、`ScheduledManagementNavigator.kt`、`client/page/ShellPages.kt`。
- **步骤**：
  1. 保存保留用户选择的状态；有执行器时 Active，没有执行器时保存为 Paused 并提示原因。
  2. 列表按 Current / Paused 分区，行内提供运行、暂停、恢复、删除；补 Next run 行。
  3. 编辑器补 RRULE、heartbeat 间隔、reasoning；与 restored `automations/` 的字段逐项对照。
  4. 删除或接入 `ScheduledTasksPage` 死代码，只保留一个入口。
- **验收**：保存状态正确；分区与行操作生效；下次运行时间由真实调度计算；执行器单实例；repository 与 interaction test。

### G9 — 侧栏交互（P1，波次 4）

- **目标**：补齐拖拽排序、置顶最近分区、hover card、滚动渐隐与 worktree 移动确认。
- **现状**：五项都没有；排序与置顶没有持久化。
- **落点**：`shell/AppShell.kt`、`client/app/HarnessApp.kt`、`client/page/ShellPages.kt`、G1 的布局 Store。
- **步骤**：
  1. 会话/项目拖拽排序：优先走 Host workspace 的 `insertBefore`；无 Host 时本地持久化。
  2. 置顶最近分区与相对时间、状态 chip；hover card 显示项目信息。
  3. 滚动渐隐只在内容溢出时出现；worktree 移动前确认。
- **验收**：排序重启保留；键盘可操作；渐隐不误伤短列表；确认对话框覆盖取消路径。

### G10 — 右侧面板 tab 与摘要数据接线（P1，波次 4）

- **目标**：摘要面板的 plan / outputs / sources / side chats 接真实数据；右侧面板按数据出现 tab。
- **现状**：`HarnessApp` 调 `ThreadSummaryPanel` 时未传 `plan`，计划卡实际不显示；右侧面板只有终端与子 agent。
- **落点**：`client/app/HarnessApp.kt`、`client/summary/ThreadSummaryPanel.kt`、`client/panels/ThreadPanelModels.kt`、`ThreadPanels.kt`。
- **步骤**：
  1. 先把已有数据源接上：plan、outputs、sources、side chats；没有数据的 section 不渲染。
  2. 右侧面板增加 Plan / Diff（依赖 G3）tab；无数据时不显示 tab，不画空面板。
  3. tab 状态与摘要展开状态持久化交给 G1。
- **验收**：每个 section 有数据才出现；tab 与数据一致；无 placeholder；interaction test。

### G11 — 插件市场、安装与详情 tab（P2，波次 4）

- **目标**：插件详情补齐 Skills / Apps / MCPs tab，并支持本地安装与卸载。
- **现状**：只有本地已安装列表与能力名；`DesktopUserPlugins.registerLocal` 已存在，但没有卸载入口与详情 tab。
- **落点**：`client/plugins/InstalledPluginsPage.kt`、`PluginDetail*`、`modules/bundle/desktop/DesktopUserPlugins.kt`。
- **步骤**：
  1. 详情 tab：Skills / Apps / MCPs / Hooks，按 R6/R7 的真实能力填充；未接入项显示原因。
  2. 本地安装：选择目录 → `registerLocal` → 刷新列表；卸载：新增 `unregister`，二次确认。
  3. 市场与分享需要目录、信任与网络模型，暂不实现；在页面上明确“本地安装”而非假装有市场。
- **验收**：本地安装/卸载可回读；详情 tab 与能力一致；失败不吞错；无远程请求。

### G12 — 新标签页工具与地址栏（P2，波次 4）

- **目标**：新标签页的工具卡要么可用，要么显式说明不可用；地址栏行为明确。
- **现状**：只有“变更”可用，终端/文件/侧边聊天禁用；地址栏不可编辑。
- **落点**：`client/page/NewTabPage.kt`。
- **步骤**：
  1. 终端卡接入会话终端；文件卡接入 Host 的 `pickDirectory`/`listDirectory`；变更卡保持。
  2. 侧边聊天在 G14 的快捷聊天落地前保持禁用并写明依赖。
  3. 地址栏二选一并在文档记录：做成命令/搜索入口，或明确本客户端不是浏览器、保持只读展示。推荐前者，最小实现为“快速打开页面/设置”。
- **验收**：可用卡片能导航；禁用卡有原因；地址栏行为有测试或明确接受理由。

### G13 — 项目页 / 账号页 / 死代码清理（P2，波次 4）

- **目标**：消除不可达页面与命名不一致，项目页与账号页给出明确产品决定。
- **现状**：`ProjectsPage`、`ProjectPage`、`RecentTasksPage` 无调用点；`RailDestinations.kt` 注释称“资料库”打开项目工作区，但代码 label 是“资料库”并路由 `PageId.Library`；账号页是自建手机号/密码。
- **落点**：`client/page/ShellPages.kt`、`client/page/ProjectsPage.kt`、`client/shell/RailDestinations.kt`、账号页。
- **步骤**：
  1. 三选一：接入 Host workspace API（list/create/rename/archive）后启用；或删除死代码；或保留但标注实验。推荐先删除 `RecentTasksPage`，项目页接入 Host workspace。
  2. 修正“资料库/项目”的注释与 label 不一致，统一为一个名称。
  3. 账号页：对齐 restored 的登录入口，或明确记录“本产品使用自建账号”的接受理由。
- **验收**：无不可达页面；命名一致；项目页若有入口则 CRUD 可用；账号决定写入文档。

### G14 — 跨页面能力（P2，波次 4）

- **目标**：按独立能力补齐页内查找、快捷聊天、全局热键与系统通知。
- **现状**：四项都无落点；快捷键模型把查找组合键列为保留。
- **落点**：新增 `client/find/`、`client/quickchat/`、`apps/jvm-app/.../hotkey/`、`client/notifications/`。
- **步骤**：
  1. 页内查找（先做）：对话与 diff 两条查找栏，命中计数、上一个/下一个、Esc 关闭；只查已渲染文本。
  2. 系统通知：定时任务完成/失败发通知，尊重系统权限，不重复轰炸。
  3. 快捷聊天与全局热键：独立窗口 + 可配置全局快捷键；平台不支持时显式不可用。
- **验收**：查找在长对话与 diff 上可用；通知有权限拒绝路径；热键冲突可检测；每项独立 interaction test。

### G15 — 主题预设与外观高级项（P2，波次 4）

- **目标**：补齐代码主题预设、半透明侧栏、Dock 图标与主题导入/导出。
- **现状**：颜色/字体/对比度/光标/动画/差异标记已有；缺预设与平台项；字体平滑只存储未接线。
- **落点**：`client/page/SettingsAppearancePage.kt`、`AppearanceRepository.kt`。
- **步骤**：
  1. 代码主题预设列表（只收录 restored `themes/` 中可映射的 token）。
  2. 半透明侧栏与 Dock 图标：macOS 先做，其他平台显式不可用。
  3. 主题导出/导入/复制走 JSON，校验版本与字段，失败不覆盖当前主题。
- **验收**：预设应用并持久化；导入/导出往返一致；平台不支持项有说明。

## 五、依赖与执行顺序

```text
波次 1  R4 ── G1 ── R2
波次 2  G2a ── G2b ── G3a ── G3b
波次 3  G5+G6   G4   R6   R7   G7   G8   R5
波次 4  G9   G10   G11   G12   G13   G14   G15   R8   R9
```

- **G2b → G3b**：没有工具门控与文件编辑工具，就没有可信的 diff producer；G3a 可先用 fixture 合入。
- **G4/G5/G6** 互相独立，可并行；G6 的布局行依赖 G1。
- **G7** 先做能力核对，可能被认证后端切成两段；SSH 管理先交付。
- **G8** 依赖执行器可用；没有执行器时只修保存语义与列表，不假装能运行。
- **R5** 依赖 R4 的行选择与 G3 的 patch 展示，放在波次 3 末尾。
- **G10** 依赖 G3 的 diff tab 与 R2 的最终回复数据。
- **G11** 依赖 R6/R7 的真实能力；市场与分享不在本轮。
- **R9** 放最后，等页面稳定后一次性做同状态对照。

## 六、Kaneo 映射

| 工作项 | Kaneo |
| --- | --- |
| R2 | #35（已存在） |
| R4 | #36（已存在） |
| R5 | #37（已存在） |
| R6 | #34（已存在） |
| R7 | #38（已存在） |
| R8 | #39（已存在） |
| R9 | #40（已存在） |
| G2 | #17（扩展为审批协议 + UI + 门控；sandbox 仍归 #17） |
| G1 | #41 |
| G3 | #42 |
| G4 | #43 |
| G5 + G6 | #44 |
| G7 | #45 |
| G8 | #46 |
| G9 | #47 |
| G10 | #48 |
| G11 | #49 |
| G12 | #50 |
| G13 | #51 |
| G14 | #52 |
| G15 | #53 |

## 七、验收与回归

- 每项合并前跑：`./kotlin test --build-dir=/tmp/kmp-harness-build -p jvm --include-module <module> --include-classes <fqcn>`。
- 涉及 Skia/PDF/窗口/热键的测试放 `apps/jvm-app/test`；共享状态机放 `apps/shared/test`。
- 每项至少一条失败路径：拒绝、超时、断连、损坏配置、空数据或权限不足。
- 涉及用户可见文案的项，从 `restored/locales/zh-cn.ts` 取官方措辞，不自行发明。
- 每完成一项，在本文档追加“执行记录”，并同步 `restored-reference-index.md` 与 `page-component-feature-audit-2026-11-02.md` 的状态。

## 八、明确不做

- 沙箱执行器（seatbelt / bwrap / ACL）本身：仍归 Kaneo #17。
- 云端项目、云端环境、自动代码审查、浏览器/电脑操控、图片库、团队/计费/家长控制。
- 远程插件市场、分享链接与签名信任模型：没有目录与信任设计前不做。
- 多语言：只做 zh-CN，不引入 locale 层。
- 为对齐 restored 而复制其源码或私有资源。

## 九、执行记录

### 2026-11-02 — R2 子 Agent 最终回复与 diffStats（Kaneo #35，未提交）

- **最终回复节**：`ChildDetailPanel` 在状态/委派任务后新增「最终回复」，正文取 `ChildAgentDetail.finalReply`（typed 历史中最新 assistant 文本）；typed 历史缺失 → 「最终回复不可用」，无 assistant 文本 → 「尚无最终回复」。兼容 transcript 不再被当作最终回复。
- **列表预览**：Host 的 `SessionDescendant` 增加 `lastAssistantMessage` / `lastAssistantAtEpochMillis`，由 `LiveSessionDescendants` 从子会话消息中提取最新 assistant 文本；`preview` 顺序改为最终回复 → objective → statusSummary。可选读取失败不隐藏子会话，取消仍传播。
- **diffStats 徽标**：新增 `ChildDiffStats`，摘要面板子智能体行显示 `+N −N`；协议两侧增加可选 `diffStats`，只接受 Host 真实非零统计。当前 Host 没有 turn-diff 生产方，徽标在生产中保持隐藏。
- **autoCollapse**：沿用 R1 已接线的摘要面板小节原语。
- **验证**：shared 全模块 629/629；`SessionDescendantsContractTest` 7/7；`SessionDescendantsGatewayTest` 2/2；子 Agent/摘要面板 interaction 16/16；`ThreadPanelsInteractionTest` + `ThreadSummaryPanelInteractionTest` 30/30。
- **未做**：diffStats 的 Host turn-diff 采集；最终回复节不折叠（对齐 restored 的静态小节）。

### 2026-11-02 — G1a 布局持久化（Kaneo #41，未提交）

- **新增**：`client/shell/ShellLayoutSettings.kt`、`ShellLayoutRepository.kt`（`ShellLayoutStore` / `MemoryShellLayoutStore`）、`apps/shared/src@jvm/.../DesktopShellLayoutStore.kt`。
- **接线**：`main.kt` 在首帧前加载 `~/.harness/client-layout.json`；`HarnessApp` 恢复 navigator 模式/宽度、pinned rail、摘要小节展开，并在 350ms 去抖后保存。`ThreadSummarySectionState` 复用 R1 的 persist 回调，存原始小节键。
- **校验**：宽度按 240–520dp 夹取，NaN/Infinity 回落默认；`TemporarilyExpanded` 属于窗口宽度推导的瞬时态，不恢复；损坏文档回退默认值并保留可写性；保存失败保留上次成功值。
- **验证**：`ShellLayoutRepositoryTest` 6/6、`DesktopShellLayoutStoreTest` 2/2、`ShellLayoutPersistenceInteractionTest` 2/2、`ThreadSummarySectionInteractionTest` 7/7 回归。
- **未做**：G1b 的 diff 工具栏与页面滚动位置；tab 条布局需等 G10 的 tab 模型落定。

### 2026-11-02 — R4 Git Diff 阅读能力（Kaneo #36，未提交）

- **新增**：`client/git/GitDiffReading.kt`（阈值、hunk/gap 解析、上下文重建、行号映射、搜索）；`apps/shared/test/.../GitDiffReadingTest.kt`；`apps/jvm-app/test/.../GitDiffReadingInteractionTest.kt`。
- **阈值与横幅**：restored `editor-diff-page.tsx` 的 25 文件 / 2000 变更行（边界含等号）成为默认可注入参数；超过后显示 `此差异较大，每次仅显示一个文件` 横幅，并改为每次只展开一个文件。`large-diff-banner.tsx` 没有自己的阈值常量，复用自动展开阈值的选择记录在源码注释。
- **sticky 文件头**：沿用单个 `verticalScroll` Column，不改成 lazy 列表，保证正文仍是可整段选中的文本；`ScrollState` + `derivedStateOf` 求吸顶位移，`graphicsLayer` 平移、`zIndex` 覆盖，下一个文件头顶出。
- **上下文展开**：纯函数按 hunk 读取工作树文本，每次 20 行，重算 `@@` 头；暂存区 diff 与删除文件禁用；读取失败显示真实 unavailable 原因。索引 diff 禁用避免工作树与索引不一致时伪造上下文。
- **搜索**：不区分大小写字面匹配（含 `@@` 头），500 条上限；查找栏显示当前/总数、上/下一个循环、命中文件自动展开；unified 布局按 `TextLayoutResult` 行高精确滚动，正文高亮命中与当前命中，文件头显示命中数。
- **验证**：`GitDiffReadingTest` 7/7；shared git 包 141/141；`GitDiffReadingInteractionTest` 8/8（含阈值边界、单文件切换、吸顶、搜索、上下文、工作树不可读、暂存区禁用、性能指标）；既有 Git 页面 interaction 39/39。
- **性能指标**：25 文件 / 2000 变更行全部展开时首帧 216–471ms、连续 10 次滚动 650–818ms（随同进程已跑过的 UI 测试数变化）；测试只保留宽松上限，数字只作本机记录。
- **未做**：blame 移出 R4；行内评论/apply/revert 属 R5；工具栏与滚动位置持久化属 G1b；不引入 diff 虚拟化。

- **环境说明**：`GitCommitInteractionTest` 与 `git.DesktopGitCommitTest` 的 include-unstaged 提交用例在本机 15s 超时；后者完全不经过本批改动（`DesktopGitBackend` 未修改），与历史验证记录中的同类真实 Git 抖动一致。

### 2026-11-02 — G2a 审批协议与卡片（Kaneo #17，未提交）

- **协议**：`client/connection/Connection.kt` 新增 `ApprovalRequest(id, sessionId, toolName, command, reason, choices)`、`ApprovalChoice`（`allow-once` / `allow-session` / `deny` / `cancel`）、`ApprovalDecision`；构造器拒绝空 id、空工具名、空选项列表与重复选项。`SessionApi.respondApproval` 以默认实现返回不可用，G2b 接线前不假装能提交。
- **事件**：`LiveSessionEventNames` 增加 `approval/requested`、`approval/resolved`；`LiveApprovalEventCodec` 按 `{"request": …}` / `{"requestId": …}` 解码，畸形 payload 返回 null 而不打断流。
- **Controller**：`ConversationState.approvals: List<PendingApproval>` 保存待审批请求；`respondApproval(requestId, choice)` 只接受 Host 提供的选项、只提交一次，失败保留卡片并显示「无法提交审批决定，请重试。」。请求 sessionId 与当前会话不一致时忽略；`select()`、`turn/end`、`stopLive()`（换流/断连）都清空待审批，即断连按拒绝处理。
- **卡片**：新增 `client/app/ApprovalCard.kt`，`ApprovalPanel` 固定在 thread 列末尾（composer 之上）。卡片显示工具名、`是否运行此命令？`、可选择的命令全文（160dp 上限内滚动，不缩写）、原因、状态（待批准/提交中…）与错误；按钮按 Host 给出的选项过滤，顺序为取消 / 拒绝 / 本会话允许 / 允许一次，提交中或断连时全部禁用。
- **验证**：`ApprovalProtocolTest` 5/5、`ConversationControllerTest` 50/50（含 5 条审批用例）、`ApprovalCardInteractionTest` 10/10、shared 全模块 651/651。
- **未做（G2b）**：`ToolApprovalGate`、agent-loop 挂起、session gateway 的 respond 端点；等待 #17 对“哪些工具在哪些权限档位需要审批”的拍板。

### 2026-11-02 — G3a 会话内 diff 卡（Kaneo #42，未提交）

- **内容类型**：`Connection.kt` 新增 `FileChangeContent(path, kind, added, removed, patch, truncated, previousPath)` 与 `FileChangeKind`（added/modified/deleted/renamed），wire 名 `file-change`；构造器拒绝空路径、负行数与空 `previousPath`。
- **卡片**：新增 `client/app/FileChangeCard.kt`；同一消息内连续的文件改动聚合为一张卡片组，组内按路径排序，头部显示「{n} 个文件已更改」与 `+N −M` 汇总。每行显示 已创建/已编辑/已删除/已重命名、路径（重命名显示 `旧路径 → 新路径`）、行数与展开箭头。
- **展开**：逐文件独立展开；有 patch 时按行渲染并区分增删底色，超过 400 行只渲染前 400 行并提示「补丁过长，仅显示前 400 行」；producer 标记 `truncated` 时只显示「内容过大，无法内联渲染」，不猜 patch。没有 patch、没有截断、也没有 Git 动作的行不可展开。
- **Git 跳转**：非删除文件且提供了回调时显示「在 Git 中查看」；`HarnessApp` 记录一次性 `gitRevealPath` 并打开「变更」侧栏 tab（无侧栏时回退 Git 设置页），`GitDiffView` 消费后展开对应文件并滚动到正文起始。删除文件不提供该动作。
- **验证**：`FileChangeRunsTest` 5/5、`FileChangeCardInteractionTest` 7/7、shared 全模块 651/651、jvm-app 回归 92/92（对话、线程视觉、Git diff 阅读/视图/设置路由、右侧面板、子 Agent）。
- **未做（G3b）**：生产运行时没有结构化文件编辑工具，暂不生成 `FileChangeContent`；不解析自由文本、不猜 patch。

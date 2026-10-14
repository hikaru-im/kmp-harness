# Desktop Navigation and Page Plan

状态：历史架构规划。当前实施范围以 [2026-10-07 复刻计划](desktop-ui/replication-plan-2026-10-07.md) 的“已确认范围”为准；下文旧范围决定与之冲突时不再适用。定时任务、插件管理、Git/Worktrees、终端和子 Agent 面板纳入；图片库、团队管理、云端及其他明确排除项退出实施范围。

## 1. 目标和边界

KMP Harness 的目标是做一个以 Agent 工作流为中心的客户端，并吸收 Codex Desktop 的信息架构、导航层级、Shell 插槽和交互密度。复刻对象是可迁移的产品结构和视觉规则；Codex、ChatGPT、企业计费和云端插件市场的专属能力不进入第一版产品边界。

本轮只决定四件事：

1. 用户从哪里进入项目、任务和对话。
2. 当前 Harness 页面如何映射到新的页面模型。
3. 哪些页面沿用、改造、新增、延后或舍去。
4. Desktop、Android、iOS 共享哪些导航状态，以及各自如何呈现。

## 2. 参考实现的代码级证据

分析对象：

| 项目 | 值 |
| --- | --- |
| App | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| App version | `26.930.31730` |
| SHA-256 | `64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e` |
| 分析范围 | `webview/assets` 中的 Shell、Sidebar、Project、Task、Thread、Settings、Plugin、Library、Workspace bundle |

关键 bundle 和结论：

| Bundle | 观察到的职责 | 对 Harness 的启示 |
| --- | --- | --- |
| `sidebar-d1bfe8c77b62.js` | Scheduled 入口、任务筛选、任务状态和运行历史 | Task 是带状态和运行记录的领域对象，不应只当作一条会话标题 |
| `sidebar-b3619131cde6.js` | Recents、Pinned、Sections、Code Review 分组 | Sidebar 是多个数据源的导航投影，不是单一列表 |
| `sidebar-projects-icon-55225a1c26e6.js`、`sidebar-tasks-icon-a5050e1add74.js`、`sidebar-library-icon-a6e81a086025.js`、`sidebar-plugins-icon-8d185e42c466.js` | Projects、Tasks、Library、Plugins 的独立入口图标 | App Rail 和 Workspace Navigator 需要分层 |
| `projects-index-page-52d8e1d790a3.js` | 同时处理 `localProjects`、云端项目、`localThreadKeys`、`recentThreadKeys`、搜索和项目内聊天 | Project 页面是项目索引和子项展开层，不等同于 Thread 页面 |
| `local-conversation-page-c5fb95945be8.js`、`local-conversation-thread-4cbba7705144.js` | `local-thread`、`client-local-thread`、归档预览、项目和 workspace 关联 | Thread 是主要内容路由；transport session 不应直接成为一级页面 |
| `task-summary-739d44dbb9bd.js`、`task-unread-58e62da4c30d.js` | Task 摘要、计划、状态、未读和运行结果 | Task 可以投影到 Recent、Project 和 Thread 三处 |
| `settings-page-bbd5e3fa3b8c.js`、`settings-tabs-4bf8cf96da38.js` | Settings 采用 section 路由和子 tab，当前可见的 Worktrees tab 含 General/Environments | Settings 应是分区页面，而不是一个内容占位页 |
| `plugin-detail-page-1bcc86c68199.js`、`plugin-detail-view-e0f75c853544.js` | Plugin detail、连接、设置、共享和编辑 | Harness 第一版只保留已安装插件清单和本地详情 |
| `library-page-f644e8425dd0.js`、`library-file-route-915e80a083c6.js` | Library、文件/页面/空间、筛选和回收站 | 这是文件产品，不是 Agent 对话必需页面，先延后 |
| `thread-app-shell-chrome-db6a0c50aea0.js`、`thread-panel-state-eeea0dc1812b.js`、`thread-right-panel-state-891c0842f478.js` | Thread Shell、右侧 Panel、Summary、Toolbar slot | Project Context、Tool Details、Model Picker 应作为独立 Panel 状态 |

从用户提供的实际截图确认的视觉层级是：

```text
App Rail
├── Home
├── History / Recent
├── Projects
├── Library / Extensions
└── Account

Workspace Navigator
├── Workspace selector
├── New Chat / New Task
├── Projects
│   └── Project
│       ├── Tasks
│       └── Threads
└── Recent tasks

Main Surface
├── Thread titlebar
├── Thread timeline
└── Composer

Optional Context Panel
└── Project Context
```

因此，`Project`、`Task`、`Thread`、transport `Session` 不能继续使用同一个平级 `HarnessDestination` 表示。

## 3. 领域关系

```text
WorkspaceContext 1 ── * Project
Project          1 ── * Task
Task             1 ── * Thread
Thread           1 ── 1 TransportSession? 
```

### 3.1 WorkspaceContext

客户端当前使用的运行上下文，包含登录账号、租户、Host/远端连接、项目根目录和能力集合。它决定数据从哪里加载，但不是用户正在处理的任务。

在当前代码中，它由 `MemberAccount`、`BackendTenant`、`HostDescription`、`Connection` 等对象共同表达。第一版导航模型先使用稳定的 `workspaceId`，以后再把本地目录和远端 Host 的组合细化为 `WorkspaceContext`。

### 3.2 Project

一组相关任务和线程的容器，至少包含：

- `projectId`
- 显示名
- 本地根目录或远端 workspace 引用
- 最近更新时间
- 项目级 instructions/config
- 可选的关联 Host

Project 是 Sidebar 树和 Project 页面使用的对象。

### 3.3 Task

用户可识别的工作项，至少包含：

- `taskId`
- 标题和摘要
- `projectId?`
- `status`: queued/running/paused/completed/failed
- 最近更新时间和未读状态
- `threadId?`
- 运行记录摘要

Task 是 Recent 和 Project 子列表的对象。Scheduled/automation 只有在后端能力稳定后才进入该模型的扩展字段。

### 3.4 Thread

可打开的对话时间线，包含消息、工具调用、计划、产物和可选的 `taskId`。Thread 是主内容区的路由对象：

- `Home` 创建一个新的 Thread/Task。
- `Project` 打开项目下的 Task/Thread。
- `Recent` 打开最近的 Task/Thread。
- Thread 页面显示消息和 Composer。

### 3.5 TransportSession

当前 `SessionScreen` 使用的连接层会话，用于订阅 Relay stream、恢复历史和发送消息。它是 Thread 的运行时依赖，不作为一级导航页面。现有 `SessionSummary` 和 `SessionScreen` 保留，但在路由层改名为 Thread/Task 的数据投影，避免把网络会话误认为用户页面。

## 4. 目标导航结构

### 4.1 Desktop

```text
AppShell
├── AppRail
│   ├── Home
│   ├── Recent
│   ├── Library
│   ├── Images
│   ├── Plugins
│   ├── More
│   └── Account chip
├── WorkspaceNavigator
│   ├── WorkspaceHeader
│   │   ├── workspace selector
│   │   └── new task
│   ├── ProjectTree
│   │   ├── Project row
│   │   │   ├── project actions
│   │   │   └── task/thread rows
│   │   └── project context action
│   └── RecentTasks
├── MainContent
│   ├── HomePage
│   ├── ProjectsPage
│   ├── ProjectPage
│   ├── RecentTasksPage
│   ├── ThreadPage
│   ├── HostsPage
│   ├── PluginsPage
│   └── SettingsPage
└── Overlay / Panels
    ├── ProjectContextPanel
    ├── ToolDetailsPanel
    ├── ModelPickerPanel
    └── CommandPalette
```

### 4.2 一级入口顺序

第一版 Desktop 固定为 Codex 的六槽位 Rail，加底部账号 chip：

1. Home
2. Recent
3. Library（承载项目工作区路由）
4. Images
5. Plugins
6. More（打开账号面板）
7. Account chip

Hosts、Settings 和 Tenant 不放进 App Rail；它们从 Account 面板、Workspace selector 或设置入口进入。这样保留 Codex 的主工作区优先级，也避免把连接配置和用户工作混在一起。

> 最终取舍（2026-10-06，用户确认）：Rail 采用 Codex 的固定六槽位 Home / Recent / Library / Images /
> Plugins / More，底部保留账号 chip。Library 槽位承载本客户端的项目工作区路由（`PageId.Projects` /
> `PageId.Project`）；Images 是新的占位页面。`Hosts` / `Settings` 不再拥有 Rail 槽位，
> 改为账号面板内的入口，仍走既有的 `PageId.Hosts` / `PageId.Settings` 路由。More 槽位是
> 可用入口，打开账号面板。中间一版 "Secondary 渲染 Hosts/Settings" 的回填作废。
> 证据与取舍记录在 `docs/desktop-ui/reference/rail-measurement-2026-10-05.md`。

### 4.3 Desktop 与移动端的差异

| 能力 | Desktop | Android/iOS |
| --- | --- | --- |
| App Rail | 常驻窄栏，固定六槽位 Home/Recent/Library/Images/Plugins/More + 账号 chip | 底部导航或抽屉，最多保留 Home/Recent/Library/Account |
| Workspace Navigator | 常驻或可折叠左栏 | 独立 Project/Recent 页面，通过返回栈进入 |
| Project tree | 展开树，支持 pinned/recent | 列表 + drill-down |
| Thread | 主窗口，支持右侧 Panel | 全屏页面，Panel 变成 bottom sheet 或独立页 |
| Project Context | 右侧 panel | bottom sheet |
| Command Palette | 保留快捷键入口 | 延后，用页面内搜索和动作菜单代替 |
| Hosts/Settings | 独立设置页 | 独立设置页，使用系统返回栈 |
| Library | Rail 槽位承载项目工作区（`PageId.Projects`/Project） | 同左，走底部导航 |

共享的是 `NavigationState`、`PageId`、选中对象和返回关系；不共享 Desktop 的栏宽、窗口标题栏和快捷键实现。

## 5. 页面清单和决策

决策含义：

- **沿用**：保留当前领域能力，换成新的路由和视觉。
- **改造**：有可复用代码，但需要拆分模型或重做内容。
- **新增**：当前没有对应页面或状态模型。
- **延后**：保留扩展点和 `PageId` 位置，当前迭代不实现。
- **舍去**：没有 KMP Harness 领域对应物，不进入产品导航。

| Codex/目标能力 | 当前 Harness | 目标页面/对象 | 决策 | 说明 |
| --- | --- | --- | --- | --- |
| Home / New Chat | `HarnessHome` | `HomePage` | 改造 | 作为新任务入口、连接状态和空线程页；不再展示成独立的连接状态仪表盘 |
| Recent | 无 | `RecentTasksPage` | 新增 | 汇总最近 Task/Thread，支持未读、运行中和失败状态 |
| Projects index | 无 | `ProjectsPage` | 新增 | 展示项目列表、搜索、创建和最近更新时间 |
| Project detail | `ProjectContextState` 只有 visible | `ProjectPage` + `ProjectContextPanel` | 新增 | 项目树、项目 instructions、Host 和任务列表分开建模 |
| Project tree | `AppShell` 的平级 session 列表 | `WorkspaceNavigator.ProjectTree` | 改造 | Project > Task > Thread 三层展开 |
| Task/Thread | `Sessions` + `SessionScreen` | `ThreadPage(taskId?, threadId?)` | 改造 | 复用消息、历史恢复、订阅和 Composer；路由改用 Thread/Task 身份 |
| Account/Login | `AccountScreen` | `AccountPage` / auth gate | 沿用 | 保留登录、退出和本地模式；移出主导航内容区 |
| Tenant | `TenantScreen` | `Settings.Connection` / Workspace selector | 改造 | 租户是连接上下文配置，不作为独立工作页面 |
| Profile | `ProfileScreen` | `Settings.Account` | 改造 | 并入账号设置；保留 profile load 能力 |
| Hosts | `HostScreen` | `HostsPage` | 沿用 | 改造成 Workspace/Host 选择和连接诊断页 |
| Settings | `SettingsScreen` | `SettingsPage(section)` | 改造 | 采用 section 路由和 typed settings tabs |
| Plugin inventory | `PluginInventoryScreen` | `PluginsPage` + `PluginDetailPage` | 改造 | 第一版只做已安装插件、状态、Slot 来源和启停 |
| Tool Details | `PanelKind.ToolDetails` | `ToolDetailsPanel` | 保留 | 作为 Thread 右侧 Panel，后续接 Tool projection |
| Model Picker | `PanelKind.ModelPicker` | `ModelPickerPanel` | 保留 | 作为 Composer/Header 面板 |
| Command Palette | `PanelKind.CommandPalette` | `CommandPalette` | 延后 | 状态类型保留，核心导航稳定后实现 |
| Library / Files | 无 | `LibraryPage` | 延后 | 需要文件、产物和权限模型；第一版在 Thread 中显示产物入口。Rail 的 Library 槽位不指向该页，而承载项目工作区路由 |
| Images | 无 | `PageId.Images` | 新增（占位） | Rail 的 Images 槽位；当前只有空状态，图片仍在 Thread 中查看 |
| Scheduled / Automations | 无 | `ScheduledTasksPage` | 已实现本地版 | 独立 `PageId.ScheduledTasks`、专用 navigator、确定性内存仓库；真实远端自动化契约后接 |
| Code Review / PR / MR | 无 | 无 | 舍去 | 依赖 Codex 专属 SCM 工作流 |
| ChatGPT cloud project | 无 | 无 | 舍去 | KMP 先使用本地/远端 Harness Project，不复制云端 ChatGPT project 语义 |
| Team admin / billing / workspace credits | 无 | 无 | 舍去 | 不属于 Agent 客户端核心领域 |
| Generic marketplace | 无 | 无 | 舍去 | 保留插件 Catalog 扩展点，不做外部插件市场 |

## 6. PageId 和 NavigationState

页面路由应由共享模块定义，建议形态如下。具体 Kotlin 命名可以在实现阶段调整，但语义不能再退回平级 enum。

```text
PageId
├── Home
├── RecentTasks(filter)
├── Projects(query)
├── Project(projectId, childFilter)
├── Thread(threadId, taskId?)
├── Hosts
├── Plugins
├── PluginDetail(pluginId)
├── Account
└── Settings(section, childTab?)

OverlayId
├── None
├── ProjectContext(projectId)
├── ToolDetails(toolCallId)
├── ModelPicker
└── CommandPalette
```

`NavigationState` 至少包含：

```text
NavigationState
├── currentPage: PageId
├── backStack: List<PageId>
├── selectedWorkspaceId: WorkspaceId?
├── expandedProjectIds: Set<ProjectId>
├── selectedTaskId: TaskId?
├── selectedThreadId: ThreadId?
├── overlay: OverlayId
├── sidebarMode
└── navigatorSearchQuery
```

规则：

1. 点击 Project tree 的 Thread 只更新 `currentPage = Thread(...)`，不创建新的 transport session。
2. 点击 Recent 的 Task，若 Task 没有 Thread，则创建或恢复 Thread，再进入 `ThreadPage`。
3. 打开 Project Context 只更新 `overlay`，不替换当前页面。
4. Workspace/Host 切换使项目和线程查询失效，但不改变 `PageId` 的类型。
5. 本地模式可以没有登录和远端 Host；`Home`、本地 Project 和本地 Thread 仍然可用。
6. `ShellDestination` 只作为兼容适配层，不能继续承载业务页面全集。

## 7. 页面 Slot 目录

Slot 的 Owner 由宿主页面声明，插件只贡献内容。第一批目录如下：

| Slot | 类型 | Owner | 用途 | 当前状态 |
| --- | --- | --- | --- | --- |
| `app.rail.primary` | list | AppShell | Home/Recent/Library/Images/Plugins/More 入口 | 新增 |
| `workspace.header` | single | WorkspaceNavigator | Workspace selector 和 New Task | 新增 |
| `workspace.project.tree` | keyed/list | WorkspaceNavigator | Project、Task、Thread 树节点 | 新增 |
| `workspace.project.actions` | list | Project row | Rename、Open、Archive、Context | 新增 |
| `workspace.recent.tasks` | list | WorkspaceNavigator | Recent Task 投影 | 新增 |
| `thread.titlebar.actions` | list | ThreadPage | 分享、重命名、Panel 和 Host 动作 | 新增 |
| `thread.summary` | single | ThreadPage | Goal、Plan、Usage 和来源摘要 | 新增 |
| `thread.composer.actions` | list | Composer | 附件、模式、Agent 和模型动作 | 对应 DSH，待接入 |
| `thread.tool.view` | keyed | ThreadPage | Tool Call renderer | 对应 DSH，待接入 |
| `project.context.panel` | single | ProjectContextPanel | 项目来源、变更和任务入口 | 新增 |
| `settings.tabs` | list | SettingsPage | Provider、Plugin、Connection 等设置贡献 | 新增 |

现有 `ClientUiSlots.Root`、`SessionHeader`、`SidebarActions` 保留为迁移别名。实现新 Shell 时，先让旧 Slot 映射到新 Owner，再逐步删除旧名称，避免插件契约和页面重构同时断裂。

## 8. 现有代码拆分计划

### 8.1 `HarnessApp.kt`

当前文件同时承担连接状态、导航、页面实现、账号表单、设置页面和插件页面。拆分顺序：

1. 保留 `HarnessApp` 作为组装入口，只负责创建 `NavigationState`、连接状态和 `AppShell`。
2. 将 `HarnessDestination` 替换为 `PageId` 适配层，保留 `fromLaunchValue` 的兼容解析。
3. 将 `HarnessHome` 移到 `page/HomePage.kt`。
4. 将 `AccountScreen`、`TenantScreen`、`ProfileScreen`、`HostScreen` 移到 `page/settings` 或 `page/account`。
5. 将 `SettingsScreen` 拆成 `SettingsPage`、`SettingsNavigation` 和 section 内容。
6. 将 `PluginInventoryScreen` 拆成 `PluginsPage`、`PluginDetailPage` 和 Catalog 状态。
7. 将 `SessionScreen` 和 `ConversationComponents` 归到 `page/thread`，将 transport session 作为其输入。
8. 新增 `HomePage`、`RecentTasksPage`、`ProjectsPage`、`ProjectPage`、`ProjectContextPanel`。

### 8.2 `AppShellState.kt`

现有 `AppShellState` 中的 `ShellDestination`、`selectedSessionId` 和只有 `visible` 字段的 `ProjectContextState` 不足以表达目标结构。建议演进为：

```text
AppShellState
├── navigation: NavigationState
├── workspace: WorkspaceNavigatorState
├── thread: ThreadSurfaceState?
├── panel: PanelState
├── composer: ComposerState
├── sidebar: SidebarState
└── connection: ConnectionIndicatorState
```

迁移时先加新字段，再让旧 `destination` 和 `selectedSessionId` 从新状态派生，最后删除旧字段。这样可以保留现有测试和 Desktop 启动参数的兼容性。

### 8.3 数据层前置

页面编码前需要确认以下共享模型存在或有明确的适配器：

- Project catalog：列表、创建、重命名、归档和根目录。
- Task catalog：标题、状态、更新时间、未读和最近运行。
- Thread catalog：Thread/Task/Project 关系和恢复入口。
- Workspace/Host catalog：当前 Host、可用 Host、能力和协议版本。
- Plugin catalog：插件 ID、来源、版本、Slot 贡献、启用状态和错误。

如果后端暂时只有 `SessionSummary`，先做只读 `Task/Thread` 投影，不在 UI 层伪造持久化 Project/Task 操作。

## 9. 验收顺序

在开始像素级视觉实现前，按以下顺序确认行为：

1. **Shell 层级**：App Rail、Workspace Navigator、Main Surface、Panel 的边界与截图一致。
2. **本地冷启动**：无账号、无 Host 时仍能进入 Home 和本地 Thread。
3. **Project 树**：Project > Task > Thread 展开、收起、选中和空状态正确。
4. **Recent**：最近 Task 与 Project 内 Thread 使用同一稳定 ID，点击后能打开同一 Thread。
5. **Thread 恢复**：历史加载、Relay subscribe、取消和 Composer 状态不因导航重组回归。
6. **Workspace 切换**：切换 Tenant/Host 后查询和选中状态正确失效。
7. **Settings/Plugins**：section 路由、插件 Catalog 和 Slot Snapshot 不依赖 Desktop 专属对象。
8. **移动端**：使用同一 `PageId` 和 `NavigationState`，通过抽屉、底部导航和 bottom sheet 呈现。
9. **视觉验收**：最后再按 Codex 参考截图校验栏宽、行高、间距、字体、圆角、hover/selected/focus 状态。

## 10. 开始 UI 实施前的检查清单

- [ ] 本文页面决策已确认。
- [ ] `PageId`、`OverlayId` 和 `NavigationState` 的字段已确定。
- [ ] Project、Task、Thread、TransportSession 的关系已有共享 contract。
- [ ] Project/Task/Thread 的数据来源和空状态已确定。
- [ ] `HarnessDestination` 的兼容期和删除条件已确定。
- [ ] `AppShellState` 的迁移测试已补齐。
- [ ] 新 Slot 的 Owner、Props、cardinality 和生命周期已登记到 Catalog。
- [ ] Desktop/Android/iOS 的导航差异已通过状态矩阵验证。
- [ ] Codex `app.asar` 参考版本、截图和 Token 快照已固定。
- [ ] 在得到页面规划确认前，不继续扩大 `ui-foundation`、`AppShell` 或 `HarnessApp` 的 UI 改动。

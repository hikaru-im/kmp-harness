# Desktop UI 与跨端组件复用规划

状态：Proposed  
评估日期：2026-10-05

目标是以 Codex Desktop 为视觉基线，构建 KMP Harness 的 Desktop Shell，并让核心组件在
Android、iOS 和 Desktop 之间复用。复用的是组件契约、状态模型、交互语义和视觉 Token；平台
输入、窗口、系统菜单和文件能力由平台适配层提供。

## 一、当前基础

- `apps/shared` 已提供共享的 `HarnessApp`，Desktop、Android、iOS 都从这里进入。
- Desktop 通过 Compose `Window` 启动；Android 使用 `ComponentActivity`，iOS 使用
  `ComposeUIViewController`。
- `modules/ui-slot` 已成为独立模块，Desktop Client Catalog 和 `root`、Header、Sidebar Slot
  已开始接线。
- 当前 Shell 仍使用 Material3 默认 `Button`、`TextButton`、`OutlinedTextField`、`Column` 和
  简单横向导航。下一阶段要先建立自己的视觉基础层，再扩展业务页面。
- `backend/ui` 是 RuoYi 管理端，继续保持 Vue 体系。它提供后台管理能力，不作为 Desktop
  Compose 组件库的实现来源。

导航层级、页面取舍和 `Project`/`Task`/`Thread`/transport `Session` 的关系见
[Desktop Navigation and Page Plan](desktop-navigation-page-plan.md)。本文档只负责视觉基础、Shell
组件和跨端复用；页面实现必须遵循导航规划中的 `PageId` 和 `NavigationState`。

## 二、目标界面结构

Desktop 以固定的三层结构组织：窗口壳层、应用壳层、功能内容层。

```text
Desktop Window
└── AppShell
    ├── AppRail
    │   ├── Home
    │   ├── Recent
    │   ├── Projects
    │   ├── Plugins
    │   └── Account menu
    ├── WorkspaceNavigator
    │   ├── Workspace selector / New task
    │   ├── Project > Task > Thread tree
    │   └── Recent tasks
    └── MainSurface
        ├── Window / thread header
        ├── ConversationViewport
        │   ├── Empty state
        │   ├── User / assistant messages
        │   ├── Reasoning and tool cards
        │   └── Streaming state
        └── ComposerDock
            ├── Attachment and mode actions
            ├── Prompt editor
            ├── Model / permission controls
            └── Send / stop action
```

Settings、Account、Host、Plugin Inventory 和 Model Catalog 使用同一套 Shell，在主内容区切换，
不要再为每个页面各自创建一套布局。

## 三、像素级视觉基线

“像素级”必须建立在固定的参考环境上。第一版锁定 Desktop 的一个基准组合：macOS、深色主题、
系统缩放 100%、固定字体版本、固定窗口尺寸和固定数据快照。Android、iOS 复用同一组 Token 和
组件状态，按触控和屏幕宽度调整布局。

视觉参考包需要包含：

- 1440×900、1280×800、1024×768 三个 Desktop 尺寸。
- 空会话、普通对话、流式生成、Tool Call 展开、失败、无 Host、登录和设置页面。
- 深色主题为第一验收主题，浅色主题随后复用同一组语义 Token。
- Sidebar 展开、收起、窄窗口和键盘焦点状态。
- 鼠标悬停、按下、禁用、加载、错误、选中和拖拽状态。

每个参考画面都要记录窗口尺寸、平台、字体、主题、数据 Fixture 和滚动位置。未锁定这些输入
时，截图差异无法区分布局错误与系统字体或抗锯齿差异。

## 四、组件分层

### 4.1 `ui-foundation`：跨端视觉基础

新增共享 UI 基础层，建议放在 `apps/shared` 之下的独立模块或拆成 `modules/ui-foundation`：

- 颜色、表面、边框、文字、强调色和语义状态。
- 间距、圆角、控件高度、图标尺寸和内容最大宽度。
- Typography、Icon、Surface、Divider、Tooltip、Button、TextField、Menu、Dialog、Badge、
  Scrollbar 等基础组件。
- 焦点、悬停、按下、禁用、加载、错误和选中状态。
- 无障碍语义、测试标签和键盘焦点顺序。

基础组件要控制最终布局尺寸，不直接暴露 Material3 的默认 padding、颜色或 shape。Material3
可以继续提供可访问性和部分行为，但视觉值必须来自 Harness Token。

### 4.2 `ui-shell`：Desktop 和移动端共享的应用壳

共享 AppShell 的状态和区域契约：

- `SidebarState`：展开、收起、临时展开、选中 Session。
- `ShellDestination`：Conversation、Settings、Account、Host 和 Plugin Inventory。
- `ConnectionIndicatorState`：Local、Connecting、Online、Disconnected 和 Incompatible。
- `PanelState`：无面板、Settings、Tool Details、Model Picker 和 Command Palette。
- `ComposerState`：草稿、附件、模式、模型、发送中、停止中和错误。

Desktop 负责窗口标题栏、拖拽区域、菜单、快捷键和窗口尺寸；移动端负责系统返回、Insets、触控
手势和底部导航。Shell 的业务状态与这些平台操作分离。

### 4.3 `ui-feature-*`：功能组件

按功能拆分，而不是按平台复制：

- `ui-session`：Session 列表、标题、消息时间线和恢复状态。
- `ui-message`：文本、Markdown、Reasoning、Tool Call、Tool Result 和错误卡片。
- `ui-composer`：编辑器、附件、模型选择、权限和发送控制。
- `ui-settings`：Provider、Model、Plugin、凭据和 Desktop Preferences。
- `ui-account`：登录、租户、资料和 Host 连接。

每个功能模块只依赖 typed state、callbacks 和 UI 基础层。它不直接读取 Runtime、Host、数据库或
平台 API。

### 4.4 平台适配层

使用 `expect/actual` 或明确的 adapter interface 提供：

- Desktop：窗口尺寸、系统菜单、文件选择、剪贴板、全局快捷键、拖拽和通知。
- Android：返回键、Insets、触控长按、系统分享和文件选择。
- iOS：导航返回、Safe Area、文件导入、键盘和系统分享。

平台适配层只返回稳定的数据和事件，不能把平台 View 或窗口对象传进共享 Feature 组件。

## 五、UI Slot 与 Shell 的接线

Slot 只负责插件贡献，Shell 负责布局和视觉规则。第一批 Slot 规划如下：

| Slot | 类型 | 宿主区域 | 插件可以贡献 |
| --- | --- | --- | --- |
| `root` | single | AppShell 根节点 | 整体 Shell 或受控替换 |
| `sidebar.actions` | list | Sidebar 操作区 | 新建、插件入口、Agent 操作 |
| `conversation.header` | single | 主内容 Header | 状态、标题扩展和操作 |
| `conversation.header.actions` | list | Header 操作区 | 刷新、分享、分支和诊断 |
| `tool.call.view` | keyed | Tool Card | 特定 Tool 的展示器 |
| `composer.actions` | list | Composer 工具栏 | 附件、模式和插件动作 |
| `settings.tabs` | list | Settings 内容区 | Provider、Plugin 和扩展设置 |

插件注册的是 `UiContribution` 和 typed Props。插件不改变父布局的 padding、尺寸和颜色，也不
直接替换共享组件内部结构。需要接管默认实现时再引入 `chain` Slot，并为接管者保留明确的回退。

## 六、复用规则

组件复用分为三层：

1. **完全共享**：Token、状态模型、基础控件、Session/Message/Tool 数据投影和交互命令。
2. **共享结构、平台渲染**：滚动条、窗口 Header、Sidebar、Composer、Context Menu。共享组件
   提供布局和状态，平台适配器提供系统行为。
3. **平台专属**：Desktop 标题栏和菜单、Android 返回处理、iOS Safe Area 和系统分享。

跨端组件禁止在 Composable 内通过 `Platform.current` 分支业务逻辑。平台差异先进入
`PlatformUiAdapter`，组件只消费 adapter 提供的能力。

## 七、实施阶段

### Phase 0：冻结 Codex 视觉参考包

- 选定 Codex Desktop 的版本、平台、主题、字体和窗口尺寸。
- 建立截图、Fixture、滚动位置和交互状态清单。
- 把参考画面拆成区域尺寸、颜色、间距、字体、圆角、阴影和交互状态。

### Phase 1：视觉 Token 和基础组件

- 新增 `ui-foundation`。
- 替换当前 Shell 对 Material3 默认视觉值的直接依赖。
- 先实现 Surface、Text、IconButton、Button、TextField、Menu、Dialog、Tooltip、Badge 和
  Scrollbar。
- 为每个组件提供正常、悬停、按下、禁用、聚焦、加载和错误状态。

### Phase 2：Desktop AppShell

- 实现 Sidebar、MainSurface、Session Header、Conversation Viewport 和 Composer Dock。
- 先完成空会话、普通会话和流式会话三条状态链路。
- 接入现有 Connection、Session Stream 和 UI Snapshot，不改变 Host API。

### Phase 3：功能页面和 UI Slots

- 把 Account、Tenant、Profile、Host、Settings 和 Plugin Inventory 接入统一 Shell。
- 接入 Sidebar、Header、Composer、Tool 和 Settings Slot。
- 新增一个真实插件贡献验证从 Catalog 到 Compose 的完整链路。

### Phase 4：Android/iOS 适配

- 复用 Feature 和基础组件。
- 增加移动端导航、Insets、触控和系统能力 adapter。
- 以同一组状态 Fixture 验证移动端功能和语义一致，再按屏幕宽度调整布局。

### Phase 5：像素级回归

- 为 Desktop 每个目标尺寸生成 Golden Screenshot。
- 使用 Compose UI 测试和截图差异检查验证几何、颜色、文字、图标、滚动和状态切换。
- 允许字体抗锯齿设置造成的受控差异；布局尺寸、Token、组件状态和 Slot 位置必须零偏差。
- 任何 Golden 更新都要同时记录原因和参考版本。

## 七点一、根据实际 Codex 截图修正导航模型

用户提供的 2560×1440 Desktop 截图显示，Codex 的左侧结构由两列组成：

```text
应用导航 Rail
├── Home
├── Recent / History
├── Projects
├── Library / Extensions
└── Account

Workspace Navigator
├── Codex workspace selector
├── New Chat
├── 项目
│   ├── KMP Harness
│   │   ├── 实施 DSH Koog 同步与 UI Slots
│   │   └── 查看项目状态（当前选中）
│   └── 黑苹果
└── 最近
    ├── 实施 DSH Koog 同步与 UI Slots
    ├── 查看项目状态
    └── 其他最近任务
```

右侧的 `kmp-harness` 浮层属于 Project Context Panel，显示项目变更、已创建任务、来源和“查看
全部”，它与左侧导航状态独立。主区域顶部还有当前任务标题栏，底部保留 Composer。

因此 Desktop Shell 的真实分区应调整为：

```text
Window Chrome
├── App Rail
├── Workspace Navigator
├── Main Thread Surface
│   ├── Thread Titlebar
│   ├── Thread Content
│   └── Composer
└── Project Context Panel（可选）
```

第一批 Desktop Slot 应补充：

| Slot | 类型 | 用途 |
| --- | --- | --- |
| `app.rail.primary` | list | 应用级入口和当前区域切换 |
| `workspace.header` | single | Codex/Workspace 选择器、通知和搜索 |
| `workspace.project.tree` | keyed | 项目和任务层级 |
| `workspace.project.actions` | list | 项目菜单和创建动作 |
| `workspace.recent.tasks` | list | 最近任务列表 |
| `thread.titlebar.actions` | list | 当前任务的标题栏动作 |
| `project.context.panel` | single | 项目变更、任务和来源摘要 |

`session list` 只表示 Workspace Navigator 中的一种数据投影。它不能作为整个左侧导航的模型。
Android 和 iOS 可以把 App Rail、Workspace Navigator、Project Context Panel 组合成抽屉、页面和
底部导航，但必须复用相同的 Project、Task、RecentTask 和 Selection 状态。

## 八、验收门禁

功能验收：

- Desktop、Android、iOS 共用同一组 Session、Message、Tool 和 Composer 状态模型。
- Host 连接、断开、重连、流式输出和历史恢复不会因为 UI 重组丢失。
- UI Plugin 可以通过 Catalog 注入 Sidebar、Header、Tool、Composer 和 Settings 内容。
- Host Runtime、Client Runtime 和 Compose 生命周期独立关闭。

视觉验收：

- Desktop 三个窗口尺寸的 Golden Screenshot 通过。
- 深色主题的区域边界、控件尺寸、文字层级、颜色和状态全部对齐参考包。
- 键盘导航、焦点环、悬停、拖拽、滚动和弹层层级通过交互回归。
- Android 和 iOS 在窄屏、横屏、键盘弹出和系统返回下不破坏共享状态。

建议的检查命令：

```bash
./kotlin test --include-module=shared --platform=jvm
./kotlin test --include-module=ui-slot --platform=jvm
./kotlin test --include-module=jvm-app --platform=jvm
./kotlin build --platform=jvm
```

## 九、当前下一步

上一阶段的 Koog 和 UI Slot 最小实现已经完成。Phase 0 已建立
`docs/desktop-ui/reference/`，其中包含固定环境表、1440×900、1280×800、1024×768 三种窗口尺寸、
状态矩阵、交互清单和 JSON fixture；Codex Desktop 的截图、字体版本和像素测量仍明确标为未验证。

Phase 1 已新增独立 `modules/ui-foundation`，以 Harness Token 驱动跨端 Compose 基础组件，并保留
Material3 作为行为和无障碍实现基础。Phase 2 已新增共享 `AppShell` 和 typed state，并让现有
`HarnessApp` 的 Desktop/Android/iOS 共享入口使用 Sidebar、MainSurface、Session Header、
Conversation Viewport 和 Composer Dock。Session 页面仍作为内容适配层，但已经把真实的消息编辑、
发送和取消操作注册到 Shell composer slot；下一步继续迁移 Account、Host、Settings 和 Plugin
Inventory 功能页。

不要把新的视觉值或页面布局继续堆进 `HarnessApp.kt`；新增区域应进入 Shell 或功能模块。

## 十、app.asar 代码级参考结果

已对本机 `/Applications/ChatGPT.app/Contents/Resources/app.asar` 做只读盘点。当前安装包的
应用版本为 `26.930.31730`，归档 SHA-256 为
`64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e`。

UI 主要位于 `/webview`，按功能拆成多个 bundle，而不是单个页面：

- `thread-app-shell-chrome-*`：窗口壳、标题栏、主区、右侧面板、底部面板和 Tab Strip。
- `sidebar-*`：Sidebar、Actions、Session/Workspace 入口。
- `composer-*`：Composer 状态、Host、Controls、工具栏和发送区域。
- `conversation-*`、`thread-*`：消息时间线、滚动布局、Tool/Panel 和会话状态。
- `codex-dark-*`、`codex-light-*`：主题定义。
- `app-shared-*.css`：共享 CSS Token、字体、颜色、间距和圆角。

可以直接转译到 KMP Token 的证据包括：

- 基础间距为 `0.25rem`，Typography 以 11、12、14、16、20、24、28px 等级组织。
- Sidebar 偏好宽度变量为 `275px`，允许在 `240px` 到 `520px` 范围内调整。
- 基础圆角从 `2px`、`4px`、`6px`、`8px`、`10px`、`12px`、`16px`、`20px`、`24px` 组成。
- Composer 单行圆角使用 `5.5 × spacing`；Toolbar 按钮使用独立的按钮圆角和尺寸 Token。
- UI 字体包含 OpenAI Sans Regular、Medium、Semibold，代码区域使用系统等宽字体。
- Shell 插槽通过 `desktopPlacement`、`slotPosition`、`HeaderAction`、`RightPanelOutlet`、
  `BottomPanelOutlet` 等概念组织。KMP 继续用 typed UI Slot 表达这些位置。

复刻实现只吸收布局、Token、状态和插槽结构，KMP 组件保持自己的实现和数据契约。`app.asar`
中的 bundle、字体和图片不直接纳入仓库；需要视觉一致的资源另行建立可追溯的替代资源清单。

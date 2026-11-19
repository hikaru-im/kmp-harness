# Codex 26.1027.61615 → Harness UI 迁移差异矩阵

日期：2026-11-06。状态：**只读矩阵与差距分析；未修改 `apps/**`，未启动 Electron，未重新解码。**

- Codex 基线：`26.1027.61615`（build `24141`），ASAR SHA-256 `335bb4f5bf2dd682d3fec3455b43935ff3b902043b4682fae74d1520aeec89e9`。解码产物在 [`references/codex-26.1027.61615-24141/full-restore/`](../references/codex-26.1027.61615-24141/full-restore/README.md)。
- Harness 基线：分支 `feat-SuiYI`，提交 `e4296aad`，加当前工作区已有的 S-01/S-02 未提交改动。本矩阵只读取当前工作区，不回退、不覆盖、不清理这些改动。
- 旧的全应用差异报告 [`ui-component-difference-audit.md`](ui-component-difference-audit.md) 基于 `references/decode-codex`（`26.930.61225`），**不是同版本基线**；本矩阵以 full-restore 为准，旧报告只作为 Harness 侧功能清单的交叉参考。
- 本文所有 `file:line` 都对应本日期的工作区。行号会随后续改动漂移。

## 0. 结论摘要

1. **设置面是最大的结构性差距。** Codex 运行态有 **19 个设置面板**（`data-settings-panel-slug` 为准），Harness 有 **8 个设置 section**。19 个里有 12 个在 Harness 没有对应页面；其余 7 个中 3 个 `partial`、4 个 `divergent`，没有一个达到 `aligned`。另有 36 个 route key 在该构建运行态不可达（static-only），其中多数在 Harness 也没有对应面；`billing`、`teams`、`parental-controls`、`cloud-computer` 已定为一期不做（Phase 2），见 3.1.3.1，不重复计入这 12 个。
2. **核心编码回路仍缺三块。** ①线程侧栏没有 `变更/Review` 标签页（Codex 的真实 Git 入口）；②composer 的附件按钮是禁用占位；③插件页没有 Codex 的 `plugins / apps / mcps / skills / marketplace` 管理面，MCP 服务器在 Harness 完全没有 UI。
3. **外壳语义存在偏差，NAV-01 已补静态证据。** Harness 契约注释声称 Codex rail 为 `首页 · 图像 · 定时任务 · 资料库 · 插件 · 探索`，而 Harness 是 `主页 · 定时任务 · 资料库(=项目) · 插件 · 探索` + `账号`。静态真源（`app-initial-18b189e867e7.js:248478-248518`）确认 Codex 有 8 个目的地 key（`home`/`customize`/`scheduled`/`space`/`code-review`/`orbit`/`meetings`/`defense-factory`），**没有 account/profile**；账号入口在 rail 底部 profile footer，不是 rail 目的地。因此 NAV-01 标 `divergent`，并已决定移除账号 rail（D-06）：账号只保留在 profile footer 与设置，Harness 目的地集合向 Codex 对齐。R13 运行态仍只确认 `应用导航`/`首页`，不能把静态标签当运行态按钮证据。探索菜单的 `exploreDestinations` 只有静态构造线索，R13 点击仍只出现 `项目`，NAV-02 保持 `unknown`。
4. **对话页是"能用但结构不同"。** Harness 有消息卡片、推理、工具活动、文件变更、错误卡片、模型/推理/权限选择；Codex 的 turn 级虚拟化、滚动锚定、plan/default composer 模式、附件、follow-up 队列、turn diff 摘要、子 Agent 活动组、草稿恢复提示在 Harness 缺位或只做了部分。
5. **运行态事实必须和静态事实分开读。** 19 个面板是运行态观测；36 个 route key 是静态存在但本构建不可达；没有任何 API 往返被观测到（所有 `api-endpoint` 的 `observedInNetwork=false`）。因此"Codex 会调用哪个接口、保存失败长什么样"这类结论，本矩阵一律标 `unknown`，不拿静态调用点冒充运行态契约。

### 0.1 状态统计

| 区域 | 矩阵条目 | aligned | partial | divergent | missing | unknown |
|---|---:|---:|---:|---:|---:|---:|
| settings（含外壳与 19 面板） | 26 | 0 | 7 | 5 | 12 | 2 |
| shell / nav | 8 | 0 | 5 | 1 | 0 | 2 |
| sidebar / landing | 8 | 0 | 7 | 1 | 0 | 0 |
| conversation / composer | 16 | 0 | 10 | 2 | 4 | 0 |
| Git / worktree | 10 | 0 | 5 | 1 | 4 | 0 |
| scheduled | 8 | 0 | 5 | 0 | 3 | 0 |
| plugins / account | 8 | 0 | 2 | 4 | 2 | 0 |
| terminal / file panel | 6 | 0 | 4 | 0 | 2 | 0 |
| responsive | 5 | 0 | 2 | 1 | 0 | 2 |
| **合计** | **95** | **0** | **47** | **15** | **27** | **6** |

> 统计按本矩阵的实际行计数；`unknown` 不等于"没差异"，只表示现有解码或 Harness 代码不足以判定。
>
> scope 统计：`deferred_phase2: 4`（`billing`、`teams`、`parental-controls`、`cloud-computer`）。这 4 项是 static-only，已单列 3.1.3.1，不进入上面的状态列，也不重复计入 `missing`。

## 1. 证据口径、状态与优先级

### 1.1 证据来源与代号

| 代号 | 产物 | 能证明什么 | 不能证明什么 |
|---|---|---|---|
| `S03`–`S12` | `03-semantic` … `12-semantic` 的 `README.md`、`ui-evidence.json`、`binding-map.json`、`verification.json` | 静态切片中的组件、文案、绑定、CSS、行为调用点，逐条带 offset/行列/摘录 | 运行态是否执行、是否可见、真实接口往返 |
| `R13` | `13-runtime/runtime-evidence.json`、`settings-rail-inventory.json`、`crosscheck.json`、`route-stylesheets.json` | 只读导航观测到的 DOM、可访问名、面板 slug、路由/样式表差异、运行态截图 | 任何写操作、表单提交、保存/删除/运行、登录、插件安装 |
| `S14` | `14-callgraph/graph.json`、`routes-to-panels.json`、`message-id-pairs.json`、`chains/*.json`、`gaps.md` | route → panel → chunk、符号级调用/渲染/导入边、message id 生产/消费配对 | 传递闭包；间接依赖三层以外；API 是否被调用 |
| `H` | Harness 当前源码 | 现有组件、状态、文案、入口与行号 | 未运行的平台行为、未覆盖的交互状态 |

### 1.2 运行态事实与静态事实

- **运行态（R13）**：只有"设置面 19 个面板"、"外壳 6 个 surface"、"变更/Git 只读页面"、"插件页目录"、"对话页首帧"等只读观测。所有 `api-endpoint` 的 `runtime.observedInNetwork` 都是 `false`（`gaps.md` §3），所以本矩阵不写"Codex 保存时一定请求 X"。
- **R13 设置侧栏清单的口径限制**：`settings-rail-inventory.json` 只统计 `nav[aria-label=设置]` 内的 **button**（19 个），不包含输入框；因此不能用"19 个按钮、无搜索按钮"证明设置页没有搜索框。`screenshots/general.png` 显示标题「设置」下方有占位符「搜索」的搜索框。
- **静态（S03–S14）**：组件、文案、绑定、调用点、CSS 是完整的；静态存在不等于运行态可见。`routes-to-panels.json` 明确区分 `runtimeSlug` 与 `staticGate`。
- **两者冲突时以运行态为准**，并在矩阵中标注 `R13`。已知的 9 条 Phase 13 差异（d1–d9）直接纳入，不重新解释。

### 1.3 状态定义

| 状态 | 含义 |
|---|---|
| `aligned` | 结构层（信息架构、分区、导航深度、控件与状态集合）与视觉层（字体层级、间距密度、色彩/表面、图标、动效）都通过；视觉层需有同视口截图或交互基线佐证。只有语义身份一致不算 `aligned` |
| `partial` | 有对应面，但结构层或视觉层的一部分缺失；包括结构/语义一致但缺同视口视觉基线 |
| `divergent` | 槽位或名称相近，但产品语义/交互模型不同（不是简单缺一块） |
| `missing` | Harness 没有对应面或对应能力 |
| `unknown` | 现有解码或 Harness 代码不足以判定；或该 Codex 面只有静态证据、无运行态观测 |

### 1.4 优先级定义

| 优先级 | 含义 |
|---|---|
| `P0` | 核心编码回路（对话、变更、composer、插件/MCP）的迁移阻断项；不做就无法宣称 Codex 语义迁移完成 |
| `P1` | 运行态已观测到的用户可见面缺失或语义偏差；影响主壳层/主流程 |
| `P2` | 次要面、状态覆盖、静态-only 面，或需要产品范围决定后再做 |
| `P3` | 文案/视觉/边缘状态；或明显属于 Codex 平台特有、Harness 未必需要复制的能力 |

`aligned` 是双层判定：结构层与视觉层必须同时通过。视觉层没有同视口截图或交互基线时，降为 `partial`（缺视觉基线），不得用语义身份或单个尺寸常量替代。

### 1.5 决策记录

| 编号 | 日期 | 决定 | 影响 |
|---|---|---|---|
| D-01 | 2026-11-06 | `billing`、`teams`、`parental-controls`、`cloud-computer` 一期不做，列入 Phase 2 backlog | 4 项 static-only 不进入 M0–M6；scope 统计 `deferred_phase2: 4`，不重复计入 `missing`（见 3.1.3.1） |
| D-02 | 2026-11-06 | `aligned` 采用「结构+视觉」双层判定 | 本次复核将 SET-01、RESP-02、TERM-06 从 `aligned` 降为 `partial`（缺同视口视觉基线）；0.1 统计同步更新 |
| D-03 | 2026-11-06 | 迁移顺序保持 M0–M6 | 不新增、不删除、不重排阶段；M0 验收增加「aligned 必须有视觉基线」 |
| D-04 | 2026-11-06 | Harness 自有扩展逐项决定，未决定前不删除 | 见 3.10 表；逐项状态按 D-05～D-08 更新，未决定项不删除 |
| D-05 | 2026-11-06 | 模型管理（SET-29/30）保留并标注为 Harness 扩展，不纳入 Codex 对齐验收 | 3.10 该行状态「已决定（D-05）」；SET-29/30 仍为 `unknown`/P2、`unknown`/P3 |
| D-06 | 2026-11-06 | 账号 rail（NAV-01）：移除；账号只保留在个人资料菜单/设置，向 Codex 对齐 | 3.10 该行状态「已决定：移除账号 rail（D-06）」；NAV-01 仍 `divergent`/P2（尚未实施） |
| D-07 | 2026-11-06 | 资料库占位页（SIDE-08）保留占位，不实现文件库；继续标 `divergent` | 状态「已决定（D-07）」；SIDE-08 仍 `divergent`/P2 |
| D-08 | 2026-11-06 | 主题编码（SET-12）移除导入导出；其余外观控件继续按 Codex 对齐 | 3.10 该行状态「已决定：移除导入导出（D-08）」；SET-12 仍 `partial` |
| D-09 | 2026-11-06 | 设置侧栏搜索（SET-04）选择对齐 Codex：按占位符「搜索」与视觉细节对齐，补同视口基线，不移除 | C-01 的处理结论；SET-04 保持 `partial`，不进入 3.10/summary 3.5 扩展清单 |

**修正记录（非用户决定）**

| 编号 | 日期 | 修正 | 影响 |
|---|---|---|---|
| C-01 | 2026-11-06 | SET-04 误分类修正：`settings-rail-inventory.json` 只统计 button，`screenshots/general.png` 证明 Codex 设置页有搜索框；SET-04 从 Harness 扩展清单移除 | SET-04 `unknown`→`partial`；3.10/summary 3.5 由 5 项减为 4 项；settings `unknown` 3→2、`partial` 6→7；总计 `partial` 46→47、`unknown` 8→7；处理结论见 D-09 |

## 2. Harness 区域清单与入口

| 区域 | Codex 入口/路由 | Harness 入口 | 主要文件 |
|---|---|---|---|
| shell / nav | `AppShellRoot`、`AppShellFrame`、`AppSidebarContainer`、`SidebarAreaDispatcher`（S03） | `AppShell`、`AppRail`、`WorkspaceNavigator`、`SettingsNavigator`、`HarnessTabStrip` | `apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt:79`、`RailDestinations.kt:7`、`NavigationState.kt:17`、`HarnessTabStrip.kt:18` |
| sidebar / landing | `SidebarContent`、`HomeSidebarContent`、`ProjectsIndexPage`、`ProjectHomeContent`、`ThreadPageContainer`（S04） | `WorkspaceNavigator`、`LocalProjectsPage`、`ProjectsRecentPageComponents`、`ThreadPage`、`NewTabPage` | `AppShell.kt:1342`、`page/LocalProjectsPage.kt:35`、`page/ThreadPage.kt:41`、`page/NewTabPage.kt:109` |
| conversation / composer | `LocalConversationPage`、`ConversationThreadSurface`、`VirtualizedConversationTurnList`、`CodexComposer`（S05） | `LiveConversationSurface`、`LiveConversationComposer`、`ConversationComponents`、`ThreadSummaryPanel` | `app/LiveConversationSurface.kt:184`、`app/LiveConversationComposer.kt:24`、`app/ConversationComponents.kt:161`、`summary/ThreadSummaryPanel.kt:51` |
| settings | `/settings/:section/*`、`SettingsPage`、19 个运行态面板（S06、S12、R13） | `PageId.Settings(SettingsSection)`、`SettingsNavigatorModel`、8 个设置页 | `shell/SettingsNavigatorModel.kt:40`、`page/SettingsGeneralPage.kt:93`、`page/SettingsAppearancePage.kt:30`、`page/SettingsModelsPage.kt:144` |
| Git / worktree | `ReviewThreadSidePanelTab`、`/diff`、`GitBranchSwitcher`、`WorktreeBranchSetupModal`、`/settings/git-settings`、`/settings/worktrees`（S07） | `SettingsGitPage`、`WorktreesPage`、`GitController`、`ManagedWorktreeController` | `git/GitPages.kt:19`、`git/GitPages.kt:131`、`git/GitController.kt:9`、`git/ManagedWorktreeController.kt:7` |
| scheduled | `/automations` → `/scheduled`、`ScheduledTasksPage`、`AutomationsLayoutPage`（S08） | `PageId.ScheduledTasks`、`ScheduledManagementPage`、`ScheduledManagementNavigator` | `scheduling/ScheduledManagementPage.kt:18`、`scheduling/ScheduledManagementNavigator.kt:22`、`scheduling/ScheduledPageLayout.kt:28` |
| plugins / account | `/plugins`、`/plugins/:pluginId`、`/settings/plugins-settings`、`/settings/connections`、`/settings/account`（S09） | `InstalledPluginsPage`、`PluginsManagementNavigator`、`AccountWorkspacePage`、`SettingsConnectionsPage` | `plugins/InstalledPluginsPage.kt:51`、`plugins/PluginsManagementNavigator.kt:39`、`account/AccountWorkspacePage.kt:23`、`page/SettingsConnectionsPage.kt:44` |
| terminal / file panel | `terminal-tab`、`terminal-panel`、file browser/tree、editor/artifact preview（S11） | `ThreadPanels`、`PtyTerminalPanel`、`ProjectFileSearchDialog` | `panels/ThreadPanels.kt:62`、`panels/PtyTerminalPanel.kt:22`、`projects/ProjectFileSearchDialog.kt:31` |
| responsive | `smallViewportStore`、`AppShellFrame`、`DialogContent`、`Popover`（S10） | `NavigatorLayout`、`ShellLayoutSettings`、`PageShellSpec`、`HarnessTabStrip` | `shell/NavigatorLayout.kt:21`、`shell/ShellLayoutSettings.kt:14`、`shell/PageShellSpec.kt:10` |

## 3. 差异矩阵

### 3.1 settings（试点区域）

选择 settings 作为试点，因为它是唯一有完整运行态证据的区域（R13）：19 个面板逐一点开、侧栏几何被测量、面板身份按 `data-settings-panel-slug` 记录。矩阵列在这里定稿，后续区域沿用同一格式。

#### 3.1.1 设置外壳

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| SET-01 | `/settings/:section/*` → `SettingsPage`；从账号菜单进入 | 全部 19 面板 | `settings-page-4899da12a575.js`（`ki`→`SettingsPage`、`Qr`→`SettingsSidebarRoute`） | 设置是**状态面不是 URL 面**：`history.pushState('/settings/notifications')` 不会切换已渲染的 Worktrees 面板；点击 `设置` 后 `location.pathname === '/'`。面板身份是 `data-settings-panel-slug`。设置导航有 `settings.nav.back` 返回应用。 | `PageId.Settings(section)` + `SettingsSection` 枚举 + `SettingsNavigatorModel`；`onSettingsBack` 由 `AppShell` 提供。没有 URL 同步，也没有 `data-settings-panel-slug` 对应物。 | partial | S06 `shell-back-button`、`shell-route-section-param`；R13 d1；H `shell/NavigationState.kt:90`、`shell/SettingsNavigatorModel.kt:40`、`shell/AppShell.kt:101`；视觉层：Codex 有 R13 `screenshots/general.png` 等面板截图，但 Harness 无同视口截图/视觉基线，`ui-interaction-confirmations.md` 未做全应用视觉验收 | 保持 slug 身份；为每页补稳定的语义 testTag（如 `settings-panel-<slug>`），并补同视口截图/视觉基线后再评 `aligned` | P3 | 需要决定是否暴露 URL 深链；Codex 本构建明确不可寻址 |
| SET-02 | 设置侧栏：`SettingsSidebar` / `initSettingsSidebarDefinitions` | `general-settings` … `worktrees` 共 19 个 | `settings-page-4899da12a575.js`（`ei`、`Oi`）；静态扁平顺序 `Ti`，分组 `Ei`（Personal / Integrations / Coding / Teams） | 运行态侧栏恰好 19 个按钮，几何 `scrollHeight === clientHeight === 1272`、`overflow-y: visible`，不可滚动；分组标题为 个人 / 集成 / 编码 / Teams（Teams heading 为 null）。 | `SettingsNavigatorModel.groups`：个人（常规、外观、模型管理、键盘快捷键）、集成（插件）、编码（连接、Git、Worktrees）。共 8 行、3 组，无 Teams。 | divergent | R13 `settings-rail-inventory.json`、`runtime-evidence.json` coverage.settings；S06 设置侧栏分组 claims；H `shell/SettingsNavigatorModel.kt:44` | 先做产品范围决定：12 个缺失面板哪些纳入；再按 Codex 顺序/分组重排。`模型管理` 是 Harness 自有项，不应伪装成 Codex 面板 | P1 | 12 个缺失面板中多项是 ChatGPT/云能力，是否属于 Harness 产品范围未决定 |
| SET-03 | `SettingsPageFrame` + `SettingsPersistentWorkspaceTabs` + `SettingsContent` | 全部 19 面板 | `settings-page-frame-7cad56995a2a.js`（`j`、`D`）；`settings-content-layout-ac451a456013.js`（`S`、`x`） | 设置页用 `MainContentLayout layout="full-bleed"`；页面容器带 `data-codex-history-swipe-page`；`SettingsContent` 带 `data-settings-mobile-header`、`data-density`、`data-contain-touch-overscroll`；设置页与工作区标签持久共存。 | `SettingsFormFrame` / `SettingsFormSection` / `SettingsFormCard`（`page/SettingsFormComponents.kt`）是普通可滚动表单；没有 full-bleed 布局、持久工作区标签或 swipe/density 属性。 | partial | S06 `shell-frame-swipe-attribute`、`shell-frame-full-bleed`；H `page/SettingsFormComponents.kt` | 评估 full-bleed 与持久标签是否纳入；至少补齐页面容器的语义属性与滚动策略 | P2 | Compose 没有 DOM data 属性；需要用 semantics testTag 表达同等身份 |
| SET-04 | 设置侧栏搜索 | — | 静态侧栏切片未发现搜索输入；运行态 `screenshots/general.png` 显示设置页标题「设置」下方有搜索框，占位符「搜索」，带放大镜图标 | Codex 设置侧栏有搜索框（R13 截图）；`settings-rail-inventory.json` 只统计 `nav[aria-label=设置]` 内的 button，输入框不是 button，不能用"19 个按钮"证明无搜索。 | `SettingsNavigatorSearch`（`shell/AppShell.kt:1601`），占位符「搜索设置」；`settingsNavigatorGroups` 按分组标题与行标签过滤（`shell/SettingsNavigatorModel.kt:77`）。 | partial | R13 `screenshots/general.png`、`settings-rail-inventory.json` 口径限制；H `shell/AppShell.kt:1601`、`shell/SettingsNavigatorModel.kt:77` | D-09：按 Codex 的占位符「搜索」与视觉细节对齐，补同视口基线，不移除搜索框 | P3 | 结构一致；无同视口视觉基线；文案差异 |
| SET-05 | 设置面板切换的持久化/恢复 | 全部 19 面板 | `settings-page-4899da12a575.js`；`SettingsPersistentWorkspaceTabs` | Codex 用持久工作区标签承载设置页；切换面板不改变 URL，但保留工作区标签上下文。 | `PageId.Settings(section, childTab)` 进入 Nav3 返回栈；没有"持久标签"模型。 | partial | S06 `shell-frame-full-bleed`；H `shell/NavigationState.kt:75` | 明确设置页是返回栈页面还是持久标签；若保留返回栈，补"返回应用"与"返回上一面板"的区分 | P2 | 依赖导航模型决定；当前 `PageId.Settings` 已可序列化 |

#### 3.1.2 运行态 19 个设置面板

> 运行态 slug 与 rail 顺序来自 R13 `settings-rail-inventory.json`；chunk 来自 S14 `routes-to-panels.json`（`chunkCompiledAtRuntime` 为 true 的 19 行）。静态契约来自 S06/S07/S09/S12 的 `ui-evidence.json`。Harness 行号对应当前工作区。

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| SET-10 | General settings | `general-settings`（rail #0） | 运行态 `general-settings-4375afb195b0.js`；静态 `general-settings-eef28ee7c589.js`（`Ec/Dc/Oc`） | General 分组标题；关闭前确认（popover 触发）；自动归档；Enter 行为选项；纯文本 composer 开关；桌面 beta；教学提示开关；纯文本保存失败 toast。 | `SettingsGeneralPage` + `SettingsGeneralModel`：默认权限、完整访问权限、无项目任务文件夹、默认文件打开位置、语言、菜单栏、关闭前确认、完整视图、底部面板、默认终端位置、防止休眠、压缩历史、提示词建议、许可证、插件。 | divergent | S06 `general-group-title`、`general-confirm-close-label`、`general-confirm-close-popover`、`general-auto-archive-label`、`general-enter-behavior`、`general-plain-text-switch`、`general-plain-text-toast`、`general-desktop-beta`、`general-educational-tips-switch`；H `page/SettingsGeneralPage.kt:53`、`:93` | 对齐行集合：保留 Harness 已有权限/终端/休眠项，补 auto-archive、Enter 行为、plain-text、desktop beta、educational tips；把 confirm-close 改成 Codex 的 popover 形态或说明保留 dropdown 的理由 | P1 | 需要产品决定哪些 Codex 桌面项在 KMP 多端适用 |
| SET-11 | Import settings | `import`（rail #1） | `import-settings-b00895178720.js`（`ImportSettings`） | 页面副标题 `settings.import.subtitle.v2`；导入历史 loading/error/retry/latestTitle/runningBadge；autosync 全类别/自定义/未选中/选中数；导入失败 error label/description/retry/empty；autosync 保存失败。 | 无对应页面。 | missing | S12 `p12-009`…`p12-019`、`p12-330`…`p12-333`；R13 rail #1 | 先决定是否引入"从其他应用导入"能力；若要，按 Codex 的 history/autosync/error 四态建页 | P2 | 依赖后端导入能力；Codex 该页属于 ChatGPT 迁移场景 |
| SET-12 | Appearance settings | `appearance`（rail #2） | `appearance-settings-28adb61e16c5.js`（`f`）；CSS `fd9b7fd80db1.css` | 共享 section title；高级子区在导航指向控件时自动展开；accent strength 0–100 range slider；主题导入对话框；主题导出成功 toast；sans font-size 数字输入。 | `SettingsAppearancePage`：视觉样式、模式预览、亮/暗主题字段、复制主题、导入/导出、高级区。 | partial | S06 `appearance-page-slug`、`appearance-advanced-expand`、`appearance-accent-slider`、`appearance-import-dialog`、`appearance-export-toast`、`appearance-sans-font-size`；H `page/SettingsAppearancePage.kt:30`、`:57`、`:71` | 主题导入/导出按 won't-do 记录（D-08）；其余外观控件（accent slider / font-size / `高级` 自动展开等）继续按 Codex 对齐 | P2 | 主题格式是 Harness 自有编码（`AppearanceThemeCodec`）；移除导入导出后仅保留内部使用，不能假定与 Codex 主题 JSON 互通 |
| SET-13 | Archived chats | `archived-chats`（rail #3） | `data-controls-237ffa3a96c3.js`（`DataControlsSettings`） | 已归档聊天搜索（含 noMatches）；单条删除带标题确认；全部删除确认与成功；项目组操作。 | 无对应页面；`ProjectChildFilter.Archived` 只是项目页的筛选枚举。 | missing | S12 `p12-145`…`p12-155`；R13 rail #3；H `shell/NavigationState.kt:82` | 增加归档会话管理面：搜索、恢复/删除、全量删除确认；复用现有会话仓库 | P1 | 需要确认归档在 Harness 会话模型中的存储位置 |
| SET-14 | Voice | `voice`（rail #4） | `voice-settings-320cf1212f2c.js`（`VoiceSettings`） | 常规区；麦克风 systemDefault/unavailable/saveError；听写热键错误；实时语音 voice picker/retry/loadError；听写词典新增；转写失败重试；audioOnly 标记。 | 无对应页面；composer 听写按钮是禁用占位。 | missing | S12 `p12-094`…`p12-103`、`p12-349`、`p12-350`；R13 rail #4；H `app/ConversationComposerToolbar.kt` 听写占位 | 若不做语音，明确标注"Harness 不提供"；不要保留无行为的听写占位 | P3 | 桌面语音/麦克风能力跨平台差异大 |
| SET-15 | Storage | `storage`（rail #5） | `device-storage-49df203d8c6c.js`（`DeviceStorageSettings`） | 主目录标签；measuredTotal；updating；cleanup 审核对话框、空态、按年龄筛选、可回收大小警告、deleting、recheckError、confirmTitle/confirmDelete、loadError。 | 无对应页面。 | missing | S12 `p12-104`…`p12-114`、`p12-351`、`p12-352`；R13 rail #5 | 若纳入，需先有本地存储统计与安全清理后端；UI 按 Codex 的 review→confirm→delete 三段式 | P2 | 依赖本地文件扫描/删除能力；破坏性操作需谨慎 |
| SET-16 | Agent configuration（配置） | `agent`（rail #6） | `configuration-panel-45ebea890453.js`（`ConfigurationPanel`） | work/codex 默认值；managed/read-only/unavailable scope；依赖诊断 ok/failed/cancel.failed；runtime 安装 downloading；配置 writeError/inlineSandboxWriteError；读取 `work.cloud-orchestration.preferences`。 | 无对应页面；Harness 的 `模型管理` 是 provider 配置，不是 Codex 的 agent configuration scope/diagnostics。 | missing | S12 `p12-126`…`p12-134`、`p12-355`…`p12-357`；R13 rail #6 | 先定义 Harness 的 config scope 模型（用户/项目/托管）与依赖诊断入口；再按 Codex 的 scope/diagnostics 结构建页 | P1 | 与后端配置所有权/托管策略强相关；不能只做 UI |
| SET-17 | Personalization | `personalization`（rail #7） | `personalization-settings-7ef3fe8695e0.js`（`ChatGptPersonalizationSettings`） | 保存成功/失败；instructionsSaved；memory loadError/retry/unavailable；chatGpt updateError/preference.error。 | 无对应页面；Harness 没有 memory/instructions 设置面。 | missing | S12 `p12-185`…`p12-190`、`p12-371`、`p12-372`；R13 rail #7 | 若 Harness 有 memory/instructions 能力，按 Codex 的 saved/error/retry/unavailable 四态建页；否则明确排除 | P2 | 依赖 Harness memory 能力是否落地 |
| SET-18 | Pets / Mini | `pets`（rail #8） | `pets-settings-route-bfcfc8a51f0e.js`（`PetsSettingsRoute`） | 自定义宠物创建；头像刷新；尺寸；on-demand 可见性；`settings.pets.*` 文案与虚拟宠物画廊。**运行态明确 `pets` ≠ `codex-micro`**，`codex-micro-settings-*.js` 从未被解析。 | 无对应页面。 | missing | R13 d2；S12 pets claims；H 无 | 产品决定：若不做宠物，明确排除；不要把 `codex-micro` 误当 pets 实现 | P3 | Codex 平台特有的娱乐功能 |
| SET-19 | Keyboard shortcuts | `keyboard-shortcuts`（rail #9） | 运行态 `keyboard-shortcuts-settings-44c71b6673a9.js`；静态 wrapper `keyboard-shortcuts-settings-bac62a963482.js` | 按键搜索（searchByKeystrokes.tooltip）；恢复默认；resetAllError；重置全部确认对话框；逐命令编辑/冲突/平台差异。 | `SettingsShortcutsPage` + `ShortcutsRepository`：搜索、按键搜索、逐命令捕获、重置全部（含确认）、错误显示。 | partial | S12 `p12-069`…`p12-073` 等；H `page/SettingsShortcutsPage.kt:21`、`:67`、`:74` | 对照 Codex 逐项核对：恢复默认文案、resetAllError、冲突提示、平台可用性；补交互测试 | P2 | Harness 已有较完整实现，优先补差异而不是重写 |
| SET-20 | Plugins settings | `plugins-settings`（rail #10） | `plugins-settings-322a6fca639e.js`（`PluginsSettings`）→ `PluginsManagePage` | manage tabs：plugins / apps / mcps / skills / marketplace；MarketplaceListSection/Remove/Details；McpServerDetailEditor/McpServerAuthControls/AddMcpServerPanel；host/plugin 权限页。**MCP 服务器列表实际渲染在「插件」页，`mcp-settings` 没有独立面板。** | `InstalledPluginsPage` + `PluginsManagementNavigator`：本地插件登记、列表、详情、Skills、能力说明；无 apps/mcps/skills/marketplace tabs，无 MCP server 编辑/认证。 | divergent | S09 plugins-settings claims；S14 d7/d9（MCP 在插件页）；R13 `shell-plugins` surface；H `plugins/InstalledPluginsPage.kt:51`、`:99` | 按 Codex 的 manage tabs 重构；至少先补 MCP 服务器列表/详情/认证，这是 Harness 完全缺失的核心集成面 | P0 | 依赖插件/MCP 后端能力；UI 不能单独完成 |
| SET-21 | Computer use | `computer-use`（rail #11） | `computer-use-settings-981697fabc1b.js`（`ComputerUseSettings`） | 副标题；read access 保存/移除错误；browser detection loading/retry/notInstalled；chat search searching/error/empty/retry。 | 无对应页面。 | missing | S12 `p12-218`…`p12-225`、`p12-382`…`p12-384`；R13 rail #11 | 产品决定；若不做，明确排除 | P3 | macOS 桌面控制能力，跨平台不适用 |
| SET-22 | Appshots | `appshots`（rail #12） | `appshots-settings-73a1db0fe376.js`（`AppshotsSettings`） | destination 保存错误等 `settings.appshots.*` 状态。 | 无对应页面。 | missing | S12 appshots claims（`p12-348` 等）；R13 rail #12 | 产品决定；若不做，明确排除 | P3 | Codex 特有截图能力 |
| SET-23 | Browser use | `browser-use`（rail #13） | `browser-use-settings-802dca83451c.js`（`BrowserUseSettings`） | policy authoring 副标题；browsing history 搜索/loading/retry/emptyBySource；download history clearError；downloads location changeError/saveError。 | 无对应页面；`NewTabPage` 有浏览器工具栏外观，但没有 Codex 浏览器历史/下载策略设置。 | missing | S12 `p12-227`…`p12-232`、`p12-385`…`p12-388`；R13 rail #13；H `page/NewTabPage.kt:109` | 产品决定；若纳入，先做策略/历史/下载三块 | P3 | 需要内嵌浏览器与下载后端 |
| SET-24 | Hooks settings | `hooks-settings`（rail #14） | `hooks-settings-dcc6d1fed9c5.js`（`HooksSettings` + model/copy/route/navigation/source-label） | 副标题；hook 列表/来源标签/导航/编辑/启用状态；`settings.hooks.*` 文案。 | 无对应页面；插件能力里只把 Hook 标为不支持的 cap。 | missing | S12 hooks claims；R13 rail #14；H `plugins/InstalledPluginsPage.kt:343`、`:354` | 建 Hooks 设置页：按来源列出 hook、启用/禁用、编辑、错误；与插件 Hook 能力打通 | P1 | 依赖 hook 运行时与配置模型 |
| SET-25 | Connections（远程连接） | `connections`（rail #15） | `connections-settings-*.js`（`RemoteConnectionsSettings`） | access other devices / control this Mac / SSH / WSL tabs；RemoteConnectionsTable、ConnectionDetailsCard；AddSshConnectionDialog、ConnectionDiscoveryDialog、PairingCodeDialog、AuthorizeRemoteControlDialog；RemoteControlClientsSection、RemoteDeviceConnectionsSection；旧 `/remote-connections` 重定向。 | `SettingsConnectionsPage`：本机/远程 Host 连接、发现、选择、SSH/WSL 入口；没有"控制这台 Mac"、配对码、远程控制客户端、授权对话框。 | divergent | S09 `p9-047…p9-050、p9-239…p9-267-remote-connections` claims；R13 rail #15；H `page/SettingsConnectionsPage.kt:44` | 明确 Harness 的"连接"是 Host 连接还是 Codex 的远程控制；两者不要共用同名页。若两者都要，拆成两个 section | P1 | 产品语义冲突：Harness 现有 Connections 是核心 Host 选择面，不能直接替换 |
| SET-26 | Git settings | `git-settings`（rail #16） | `git-settings-b9c4caaa37d1.js`（`GitSettings`） | 关闭 Git-based diffs（Changes 面板只剩 Last Turn）；branch prefix；always force push；draft PR；PR merge method；commit instructions；PR instructions；review delivery inline/detached。 | `SettingsGitPage`：仓库绑定、状态、分支列表、提交表单、diff 查看器、worktree 入口。它是"仓库操作台"，不是 Codex 的 Git 偏好页。 | divergent | S07 `p7-182`…`p7-190`；R13 rail #16；H `git/GitPages.kt:19` | 拆分为「Git 偏好」与「仓库操作」两个面；Codex 的 9 项偏好按原语义补；仓库操作迁到 Changes/Composer 上下文 | P1 | 现有 Git 页被多处入口复用（`HarnessApp.kt:1330`、`:1392`、`:1581`），拆分需改导航但不动行为 |
| SET-27 | Local environments | `local-environments`（rail #17） | `local-environments-settings-page-a6d95bf07320.js`（`LocalEnvironmentsSettings`） | 本地执行环境列表/创建/选择；环境与 worktree 的关系；`settings.localEnvironments.*` 状态。 | 无独立页面；`WorktreeSettings` 里有 worktree root/auto-cleanup，但没有"环境"实体。 | missing | R13 rail #17；S12 local-environments claims；H `settings/WorktreeSettings.kt:20` | 先定义 Harness 的 environment 模型；再建列表/创建/选择面；避免与 worktree 概念混用 | P1 | 依赖执行环境后端；Codex 该页只有静态契约 |
| SET-28 | Worktrees | `worktrees`（rail #18） | `worktrees-settings-page-*.js`（`WorktreesSettingsPage`） | inventory、repository 分组与计数、known conversations、new chat、delete（有 pinned chat 时阻止）、auto cleanup（确认、保留数量）、upstream refresh、空态、Git settings 里的 worktree root。 | `WorktreesPage`：仓库绑定、本地 Git 工作树列表、known conversations、new chat、创建、删除、root、auto-cleanup、保留数量、upstream refresh、空态。 | partial | S07 `p7-148`…`p7-158`、`p7-151`；R13 rail #18；H `git/GitPages.kt:131` | 对照补齐：repository 分组计数、pinned chat 删除阻止的明确文案、auto-cleanup 确认措辞；核对 root 是否在 Git settings 而非 Worktrees | P1 | 现有实现已较完整；以差异核对和测试为主 |
| SET-29 | Model Providers（Gateways） | 运行态未出现（static-only） | `settings-5591ce1b43e9.js`（`GatewaysSettings`、`kn/An/Cn`、`tn/nn/rn/an/on/cn/pn/hn/yn`） | provider 列表/编辑器/连接测试；`testGatewayConnection` 发合成 turn 要求回复 `gateway-ok`；保存后重启 app-server 并拉 `model/list`；managed/busy 原因；内置 provider 过滤；auth mode 强制。 | `SettingsModelsPage`：厂商列表、预设/自定义添加、模型发现、保存/删除、只读/错误状态。它对应 Codex 的 `gateways` 静态契约，但 Codex 本构建运行态没有该面板。 | unknown | S06 gateways claims（`io-*`）；S14 gateways `staticGate.surfaceKey=codexLocal`；R13 `catalogNotObserved` 含 `gateways`；H `page/SettingsModelsPage.kt:144` | 不要求把 Codex gateways 的运行态当基准；以静态契约为参考核对 provider 编辑/测试/重启语义；保留 Harness 自有交互（P-01～P-03 已确认） | P2 | Codex 运行态未观测；Harness 已有用户确认的模型交互，不能回退 |
| SET-30 | Harness 自有：模型管理 | — | — | Codex 运行态没有 `models` 面板；最近似的是 static-only `gateways`（Model Providers）。 | `SettingsModelsPage`（`page/SettingsModelsPage.kt:144`），8 个设置 section 之一。 | unknown | R13 `settings-rail-inventory.json`（无 models）；H `shell/SettingsNavigatorModel.kt:48` | 明确标注为 Harness 扩展；D-05 已决定保留并标注，不纳入 Codex 对齐验收 | P3 | 与 DSH 模型配置交互强相关，不宜简单删除 |

#### 3.1.3 static-only route key（36 个）

`routes-to-panels.json` 的 `runtimeSlug` 为 null 的 36 个 route key。它们的共同运行态原因是：**本构建设置侧栏没有入口**；侧栏几何已证明不是滚动隐藏（R13）。完整 55 行见附录 A。这里按 gate 归类，避免逐行重复：

| gate 类 | route key | Harness 现状 | 迁移判断 |
|---|---|---|---|
| ChatGPT 账号/云/计费（surface=`chatGpt`） | `billing` `notifications` `security` `account` `age` `agreements` `parental-controls` `trusted-contact` `safety` `time-management` `ads-controls` `extension` `cloud-computer` `passwords` | 无对应面；`account` 在 Harness 是本地 Host 登录页，语义不同 | 先做产品范围决定；多数属于 ChatGPT 云产品，不应默认复制；其中 `billing`、`parental-controls`、`cloud-computer` 一期不做（Phase 2），其余仍按产品范围待定 |
| Codex 云/团队/用量（surface=`codex` / `None`） | `rosalind` `work-settings` `code-review` `cloud-settings` `codex-cloud` `cloud-environments` `chronicle` `usage` `analytics` `usage-code-review` `consumer-view` `teams` | 无对应面 | 产品范围决定；`code-review`、`cloud-environments` 若 Harness 做云端才有意义；其中 `teams` 一期不做（Phase 2），其余仍按产品范围待定 |
| 本地 Codex 能力（surface=`codexLocal` / `codexOrWorkLocal`） | `gateways` `mcp-settings` `skills-settings` `file-type-handlers` `debug` `environments` `family` | `gateways` → Models 页；`mcp-settings` → 无；`skills-settings` → 插件详情里的 Skills 只读；`file-type-handlers` → 无 | `gateways` 按静态契约核对；`mcp-settings` 按运行态事实并入插件页（见 SET-20）；其余待产品决定 |
| 已由运行态面板覆盖的别名 | `time-management`（解析为 `safety`）、`family`（无 catalog 条目） | `family` 在 Harness 无对应；`time-management` 属于 ChatGPT safety | 不单独建页；按别名语义处理 |

#### 3.1.3.1 一期不做（Phase 2 backlog）

用户已决定，以下 4 个 static-only route **一期不做**，留待二期重新评估。它们在本构建运行态没有侧栏入口，但**不属于 3.1.2 的 12 个运行态缺失面板**；因此不重复计入 `missing`，scope 统计单列为 `deferred_phase2: 4`。

| route key | 真实页面 / export | 为什么延后 | 二期重新评估的条件 |
|---|---|---|---|
| `billing` | `ConsumerBillingSettings`（S09；callgraph 的 `exportName` 为 null） | ChatGPT 消费级计费：计费明细、支付方式、钱包、交易历史、取消订阅；本构建 gate 硬关闭（`visible: !1`）；Harness 没有计费后端 | Harness 引入 ChatGPT 消费级账号与计费/支付后端，且产品确认需要账单与订阅管理 |
| `teams` | `TeamSettingsPage`（S12） | 团队工作区：成员、管理员、团队活动；需要 ChatGPT workspace | Harness 引入 workspace/团队身份模型与成员管理 |
| `parental-controls` | `ChatGptParentalControlsSettingsPage`（S12；callgraph 的 `exportName` 为 null） | 家庭/信任联系人/U18 解绑；需要家庭账号状态 | Harness 支持家庭账号与监护人关系 |
| `cloud-computer` | `ChatGptCloudBrowserSettings`（S12） | **不是云电脑**：ChatGPT Cloud Browser 的站点权限 / cookie / Orbit cookie 设置 | Harness 提供 ChatGPT Cloud Browser 或等价的站点权限管理 |

**一致性约束**：这 4 项在正文、3.1.3 分类表、附录 A、summary 与 M4 中一律标为「一期不做（Phase 2）」；其他 static-only route 仍写「产品范围待定」，不因本决定扩大延后范围。

#### 3.1.4 settings 小结

- **可立即做、不依赖产品范围决定**：SET-10 行集合对齐、SET-12 appearance 控件、SET-19 shortcuts 差异、SET-26 Git 页拆分、SET-28 Worktrees 细节、SET-20 至少先做 MCP 列表。
- **需要先决定产品范围**：SET-11/13/14/15/16/17/18/21/22/23/24/27 与 static-only 的 ChatGPT/云组（不含已定为 Phase 2 的 4 个 route）。
- **不能照搬**：SET-25 Connections（Harness 的同名页是 Host 选择）、SET-30 模型管理（Harness 自有扩展、已有用户确认交互）。
- **一期不做（Phase 2）**：`billing`、`teams`、`parental-controls`、`cloud-computer` 是 static-only，**不在 12 个运行态缺失面板内**；已单列 3.1.3.1，不重复计入 `missing`，scope 统计为 `deferred_phase2: 4`。

### 3.2 shell / nav

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| NAV-01 | App rail 内建目的地 | `shell-home` / `shell-plugins` / `shell-scheduled` | `app-initial-18b189e867e7.js:248478-248518`（`f0r` / 导出 `C3t`，app-primary import 为 `fm`）；`app-primary-75d53eef2374.js`（message id）；S03 | 静态真源确认 8 个目的地 key：`home`(fixed)、`customize`(fixed)、`scheduled`(primary)、`space`(primary)、`code-review`(pinned)、`orbit`(null)、`meetings`(null)、`defense-factory`(null)，**没有 account/profile 条目**。标签 id：`sidebarElectron.appNavigation`=应用导航、`sidebarElectron.rail.homeDestination`=首页、`sidebarElectron.rail.home`=聊天、`sidebarElectron.rail.settings`=设置、`sidebarElectron.rail.pluginsDestination`=插件、`sidebar.scheduledTasks.destinationLabel`=任务、`sidebarElectron.codeReviewRouteNavLink`=代码审查、`sidebarElectron.projectsNavLink`=项目、`sidebarCustomization.unpinDestination`=取消置顶、`codex.sidebarRail.upgrade`=升级订阅。账号入口在 rail 底部 profile footer（`uNt`，app-primary:33279），走 `codex.profileFooter.openProfileMenu` / `openSettings`，不是 rail 目的地；`account` 只是设置面板 slug，且不在运行态 19 个可达面板内。 | `RailDestination`：`Home / Scheduled / Library / Plugins / More`，`harnessRailItems` 输出 `主页 · 定时任务 · 资料库 · 插件 · 探索` + 账号；`资料库` 打开项目工作区，不是文件库。 | divergent | S03 静态 `02-baseline/formatted/webview/assets/app-initial-18b189e867e7.js:248478-248518`、`app-primary-75d53eef2374.js`（message id）；R13 `coverage.shell.surfaces`（6/6 观测 `应用导航`，shell-home 另有 `首页`；截图仅图标）；H `shell/RailDestinations.kt:7`、`:14`、`:18-32`、`:37`、`:44-50`、`shell/NavigationState.kt:72` | D-06：从 Harness rail 移除账号入口；账号/个人资料只保留在 profile footer（打开个人资料菜单）与设置。其余差异按 Codex 8 key 清单评估（`图像` 无对应 key、`资料库` 语义待定） | P2 | Codex 有稳定目的地清单，Harness 集合不同；`资料库` 语义影响 `PageId.Library`、`PageId.Projects`；`账号` 不是 Codex rail 目的地 |
| NAV-02 | 探索菜单与钉住 | `shell-home` | `app-primary-75d53eef2374.js`（探索菜单、rail pinned 区） | **R13 观测是 blocked**：点击 `探索` 只出现 228×37 的 popover，内容只有 `项目` 一行；没有出现 `站点/地图/GPT/代码审查` 等二级目的地，因此本构建无法把探索菜单证成"目的地菜单"。Harness 契约注释声称 Codex 可钉住 5 项，但这不是 R13 观测。 | `ExploreMenu` 只提供 `项目` 一个可钉住项；`RailDestination.Projects` 是唯一非固定目的地。 | unknown | R13 `coverage.exploreMenu`（blocked，只有 `项目`）；H `shell/AppShell.kt:981`、`:1027`、`shell/RailDestinations.kt:14` | 先按 R13 的 `项目` 单项行为对齐；5 项钉住清单需补静态/运行态证据后再做；保留 S-02 已确认的键盘与固定按钮规则 | P3 | 站点/地图/GPT/代码审查缺 full-restore 证据；Harness 未必有对应页 |
| NAV-03 | 账户菜单 | `shell-home` 的 `打开个人资料菜单` | `app-primary` 的 `AccountMenu` / `AccountMenuItems`；S09 | 头像菜单含账号身份、管理工作区、设置、登出确认；`/settings/account` 是 ChatGPT 账户页。 | `AccountMenu` + `AccountMenuRow`：身份、设置、登出等；`AccountWorkspacePage` 是 Host 登录/租户页，不是 ChatGPT 账户页。 | partial | S09 account claims；R13 shell-home composerButtons（`打开个人资料菜单`）；H `shell/AppShell.kt:829`、`:931`、`account/AccountWorkspacePage.kt:23` | 保持 Host 登录语义；在账户菜单里区分"本机 Host 账号"与"ChatGPT 账户"；补登出确认措辞 | P1 | 产品身份模型不同；不要为了对齐把 Host 登录改成 ChatGPT 登录 |
| NAV-04 | 侧栏区域分派 | `shell-home` / `shell-projects` / `shell-scheduled` / `shell-plugins` | `app-initial-18b189e867e7.js`（`resolveSidebarArea`、area registry）；S03 | `resolveSidebarArea` 依次判断 meetings、defense-factory、plugin 工具路由、scheduled、space；`SidebarAreaDispatcher` 按 area 渲染不同侧栏内容。 | `WorkspaceNavigator` + `NavigatorKind`（`PageShellSpec.kt:10`）+ `PluginsNavigator` / `ScheduledTasksNavigator` / `SettingsNavigator` 分支。 | partial | S03 `resolveSidebarArea` claims；H `shell/AppShell.kt:1342`、`:1734`、`:1756`、`shell/PageShellSpec.kt:10` | 对照 Codex 的 area 判定顺序，确认 Harness 的 navigator kind 不会在 plugin 工具路由/scheduled/space 之间误判 | P2 | Codex 有 meetings/defense-factory 分支，Harness 没有这些产品面 |
| NAV-05 | 侧栏标签/分屏 | `shell-conversation` | `app-primary` 的 TabStrip / side panel；S03/S10 | 侧栏标签（任务选项卡）与对话并排或全屏；每个 tab 是 `@container/app-shell-tab`；对话不是 tab，另一侧才是。 | `HarnessWorkspaceTab` + `HarnessTabLayout.Full/Split` + `HarnessPaneSide`；模型与 Codex 语义一致。 | partial | S03/S10 tab claims；H `shell/HarnessTabStrip.kt:18`、`:29`、`:38` | 核对拖拽分隔条、全屏时对话变为 `聊天` tab、tab 关闭/重开；补运行态测试 | P2 | 需要窗口级运行态验证；当前只有单元/交互测试 |
| NAV-06 | 命令菜单 / spotlight | `?initialRoute=/spotlight` | `app-runtime` / spotlight window；S03 | 应用有独立 spotlight 窗口（760×640），命令菜单含快捷操作/新聊天等；主窗口与 spotlight 是两个 page target。 | `AppShell` 有 `navigatorSearchOpen`、文件搜索 ⌘P；没有独立命令菜单窗口。 | unknown | S03 renderer claims；R13 「两个同名窗口」说明；H `shell/AppShell.kt:103`、`projects/ProjectFileSearchDialog.kt:31` | 先确认 Harness 是否需要命令菜单；若要，按 Codex 的 spotlight 结果分组/键盘语义设计 | P2 | 多窗口是桌面壳能力；KMP 各端窗口模型不同 |
| NAV-07 | 标题栏 / 页面头 | `shell-home` / `shell-conversation` | `app-primary` 的 title strip / `聊天工具栏`；S03/S05 | 主标题栏在侧栏展开时被切成对话段与面板段；对话段有聊天名与工具栏；隐藏侧边栏 toggle 在标题条。 | `HarnessTitleStrip`、`WorkspaceNavigator` 的 title/search、`ThreadPage` 头部；需要核对分段行为。 | partial | S03/S05 title claims；H `app/HarnessTitleStrip.kt`、`shell/AppShell.kt:1582` | 对照 Codex 的分段标题栏：侧栏展开/收起时的宽度与控件归属；补 S-02 已确认的固定按钮规则 | P2 | 依赖窗口布局与侧栏模式 |
| NAV-08 | 返回栈 / 历史 | 全部 surface | `AppRoutes` + Navigation；S03 | 路由栈 + 浏览器 history；设置面不是 URL 面，但应用路由是。 | `PageId` 是 Navigation 3 `NavKey`，`NavigationState.pages` 是返回栈；没有 URL/history 同步。 | partial | S03 `app-routes` claims；H `shell/NavigationState.kt:17`、`:80` | 明确桌面 back/forward 与移动端返回手势的映射；不要假定 Codex 的 URL 语义 | P3 | KMP 各端返回模型不同 |

> **NAV-02 注记（2026-11-06）**：静态另有 `exploreDestinations` 构造证据——排除 `home`/`customize`，primary 为 `scheduled`/`space`；但 R13 运行态仍 blocked，本行状态保持 `unknown`，U-14 不变，不据此改写 NAV-02。

### 3.3 sidebar / landing

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| SIDE-01 | 侧栏头 / 搜索 / 听写 | `shell-home` | `app-primary`（`SidebarHeaderContainer`、`SidebarWorkspaceHeader`、`SidebarHeaderActionGroup`、`SidebarSearchButton`、`DictationDraftRow`）；S04 | sticky header、滚动分隔线、间距 token；搜索按钮打开 `chat-search-command-menu` host message；搜索导航行；听写草稿行。 | `WorkspaceNavigator` 有 title、搜索、文件搜索；没有听写草稿行；搜索走 Harness 自己的 command menu/文件搜索。 | partial | S04 sidebar header claims；H `shell/AppShell.kt:1342`、`:1602` | 补 sticky header/分隔线视觉 token；听写若要保留，必须有真实行为；搜索命令菜单对齐 Codex 的结果分组 | P2 | 听写依赖语音能力（SET-14） |
| SIDE-02 | Chats 区 / 分组 / 重排 | `shell-home` | `UnifiedSidebarSections`、`ChatsSection`、`ThreadList`、`ThreadRow`；S04 | 统一侧栏 sections；chats 分组；任务行；支持分组重排/拖拽；空态与 loading。 | `WorkspaceNavigator` + `WorkspaceProjectNode` / `RecentTaskItem`；`RecentTasksPage` 是独立页面。 | partial | S04 chats claims；H `shell/AppShell.kt:1342`、`page/ShellPages.kt:37` | 对照 chats 分组/重排/空态；确认任务行与项目树是否同一层级 | P2 | 需要会话仓库支持分组/排序 |
| SIDE-03 | 项目树 / hover card / 上下文菜单 | `shell-projects` | `ProjectsSection`、`ProjectTree`、`ProjectGroup`、`ProjectRow`、`ProjectHoverCard`、project context menu；S04 | 项目树按项目分组；hover card；上下文菜单；项目行选中/展开。 | `LocalProjectsPage` + `WorkspaceProjectNode`；`ProjectsState`；有项目行/详情/选择器/编辑器。 | partial | S04 project tree claims；H `page/LocalProjectsPage.kt:35`、`:60`、`:113`、`:175`、`shell/ProjectsState.kt` | 补 hover card 与上下文菜单；对齐项目行的选中/展开状态 | P2 | 需要项目仓库与文件系统支持 |
| SIDE-04 | Thread 行族 / 操作 | `shell-home` / `shell-projects` | `ThreadRowContent`（Local/Work/Remote 变体）、`ThreadRowActions`；S04 | 本地/工作/远程三种行变体；行操作（置顶、归档、更多）。 | `RecentTaskItem` / `WorkspaceItemNode`；没有 Work/Remote 变体；置顶/归档在 `AppShell` 的账户/会话操作中部分存在。 | partial | S04 thread row claims；R13 `shell-home` surface 的按钮清单含 `置顶聊天`/`归档聊天`（行级操作，不是 composer 控件）；H `shell/AppShell.kt:1342` | 明确 Harness 的 task/thread 行模型；补 Work/Remote 变体或标注不支持；核对置顶/归档入口 | P2 | Harness 的会话模型可能没有 work/remote 区分 |
| SIDE-05 | Projects index | `shell-projects` | `ProjectsIndexPage` + toolbar/sort/status/loading/list rows；S04 | 项目列表 toolbar、按名称/修改时间排序、状态、loading、空态；行可打开。 | `LocalProjectsPage`：项目列表、详情、选择器、编辑器、状态；排序/筛选需核对。 | partial | S04 projects index claims；H `page/LocalProjectsPage.kt:35` | 对照 toolbar/sort/status；补空态/loading 的 Codex 文案 | P2 | 需要项目仓库支持排序字段 |
| SIDE-06 | Project home | `shell-projects` | `ChatGptProjectHome` → `ProjectHomeContent` → `ProjectChatList`；S04 | 项目主页；chats 列表；排序 `updated_at`；分页/空态。 | `ProjectContextPanel` / `ThreadPage` 的项目上下文；没有独立项目主页的 chats 列表。 | partial | S04 project home claims；H `page/ThreadPage.kt:213` | 决定是否把项目主页做成独立 landing；若不做，在项目页补 chats 列表 | P2 | 依赖项目/会话聚合查询 |
| SIDE-07 | Thread landing / access guard | `shell-conversation` | `LocalConversationPage` / `AppServerConversationPage` / `RemoteConversationPage`、`ConversationAccessGuard`；S04 | 本地/AppServer/远程三种 conversation page；访问守卫；远程环境标签；不可访问 toast。 | `PageId.Thread` + `ThreadPage`；`LiveThreadPane` / `LiveThreadSurface`；没有远程 conversation page 变体。 | partial | S04 thread landing claims；H `page/ThreadPage.kt:41`、`app/LiveConversationSurface.kt:184` | 明确 Harness 的 thread 来源（本地/远程 Host）；补 access guard 的错误态 | P2 | Harness 是 Host 连接模型，不是 Codex 的 remote conversation |
| SIDE-08 | 新标签页 / 资料库 | `shell-home` | `TabStrip` / New Tab；S03/S10 | 新标签页是独立页面，含浏览器工具栏与工具网格；资料库是文件产品。 | `NewTabPage` 有浏览器工具栏与工具网格；`PageId.Library` 是占位页（注释明确"不实现 Codex Library"）。 | divergent | S03/S10 new tab claims；H `page/NewTabPage.kt:109`、`shell/NavigationState.kt:34` | D-07 已决定保留占位，不实现文件库；继续标 divergent。新标签页的工具网格按 Codex 工具项核对 | P2 | 资料库需要文件产品后端 |

### 3.4 conversation / composer

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| CONV-01 | 对话页容器 / 标题栏 | `shell-conversation` | `LocalConversationPage`（`Fl`）、`ConversationPageBody`、`ConversationHeaderRegion`、`ConversationTitlebarHeader`、`ConversationGitActions`、`ConversationTurnDiffSummary`；S05 | 对话页容器；标题栏含对话名；Git actions；turn diff 摘要；摘要面板入口。 | `ThreadPage` + `LiveThreadPane` / `LiveThreadSurface`；`HarnessTitleStrip`；没有 Git actions 与 turn diff 摘要。 | partial | S05 conversation page/header claims；R13 shell-conversation；H `page/ThreadPage.kt:41`、`app/LiveConversationSurface.kt:184` | 补标题栏的 Git actions 与 turn diff 摘要；与 GIT 区共用 diff 数据 | P1 | 依赖 Git diff 能力 |
| CONV-02 | Turn 虚拟化 / 滚动锚定 | `shell-conversation` | `VirtualizedConversationTurnList`（`GP`）、`VirtualizedTurnList`（`P`）、`buildTurnListLayout`（`se`）、`computeVisibleTurnRange`（`ce`）、`rangeForAnchorKey`（`le`）、`adjustScrollOffsetForAnchor`（`ue`）、`ScrollFooterFocusGuard`（`ie`）；S05 | turn 级虚拟化；可见 turn range；anchor key 定位；滚动偏移调整；footer focus guard。 | `ConversationViewport` 是 `LazyColumn` + 扁平消息列表；没有 turn 分组/虚拟化/锚定 API。 | divergent | S05 virtualized turn list claims；H `app/ConversationComponents.kt:47`、`:246` | 先引入 turn 模型（turn id、消息归属、diff 摘要），再评估 turn 级 LazyColumn；不要直接搬 React 虚拟化实现 | P1 | 大改对话渲染；需要回归现有交互测试 |
| CONV-03 | 消息条目分派 | `shell-conversation` | `ConversationItemRenderer`（`vj`）、`ConversationItemDataResolver`（`bj`）、`ConversationItemContent`（`rj`）；S05 | 按 item type 分派：用户消息、计划中的任务消息、assistant、reasoning、exec tool call、file change patch、stream error、system error、turn diff、subagent activity 等。 | `MessageContentView` 按 `MessageContent` 分派：文本、推理、工具调用/结果、文件变更、审批；`ConversationErrorCard`；没有 plan 消息、turn diff、subagent 条目。 | partial | S05 message renderer claims；H `app/ConversationComponents.kt:430`、`:541`、`connection/Connection.kt:61` | 补缺失 item type；保持现有 `MessageContent` 模型可扩展 | P1 | 需要协议侧新增内容类型 |
| CONV-04 | Reasoning block | `shell-conversation` | `ReasoningBlock`（`JO`）；S05 | 推理块可展开/收起；进行中/完成文案；与 turn 绑定。 | `ReasoningContent` → `ActivityDisclosure("正在思考…"/"已完成思考")`（`ConversationComponents.kt:447`）。 | partial | S05 reasoning claims；H `app/ConversationComponents.kt:447` | 核对 Codex 的展开默认值、耗时、完成态；补长推理的折叠/滚动 | P2 | 低风险 |
| CONV-05 | 工具调用 / 执行输出 | `shell-conversation` | `ExecToolCall`（`UT`）、tool result、`ToolCallContent`；S05 | 命令、工作目录、输出、退出码；运行中/完成/失败；长输出折叠。 | `ToolCallContent` → `ActivityDisclosure` + `toolActivityLabel`；`toolResultIndex` 合并结果；没有退出码/工作目录的完整呈现。 | partial | S05 tool call claims；H `app/ConversationComponents.kt:490`、`:504`、`:352` | 补命令/输出/退出码/工作目录；核对折叠与复制 | P1 | 需要协议侧工具结果字段 |
| CONV-06 | 文件变更卡片 | `shell-conversation` | `FileChangePatchCard`（`hO`）、`TurnDiffItem`（`lA`）；S05 | 文件变更 patch 卡片；diff 行；展开/收起；与 turn diff 关联。 | `FileChangeContent` → `FileChangeCard`（`app/FileChangeCard.kt:26`）；`fileChangeRuns` 聚合。 | partial | S05 file change claims；H `app/FileChangeCard.kt:26`、`app/ConversationComponents.kt:472` | 核对 patch 展示、文件头、行内 diff、复制 git apply 命令；补 turn diff 关联 | P1 | 与 Git 区共用 diff 渲染 |
| CONV-07 | 错误卡片 | `shell-conversation` | `StreamErrorMessage`（`lk`）、`SystemErrorMessage`（`_t`）；S05 | 流错误与系统错误是两类；错误可重试；错误文案/详情分开。 | `ConversationErrorCard(message, onRetry)` 单一类型（`ConversationComponents.kt:541`）。 | partial | S05 error claims；H `app/ConversationComponents.kt:541` | 区分 stream/system 错误；补错误详情折叠与重试语义 | P2 | 需要协议侧错误分类 |
| CONV-08 | 子 Agent 活动组 | `shell-conversation` | `SubagentActivityGroup`（`Bc`）、`SubagentActivityChipGroup`；S05 | turn 内子 Agent 活动组；chip 展开；进行中/完成。 | `ThreadPanels` 的 `Subagents` tab + `SubagentSection`（`panels/ThreadPanelModels.kt:21`、`panels/ThreadPanels.kt:62`）；对话内没有 activity group。 | partial | S05 subagent claims；H `panels/ThreadPanelModels.kt:21`、`panels/ThreadPanels.kt:62` | 决定子 Agent 活动是放在对话内还是侧栏；若对话内，补 turn 内 chip group | P2 | 现有侧栏已有子 Agent 数据 |
| CONV-09 | Turn diff 摘要 | `shell-conversation` | `ConversationTurnDiffSummary`（`Yl`）、`TurnDiffItem`（`lA`）；S05 | 每个 turn 的文件变更摘要；打开 diff。 | 对话内没有 turn diff 摘要；`ThreadSummaryPanel` 有环境/活动摘要，但不是 diff。 | missing | S05 turn diff claims；H `summary/ThreadSummaryPanel.kt:51` | 在标题栏/助手消息尾部加 turn diff 摘要；复用 Git diff 数据 | P1 | 依赖 Git diff 能力 |
| CONV-10 | 用户消息编辑 | `shell-conversation` | `UserMessage`（`rx`）→ `UserMessageBubble`（`wt`）/ `UserMessageEditor`（`bt`）；S05 | 用户消息气泡；编辑态；长文本展开；提交/取消编辑。 | `LiveConversationComposer` 有 `onCancelEdit` 与编辑状态；`MessageCard` 渲染用户/助手消息；长文本展开需核对。 | partial | S05 user message claims；H `app/LiveConversationComposer.kt:24`、`app/ConversationComponents.kt:161` | 核对编辑入口、长文本折叠、取消编辑的焦点返回 | P2 | 现有编辑模型可能只在 composer |
| CONV-11 | Composer 模式与键盘 | `shell-conversation` | `DefaultComposerSurface`（`Il`）→ `CodexComposer`（`Q`）、`handleComposerKeyDown`（`a`）、`resolveEscapeAction`（`r`）、`canStopTurnFromEscape`（`n`）、`formatSubmitShortcut`（`t`）、`toggleComposerMode`（`i`）、`isDefaultComposerMode`（`Tr`）、`isPlanComposerMode`（`Er`）；S05 | 默认/plan 两种 composer 模式；Escape 在可停止时停止 turn，否则关闭/回退；submit shortcut 动态显示；Enter 行为可配置。 | `LiveConversationComposer`：Enter/Shift+Enter、发送/停止、编辑态；`ComposerPresentation` 有主操作解析；没有 plan 模式，Escape 只关闭 popup，没有"Escape 停止 turn"。 | divergent | S05 composer claims；R13 shell-home composerButtons；H `app/LiveConversationComposer.kt:24`、`app/ConversationComposerToolbar.kt:71`、`app/ComposerPresentation.kt:50` | 先补 Escape 停止 turn 与 submit shortcut 文案；再评估 plan 模式；Enter 行为接入 General 设置 | P1 | 键盘语义影响所有端；需要交互测试矩阵 |
| CONV-12 | 附件 | `shell-home` / `shell-conversation` | `useComposerAttachmentActions`（`X`）、`formatAttachmentUploadError`（`he`）；S05 | 添加文件等内容；附件上传成功/失败；附件列表/移除；与消息一起发送。 | `ConversationComposerToolbar` 的附件按钮是 `disabled()` 占位（`testTag = "live-composer-attachment"`，`ConversationComposerToolbar.kt:86`）。 | missing | S05 attachment claims；R13 shell-home composerButtons（`添加文件等内容`）；H `app/ConversationComposerToolbar.kt:86` | 接通附件选择/上传/移除/错误；与 Host API 的文件能力对齐 | P0 | 需要 Host 文件上传/存储能力；不是纯 UI |
| CONV-13 | Follow-up 队列 | `shell-home` / `shell-conversation` | `useFollowUpQueueMode`（`f`）、`QueuedMessageList`（`ze`）；S05 | 运行中可排队 follow-up；队列列表；编辑/删除/立即发送；停止后保留队列。 | 没有队列 UI；`ConversationController` 只有"Stop keeps queued inbox"注释（`conversation/ConversationController.kt:115`、`:745`），没有可见队列。 | missing | S05 queue claims；H `conversation/ConversationController.kt:115` | 先确认 Harness 协议是否支持排队；若支持，补队列 UI 与停止语义；若不支持，明确标注 | P1 | 协议/执行器能力；DSH 与 Codex 的队列语义可能不同 |
| CONV-14 | 草稿恢复 | `shell-home` / `shell-conversation` | `ComposerDraftRecoveryNotice`（`fr`）；S05 | 检测到未发送草稿时提示恢复；恢复/丢弃。 | 未发现对应组件；草稿只存在 composer 当前状态。 | missing | S05 draft recovery claims；H 无 | 若 Harness 有本地草稿存储，补恢复提示；否则明确不做 | P2 | 需要本地持久化与恢复策略 |
| CONV-15 | Composer rail | `shell-home` / `shell-conversation` | `DefaultComposerSurface` 的 project/work-location/branch/permission/reasoning/send；R13 shell-home composerButtons；S05 | 项目选择、运行位置选择、分支切换、添加文件、改权限、选择推理强度、发送；底部 rail 38dp。 | `ComposerProjectRail`：项目选择、工作位置 chip（仅本机）、环境齿轮；工具栏有模型/推理/权限/发送；没有分支 chip。 | partial | R13 shell-home composerButtons；H `app/ConversationComposerRail.kt:48`、`app/ConversationComposerToolbar.kt:44` | 补分支 chip 与运行位置的真实选项；核对 rail 的顺序/分组；把模型选择并入 Codex 的推理强度控件还是保留独立控件需决定 | P1 | 分支切换依赖 Git 能力；运行位置依赖 worktree/cloud |
| CONV-16 | 摘要面板 / 环境 | `shell-conversation` | `ConversationPage` 的 summary panel；S05 | 线程摘要、环境、活动、来源；打开 review。 | `ThreadSummaryPanel`（`summary/ThreadSummaryPanel.kt:51`）：环境、活动、来源、打开 review 入口。 | partial | S05 summary claims；H `summary/ThreadSummaryPanel.kt:51` | 对照 Codex 摘要面板的分区/空态；补 review 入口的落点（当前指向 Git 设置页） | P2 | review 落点依赖 GIT-01 |

### 3.5 Git / worktree

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| GIT-01 | Changes / Review 侧栏标签 | `shell-conversation`（变更 tab）；R13 gitChanges | `thread-side-panel-tab-content.js`（`ReviewThreadSidePanelTab`、`ReviewSidePanelContent`、`ReviewToolbar`、`ReviewDiffArea`、`ReviewSourceMenu`、`ReviewStageFilter`、`ReviewHeaderToolbar`、`ReviewFileTree`、`ReviewDiffView`）；S07 | 线程侧栏的 Review/Changes 标签页：工具栏、diff 区、来源菜单（cloud/this branch/branch/last turn/commits/PR）、stage 过滤（Uncommitted/Unstaged/Staged）、文件树、jump to file、refresh、wrap、expand/collapse、options、stage/unstage、hunk 菜单。 | 侧栏 `ThreadPanelTab` 只有 `BackgroundOutput` / `Subagents`（`panels/ThreadPanelModels.kt:15`）；Git 操作在设置页 `SettingsGitPage`。 | missing | S07 review tab claims；R13 `coverage.gitChanges`；H `panels/ThreadPanelModels.kt:15`、`panels/ThreadPanels.kt:62` | 把 Changes 作为线程侧栏第一等 tab；先做只读 diff + 文件树，再做 stage/unstage；来源菜单按 Codex 的 6 个来源 | P0 | 需要 Git 后端支持 stage/unstage、来源切换；现有 `GitController` 只有仓库/状态/分支/提交/diff |
| GIT-02 | `/diff` 路由 / diff 查看器 | `shell-conversation` | `EditorDiffPage`（懒加载）；`code-diff.js`（`or`、`lr/Tr/Er/Dr/Or`、`pr/yr/xr/Sr/Cr/wr`）；S07 | 独立 `/diff` 路由；Pierre diff 渲染；文件头、body actions、行内 diff、word diff。 | `GitPages.kt` 内有 diff 查看器（`GitDiffLayout`、`GitDiffLines`、`GitDiffWordDiff` 等）；没有独立 `/diff` 路由。 | partial | S07 code-diff claims；H `git/GitPages.kt`、`git/GitDiffLayout.kt:8`、`git/GitWordDiff.kt:91` | 决定 diff 是设置页内、侧栏 tab 还是独立页；优先复用现有 diff 渲染组件 | P1 | 与 GIT-01 的侧栏 tab 归属需要统一 |
| GIT-03 | 分支切换器 | `shell-home` / `shell-conversation` | `GitBranchSwitcher` → `BranchPickerPopoverContent`、`CreateBranchDialog`、`CheckoutBlockedDialog`；S07 | composer footer 的分支控件；分支列表/搜索/创建；checkout 被阻止时弹窗说明。 | `GitController` 有 `GitBranchSwitcher`/`GitCommitter` 接口与创建分支逻辑（`git/GitController.kt:9`、`git/GitModels.kt:182`）；UI 在 `SettingsGitPage` 内，不在 composer。 | partial | S07 branch switcher claims；R13 shell-home composerButtons（`切换分支`）；H `git/GitController.kt:9`、`git/GitModels.kt:182`、`git/GitPages.kt:19` | 把分支切换器放到 composer rail；复用 controller 的 create/checkout blocked 状态；补搜索与当前分支显示 | P1 | 需要 controller 暴露 UI 所需状态 |
| GIT-04 | 提交表单 | `shell-conversation` | 提交表单（commit message / include unstaged / commit & push）；S07 | commit message、include unstaged、commit & push；提交前校验；提交错误。 | `SettingsGitPage` 有提交表单（`git/GitPages.kt:19` 起）；`GitController` 有 commit 操作。 | partial | S07 commit claims；H `git/GitPages.kt:19`、`git/GitController.kt:9` | 把提交表单迁到 Changes 侧栏/对话上下文；核对 include unstaged 与 commit & push | P1 | 与 GIT-01 一起做 |
| GIT-05 | Commit & push / Create PR | `shell-conversation` | Git actions 菜单（Commit / Push / Create PR）；`WorktreeBranchSetupModal`；S07 | commit、push、create PR；worktree 内建分支后提交/推送/PR。 | `GitController` 有 commit；未发现 push/PR 能力（grep 无 `Create PR`/`Pull Request`）。 | missing | S07 git actions claims；H `git/GitController.kt:9`、`git/GitPages.kt` | 先做 push；PR 需要 GitHub/远端集成与凭据，按产品范围决定 | P1 | PR 依赖远端 API；不能只做按钮 |
| GIT-06 | Worktree 创建 | `shell-conversation` | `WorktreeBranchSetupModal`；S07 | worktree 内建分支后提交/推送/PR；创建流程。 | `ManagedWorktreeController` + `WorktreesPage` 有创建/管理工作树；composer 侧没有 worktree 创建弹窗。 | partial | S07 worktree modal claims；H `git/ManagedWorktreeController.kt:7`、`git/GitPages.kt:131` | 决定 worktree 创建入口：设置页（现）还是 composer（Codex）；若迁移，复用 managed controller | P2 | 入口迁移影响现有 Worktrees 页 |
| GIT-07 | Worktree 恢复横幅 | `shell-conversation` | `WorktreeRestoreBanner`；S07 | 被清理的 worktree 在对话内显示恢复横幅；恢复动作。 | 未发现恢复横幅；`pendingWorktreeChat` 只处理"在此工作树新建聊天"（`app/HarnessApp.kt:531`）。 | missing | S07 restore banner claims；H `app/HarnessApp.kt:531` | 补恢复横幅与恢复动作；与 managed worktree 归档/清理状态打通 | P2 | 需要 worktree 生命周期事件 |
| GIT-08 | Git 设置偏好 | `git-settings` | `git-settings-b9c4caaa37d1.js`（`GitSettings`）；S07 | 9 项偏好：disable Git-based diffs、branch prefix、always force push、draft PR、PR merge method、commit instructions、PR instructions、review delivery inline/detached。 | `SettingsGitPage` 是仓库操作台，没有这 9 项偏好。 | divergent | S07 `p7-182`…`p7-190`；R13 rail #16；H `git/GitPages.kt:19` | 新建 Git 偏好区（可放在设置页顶部或独立 section）；把仓库操作迁出 | P1 | 需要设置存储模型；现有 `WorktreeSettings` 可作参照 |
| GIT-09 | Worktrees 设置 | `worktrees` | `worktrees-settings-page-*.js`；S07 | 见 SET-28。 | `WorktreesPage`（`git/GitPages.kt:131`）。 | partial | S07 worktree claims；R13 rail #18；H `git/GitPages.kt:131` | 见 SET-28 | P1 | 同 SET-28 |
| GIT-10 | Stage/unstage + 来源/stage 过滤 | `shell-conversation` | `ReviewStageFilter`、`StageUnstageButton`、`HunkActionMenu`、`ReviewSourceMenu`；S07 | Uncommitted/Unstaged/Staged 过滤；逐文件/逐 hunk stage/unstage；来源切换。 | `GitController` 的查询只有 Repository/Status/Branches/Worktrees/Diff/Commit/SummaryStats（`git/GitController.kt:14`）；没有 stage/unstage。 | missing | S07 stage claims；H `git/GitController.kt:14` | 先补 Git 后端 stage/unstage/hunk；再补 UI 过滤与动作 | P1 | 后端能力是前置；错误恢复（部分 stage 失败）需设计 |

### 3.6 scheduled

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| SCH-01 | 路由与重定向 | `shell-scheduled` | `app-runtime`（`/automations` → `/scheduled`）；`ScheduledTasksPage`（`page-7ef1423c8917.js`）；`AutomationsLayoutPage`；S08 | 旧 `/automations` 重定向到 `/scheduled`，保留 query/hash；页面标题 Scheduled，列表头 Scheduled tasks；查询参数 `automationId`、`edit=true`、`shared_task`、`share_dialog`；应用内 `/scheduled/automation/:id` 打开编辑标签页。 | `PageId.ScheduledTasks(query)`；`ScheduledManagementPage`；没有 `/automations` 别名与搜索参数语义。 | partial | S08 routing claims；R13 shell-scheduled；H `shell/NavigationState.kt:58`、`scheduling/ScheduledManagementPage.kt:18` | 补 `/automations` 兼容入口（若需要）；建立 `automationId`/`edit`/`shared_task`/`share_dialog` 的页面参数模型 | P2 | 需要导航参数与编辑标签页模型 |
| SCH-02 | 列表 / 状态筛选 / 分组 / 搜索 | `shell-scheduled` | `ScheduledTaskStatusFilter`（Active/Paused/Completed）、`ScheduledTaskGroupSection`、`ScheduledTasksListContent`、`ScheduledTaskRow`；S08 | Active / Paused / Completed；分别查询 scheduled/paused/finished；Active 说明含最近一天暂停/完成；按状态分组；空组 No tasks；搜索空结果 No scheduled tasks found；`matchesAutomationSearch` 规范化（小写、折叠空白、trim）。 | `ScheduledManagementNavigator`：搜索、状态筛选（`ScheduledTaskStatus` 只有 Paused/Active）、排序（Automatic/NextRun/Updated/Title）、分组/空态需核对。 | partial | S08 list claims；R13 shell-scheduled（`安排任务`、新建任务）；H `scheduling/ScheduledManagementNavigator.kt:22`、`scheduling/ScheduledModels.kt:52`、`:88` | 补 Completed 状态与对应查询；对齐搜索规范化与空态文案；核对分组/最近一天说明 | P1 | 需要后端支持 finished 状态查询 |
| SCH-03 | 行操作 | `shell-scheduled` | `ScheduledTaskRow`：暂停/恢复、编辑、Run now、删除、更多；S08 | 行内暂停/恢复、编辑、Run now、删除、更多；失败文案；删除确认 `Delete {name}?`。 | `ScheduledManagementPage` 有暂停/恢复、删除确认、运行入口（`onOpenRun`）；run now 需核对。 | partial | S08 row actions claims；H `scheduling/ScheduledManagementPage.kt:18` | 核对 Run now 与更多菜单；补失败文案与删除确认措辞 | P2 | 需要执行器 RPC |
| SCH-04 | 创建菜单 | `shell-scheduled` | `AutomationCreateMenu`（Set up manually / Create with ChatGPT / Create with Codex）；S08 | 三个创建入口；手动创建写入 `automationMode=create`；触发器名 Create scheduled task options。 | `ScheduledManagementPage` 有新建任务（手动表单）；没有 ChatGPT/Codex 创建入口。 | missing | S08 create menu claims；R13 shell-scheduled（新建任务）；H `scheduling/ScheduledManagementPage.kt:18` | 补创建菜单与两个非手动入口；若 Harness 不做，明确标注 | P1 | 依赖 ChatGPT/Codex 创建后端 |
| SCH-05 | 编辑器字段 / 频率 / 目标 / 通知 | `shell-scheduled` | `ScheduledTaskComposer`、`AutomationPromptEditor`、`AutomationModelControls`、`AutomationSandboxBanner`、`AutomationFrequencySection`（ScheduleMode/Weekday/MonthDay/EndRepeat/Advanced）、`ProjectSelect`/`ThreadPicker`/`DestinationPicker`/`ExecutionLocationSelect`/`NotificationPolicySelect`；S08 | 名称/prompt/模型/reasoning/sandbox/audience；频率（每小时/每天/工作日/每周/每月/结束重复/高级规则）；目标（项目/线程/目的地/执行位置/通知策略）；提交校验 tooltip。 | `ScheduledModels`：`ScheduledRule` Hourly/Daily/Weekdays/Weekly；`ScheduledTarget` Cron/Heartbeat；`ScheduledModelRef`；`ScheduledPageLayout` 表单；没有 MonthDay/EndRepeat/Advanced、通知策略、团队 audience。 | partial | S08 editor claims；H `scheduling/ScheduledModels.kt:26`、`:39`、`:80`、`scheduling/ScheduledPageLayout.kt:28` | 补月日/结束重复/高级规则、通知策略；核对目标选择器与执行位置；补提交校验 tooltip | P1 | 需要调度后端支持复杂规则 |
| SCH-06 | 运行历史 / ledger / 归档 | `shell-scheduled` | `AutomationRunHistoryPanel`（Previous runs、archive、unread）、`ScheduledTaskRunResults`、`ScheduledTaskRunSummary`、`RunChatActions`、`RunResultsHistoryList`；S08 | 运行历史、归档、未读；运行结果/摘要；打开运行会话。 | `ScheduledExecutor`、`ScheduledLedgerRules`、`ScheduledTaskSummary`、`onOpenRun`；历史/归档/未读 UI 需核对。 | partial | S08 run history claims；H `scheduling/ScheduledExecutor.kt:12`、`scheduling/ScheduledLedgerRules.kt:4`、`scheduling/ScheduledTaskSummary.kt:39` | 对照补未读/归档/历史列表；核对运行结果与会话打开 | P2 | 现有执行器/ledger 已具备；以 UI 为主 |
| SCH-07 | 团队 / 个人所有权 | `shell-scheduled` | `TeamAutomationSelect`、All/Team/Personal、Run as、团队选择器 loading/empty/retry；S08 | 团队/个人筛选；Run as；团队选择器状态。 | 无团队/个人所有权模型；`ScheduledTask` 没有 audience/team 字段（需核对）。 | missing | S08 team claims；H `scheduling/ScheduledModels.kt:57` | 产品决定是否引入团队所有权；若不做，标注为 Harness 单用户模型 | P2 | 依赖团队/租户后端 |
| SCH-08 | 侧栏 automation tab | `shell-scheduled` | `AutomationSidePanelTabContent`（site/cloud）；S08 | 运行中定时任务的侧栏 tab；site/cloud 两种内容。 | `ThreadPanelTab` 只有 BackgroundOutput/Subagents；没有 automation tab。 | missing | S08 side panel claims；H `scheduling/ScheduledModels.kt` 无 | 若定时任务会产生运行会话，补侧栏 tab 与结果展示 | P2 | 依赖运行会话归属 |

### 3.7 plugins / account

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| PLG-01 | 插件目录 / marketplace | `shell-plugins` | `/plugins`（`SkillsPage` → `SkillsPageRouter` → `PluginDirectoryPage`）；`PluginDirectoryTabs`（plugins/skills）、`PluginDirectoryList`、`PluginCardGrid`、`PluginCard`、`PluginRequestDialog`、`ImportedConnectorsSection`、`PluginOwnershipSections`；S09 | 插件目录；静态有 plugins/skills 两个 tab；运行态插件页是 marketplace 形态：`公开`/`个人` 两个 tab、分类标题（Communication / Creativity / Data & Analytics / Developer Tools / Education & Research 等）、`未安装任何插件`；另有卡片网格、请求插件、导入连接器、所有权分组。 | `InstalledPluginsPage`：本地插件列表/详情/Skills/能力；没有 marketplace、公开/个人、分类、卡片网格、请求插件、连接器。 | divergent | S09 plugin directory claims；R13 `coverage.plugins`（公开/个人、分类、未安装提示）；H `plugins/InstalledPluginsPage.kt:51` | 明确 Harness 是"本地插件登记"还是"插件市场"；若只做本地，重命名/收敛文案，避免呈现成 Codex marketplace | P1 | 需要 marketplace 后端；产品范围决定 |
| PLG-02 | 插件详情 | `shell-plugins` | `/plugins/:pluginId`（`PluginDetailPage`、`PluginIncludesSection`、`PluginDependenciesSection`、`PluginInstancesSection`、`PluginDetailActions`、`PluginManagementMenu`、`PluginUpgradeCallToAction`、`PluginInstallModal`、`UninstallPluginDialog`、`PluginDesktopHandoffDialog`）；S09 | 插件详情：包含项、依赖、实例、安装/卸载、升级、桌面 handoff。 | `InstalledPluginDetailPage`：详情、Skills、能力、启用状态、刷新、返回；没有依赖/实例/升级/安装 modal/卸载确认。 | partial | S09 plugin detail claims；H `plugins/InstalledPluginsPage.kt:99` | 补依赖/实例/卸载确认；核对安装/升级语义 | P2 | 依赖插件仓库能力 |
| PLG-03 | Plugins settings manage tabs | `plugins-settings` | `PluginsSettings` → `PluginsManagePage`：plugins/apps/mcps/skills/marketplace；`MarketplaceListSection`/`Remove`/`Details`；S09 | manage tabs；marketplace 列表/移除/详情；host/plugin 权限页；Browser/PluginHooks/Information/Remove sections。 | 无 manage tabs；`InstalledPluginsPage` 只有本地插件；没有 apps/mcps/skills/marketplace 分区。 | missing | S09 plugins-settings claims；R13 rail #10；H `plugins/InstalledPluginsPage.kt:51` | 按 Codex 的 5 个 tab 建管理面；至少先做 mcps（见 PLG-04） | P0 | 依赖各 tab 的后端能力 |
| PLG-04 | MCP 服务器列表 / 编辑 / 认证 | `plugins-settings`（MCP 内容在插件页） | `McpServerDetailEditor`、`McpServerAuthControls`、`AddMcpServerPanel`；S09；S14 d7/d9 | MCP 服务器列表、详情编辑、认证控制、添加面板；`mcp-settings` 没有独立面板，内容在插件页。 | 无 MCP UI；`PluginCapabilityKind.McpServer` 只用于能力标签，文案明确"MCP (unsupported)"（`plugins/InstalledPluginsPage.kt:343`、`:354`）。 | missing | S09 MCP claims；S14 `chains/mcp.json`（内容在插件页）；R13 d7/d9；H `plugins/PluginModels.kt:45`、`plugins/InstalledPluginsPage.kt:343` | 建 MCP 服务器列表/详情/认证/添加；不要把 MCP 放到独立设置页（Codex 运行态没有 `mcp-settings` 面板） | P0 | 需要 MCP 客户端/凭据存储；当前明确不支持 |
| PLG-05 | Connections / 远程控制 | `connections` | `RemoteConnectionsSettings`（见 SET-25）；S09 | access other devices / control this Mac / SSH / WSL；配对码；授权远程控制；远程控制客户端。 | `SettingsConnectionsPage`：Host 连接/发现/选择；没有远程控制/配对/授权。 | divergent | S09 remote connections claims；R13 rail #15；H `page/SettingsConnectionsPage.kt:44` | 拆分"Host 连接"与"远程控制"；若两者都要，各自独立 section | P1 | 与 SET-25 同一产品决定 |
| PLG-06 | Account / profile / billing / usage | `account` 等 static-only | `/settings/account`（`ChatGptAccountSettings`）、`/settings/profile`（`ProfileSettings`）、`/settings/billing`（`ConsumerBillingSettings`）、usage 路由；S09 | 账户名/Builder profile/邮箱验证/用户名/standing/status/删除账户；profile；billing 明细/支付方式/钱包/交易历史/取消订阅；usage 页。 | `AccountWorkspacePage`：Host 登录（租户/手机号/密码）、登录/登出；没有 ChatGPT 账户/profile/billing/usage。 | divergent | S09 account/billing claims；R13 `catalogNotObserved` 含 account/profile/billing/usage；H `account/AccountWorkspacePage.kt:23` | 保持 Host 登录；不要把 ChatGPT 账户面板硬塞进 Account 页；若产品需要 ChatGPT 账户，另设入口；其中 `billing` 一期不做（Phase 2），account/profile/usage 仍按原优先级评估 | P2 | 产品身份模型不同；多数为 static-only |
| PLG-07 | Login / welcome / workspace select | 无运行态（未登录） | `/login`（`LoginRoute`、`BedrockLoginForm`、`LoginFallbackMethods`）、`/welcome`、`/select-workspace`、`/logout`；S09 | 登录路由、Bedrock 登录、回退方式、浏览器会话继续提示、登录状态、工作区选择、登出影响列表。 | `AccountWorkspacePage` 的 Host 登录/登出；没有 Codex 的 ChatGPT 登录/工作区选择。 | divergent | S09 login claims；R13 没有登录观测（已登录态启动）；H `account/AccountWorkspacePage.kt:23` | 明确登录模型差异；若 Harness 没有 ChatGPT 登录，标注为平台差异 | P2 | 登录体系不同；不能按 Codex 复制 |
| PLG-08 | Plugin capability / host 隔离 | `plugins-settings` / `shell-plugins` | `PluginPermissionsPage`、`PluginAppSettingsRoute`、`PluginSettingsPage`、`Host` 隔离；S09 | 按 host 的插件权限/设置；插件与 host 的隔离。 | `PluginManagement` / `PluginRepository` 有本地能力枚举；没有 host 级权限页。 | partial | S09 permission claims；H `plugins/PluginManagement.kt:14`、`plugins/PluginModels.kt:45` | 补 host 级权限/设置；至少把能力枚举与权限语义对齐 | P2 | 依赖 Host 权限模型 |

### 3.8 terminal / file panel

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| TERM-01 | 终端 tab / panel | `shell-conversation` | `terminal-tab`（`LocalTerminalTab`/`CloudTerminalTab`）、`terminal-panel`（xterm + Fit/WebLinks/Clipboard）、`TerminalHostService`（snapshots/write/resize/readOutput/reconnect）、主题/字体/scrollToBottom/custom key handler；S11 | 终端 tab；xterm 面板；PTY host 服务；快照/写入/resize/读输出/重连；主题与代码字体；滚动到底；自定义按键。 | `ThreadPanelTab.BackgroundOutput` + `PtyTerminalPanel` + `PtyTerminalController` + `PtyTerminalGrid`；有 PTY 输入/输出/resize/取消；没有多终端 tab、主题/字体设置、scrollToBottom/reconnect 的完整状态。 | partial | S11 terminal claims；H `panels/ThreadPanels.kt:62`、`panels/PtyTerminalPanel.kt:22`、`panels/PtyTerminalController.kt:13` | 核对多终端/重连/主题字体；补 tab 关闭与进程生命周期分离（现有注释已声明关闭视图不杀进程） | P1 | 需要 PTY 服务与多 session 管理 |
| TERM-02 | 后台终端 / thread terminal | `shell-conversation` | `background-terminal`、`local-conversation-background-terminal-tab`、`XtermOutputPanel`、`read_thread_terminal` tool；S11 | 后台终端缩略图/tab；读取 cwd/shell/buffer 快照。 | `BackgroundTerminalKey` / `BackgroundTerminalOutput` / `BackgroundTerminalSummary` + `ThreadPanels` 的 BackgroundOutput tab。 | partial | S11 background terminal claims；H `panels/ThreadPanelModels.kt:74`、`panels/ThreadPanels.kt:62` | 核对快照字段（cwd/shell/buffer）与缩略图；补 reconnect/cancel 的状态 | P2 | 协议侧字段可能已具备 |
| TERM-03 | 文件浏览器 / 文件树 | `shell-conversation` | 文件浏览器 + virtualized tree（custom element/shadow DOM）；context menu、open-in、save-as、copy-path、rename、delete；Files toolbar find-in-files；S11 | 文件树；上下文菜单；打开方式/另存为/复制路径/重命名/删除；find-in-files host message。 | 没有侧栏文件树/文件浏览器；只有 `ProjectFileSearchDialog`（S-01 文件搜索）与项目文件搜索。 | missing | S11 file browser claims；H `projects/ProjectFileSearchDialog.kt:31`、`projects/ProjectFileSearch.kt:8` | 建文件树面板（可作为侧栏 tab）；先做只读 + 打开文件，再做重命名/删除；find-in-files 复用项目搜索 | P1 | 需要文件系统 API 与权限模型 |
| TERM-04 | 编辑器 / artifact preview / sketch | `shell-conversation` | 文本/Markdown/Pierre 编辑器 tabs、artifact preview、sketch/annotation editors、workspace-agent files、library picker；S11 | 多类型编辑器 tab；artifact preview；sketch/annotation；workspace-agent 文件；库选择器。 | 没有编辑器/预览/sketch；`ThreadPanelTab` 只有 BackgroundOutput/Subagents。 | missing | S11 editor claims；H `panels/ThreadPanelModels.kt:15` | 产品决定编辑器范围；至少先做只读文本/Markdown 预览与 artifact preview | P2 | 工作量大；依赖编辑器组件选型 |
| TERM-05 | 底部面板 chrome | `shell-conversation` | `app-shell-bottom-panel`（持久 280px 高度、focus area bottom-panel）；S11 | 底部面板；持久高度；焦点区域；关闭/展开。 | `ThreadPanelLayout`（`panels/ThreadPanelLayout.kt:6`）有宽度/布局策略；高度持久化需核对。 | partial | S11 bottom panel claims；H `panels/ThreadPanelLayout.kt:6` | 核对 280px 默认高度与持久化；补 focus area 语义 | P2 | 需要布局存储 |
| TERM-06 | 文件搜索（S-01 已确认） | `shell-home` / `shell-conversation` | 命令菜单 Search files + fuzzy search；旧审计 S-01 | 文件搜索接通、⌘P/Ctrl+P、键盘选择、只读预览。 | `ProjectFileSearchDialog` + `ProjectFileSearch` + `DesktopProjectFileSearch`（S-01 已实现）；`AppShell` 的 `onSearchFiles`。 | partial | 旧审计 S-01 确认记录；H `projects/ProjectFileSearchDialog.kt:31`、`shell/AppShell.kt:105`；S-01 自动化交互测试通过，但确认记录明确“尚未进行独立桌面窗口的人工视觉验收”，无同视口视觉基线 | 保持功能；补视觉验收/同视口截图后再评 `aligned`，并继续核对 fuzzy 排序/高亮 | P2 | 功能已确认，不应回退；视觉基线未验收 |

### 3.9 responsive

| ID | Codex surface / route / panel | runtime slug | source chunk / binding | Codex 行为契约 | Harness 当前实现（file:line） | 状态 | 证据 | 迁移动作 | 优先级 | 风险 / 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|
| RESP-01 | 断点 / 指针 / 触控 | 全部 surface | `smallViewportStore`（`(width < 640px), (pointer: coarse)`）、`useMediaQuery`、`isTouchDevice`、`mobileLayoutStore`（恒 false）；S10 | 640px 小屏；粗指针在任何宽度算小屏；移动布局 flag 恒 false；Electron `browser:` 变体不生效。 | `NavigatorLayout` 按可用宽度分 Hidden/Pushed/Floating；`ShellLayoutSettings` 持久化宽度；没有统一的 640px/pointer-coarse store。 | divergent | S10 breakpoint claims；H `shell/NavigatorLayout.kt:21`、`shell/ShellLayoutSettings.kt:14` | 定义 KMP 各端的断点/输入能力模型；不要复制 Electron 的 `browser:` 变体 | P2 | 多端（Android/iOS/Desktop）语义不同 |
| RESP-02 | 侧栏宽度 clamp / 抽屉 | 全部 surface | `--spacing-token-sidebar: clamp(240px, 275px, min(520px, calc(100vw - 320px)))`；`AppShellFrame` 的 `data-app-shell-responsive-sidebar=false`；`FloatingLeftPanel`；S10 | 侧栏最小 240px、最大 520px、主面至少 320px；临时展开时浮动；Electron 路径响应侧栏 false。 | `NavigatorMinWidth=240.dp`、`NavigatorMaxWidth=520.dp`、`NavigatorPreferredWidth`；`NavigatorLayout.Floating`；注释明确对齐 `--spacing-token-sidebar`。 | partial | S10 sidebar CSS/AppShellFrame claims；H `shell/NavigatorLayout.kt:28`、`:34`、`:42`；视觉层：只有 CSS token 与 240/520 常量，无同视口截图/视觉基线 | 保持尺寸契约；补窗口缩放/抽屉的同视口截图与交互基线后再评 `aligned` | P3 | 结构对齐，视觉基线缺失 |
| RESP-03 | 响应式 Dialog / Sheet | 全部 surface | `DialogContent`（`responsiveSheet` / `edgeToEdgeSheet`）：<40rem 底部 sheet，>=40rem 垂直居中；S10 | 窄屏底部 sheet；宽屏居中 dialog；edge-to-edge。 | `HarnessDialog` 的响应式行为未在本矩阵核对；需要看 `ui-foundation`。 | unknown | S10 dialog claims；H `ui-foundation` 的 `HarnessDialog`（未展开核对） | 核对 `HarnessDialog` 在窄宽度下的 sheet 行为；补 40rem 断点 | P2 | 需要 UI foundation 代码与运行态 |
| RESP-04 | Reduced motion / hover / pointer | 全部 surface | `useReducedMotion`、`isTouchDevice`、`pointer-coarse:` 工具类、Popover `sideOffset 8`/`collisionPadding 20`/`hideWhenDetached`；S10 | reduced motion 三态；粗指针；hover 能力；popover 定位。 | `HoverCapability`（S-02）、`HoverPopoverState`、`NavigatorLayout`；reduced motion 需核对。 | partial | S10 motion/pointer claims；H `shell/HoverCapability.kt:1`、`shell/HoverPopoverState.kt` | 补 reduced motion；核对 popover 定位常量；把 hover 能力抽象用于桌面/触控 | P2 | S-02 已确认 hover 行为，不应回退 |
| RESP-05 | 平台差异（Electron vs KMP） | 不适用 | `data-codex-window-type="electron"`、`browser:` 变体、无原生移动壳；S10 | Codex 只有响应式 Web 布局，没有原生移动壳；Electron 入口固定 `electron`。 | Harness 是 Compose Multiplatform（Desktop/Android/iOS），有原生入口与移动导航。 | unknown | S10 结论；H `apps/android-app`、`apps/ios-app` | 不追求"复制 Electron"；建立 KMP 各端的等价交互矩阵（返回、触控、safe-area、IME） | P3 | 平台模型不同；需要真机验证 |

### 3.10 Harness 扩展逐项决定

扫描范围：矩阵中所有明确标为「Harness 扩展」「Harness 自有」「非 Codex 行为」的条目，以及 rail/资料库中 Harness 特有的槽位。以下 4 项；D-05～D-08 均已决定。SET-04 经 C-01 修正后不在此表，处理结论见 D-09。

| 条目 ID | file:line | Codex 对应面或明确缺失 | 当前矩阵处理 | 可选项（保留并标注 / 向 Codex 对齐 / 移除） | 建议与影响 | 状态 |
|---|---|---|---|---|---|---|
| SET-29 / SET-30 模型管理 | `page/SettingsModelsPage.kt:144`、`shell/SettingsNavigatorModel.kt:48` | Codex 运行态 19 面板无 `models`；最近似的是 static-only `gateways`（Model Providers） | SET-29 `unknown`/P2；SET-30 `unknown`/P3；SET-02 注明“模型管理是 Harness 自有项”；D-05 已决定保留并标注 | 保留并标注 / 向 Codex 对齐（映射到 `gateways`）/ 移除 | D-05：保留并标注为 Harness 扩展，不纳入 Codex 对齐验收；移除会影响模型选择与设置；向 `gateways` 对齐不在当前范围 | 已决定（D-05） |
| NAV-01 账号 rail | `shell/RailDestinations.kt:7`、`:37`、`:44-50`、`shell/NavigationState.kt:72` | 明确无账号 rail；账号入口在 profile footer（`uNt`，app-primary:33279，`codex.profileFooter.openProfileMenu` / `openSettings`），不是 rail 目的地；`account` 仅是设置面板 slug | `divergent`/P2；D-06 已决定移除，尚未实施 | 保留并标注为 Harness 扩展 / 移除（跟随 Codex）/ 向 Codex 对齐（把账号移出 rail，保留 profile footer 入口） | D-06：移除账号 rail；账号/个人资料只保留在 profile footer 与设置。移除会影响 rail 与导航入口，需同步调整账户菜单入口 | 已决定：移除账号 rail（D-06） |
| SIDE-08 资料库占位页 | `page/NewTabPage.kt:109`、`shell/NavigationState.kt:34` | Codex `资料库` 是文件产品；Harness `PageId.Library` 是明确的占位页 | `divergent`/P2；D-07 保留占位，不实现文件库 | 保留并标注 / 向 Codex 对齐（实现文件库）/ 移除 | D-07：保留占位，不实现文件库；继续标 `divergent`。实现 Library 需要文件后端与权限模型 | 已决定（D-07） |
| SET-12 主题编码（`AppearanceThemeCodec`） | `settings/AppearanceSettings.kt:109`、`page/SettingsAppearancePage.kt:30` | Codex 有主题导入/导出；Harness 用自有编码，格式互通未证实 | `partial`/P2；风险列标注“Harness 自有编码”；D-08 已决定移除导入导出 | 保留并标注 / 向 Codex 对齐（改用 Codex 主题 JSON）/ 移除导入导出 | D-08：主题导入/导出按 won't-do 记录，移除 UI 入口；其余外观控件继续按 Codex 对齐；`AppearanceThemeCodec` 保留内部使用，切换外部格式不在当前范围 | 已决定：移除导入导出（D-08） |

四项最终状态：D-05 模型管理保留并标注、D-06 移除账号 rail、D-07 资料库保留占位、D-08 主题移除导入导出；SET-04 已按 C-01/D-09 移出本表。

补充说明：

- SET-02 中的「模型管理」即 SET-30，不重复列。
- SET-12 的 `AppearanceThemeCodec` 是格式实现而非独立界面；因矩阵明确标注为“Harness 自有编码”，单列一行按格式兼容性决定。D-08 已决定移除导入/导出 UI，编码器保留内部使用。
- SIDE-01 的搜索走 Harness command menu/文件搜索，但 TERM-06 已由 S-01 确认，不作为待决定扩展。
- 其他未标为扩展、但 Harness 特有的行为（Host 登录、Relay 等）已有独立条目与语义冲突说明，不在本表。

## 4. Top 20 高优先级差距

排序原则：先核心编码回路（P0），再运行态已观测的主壳层/主流程（P1），最后是依赖产品范围决定的项。每项都能在上表按 ID 找到完整证据。

| # | ID | 差距 | 状态 | 优先级 | 为什么排在这里 |
|---:|---|---|---|---|---|
| 1 | GIT-01 | 线程侧栏没有 Changes/Review 标签页；Git 只在设置页 | missing | P0 | Codex 的真实 Git 入口是 Changes 侧栏，不是 `/git` 设置页；不做就没有"改完就能看/暂存/提交"的闭环 |
| 2 | CONV-12 | Composer 附件按钮是禁用占位 | missing | P0 | Codex 运行态 composer 明确有"添加文件等内容"；Harness 只有灰按钮 |
| 3 | PLG-03 / PLG-04 | 插件管理缺 apps/mcps/skills/marketplace；MCP 完全无 UI | missing | P0 | Codex 运行态 MCP 列表渲染在插件页；Harness 明确标注 MCP unsupported |
| 4 | SET-02 | 设置侧栏 8 项 vs Codex 19 个运行态面板；12 个面板缺失 | divergent | P1 | 设置面是运行态证据最全的区域，也是用户最容易感知的结构差距 |
| 5 | SET-13 | 已归档聊天没有管理面（搜索/恢复/删除/全量删除确认） | missing | P1 | Codex 运行态 rail #3；Harness 只有项目页的 `Archived` 筛选枚举 |
| 6 | CONV-02 | 对话没有 turn 级虚拟化/滚动锚定 | divergent | P1 | 长对话性能与阅读位置的核心行为；Codex 有完整 anchor/visible-range 算法 |
| 7 | CONV-11 | Composer 缺 plan 模式、Escape 停止、动态 submit shortcut | divergent | P1 | 键盘语义是 Codex composer 的主交互；Harness 只有 Enter/Shift+Enter |
| 8 | CONV-13 | Follow-up 队列无 UI | missing | P1 | Codex 运行中可排队；Harness 只有"Stop keeps queued inbox"注释 |
| 9 | CONV-01 / CONV-09 | 标题栏缺 Git actions 与 turn diff 摘要 | partial / missing | P1 | 对话页是主工作面；Codex 把 diff 摘要放在标题栏 |
| 10 | TERM-03 | 没有文件浏览器/文件树 | missing | P1 | Codex 的文件面板是 coding agent 的核心上下文；Harness 只有文件搜索 |
| 11 | GIT-08 / SET-26 | Git 设置页是仓库操作台，不是 Codex 的 9 项 Git 偏好 | divergent | P1 | 同名页语义冲突；不拆会把偏好和操作混在一起 |
| 12 | GIT-03 | 分支切换器只在设置页，不在 composer | partial | P1 | Codex composer footer 有"切换分支"；Harness 运行态没有 |
| 13 | GIT-10 | 没有 stage/unstage、来源/stage 过滤 | missing | P1 | Changes 面板的核心动作；现有 GitController 只有 commit/diff |
| 14 | GIT-05 | 没有 commit & push / Create PR | missing | P1 | Codex Git actions 菜单的闭环；Harness 只有 commit |
| 15 | SET-25 / PLG-05 | Connections 页是 Host 连接，不是 Codex 远程控制 | divergent | P1 | 同名页语义冲突；Codex 有配对码/授权远程控制/远程客户端 |
| 16 | SET-28 / GIT-09 | Worktrees 缺分组计数、pinned chat 删除阻止等细节 | partial | P1 | 现有实现已较完整；差异集中在边界状态 |
| 17 | SET-16 | 没有 Agent configuration（scope/diagnostics/runtime 安装） | missing | P1 | Codex 运行态 rail #6；Harness 的模型管理不是同一个面 |
| 18 | SET-24 / PLG-05 | 没有 Hooks 设置页 | missing | P1 | Codex 运行态 rail #14；Harness 只把 Hook 标为不支持 |
| 19 | SCH-04 / SCH-05 | 定时任务缺 ChatGPT/Codex 创建入口，编辑器缺月日/结束重复/通知策略 | missing / partial | P1 | Codex `/scheduled` 是运行态观测到的完整管理面 |
| 20 | PLG-01 | 插件目录是本地登记，不是 Codex marketplace | divergent | P1 | 运行态 Codex 插件页有分类市场；Harness 的"插件"语义偏窄 |

> 次高但未入 Top 20 的 P1：SIDE-01～SIDE-07（侧栏/landing 细节）、CONV-05/06（工具/文件变更卡片）、TERM-01（终端多 tab/重连）、SET-10/12/19（General/Appearance/Shortcuts 差异）。它们应在对应阶段与 Top 20 一起排期，而不是单独开线。

## 5. 建议迁移顺序

依赖关系是硬约束：没有 Git 后端 stage/unstage，就不能做 Changes 面板；没有协议侧附件/队列字段，就不能做 composer 附件/队列；没有 MCP 客户端，就不能做 MCP 管理面。因此顺序按"先能力、后 UI"排。阶段顺序保持 M0–M6（D-03）；本次 `aligned` 口径修订不改变阶段依赖，只强化 M0 的视觉基线验收。D-04 的扩展项不进入 M0–M6 的依赖链；涉及某阶段的扩展在该阶段开始前按 3.10 逐项决定。

### M0 — 基线与护栏（0.5–1 周）

- 固化本矩阵与 55 route key 附录；建立"运行态/静态/unknown"标注规范。
- 为设置页补 `settings-panel-<slug>` 语义 testTag 与截图/交互测试基线（SET-01、SET-03）。
- 按 D-02 建立结构+视觉双层基线：拟标 `aligned` 的条目必须有同视口截图或交互基线；SET-01、RESP-02、TERM-06 先按 `partial`（缺视觉基线）记录。
- 不改变任何现有用户确认行为（P-01～P-03、S-01、S-02）。

**验收**：矩阵行可追溯到 decode artifact 与 Harness 行号；每个 `aligned` 结论都有同视口截图或交互基线；现有测试全绿。

### M1 — 对话核心回路（2–3 周）

- CONV-12 附件（先协议/Host 能力，再 UI）。
- CONV-11 Escape 停止、submit shortcut、plan 模式（按产品决定）。
- CONV-13 follow-up 队列（先确认协议支持）。
- CONV-02 turn 模型 + turn 级列表/滚动锚定（可分两步：先 turn 分组，再虚拟化）。
- CONV-01/CONV-09 标题栏 Git actions + turn diff 摘要（依赖 M2 的 diff 数据）。

**验收**：长对话滚动位置稳定；运行中可停止/排队；附件可发送；对话页标题栏显示 turn diff 摘要。

### M2 — Changes / Git 闭环（2–3 周）

- GIT-10 后端 stage/unstage/hunk + 来源/stage 过滤。
- GIT-01 Changes 侧栏 tab（只读 diff + 文件树 → stage/unstage → 来源菜单）。
- GIT-03 composer 分支切换器；GIT-04 提交表单迁入 Changes 上下文；GIT-05 push（PR 按产品决定）。
- GIT-02 diff 渲染复用与 `/diff` 路由决策。
- GIT-08/SET-26 拆分 Git 偏好与仓库操作。

**验收**：从对话内可查看/暂存/提交/推送；Git 设置页只保留偏好；现有 Git 页入口不回归。

### M3 — 集成管理（2–3 周）

- PLG-04 MCP 服务器列表/详情/认证/添加（先只读列表，再编辑/认证）。
- PLG-03 插件 manage tabs（mcps → plugins → skills → apps → marketplace，按后端能力排序）。
- SET-24 Hooks 设置页；SET-16 Agent configuration（先 scope/diagnostics 只读）。
- PLG-01/PLG-02 插件目录与详情按产品范围收敛或扩展。

**验收**：MCP 服务器可列出/查看/认证；Hooks 可查看/启停；Agent config 可查看 scope 与诊断。

### M4 — 设置面补齐（2–4 周）

- SET-10 General 行集合、SET-12 Appearance 控件、SET-19 Shortcuts 差异。
- SET-28 Worktrees 细节；SET-25 拆分 Host 连接与远程控制。
- 12 个运行态缺失面板按产品范围决定：优先 SET-13 archived-chats、SET-27 local-environments、SET-17 personalization；ChatGPT/云/娱乐类（SET-11/14/15/18/21/22/23 与除 Phase 2 4 项外的 static-only ChatGPT 组）先标注"不纳入"或"待定"。
- 明确排除 3.1.3.1 的 Phase 2 backlog（`billing`、`teams`、`parental-controls`、`cloud-computer`）；M4 只覆盖 12 个运行态缺失面板与仍未决定的本地 static-only route。

**验收**：设置侧栏项与产品范围表一致；每个纳入的面板有 aligned/partial 结论与测试。

### M5 — 文件/终端（2–3 周）

- TERM-03 文件树（只读 → 打开文件 → 重命名/删除）。
- TERM-04 只读文本/Markdown/artifact preview；编辑器按产品决定。
- TERM-01 多终端 tab/重连/主题字体；TERM-05 底部面板高度持久化。

**验收**：对话侧栏可浏览文件并打开只读预览；终端可多 tab/重连；关闭视图不杀进程。

### M6 — 次级面与响应式（1–2 周）

- SCH-04/SCH-05 定时任务创建菜单与编辑器字段；SCH-02 Completed 状态。
- SIDE-01～SIDE-07 侧栏/landing 细节。
- RESP-01/03/04 断点、Dialog sheet、reduced motion；RESP-05 各端真机验证。

**验收**：定时任务可完成 Codex 的三类创建与复杂频率；窄屏 dialog/sheet 行为有测试。

## 6. 无法判定的 unknown 列表

以下条目**不是"没差异"**，而是现有解码或 Harness 代码不足以判定；在补齐证据前不要写进验收。

| # | unknown | 为什么无法判定 | 需要什么证据 |
|---:|---|---|---|
| U-01 | 36 个 static-only route key 的真实交互/保存/错误行为 | 本构建设置侧栏没有入口，运行态未渲染；静态 gate 条件未解析 | 换构建/flag 的运行态，或产品决定不纳入 |
| U-02 | 所有 `api-endpoint` 的真实请求/响应契约 | Phase 13 只读导航，没有任何 API 往返；`observedInNetwork=false` | 受控的只读网络观测或后端契约 |
| U-03 | message id 的完整文案 wiring | 严格配对率 14.3%（162/1132），71 个只有生产者，298 处消费者无法解析 | 更完整的跨 chunk 解析或运行态 DOM 文案核对 |
| U-04 | 逐路由 CSS 的真实归属 | 28 个 route stylesheet 只有 24 个应用；89 个 stylesheet 基本共享；运行态无法切分 | 运行态样式快照 + 路由切换对照 |
| U-05 | 调用图的三层以外间接依赖 | 只覆盖 172 个切片文件 + 一层依赖（618 chunk），不是传递闭包 | 扩展扫描集或按需补边 |
| U-06 | `gateways`（Model Providers）的运行态行为 | static-only；运行态未出现 | codexLocal 存储上下文下的运行态，或产品决定以静态契约为准 |
| U-07 | `codex-micro` 是否在别的构建/flag 下可达 | 本构建从未解析 `codex-micro-settings-*.js`；`pets` 明确是另一个面板 | 换构建/flag 的运行态 |
| U-08 | `family` / `environments` route key 的 catalog 身份 | `routes-to-panels.json` 记录 route key 无对应 catalog 条目 | catalog 源码补证或运行态 |
| U-09 | Harness 在 Android/iOS 的响应式/返回/IME/safe-area 行为 | 本矩阵只读源码；没有真机运行 | 真机交互测试 |
| U-10 | Harness 设置页的语义身份是否等价于 `data-settings-panel-slug` | Compose 没有 DOM 属性；testTag 未统一 | 统一 testTag + 交互测试断言 |
| U-11 | Codex 保存/删除/连接测试等写操作的精确状态机 | Phase 13 明确不填表、不保存、不删除、不运行 | 受控写操作观测（需用户授权） |
| U-12 | 旧审计（`26.930.61225`）与当前 full-restore 的逐条对应 | 两个版本不同；旧审计的 Codex 证据不能直接复用 | 以本矩阵的 full-restore 证据为准，逐条重核 |
| U-13 | Codex rail 的逐项按钮标签 | R13 只记录到 `应用导航`/`首页` landmark；S03/S04 不枚举 rail 标签；现有清单来自 Harness 契约注释 | full-restore 的 rail DOM/截图或静态切片补证 |
| U-14 | 探索菜单的完整目的地集合 | R13 `exploreMenu.status=blocked`，popover 只有 `项目` 一行 | 换构建/flag 的运行态，或静态切片补证 |

## 附录 A：55 个 settings route key 全表

> 来源：`14-callgraph/routes-to-panels.json`。`runtime slug` + `rail #` 为 R13 运行态；`静态 gate / 原因` 为静态解析结果（`surface` 与 gate 表达式）。`运行态已渲染` 表示 `chunkCompiledAtRuntime=true`。chunk 文件名在正文各面板行给出；本表不重复，避免与正文不一致。`billing` 与 `parental-controls` 的 `export` 在 callgraph 中为 null；semantic slice 的真实 export 分别为 `ConsumerBillingSettings`（S09）与 `ChatGptParentalControlsSettingsPage`（S12），见 3.1.3.1。

| # | route key | export | runtime slug | rail # | 静态 gate / 原因 |
|---:|---|---|---|---:|---|
| 1 | gateways | GatewaysSettings | — | — | surface=codexLocal；`return { visible: _e && !P, pending: P };` |
| 2 | rosalind | RosalindSettings | — | — | surface=codexOrWorkLocal；`return { visible: p && ae != null, pending: p && ae === void 0 };` |
| 3 | billing | — | — | — | surface=chatGpt；`return { visible: !1, pending: !1 };`｜一期不做（Phase 2） |
| 4 | general-settings | GeneralSettings | general-settings | 0 | 运行态已渲染 |
| 5 | notifications | NotificationsSettingsPage | — | — | surface=chatGpt；status allowed/loading/error gate |
| 6 | import | ImportSettings | import | 1 | 运行态已渲染 |
| 7 | profile | ProfileSettings | — | — | surface=codex；`return { visible: ct, pending: lt };` |
| 8 | security | ChatGptSecuritySettings | — | — | surface=chatGpt；`return { visible: !1, pending: !1 };` |
| 9 | account | ChatGptAccountSettings | — | — | surface=chatGpt；chatgpt/chatgptAuthTokens gate |
| 10 | age | ChatGptAgeSettings | — | — | surface=chatGpt；`return { visible: Se, pending: P ／／ U };` |
| 11 | agreements | AgreementsSettings | — | — | surface=chatGpt；status allowed/loading/error gate |
| 12 | family | GeneralSettings | — | — | surface=None；`return { visible: !1, pending: !1 };` |
| 13 | parental-controls | — | — | — | surface=chatGpt；status allowed/loading/error gate｜一期不做（Phase 2） |
| 14 | trusted-contact | ChatGptTrustedContactSettings | — | — | surface=chatGpt；status + consent gate |
| 15 | safety | ChatGptSafetySettings | — | — | surface=chatGpt；status + safety gate |
| 16 | time-management | ChatGptSafetySettings | — | — | 解析为 safety；surface=chatGpt |
| 17 | keyboard-shortcuts | KeyboardShortcutsSettings | keyboard-shortcuts | 9 | 运行态已渲染 |
| 18 | codex-micro | CodexMicroSettings | — | — | surface=codexLocal；`return { visible: de && ue === !0, pending: P ／／ (de && ue === void 0), };` |
| 19 | appshots | AppshotsSettings | appshots | 12 | 运行态已渲染 |
| 20 | appearance | AppearanceSettings | appearance | 2 | 运行态已渲染 |
| 21 | voice | VoiceSettings | voice | 4 | 运行态已渲染 |
| 22 | storage | DeviceStorageSettings | storage | 5 | 运行态已渲染 |
| 23 | pets | PetsSettingsRoute | pets | 8 | 运行态已渲染 |
| 24 | agent | ConfigurationPanel | agent | 6 | 运行态已渲染 |
| 25 | work-settings | WorkSettingsPanel | — | — | surface=chatGpt；capability + status gate |
| 26 | git-settings | GitSettings | git-settings | 16 | 运行态已渲染 |
| 27 | ads-controls | AdsControlsSettings | — | — | surface=chatGpt；`return { visible: b, pending: ye ／／ U };` |
| 28 | archived-chats | DataControlsSettings | archived-chats | 3 | 运行态已渲染 |
| 29 | data-controls | ChatGptDataControlsSettings | — | — | surface=chatGptOrCodex；chatgpt/chatgptAuthTokens gate |
| 30 | code-review | CodeReviewAppSettings | — | — | surface=None；cloud/app visibility gate |
| 31 | cloud-settings | CloudPreferencesSettings | — | — | surface=None；`return { ...O, visible: !P && O.visible, pending: P ／／ O.pending };` |
| 32 | codex-cloud | CodexCloudSettings | — | — | surface=None；`cloudAccess === true` gate |
| 33 | cloud-environments | CloudEnvironmentsSettingsPage | — | — | surface=None；`return F ? O : { visible: !1, pending: P };` |
| 34 | personalization | ChatGptPersonalizationSettings | personalization | 7 | 运行态已渲染 |
| 35 | chronicle | ComputerHistorySettingsPage | — | — | surface=codexOrWorkLocal；history available gate |
| 36 | usage | UsageSettingsPage | — | — | surface=codex；usage visibility gate |
| 37 | analytics | UsageSettingsPage | — | — | surface=codex；usage + analytics gate |
| 38 | usage-code-review | UsageSettingsPage | — | — | surface=codex；usage + review gate |
| 39 | consumer-view | UsageSettingsPage | — | — | surface=codex；consumer view gate |
| 40 | debug | DebugSettings | — | — | surface=None；buildFlavor/isGpuTearingDebugEnabled |
| 41 | computer-use | ComputerUseSettings | computer-use | 11 | 运行态已渲染 |
| 42 | browser-use | BrowserUseSettings | browser-use | 13 | 运行态已渲染 |
| 43 | extension | BrowserExtensionSettings | — | — | surface=chatGpt；`return { visible: !1, pending: Fe };` |
| 44 | local-environments | LocalEnvironmentsSettings | local-environments | 17 | 运行态已渲染 |
| 45 | worktrees | WorktreesSettingsPage | worktrees | 18 | 运行态已渲染 |
| 46 | environments | SettingsExternalSection | — | — | surface=None；`return { visible: !0, pending: !1 };` |
| 47 | mcp-settings | McpSettings | — | — | surface=codexOrWorkLocal；`return { visible: !0, pending: !1 };`（无独立运行态面板；MCP 内容在插件页） |
| 48 | hooks-settings | HooksSettings | hooks-settings | 14 | 运行态已渲染 |
| 49 | connections | RemoteConnectionsSettings | connections | 15 | 运行态已渲染 |
| 50 | plugins-settings | PluginsSettings | plugins-settings | 10 | 运行态已渲染 |
| 51 | file-type-handlers | FileTypeHandlersSettings | — | — | surface=codexOrWorkLocal；`visible ／／ (enabled && i === 'file-type-handlers')` |
| 52 | skills-settings | SkillsSettings | — | — | surface=codexOrWorkLocal；`return { visible: !1, pending: !1 };` |
| 53 | cloud-computer | ChatGptCloudBrowserSettings | — | — | surface=chatGpt；cloud browser status gate｜一期不做（Phase 2） |
| 54 | passwords | PasswordsSettings | — | — | surface=chatGpt；`return { visible: Pe, pending: P ／／ U };` |
| 55 | teams | TeamSettingsPage | — | — | surface=None；workspace/team gate｜一期不做（Phase 2） |

## 附录 B：运行态 19 面板与 rail 顺序

来源：R13 `settings-rail-inventory.json`。完整 19 项如下（rail 顺序）：

```text
#0  general-settings    常规
#1  import              导入
#2  appearance          外观
#3  archived-chats      已归档的聊天
#4  voice               语音
#5  storage             存储
#6  agent               配置
#7  personalization     个性化
#8  pets                Mini 与虚拟宠物
#9  keyboard-shortcuts  键盘快捷键
#10 plugins-settings    插件
#11 computer-use        电脑操控
#12 appshots            应用快照
#13 browser-use         浏览器
#14 hooks-settings      钩子
#15 connections         连接
#16 git-settings        Git
#17 local-environments  环境
#18 worktrees           Worktrees
```

- 侧栏几何：19 个按钮，`scrollHeight === clientHeight === 1272`，`overflow-y: visible`；因此"没出现在列表里"等于"本构建 UI 里没有"，不是滚动隐藏。
- `mcp-settings` 没有独立面板；MCP 服务器列表实际渲染在「插件」页（S14 d7/d9、R13）。
- `pets` ≠ `codex-micro`：`pets` 面板渲染 `settings.pets.*` 与虚拟宠物画廊；`codex-micro-settings-*.js` 从未被解析（R13 d2）。

## 附录 C：证据索引

| 证据 | 路径 |
|---|---|
| 解码总览 | `references/codex-26.1027.61615-24141/full-restore/README.md` |
| 设置外壳/模型厂商静态契约 | `06-semantic/README.md`、`06-semantic/ui-evidence.json`、`06-semantic/binding-map.json` |
| 剩余设置页静态契约 | `12-semantic/README.md`、`12-semantic/ui-evidence.json` |
| shell/nav | `03-semantic/README.md`、`03-semantic/ui-evidence.json` |
| sidebar/landing | `04-semantic/README.md`、`04-semantic/ui-evidence.json` |
| conversation/composer | `05-semantic/README.md`、`05-semantic/ui-evidence.json` |
| Git/worktree | `07-semantic/README.md`、`07-semantic/ui-evidence.json` |
| scheduled | `08-semantic/README.md`、`08-semantic/ui-evidence.json` |
| plugins/account | `09-semantic/README.md`、`09-semantic/ui-evidence.json` |
| responsive | `10-semantic/README.md`、`10-semantic/ui-evidence.json` |
| terminal/file panel | `11-semantic/README.md`、`11-semantic/ui-evidence.json` |
| 运行态 | `13-runtime/README.md`、`13-runtime/runtime-evidence.json`、`13-runtime/settings-rail-inventory.json`、`13-runtime/crosscheck.json`、`13-runtime/route-stylesheets.json` |
| 调用图 | `14-callgraph/README.md`、`14-callgraph/routes-to-panels.json`、`14-callgraph/gaps.md`、`14-callgraph/message-id-pairs.json`、`14-callgraph/chains/*.json` |

## 附录 D：与旧审计的关系

| 旧审计条目 | 本矩阵对应 | 处理 |
|---|---|---|
| S-01 接通文件搜索 | TERM-06 | 已确认并实现；本矩阵标记 `partial`（功能已确认，缺同视口视觉基线），不回退功能 |
| S-02 Explore 键盘与固定按钮 | NAV-02、RESP-04 | 已确认部分；本矩阵沿用，不扩大 |
| P-01～P-03 模型厂商交互 | SET-29、SET-30 | 用户已确认；本矩阵不要求按 Codex gateways 回退 |
| 旧审计其余 100+ 条 | 无直接对应 | 旧审计基于 `26.930.61225`，不是同版本；本矩阵以 full-restore 为准，旧条目只作 Harness 侧线索 |

> 说明：旧审计里的 Codex 行号与 `references/decode-codex` 路径不应在新矩阵中继续引用；需要 Codex 证据时一律用 full-restore 的 phase 产物。

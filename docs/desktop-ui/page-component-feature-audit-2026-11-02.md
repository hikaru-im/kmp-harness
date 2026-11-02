# 逐页面 · 逐组件 · 逐功能核对

状态：已完成（静态代码核对）  
核对日期：2026-11-02  
参考基线：`references/decode-codex`（HEAD `a3cd34ee`）的 `restored/`  
实现基线：当前分支 `feature/harness-25-scrollbar`（提交 `4ed78456`）

## 一、方法与口径

与 R0 的[区域级索引](restored-reference-index.md)不同，本文按**页面 → 组件 → 功能**三层逐项核对，每一行都给出两侧证据：

- Kotlin 证据：本仓库文件与符号（行号可能随提交漂移，以符号名为准）。
- restored 证据：相对 `references/decode-codex/restored/` 的路径。
- 状态：`✅ 已对齐` / `◐ 部分` / `○ 未实现` / `— 不做`（代码或文档明确排除）/ `? 未确认`。

核对只认代码落点：文档里写了但代码没有的，记为 `○`；代码有但没有定向测试的，在说明里注明“无定向测试”。

## 二、页面总览

| # | 页面 | Kotlin 入口 | restored 区域 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 对话页 | `client/page/ThreadPage.kt`、`client/app/`、`client/conversation/` | `conversations/`、`composer/`、`thread/`、`thread-summary/` | ◐ |
| 2 | 设置 | `client/page/Settings*.kt`、`client/settings/` | `settings/`、`config/`、`auth/`、`connectors/` | ◐ |
| 3 | 定时任务 | `client/scheduling/`（`ScheduledManagementPage`） | `automations/`、`automation/` | ◐ |
| 4 | 插件 | `client/plugins/` | `plugins/`、`skills/` | ◐ |
| 5 | 应用外壳 / 首页 / 新标签页 / 项目 / 账号 / Library | `client/shell/`、`client/page/` | `app-shell/`、`sidebar/`、`home/`、`projects/` | ◐ |
| 6 | Git / Worktrees | `client/git/`、`apps/jvm-app/.../git/` | `git/`、`review/`、`worktree/` | ◐ |

## 三、逐页面明细

### 1. 对话页（PageId.Thread / Home）

- Kotlin 入口：`client/page/ThreadPage.kt:41` `ThreadPage`（fixture/截图）；实时页 `client/app/LiveConversationSurface.kt:183` `LiveThreadPane`，外壳 `client/app/HarnessApp.kt:249` `HarnessApp`
- restored 对应：`conversations/`、`composer/`、`thread/`、`thread-summary/`、`markdown/`、`artifacts/`、`image-side-panel/`

#### 组件

| 组件 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 消息卡片（用户/助手、复制、编辑入口） | `client/app/ConversationComponents.kt` `MessageCard` | `conversations/conversation-copy.ts` | ✅ | 复制、编辑入口、末条消息动作具备 |
| Markdown 渲染 | `client/app/ConversationMarkdown.kt` | `markdown/streaming-fade-markdown.tsx`、`markdown/mermaid-diagram-renderer.tsx` | ◐ | GFM 标题/代码/表格/链接已支持；无 mermaid、流式淡入 |
| 推理块 | `ConversationComponents.kt` 推理区 | `conversations/activity-disclosure.tsx` | ✅ | 正在思考…/已完成思考，流式时保持展开 |
| 工具调用卡 | `ConversationComponents.kt` 工具调用区 | `conversations/dynamic-tool-call-row.tsx`、`collapsed-tool-activity-summary.tsx` | ◐ | 通用折叠 + JSON 参数；无按工具的专用行与分组摘要 |
| 会话内 diff 卡 | `client/app/FileChangeCard.kt`、`client/connection/Connection.kt` `FileChangeContent` | `conversations/file-change-patch-summary.tsx`、`file-change-patch-row.tsx` | ◐ | G3a 已完成四类卡片、聚合、截断与 Git 跳转；G3b 缺 producer（无结构化文件编辑工具），生产中不会出现卡片 |
| 计划卡 | `client/summary/ThreadSummaryPanel.kt` `PlanSection` | `.../plan-summary-card/card.tsx` | ◐ | 组件存在，但 `HarnessApp.kt` 调 `ThreadSummaryPanel` 时未传 `plan`，实际不显示 |
| 审批卡 | `client/app/ApprovalCard.kt`、`ConversationController` `PendingApproval`、`ApprovalRequest` | `utils/permission-request-model.ts` | ◐ | G2a 已完成请求模型、事件、controller 状态与卡片；G2b 门控与 gateway respond 待 Kaneo #17 |
| 错误/空态/加载/断连 | `client/app/ConversationErrorToasts.kt`、`LiveConversationSurface.kt` | `.../local-conversation-resume-errors.tsx` | ✅ | 错误走 toast；内联 `ConversationErrorCard` 未接入实时页 |
| 用时/时间分隔 | `ConversationComponents.kt` 回合分隔 | `conversations/turn-elapsed-time-divider.tsx`、`user-message-timestamp.tsx` | ◐ | 有时间分隔；无 working 实时计时与 stopped 态 |
| Composer 输入 | `client/app/LiveConversationComposer.kt` | `composer/composer.tsx`、`composer-editor-runtime.tsx` | ✅ | Enter 发送 / Shift+Enter 换行，编辑态横幅 |
| 附件 / @mention / slash | `ConversationComposerToolbar.kt`（附件按钮 `disabled()`） | `composer/attachment-pill.tsx`、`at-mention-list.ts`、`ui/slash-command-item/` | ○ | 附件显式禁用；无 @ 与 / |
| Composer 控件（模型/推理/权限/发送·停止） | `ConversationComposerToolbar.kt`、`ComposerPresentation.kt` | `composer/composer-footer-controls.tsx`、`composer-permissions-dropdown.tsx`、`composer-submit.ts` | ✅ | 模型 + 推理强度、权限 4 档、发送/停止/停止中 |
| 右侧面板 tab | `client/panels/ThreadPanelModels.kt`、`ThreadPanels.kt` | `app-shell/thread-side-panel-tabs.ts`、`right-panel-tab-bar.tsx` | ◐ | 仅后台终端/子智能体；缺 browser/diff/plan/sandbox/mcp/timeline |
| 摘要面板 | `client/summary/ThreadSummaryPanel.kt` | `.../thread-summary-panel-sections.tsx` | ◐ | 8 节；subagents/processes/scheduled 已接线，plan/outputs/sources/sideChats 未接线 |
| 子 agent 面板 | `ThreadPanels.kt` `SubagentsPanel` | `.../background-subagents-panel.tsx`、`subagent-activity-row.tsx` | ✅ | 进行中/已完成、子会话详情、刷新 |
| 终端面板 | `client/panels/PtyTerminalPanel.kt`、`PtyTerminalController.kt` | `app-shell/thread-background-processes/background-terminal-tab.tsx` | ✅ | PTY 网格、输入桥、复制/粘贴、resize |
| 图片 / artifact 侧面板 | 会话内无 | `image-side-panel/image-side-panel.tsx`、`artifacts/notebook-preview-panel.tsx` | ○ | 图片预览只在 Git 页 |

#### 功能

| 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 流式更新 | `client/conversation/LiveTranscript.kt` `LiveAssistantFold`、`SessionStreamDriver.kt` | `markdown/streaming-fade-markdown.tsx` | ✅ | 文本/推理/工具参数增量；无淡入动画 |
| 重试 | `ConversationErrorToasts.kt`、`ConversationController.kt` `submit` | `.../local-conversation-resume-errors.tsx` | ✅ | 失败重试在发送/停止路径 |
| 编辑重发 | `ConversationController.kt` `beginEdit` | `conversations/edit-user-message-form.tsx`、`fork-conversation-at-turn.ts` | ✅ | 仅 settled 用户消息；fork 前原文不变 |
| 复制 | `ConversationComponents.kt`、`ConversationMarkdown.kt` | `conversations/conversation-copy.ts` | ✅ | 消息与代码块复制 |
| Fork | `ConversationController.kt`（编辑路径内） | `.../local-conversation-fork-dialog.tsx` | ◐ | 无独立 fork 对话框与任意轮次入口 |
| 会话内查找 | 无 | `find/thread-find-bar.tsx`、`find/thread-find-store.ts` | ○ | 快捷键模型把查找组合键列为保留 |
| 滚动锚定 | `ConversationComponents.kt` 跟随逻辑 | `.../local-conversation-auto-follow-turn-list.tsx` | ✅ | 跟随底部，离开后停止跟随 |
| 快捷键 | `HarnessApp.kt` `shortcutConsumer`、composer Enter | `composer/command-shortcut-bindings.ts` | ◐ | 全局 5 项 + Enter；无 Ctrl+F 与命令键位 |
| 审批交互 | `ConversationController.respondApproval`、`ApprovalPanel` | `utils/permission-request-model.ts` | ◐ | 客户端可接收/提交审批并按拒绝处理断连；运行时门控与 respond 端点未接线（G2b，Kaneo #17） |
| 持久化 | `ConversationController.kt` `drafts`（内存） | `app-shell/thread-panel-tabs-store.ts` | ◐ | 草稿、面板 tab、摘要展开都不落盘 |

**本页结论**：流式增量、编辑重发、错误重试、滚动锚定、composer 控件、子 agent 与 PTY 面板已对齐。审批卡与会话内 diff 卡已具备客户端协议、状态与 UI（G2a/G3a），剩余最高优先级缺口是**运行时审批门控**（G2b，依赖 Kaneo #17 与 gateway respond）和**文件改动 producer**（G3b，依赖结构化文件编辑工具）；其次是附件/@mention/slash、独立 fork、会话内查找，以及右侧面板与摘要面板的数据接线。

### 2. 设置页（PageId.Settings）

- Kotlin 入口：`client/shell/NavigationState.kt` `PageId.Settings` / `SettingsSection`；`client/app/HarnessApp.kt` `SettingsScreen`；`client/shell/AppShell.kt` `SettingsNavigator`
- restored 对应：`settings/settings-page-current/`、`settings/settings-sections.ts`、`settings/general-settings-current.tsx`、`settings/keyboard-shortcuts-settings/`、`settings/git-settings/`、`settings/worktrees-settings-page/`
- 子页：Kotlin 8 个（General / Appearance / Models / Plugins / Connections / Shortcuts / Git / Worktrees）；restored 20+ 个（含 agent、personalization、usage、mcp、hooks、cloud、code-review、environments、browser/computer-use、data-controls 等）

#### 组件

| 组件 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 设置导航标题/搜索/分组 | `shell/AppShell.kt` `SettingsNavigator`、`shell/SettingsNavigatorModel.kt` `groups` | `settings-page-current/navigation-groups.ts`、`settings-navigation-sidebar.tsx` | ◐ | Kotlin 3 组 8 行；restored 4 组 27 个 slug（含 Archived）；Kotlin 注释声称有“已归档”组但代码没有 |
| 导航搜索 | `AppShell.kt` `SettingsNavigatorSearch`、`SettingsNavigatorModel.kt` `settingsNavigatorGroups` | `settings-page-current/settings-search.ts`、`settings-search-targets.ts` | ◐ | 只过滤导航标签，不能按设置项内容搜索 |
| Host 下拉（按宿主过滤设置） | 无 | `settings-page-current/settings-page.tsx` `buildHostSelector`、`settings-host-dropdown.tsx` | ○ | 无宿主选择 / All settings 过滤 |
| General 行模型 | `client/page/SettingsGeneralPage.kt` `SettingsGeneralModel`、`SettingsGeneralPage` | `settings/general-settings-current.tsx` | ◐ | 权限 2 行 + 偏好 13 行；仅 `confirm-close`、`plugins` 可用，其余禁用占位 |
| Appearance 模式与预览 | `SettingsAppearancePage.kt` `ModePreviews` | `general-settings-current.tsx`（Theme/Light/Dark/System） | ✅ | 浅色/深色/系统 + 三张预览卡 |
| Appearance 高级项 | `SettingsAppearancePage.kt` `VisualThemeFields`、`AdvancedThemeFields` | `general-settings-current.tsx`、`general-appearance-runtime/theme-editor.tsx` | ◐ | 颜色/字体/对比度/光标/动画/差异标记有；缺半透明侧栏、Dock 图标、代码主题预设；字体平滑只存储未接线 |
| 主题复制/导入 | `AppearanceSettings.kt` `AppearanceThemeCodec` | `general-settings-current.tsx`、`general-appearance-runtime/theme-share.ts` | ◐ | 自有 `harness-theme-v1:` 文本格式，非 Codex 分享格式（注释声明刻意独立） |
| Models Provider 列表/编辑器/凭据 | `SettingsModelsPage.kt` `SettingsModelsPage`、`ProviderEditor`、`CredentialEditor` | 无对应子页（restored 走 config.toml / Agent 设置） | — 不做 | Kotlin 额外实现，需自定义验收口径 |
| Connections 当前连接/发现/详情 | `SettingsConnectionsPage.kt`、`ConnectionsDetailsDialog` | `settings/remote-connections-settings-current.tsx`、`remote-connections-page.tsx` | ◐ | 有发现/选择/详情；缺 SSH 添加编辑、认证方式、设备管理、登录、重启 |
| Connections SSH/WSL | `SettingsConnectionsPage.kt` 禁用占位 | `remote-connections-settings-current.tsx` | ○ | 注释明确“尚未接入 SSH/WSL 适配器” |
| Shortcuts 表格/录制/搜索/重置 | `SettingsShortcutsPage.kt`、`settings/ShortcutsRepository.kt` | `keyboard-shortcuts-settings/` 全套 | ◐ | 冲突检测、按键搜索、每命令重置、重置确认都有；仅 6 个命令，不支持序列/裸修饰键 |
| Git 设置页 | `client/git/GitPages.kt` `SettingsGitPage` | `settings/git-settings/index.tsx` | ○ | restored 的 Git 偏好行在 Kotlin 全无；Kotlin 的 “Git 设置” 实际是仓库操作页 |
| Worktrees 设置 | `GitPages.kt` `WorktreesPage`、`WorktreeRootSection`、`WorktreeEnvironmentSection` | `settings/worktrees-settings-page/`、`git-settings/worktree-auto-cleanup-settings.tsx` | ◐ | 清单/删除/新建聊天/根目录/自动清理/保留数量/上游刷新有；restored 还按仓库分组并过滤 backing roots |
| 关于/版本/开源许可证 | `SettingsGeneralPage.kt` `licenses` 行禁用 | `settings/open-source-licenses-page.tsx` | ○ | 只有禁用行，没有许可证子页 |
| 错误/保存状态 | 各设置页 loading/saving/error/retry | `settings/setting-storage.tsx` `writeSettingValue` | ◐ | 有完整状态；写入非乐观，保存成功后才生效 |

#### 功能

| 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 设置读取/默认值/持久化 | 各 repository `load()` + 默认值；`apps/jvm-app/src/main.kt` 注入 store | `settings/setting-storage.tsx`、`config/config-queries/mutations.ts` | ◐ | 原子 JSON 写可靠；但存客户端本地、不随 Host 切换，restored 是宿主作用域 |
| 保存失败回滚/重试 | `GeneralSettingsRepository.kt`、`AppearanceRepository.kt`、`ShortcutsRepository.kt` | `setting-storage.tsx` | ✅ | 失败不替换生效值，页面可重试 |
| 重置 | `AppearanceSettings.kt` `resetAdvanced`、`ShortcutsRepository.kt` `resetAll` / `reset` | `reset-all-shortcuts-dialog.tsx`、`keyboard-shortcuts-utils.ts` | ◐ | 仅外观高级项与快捷键；General/Models/Worktree 无恢复默认 |
| 快捷键冲突检测 | `settings/Shortcuts.kt` `chordError`、`validationError` | `keyboard-shortcuts-utils.ts` `findConflictingCommandTitle` | ✅ | 重复、保留组合、跨命令冲突都在保存前拦截 |
| 快捷键录制/多绑定 | `ShortcutsRepository.kt` `beginRecording` / `record` | `shortcut-capture-input.tsx` | ◐ | 多绑定与追加/替换/移除有；单组合键、无序列、无裸修饰键 |
| 模型切换 | composer 内模型选择器；设置页只管 Provider | `agent-settings/model-features-section-impl.tsx`、config.toml | ◐ | 切换入口在 composer，restored 设置侧是 Agent 模型特性页 |
| 连接登录/登出/健康检查 | `ConnectionsState.kt` 有 `RequiresLogin` 阶段，但无操作入口 | `remote-connections-settings-current.tsx` | ○ | 只显示“需要登录”，不能登录/登出/撤销/重启 |
| 主题即时生效 | `HarnessApp.kt` `HarnessTheme(appearanceState...)`、`main.kt` 窗口主题 | `theme-editor.tsx` | ✅ | Compose 与原生窗口同时重绘 |
| 语言 | `SettingsGeneralPage.kt` 语言下拉固定“中文”且禁用 | `general-settings-current.tsx` 语言选择 | ○ | 无 i18n/语言持久化 |
| 主题预设/代码主题 | `SettingsAppearancePage.kt` `UnavailableRow("代码主题预设")` | `themes/`（13 个预设） | ○ | 只有自选颜色/字体，无预设目录 |
| Git 偏好设置 | 无 | `settings/git-settings/index.tsx` | ○ | 分支前缀、PR 图标、force push、draft PR、merge method、提交/PR 指令全缺 |
| Worktree 自动清理 | `GitPages.kt` `WorktreeEnvironmentSection` | `worktree-auto-cleanup-settings.tsx` | ✅ | 开关+关闭确认、保留数量、上游刷新对齐 |
| 设置内容搜索 | `SettingsNavigatorModel.kt` 仅标签过滤 | `settings-search.ts`、`settings-search-targets.ts` | ○ | 不能按“语言/权限/主题”等内容搜索 |

**本页结论**：外观、快捷键冲突与录制、Worktree 自动清理、保存失败回滚、主题即时生效已对齐。真实缺口按影响排序：① 缺 15+ 个 restored 设置子页（Agent/权限沙箱、Usage、MCP、Hooks、Cloud、Code Review、Environments、Browser/Computer Use 等）；② General 页 13 行禁用占位；③ Connections 无 SSH/WSL 与登录链路；④ Git 偏好整组缺失；⑤ 主题预设、设置内容搜索、乐观写入缺失。

### 3. 定时任务页（PageId.ScheduledTasks）

- Kotlin 入口：`client/app/HarnessApp.kt` `PageId.ScheduledTasks -> ScheduledManagementPage`；页面 `client/scheduling/ScheduledManagementPage.kt` `ScheduledManagementPage`；导航器 `ScheduledManagementNavigator.kt`
- 遗留代码：`client/page/ShellPages.kt` `ScheduledTasksPage` 没有任何调用点；`client/scheduled/` 只被 `AppShell` 的旧 fallback 导航器使用
- restored 对应：`automations/automations-page-current.tsx`、`automations/automation-side-panel/`、`automations/automation-schedule-picker.tsx`、`automation/automation-schedule/`

#### 组件

| 组件 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 页面骨架/状态条 | `ScheduledManagementPage.kt` `ScheduledPageStatus` | `automations-page-current.tsx` 状态区 | ✅ | 未绑定/断连/加载/错误/durability/mutation 状态齐全 |
| 任务导航列表 | `ScheduledManagementNavigator.kt` 列表 | `automations-page-current.tsx` Current/Paused 分区 | ◐ | 单列表 + Automatic 排序；无 Current/Paused 分区标题与行内操作 |
| 任务行摘要 | `ScheduledTaskSummary.kt` `scheduledTaskSummary`、`scheduledSummaryCadence` | `automations-page-current.tsx` 行摘要 | ✅ | 频率/下次运行/已暂停/正在运行；无法格式化时降级为 cadence |
| 落地页/模板 | `ScheduledPageLayout.kt` `ScheduledPageLanding`、`templates` | `automations-page-current.tsx` Templates 页与 empty 建议 | ◐ | 6 个硬编码模板卡片；无模板搜索、模板 tab、聊天创建 |
| 编辑器 | `ScheduledManagementPage.kt` `ScheduledPageEditor` | `automation-side-panel/body.tsx`、`footer.tsx` | ◐ | 显式保存且固定 Paused；restored 600ms 防抖自动保存、默认 ACTIVE、保存重试 |
| 详情面板 | `ScheduledManagementPage.kt` `ScheduledPageDetail` | `automations-page-current.tsx` 详情 | ◐ | 有状态/上次运行/更新时间/prompt/目标/schedule/model/版本；缺 Next run、Runs in、Execution environment |
| 运行历史 | `ScheduledManagementPage.kt` `ScheduledPageLedger`、`ScheduledPageRunRows` | `automations-page-current.tsx` Previous runs、`current-automation-history.ts` | ◐ | Kotlin 是 run 账本；restored 是线程历史，带已读/归档/全部归档 |
| 删除确认 | `ScheduledManagementPage.kt` `HarnessConfirmDialog` | `automations-page-current.tsx` 删除确认 | ✅ | 确认、提交中/失败态都有；不取消已开始运行、不清会话历史 |
| 执行模型提示 | `ScheduledModels.kt` `ScheduledLocalExecutionModel` | 无对应（restored 由 Host 调度） | ✅ | 明示“仅应用运行期间执行，无系统守护进程”，属本机实现差异 |
| 选择器/长值复制 | `ScheduledPageLayout.kt` `ScheduledPageValue`、`ScheduledPagePicker` | `automation-schedule-picker.tsx` | ✅ | 目标/模型/频率/星期选择与长值复制 |

#### 功能

| 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 列表/搜索/筛选/排序 | `ScheduledManagementNavigator.kt` 筛选与排序 | `automations-page-current.tsx` | ◐ | 搜索、全部/已启用/已暂停、4 种排序都有；缺分区与行内操作 |
| 创建任务 | `ScheduledRepository.kt` `beginCreate` / `save` | `automation-side-panel/body.tsx`、`footer.tsx` | ◐ | 只能创建 Paused；无 Create via chat / 建议模式 |
| 编辑任务 | `ScheduledRepository.kt` `beginEdit` / `save` | `use-automation-side-panel-controller.ts` | ◐ | 显式保存 + revision CAS；restored 自动保存 + 失败重试 |
| 删除任务 | `ScheduledRepository.kt` `delete` | `automations-page-current.tsx` 删除 | ✅ | 持久删除任务/游标并记录 deletedTaskIds |
| 启用/暂停 | `ScheduledRepository.kt` `setPaused`；详情入口 | `automations-page-current.tsx` 行内 + 详情 | ◐ | 只有详情页入口；restored 列表 hover 也可操作 |
| 立即运行 | `ScheduledRepository.kt` `runNow` | `automations-page-current.tsx` | ✅ | pending/blocked/失败都有；Unknown 运行会阻断 |
| 目标：新会话 vs 既有会话 | `ScheduledModels.kt` `ScheduledTarget`；`HarnessApp.kt` 目标装配 | `automation-execution-environment-dropdown.tsx`、`automation-heartbeat-thread-dropdown.tsx` | ◐ | Cron→Projectless/Cwd 新会话，Heartbeat→任意既有会话；restored 还有 worktree，heartbeat 仅 pinned chat |
| Cron 输入与校验 | `ScheduledModels.kt`、`ScheduledValidator.kt`、`ScheduleEngine.kt` | `automation-schedule-picker.tsx`、`automation-schedule/types.ts` | ◐ | 结构化 hourly/daily/weekdays/weekly + 范围/时区校验；无自定义 RRULE、heartbeat 分钟间隔 |
| 时区 | `ScheduledModels.kt` `zoneId`、`ScheduleEngine.kt`、`DesktopScheduleCalendar.kt` | restored 按本地时间 | ✅ | 显式 IANA 时区 + DST resolve/校验，属超集 |
| 模型/推理强度 | `ScheduledManagementPage.kt`、`ScheduledModels.kt` `ScheduledModelRef` | `automation-model-reasoning-dropdown.tsx` | ◐ | 只有 model，无 reasoning effort |
| 运行中/阻塞状态 | `ScheduledTaskSummary.kt`、`ScheduledExecutionModels.kt` `blocksTask` | `automations-page-current.tsx` | ✅ | 有 in-progress；额外区分 Unknown 运行并阻止重新投递 |
| 加载/错误/空态 | `ScheduledManagementPage.kt`、`ScheduledPageEmptyState`、导航器空态 | `automations-page-current.tsx` | ✅ | 状态齐全，另有 durability warning |
| 本机持久化/执行/恢复 | `DesktopScheduledStore.kt`、`ScheduledExecutor.kt`、`ScheduledExecutorOwner.kt` | restored 由 Host 服务承担 | ✅ | CAS 写、leader lock、执行器恢复、Unknown 阻断 |
| 模板/聊天创建 | `ScheduledPageLayout.kt` `templates` | `automations-page-current.tsx` 模板与聊天创建 | ◐ | 模板只预填编辑器；无模板搜索/个性化/聊天创建 |

**本页结论**：本机持久化与执行链路（CAS、leader lock、恢复、Unknown 阻断）、立即运行、删除确认、时区与 DST 校验已对齐。缺口集中在编辑器与列表形态：固定 Paused、显式保存、无分区/行内操作、无 Next run 行；调度输入无 RRULE/heartbeat 分钟间隔/reasoning effort；无“与 Codex 一起创建”。

### 4. 插件页（PageId.Plugins / PluginDetail）

- Kotlin 入口：`client/app/HarnessApp.kt` `PageId.Plugins -> InstalledPluginsPage`、`PageId.PluginDetail -> InstalledPluginDetailPage`；页面 `client/plugins/InstalledPluginsPage.kt`
- restored 对应：`plugins/plugins-page-current.tsx`、`plugins/plugin-detail-page-current.tsx`、`plugins/plugin-detail-page-utils/`、`plugins/plugins-page-selectors/`、`skills/`、`connectors/`

#### 组件

| 组件 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 页面骨架/导航 | `InstalledPluginsPage.kt`、`PluginsManagementNavigator.kt` | `plugins-page-current.tsx` 五 tab + 搜索 | ◐ | 单列表 + 全部/已启用筛选；restored 有 Plugins/Skills/Apps/MCPs/Marketplace tab |
| 已安装列表行 | `InstalledPluginsPage.kt` `InstalledPluginRow` | `plugins-page-current.tsx` 分组与卡片操作 | ◐ | 行只有名称/状态/来源/详情；restored 卡片带启用开关、菜单、安装进度 |
| 详情页头/操作 | `InstalledPluginsPage.kt`、`PluginManagementControls` | `plugin-detail-page-current.tsx` | ◐ | 启用/停用、本地更新、移除登记；restored 还有安装/Try in chat/Manage/Uninstall/分享 |
| 详情信息 | `InstalledPluginsPage.kt` 详情信息区 | `plugin-detail-page-current.tsx` | ◐ | 说明/版本/来源/状态/主机；restored 还有 developer/website/privacy/terms/category/capabilities |
| Skills 子项 | `InstalledPluginsPage.kt` Skills 列表 | `plugin-detail-page-current.tsx`、`plugins-page-current.tsx` Skills tab | ◐ | 名称 + 描述行；restored 有完整浏览/安装/详情 |
| Apps 子项 | `InstalledPluginsPage.kt` Apps 列表 | `plugin-detail-page-current.tsx` | ◐ | 仅名称列表；restored 有 app 卡片/分类/打开 |
| Hooks/Scripts/MCP | `InstalledPluginsPage.kt` 能力区 | `plugin-detail-page-current.tsx` MCP/hooks/trust | ◐ | 只有声明名 + “配置和执行服务尚未接入” |
| 本地注册对话框 | `InstalledPluginsPage.kt` 注册对话框 | restored 无本地目录注册 | ✅ | 绝对路径校验、Skills 快照、默认停用，属额外能力 |
| 移除登记对话框 | `InstalledPluginsPage.kt` 移除对话框 | `plugin-detail-page-current.tsx` uninstall | ◐ | 只移除本地登记；restored 卸载安装包 |
| 读取/错误/不支持态 | `InstalledPluginsPage.kt` `PluginReadFeedback` | `plugins-page-current.tsx`、`plugin-detail-page-current.tsx` | ✅ | NotLoaded/Loading/Error/Unavailable/AuthRequired/UnsupportedHost 齐全 |
| 管理反馈 | `InstalledPluginsPage.kt` `PluginManagementFeedback` | restored toast/错误提示 | ✅ | 保存 receipt、刷新失败、mutation error 都有 |

#### 功能

| 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 已安装列表/搜索/筛选 | `InstalledPluginsPage.kt` | `plugins-page-current.tsx` | ◐ | 平铺列表；restored 分区 + 五 tab + marketplace |
| 详情 | `InstalledPluginsPage.kt` | `plugin-detail-page-current.tsx` | ◐ | 基本信息/技能/能力只读；restored 有完整详情与管理动作 |
| 启用/停用 | `InstalledPluginsController.kt` `setEnabled`、`DesktopUserPlugins.kt` | `plugins-page-current.tsx`、`plugin-detail-page-current.tsx` | ✅ | 持久化并下次 Agent 请求生效；仅详情入口 |
| 更新 | `InstalledPluginsController.kt`、`DesktopUserPlugins.kt` | `plugin-detail-page-current.tsx` 共享版本更新 | ◐ | 只从本地源目录重新导入；无版本检查/市场更新 |
| 卸载 | `InstalledPluginsPage.kt`、`DesktopUserPlugins.kt` | `plugin-detail-page-current.tsx` uninstall | ◐ | 只移除登记 |
| 来源/版本 | `InstalledPluginsPage.kt` | `plugin-detail-page-current.tsx` | ✅ | manifest 版本 + 本地源路径；缺失时明确“服务未提供” |
| 本地注册 | `InstalledPluginsPage.kt`、`InstalledPluginsController.kt` | 无直接对应 | ✅ | 额外能力：绝对路径、Skills 快照、默认停用、CAS |
| Skills 注入 Agent | `DesktopUserPlugins.kt` `renderInstructions` | restored skills runtime | ✅ | SKILL.md 文本进入 Agent instructions |
| Hooks/Scripts/MCP/Apps 执行 | 仅名称（`InstalledPluginsPage.kt` 能力区） | `plugin-detail-page-current.tsx` | ○ | Kotlin 明确不读取/不执行；restored 有 hooks 信任、MCP 启停、Apps 运行 |
| Marketplace 安装 | 无 | `plugins-page-current.tsx` | ○ | 无市场源、安装、安装进度 |
| 分享/协作 | 无 | `plugin-detail-page-current.tsx` 分享 | ○ | 无分享链接、成员权限、共享版本 |
| Skills 独立页 | 无 | `skills/`、Skills tab | ○ | 只有插件内 Skills 列表 |
| Connectors/App 连接 | 无 | `connectors/apps-queries/*`、`app-connect-oauth/*` | ○ | 无连接器目录/OAuth/连接流 |
| Admin 禁用/缺失态 | `PluginUnavailableReason`、`UnsupportedHost` | `plugins.card.disabledByAdmin`、`plugins.detail.missing` | ◐ | 有服务不可用/需认证/主机不支持；无管理员禁用/市场缺失态 |

**本页结论**：已安装插件的读取、启用停用、本地注册、从源目录更新、移除登记都走真实桌面服务，Skills 文本会注入 Agent。缺口集中在插件生态：无 Marketplace、安装/卸载、分享、Skills/Apps/MCPs 独立 tab、Connectors；Hooks/Scripts/MCP/Apps 只展示名字，不执行。

### 5. 应用外壳（AppShell / 全局）

- Kotlin 入口：`client/shell/AppShell.kt` `AppShell`；路由 `client/app/HarnessApp.kt` `renderPage`
- restored 对应：`app-shell/`、`sidebar/`、`command-menu/`、`inbox/`

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 外壳布局 | `AppShell.kt` `AppShell`、`MainSurface`、`HarnessMainRegion.kt` | `app-shell-layout.tsx`、`app-shell-header.tsx` | ◐ | 固定 rail + 页面自有 navigator + 主区卡片；restored 有 header 三段槽位、docked/floating 左面板、底部面板 |
| 固定 Rail | `RailDestinations.kt` `RailDestination` / `harnessRailItems` | `sidebar/sidebar-footer.tsx`、`sidebar-navigation-controls.tsx` | ◐ | 主页/定时任务/资料库/插件/探索 + pinned + 账号；无 Sites。`RailDestination.Library` 注释与代码不一致（注释说打开项目工作区，代码路由 `PageId.Library` 占位页） |
| 页面 Navigator 矩阵 | `PageShellSpec.kt` `pageShellSpec`、`AppShell.kt` `NavigatorPane` | `app-shell/left-panel.tsx` | ✅ | 有 `PageShellSpecTest`、`PageNavigatorMatrixAuditTest` |
| Navigator 宽度/折叠 | `NavigatorLayout.kt`、`AppShellState.kt` `SidebarState` | `left-panel.tsx`、`app-shell-layout.tsx` | ◐ | 240–520dp clamp、275dp preferred、<640dp float、拖拽 resize 都有测试；但 `HarnessApp.kt` 里 `navigatorMode` / `navigatorWidth` / `pinnedRailDestinations` 都是 `remember`，重启不保留 |
| 命令菜单 | `AppShell.kt` `CommandMenu`、`WorkspaceNavigator` 搜索框 | `command-menu/chat-search-command.tsx`、`recent-thread-switcher.tsx` | ◐ | 搜索/过滤/Esc/打开会话有测试；缺 RecentThreadSwitcher、NewThreadInProject、Theme、GitReviewMode 等命令注册 |
| 活动收件箱 | `AppShell.kt` `NavigatorActivityView` | `inbox/`、`sidebar-activity-boundary-runtime.ts` | ◐ | 未读/最近、全部已读、归档全部有测试；restored 是独立路由并带 automation 详情 |
| 最近/项目列表行 | `AppShell.kt` `RecentTaskRow`、`ProjectRow`、`ProjectChildRow`、`ShowAllRow` | `sidebar-thread-list.tsx`、`sidebar-thread-list-full-model.ts` | ◐ | 项目置顶排序、子行、查看全部有；recents 只有标题过滤，无置顶分区、相对时间、状态/PR chip、滚动位置持久化、滚动渐隐 |
| 会话上下文菜单 | `ConversationContextMenu.kt`、`RecentTaskRow` | `sidebar-thread-list.tsx`、`app-shell-tab-controller/controller.ts` | ◐ | 重命名/固定/未读/分区/归档/删除/复制 ID 已接；fork、open、copy-link 禁用；无拖拽排序与 worktree 移动确认 |
| 账号菜单 | `AppShellState.kt` `accountMenuItems`、`AppShell.kt` `AccountMenu` | `app-shell/profile-dropdown.tsx` | ◐ | 设置/资料/登录/登出；缺 usage、gift credits、登出确认、连接状态 |
| Tab 条 | `HarnessTabStrip.kt`、`desktop/window/HarnessTabStrip.kt`、`HarnessSplitMainRegion.kt` | `app-shell-tab-strip.tsx`、`right-panel-tab-bar.tsx` | ◐ | 新建/关闭/前进后退/Full-Split/换边/拖拽分隔/隐藏显示有测试；无拖拽重排、右键菜单、preview/label tab、持久化；tab 类型只有工具页 |
| 设置导航 | `SettingsNavigatorModel.kt` `SettingsNavItem` / `SettingsNavGroup` | `navigation-groups.ts`、`nav-row.tsx` | ◐ | 实际三组，注释称四组含「已归档」；restored 四组且条目多得多 |
| Quick Chat / Hotkey window / Sites | 无落点 | `quick-chat/`、`hotkey-window/`、`sidebar/sites-route-nav-link.tsx` | ○ | 均无 Kotlin 对应 |
| 拖拽排序 / 滚动位置持久化 / hover card | 未找到 | `sidebar-thread-list.tsx`、`scroll-fade-mask.tsx`、`project-hover-card.tsx` | ○ | 三项都缺 |

#### 首页（PageId.Home）

- Kotlin 入口：`client/app/LiveConversationSurface.kt` `LiveHomeSurface`；navigator 最近行 `AppShell.kt` `RecentTaskRow`
- restored 对应：`home/home-page/`、`sidebar/sidebar-thread-list.tsx`

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| Home hero / 项目标题 | `LiveConversationSurface.kt` `HomeHero`、`HeroHeading` | `home-page-body.tsx`、`hero-heading.tsx` | ✅ | 宿主名/项目名两种标题与点线规则 |
| 公告 / 模板 / 对话起始 / ambient suggestions | 未找到 | `home-page-body.tsx`、`home-conversation-starters.tsx`、`home-ambient-suggestions-content/` | ○ | 只有 hero + composer 槽 |
| 最近任务列表 | `RecentTaskRow` | `sidebar-thread-list.tsx` | ✅ | 基础列表与打开可用 |
| 置顶最近分区 / 相对时间 / 状态 chip | 未找到 | `sidebar-thread-list-full-model.ts`、`sidebar-thread-list.tsx` | ○ | 行内只有标题 |
| `RecentTasksPage` | `client/page/ShellPages.kt` `RecentTasksPage` | `sidebar-thread-list.tsx` | ○ | 有完整定义但全仓无调用点（死代码），`PageId.Home` 实际渲染 `LiveHomeSurface` |

#### 新标签页（PageId.NewTab）

- Kotlin 入口：`client/page/NewTabPage.kt` `NewTabPage`
- restored 对应：`app-shell/thread-app-shell-chrome/new-tab-*.tsx`、`browser/browser-sidebar*.tsx`

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 工具网格与 chord | `NewTabPage.kt` `ToolSection`、`ToolCell`、`ChordPill` | `new-tab-empty-state.tsx`、`new-tab-model.tsx` | ✅ | 布局、图标、chord 有 `ThemeLayersInteractionTest` |
| 打开「变更」 | `HarnessApp.kt` `NewTabPage onOpenTool` | `new-tab-model.tsx` review action | ✅ | 作为侧边面板 tab 打开 Git 页 |
| 终端 / 文件 / 侧边聊天 | `NewTabTool`、`ToolCell` | `new-tab-model.tsx` | ○ | 卡片存在但 `alpha 0.4` 且不可用 |
| 地址栏 | `NewTabPage.kt` `AddressCapsule` | `browser-sidebar-view.tsx` | ○ | 只有不可编辑占位，无导航 |
| 工具栏重新加载/扩展/选项 | `NewTabPage.kt` `BrowserToolbar` | `browser-sidebar.tsx`、`browser-options-menu.tsx` | ○ | 返回/前进按参考禁用，其余按钮只画 hover 不执行 |
| 推荐 / artifacts / MCP | 无 | `new-tab-empty-state.tsx` | — 不做 | 注释明确「推荐 row 故意不画」 |

#### 项目页（PageId.Projects / PageId.Project）

- Kotlin 入口：`client/page/LocalProjectsPage.kt` `LocalProjectsPage` / `LocalProjectDetailPage`；路由 `HarnessApp.kt`
- 死代码：`client/page/ShellPages.kt` 的 `ProjectsPage`、`ProjectPage` 全仓无调用点
- restored 对应：`projects/projects-index-page.tsx`、`row-state-helpers.ts`、`sidebar/project-hover-card.tsx`

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 本机项目列表 | `LocalProjectsPage` | `projects-index-page.tsx` | ◐ | 列表/搜索/登记目录/刷新/状态/空态有；缺排序、pin/unpin、edit-remove-archive、add source、折叠、无限滚动 |
| 项目详情 | `LocalProjectDetailPage` | `projects-index-page.tsx` | ◐ | 名称/根路径复制、开始会话、更换目录、关联会话有；缺行内项目动作 |
| 选择器 / 编辑对话框 | `LocalProjectPicker`、`LocalProjectEditorDialog` | `projects-index-page.tsx` | ✅ | 有 `LocalProjectsInteractionTest`、`ProjectsRecentInteractionTest` |
| 页面框架 / 空态原语 | `ProjectsRecentPageComponents.kt`、`WorkspacePageComponents.kt` | `app-shell-layout.tsx` | ✅ | max 1120dp、640dp 响应式 header、空态原语 |
| 搜索 / 空态 / 状态阶段 | `LocalProjectsPage`、`LocalProjectsState` | `row-state-helpers.ts` | ✅ | Unbound/Loading/Ready/Empty/Error/Unavailable/Unsupported/AccessDenied |
| 排序 / pin / collapse / 无限滚动 | 未找到 | `projects-index-page.tsx` | ○ | 四项都缺 |
| 项目 hover card | 未找到 | `sidebar/project-hover-card.tsx` | ○ | 无 |

#### 账号页（PageId.Account）

- Kotlin 入口：`client/account/AccountWorkspacePage.kt` `AccountWorkspacePage`
- restored 对应：`routes/login-route.tsx`、`app-shell/profile-dropdown.tsx`、`settings/remote-connections-settings-current.tsx`

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 账号页 | `AccountWorkspacePage` | `login-route.tsx` | ◐ | Kotlin 是自建手机号/密码 + 租户/会话管理；restored 是 ChatGPT/Google/Microsoft/API key 登录 |
| 连接行 / 状态 | `AccountWorkspacePage` 连接行 | `remote-connections-settings-current.tsx` | ◐ | 只有 host/status；无 remote control devices、keep-alive |
| 登出 | `AccountWorkspacePage` | `profile-dropdown.tsx` | ✅ | 基本登出可用 |
| 第三方登录 / 设备授权 / onboarding | 未找到 | `login-route.tsx` | ○ | 均缺 |

#### Library 占位页（PageId.Library）

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 文件库本体 | 无 | `appgen/library-page.tsx`、`library-page-current.tsx` | — 不做 | 注释明确「Codex's Library is a file product this client does not implement」 |
| 占位语义 | `HarnessApp.kt` `LibraryPlaceholderPage`、`library-unavailable` tag | 无 | ✅ | 占位文案明确且有 `ProjectsRecentInteractionTest` 断言 |

**外壳/首页结论**：Navigator 矩阵、resize/float、Tab 条核心操作、项目列表/详情/选择器、活动收件箱基础动作已对齐。缺口按影响：① 布局状态全部不持久化（navigator 宽度/模式、pinned rail、tab 布局、滚动位置）；② 侧栏交互缺拖拽排序、置顶分区、hover card、滚动渐隐；③ 新标签页地址栏与工具栏不可用、3/4 工具禁用；④ `RecentTasksPage`、`ProjectsPage`、`ProjectPage` 是死代码；⑤ Quick Chat、Hotkey window、Sites 未实现。

### 6. Git 页面（PageId.Settings.Git）

- Kotlin 入口：`client/git/GitPages.kt` `SettingsGitPage`；控制器 `client/git/GitController.kt`
- Host 实现：`apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt`
- restored 对应：`review/`（146 个文件）、`git/`（17 个）、`worktree/`（12 个）

#### 组件

| 组件 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 仓库/分支控制 | `GitPages.kt` `RepositoryControls`、`BranchCheckoutForm` | `git/git-branch-switcher.tsx`、`review/review-source-controls.tsx`、`review-header.tsx` | ◐ | 选择仓库/worktree、刷新、切换分支有；无分支搜索列表、无 source control 面板 |
| 工作区状态 | `GitPages.kt` 状态卡（暂存/未暂存/未跟踪、变更列表） | `review/changed-files-panel.tsx`、`review/review-file-tree.tsx` | ◐ | 有计数与变更行；无文件树、无按目录分组 |
| Diff 工具栏 | `GitPageComponents.kt` `GitDiffView`、`GitDiffLayoutSelector`、`GitDiffRichPreviewToggle`、`GitDiffOptionsMenu` | `review/review-options-toolbar.tsx`、`review-options-menu-compact.tsx` | ✅ | 布局、富预览、换行、展开全部、隐藏空白、加载完整文件、词级 diff、隐藏 imports、复制 apply、刷新 |
| 文件卡头 | `GitPageComponents.kt` `GitDiffFileCard` | `review/review-diff-card.tsx` | ✅ | 路径、状态、+/−、展开/收起；R4 起吸顶并显示命中数；无面包屑、文件间跳转、打开文件 tab |
| Diff 正文 | `GitDiffLayout.kt`、`GitDiffLines.kt`、`GitWordDiff.kt`、`GitDiffReading.kt` | `review/file-diff.tsx`、`file-change-gutter.ts`、`diff-column-line-number.ts` | ◐ | unified/split/Auto、词级 diff、gutter、R4 上下文展开都有；无 blame gutter、选区摘要 |
| 富预览 | `GitPageComponents.kt` `GitBinaryPreview`、`GitPreviewDecoding.kt` | `review/file-source-rich-preview.tsx`、`file-source-side-panel.tsx` | ✅ | image/SVG（JVM/iOS）、PDF（仅 JVM）；未知与删除回落 diff |
| 大 diff 横幅 | `GitPageComponents.kt` `GitDiffLargeBannerCopy`、`GitDiffReading.kt` `gitDiffOpensByDefault` | `review/large-diff-banner.tsx`、`review/editor-diff-page.tsx` | ✅ | R4：restored 的 25 文件 / 2000 变更行阈值（含边界）；超限显示横幅并每次只展开一个文件 |
| Diff 搜索 | `GitPageComponents.kt` `GitDiffSearchBar`、`GitDiffReading.kt` `findGitDiffMatches` | `review/diff-search-adapter.ts`、`diff-search-controller-runtime.ts`、`diff-find-highlight.ts` | ✅ | R4：不区分大小写字面搜索、命中计数、上下跳转、自动展开命中文件并滚动到行、正文高亮；500 条上限 |
| 行内评论 / apply / revert | 无 | `review/review-comment-utils.ts`、`patch-action-runner.ts`、`apply-review-patch.ts`、`revert-confirm-dialog.tsx` | ○ | 只能复制 apply 命令，不能在 UI 内执行 |
| 提交表单 | `GitPages.kt` `CommitForm`、`CommitOutcome` | `review/commit-command-menu.tsx`、`git-action-*` | ◐ | 仅 index-only 提交；无 amend、push、PR、hooks、签名（`GitModels.kt` 注释明确） |
| Pull request 页 | 无 | `review/pull-requests-page-current.tsx`、`derive-pull-request-status.ts` | ○ | 无 PR 列表/详情/创建 |

#### 功能

| 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| 分支切换 | `GitController.kt` `selectBranch` / `switchBranch` | `git-branch-switcher.tsx` | ✅ | 有确认与失败反馈 |
| 新建分支 | `GitController.kt` `setNewBranchName` / `setNewBranchStart` / `createBranch` | `git/git-branch-switcher-parts` | ✅ | 起始 ref 可选 |
| 提交 | `GitController.kt` `commit`；`normalizeGitCommitMessage` | `commit-command-menu.tsx`、`git-workflow-commit-runner.ts` | ✅ | 只提交已暂存内容，不自动 stage/amend/push |
| 刷新 | `GitController.kt` `refresh` | `review/git-workflow-refresh.ts` | ✅ | — |
| Diff 源与 include unstaged | `GitController.kt` `selectDiff` / `setIncludeUnstaged`；`GitDiffRequest` | `review/review-diff-target-state.ts`、`review-branch-range-controls.tsx` | ◐ | 工作区/暂存/分支对比有；无 commit range 过滤菜单 |
| 复制 apply 命令 | `GitDiffApply.kt` `gitApplyCommand`、`GitDiffOptionsMenu` | `apply-review-patch.ts` | ✅ | 只复制命令，不执行 |
| 工具栏状态持久化 | `GitPageComponents.kt` 内全部 `remember` | `review/review-persistent-settings.ts` | ○ | 布局/富预览/换行/词级/隐藏 imports 重启后重置 |
| 词级 diff / 隐藏空白 / 隐藏 imports / 加载完整文件 | `GitWordDiff.kt`、`GitDiffImports.kt`、`GitDiffView` 参数 | `review/review-options-toolbar.tsx` | ✅ | 均有定向测试 |
| 大 diff 单文件模式 | `GitDiffReading.kt` `gitDiffOpensByDefault`；`GitDiffView` `largeDiff` | `large-diff-banner.tsx`、`review-diff-metrics.ts` | ✅ | R4：25/2000 阈值，超限横幅与单文件模式；阈值可注入 |
| Diff 内搜索/跳转 | `GitDiffReading.kt` `findGitDiffMatches`；`GitDiffSearchBar` | `diff-search-*`、`review-search-controller.tsx`、`review-file-navigation.ts` | ✅ | R4：查找栏、命中计数、上/下一个、命中文件自动展开并逐行定位 |
| 行内评论/应用补丁/还原 | 无 | `review-comment-utils.ts`、`patch-action-runner.ts`、`revert-confirm-dialog.tsx` | ○ | 无 |
| Push / Pull / PR / 合并 | 无 | `git-workflow-push-runner.ts`、`git-workflow-pull-request-runner.ts`、`git-pull-request-mutation.ts` | ○ | `GitModels.kt` 明确本期只做 index-only |

#### Worktrees 页面

- Kotlin 入口：`client/git/GitPages.kt` `WorktreesPage`；托管逻辑 `client/git/ManagedWorktreeController.kt`、`ManagedWorktreeModels.kt`；Host 侧 `DesktopManaged*`、`DesktopWorktreeChatArchive*`
- restored 对应：`worktree/`、`settings/worktrees-settings-page/`

| 组件 / 功能 | Kotlin 证据 | restored 证据 | 状态 | 缺口/说明 |
| --- | --- | --- | --- | --- |
| Worktree 列表 | `GitPages.kt` `WorktreesPage`；`GitController` 读取 | `worktree/worktree-activity-list.tsx` | ✅ | 路径、分支、HEAD、当前标记 |
| 新建 worktree | `GitPages.kt` `WorktreeCreationForm`、`GitController.createWorktree` | `worktree/new-thread-orchestrator-backing.ts`、`worktree-init-page.tsx` | ✅ | 路径/分支/起始 ref |
| 托管 worktree 保护 | `ManagedWorktreeModels.kt` `ManagedOwnership`、`ManagedProtectionAvailability`、`canRemove` | `worktree/managed-worktree-status.ts` | ✅ | 指纹、归属、移除阻断理由 |
| 会话观察 / 归档 / 恢复 | `ManagedWorktreeController.kt`；`DesktopWorktreeChatArchiveGate.kt`、`DesktopWorktreeChatRestoreService.kt` | `worktree/worktree-activity-boundary-runtime.tsx`、`worktree-restore-runtime.ts` | ✅ | 在线/持久观察、归档门禁、恢复 |
| Pin worktree | `DesktopWorktreePinStore.kt`、`ManagedWorktreeModels.kt` `canPin` | `worktree/pending-worktree-atoms.ts` | ✅ | — |
| 根目录 / 环境设置 | `GitPages.kt` `WorktreeRootSection`、`WorktreeEnvironmentSection` | `settings/worktrees-settings-page/` | ✅ | 根目录、自动清理、保留数量、上游刷新 |
| 从 hotkey window 初始化 worktree | 无 | `worktree/hotkey-window-worktree-init-page.tsx` | ○ | 无 hotkey window |
| 新建会话时的 worktree 选择 | `ManagedWorktreeController` 关联会话 | `worktree/pending-worktree-conversation-ui.tsx` | ◐ | 有托管与关联；无 pending worktree 的会话 UI |

**Git 结论**：diff 阅读的工具栏、布局、词级 diff、富预览、apply 命令复制、R4 的吸顶文件头/上下文展开/大 diff 单文件模式/搜索跳转，以及 Worktrees 的托管/保护/归档恢复已对齐。缺口按影响：① 无行内评论/应用补丁/还原与 blame gutter；② 无 push/pull/PR 工作流与 PR 页；③ 工具栏状态不持久化（G1b）；④ 无文件树/变更面板与文件间跳转。

## 四、跨页面能力

| 能力 | Kotlin 证据 | restored 证据 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 页内查找 | diff：`GitDiffSearchBar`；对话页仍无落点 | `find/`（23 个文件） | ◐ | R4 已有 diff 内查找；对话页无查找栏；快捷键模型仍把查找组合键列为保留 |
| 全局快捷键窗口 | 无落点 | `hotkey-window/`（4 个文件） | ○ | 只有应用内快捷键（`client/settings/Shortcuts.kt`） |
| 快捷聊天 | 无落点 | `quick-chat/` | ○ | 未提供任何入口 |
| 系统通知 | 无落点（`rg "NotificationService\|系统通知"` 无命中） | `notifications/` | ○ | 定时任务完成/失败不会发系统通知 |
| 多语言 | 文案硬编码 zh-CN，无 locale 层 | `locales/`、`i18n/` | — | 已确认只做 zh-CN；逐条文案对照见 `restored-reference-index.md` 第 11 节 |

命令菜单与主题属于外壳/设置范围，见对应页面小节。

## 五、缺口汇总与建议顺序

按“影响日常使用 + 是否阻塞其他能力”排序。每条都可在上面的页面小节找到两侧证据。

### P0 — 先用起来会直接撞上的

| # | 缺口 | 页面 | 对应任务 |
| --- | --- | --- | --- |
| 1 | ~~审批交互完全缺失~~：协议、待审批状态与卡片已完成（G2a）；仍缺运行时门控与 respond 端点 | 对话页 | G2b / Kaneo #17 |
| 2 | ~~会话内 diff 卡缺失~~：四类卡片、截断与 Git 跳转已完成（G3a）；仍缺 producer | 对话页 | G3b / Kaneo #42 |
| 3 | 布局状态全部不持久化：navigator 宽度/模式、pinned rail、tab 布局、diff 工具栏、滚动位置 | 外壳 / Git | G1 / Kaneo #41 |

### P1 — 主路径能用，但能力明显不完整

| # | 缺口 | 页面 | 对应任务 |
| --- | --- | --- | --- |
| 4 | ~~Diff 无搜索、无大 diff 单文件模式、无上下文展开~~；仍无 blame | Git | R4（搜索/横幅/上下文已完成，blame 移出） |
| 5 | 无行内评论 / 应用补丁 / 还原 | Git | R5 |
| 6 | 设置 General 13 行禁用；缺 15+ 子页（Agent/Usage/MCP/Hooks/Cloud/Environments 等） | 设置 | 部分归 R6/R7，其余未列入 |
| 7 | Connections 无 SSH/WSL、登录/登出、设备授权、重启连接 | 设置 | 未列入 |
| 8 | Git 偏好整组缺失；无 push/pull/PR 工作流与 PR 页 | 设置 / Git | 未列入 |
| 9 | 定时任务固定保存为 Paused、无 Current/Paused 分区与行内操作、无 RRULE/heartbeat 间隔/reasoning | 定时任务 | R7 只覆盖 triggers 的一部分 |
| 10 | 插件只覆盖本地已安装：无 Marketplace、安装/卸载、分享、Skills/Apps/MCPs tab、Connectors | 插件 | R6/R7 |
| 11 | 侧栏无拖拽排序、置顶最近分区、hover card、滚动渐隐、worktree 移动确认 | 外壳 | 未列入 |
| 12 | 右侧面板只有终端/子 agent 两类 tab；摘要面板 plan/outputs/sources/sideChats 未接线 | 对话页 | R1 收尾 / R2 |

### P2 — 体验与一致性

| # | 缺口 | 页面 | 对应任务 |
| --- | --- | --- | --- |
| 13 | 新标签页地址栏不可编辑、工具栏 inert、3/4 工具禁用 | 新标签页 | 未列入 |
| 14 | 项目页无排序/pin/remove/archive/折叠/无限滚动；`RecentTasksPage`、`ProjectsPage`、`ProjectPage` 是死代码 | 项目 | 未列入 |
| 15 | 账号页是自建手机号/密码，不是 ChatGPT/Google/Microsoft/API key | 账号 | 未列入 |
| 16 | 无页内查找、Quick Chat、Hotkey window、系统通知；仅 zh-CN | 跨页面 | 未列入 / 明确不做 |
| 17 | 主题预设、代码主题、设置内容搜索、乐观写入缺失 | 设置 | 未列入 |
| 18 | 无同状态像素对照与 token/文案偏差清单 | 跨页面 | R9 |

### 建议顺序

1. **R4（diff 阅读）**：缺口最集中，且 R3 的预览容器已就位；同批可把“工具栏状态持久化”一起做掉。
2. **R2（子 Agent 最终回复与 diffStats）**：收尾 R1 的摘要面板，把 plan/outputs/sources/sideChats 的数据接线补齐。
3. **新立项：审批交互 + 会话内 diff 卡**：这是对话页与 Codex 差距最大的一处，且 R5 的行内评论依赖 diff 渲染。
4. **R6 / R7（MCP、hooks/scripts）**：插件页从“只读清单”走向可用。
5. **R8 / R9**：终端与像素对照作为收口。

逐项拆分、依赖、验收与 Kaneo 映射见 [缺口逐项实施计划](gap-implementation-plan-2026-11-02.md)。

### 核对边界

- 本次是静态代码核对，没有运行应用做端到端验证；标注 ✅ 表示“代码与测试落点齐全”，不代表像素一致。
- Android/iOS 的适配未逐页核对，只覆盖桌面（jvm）路径。
- `connectors/`、`auth/`、`environments/` 只做了入口级确认。

# Restored 参考索引与契约偏差审计

状态：R0 交付  
审计日期：2026-11-02  
参考副本：`references/decode-codex`（HEAD `a3cd34ee`）  
规划来源：[restored-code-task-plan-2026-11-02.md](restored-code-task-plan-2026-11-02.md)

## 一、目的与方法

`references/decode-codex/restored/` 是可读的 Codex 桌面端语义源码。本索引把它按 12 个区域映射到本仓库的 Kotlin 实现，并给出当前状态，作为 R1–R9 的输入。

状态口径：

| 标记 | 含义 |
| --- | --- |
| ✅ 已对齐 | Kotlin 已实现主契约，且有契约文档或定向测试 |
| ◐ 部分 | 主路径已实现，缺口在下方逐条列出 |
| ○ 未实现 | 没有 Kotlin 落点 |
| — 不做 | 已确认排除，或计划中明确不做 |

证据优先级：Kotlin 源码 > `docs/desktop-ui/reverse-engineering/` 契约文档 > `docs/desktop-ui/verification/` 验证记录 > `replication-plan-2026-10-07.md` 的批次记录。本索引不把“文档写了”当成“代码做了”，每条状态都尽量给源码落点。

路径约定：

- restored 路径相对 `references/decode-codex/restored/`；
- Kotlin 路径相对仓库根。

区域级索引之外，[逐页面 · 逐组件 · 逐功能核对](page-component-feature-audit-2026-11-02.md) 按页面给出组件与功能两层的逐项状态和缺口，两者状态口径一致。

## 二、总览

| # | 区域 | restored | Kotlin | 状态 | 主要缺口 |
| --- | --- | --- | --- | --- | --- |
| 1 | 会话时间线 | `conversations/`（478） | `client/conversation/`、`client/app/` | ◐ | 搜索、fork UI、resume 错误恢复、虚拟化列表、分隔线、patch 失败对话框 |
| 2 | 摘要面板 | `conversations/local-conversation-thread-parts/thread-summary-*` | `client/summary/` | ◐ | R1 已补小节顺序、autoCollapse 与三种 mode；G1 已接线展开状态落盘，plan/artifacts/side-chat 数据源尚缺 |
| 3 | 子 Agent / 后台任务 | `conversations/background-*`、`subagent-activity-*` | `client/panels/ThreadPanels*` | ◐ | R2 已补最终回复节、diffStats 徽标与 autoCollapse；diffStats 仍无 Host turn-diff 生产方 |
| 4 | Composer | `composer/`（205） | `client/app/ConversationComposer*` | ◐ | 附件、@mention、slash/命令键位、文件上下文监听 |
| 5 | Git / Diff / Review | `review/`（146）、`git/`（17）、`worktree/`（12） | `client/git/` | ◐ | R3 已补 image/SVG/PDF 预览；R4 已补 sticky、上下文展开、diff 搜索、大 diff 横幅；余行内评论、apply/revert、blame |
| 6 | 插件 / MCP / Skills | `plugins/`（111） | `client/plugins/`、`modules/bundle/desktop/` | ◐ | MCP 真实连接/授权、MCP App 宿主、hooks 写入/启停、scripts 生命周期、目录型 apps |
| 7 | 定时任务 | `automations/`（37）、`automation/`（30） | `client/scheduled/`、`client/scheduling/` | ◐ | 触发条件（triggers）；应用退出后执行明确不做 |
| 8 | 终端 | `terminal/`（16） | `client/panels/PtyTerminal*`、`TerminalTextInputBridge` | ◐ | 链接处理、原生窗口组合、远端链路 |
| 9 | 壳层 / 导航 | `app-shell/`（184）、`sidebar/`（49）、`command-menu/`（11）、`routes/`（9） | `client/shell/`、`client/page/` | ◐ | 同状态像素、原生窗口、远端 |
| 10 | 设置 | `settings/`（248） | `client/settings/`、`client/page/Settings*` | ◐ | hooks 设置、agent 设置、自动化设置、像素 |
| 11 | 主题 / 图标 / 文案 | `themes/`（13）、`icons/`（200）、`ui/`（97）、`locales/`（64） | `modules/ui-foundation/`、`client/settings/Appearance*` | ◐ | 无系统化 token/文案偏差清单 |
| 12 | 项目 / Recent / Home | `projects/`（12）、`home/`（20）、`threads/`（74） | `client/projects/`、`client/page/` | ◐ | ambient suggestions、Recent 元数据、像素 |

## 三、逐区域索引

### 1. 会话时间线

**restored 关键文件**

- `conversations/local-conversation-thread-parts/local-conversation-thread-*.tsx` — 线程框架、body、footer、route
- `.../local-conversation-turn-row.tsx`、`local-conversation-turn-list-entries.ts`、`local-conversation-virtualized-turn-list*` — 轮次渲染与虚拟化
- `.../conversation-turn-rendering.ts`、`conversation-content-runtime.ts`、`conversation-detail-runtime.ts` — 渲染与详情模式
- `.../local-conversation-search*.ts` — 会话内搜索
- `.../local-conversation-fork-dialog.tsx`、`conversation-fork-runtime.ts` — 分叉
- `.../local-conversation-resume*.ts(x)` — 恢复与错误
- `.../local-conversation-read-state.ts`、`local-conversation-auto-follow-turn-list.tsx` — 已读与自动跟随
- `.../model-changed-divider.tsx`、`personality-changed-divider.tsx` — 分隔线
- `.../patch-failure-dialog.tsx` — patch 失败
- `.../pull-request-side-panel-*.tsx` — PR 侧栏（与摘要面板共享）

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/conversation/ConversationController.kt` — 命令、状态、fork、已读、管理
- `apps/shared/src/im/hikaru/harness/client/conversation/LiveTranscript.kt` — 实时转录
- `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` — 消息、工具卡、复制/编辑/重试
- `apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt`、`SessionStreamDriver.kt`
- `apps/shared/src/im/hikaru/harness/client/page/ThreadPage.kt`

**已有契约/验证**

- `docs/desktop-ui/reverse-engineering/home-thread-implementation-contract-2026-10-07.md`
- `docs/desktop-ui/verification/` 下 Thread 相关批次

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| typed 历史、流式增量、工具调用实时展示 | ✅ | `ConversationComponents.kt`、`LiveTranscript.kt`；提交 `cb578f2b` |
| 复制/编辑/重试、已读、rename/pin/archive | ✅ | `ConversationComponents.kt:51-53,189-194`、`ConversationController.kt:94-104` |
| fork | ◐ | 控制器有 `ConversationErrorKind.Fork`；`ConversationContextMenu.kt:36-38` 的 fork 菜单项仍 `enabled = false` |
| 会话内搜索 | ○ | restored `local-conversation-search*.ts`；Kotlin 无对应 |
| 自动跟随 / 虚拟化轮次列表 | ○ | restored `local-conversation-auto-follow-turn-list.tsx`、`local-conversation-virtualized-turn-list*` |
| resume 错误恢复 | ○ | restored `local-conversation-resume-errors.tsx`、`local-conversation-resume-runtime.ts` |
| model/personality 分隔线 | ○ | restored `model-changed-divider.tsx`、`personality-changed-divider.tsx` |
| patch 失败对话框 | ○ | restored `patch-failure-dialog.tsx` |
| 详情模式（steps/prose） | ◐ | restored `conversation-detail-runtime.ts`；Kotlin 未见显式模式切换 |
| PR 侧栏 | ○ | restored `pull-request-side-panel-*`；是否纳入取决于 PR 范围确认 |

### 2. 摘要面板

**restored 关键文件**

- `conversations/local-conversation-thread-parts/thread-summary-panel-sections.tsx` — 小节装配
- `.../thread-summary-panel-section.tsx` — `autoCollapse` 30000ms、`accordion | dropdown | headerless`、持久化 key 前缀 `thread-summary-panel-section-expanded-`
- `.../thread-summary-source-rows.tsx`、`thread-summary-web-sources.ts` — Sources
- `.../summary-panel-artifacts-list.tsx`、`local-conversation-output-artifacts.ts` — Artifacts / Outputs
- `.../plan-summary-card.tsx`、`plan-summary-card/` — Plan
- `.../thread-summary-side-chat-rows.tsx` — Side chats
- `.../thread-summary-automation-row.tsx` — Automations
- `.../pull-request-summary-row.tsx`、`pull-request-git-summary-runtime.ts` — Pull requests
- `.../thread-summary-panel-chrome-primitives.tsx` — 标题/计数/按钮
- `.../local-conversation-summary-panel.tsx`、`local-conversation-summary-panel-mcp-sources.ts` — 面板装配与 MCP sources

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryPanel.kt`（377 行）
- `.../ThreadSummaryModels.kt`、`ThreadSummaryActivity.kt`、`ThreadSummaryActivityController.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| environment / subagents / tasks / scheduled 四类小节 | ✅ | `ThreadSummaryPanel.kt`；R1 起统一走 `ThreadSummarySection` |
| Sources | ✅ UI / ○ 数据源 | `SourcesSection` + `暂无来源` 空态；harness 尚无 MCP/网页搜索工具 |
| Artifacts / Outputs | ✅ UI / ○ 数据源 | 无 Git 摘要时渲染 `OutputsSection` + `暂无产物` 空态；无 artifact 采集 |
| Plan | ✅ UI / ○ 数据源 | `PlanSection` 仅在传入 plan 时渲染；无 plan 文档来源 |
| Side chats | ✅ UI / ○ 数据源 | `SideChatsSection` 仅在传入时渲染；无 side-chat 服务 |
| Automations | ✅ | 既有 `ScheduledSection`（automation 小节） |
| Pull requests | ○ | restored `pull-request-summary-row.tsx` |
| autoCollapse 30s | ✅ | `ThreadSummarySection.kt`，可注入 `autoCollapseDelayMillis` |
| 三种 mode / 持久展开状态 | ✅ 会话内 / ○ 落盘 | `ThreadSummarySectionMode`、`ThreadSummarySectionState`；`persist` 回调待接设置存储 |
| diffStats 徽标 | ✅ | R0 已确认由 `EnvironmentSection`/`ChangesRow` 实现 |

### 3. 子 Agent / 后台任务

**restored 关键文件**

- `conversations/background-subagent-signals.ts`、`background-subagent-linked-threads.ts`
- `conversations/background-summary.ts`、`background-thread-*.ts`
- `conversations/subagent-activity-inline-group.tsx`、`subagent-activity-row.tsx`
- `conversations/local-conversation-thread-parts/local-conversation-background-agent-*.ts*`

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt`、`ThreadPanelsController.kt`、`ThreadPanelsRepository.kt`、`ThreadPanelsState.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| typed 历史复用 MessageCard | ✅ | 计划记录 R1 59/59 |
| 最终回复节 | ✅ | 详情节按 `subagents.panel.response`；Host 从子会话 typed 消息读取最新 assistant 文本，无数据时显式 unavailable；R2 验证 |
| diffStats 徽标 | ◐ | 摘要面板行徽标与协议字段已就绪，数字只来自 Host 真实 stats；当前 Host 无 turn-diff 生产方，故不显示 |
| autoCollapse | ✅ | 与 R1 共用原语，摘要面板子智能体小节已接线 |

### 4. Composer

**restored 关键文件**

- `composer/composer-atoms.ts`、`composer-attachment-*.ts*` — 状态与附件
- `composer/at-mention-list.ts`、`artifact-mention.ts`、`active-artifact-mention-signal.ts` — @mention
- `composer/above-composer-suggestions.tsx`、`above-composer-panel-row.tsx` — 输入框上方建议
- `composer/composer-command-keymap.ts`、`command-shortcut-bindings.ts` — 键位
- `composer/composer-context-files-watcher.tsx` — 文件上下文
- `composer/composer-banners/` — 横幅
- `composer/build-create-conversation-params.ts`、`build-image-input-items.ts` — 提交参数

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/app/ConversationComposerToolbar.kt`（498 行）
- `.../ConversationComposerRail.kt`（389 行）
- `.../LiveConversationComposer.kt`、`ComposerPresentation.kt`
- `.../ConversationComposerRail.kt` 对应 Home 的空态 composer

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 文本输入、模型选择、发送/停止、横幅 | ✅ | `ConversationComposerToolbar.kt`、`LiveConversationComposer.kt` |
| 附件 | ○ | `ConversationComposerToolbar.kt:86` 仅有 `live-composer-attachment` 禁用按钮 |
| @mention / artifact mention | ○ | restored `at-mention-list.ts`、`artifact-mention.ts`；Kotlin 无 |
| slash / 命令键位 | ○ | restored `composer-command-keymap.ts`；Kotlin 无 |
| 输入框上方建议 | ○ | restored `above-composer-suggestions.tsx`；Home 无 ambient suggestions |
| 文件上下文监听 | ○ | restored `composer-context-files-watcher.tsx` |

### 5. Git / Diff / Review

**restored 关键文件**

- `review/file-diff.tsx`、`file-diff-runtime.ts`、`file-source-*.tsx` — 文件 diff 与源文件视图
- `review/file-source-rich-preview.tsx` — `RichPreviewKind = image | markdown | pdf | pdb`
- `review/large-diff-banner.tsx` — 大 diff
- `review/diff-search-adapter.ts`、`diff-search-controller-runtime.ts`、`diff-find-highlight.ts` — 搜索
- `review/review-comment-utils.ts`、`diff-selection-summary.ts`、`apply-review-patch.ts` — 行内评论与 patch
- `review/file-change-gutter.ts`、`file-blame-gutter.tsx` — gutter
- `review/editor-diff-page.tsx`、`changed-files-panel.tsx` — 页面装配
- `git/git-branch-switcher.tsx`、`git/git-review-primitives.tsx`
- `worktree/` — 工作树

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`（804 行）、`GitPageComponents.kt`（930 行）
- `.../GitDiffLayout.kt`、`GitDiffLines.kt`、`GitDiffFiles.kt`、`GitDiffApply.kt`、`GitDiffImports.kt`
- `.../GitRichPreview.kt`、`GitWordDiff.kt`、`GitModels.kt`
- `.../ManagedWorktreeController.kt`、`ManagedWorktreeModels.kt`

**已有契约/验证**

`docs/desktop-ui/reverse-engineering/` 下 git-*、diff-*、hide-imports、load-full-files、rich-preview-menu 等 12 份契约；`verification/` 下对应批次。

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 分支切换、提交、新建分支、刷新、复制 apply | ✅ | 契约文档 + 验证批次 |
| diff 布局 unified/split/auto | ✅ | `GitDiffLayout.kt:7` 枚举；`GitDiffView` 选择器 |
| 词级 diff、隐藏空白、隐藏 imports、加载完整文件 | ✅ | `GitWordDiff.kt`、`GitDiffView` 参数 |
| markdown 富预览 | ✅ | `GitRichPreview.kt` 的 `GitDiffPreviewKind.Markdown` |
| image / SVG / PDF 预览 | ✅ | R3：`GitDiffPreviewKind.Image/Svg/Pdf` + `GitPreviewDecoding.kt`；PDF 仅 JVM 渲染，Android/iOS 回落 diff |
| sticky 文件头 | ✅ | R4：`GitDiffFileCard` 用 `ScrollState` + `graphicsLayer translationY` 吸顶，并由下一个文件头顶出 |
| 上下文展开 | ✅ | R4：`GitDiffReading.kt` 解析 hunk/gap，按 hunk 展开 20 行并重算 `@@` 头；暂存区 diff 与删除文件禁用 |
| diff 内搜索 | ✅ | R4：`findGitDiffMatches` + `GitDiffSearchBar`；计数、上下跳转、命中文件自动展开并定位到行 |
| 行内评论 / apply patch | ○ | 无 `InlineComment` / `ReviewComment` 落点 |
| 大 diff 横幅 | ✅ | R4：`GitDiffLargeBannerCopy` 与 restored 阈值 25 文件 / 2000 变更行 |
| blame gutter | ○ | 无 `file-blame-gutter` 对应 |

### 6. 插件 / MCP / Skills

**restored 关键文件**

- `plugins/plugin-capabilities.ts`、`plugin-config-edits.ts`、`plugin-detail-page-current.tsx`
- `plugins/active-mcp-app-frame.tsx`、`mcp-app-frame*.ts*`、`mcp-app-sandbox-*.ts`
- `plugins/mcp-app-tool-proxy.ts`、`mcp-app-tool-resolver.ts`、`mcp-tool-activity-runtime.tsx`
- `plugins/mcp-app-errors.ts`、`mcp-capability-signals/`
- `plugins/format-skill-scope.tsx`、`skill-utils.ts`、`use-skills.ts`
- `runtime/app-main-host-runtime/hooks-settings/`
- `settings/mcp-settings.tsx`、`settings/hooks-settings-page/`

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt`（487 行）、`InstalledPluginsController.kt`
- `.../PluginModels.kt`、`PluginManagement.kt`、`PluginRepository.kt`、`PluginsManagementNavigator.kt`
- `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 已安装列表、详情、能力清单、启停、登记/移除、更新、Skills | ✅ | 契约文档 + `InstalledPluginsPage.kt` |
| 能力名读取（MCP/Hooks/Scripts） | ✅ | `DesktopUserPlugins.kt:318,391,401` 只读名称 |
| MCP 真实连接 / 授权 / 工具调用 | ○ | 无连接或授权实现 |
| MCP App 宿主 / 沙箱桥 | ○ | restored `mcp-app-*`；Kotlin 无 |
| hooks 配置写入 / 启停 | ○ | 只有名称读取 |
| scripts 执行生命周期 | ○ | 只有名称读取 |
| 目录型 apps | ○ | `PluginAppsState` 只有名称列表 |

### 7. 定时任务

**restored 关键文件**

- `automations/` — 页面、列表、详情、编辑器、运行历史
- `automation/` — 触发条件、调度模型
- `conversations/local-conversation-thread-parts/thread-summary-automation-row.tsx` — 摘要联动

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/scheduling/`（14 文件）
- `apps/shared/src/im/hikaru/harness/client/scheduled/ScheduledTasksRepository.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 页面、列表、详情、编辑器、暂停/恢复、运行历史、ledger | ✅ | `ScheduledManagementPage.kt`、`ScheduledExecutor.kt`、契约文档 |
| 执行模型披露、筛选、排序、时间戳、删除对话框 | ✅ | 计划批次 AJ/BA–BU |
| 触发条件（triggers） | ○ | restored `thread-summary-automation-row.tsx` / automations；Kotlin 未见 trigger 模型 |
| 应用退出后后台执行 | — | 计划明确不做 |

### 8. 终端

**restored 关键文件**

- `terminal/terminal-panel.tsx`、`terminal-session-tab.tsx`、`terminal-panel-tabs*.ts*`
- `terminal/terminal-key-event-handler.ts`、`terminal-link-handler.ts`
- `terminal/terminal-view-utils.ts`、`ansi-output.tsx`
- `terminal/use-conversation-terminal-snapshot.ts`

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalController.kt`、`PtyTerminalGrid.kt`、`PtyTerminalModels.kt`、`PtyTerminalPanel.kt`
- `.../TerminalTextInputBridge.kt`、`TerminalLaunchProvider.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| PTY 网格、紧凑视觉、字体 | ✅ | 计划批次 R5/R1 |
| IME 组合输入 | ✅ | `TerminalTextInputBridge.kt:29-67` 已处理 composition / Escape 取消 / 提交 |
| 按键处理 | ✅ | `TerminalTextInputBridge.kt` |
| 链接识别 / 打开 | ○ | restored `terminal-link-handler.ts`；Kotlin 无 |
| 原生窗口组合 | ○ | 计划「尚缺」 |
| 远端链路 | ○ | 依赖真实 Host |

### 9. 壳层 / 导航

**restored 关键文件**

- `app-shell/` — 壳层、面板、tab strip、窗口区域
- `sidebar/` — 侧栏、工作区导航
- `command-menu/` — 命令菜单
- `routes/` — 路由模型

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt`（3084 行）、`AppShellState.kt`、`HarnessMainRegion.kt`
- `.../HarnessTabStrip.kt`、`NavigationState.kt`、`RailDestinations.kt`、`NavigatorLayout.kt`
- `.../ConversationContextMenu.kt`、`SettingsNavigatorModel.kt`
- `.../page/ShellPages.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| rail、navigator、tab strip、命令菜单、搜索、设置导航 | ✅ | 源码 + 计划批次 |
| 上下文菜单（rename/pin/archive） | ✅ | `ConversationContextMenu.kt` |
| 同状态像素 | ○ | R9 |
| 原生窗口组合 | ○ | 计划「尚缺」 |
| 远端链路 | ○ | 计划「尚缺」 |

### 10. 设置

**restored 关键文件**

- `settings/general-settings-page.tsx`、`appearance-settings.tsx`、`keyboard-shortcuts-settings/`
- `settings/mcp-settings.tsx`、`hooks-settings-page/`、`plugins-settings.tsx`
- `settings/agent-settings.tsx`、`automations/`、`remote-connections-page.tsx`
- `settings/setting-storage.tsx`、`settings-keys-runtime.ts`

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/settings/`（12 文件）
- `.../page/SettingsGeneralPage.kt`、`SettingsAppearancePage.kt`、`SettingsModelsPage.kt`、`SettingsConnectionsPage.kt`、`SettingsShortcutsPage.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 常规、外观、模型、连接、快捷键、工作树设置 | ✅ | 源码 + 契约文档 |
| hooks 设置 | ○ | 依赖 R7 |
| agent 设置 | ○ | restored `agent-settings.tsx` |
| 自动化设置 | ○ | restored `settings/automations/` |
| 同状态像素 | ○ | R9 |

### 11. 主题 / 图标 / 文案

**restored 关键文件**

- `themes/` — 主题变量
- `icons/`（200） — 图标
- `ui/`（97） — 基础组件
- `locales/zh-cn.ts` — 官方中文文案

**Kotlin 落点**

- `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/`
- `apps/shared/src/im/hikaru/harness/client/settings/AppearanceSettings.kt`、`AppearanceThemeAdapter.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| token、主题、图标基础 | ✅ | `HarnessTokens.kt`、`HarnessComponents.kt` |
| 系统化 token 偏差清单 | ○ | R9 |
| 官方 zh-CN 文案对照 | ◐ | 已做过插件错误态本地化（BZ），无全量清单 |

### 12. 项目 / Recent / Home

**restored 关键文件**

- `projects/` — 项目页与状态
- `home/` — Home 空态、建议
- `threads/` — 会话列表与元数据
- `main/ambient/ambient-suggestions*` — 环境建议

**Kotlin 落点**

- `apps/shared/src/im/hikaru/harness/client/projects/`、`page/LocalProjectsPage.kt`、`page/NewTabPage.kt`、`page/ProjectsRecentPageComponents.kt`
- `.../shell/ProjectsState.kt`

**偏差**

| 项 | 状态 | 证据 |
| --- | --- | --- |
| 项目登记、cwd 绑定、Recent、Home 选择 | ✅ | 计划批次 14/14、4/4 |
| ambient suggestions | ○ | restored `main/ambient/ambient-suggestions*`；Kotlin 无 |
| Recent 的 title/updatedAt/unread/运行状态 | ◐ | 计划明确「不伪造这些操作」，需真实服务 |
| 同状态像素 | ○ | R9 |

## 四、对规划假设的修正

R0 审计发现两处需要修正 [restored-code-task-plan-2026-11-02.md](restored-code-task-plan-2026-11-02.md) 的假设：

1. **R4 的「diff 模式选择」已经实现**：`GitDiffLayout.kt:7` 已有 `Unified | Split | Auto`，页面也有选择器。R4 应移除该项，只保留 sticky、上下文展开、大 diff 横幅、diff 搜索。
2. **R8 的「IME 组合输入」已经实现**：`TerminalTextInputBridge.kt` 已处理 composition 区间、Escape 取消和提交，且有计划记录。R8 应改为「链接处理 + 原生窗口组合 + 远端链路」，不再把 IME 基础能力列为缺口。

其余 R1、R2、R3、R5、R6、R7、R9 的缺口假设与源码一致。

## 五、下一步

- R1：已完成（摘要面板小节与交互原语）。
- R3：已完成（image / SVG / PDF 富预览；PDF 仅 JVM 渲染）。
- 页面级核对：已完成，见 [逐页面 · 逐组件 · 逐功能核对](page-component-feature-audit-2026-11-02.md)；P0 缺口为审批交互、对话内 diff 卡片、布局状态持久化。
- R6：按第 6 节接入 MCP 连接/授权，先把「能力名读取」升级为「服务可用」。
- R4：已完成（sticky、上下文展开、大 diff 横幅与单文件模式、diff 内搜索；blame 明确移出 R4）。
- R8：按第四节修正后的范围执行。
- 本索引随实现推进更新；状态变化以源码和验证记录为准，不以计划文字为准。

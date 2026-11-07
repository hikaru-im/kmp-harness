# UI 组件源码覆盖清单

日期：2026-11-03。文件级盘点；列入此表表示纳入审查范围，不表示已完成行为、视觉或无障碍对照。具体覆盖结论和待核对项见 [差异报告](ui-component-difference-audit.md)。

此表包含路由、状态和平台适配文件，便于防止只审页面截图而漏掉入口、状态及实际挂载路径。参考项目不写入、不复制代码。

## apps/shared/src/im/hikaru/harness/client

源码文件数：142（含 S-01 新增的搜索契约、对话框及 S-02 悬停能力契约）。

| 文件 | 对照状态归属 |
|---|---|
| [AccountModels.kt](../apps/shared/src/im/hikaru/harness/client/account/AccountModels.kt) | account；见差异报告覆盖表 |
| [AccountModule.kt](../apps/shared/src/im/hikaru/harness/client/account/AccountModule.kt) | account；见差异报告覆盖表 |
| [AccountWorkspacePage.kt](../apps/shared/src/im/hikaru/harness/client/account/AccountWorkspacePage.kt) | account；见差异报告覆盖表 |
| [KtorMemberTransport.kt](../apps/shared/src/im/hikaru/harness/client/account/KtorMemberTransport.kt) | account；见差异报告覆盖表 |
| [MemberAccount.kt](../apps/shared/src/im/hikaru/harness/client/account/MemberAccount.kt) | account；见差异报告覆盖表 |
| [ApprovalCard.kt](../apps/shared/src/im/hikaru/harness/client/app/ApprovalCard.kt) | app；见差异报告覆盖表 |
| [ClientUiSlots.kt](../apps/shared/src/im/hikaru/harness/client/app/ClientUiSlots.kt) | app；见差异报告覆盖表 |
| [ComposerPresentation.kt](../apps/shared/src/im/hikaru/harness/client/app/ComposerPresentation.kt) | app；见差异报告覆盖表 |
| [ConversationAppearance.kt](../apps/shared/src/im/hikaru/harness/client/app/ConversationAppearance.kt) | app；见差异报告覆盖表 |
| [ConversationComponents.kt](../apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt) | app；见差异报告覆盖表 |
| [ConversationComposerRail.kt](../apps/shared/src/im/hikaru/harness/client/app/ConversationComposerRail.kt) | app；见差异报告覆盖表 |
| [ConversationComposerToolbar.kt](../apps/shared/src/im/hikaru/harness/client/app/ConversationComposerToolbar.kt) | app；见差异报告覆盖表 |
| [ConversationErrorToasts.kt](../apps/shared/src/im/hikaru/harness/client/app/ConversationErrorToasts.kt) | app；见差异报告覆盖表 |
| [ConversationMarkdown.kt](../apps/shared/src/im/hikaru/harness/client/app/ConversationMarkdown.kt) | app；见差异报告覆盖表 |
| [DesktopClientCatalog.kt](../apps/shared/src/im/hikaru/harness/client/app/DesktopClientCatalog.kt) | app；见差异报告覆盖表 |
| [FileChangeCard.kt](../apps/shared/src/im/hikaru/harness/client/app/FileChangeCard.kt) | app；见差异报告覆盖表 |
| [HarnessApp.kt](../apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt) | app；见差异报告覆盖表 |
| [HarnessTitleStrip.kt](../apps/shared/src/im/hikaru/harness/client/app/HarnessTitleStrip.kt) | app；见差异报告覆盖表 |
| [LiveConversationComposer.kt](../apps/shared/src/im/hikaru/harness/client/app/LiveConversationComposer.kt) | app；见差异报告覆盖表 |
| [LiveConversationSurface.kt](../apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt) | app；见差异报告覆盖表 |
| [ReasoningOptionsEditor.kt](../apps/shared/src/im/hikaru/harness/client/app/ReasoningOptionsEditor.kt) | app；见差异报告覆盖表 |
| [SessionStreamDriver.kt](../apps/shared/src/im/hikaru/harness/client/app/SessionStreamDriver.kt) | app；见差异报告覆盖表 |
| [Connection.kt](../apps/shared/src/im/hikaru/harness/client/connection/Connection.kt) | connection；见差异报告覆盖表 |
| [ConnectionDescriptionRepository.kt](../apps/shared/src/im/hikaru/harness/client/connection/ConnectionDescriptionRepository.kt) | connection；见差异报告覆盖表 |
| [ConnectionsState.kt](../apps/shared/src/im/hikaru/harness/client/connection/ConnectionsState.kt) | connection；见差异报告覆盖表 |
| [HostApi.kt](../apps/shared/src/im/hikaru/harness/client/connection/HostApi.kt) | connection；见差异报告覆盖表 |
| [ReasoningOptions.kt](../apps/shared/src/im/hikaru/harness/client/connection/ReasoningOptions.kt) | connection；见差异报告覆盖表 |
| [ReconnectPolicy.kt](../apps/shared/src/im/hikaru/harness/client/connection/ReconnectPolicy.kt) | connection；见差异报告覆盖表 |
| [RelaySequencing.kt](../apps/shared/src/im/hikaru/harness/client/connection/RelaySequencing.kt) | connection；见差异报告覆盖表 |
| [RemoteConnection.kt](../apps/shared/src/im/hikaru/harness/client/connection/RemoteConnection.kt) | connection；见差异报告覆盖表 |
| [RemoteConnectionOwner.kt](../apps/shared/src/im/hikaru/harness/client/connection/RemoteConnectionOwner.kt) | connection；见差异报告覆盖表 |
| [SessionBackgroundProcesses.kt](../apps/shared/src/im/hikaru/harness/client/connection/SessionBackgroundProcesses.kt) | connection；见差异报告覆盖表 |
| [SessionBackgroundProcessesRemote.kt](../apps/shared/src/im/hikaru/harness/client/connection/SessionBackgroundProcessesRemote.kt) | connection；见差异报告覆盖表 |
| [SessionDescendants.kt](../apps/shared/src/im/hikaru/harness/client/connection/SessionDescendants.kt) | connection；见差异报告覆盖表 |
| [SessionDescendantsRemote.kt](../apps/shared/src/im/hikaru/harness/client/connection/SessionDescendantsRemote.kt) | connection；见差异报告覆盖表 |
| [SessionTerminals.kt](../apps/shared/src/im/hikaru/harness/client/connection/SessionTerminals.kt) | connection；见差异报告覆盖表 |
| [TerminalInput.kt](../apps/shared/src/im/hikaru/harness/client/connection/TerminalInput.kt) | connection；见差异报告覆盖表 |
| [HandshakeNegotiationResult.kt](../apps/shared/src/im/hikaru/harness/client/connection/handshake/HandshakeNegotiationResult.kt) | handshake；见差异报告覆盖表 |
| [HandshakeNegotiator.kt](../apps/shared/src/im/hikaru/harness/client/connection/handshake/HandshakeNegotiator.kt) | handshake；见差异报告覆盖表 |
| [ConversationController.kt](../apps/shared/src/im/hikaru/harness/client/conversation/ConversationController.kt) | conversation；见差异报告覆盖表 |
| [LiveTranscript.kt](../apps/shared/src/im/hikaru/harness/client/conversation/LiveTranscript.kt) | conversation；见差异报告覆盖表 |
| [ReferenceFixture.kt](../apps/shared/src/im/hikaru/harness/client/fixture/ReferenceFixture.kt) | fixture；见差异报告覆盖表 |
| [GitController.kt](../apps/shared/src/im/hikaru/harness/client/git/GitController.kt) | git；见差异报告覆盖表 |
| [GitDiffApply.kt](../apps/shared/src/im/hikaru/harness/client/git/GitDiffApply.kt) | git；见差异报告覆盖表 |
| [GitDiffFiles.kt](../apps/shared/src/im/hikaru/harness/client/git/GitDiffFiles.kt) | git；见差异报告覆盖表 |
| [GitDiffImports.kt](../apps/shared/src/im/hikaru/harness/client/git/GitDiffImports.kt) | git；见差异报告覆盖表 |
| [GitDiffLayout.kt](../apps/shared/src/im/hikaru/harness/client/git/GitDiffLayout.kt) | git；见差异报告覆盖表 |
| [GitDiffLines.kt](../apps/shared/src/im/hikaru/harness/client/git/GitDiffLines.kt) | git；见差异报告覆盖表 |
| [GitDiffReading.kt](../apps/shared/src/im/hikaru/harness/client/git/GitDiffReading.kt) | git；见差异报告覆盖表 |
| [GitMarkdown.kt](../apps/shared/src/im/hikaru/harness/client/git/GitMarkdown.kt) | git；见差异报告覆盖表 |
| [GitModels.kt](../apps/shared/src/im/hikaru/harness/client/git/GitModels.kt) | git；见差异报告覆盖表 |
| [GitPageComponents.kt](../apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt) | git；见差异报告覆盖表 |
| [GitPages.kt](../apps/shared/src/im/hikaru/harness/client/git/GitPages.kt) | git；见差异报告覆盖表 |
| [GitPreviewDecoding.kt](../apps/shared/src/im/hikaru/harness/client/git/GitPreviewDecoding.kt) | git；见差异报告覆盖表 |
| [GitRichPreview.kt](../apps/shared/src/im/hikaru/harness/client/git/GitRichPreview.kt) | git；见差异报告覆盖表 |
| [GitSettingsComponents.kt](../apps/shared/src/im/hikaru/harness/client/git/GitSettingsComponents.kt) | git；见差异报告覆盖表 |
| [GitWordDiff.kt](../apps/shared/src/im/hikaru/harness/client/git/GitWordDiff.kt) | git；见差异报告覆盖表 |
| [ManagedWorktreeController.kt](../apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeController.kt) | git；见差异报告覆盖表 |
| [ManagedWorktreeModels.kt](../apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt) | git；见差异报告覆盖表 |
| [LocalProjectsPage.kt](../apps/shared/src/im/hikaru/harness/client/page/LocalProjectsPage.kt) | page；见差异报告覆盖表 |
| [NewTabPage.kt](../apps/shared/src/im/hikaru/harness/client/page/NewTabPage.kt) | page；见差异报告覆盖表 |
| [ProjectsRecentPageComponents.kt](../apps/shared/src/im/hikaru/harness/client/page/ProjectsRecentPageComponents.kt) | page；见差异报告覆盖表 |
| [SettingsAppearancePage.kt](../apps/shared/src/im/hikaru/harness/client/page/SettingsAppearancePage.kt) | page；见差异报告覆盖表 |
| [SettingsConnectionsPage.kt](../apps/shared/src/im/hikaru/harness/client/page/SettingsConnectionsPage.kt) | page；见差异报告覆盖表 |
| [SettingsFormComponents.kt](../apps/shared/src/im/hikaru/harness/client/page/SettingsFormComponents.kt) | page；见差异报告覆盖表 |
| [SettingsGeneralPage.kt](../apps/shared/src/im/hikaru/harness/client/page/SettingsGeneralPage.kt) | page；见差异报告覆盖表 |
| [SettingsModelsPage.kt](../apps/shared/src/im/hikaru/harness/client/page/SettingsModelsPage.kt) | page；见差异报告覆盖表 |
| [SettingsShortcutsPage.kt](../apps/shared/src/im/hikaru/harness/client/page/SettingsShortcutsPage.kt) | page；见差异报告覆盖表 |
| [ShellPages.kt](../apps/shared/src/im/hikaru/harness/client/page/ShellPages.kt) | page；见差异报告覆盖表 |
| [ThreadPage.kt](../apps/shared/src/im/hikaru/harness/client/page/ThreadPage.kt) | page；见差异报告覆盖表 |
| [WorkspacePageComponents.kt](../apps/shared/src/im/hikaru/harness/client/page/WorkspacePageComponents.kt) | page；见差异报告覆盖表 |
| [PtyTerminalController.kt](../apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalController.kt) | panels；见差异报告覆盖表 |
| [PtyTerminalGrid.kt](../apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt) | panels；见差异报告覆盖表 |
| [PtyTerminalModels.kt](../apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalModels.kt) | panels；见差异报告覆盖表 |
| [PtyTerminalPanel.kt](../apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt) | panels；见差异报告覆盖表 |
| [TerminalLaunchProvider.kt](../apps/shared/src/im/hikaru/harness/client/panels/TerminalLaunchProvider.kt) | panels；见差异报告覆盖表 |
| [TerminalTextInputBridge.kt](../apps/shared/src/im/hikaru/harness/client/panels/TerminalTextInputBridge.kt) | panels；见差异报告覆盖表 |
| [ThreadPanelLayout.kt](../apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelLayout.kt) | panels；见差异报告覆盖表 |
| [ThreadPanelModels.kt](../apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelModels.kt) | panels；见差异报告覆盖表 |
| [ThreadPanels.kt](../apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt) | panels；见差异报告覆盖表 |
| [ThreadPanelsController.kt](../apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelsController.kt) | panels；见差异报告覆盖表 |
| [ThreadPanelsRepository.kt](../apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelsRepository.kt) | panels；见差异报告覆盖表 |
| [ThreadPanelsState.kt](../apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelsState.kt) | panels；见差异报告覆盖表 |
| [InstalledPluginsController.kt](../apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsController.kt) | plugins；见差异报告覆盖表 |
| [InstalledPluginsPage.kt](../apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt) | plugins；见差异报告覆盖表 |
| [PluginManagement.kt](../apps/shared/src/im/hikaru/harness/client/plugins/PluginManagement.kt) | plugins；见差异报告覆盖表 |
| [PluginModels.kt](../apps/shared/src/im/hikaru/harness/client/plugins/PluginModels.kt) | plugins；见差异报告覆盖表 |
| [PluginRepository.kt](../apps/shared/src/im/hikaru/harness/client/plugins/PluginRepository.kt) | plugins；见差异报告覆盖表 |
| [PluginsManagementNavigator.kt](../apps/shared/src/im/hikaru/harness/client/plugins/PluginsManagementNavigator.kt) | plugins；见差异报告覆盖表 |
| [ProjectFileSearch.kt](../apps/shared/src/im/hikaru/harness/client/projects/ProjectFileSearch.kt) | projects；S-01 搜索/预览契约 |
| [ProjectFileSearchDialog.kt](../apps/shared/src/im/hikaru/harness/client/projects/ProjectFileSearchDialog.kt) | projects；S-01 搜索/预览交互 |
| [LocalProjectModels.kt](../apps/shared/src/im/hikaru/harness/client/projects/LocalProjectModels.kt) | projects；见差异报告覆盖表 |
| [LocalProjectRepository.kt](../apps/shared/src/im/hikaru/harness/client/projects/LocalProjectRepository.kt) | projects；见差异报告覆盖表 |
| [LocalProjectsController.kt](../apps/shared/src/im/hikaru/harness/client/projects/LocalProjectsController.kt) | projects；见差异报告覆盖表 |
| [ScheduledTasksRepository.kt](../apps/shared/src/im/hikaru/harness/client/scheduled/ScheduledTasksRepository.kt) | scheduled；见差异报告覆盖表 |
| [ScheduleEngine.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduleEngine.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledExecutionModels.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledExecutionModels.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledExecutor.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledExecutor.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledExecutorOwner.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledExecutorOwner.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledLedgerRules.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledLedgerRules.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledManagementNavigator.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledManagementPage.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementPage.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledModels.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledModels.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledPageLayout.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledPageLayout.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledRepository.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledRepository.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledSessionAccess.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledSessionAccess.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledStore.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledStore.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledTaskSummary.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledTaskSummary.kt) | scheduling；见差异报告覆盖表 |
| [ScheduledValidator.kt](../apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledValidator.kt) | scheduling；见差异报告覆盖表 |
| [AppearanceRepository.kt](../apps/shared/src/im/hikaru/harness/client/settings/AppearanceRepository.kt) | settings；见差异报告覆盖表 |
| [AppearanceSettings.kt](../apps/shared/src/im/hikaru/harness/client/settings/AppearanceSettings.kt) | settings；见差异报告覆盖表 |
| [AppearanceThemeAdapter.kt](../apps/shared/src/im/hikaru/harness/client/settings/AppearanceThemeAdapter.kt) | settings；见差异报告覆盖表 |
| [GeneralSettings.kt](../apps/shared/src/im/hikaru/harness/client/settings/GeneralSettings.kt) | settings；见差异报告覆盖表 |
| [GeneralSettingsRepository.kt](../apps/shared/src/im/hikaru/harness/client/settings/GeneralSettingsRepository.kt) | settings；见差异报告覆盖表 |
| [ModelSettings.kt](../apps/shared/src/im/hikaru/harness/client/settings/ModelSettings.kt) | settings；见差异报告覆盖表 |
| [ModelSettingsRepository.kt](../apps/shared/src/im/hikaru/harness/client/settings/ModelSettingsRepository.kt) | settings；见差异报告覆盖表 |
| [ReasoningValidation.kt](../apps/shared/src/im/hikaru/harness/client/settings/ReasoningValidation.kt) | settings；见差异报告覆盖表 |
| [Shortcuts.kt](../apps/shared/src/im/hikaru/harness/client/settings/Shortcuts.kt) | settings；见差异报告覆盖表 |
| [ShortcutsKeyEvents.kt](../apps/shared/src/im/hikaru/harness/client/settings/ShortcutsKeyEvents.kt) | settings；见差异报告覆盖表 |
| [ShortcutsRepository.kt](../apps/shared/src/im/hikaru/harness/client/settings/ShortcutsRepository.kt) | settings；见差异报告覆盖表 |
| [WorktreeSettings.kt](../apps/shared/src/im/hikaru/harness/client/settings/WorktreeSettings.kt) | settings；见差异报告覆盖表 |
| [WorktreeSettingsRepository.kt](../apps/shared/src/im/hikaru/harness/client/settings/WorktreeSettingsRepository.kt) | settings；见差异报告覆盖表 |
| [AppShell.kt](../apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt) | shell；见差异报告覆盖表 |
| [AppShellState.kt](../apps/shared/src/im/hikaru/harness/client/shell/AppShellState.kt) | shell；见差异报告覆盖表 |
| [ConversationContextMenu.kt](../apps/shared/src/im/hikaru/harness/client/shell/ConversationContextMenu.kt) | shell；见差异报告覆盖表 |
| [HarnessMainRegion.kt](../apps/shared/src/im/hikaru/harness/client/shell/HarnessMainRegion.kt) | shell；见差异报告覆盖表 |
| [HarnessTabStrip.kt](../apps/shared/src/im/hikaru/harness/client/shell/HarnessTabStrip.kt) | shell；见差异报告覆盖表 |
| [HoverCapability.kt](../apps/shared/src/im/hikaru/harness/client/shell/HoverCapability.kt) | S-02 平台默认悬停能力；JVM 为 true，Android/iOS 为 false，混合输入设备运行态尚未验证 |
| [HoverPopoverState.kt](../apps/shared/src/im/hikaru/harness/client/shell/HoverPopoverState.kt) | shell；见差异报告覆盖表 |
| [NavigationState.kt](../apps/shared/src/im/hikaru/harness/client/shell/NavigationState.kt) | shell；见差异报告覆盖表 |
| [NavigatorLayout.kt](../apps/shared/src/im/hikaru/harness/client/shell/NavigatorLayout.kt) | shell；见差异报告覆盖表 |
| [PageShellSpec.kt](../apps/shared/src/im/hikaru/harness/client/shell/PageShellSpec.kt) | shell；见差异报告覆盖表 |
| [ProjectsState.kt](../apps/shared/src/im/hikaru/harness/client/shell/ProjectsState.kt) | shell；见差异报告覆盖表 |
| [RailDestinations.kt](../apps/shared/src/im/hikaru/harness/client/shell/RailDestinations.kt) | shell；见差异报告覆盖表 |
| [SettingsNavigatorModel.kt](../apps/shared/src/im/hikaru/harness/client/shell/SettingsNavigatorModel.kt) | shell；见差异报告覆盖表 |
| [ShellLayoutRepository.kt](../apps/shared/src/im/hikaru/harness/client/shell/ShellLayoutRepository.kt) | shell；见差异报告覆盖表 |
| [ShellLayoutSettings.kt](../apps/shared/src/im/hikaru/harness/client/shell/ShellLayoutSettings.kt) | shell；见差异报告覆盖表 |
| [ThreadSummaryActivity.kt](../apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivity.kt) | summary；见差异报告覆盖表 |
| [ThreadSummaryActivityController.kt](../apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryActivityController.kt) | summary；见差异报告覆盖表 |
| [ThreadSummaryModels.kt](../apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryModels.kt) | summary；见差异报告覆盖表 |
| [ThreadSummaryPanel.kt](../apps/shared/src/im/hikaru/harness/client/summary/ThreadSummaryPanel.kt) | summary；见差异报告覆盖表 |
| [ThreadSummarySection.kt](../apps/shared/src/im/hikaru/harness/client/summary/ThreadSummarySection.kt) | summary；见差异报告覆盖表 |

## S-02 平台适配增量

下列文件是此次新增的默认能力声明，不表示已对各平台混合输入设备完成运行态验收。

| 文件 | 默认能力 |
|---|---|
| [HoverCapability.jvm.kt](../apps/shared/src@jvm/im/hikaru/harness/client/shell/HoverCapability.jvm.kt) | 支持悬停 |
| [HoverCapability.android.kt](../apps/shared/src@android/im/hikaru/harness/client/shell/HoverCapability.android.kt) | 触屏默认，固定按钮始终显示 |
| [HoverCapability.ios.kt](../apps/shared/src@ios/im/hikaru/harness/client/shell/HoverCapability.ios.kt) | 触屏默认，固定按钮始终显示 |

## modules/ui-foundation/src/im/hikaru/harness/ui/foundation

源码文件数：24。

| 文件 | 对照状态归属 |
|---|---|
| [BrowserIcons.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/BrowserIcons.kt) | foundation；见差异报告覆盖表 |
| [CodexMark.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/CodexMark.kt) | foundation；见差异报告覆盖表 |
| [HarnessActionControls.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessActionControls.kt) | foundation；见差异报告覆盖表 |
| [HarnessAnchoredOverlay.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessAnchoredOverlay.kt) | foundation；见差异报告覆盖表 |
| [HarnessAvatar.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessAvatar.kt) | foundation；见差异报告覆盖表 |
| [HarnessCollections.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessCollections.kt) | foundation；见差异报告覆盖表 |
| [HarnessComponentModels.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComponentModels.kt) | foundation；见差异报告覆盖表 |
| [HarnessComponents.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComponents.kt) | foundation；见差异报告覆盖表 |
| [HarnessComposerControls.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComposerControls.kt) | foundation；见差异报告覆盖表 |
| [HarnessConfirmDialog.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessConfirmDialog.kt) | foundation；见差异报告覆盖表 |
| [HarnessContextMenu.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessContextMenu.kt) | foundation；见差异报告覆盖表 |
| [HarnessDiscreteSlider.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessDiscreteSlider.kt) | foundation；见差异报告覆盖表 |
| [HarnessFeedback.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessFeedback.kt) | foundation；见差异报告覆盖表 |
| [HarnessIcons.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessIcons.kt) | foundation；见差异报告覆盖表 |
| [HarnessOverlays.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessOverlays.kt) | foundation；见差异报告覆盖表 |
| [HarnessResizeHandle.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessResizeHandle.kt) | foundation；见差异报告覆盖表 |
| [HarnessScrollbar.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessScrollbar.kt) | foundation；见差异报告覆盖表 |
| [HarnessSearchField.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessSearchField.kt) | foundation；见差异报告覆盖表 |
| [HarnessTheme.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTheme.kt) | foundation；见差异报告覆盖表 |
| [HarnessThemeCustomization.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessThemeCustomization.kt) | foundation；见差异报告覆盖表 |
| [HarnessToast.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessToast.kt) | foundation；见差异报告覆盖表 |
| [HarnessTokens.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt) | foundation；见差异报告覆盖表 |
| [HarnessTooltip.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTooltip.kt) | foundation；见差异报告覆盖表 |
| [SettingsNavIcons.kt](../modules/ui-foundation/src/im/hikaru/harness/ui/foundation/SettingsNavIcons.kt) | foundation；见差异报告覆盖表 |

## apps/jvm-app/src

源码文件数：40（含 S-01 新增的桌面搜索适配器）。

| 文件 | 对照状态归属 |
|---|---|
| [DesktopShutdown.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/DesktopShutdown.kt) | desktop；见差异报告覆盖表 |
| [DesktopHostRelay.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/DesktopHostRelay.kt) | connection；见差异报告覆盖表 |
| [HostEventStream.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/HostEventStream.kt) | connection；见差异报告覆盖表 |
| [HostRelayConnection.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/HostRelayConnection.kt) | connection；见差异报告覆盖表 |
| [HostStreamSubscriptions.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/HostStreamSubscriptions.kt) | connection；见差异报告覆盖表 |
| [LocalConnection.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt) | connection；见差异报告覆盖表 |
| [LocalSessionEventSource.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionEventSource.kt) | connection；见差异报告覆盖表 |
| [LocalSessionTerminals.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionTerminals.kt) | connection；见差异报告覆盖表 |
| [ReasoningOptionsMapping.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/ReasoningOptionsMapping.kt) | connection；见差异报告覆盖表 |
| [SessionBackgroundProcessesMapping.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/SessionBackgroundProcessesMapping.kt) | connection；见差异报告覆盖表 |
| [SessionBackgroundProcessesRelay.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/SessionBackgroundProcessesRelay.kt) | connection；见差异报告覆盖表 |
| [SessionDescendantsMapping.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/SessionDescendantsMapping.kt) | connection；见差异报告覆盖表 |
| [SessionDescendantsRelay.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/connection/SessionDescendantsRelay.kt) | connection；见差异报告覆盖表 |
| [DesktopGitBackend.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt) | git；见差异报告覆盖表 |
| [DesktopManagedGitBackend.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedGitBackend.kt) | git；见差异报告覆盖表 |
| [DesktopManagedWorktreeRepository.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt) | git；见差异报告覆盖表 |
| [DesktopManagedWorktreeStore.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeStore.kt) | git；见差异报告覆盖表 |
| [DesktopWorktreeChatArchiveGate.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeChatArchiveGate.kt) | git；见差异报告覆盖表 |
| [DesktopWorktreeChatArchiveStore.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeChatArchiveStore.kt) | git；见差异报告覆盖表 |
| [DesktopWorktreeChatRestoreService.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeChatRestoreService.kt) | git；见差异报告覆盖表 |
| [DesktopWorktreePinStore.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreePinStore.kt) | git；见差异报告覆盖表 |
| [DesktopWorktreeSessionObserver.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt) | git；见差异报告覆盖表 |
| [GitParsers.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/git/GitParsers.kt) | git；见差异报告覆盖表 |
| [DesktopInstalledPluginRepository.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepository.kt) | plugins；见差异报告覆盖表 |
| [DesktopLocalProjectRepository.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopLocalProjectRepository.kt) | projects；见差异报告覆盖表 |
| [DesktopLocalProjectStore.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopLocalProjectStore.kt) | projects；见差异报告覆盖表 |
| [DesktopProjectFileSearch.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopProjectFileSearch.kt) | projects；S-01 本地项目文件访问 |
| [DesktopProjectDirectoryAccess.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopProjectDirectoryAccess.kt) | projects；见差异报告覆盖表 |
| [DesktopScheduleCalendar.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduleCalendar.kt) | scheduling；见差异报告覆盖表 |
| [DesktopScheduledExecution.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecution.kt) | scheduling；见差异报告覆盖表 |
| [DesktopScheduledLeaderLease.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledLeaderLease.kt) | scheduling；见差异报告覆盖表 |
| [DesktopScheduledSessionEvidence.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidence.kt) | scheduling；见差异报告覆盖表 |
| [DesktopScheduledStore.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledStore.kt) | scheduling；见差异报告覆盖表 |
| [DesktopModelSettingsBackend.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/settings/DesktopModelSettingsBackend.kt) | settings；见差异报告覆盖表 |
| [DesktopTerminalLaunchProvider.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProvider.kt) | terminal；见差异报告覆盖表 |
| [DesktopWindowClose.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/window/DesktopWindowClose.kt) | window；见差异报告覆盖表 |
| [DesktopWindowContent.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/window/DesktopWindowContent.kt) | window；见差异报告覆盖表 |
| [HarnessSplitMainRegion.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/window/HarnessSplitMainRegion.kt) | window；见差异报告覆盖表 |
| [HarnessTabStrip.kt](../apps/jvm-app/src/im/hikaru/harness/desktop/window/HarnessTabStrip.kt) | window；见差异报告覆盖表 |
| [main.kt](../apps/jvm-app/src/main.kt) | src；见差异报告覆盖表 |

## apps/android-app/src

源码文件数：2。

| 文件 | 对照状态归属 |
|---|---|
| [HarnessApplication.kt](../apps/android-app/src/im/hikaru/harness/HarnessApplication.kt) | harness；见差异报告覆盖表 |
| [MainActivity.kt](../apps/android-app/src/im/hikaru/harness/MainActivity.kt) | harness；见差异报告覆盖表 |

## apps/ios-app/src

源码文件数：2。

| 文件 | 对照状态归属 |
|---|---|
| [ViewController.kt](../apps/ios-app/src/ViewController.kt) | src；见差异报告覆盖表 |
| [iosApp.swift](../apps/ios-app/src/iosApp.swift) | src；见差异报告覆盖表 |

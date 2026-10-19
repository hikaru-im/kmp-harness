package im.hikaru.harness.client.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.client.account.AccountWorkspacePage
import im.hikaru.harness.client.account.AccountException
import im.hikaru.harness.client.account.AccountState
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.AgentOptions
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.CreateSessionOptions
import im.hikaru.harness.client.conversation.ConversationController
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.client.conversation.CompletionCapability
import im.hikaru.harness.client.panels.ThreadPanelScope
import im.hikaru.harness.client.panels.ThreadPanelTab
import im.hikaru.harness.client.panels.ThreadPanelAction
import im.hikaru.harness.client.panels.ThreadPanelsController
import im.hikaru.harness.client.panels.ThreadPanelsOwner
import im.hikaru.harness.client.panels.ConnectionThreadPanelsRepository
import im.hikaru.harness.client.panels.PanelLoad
import im.hikaru.harness.client.panels.ThreadPanels
import im.hikaru.harness.client.panels.ThreadPanelWidthMode
import im.hikaru.harness.client.plugins.InstalledPluginRepository
import im.hikaru.harness.client.plugins.InstalledPluginsController
import im.hikaru.harness.client.plugins.InstalledPluginsState
import im.hikaru.harness.client.plugins.InstalledPluginsAction
import im.hikaru.harness.client.plugins.InstalledPluginsPage
import im.hikaru.harness.client.plugins.InstalledPluginDetailPage
import im.hikaru.harness.client.plugins.PluginsManagementNavigator
import im.hikaru.harness.client.plugins.PluginScope
import im.hikaru.harness.client.plugins.PluginAccountId
import im.hikaru.harness.client.plugins.PluginId
import im.hikaru.harness.client.plugins.PluginAccessBinding
import im.hikaru.harness.client.plugins.LocalPluginManagementRepository
import im.hikaru.harness.client.plugins.UnavailablePluginRepository
import im.hikaru.harness.client.git.GitAccess
import im.hikaru.harness.client.git.GitBackend
import im.hikaru.harness.client.git.GitBinding
import im.hikaru.harness.client.git.GitController
import im.hikaru.harness.client.git.GitDiffRequest
import im.hikaru.harness.client.git.GitDiffSnapshot
import im.hikaru.harness.client.git.GitFailure
import im.hikaru.harness.client.git.GitFailureKind
import im.hikaru.harness.client.git.GitReadException
import im.hikaru.harness.client.git.GitRead
import im.hikaru.harness.client.git.GitRepository
import im.hikaru.harness.client.git.GitStatusSnapshot
import im.hikaru.harness.client.git.GitState
import im.hikaru.harness.client.git.GitBranch
import im.hikaru.harness.client.git.GitWorktree
import im.hikaru.harness.client.git.ManagedWorktreeChatArchiveReceipt
import im.hikaru.harness.client.git.SettingsGitPage
import im.hikaru.harness.client.git.WorktreesPage
import im.hikaru.harness.client.summary.ThreadSummaryPanel
import im.hikaru.harness.client.summary.ThreadSummaryActivity
import im.hikaru.harness.client.summary.ThreadSummaryActivityController
import im.hikaru.harness.client.summary.ThreadSummaryActivityKind
import im.hikaru.harness.client.summary.threadSummaryEnvironment
import im.hikaru.harness.client.summary.threadSummaryScheduledTasks
import im.hikaru.harness.client.page.SettingsFormFrame
import im.hikaru.harness.ui.slot.SlotOutlet
import im.hikaru.harness.ui.slot.UiSnapshot
import im.hikaru.harness.ui.slot.UiSnapshotProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.connection.HistoryRecovery
import im.hikaru.harness.client.connection.RemoteConnectionOwner
import im.hikaru.harness.client.connection.ConnectionDescriptionRepository
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.ConnectionsPhase
import im.hikaru.harness.client.connection.ConnectionsState
import im.hikaru.harness.client.connection.RemoteConnectionStatus
import im.hikaru.harness.client.connection.RemoteDiscoveryState
import im.hikaru.harness.client.page.SettingsConnectionsPage
import im.hikaru.harness.client.shell.AppShell
import im.hikaru.harness.client.shell.ConversationNavigationActions
import im.hikaru.harness.client.shell.LocalConversationNavigationActions
import im.hikaru.harness.client.conversation.ConversationCommand
import kotlinx.coroutines.async
import im.hikaru.harness.client.shell.AppShellState
import im.hikaru.harness.client.shell.AccountMenuAction
import im.hikaru.harness.client.shell.AccountMenuDestination
import im.hikaru.harness.client.shell.AccountMenuState
import im.hikaru.harness.client.shell.ConnectionIndicatorState
import im.hikaru.harness.client.shell.SidebarState
import im.hikaru.harness.client.shell.SidebarMode
import im.hikaru.harness.client.page.SettingsGeneralPage
import im.hikaru.harness.client.page.SettingsAppearancePage
import im.hikaru.harness.client.page.SettingsModelsPage
import im.hikaru.harness.client.page.SettingsShortcutsPage
import im.hikaru.harness.client.settings.ShortcutsRepository
import im.hikaru.harness.client.settings.ShortcutCommand
import im.hikaru.harness.client.settings.LocalShortcutsRepository
import im.hikaru.harness.client.settings.LocalShortcutsEventRoot
import im.hikaru.harness.client.settings.toShortcutInput
import im.hikaru.harness.client.settings.ModelSettingsRepository
import im.hikaru.harness.client.settings.GeneralSettingsRepository
import im.hikaru.harness.client.settings.WorktreeSettings
import im.hikaru.harness.client.settings.WorktreeSettingsRepository
import im.hikaru.harness.client.settings.WorktreeSettingsState
import im.hikaru.harness.client.settings.GeneralSettingsState
import im.hikaru.harness.client.settings.CloseConfirmationPolicy
import im.hikaru.harness.client.settings.ModelSettingsState
import im.hikaru.harness.client.settings.ProviderConfiguration
import im.hikaru.harness.client.settings.AppearanceRepository
import im.hikaru.harness.client.settings.AppearanceSettings
import im.hikaru.harness.client.settings.AppearanceState
import im.hikaru.harness.client.settings.AppearanceMode
import im.hikaru.harness.client.settings.MemoryAppearanceStore
import im.hikaru.harness.client.settings.themeCustomization
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.client.scheduled.ScheduledTasksState
import im.hikaru.harness.client.scheduling.ScheduleCalendar
import im.hikaru.harness.client.scheduling.ScheduleDate
import im.hikaru.harness.client.scheduling.ScheduleEngine
import im.hikaru.harness.client.scheduling.ScheduledExecutor
import im.hikaru.harness.client.scheduling.ScheduledDestination
import im.hikaru.harness.client.scheduling.ScheduledLoadPhase
import im.hikaru.harness.client.scheduling.ScheduledManagementNavigator
import im.hikaru.harness.client.scheduling.ScheduledManagementPage
import im.hikaru.harness.client.scheduling.ScheduledManagementState
import im.hikaru.harness.client.scheduling.ScheduledModelRef
import im.hikaru.harness.client.scheduling.SystemScheduledClock
import im.hikaru.harness.client.scheduling.ScheduledRepository
import im.hikaru.harness.client.scheduling.ScheduledScope
import im.hikaru.harness.client.scheduling.ScheduledStore
import im.hikaru.harness.client.scheduling.ScheduledTarget
import im.hikaru.harness.client.scheduling.ScheduledTaskSort
import im.hikaru.harness.client.scheduling.ScheduledTaskStatus
import im.hikaru.harness.client.scheduling.ScheduledValidationContext
import im.hikaru.harness.client.scheduling.UnavailableScheduledStore
import im.hikaru.harness.client.shell.HarnessChangesPage
import im.hikaru.harness.client.shell.LocalHarnessPaneTab
import im.hikaru.harness.client.shell.LocalHarnessTabStrip
import im.hikaru.harness.client.shell.NavigationState
import im.hikaru.harness.client.shell.NavigatorPreferredWidth
import im.hikaru.harness.client.shell.OverlayId
import im.hikaru.harness.client.shell.PageId
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.client.page.LocalProjectStatus
import im.hikaru.harness.client.page.LocalProjectsPage
import im.hikaru.harness.client.page.NewTabPage
import im.hikaru.harness.client.page.NewTabTool
import im.hikaru.harness.client.page.LocalProjectDetailPage
import im.hikaru.harness.client.page.LocalProjectPicker
import im.hikaru.harness.client.page.localProjectsForBinding
import im.hikaru.harness.client.page.LocalProjectEditorDialog
import im.hikaru.harness.client.page.message
import im.hikaru.harness.client.shell.WorkspaceProjectNode
import im.hikaru.harness.client.shell.WorkspaceItemNode
import im.hikaru.harness.client.shell.RecentTaskItem
import im.hikaru.harness.client.shell.ExploreMenuState
import im.hikaru.harness.client.shell.RailDestination
import im.hikaru.harness.client.shell.ThreadSurfaceState
import im.hikaru.harness.client.shell.WorkspaceNavigatorState
import im.hikaru.harness.client.shell.NavigatorKind
import im.hikaru.harness.client.shell.NavigatorLayout
import im.hikaru.harness.client.shell.PluginsNavigatorState
import im.hikaru.harness.client.shell.toPageId
import im.hikaru.harness.client.shell.navigatorSlug
import im.hikaru.harness.client.shell.SettingsNavigatorModel
import im.hikaru.harness.client.shell.SettingsSection
import im.hikaru.harness.client.shell.SettingsNavigatorState
import im.hikaru.harness.client.shell.pageShellSpec
import im.hikaru.harness.client.shell.mountsNavigator
import im.hikaru.harness.client.shell.navigatorLayout
import im.hikaru.harness.client.shell.toggled
import im.hikaru.harness.client.shell.toShellDestination
import im.hikaru.harness.client.shell.railInitials
import im.hikaru.harness.client.shell.harnessRailItems
import im.hikaru.harness.client.shell.railDestinationPage
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessBadge
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The first application shell shared by Desktop, Android and iOS. Assembly only: it creates the
 * navigation state, connection state and shell regions, then dispatches content on [PageId].
 *
 * Production routes consume scoped Connection services. Reference fixtures belong to previews.
 */
@Composable
public fun HarnessApp(
    connection: Connection?,
    connectionChanges: Flow<Connection?> = flowOf<Connection?>(null),
    account: MemberAccount? = null,
    defaultTarget: BackendTenant? = null,
    availableHosts: List<HostDescription> = emptyList(),
    onSelectHost: ((HostDescription) -> Unit)? = null,
    sessionEvents: Flow<RelayEvent> = emptyFlow(),
    historyRecoveries: Flow<HistoryRecovery> = emptyFlow(),
    onSubscribeSession: (suspend (SessionId) -> Unit)? = null,
    onUnsubscribeSession: (suspend (SessionId) -> Unit)? = null,
    uiSnapshot: UiSnapshot = UiSnapshot.Empty,
    initialDestination: HarnessDestination = HarnessDestination.Harness,
    initialAccountMenuOpen: Boolean = false,
    themeMode: HarnessThemeMode = HarnessThemeMode.System,
    appearanceRepository: AppearanceRepository? = null,
    modelSettingsRepository: ModelSettingsRepository? = null,
    generalSettingsRepository: GeneralSettingsRepository? = null,
    worktreeSettingsRepository: WorktreeSettingsRepository? = null,
    shortcutsRepository: ShortcutsRepository? = null,
    installedPluginRepository: InstalledPluginRepository = UnavailablePluginRepository,
    remoteStatus: RemoteConnectionStatus? = null,
    remoteSelectedHost: HostDescription? = null,
    remoteDiscovery: RemoteDiscoveryState = RemoteDiscoveryState(),
    onRefreshHosts: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
    connectionTransport: ConnectionTransport? = null,
    gitBackend: GitBackend? = null,
    gitInitialCwd: String? = null,
    scheduledStore: ScheduledStore = UnavailableScheduledStore(),
    scheduleCalendar: ScheduleCalendar? = null,
    scheduledExecutor: ScheduledExecutor? = null,
    scheduledExecutorConnection: Connection? = null,
    scheduledExecutorOwner: im.hikaru.harness.client.scheduling.ScheduledExecutorOwner? = null,
    scheduledExecutionRecoveryEnabled: Boolean = true,
    localProjectRepository: LocalProjectRepository = UnavailableLocalProjectRepository,
    managedWorktreeRepository: im.hikaru.harness.client.git.ManagedWorktreeRepository? = null,
    terminalLaunchProvider: im.hikaru.harness.client.panels.TerminalLaunchProvider =
        im.hikaru.harness.client.panels.UnavailableTerminalLaunchProvider,
) {
    val appearance = appearanceRepository ?: remember {
        val initial = AppearanceSettings(mode = AppearanceMode.from(themeMode))
        AppearanceRepository(MemoryAppearanceStore(initial), initial)
    }
    val appearanceState by appearance.state.collectAsState()
    val appearanceScope = rememberCoroutineScope()
    LaunchedEffect(appearance) { if (!appearance.state.value.loaded) appearance.load() }
    val models = modelSettingsRepository ?: remember { ModelSettingsRepository() }
    val modelSettings by models.state.collectAsState()
    val modelScope = rememberCoroutineScope()
    LaunchedEffect(models) { if (!models.state.value.loaded) models.load() }
    val general = generalSettingsRepository ?: remember { GeneralSettingsRepository() }
    val generalState by general.state.collectAsState()
    val generalScope = rememberCoroutineScope()
    LaunchedEffect(general) { if (!general.state.value.loaded) general.load() }
    val worktreeSettings = worktreeSettingsRepository ?: remember { WorktreeSettingsRepository() }
    val worktreeSettingsState by worktreeSettings.state.collectAsState()
    val worktreeSettingsScope = rememberCoroutineScope()
    LaunchedEffect(worktreeSettings) { if (!worktreeSettings.state.value.loaded) worktreeSettings.load() }
    val inheritedShortcuts = LocalShortcutsRepository.current
    require(shortcutsRepository == null || inheritedShortcuts == null || shortcutsRepository === inheritedShortcuts) {
        "The app and window must share one ShortcutsRepository"
    }
    val shortcuts = shortcutsRepository ?: inheritedShortcuts ?: remember { ShortcutsRepository() }
    LaunchedEffect(shortcuts) { if (!shortcuts.state.value.loaded) shortcuts.load() }
    val ownsShortcutRoot = LocalShortcutsEventRoot.current !== shortcuts.input
    var navigation by remember { mutableStateOf(NavigationState(currentPage = initialDestination.toPageId())) }
    var workspaceState by remember { mutableStateOf(WorkspaceNavigatorState()) }
    // Page-owned navigators. They are separate from [workspaceState] so a Settings/Plugins/Hosts
    // page never reuses the Workspace search, project tree or recent-task state.
    var settingsNavigator by remember { mutableStateOf(SettingsNavigatorState()) }
    var pluginsNavigator by remember { mutableStateOf(PluginsNavigatorState()) }
    // The collapse mode itself is not page-owned: Codex keeps one navigator mode for the whole app, so
    // collapsing the navigator on one page leaves it collapsed on every other page that owns one, and a
    // page with no navigator ([NavigatorKind.None]) has nothing to collapse. Each navigator keeps its
    // own content (search, filters, selection) — only the mode is shared, written into every navigator
    // state below.
    var navigatorMode by remember { mutableStateOf(SidebarMode.Expanded) }
    // The navigator's status filter is UI state (like navigator mode); the page owns the real tasks.
    var scheduledStatusFilter by remember { mutableStateOf<ScheduledTaskStatus?>(null) }
    var scheduledSort by remember { mutableStateOf(ScheduledTaskSort.Automatic) }
    // The Workspace header search action reveals its inline search row; the sidebar keeps no permanent field.
    var navigatorSearchOpen by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(AccountMenuState(isOpen = initialAccountMenuOpen)) }
    // The 探索 pin menu is a separate menu from the account chip dropdown.
    var exploreMenu by remember { mutableStateOf(ExploreMenuState()) }
    // Destinations the user pinned from 探索, rendered in the rail's second group.
    var pinnedRailDestinations by remember { mutableStateOf(emptySet<RailDestination>()) }
    // The thread summary island is separate from the tabbed context panel; it survives tab changes
    // but is only drawn on a Thread page.
    var summaryPanelOpen by remember { mutableStateOf(false) }
    // The Workspace Navigator is user-resizable; the drag writes this preference and the shell
    // clamps it (navigatorResizeWidth) before laying out (navigatorWidth).
    var navigatorWidth by remember { mutableStateOf(NavigatorPreferredWidth) }
    var pendingPage by remember { mutableStateOf<PageId?>(null) }
    val descriptionRepository = remember(connection) { connection?.let(::ConnectionDescriptionRepository) }
    // A new connection must start with its own unverified description, never the prior collector value.
    val descriptionState = key(descriptionRepository) {
        descriptionRepository?.state?.collectAsState()?.value
    }
    val accountState = account?.state?.collectAsState()?.value ?: AccountState()
    val accountMenuScope = rememberCoroutineScope()
    LaunchedEffect(account, defaultTarget) {
        if (account != null && defaultTarget != null && account.state.value.target == null) {
            try {
                account.selectTarget(defaultTarget)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The tenant screen remains available for an explicit retry.
            }
        }
    }
    LaunchedEffect(descriptionRepository) { descriptionRepository?.refresh() }
    val transport = connectionTransport ?:
        if (account == null && remoteStatus == null) ConnectionTransport.Local else ConnectionTransport.Relay
    val phase = when {
        transport == ConnectionTransport.Relay && accountState.session == null -> ConnectionsPhase.RequiresLogin
        transport == ConnectionTransport.Relay && remoteStatus != null && remoteStatus != RemoteConnectionStatus.ONLINE -> when (remoteStatus) {
            RemoteConnectionStatus.DISCOVERING -> ConnectionsPhase.Discovering
            RemoteConnectionStatus.CONNECTING -> ConnectionsPhase.Connecting
            RemoteConnectionStatus.RECONNECTING -> ConnectionsPhase.Reconnecting
            RemoteConnectionStatus.DISCONNECTED -> ConnectionsPhase.Disconnected
            else -> ConnectionsPhase.Idle
        }
        else -> descriptionState?.phase ?: ConnectionsPhase.Idle
    }
    val host = descriptionState?.host?.takeIf { phase == ConnectionsPhase.Online || phase == ConnectionsPhase.Incompatible }
    val connectionState = when (phase) {
        ConnectionsPhase.Online -> if (transport == ConnectionTransport.Local) HarnessConnectionState.Local else HarnessConnectionState.Online
        ConnectionsPhase.Discovering, ConnectionsPhase.Connecting, ConnectionsPhase.Reconnecting -> HarnessConnectionState.Connecting
        ConnectionsPhase.RequiresLogin -> HarnessConnectionState.RequiresLogin
        ConnectionsPhase.Disconnected -> HarnessConnectionState.Disconnected
        ConnectionsPhase.Incompatible -> HarnessConnectionState.ProtocolIncompatible
        ConnectionsPhase.Idle -> HarnessConnectionState.NoHost
    }
    var connectionActionError by remember(connection, accountState.session) { mutableStateOf<String?>(null) }
    val currentSession by rememberUpdatedState(accountState.session)
    val connectionsScope = rememberCoroutineScope()
    val connectionsState = ConnectionsState(
        scope = accountState.session?.identity,
        transport = transport, phase = phase, currentHost = host,
        hosts = if (transport == ConnectionTransport.Local) listOfNotNull(host) else availableHosts,
        refreshing = descriptionState?.refreshing == true || remoteDiscovery.refreshing,
        error = connectionActionError ?: remoteDiscovery.error ?: descriptionState?.error,
        refreshUnavailableReason = when {
            transport == ConnectionTransport.Local && connection != null -> null
            transport == ConnectionTransport.Relay && accountState.session == null -> "请先登录后发现远程 Host。"
            transport == ConnectionTransport.Relay && onRefreshHosts != null -> null
            else -> "当前没有可刷新的连接服务。"
        },
        selectionUnavailableReason = when {
            transport == ConnectionTransport.Local -> "本机模式不支持切换远程 Host。"
            accountState.session == null -> "请先登录后连接远程 Host。"
            onSelectHost == null -> "当前未接入远程 Host 连接服务。"
            else -> null
        },
    )
    val refreshConnections: () -> Unit = {
        if (connectionsState.canRefresh) {
            val capturedSession = accountState.session
            connectionsScope.launch {
                connectionActionError = null
                try {
                    if (transport == ConnectionTransport.Relay) onRefreshHosts?.invoke() else descriptionRepository?.refresh()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (currentSession == capturedSession) connectionActionError = "无法刷新连接，请重试。"
                }
            }
        }
    }
    val selectConnection: (HostDescription) -> Unit = { candidate ->
        if (connectionsState.canSelect) onSelectHost?.invoke(candidate)
    }
    val page = navigation.currentPage
    val conversationScope = rememberCoroutineScope()
    val conversation = remember { ConversationController(conversationScope) }
    val conversationState by conversation.state.collectAsState()
    val generationCounter = remember { longArrayOf(0L) }
    val conversationIdentity = accountState.session?.identity.takeIf { transport == ConnectionTransport.Relay }
    val connectionGeneration = remember(connection, transport, conversationIdentity) { ++generationCounter[0] }
    var lastLocalHost by remember(transport) { mutableStateOf<HostDescription?>(null) }
    LaunchedEffect(host, transport) {
        if (transport == ConnectionTransport.Local && host != null) lastLocalHost = host
    }
    val selectedConversationHost = when (transport) {
        ConnectionTransport.Local -> host ?: lastLocalHost
        ConnectionTransport.Relay -> if (accountState.session != null) remoteSelectedHost ?: host else null
    }
    val conversationHost = selectedConversationHost?.let {
        ConversationHostScope(it.hostId, connectionGeneration, conversationIdentity)
    }
    DisposableEffect(conversation) { onDispose { conversation.close() } }
    val verifiedConversationConnection = connection.takeIf {
        phase == ConnectionsPhase.Online && host?.hostId == conversationHost?.hostId
    }
    // A window's tabs belong to the **side panel**, and every one of them is a tool page — 新标签页,
    // 变更, and the two the reference also lists but this client cannot draw. None of them is a 会话, so
    // there is exactly one conversation in a window whatever the layout: the one `conversation` holds, in
    // the pane the shell draws its own page into. That is why the window no longer needs a second
    // controller for the other half.
    val paneStrip = LocalHarnessTabStrip.current
    val localProjects = remember(conversationScope, localProjectRepository) {
        LocalProjectsController(conversationScope, localProjectRepository)
    }
    val projectBinding = verifiedConversationConnection?.let { verified ->
        conversationHost?.let { scoped -> LocalProjectBinding(verified,
            LocalProjectScope(scoped.hostId, scoped.generation), transport) }
    }
    val observedProjects = localProjects.state.collectAsState().value
    val localProjectsState = localProjectsForBinding(observedProjects, projectBinding)
    DisposableEffect(localProjects) { onDispose { localProjects.close() } }
    LaunchedEffect(localProjects, projectBinding) { localProjects.bind(projectBinding) }
    var projectEditorOpen by remember(projectBinding) { mutableStateOf(false) }
    var editingProject by remember(projectBinding) { mutableStateOf<LocalProject?>(null) }
    var projectPreparationError by remember(projectBinding) { mutableStateOf<String?>(null) }
    // `settings.worktrees.row.newChat`: a working directory bound for the next Home creation. It only
    // records the pending directory; nothing is created or written until the user sends a message.
    var pendingWorktreeChat by remember(conversationHost?.hostId, conversationHost?.identity, transport) {
        mutableStateOf<String?>(null)
    }
    // A temporary disconnect changes binding/generation but must keep the unsent association guard.
    var pendingProjectAssociation by remember(conversationHost?.hostId, conversationHost?.identity, transport) {
        mutableStateOf<Pair<ValidatedLocalProjectTarget, im.hikaru.harness.client.connection.SessionSummary>?>(null)
    }
    var retryingProjectAssociation by remember(conversationHost?.hostId, conversationHost?.identity, transport) { mutableStateOf(false) }
    suspend fun attachProjectSession(target: ValidatedLocalProjectTarget, summary: im.hikaru.harness.client.connection.SessionSummary): Boolean {
        val job = localProjects.attachCreatedSession(target, summary) ?: return false
        try {
            job.join()
        } catch (cancelled: CancellationException) {
            job.cancel()
            withContext(NonCancellable) { job.join() }
            throw cancelled
        }
        val current = localProjects.state.value
        val attached = localProjects.isBoundTo(target.binding) && current.mutationFailure == null &&
            current.snapshot?.memberships?.any { it.projectId == target.projectId && it.session == summary } == true
        if (attached && pendingProjectAssociation?.let { it.first.projectId == target.projectId && it.second == summary } == true)
            pendingProjectAssociation = null
        return attached
    }
    val saveProject: (CreateLocalProject) -> Unit = { request ->
        val capturedBinding = projectBinding
        val job = localProjects.create(request)
        if (job != null) conversationScope.launch {
            job.join()
            if (capturedBinding != null && localProjects.isBoundTo(capturedBinding)) {
                localProjects.state.value.snapshot?.createReceipts?.firstOrNull { it.request == request }?.let { receipt ->
                    localProjects.select(receipt.projectId)
                    projectEditorOpen = false
                }
            }
        }
    }
    val gitScope = rememberCoroutineScope()
    val effectiveGitBackend = gitBackend ?: UnavailableAppGitBackend
    val git = remember(gitScope, effectiveGitBackend) { GitController(gitScope, effectiveGitBackend) }
    val observedGitState = key(git) { git.state.collectAsState().value }
    DisposableEffect(git) { onDispose { git.close() } }
    val gitBinding = conversationHost?.takeIf { verifiedConversationConnection != null }?.let {
        GitBinding(
            identity = it.identity,
            hostId = it.hostId,
            generation = it.generation,
            access = if (transport == ConnectionTransport.Local && gitBackend != null) GitAccess.LocalReadOnly
                else GitAccess.RemoteUnavailable,
        )
    }
    // Do not render the old host's snapshot during the frame before the binding effect runs.
    val gitState = observedGitState.takeIf { it.binding == gitBinding } ?: GitState(binding = gitBinding)
    val managedWorktrees = remember(gitScope, managedWorktreeRepository) {
        managedWorktreeRepository?.let { im.hikaru.harness.client.git.ManagedWorktreeController(gitScope, it) }
    }
    DisposableEffect(managedWorktrees) { onDispose { managedWorktrees?.close() } }
    val managedRepository = (gitState.repository as? GitRead.Ready<GitRepository>)?.value
        ?.takeIf { it.binding == gitBinding && gitBinding?.access == GitAccess.LocalReadOnly }
    LaunchedEffect(managedWorktrees, managedRepository) { managedWorktrees?.bind(managedRepository) }
    // An applied archive/unarchive changes which chats are live. Re-read the session list so an
    // archived chat leaves Recent (and a restored one returns) without a manual refresh; a retired
    // chat that is still selected is deselected by the same call.
    val managedArchiveReceipt: ManagedWorktreeChatArchiveReceipt? = managedWorktrees?.state
        ?.collectAsState()?.value?.archiveReceipt
    LaunchedEffect(managedArchiveReceipt) {
        val applied = managedArchiveReceipt as? ManagedWorktreeChatArchiveReceipt.Applied ?: return@LaunchedEffect
        conversation.invalidateSessions(
            retired = if (applied.request.desiredArchived) SessionId(applied.request.key.id) else null,
        )
    }
    LaunchedEffect(git, gitBinding, gitInitialCwd) {
        git.bind(gitBinding)
        if (gitBinding?.access == GitAccess.LocalReadOnly && git.state.value.selectedCwd == null) {
            git.selectRepository(gitInitialCwd)
        }
    }
    val gitSessionCwds = if (gitBinding?.access == GitAccess.LocalReadOnly &&
        conversationState.host == conversationHost && conversationState.connected) {
        conversationState.sessions.mapNotNull { it.cwd?.takeIf(String::isNotBlank) }.distinct()
    } else emptyList()
    val gitCwdChoices = if (gitBinding?.access == GitAccess.LocalReadOnly) {
        (gitSessionCwds + listOfNotNull(gitInitialCwd?.takeIf(String::isNotBlank))).distinct()
    } else emptyList()
    val gitThreadCwd = (page as? PageId.Thread)?.threadId?.let { threadId ->
        if (gitBinding?.access == GitAccess.LocalReadOnly && conversationState.host == conversationHost &&
            conversationState.connected) {
            conversationState.sessions.firstOrNull { it.id.value == threadId }?.cwd?.takeIf(String::isNotBlank)
        } else null
    }
    LaunchedEffect(git, gitBinding, gitThreadCwd) {
        if (gitBinding?.access == GitAccess.LocalReadOnly && gitThreadCwd != null) {
            git.selectRepository(gitThreadCwd)
        }
    }
    // The summary island reads its own HEAD-based numbers only while it is open, and only after the
    // selected repository and status are real. It never starts IO for a closed panel.
    val summaryLoad = threadSummaryEnvironment(gitState)
    LaunchedEffect(git, summaryPanelOpen, page, gitState.repository, gitState.status) {
        if (!summaryPanelOpen || page !is PageId.Thread) return@LaunchedEffect
        if (gitState.repository is GitRead.Loading || gitState.repository is GitRead.Unselected) return@LaunchedEffect
        if (gitState.repository !is GitRead.Ready || gitState.status !is GitRead.Ready) return@LaunchedEffect
        if (gitState.summaryStats is GitRead.Ready || gitState.summaryStats is GitRead.Loading) return@LaunchedEffect
        git.refreshSummaryStats()
    }
    val pluginScope = conversationHost?.takeIf { verifiedConversationConnection != null }?.let {
        PluginScope(it.hostId, it.generation, it.identity?.let { identity -> PluginAccountId(identity.toString()) })
    }
    val pluginAccess = pluginScope?.let { scope ->
        verifiedConversationConnection?.let { PluginAccessBinding(scope, it, transport) }
    }
    val managedPlugins = installedPluginRepository as? LocalPluginManagementRepository
    val plugins = remember(installedPluginRepository) {
        InstalledPluginsController(conversationScope, installedPluginRepository)
    }
    val observedPluginsState = key(plugins) { plugins.state.collectAsState().value }
    val pluginsState = observedPluginsState.takeIf { it.scope == pluginScope }
        ?: InstalledPluginsState(scope = pluginScope)
    DisposableEffect(plugins, managedPlugins) {
        onDispose {
            managedPlugins?.bindAccess(null)
            plugins.close()
        }
    }
    LaunchedEffect(plugins, pluginAccess, page) {
        managedPlugins?.bindAccess(pluginAccess)
        plugins.bind(pluginScope)
        if (page is PageId.PluginDetail) plugins.select(PluginId(page.pluginId))
        else plugins.back()
    }
    LaunchedEffect(verifiedConversationConnection, conversationHost) {
        val previousHost = conversation.state.value.host
        val changedOwner = previousHost != null && (conversationHost == null ||
            previousHost.hostId != conversationHost.hostId || previousHost.identity != conversationHost.identity)
        conversation.bind(
            connection = verifiedConversationConnection,
            host = conversationHost,
            completionCapability = if (transport == ConnectionTransport.Local) CompletionCapability.LocalIdle
                else CompletionCapability.DeliveryAcknowledgedOnly,
        )
        if (changedOwner && navigation.currentPage is PageId.Thread) {
            navigation = navigation.copy(currentPage = PageId.Home, backStack = emptyList())
        } else {
            conversation.select((navigation.currentPage as? PageId.Thread)?.threadId?.let(::SessionId))
        }
    }
    LaunchedEffect(page) {
        when (page) {
            PageId.Home -> conversation.select(null)
            is PageId.Thread -> conversation.select(SessionId(page.threadId))
            else -> conversation.select(null)
        }
    }
    LaunchedEffect(conversationState.selectedSessionId) {
        val created = conversationState.selectedSessionId
        if (navigation.currentPage == PageId.Home && created != null) {
            navigation = navigation.navigate(PageId.Thread(created.value))
        }
    }
    // Fence projections before the asynchronous bind effect runs; keep shell IDs factual.
    val latestConversationHost by rememberUpdatedState(conversationHost)
    val latestVerifiedConversationConnection by rememberUpdatedState(verifiedConversationConnection)
    val currentConversationSessions = if (verifiedConversationConnection != null && conversationHost != null &&
        conversationState.host == conversationHost && conversationState.connected) conversationState.sessions else emptyList()
    val sessionsById = currentConversationSessions.associateBy { it.id }
    val visibleConversationSessions = currentConversationSessions.filter { !it.archived }.sortedWith(
        compareByDescending<im.hikaru.harness.client.connection.SessionSummary> { it.pinned }
            .thenByDescending { it.updatedAt }.thenBy { it.id.value },
    )
    val recentTasks = visibleConversationSessions.map { summary -> RecentTaskItem(id = summary.id.value, title = summary.title ?: summary.id.value,
        unread = summary.unread, pinned = summary.pinned) }
    val selectedSummary = conversationState.selectedSessionId?.let { sessionsById[it] }
    LaunchedEffect(conversationHost, page, conversationState.historyPhase, conversationState.history.lastOrNull()?.id, conversationState.historyReadSeq) {
        if (page is PageId.Thread && selectedSummary?.managementAvailable == true && selectedSummary.unread &&
            page.threadId == selectedSummary.id.value && conversation.state.value.host == conversationHost &&
            conversation.state.value.selectedSessionId == selectedSummary.id &&
            conversationState.historyPhase == im.hikaru.harness.client.conversation.ConversationDataPhase.Ready) {
            conversation.markRead(selectedSummary.id, conversationState.historyReadSeq)
        }
    }
    val modelChoices = if (transport == ConnectionTransport.Local && modelSettings.snapshot.available &&
        modelSettings.loadError == null && !modelSettings.loading) {
        modelSettings.snapshot.providers.filter { it.active }.flatMap { provider ->
            provider.models.map { model -> ConversationModelChoice(provider.id, model.id, provider.displayName + " / " + model.name, model.reasoningEfforts) }
        }
    } else emptyList()
    val localSchedulingAllowed = transport == ConnectionTransport.Local && verifiedConversationConnection != null
    val schedulingUnavailableReason = when {
        transport == ConnectionTransport.Relay -> "远程 Host 未提供持久化定时任务服务，本机存储不能用于 Relay。"
        verifiedConversationConnection == null -> "当前 Host 尚未通过连接验证，定时任务存储不可用。"
        scheduleCalendar == null -> "当前平台未提供定时任务日历服务，无法校验或保存任务。"
        else -> null
    }
    // The default argument creates an unavailable adapter; retain it across recompositions.
    val suppliedScheduledStore = if (scheduledStore is UnavailableScheduledStore) remember { scheduledStore }
        else scheduledStore
    val blockedScheduledStore = remember(schedulingUnavailableReason) {
        UnavailableScheduledStore(schedulingUnavailableReason ?: "定时任务存储不可用。")
    }
    val effectiveScheduledStore = if (schedulingUnavailableReason == null) suppliedScheduledStore
        else blockedScheduledStore
    val effectiveScheduleCalendar = scheduleCalendar ?: UnavailableAppScheduleCalendar
    val scheduledScope = conversationHost?.let { ScheduledScope(it.hostId, it.identity, it.generation) }
    val scheduledConnected = verifiedConversationConnection != null
    val scheduledOwnerState = scheduledExecutorOwner?.let { key(it) { it.state.collectAsState().value } }
    val suppliedScheduledExecutor = scheduledOwnerState?.executor ?: scheduledExecutor
    val executorState = suppliedScheduledExecutor?.let { key(it) { it.state.collectAsState().value } }
    val recoveryBinding = scheduledScope?.takeIf {
        localSchedulingAllowed && scheduledExecutorConnection != null &&
            scheduledExecutorConnection === verifiedConversationConnection && scheduledOwnerState?.binding == it
    }
    // This binding survives executor replacement. Losing a verified connection permanently fences
    // the factory owner, so recovery cannot restore admission through an old connection.
    DisposableEffect(scheduledExecutorOwner, recoveryBinding) {
        onDispose { if (recoveryBinding != null) scheduledExecutorOwner?.invalidate() }
    }
    val effectiveScheduledExecutor = suppliedScheduledExecutor?.takeIf {
        localSchedulingAllowed && scheduledExecutorConnection != null &&
            scheduledExecutorConnection === verifiedConversationConnection &&
            executorState?.scope == scheduledScope
    }
    // Initial describe/loading does not revoke the app-owned executor. Once a verified owner has
    // used it, losing that binding immediately stops further claims, even off the scheduling page.
    DisposableEffect(effectiveScheduledExecutor) {
        onDispose { effectiveScheduledExecutor?.invalidate() }
    }
    val scheduledRepository = remember(conversationHost, verifiedConversationConnection,
        effectiveScheduledStore, effectiveScheduleCalendar, effectiveScheduledExecutor) {
        ScheduledRepository(effectiveScheduledStore, ScheduleEngine(effectiveScheduleCalendar), effectiveScheduledExecutor)
    }
    val observedScheduledState = key(scheduledRepository) { scheduledRepository.state.collectAsState().value }
    DisposableEffect(scheduledRepository) { onDispose { scheduledRepository.clear() } }
    val scheduledSessions = if (localSchedulingAllowed && conversationState.host == conversationHost &&
        conversationState.connected) conversationState.sessions else emptyList()
    val scheduledCwds = if (localSchedulingAllowed) {
        (scheduledSessions.mapNotNull { it.cwd?.takeIf(String::isNotBlank) } +
            listOfNotNull(gitInitialCwd?.takeIf(String::isNotBlank))).distinct()
    } else emptyList()
    val scheduledContext = ScheduledValidationContext(
        targets = if (localSchedulingAllowed) listOf(ScheduledTarget.Cron(ScheduledDestination.Projectless)) +
            scheduledCwds.map { ScheduledTarget.Cron(ScheduledDestination.Cwd(it)) } +
            scheduledSessions.map { ScheduledTarget.Heartbeat(it.id) } else emptyList(),
        models = if (localSchedulingAllowed) modelChoices.map { ScheduledModelRef(it.providerId, it.modelId) }.distinct()
            else emptyList(),
    )
    // A new controller is empty immediately; the old owner is fenced and cleared on disposal.
    val scheduledBindingReady = observedScheduledState.scope == scheduledScope &&
        observedScheduledState.connected == scheduledConnected && observedScheduledState.context == scheduledContext
    val scheduledState = if (scheduledBindingReady) observedScheduledState else ScheduledManagementState(
        scope = scheduledScope, connected = scheduledConnected, context = scheduledContext,
        phase = if (scheduledScope == null) ScheduledLoadPhase.Unbound else ScheduledLoadPhase.NotLoaded,
    )
    LaunchedEffect(scheduledRepository, scheduledScope, scheduledContext, scheduledConnected) {
        if (scheduledScope == null) scheduledRepository.clear()
        else {
            scheduledRepository.bind(scheduledScope, scheduledContext, scheduledConnected)
            if (scheduledConnected && scheduledRepository.state.value.phase == ScheduledLoadPhase.NotLoaded) {
                scheduledRepository.load()
            }
        }
    }
    LaunchedEffect(scheduledRepository) { scheduledRepository.observeExecution() }
    val scheduledQuery = (page as? PageId.ScheduledTasks)?.query.orEmpty()
    val scheduledTasks = ScheduledTasksState(mode = navigatorMode, query = scheduledQuery)
    // One clock read per scheduling snapshot/revision; the navigator row and the detail page label the
    // durable instants against this same "now".
    val scheduledNow = remember(scheduledState.effective?.revision, scheduledState.phase) {
        SystemScheduledClock.nowEpochMillis()
    }
    val onScheduledQueryChange: (String) -> Unit = { query ->
        navigation = navigation.copy(currentPage = PageId.ScheduledTasks(query))
    }
    // The summary island's 定时任务 section lists only tasks durably bound to the open conversation.
    val threadScheduledTasks = threadSummaryScheduledTasks(
        scheduledState.effective?.tasks.orEmpty(),
        (page as? PageId.Thread)?.threadId?.let { SessionId(it) },
    )
    val schedulingActionScope = rememberCoroutineScope()
    var selectedNewModel by remember(conversationHost?.hostId, conversationHost?.identity) { mutableStateOf<ConversationModelChoice?>(null) }
    val composerPreferences = remember(conversationHost?.hostId, conversationHost?.identity) {
        mutableStateMapOf<SessionId?, ComposerSessionPreferences>()
    }
    val homePreferences = composerPreferences[null] ?: ComposerSessionPreferences()
    var selectedNewEffort by remember(conversationHost?.hostId, conversationHost?.identity, selectedNewModel?.providerId, selectedNewModel?.modelId) {
        mutableStateOf<String?>(null)
    }
    val effectiveNewModel = selectedNewModel?.let { chosen ->
        modelChoices.firstOrNull { it.providerId == chosen.providerId && it.modelId == chosen.modelId }
    }
    val effectiveNewEffort = selectedNewEffort?.takeIf { it in effectiveNewModel?.reasoningEfforts.orEmpty() }
    val modelReason = when {
        transport == ConnectionTransport.Relay -> "远程 Host 暂未提供模型目录，使用 Host 默认配置。"
        modelSettings.loading -> "正在读取本机模型目录。"
        modelChoices.isEmpty() && modelSettings.snapshot.providers.any { it.applicationError != null } -> "Provider 配置未生效，请到设置中修复。"
        // Nothing configured is the ordinary first-run state, not a fault: the reference composer
        // keeps the slot silent for it (the model chip is the affordance), so it gets no notice.
        else -> null
    }
    val composerBindingReady = conversationState.host == conversationHost &&
        conversationState.selectedSessionId == null
    val composerState = if (composerBindingReady) conversationState else
        ConversationState(host = conversationHost)
    fun homeComposerIsCurrent(): Boolean = navigation.currentPage == PageId.Home &&
        conversation.state.value.host == conversationHost &&
        conversation.state.value.selectedSessionId == null
    val submitReason = when {
        !composerBindingReady -> "正在切换会话…"
        !conversationState.connected -> "请连接可用的 Host 后发送消息。"
        pendingProjectAssociation != null -> "请先确认已创建会话的项目关联。"
        page == PageId.Home && localProjectsState.selectedProjectId != null && localProjectsState.selectedProject == null -> "所选项目尚未加载，请刷新项目后再发送。"
        page == PageId.Home && localProjectsState.mutation != null -> "正在保存项目变更，请稍候。"
        else -> null
    }
    // A local session cannot start without a provider/model, but the reference composer never prints
    // an inline hint for it; the model chip is the affordance, so keep the block silent.
    val submitBlockedWithoutHint = transport == ConnectionTransport.Local && effectiveNewModel == null
    val homeComposer: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Project selection lives in the composer rail below; only the loading/error feedback stays here.
        if (page == PageId.Home && transport == ConnectionTransport.Local &&
            localProjectsState.phase !in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty)) {
            LocalProjectStatus(localProjectsState)
        }
        projectPreparationError?.let { HarnessText(it, Modifier.semantics { testTag = "local-project-prepare-error" }, color = HarnessTokens.Colors.Danger) }
        pendingProjectAssociation?.let { pending ->
            val pendingName = localProjectsState.snapshot?.project(pending.first.projectId)?.name ?: pending.first.projectId.value
            HarnessText("会话已创建，项目「$pendingName」关联待确认；消息尚未发送。",
                Modifier.semantics { testTag = "local-project-attach-pending" }, color = HarnessTokens.Colors.Danger)
            HarnessButton("重试项目关联", {
                if (!retryingProjectAssociation) {
                    retryingProjectAssociation = true
                    conversationScope.launch {
                        try {
                            projectPreparationError = null
                            when (val validated = localProjects.revalidateCreatedSessionTarget(pending.first)) {
                                is LocalProjectRootResult.Failed -> projectPreparationError = validated.failure.message()
                                is LocalProjectRootResult.Ready -> attachProjectSession(validated.target, pending.second)
                            }
                        } finally { retryingProjectAssociation = false }
                    }
                }
            }, enabled = projectBinding != null && localProjects.isBoundTo(projectBinding) &&
                localProjectsState.mutation == null && !retryingProjectAssociation &&
                conversationState.action == im.hikaru.harness.client.conversation.ConversationAction.Idle &&
                !conversationState.cancelling, testTag = "local-project-attach-retry")
            localProjectsState.mutationFailure?.let { HarnessText(it.message(), color = HarnessTokens.Colors.Danger) }
        }
        pendingWorktreeChat?.takeIf { page == PageId.Home }?.let { directory ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HarnessText("将在该工作树目录新建聊天：$directory",
                    Modifier.weight(1f).semantics { testTag = "local-worktree-chat-pending" },
                    color = HarnessTokens.Colors.TextMuted)
                HarnessButton("取消", { pendingWorktreeChat = null }, testTag = "local-worktree-chat-cancel")
            }
        }
        LiveConversationComposer(
            state = composerState, onDraftChange = { if (homeComposerIsCurrent()) conversation.updateDraft(it) },
            onSubmit = {
                if (submitReason == null && homeComposerIsCurrent()) {
                    var target: ValidatedLocalProjectTarget? = null
                    val expectedProjectId = localProjectsState.selectedProjectId
                    val expectedProjectBinding = projectBinding
                    // `settings.worktrees.row.newChat` binds a working directory instead of a project.
                    val worktreeDirectory = pendingWorktreeChat?.takeIf { page == PageId.Home }
                    val useProject = page == PageId.Home && expectedProjectId != null && worktreeDirectory == null
                    projectPreparationError = null
                    conversation.submit(
                        agentOptions = AgentOptions(provider = effectiveNewModel?.providerId, model = effectiveNewModel?.modelId,
                            reasoningEffort = effectiveNewEffort?.let { im.hikaru.harness.client.connection.ReasoningEffortId(it) }),
                        prepareCreation = if (worktreeDirectory != null) ({ CreateSessionOptions(cwd = worktreeDirectory) })
                            else if (!useProject) null else ({
                                if (expectedProjectBinding == null || !localProjects.isBoundTo(expectedProjectBinding) ||
                                    localProjects.state.value.selectedProjectId != expectedProjectId) null
                                else when (val result = localProjects.prepareSelectedRoot()) {
                                    is LocalProjectRootResult.Ready -> { target = result.target; CreateSessionOptions(cwd = result.target.root.path) }
                                    is LocalProjectRootResult.Failed -> {
                                        if (projectBinding != null && localProjects.isBoundTo(projectBinding)) projectPreparationError = result.failure.message()
                                        null
                                    }
                                }
                            }),
                        onSessionCreated = { summary ->
                            composerPreferences[summary.id] = (composerPreferences[null] ?: homePreferences).copy(model = effectiveNewModel,
                                effort = effectiveNewEffort, modelConfigurationKnown = effectiveNewModel != null)
                            composerPreferences.remove(null)
                            if (worktreeDirectory != null) { pendingWorktreeChat = null; true }
                            else if (!useProject) true else target?.let { captured ->
                                    pendingProjectAssociation = captured to summary
                                    attachProjectSession(captured, summary)
                                } ?: false
                        },
                    )
                }
            },
            onCancel = { if (homeComposerIsCurrent()) conversation.cancel() }, models = modelChoices,
            selectedModel = effectiveNewModel,
            onModelSelect = { selectedNewModel = it }, modelUnavailableReason = modelReason,
            submitUnavailableReason = submitReason, submitBlocked = submitBlockedWithoutHint,
            selectedReasoningEffort = effectiveNewEffort,
            onReasoningEffortSelect = { selectedNewEffort = it },
            useHostDefaultModel = transport == ConnectionTransport.Relay,
            permission = homePreferences.permission,
            onPermissionSelect = { if (homeComposerIsCurrent()) composerPreferences[null] = homePreferences.copy(permission = it) },
            topRail = if (page == PageId.Home && transport == ConnectionTransport.Local) ({
                ComposerProjectRail(
                    state = localProjectsState,
                    binding = projectBinding,
                    enabled = conversationState.action == im.hikaru.harness.client.conversation.ConversationAction.Idle &&
                        localProjectsState.mutation == null,
                    onSelect = { localProjects.select(it); projectPreparationError = null; pendingWorktreeChat = null },
                    onQueryChange = localProjects::setQuery,
                    onManage = { navigation = navigation.navigate(PageId.Projects()) },
                    onConfigureEnvironment = { navigation = navigation.navigate(PageId.Settings(SettingsSection.Worktrees)) },
                )
            }) else null,
        )
        }
    }
    // Fixed Codex rail: 主页 / 定时任务 / 项目 / 插件 / 探索 plus the account chip, then the
    // destinations the user pinned from 探索. Host and Settings are entries in the account panel,
    // so they are not rail slots.
    val navItems = harnessRailItems(page, railInitials(host?.displayName ?: "Harness"), pinnedRailDestinations)
    val navigator =
        workspaceState.copy(
            workspaceId = host?.hostId?.value,
            workspaceLabel = host?.displayName ?: "工作区",
            // The shell reads the Workspace navigator from this argument, not from
            // [AppShellState.workspace], so the app-wide collapse mode has to be carried here: without
            // it the shell always saw the page's own default (Expanded) and 首页/会话 could not collapse
            // at all — the titlebar control and ⌘B flipped a mode the shell never read.
            mode = navigatorMode,
        )
    val threadSurfaceState = threadSurface(page, (page as? PageId.Thread)?.threadId?.let { sessionsById[SessionId(it)]?.title })
    val projectNodes = localProjectsState.projects.map { project ->
        val ids = localProjectsState.snapshot?.memberships.orEmpty().filter { it.projectId == project.id }.map { it.session.id }.toSet()
        WorkspaceProjectNode(project.id.value, project.name, children = visibleConversationSessions.filter { it.id in ids }.map { summary ->
            WorkspaceItemNode(summary.id.value, summary.title ?: summary.id.value)
        })
    }
    val accountMenuState =
        accountMenu.copy(
            identityLabel =
                when {
                    account == null -> "本地模式"
                    accountState.session == null -> "未登录"
                    else -> accountState.session.identity.userId.toString()
                },
            modeLabel =
                when {
                    account == null -> "本地 Harness"
                    accountState.session == null -> "远程账号未登录"
                    else -> "远程账号"
                },
            connectionLabel = connectionState.label,
            hostLabel = host?.displayName ?: "当前没有选中的 Host",
            availableHostCount = availableHosts.size,
            isLoggedIn = accountState.session != null,
            canLogin = account != null,
        )
    // The window's tab strip owns everything *but* the conversation: its tabs are the side panel's, and
    // the page the conversation pane draws is this shell's. The two therefore have to agree on one value,
    // and `navigation.currentPage` is it — every navigation runs through `selectPage` below, which reports
    // the target back to the strip so a window with a strip can hand it on to the strip's own controls.
    val tabStripState = LocalHarnessTabStrip.current
    val selectPage: (PageId) -> Unit = { target ->
        if (target.requiresConnection() && connection == null) {
            pendingPage = target
            navigation = navigation.navigate(PageId.Account)
        } else {
            navigation = navigation.navigate(target)
        }
        tabStripState?.selectConversation(target)
    }
    // Selecting a side panel tab does not navigate — that is the whole point of the split, and why the
    // conversation is not a tab — so the shell only has to follow the *conversation* the strip reports,
    // which is the page the navigator and every in-page gesture also move.
    //
    // The selection epoch is what makes a *re*-selection land: choosing 新聊天 while already on 首页 is a
    // request to see 首页, so it has to navigate even though no other observed value changed.
    val conversationPage = tabStripState?.conversationPage
    val conversationSelection = tabStripState?.selectionEpoch
    LaunchedEffect(conversationSelection, conversationPage) {
        if (conversationPage != null && navigation.currentPage != conversationPage) {
            if (conversationPage.requiresConnection() && connection == null) {
                pendingPage = conversationPage
                navigation = navigation.navigate(PageId.Account)
            } else {
                navigation = navigation.navigate(conversationPage)
            }
        }
    }
    val pluginAction: (InstalledPluginsAction) -> Unit = { action ->        when (action) {
            is InstalledPluginsAction.Select -> selectPage(PageId.PluginDetail(action.id.value))
            InstalledPluginsAction.Back -> selectPage(PageId.Plugins)
            else -> plugins.dispatch(action)
        }
    }
    val toggleSidebar: () -> Unit = {
        // One mode for the whole app, so the flip does not depend on which navigator the current page
        // happens to own. A page with no navigator has no toggle at all and is left alone.
        if (pageShellSpec(navigation.currentPage).mountsNavigator) {
            navigatorMode = navigatorMode.toggled()
        }
    }
    // The window draws these in its title bar, but only the page knows which navigator it carries and
    // what it is called, so the page hands the window the actions (null = draw no control).
    val titleStrip = LocalHarnessTitleStrip.current
    val currentShellSpec = pageShellSpec(navigation.currentPage)
    val navigatorCollapsed = navigatorMode == SidebarMode.Collapsed
    // Every page that mounts a navigator can collapse it (首页, 会话, 插件, 定时任务, 设置 are all
    // collapsible in the reference); a page that mounts none has nothing to collapse and draws nothing.
    val navigatorToggleVisible = currentShellSpec.mountsNavigator
    // 新聊天 takes the same slot once the navigator is away on the 首页: with the navigator up the live
    // bar has no such control at all, and on every other page it never has one.
    val newChatVisible = page == PageId.Home && navigatorCollapsed
    SideEffect {
        titleStrip?.navigatorToggle = toggleSidebar.takeIf { navigatorToggleVisible }
        titleStrip?.newChat = ({ selectPage(PageId.Home) }).takeIf { newChatVisible }
        // The conversation's own title, drawn in the title-bar segment the tab strip does not own — the
        // whole bar while the panel is closed, the conversation's half of it while the panel is open. The
        // side panel's segment draws the strip instead, so it never needs one.
        val paneHeading = conversationTitle(page, threadSurfaceState)?.takeIf { it.isNotBlank() }
        titleStrip?.content = paneHeading?.let { heading -> { HarnessPaneTitle(heading) } }
        // The side panel hangs off the conversation, so the reference mounts it on a chat page only: it
        // is on 首页 and every 会话, and on none of the other pages. The window owns the panel, so the
        // page tells it which kind of page it is, exactly as it tells the bar whether it has a navigator.
        tabStripState?.onChatPage = isChatPage(page)
    }
    val back: () -> Unit = { navigation = navigation.back() }
    val shortcutBlocked by rememberUpdatedState(projectEditorOpen || accountMenu.isOpen || exploreMenu.isOpen || navigation.overlay != OverlayId.None)
    val shortcutConsumer by rememberUpdatedState<(ShortcutCommand) -> Boolean>({ command ->
        when (command) {
            ShortcutCommand.Settings -> { selectPage(PageId.Settings(SettingsSection.General)); true }
            ShortcutCommand.ShowKeyboardShortcuts -> { selectPage(PageId.Settings(SettingsSection.Shortcuts)); true }
            ShortcutCommand.ToggleSidebar -> {
                // The chord does what the titlebar control does, so a page that mounts no navigator
                // does not answer it.
                if (navigatorToggleVisible) { toggleSidebar(); true } else false
            }
            ShortcutCommand.NavigateBack -> {
                if (navigation.backStack.isEmpty()) false else { back(); true }
            }
            ShortcutCommand.ManageTasks -> { selectPage(PageId.ScheduledTasks()); true }
            ShortcutCommand.CloseWindow -> false // The desktop root owns the sole close consumer.
        }
    })
    val shortcutOwner = remember(shortcuts) { Any() }
    DisposableEffect(shortcuts) {
        shortcuts.input.attachApp(shortcutOwner, { shortcutBlocked }, { shortcutConsumer(it) })
        onDispose { shortcuts.input.detachApp(shortcutOwner) }
    }
    val runtimeState =
        AppShellState(
            sidebar = SidebarState(mode = navigatorMode),
            navigatorPreferredWidth = navigatorWidth,
            destination = page.toShellDestination(),
            selectedNavigationId = navItems.firstOrNull { it.selected }?.id,
            navigation = navigation,
            // Every navigator carries the same mode; the content below it stays page-owned. The
            // Workspace one is the very object handed to AppShell as its `navigator` argument (that,
            // not this field, is what the shell lays out), so both name one navigator and one mode.
            workspace = navigator,
            settingsNavigator = settingsNavigator.copy(mode = navigatorMode, selectedSlug = (page as? PageId.Settings)?.section?.navigatorSlug() ?: settingsNavigator.selectedSlug),
            pluginsNavigator = pluginsNavigator.copy(mode = navigatorMode, enabledOnly = pluginsState.enabledOnly),
            scheduledTasksNavigator = scheduledTasks,
            accountMenu = accountMenuState,
            exploreMenu = exploreMenu,
            pinnedRailDestinations = pinnedRailDestinations,
            thread = threadSurfaceState,
            connection = connectionState.toIndicatorState(),
        )
    // The docked composer belongs to the **conversation**, which is the only pane that can hold a 会话:
    // the side panel's tabs are tool pages, and the shell draws the dock only in the pane it hands no
    // tab (see the composer gate in `AppShell.MainSurface`). 首页 draws its own richer composer (project
    // picker, worktree binding) inside its page body and never reaches this one.
    val paneComposer: @Composable () -> Unit = {
        val paneState = conversationState
        val paneController = conversation
        val sessionId = (page as? PageId.Thread)?.threadId?.let(::SessionId)
        val bound = paneState.host == conversationHost && paneState.selectedSessionId == sessionId
        val preferences = composerPreferences[sessionId] ?: ComposerSessionPreferences()
        // The composer acts on the pane's live binding, so a callback that lands after the pane moved
        // to another tab cannot draft into, send to or stop the session the pane has left.
        fun stillBound(): Boolean = paneController.state.value.host == conversationHost &&
            paneController.state.value.selectedSessionId == sessionId
        LiveConversationComposer(
            state = if (bound) paneState else ConversationState(host = conversationHost, selectedSessionId = sessionId),
            onDraftChange = { if (stillBound()) paneController.updateDraft(it) },
            onSubmit = { if (bound && stillBound()) paneController.submit(agentOptions = AgentOptions()) },
            onCancel = { if (stillBound()) paneController.cancel() },
            models = modelChoices,
            selectedModel = if (bound) composerModel(paneState, modelChoices, preferences) else null,
            onModelSelect = {}, // Existing sessions expose their configuration read-only.
            modelUnavailableReason = "当前会话暂不支持更换模型。",
            submitUnavailableReason = when {
                !bound -> "正在切换会话…"
                !paneState.connected -> "请连接可用的 Host 后发送消息。"
                selectedSummary?.archived == true -> "此会话已归档，取消归档后可继续发送。"
                else -> null
            },
            selectedReasoningEffort = preferences.effort,
            reasoningEffortKnown = preferences.modelConfigurationKnown,
            permission = preferences.permission,
            onPermissionSelect = { if (bound && stillBound()) composerPreferences[sessionId] = preferences.copy(permission = it) },
        )
    }
    // The dock exists while the conversation holds one; each pane then decides for itself whether to
    // draw it, so 新标签页 never leaves the dock's gutter behind and a tool tab never borrows it.
    val composer: (@Composable () -> Unit)? = if (page is PageId.Thread) paneComposer else null
    val panelScope = if (page is PageId.Thread && conversationHost != null)
        ThreadPanelScope(conversationHost.hostId, SessionId(page.threadId)) else null
    val panelOwner = panelScope?.let { scoped ->
        verifiedConversationConnection?.let { verified ->
            conversationHost?.let { currentHost -> ThreadPanelsOwner(verified, currentHost, scoped.conversationId) }
        }
    }
    val panelController = remember(conversationScope) {
        ThreadPanelsController(conversationScope, ConnectionThreadPanelsRepository())
    }
    // Always read snapshot state, including the frame before the binding effect runs.
    val observedPanels = panelController.state.collectAsState().value
    DisposableEffect(panelController) { onDispose { panelController.close() } }
    LaunchedEffect(panelController, panelOwner) { panelController.bind(panelOwner) }
    // Hide the old owner's panel before the binding effect publishes the next snapshot.
    val panels = if (panelController.isBoundTo(panelOwner)) observedPanels else null
    val ptyController = remember(conversationScope, terminalLaunchProvider) {
        im.hikaru.harness.client.panels.PtyTerminalController(conversationScope, terminalLaunchProvider)
    }
    val observedPty = ptyController.state.collectAsState().value
    DisposableEffect(ptyController) { onDispose { ptyController.close() } }
    LaunchedEffect(ptyController, panelOwner) { ptyController.bind(panelOwner) }
    val ptyState = if (ptyController.isBoundTo(panelOwner)) observedPty else null
    // The summary island reads background activity independently of the tabbed context panel, so
    // opening it never opens a tab, starts a process, or spawns an agent.
    val summaryActivityController = remember(conversationScope) {
        ThreadSummaryActivityController(conversationScope, ConnectionThreadPanelsRepository())
    }
    val observedSummaryActivity = summaryActivityController.state.collectAsState().value
    DisposableEffect(summaryActivityController) { onDispose { summaryActivityController.close() } }
    LaunchedEffect(summaryActivityController, panelOwner) { summaryActivityController.bind(panelOwner) }
    val summaryActivity = if (summaryActivityController.isBoundTo(panelOwner)) observedSummaryActivity else null
    LaunchedEffect(summaryActivityController, summaryPanelOpen, page, panelOwner) {
        if (summaryPanelOpen && page is PageId.Thread && panelOwner != null) summaryActivityController.refresh()
    }
    val onOpenSummaryActivity: (ThreadSummaryActivity) -> Unit = { activity ->
        panels?.let { state ->
            when (activity.kind) {
                ThreadSummaryActivityKind.Subagent -> {
                    panelController.dispatch(ThreadPanelAction.OpenTab(state.scope, ThreadPanelTab.Subagents))
                    // Best effort: deep-select only when the panel already knows the child row.
                    (state.subagents as? PanelLoad.Ready)?.value?.children
                        ?.firstOrNull { it.key.sessionId.value == activity.id }
                        ?.let { panelController.dispatch(ThreadPanelAction.SelectChild(it.key)) }
                }
                ThreadSummaryActivityKind.Process -> {
                    panelController.dispatch(ThreadPanelAction.OpenTab(state.scope, ThreadPanelTab.BackgroundOutput))
                    state.terminalInventory.firstOrNull { it.key.processId == activity.id }
                        ?.let { panelController.dispatch(ThreadPanelAction.SelectTerminal(it.key)) }
                }
            }
        }
    }
    val contextPanel: (@Composable () -> Unit)? = panels?.takeIf { it.openTabs.isNotEmpty() }?.let { state ->
        { ThreadPanels(state, onAction = panelController::dispatch,
            onRefreshOverview = { panelController.refreshOverview() },
            onRefreshDetail = { panelController.refreshDetail() },
            onRefreshTerminal = { panelController.refreshTerminal() },
            onTerminalInput = { key, input -> panelController.operateTerminal(key, im.hikaru.harness.client.panels.TerminalOperationKind.Input, input) },
            onTerminalEof = { key -> panelController.operateTerminal(key, im.hikaru.harness.client.panels.TerminalOperationKind.Eof) },
            onTerminalCancel = { key -> panelController.operateTerminal(key, im.hikaru.harness.client.panels.TerminalOperationKind.Cancel) },
            ptyController = ptyController, ptyState = ptyState) }
    }
    val defaultRoot: @Composable () -> Unit = {
        val shellDensity = LocalDensity.current
        var shellWidthPx by remember { mutableStateOf<Int?>(null) }
        val dismissFloatingPluginsNavigator: () -> Unit = {
            shellWidthPx?.let { widthPx ->
                val availableWidth = with(shellDensity) { widthPx.toDp() }
                if (navigatorLayout(navigatorMode, availableWidth) ==
                    NavigatorLayout.Floating
                ) {
                    navigatorMode = SidebarMode.Collapsed
                }
            }
        }
        // Expose plugin content on route entry, including the initial route after measurement.
        // Mode, refreshes and later resizes are not keys: an explicitly opened drawer stays open.
        LaunchedEffect(page, shellWidthPx != null) {
            if (pageShellSpec(page).navigator == NavigatorKind.Plugins) {
                dismissFloatingPluginsNavigator()
            }
        }
        // Desktop owns the fixture tabs and the split workspace; each surface renders this page.
        AppShell(
            state = runtimeState,
            themeMode = appearanceState.settings.mode.themeMode(),
            navItems = navItems,
            onNavSelect = { item ->
                item.destination
                    ?.let(::railDestinationPage)
                    ?.let(selectPage)
            },
            onOpenAccountMenu = {
                exploreMenu = exploreMenu.close()
                accountMenu = accountMenu.open()
            },
            onDismissAccountMenu = { accountMenu = accountMenu.close() },
            // The 探索 pin menu and the account dropdown are mutually exclusive menus.
            onOpenExploreMenu = {
                accountMenu = accountMenu.close()
                exploreMenu = exploreMenu.open()
            },
            onDismissExploreMenu = { exploreMenu = exploreMenu.close() },
            onOpenExploreDestination = { exploreMenu = exploreMenu.close(); selectPage(it) },
            onTogglePinRailDestination = { destination ->
                pinnedRailDestinations =
                    if (destination in pinnedRailDestinations) pinnedRailDestinations - destination
                    else pinnedRailDestinations + destination
            },
            onAccountMenuAction = { action ->
                accountMenu = accountMenu.close()
                when (action) {
                    AccountMenuAction.Settings -> selectPage(AccountMenuDestination.Settings.toPageId())
                    AccountMenuAction.Profile -> selectPage(PageId.Account)
                    AccountMenuAction.Login -> selectPage(PageId.Account)
                    AccountMenuAction.Logout -> {
                        account?.let { currentAccount ->
                            accountMenuScope.launch { currentAccount.logout() }
                        }
                    }
                }
            },
            navigator = navigator,
            projectNodes = projectNodes,
            recentTasks = recentTasks,
            onToggleSidebar = toggleSidebar,
            onNavigatorResize = { navigatorWidth = it },
            onNewTask = { selectPage(PageId.Home) },
            onNavigatorSearch = { query -> workspaceState = workspaceState.withSearchQuery(query) },
            onSettingsSearch = { query -> settingsNavigator = settingsNavigator.copy(query = query) },
            onSettingsSelect = { item ->
                // Use the measured shell width and the same layout rule as AppShell. Selecting
                // a page dismisses a floating drawer; a pushed navigator keeps the user's mode.
                shellWidthPx?.let { widthPx ->
                    val availableWidth = with(shellDensity) { widthPx.toDp() }
                    if (navigatorLayout(navigatorMode, availableWidth) ==
                        NavigatorLayout.Floating
                    ) {
                        navigatorMode = SidebarMode.Collapsed
                    }
                }
                selectPage(item.toPageId())
            },
            onSettingsBack = {
                navigation = if (navigation.backStack.isEmpty()) navigation.navigate(PageId.Home) else navigation.back()
            },
            navigatorSearchOpen = navigatorSearchOpen,
            onToggleNavigatorSearch = { navigatorSearchOpen = !navigatorSearchOpen },
            onToggleProject = { projectId -> navigation = navigation.toggleProject(projectId) },
            onSelectNode = { node -> selectPage(PageId.Thread(node.id)) },
            onSelectRecent = { task -> selectPage(PageId.Thread(task.id)) },
            onPluginEnabledFilterChange = if (pluginsState.canSearch) ({ enabled ->
                dismissFloatingPluginsNavigator()
                plugins.dispatch(InstalledPluginsAction.SetEnabledOnly(enabled))
            }) else null,
            scheduledTasks = scheduledTasks,
            scheduledNavigatorContent = {
                ScheduledManagementNavigator(
                    state = scheduledState,
                    query = scheduledQuery,
                    onQueryChange = onScheduledQueryChange,
                    onNew = { if (scheduledBindingReady) scheduledRepository.beginCreate() },
                    onSelect = { id -> if (scheduledBindingReady) scheduledRepository.selectTask(id) },
                    onRetry = { if (scheduledBindingReady) schedulingActionScope.launch { scheduledRepository.load() } },
                    calendar = scheduleCalendar,
                    nowEpochMillis = scheduledNow,
                    statusFilter = scheduledStatusFilter,
                    onStatusFilterChange = { scheduledStatusFilter = it },
                    sort = scheduledSort,
                    onSortChange = { scheduledSort = it },
                )
            },
            pluginsNavigatorContent = pluginsState?.let { pluginState ->
                @Composable {
                    PluginsManagementNavigator(
                        state = pluginState,
                        onQueryChange = { pluginAction(InstalledPluginsAction.Search(it)) },
                        // Choosing a filter finishes the compact drawer action, exactly as the previous
                        // filter callback did; the pushed column ignores the dismissal.
                        onSetEnabledOnly = { enabled ->
                            if (pluginState.canSearch) {
                                dismissFloatingPluginsNavigator()
                                pluginAction(InstalledPluginsAction.SetEnabledOnly(enabled))
                            }
                        },
                        onSelect = { pluginAction(InstalledPluginsAction.Select(it)) },
                        onRetry = { pluginAction(InstalledPluginsAction.Refresh) },
                    )
                }
            },
            onOpenProjectContext = { projectId -> selectPage(PageId.Project(projectId)) },
            onNewProject = { editingProject = null; projectEditorOpen = true },
            onOpenProjects = { selectPage(PageId.Projects()) },
            modifier = modifier.onSizeChanged { shellWidthPx = it.width },
            composer = composer,
            contextPanel = contextPanel,
            contextPanelWidthMode = ThreadPanelWidthMode.Regular,
            onDismissContextPanel = { panels?.let { state ->
                state.openTabs.forEach { tab -> panelController.dispatch(ThreadPanelAction.CloseTab(state.scope, tab)) }
            } },
            summaryPanel = if (summaryPanelOpen && page is PageId.Thread) {
                {
                    ThreadSummaryPanel(
                        load = summaryLoad,
                        onOpenReview = { selectPage(PageId.Settings(SettingsSection.Git)) },
                        onRefresh = { git.refreshSummaryStats() },
                        onClose = { summaryPanelOpen = false },
                        subagents = summaryActivity?.subagents.orEmpty(),
                        processes = summaryActivity?.processes.orEmpty(),
                        onOpenActivity = onOpenSummaryActivity,
                        processesStopping = summaryActivity?.stoppingProcesses == true,
                        processesNotice = summaryActivity?.stopNotice,
                        onStopProcesses = { summaryActivityController.stopAllProcesses() },
                        scheduledTasks = threadScheduledTasks,
                        scheduledLoading = scheduledState.phase == ScheduledLoadPhase.Loading,
                        scheduledError = scheduledState.phase == ScheduledLoadPhase.Error,
                        onOpenScheduled = if (scheduledBindingReady) {
                            { id ->
                                scheduledRepository.selectTask(id)
                                selectPage(PageId.ScheduledTasks())
                            }
                        } else null,
                        onRetryScheduled = if (scheduledBindingReady) {
                            { schedulingActionScope.launch { scheduledRepository.load() } }
                        } else null,
                    )
                }
            } else {
                null
            },
            onDismissSummaryPanel = { summaryPanelOpen = false },
            content = {
                // A pane draws the page of the tab it holds (the window provides it around each
                // surface), so a split shows the conversation's page beside the side panel's tab. A
                // pane that holds no tab is the conversation, which draws the page the shell has
                // navigated to and reads `conversation`; the panel's pane never touches it.
                val paneTab = LocalHarnessPaneTab.current
                val panePage = paneTab?.page ?: page
                val paneConversationState = conversationState
                val paneConversation = conversation
                when (val current = panePage) {
                    PageId.Home -> LiveHomeSurface(hostLabel = host?.displayName, content = homeComposer)

                    PageId.NewTab -> NewTabPage(
                        onOpenTool = { tool ->
                            when (tool) {
                                // 变更 is the one 工具 card this client has a destination for: the
                                // repository's own changes, which is the Settings Git page. Choosing it
                                // opens it as another **side panel tab** beside this one — the panel's
                                // tabs *are* its tool pages — rather than navigating the conversation
                                // away, which on a page with no panel would take the panel off screen
                                // with the card still under the pointer. A window with no strip
                                // (mobile, previews) has no panel to open it in and navigates instead.
                                NewTabTool.Changes ->
                                    if (paneStrip != null) paneStrip.add(HarnessChangesPage)
                                    else selectPage(HarnessChangesPage.page ?: PageId.Settings(SettingsSection.Git))
                                NewTabTool.Terminal, NewTabTool.Files, NewTabTool.SideChat -> Unit
                            }
                        },
                    )

                    PageId.Library -> LibraryPlaceholderPage()

                    is PageId.Projects -> LocalProjectsPage(localProjectsState, current.query,
                        onQueryChange = { navigation = navigation.copy(currentPage = PageId.Projects(it)) },
                        onCreate = { editingProject = null; projectEditorOpen = true },
                        onRefresh = { localProjects.refresh() },
                        onOpen = { selectPage(PageId.Project(it.value)) })

                    is PageId.Project -> LocalProjectDetailPage(localProjectsState, LocalProjectId(current.projectId), visibleConversationSessions,
                        onBindDirectory = { editingProject = it; projectEditorOpen = true },
                        onStart = { localProjects.select(it); selectPage(PageId.Home) },
                        onOpenSession = { selectPage(PageId.Thread(it.id.value)) },
                        onRefresh = { localProjects.refresh() })

                    is PageId.ScheduledTasks ->
                        if (scheduledBindingReady) {
                            Column(Modifier.fillMaxSize().semantics { testTag = "scheduled-page" }) {
                                schedulingUnavailableReason?.let { reason ->
                                    HarnessText(reason, Modifier.padding(HarnessTokens.Spacing.Four)
                                        .semantics { testTag = "scheduled-service-unavailable" },
                                        color = HarnessTokens.Colors.TextMuted)
                                }
                                ScheduledManagementPage(
                                    repository = scheduledRepository,
                                    onRecoverExecution = if (scheduledExecutionRecoveryEnabled && recoveryBinding != null &&
                                        scheduledOwnerState?.invalidated == false && scheduledOwnerState.stopped == false &&
                                        scheduledOwnerState.busy == false && executorState?.accepting == false) {
                                        { schedulingActionScope.launch { scheduledExecutorOwner?.recover(recoveryBinding) } }
                                    } else null,
                                    executionRecoveryBusy = recoveryBinding != null && scheduledOwnerState?.busy == true,
                                    executionRecoveryError = scheduledOwnerState?.error.takeIf { recoveryBinding != null },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    query = current.query,
                                    onQueryChange = onScheduledQueryChange,
                                    calendar = scheduleCalendar,
                                    nowEpochMillis = scheduledNow,
                                    onOpenRun = { id ->
                                        if (scheduledBindingReady && scheduledConnected && localSchedulingAllowed) {
                                            selectPage(PageId.Thread(id.value))
                                        }
                                    },
                                )
                            }
                        } else HarnessText("正在绑定当前 Host…", Modifier.semantics { testTag = "scheduled-binding-loading" })

                    is PageId.Thread -> LiveThreadSurface(
                        state = paneConversationState,
                        onRetry = { paneConversation.refresh() },
                    )


                    PageId.Plugins -> InstalledPluginsPage(pluginsState, pluginAction)
                    is PageId.PluginDetail -> InstalledPluginDetailPage(pluginsState, pluginAction)


                    PageId.Account ->
                        AccountWorkspacePage(
                            account = account,
                            state = accountState,
                            status = connectionState,
                            host = host,
                            onOpenSettings = { selectPage(PageId.Settings()) },
                            onLoggedIn = {
                                val target = pendingPage
                                pendingPage = null
                                navigation = navigation.navigate(target ?: PageId.Home)
                            },
                        )

                    is PageId.Settings -> SettingsScreen(
                        current.section, appearanceState,
                        onAppearanceChange = { candidate -> appearanceScope.launch { appearance.save(candidate) } },
                        onAppearanceRetry = { appearanceScope.launch { appearance.retry() } },
                        modelSettings = modelSettings,
                        onModelsRefresh = { modelScope.launch { models.load() } },
                        onModelsRetryApplication = { modelScope.launch { models.retryApplication() } },
                        onModelsDiscover = { id, revision -> modelScope.launch { models.discoverModels(id, revision) } },
                        onProviderSave = { configuration, revision -> modelScope.launch { models.save(configuration, revision) } },
                        onProviderRemove = { id, revision -> modelScope.launch { models.remove(id, revision) } },
                        onCredentialSet = { name, value -> modelScope.launch { models.setCredential(name, value) } },
                        generalState = generalState,
                        onClosePolicyChange = { policy -> generalScope.launch { general.save(general.state.value.settings.copy(closeConfirmation = policy)) } },
                        onGeneralRetry = { generalScope.launch { general.retry() } },
                        worktreeSettingsState = worktreeSettingsState,
                        onSaveWorktreeSettings = { next -> worktreeSettingsScope.launch { worktreeSettings.save(next) } },
                        onOpenPlugins = { selectPage(PageId.Plugins) },
                        connectionsState = connectionsState,
                        onConnectionsRefresh = refreshConnections,
                        onConnectionSelect = selectConnection,
                        shortcutsRepository = shortcuts,
                        pluginsState = pluginsState,
                        onPluginAction = pluginAction,
                        git = git,
                        gitState = gitState,
                        gitBindingReady = observedGitState.binding == gitBinding,
                        gitUnavailableReason = if (transport == ConnectionTransport.Local && gitBackend == null)
                            "当前未注入本机 Git 只读服务，无法读取仓库。" else gitState.unavailableReason,
                        gitCwdChoices = gitCwdChoices,
                        managedWorktrees = managedWorktrees,
                        liveSessions = conversationState.sessions,
                        onOpenSession = { selectPage(PageId.Thread(it.id.value)) },
                        onNewChatInWorktree = { path -> pendingWorktreeChat = path; selectPage(PageId.Home) },
                    )
                }
            },
        )
    }
    val shortcutFocus = remember { FocusRequester() }
    var shortcutHasFocus by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (!windowFocused && ownsShortcutRoot) shortcuts.input.releaseAll() }
    LaunchedEffect(shortcuts, ownsShortcutRoot) { if (ownsShortcutRoot && !shortcutHasFocus) shortcutFocus.requestFocus() }
    val shortcutModifier = if (ownsShortcutRoot) Modifier.fillMaxSize()
        .focusRequester(shortcutFocus).onFocusChanged {
            shortcutHasFocus = it.hasFocus
            if (!it.hasFocus) shortcuts.input.releaseAll()
        }
        .onPreviewKeyEvent { shortcuts.input.preview(it.toShortcutInput()) }
        .onKeyEvent { shortcuts.input.dispatch(it.toShortcutInput()) }.focusable() else Modifier.fillMaxSize()
    CompositionLocalProvider(LocalShortcutsRepository provides shortcuts, LocalShortcutsEventRoot provides shortcuts.input,
        LocalConversationNavigationActions provides ConversationNavigationActions(
            sessionsById.mapKeys { it.key.value }, conversationState.managingSessionId != null,
            { id, command ->
                if (conversation.state.value.host != conversationHost) null else {
                    conversation.manage(SessionId(id), command)?.let { pending -> conversationScope.async {
                        val applied = pending.await()
                        if (applied && command == ConversationCommand.Delete && conversation.state.value.host == conversationHost &&
                            (navigation.currentPage as? PageId.Thread)?.threadId == id) selectPage(PageId.Home)
                        applied
                    } }
                }
            },
            // The activity feed changes one session at a time; awaiting each command keeps the
            // controller's single-management-slot contract intact for bulk menu actions.
            markAllRead = { ids ->
                conversationScope.launch {
                    ids.forEach { id ->
                        val summary = currentConversationSessions.firstOrNull { it.id.value == id } ?: return@forEach
                        if (summary.unread && summary.managementAvailable) {
                            conversation.manage(summary.id, ConversationCommand.MarkRead)?.await()
                        }
                    }
                }
            },
            archiveAll = { ids ->
                conversationScope.launch {
                    ids.forEach { id ->
                        val summary = currentConversationSessions.firstOrNull { it.id.value == id } ?: return@forEach
                        if (!summary.archived && summary.archiveAvailable) {
                            conversation.manage(summary.id, ConversationCommand.ToggleArchive)?.await()
                        }
                    }
                }
            },
            owner = conversationHost)) {
        Box(shortcutModifier) {
            HarnessTheme(appearanceState.settings.mode.themeMode(), appearanceState.settings.themeCustomization()) {
                UiSnapshotProvider(uiSnapshot) {
                    conversationState.managementError?.let { message ->
                        androidx.compose.material3.AlertDialog(onDismissRequest = conversation::dismissManagementError,
                            title = { Text("会话操作未完成") }, text = { Text(message) },
                            confirmButton = { Button(onClick = conversation::dismissManagementError) { Text("关闭") } })
                    }
                    SlotOutlet(
                        key = ClientUiSlots.Root,
                        props = RootProps(render = defaultRoot),
                        fallback = defaultRoot,
                    )
                    if (projectEditorOpen) key(projectBinding) {
                        LocalProjectEditorDialog(localProjectsState, editingProject,
                            onDismiss = { projectEditorOpen = false }, onCreate = saveProject,
                            onRetryCreate = { localProjectsState.pendingCreate?.let(saveProject) },
                            onBind = { id, directory ->
                                val capturedBinding = projectBinding
                                val job = localProjects.bindDirectory(id, directory)
                                if (job != null) conversationScope.launch {
                                    job.join()
                                    if (capturedBinding != null && localProjects.isBoundTo(capturedBinding) && localProjects.state.value.mutationFailure == null) projectEditorOpen = false
                                }
                            })
                    }
                }
            }
        }
    }
}

private fun PageId.requiresConnection(): Boolean = this is PageId.Thread

/** No calendar dates or time-zone success are fabricated when a platform adapter is absent. */
private object UnavailableAppScheduleCalendar : ScheduleCalendar {
    override fun isValidZone(zoneId: String): Boolean = false
    private fun unavailable(): Nothing = throw UnsupportedOperationException("当前平台未提供定时任务日历服务。")
    override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = unavailable()
    override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = unavailable()
    override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = unavailable()
}

/** Until a real local read adapter is injected, every attempted read fails explicitly. */
private object UnavailableAppGitBackend : GitBackend {
    private fun unavailable(): Nothing = throw GitReadException(
        GitFailure(GitFailureKind.Unavailable, "当前未注入本机 Git 只读服务，无法读取仓库。"),
    )
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository = unavailable()
    override suspend fun status(repository: GitRepository): GitStatusSnapshot = unavailable()
    override suspend fun branches(repository: GitRepository): List<GitBranch> = unavailable()
    override suspend fun worktrees(repository: GitRepository): List<GitWorktree> = unavailable()
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot = unavailable()
}

/**
 * The rail 资料库 placeholder. Codex's Library is a file product this client does not implement, so
 * the page states that plainly instead of showing fabricated file data.
 */
@Composable
private fun LibraryPlaceholderPage() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .semantics { testTag = "library-page" }.padding(HarnessTokens.Spacing.Four),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
    ) {
        HarnessText("资料库", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { testTag = "library-title" })
        HarnessText("资料库是 Codex 的文件库；该能力当前未接入。", color = HarnessTokens.Colors.TextMuted,
            modifier = Modifier.semantics { testTag = "library-unavailable" })
        HarnessText("本页面为占位，不显示任何示例文件。", color = HarnessTokens.Colors.TextMuted)
    }
}

/**
 * A pane's own title bar, drawn in the window's title strip while the tab strips are hidden
 * (Codex 隐藏标签页): the page title at the measured 16px / 24px line, weight 430, in the title-bar's
 * own text ink (`rgb(26, 28, 31)` on the live bar — [HarnessColors.ChromeText], which is lighter than
 * the app's body [HarnessColors.Text]). Codex draws the project chip before the title and the chat
 * actions after it; this client has neither yet, so the segment carries the title alone rather than
 * inventing controls.
 */
@Composable
private fun HarnessPaneTitle(title: String) {
    HarnessText(
        title,
        color = HarnessTokens.Colors.ChromeText,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = HarnessTokens.Text.Md, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

/**
 * Whether [page] is a **chat page**: the reference's 新聊天页 or a 会话 — the pages whose main area is a
 * conversation, with the chat's own toolbar and a composer under it.
 *
 * Measured on the live shell, these are exactly the pages that mount `role="toolbar"
 * aria-label="聊天工具栏"` in the title bar *and* the side panel (`aside` 1214 wide with its `任务选项卡`
 * strip). Every other page mounts neither, and its main area takes the whole width right of the navigator:
 * 定时任务's main surface reads `[341,44,2215,1272]` against the chat page's `[341,44,1001,1272]`.
 */
private fun isChatPage(page: PageId): Boolean = page == PageId.Home || page is PageId.Thread

/**
 * The conversation's title — the text the live shell puts in its `role="toolbar" aria-label="聊天工具栏"`,
 * or null when the main area is not a [isChatPage] and the shell draws no such toolbar at all.
 *
 * Measured on the live shell: that toolbar exists on the chat page **only** — 资料库, 定时任务 and 插件 all
 * render none of it, and the segment beside the tab strip is empty on them — while a 会话's carries the
 * chat's own name (`解释 Amper 编译删除失败`). A **new** chat is the third case: the toolbar is there but
 * its text is empty, so 新聊天 shows nothing where a 会话 shows its name. All three are why this returns a
 * String? rather than a page label.
 */
private fun conversationTitle(page: PageId, thread: ThreadSurfaceState?): String? =
    when (page) {
        PageId.Home -> null
        is PageId.Thread -> thread?.title ?: pageTitle(page)
        else -> null
    }

private fun pageTitle(page: PageId): String =
    when (page) {
        PageId.Home -> "主页"
        PageId.NewTab -> "新标签页"
        PageId.Library -> "资料库"
        is PageId.Projects -> "项目"
        is PageId.Project -> "项目"
        is PageId.Thread -> page.threadId
        is PageId.ScheduledTasks -> "定时任务"
        PageId.Plugins -> "插件"
        is PageId.PluginDetail -> page.pluginId
        PageId.Account -> "账号"
        is PageId.Settings -> "设置"
    }

private fun threadSurface(
    page: PageId,
    title: String?,
): ThreadSurfaceState? =
    if (page is PageId.Thread) {
        ThreadSurfaceState(
            threadId = page.threadId,
            title = title ?: pageTitle(page),
        )
    } else {
        null
    }

/** Mobile entry wrapper: discovers and connects the currently authenticated Host. */
@Composable
public fun RemoteHarnessApp(
    account: MemberAccount,
    remote: RemoteConnectionOwner,
    clientId: String,
    modifier: Modifier = Modifier,
) {
    val accountState = account.state.collectAsState().value
    val connection = remote.connection.collectAsState().value
    val hosts = remote.hosts.collectAsState().value
    val remoteStatus = remote.status.collectAsState().value
    val discovery = remote.discovery.collectAsState().value
    val ownerIdentity = remote.identity.collectAsState().value
    val selectedHost = remote.selectedHost.collectAsState().value
    val ownerMatchesAccount = ownerIdentity != null && ownerIdentity == accountState.session?.identity
    val scope = rememberCoroutineScope()
    LaunchedEffect(accountState.session, accountState.target) {
        val session = accountState.session
        if (session == null || accountState.target == null) {
            remote.stop()
        } else {
            try {
                remote.start(session, clientId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The connection state remains observable in the shared shell.
            }
        }
    }
    DisposableEffect(remote) {
        onDispose { remote.close() }
    }
    HarnessApp(
        connection = connection.takeIf { ownerMatchesAccount },
        connectionChanges = remote.connection,
        account = account,
        availableHosts = hosts.takeIf { ownerMatchesAccount }.orEmpty(),
        onSelectHost = { host -> scope.launch { remote.select(host, accountState.session) } },
        remoteStatus = remoteStatus.takeIf { ownerMatchesAccount } ?: RemoteConnectionStatus.IDLE,
        remoteSelectedHost = selectedHost.takeIf { ownerMatchesAccount },
        remoteDiscovery = discovery.takeIf { ownerMatchesAccount } ?: RemoteDiscoveryState(),
        onRefreshHosts = { remote.refresh(accountState.session) },
        sessionEvents = remote.sessionEvents,
        historyRecoveries = remote.historyRecoveries,
        onSubscribeSession = { id -> remote.subscribe(StreamId(id.value)) },
        onUnsubscribeSession = { id -> remote.unsubscribe(StreamId(id.value)) },
        modifier = modifier,
    )
}

public enum class HarnessDestination(
    public val label: String,
    public val requiresConnection: Boolean = false,
) {
    Harness("主页"),
    Account("账号"),
    Tenant("租户"),
    Profile("资料"),
    Sessions("会话", requiresConnection = true),
    ScheduledTasks("定时任务"),
    Settings("设置"),
    PluginInventory("插件"),

    ;

    public companion object {
        public fun fromLaunchValue(value: String): HarnessDestination = when (value.lowercase()) {
            "home", "harness", "conversation" -> Harness
            "account", "login" -> Account
            "tenant" -> Tenant
            "profile" -> Profile
            "no-host" -> Harness
            "session", "sessions", "conversation-session" -> Sessions
            "scheduled", "scheduled-tasks" -> ScheduledTasks
            "settings" -> Settings
            "plugin", "plugins", "inventory" -> PluginInventory
            else -> error("Unknown Desktop destination: $value")
        }
    }
}

public enum class HarnessConnectionState(public val label: String) {
    Connecting("连接中"),
    Online("在线"),
    Local("本地"),
    NoHost("暂无在线 Host"),
    RequiresLogin("需要登录"),
    Disconnected("已断开"),
    ProtocolIncompatible("协议不兼容"),
}

private fun HarnessDestination.toShellDestination() = when (this) {
        HarnessDestination.Harness,
        HarnessDestination.Sessions -> im.hikaru.harness.client.shell.ShellDestination.Conversation
        HarnessDestination.ScheduledTasks -> im.hikaru.harness.client.shell.ShellDestination.ScheduledTasks
    HarnessDestination.Account -> im.hikaru.harness.client.shell.ShellDestination.Account
    HarnessDestination.Tenant,
    HarnessDestination.Profile -> im.hikaru.harness.client.shell.ShellDestination.Settings
    HarnessDestination.Settings -> im.hikaru.harness.client.shell.ShellDestination.Settings
    HarnessDestination.PluginInventory -> im.hikaru.harness.client.shell.ShellDestination.PluginInventory
}

/** Compatibility adapter: the legacy launch destination maps onto the shared page model. */
private fun HarnessDestination.toPageId(): im.hikaru.harness.client.shell.PageId =
    when (this) {
        HarnessDestination.Harness -> im.hikaru.harness.client.shell.PageId.Home
        HarnessDestination.Sessions -> im.hikaru.harness.client.shell.PageId.Home
        HarnessDestination.ScheduledTasks -> im.hikaru.harness.client.shell.PageId.ScheduledTasks()
        HarnessDestination.Account -> im.hikaru.harness.client.shell.PageId.Account
        HarnessDestination.Tenant,
        HarnessDestination.Profile,
        HarnessDestination.Settings -> im.hikaru.harness.client.shell.PageId.Settings()
        HarnessDestination.PluginInventory -> im.hikaru.harness.client.shell.PageId.Plugins
    }

private val HarnessDestination.icon: HarnessIconName
    get() = when (this) {
        HarnessDestination.Harness -> HarnessIconName.Host
        HarnessDestination.Account -> HarnessIconName.User
        HarnessDestination.Tenant -> HarnessIconName.Host
        HarnessDestination.Profile -> HarnessIconName.User
        HarnessDestination.Sessions -> HarnessIconName.Menu
        HarnessDestination.ScheduledTasks -> HarnessIconName.Clock
        HarnessDestination.Settings -> HarnessIconName.Settings
        HarnessDestination.PluginInventory -> HarnessIconName.Plug
    }

private fun HarnessConnectionState.toIndicatorState() = when (this) {
    HarnessConnectionState.Connecting -> ConnectionIndicatorState.Connecting
    HarnessConnectionState.Online -> ConnectionIndicatorState.Online
    HarnessConnectionState.Local -> ConnectionIndicatorState.Local
    HarnessConnectionState.NoHost -> ConnectionIndicatorState.NoHost
    HarnessConnectionState.RequiresLogin -> ConnectionIndicatorState.RequiresLogin
    HarnessConnectionState.Disconnected -> ConnectionIndicatorState.Disconnected
    HarnessConnectionState.ProtocolIncompatible -> ConnectionIndicatorState.Incompatible
}

@Composable
private fun HarnessConnectionState.toBadgeColor() = when (this) {
    HarnessConnectionState.Online,
    HarnessConnectionState.Local -> HarnessTokens.Colors.Success
    HarnessConnectionState.Disconnected,
    HarnessConnectionState.ProtocolIncompatible -> HarnessTokens.Colors.Danger
    else -> HarnessTokens.Colors.Accent
}

@Composable
private fun ProfileScreen(account: MemberAccount?, state: AccountState) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("个人资料", style = MaterialTheme.typography.titleLarge)
        if (state.session == null) {
            Text("登录后查看个人资料。")
        } else {
            val profile = state.profile
            Text(profile?.nickname ?: "资料尚未加载")
            Button(onClick = {
                scope.launch {
                    try {
                        account?.loadProfile()
                        error = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        error = failure.message ?: "资料加载失败"
                    }
                }
            }) { Text("刷新资料") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SettingsScreen(
    section: SettingsSection,
    appearanceState: AppearanceState,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    onAppearanceRetry: () -> Unit,
    modelSettings: ModelSettingsState,
    onModelsRefresh: () -> Unit,
    onModelsRetryApplication: () -> Unit,
    onModelsDiscover: (String, Long) -> Unit,
    onProviderSave: (ProviderConfiguration, Long) -> Unit,
    onProviderRemove: (String, Long) -> Unit,
    onCredentialSet: (String, String) -> Unit,
    generalState: GeneralSettingsState,
    onClosePolicyChange: (CloseConfirmationPolicy) -> Unit,
    onGeneralRetry: () -> Unit,
    worktreeSettingsState: WorktreeSettingsState,
    onSaveWorktreeSettings: (WorktreeSettings) -> Unit,
    onOpenPlugins: () -> Unit,
    connectionsState: ConnectionsState,
    onConnectionsRefresh: () -> Unit,
    onConnectionSelect: (HostDescription) -> Unit,
    shortcutsRepository: ShortcutsRepository,
    pluginsState: InstalledPluginsState,
    onPluginAction: (InstalledPluginsAction) -> Unit,
    git: GitController,
    gitState: GitState,
    gitBindingReady: Boolean,
    gitUnavailableReason: String?,
    gitCwdChoices: List<String>,
    managedWorktrees: im.hikaru.harness.client.git.ManagedWorktreeController?,
    liveSessions: List<im.hikaru.harness.client.connection.SessionSummary>,
    onOpenSession: (im.hikaru.harness.client.connection.SessionSummary) -> Unit,
    onNewChatInWorktree: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth().semantics { testTag = "settings-content-" + section.navigatorSlug() }) {
      when (section) {
        SettingsSection.General -> SettingsGeneralPage(generalState, onClosePolicyChange, onGeneralRetry, onOpenPlugins)
        SettingsSection.Appearance -> SettingsAppearancePage(appearanceState, onAppearanceChange, onAppearanceRetry)
        SettingsSection.Models -> SettingsModelsPage(modelSettings, onModelsRefresh, onProviderSave, onProviderRemove, onCredentialSet, onModelsRetryApplication, onModelsDiscover)
        SettingsSection.Connections -> SettingsConnectionsPage(connectionsState, onConnectionsRefresh, onConnectionSelect)
        SettingsSection.Shortcuts -> SettingsShortcutsPage(shortcutsRepository)
        SettingsSection.Plugins -> InstalledPluginsPage(pluginsState, onPluginAction)
        SettingsSection.Git, SettingsSection.Worktrees -> BoundGitSettingsPage(
            git, gitState, gitBindingReady, gitUnavailableReason, gitCwdChoices,
            worktrees = section == SettingsSection.Worktrees,
            managedWorktrees = managedWorktrees,
            worktreeSettings = worktreeSettingsState,
            onSaveWorktreeSettings = onSaveWorktreeSettings,
            liveSessions = liveSessions,
            onOpenSession = onOpenSession,
            onNewChatInWorktree = onNewChatInWorktree,
        )
        else -> {
            val label = SettingsNavigatorModel.item(section.navigatorSlug())?.label ?: section.name
            Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three)) {
                HarnessText(label, style = MaterialTheme.typography.headlineSmall)
                HarnessText("此设置页面正在实现。", color = HarnessTokens.Colors.TextMuted)
            }
        }
      }
    }
}

/** Render controller-owned pages only after the current verified Host binding has been applied. */
@Composable
private fun BoundGitSettingsPage(
    controller: GitController,
    state: GitState,
    bindingReady: Boolean,
    unavailableReason: String?,
    cwdChoices: List<String>,
    worktrees: Boolean,
    managedWorktrees: im.hikaru.harness.client.git.ManagedWorktreeController?,
    worktreeSettings: WorktreeSettingsState,
    onSaveWorktreeSettings: (WorktreeSettings) -> Unit,
    liveSessions: List<im.hikaru.harness.client.connection.SessionSummary>,
    onOpenSession: (im.hikaru.harness.client.connection.SessionSummary) -> Unit,
    onNewChatInWorktree: (String) -> Unit,
) {
    val title = if (worktrees) "Worktrees" else "Git"
    val tag = if (worktrees) "worktrees-page" else "git-page"
    if (!bindingReady || unavailableReason != null) {
        SettingsFormFrame(title, tag) {
            HarnessText(
                unavailableReason ?: "正在绑定当前 Host…",
                Modifier.semantics { testTag = if (unavailableReason == null) "git-binding-loading" else "git-unavailable" },
                color = HarnessTokens.Colors.TextMuted,
            )
        }
        return
    }
    val directoryPicker: (@Composable () -> Unit)? =
        if (state.binding?.access == GitAccess.LocalReadOnly && cwdChoices.isNotEmpty()) {
            { im.hikaru.harness.client.git.GitSessionDirectoryPicker(controller, state, bindingReady, cwdChoices, compact = true) }
        } else null
    if (worktrees) WorktreesPage(controller, Modifier.fillMaxSize(), managedController = managedWorktrees,
        worktreeSettings = worktreeSettings, onSaveWorktreeSettings = onSaveWorktreeSettings,
        sessions = liveSessions, onOpenSession = onOpenSession, onNewChatInWorktree = onNewChatInWorktree,
        directoryPicker = directoryPicker)
    else SettingsGitPage(controller, Modifier.fillMaxSize(), directoryPicker = directoryPicker)
}

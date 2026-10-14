import androidx.compose.ui.window.application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import im.hikaru.harness.client.settings.AppearanceRepository
import im.hikaru.harness.client.settings.ModelSettingsRepository
import im.hikaru.harness.desktop.settings.DesktopModelSettingsBackend
import im.hikaru.harness.client.settings.AppearanceSettings
import im.hikaru.harness.client.settings.AppearanceMode
import im.hikaru.harness.client.settings.DesktopAppearanceStore
import im.hikaru.harness.client.settings.themeCustomization
import im.hikaru.harness.bundle.desktop.resolveDesktopHarnessHome
import im.hikaru.harness.bundle.desktop.DesktopUserPlugins
import im.hikaru.harness.agent.AgentInstructionsKey
import im.hikaru.harness.desktop.plugins.DesktopInstalledPluginRepository
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.app.ClientUiSchema
import im.hikaru.harness.client.app.DesktopClientEntries
import im.hikaru.harness.client.app.DesktopClientPluginCatalog
import im.hikaru.harness.client.account.KtorMemberTransport
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.account.createAccountHttpClient
import im.hikaru.harness.client.account.desktopAppSessionStore
import im.hikaru.harness.client.account.platformAccountEngine
import im.hikaru.harness.boot.HarnessHost
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.desktop.git.DesktopGitBackend
import im.hikaru.harness.desktop.git.DesktopManagedGitBackend
import im.hikaru.harness.desktop.git.DesktopManagedWorktreeRepository
import im.hikaru.harness.desktop.git.DesktopManagedWorktreeStore
import im.hikaru.harness.desktop.git.DesktopWorktreeSessionObserver
import im.hikaru.harness.client.git.GitAccess
import im.hikaru.harness.client.git.ManagedWorktreeOwner
import im.hikaru.harness.desktop.scheduling.DesktopScheduledStore
import im.hikaru.harness.desktop.scheduling.DesktopScheduleCalendar
import im.hikaru.harness.desktop.scheduling.DesktopScheduledExecution
import im.hikaru.harness.client.scheduling.ScheduledExecutorConfig
import im.hikaru.harness.client.scheduling.ScheduledShutdownResult
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.desktop.connection.DesktopHostRelay
import im.hikaru.harness.desktop.connection.HostEventStream
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.createOwnedAccount
import im.hikaru.harness.desktop.openDesktopResources
import im.hikaru.harness.desktop.shutdownDesktopResources
import im.hikaru.harness.desktop.window.DesktopHarnessWindow
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessConfirmDialog
import im.hikaru.harness.ui.foundation.HarnessConfirmSubmitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.file.Path
import im.hikaru.harness.client.settings.GeneralSettingsRepository
import im.hikaru.harness.client.settings.DesktopGeneralSettingsStore
import im.hikaru.harness.client.settings.WorktreeSettingsRepository
import im.hikaru.harness.client.settings.DesktopWorktreeSettingsStore
import im.hikaru.harness.client.settings.ShortcutsRepository
import im.hikaru.harness.client.settings.DesktopShortcutsStore
import im.hikaru.harness.desktop.window.desktopShortcutPlatform
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = DesktopLaunchOptions.parse(args)
    val appearance = AppearanceRepository(
        store = DesktopAppearanceStore(resolveDesktopHarnessHome(options.home).directory.resolve("client-appearance.json")),
        initial = AppearanceSettings(mode = AppearanceMode.from(options.themeMode)),
        launchMode = if (options.themeSpecified) AppearanceMode.from(options.themeMode) else null,
    )
    runBlocking { appearance.load() }
    val generalSettings = GeneralSettingsRepository(
        DesktopGeneralSettingsStore(resolveDesktopHarnessHome(options.home).directory.resolve("client-general.json")),
        closePolicyAvailable = true,
    )
    runBlocking { generalSettings.load() }
    val worktreeSettings = WorktreeSettingsRepository(
        DesktopWorktreeSettingsStore(resolveDesktopHarnessHome(options.home).directory.resolve("client-worktrees.json")),
    )
    runBlocking { worktreeSettings.load() }
    val shortcutPlatform = desktopShortcutPlatform()
    val shortcuts = ShortcutsRepository(
        DesktopShortcutsStore(resolveDesktopHarnessHome(options.home).directory.resolve("client-shortcuts.json"), shortcutPlatform),
        shortcutPlatform,
    )
    runBlocking { shortcuts.load() }
    val resources =
        runBlocking {
            openDesktopResources(
                startHost = {
                    DesktopWorktreeSessionObserver.startDesktopHost(
                        harnessHome = options.home,
                        overlays = options.patches,
                    )
                },
                createAccount = { createDesktopAccount(options.home) },
                closeHost = { it.profiledHost.close() },
            )
        }
    val profiledHost = resources.host.profiledHost
    val harnessHost = profiledHost.host
    val modelSettings = ModelSettingsRepository(DesktopModelSettingsBackend(harnessHost.runtime.context))
    val connection = LocalConnection(harnessHost.gateway)
    val terminalLaunchProvider = im.hikaru.harness.desktop.terminal.DesktopTerminalLaunchProvider(
        harnessHost.profile.hostDescription.hostId, connection)
    val userPlugins = DesktopUserPlugins(
        resolveDesktopHarnessHome(options.home).directory.resolve("client-plugins.json"),
    )
    runBlocking { harnessHost.runtime.context.provide(AgentInstructionsKey, userPlugins) }
    val installedPlugins = DesktopInstalledPluginRepository(
        harnessHost.profile.hostDescription.hostId, connection, userPlugins,
    )
    val originalGitBackend = DesktopGitBackend(harnessHost.profile.hostDescription.hostId)
    val managedHome = resolveDesktopHarnessHome(options.home).directory.toRealPath()
    val chatArchiveStore =
        im.hikaru.harness.desktop.git.DesktopWorktreeChatArchiveStore(managedHome.resolve("managed-chat-archive.json"))
    val managedWorktrees = DesktopManagedWorktreeRepository(
        backend = originalGitBackend,
        store = DesktopManagedWorktreeStore(managedHome.resolve("managed-worktrees.json")),
        sessionObserver = resources.host.sessionObserver,
        pinStore = im.hikaru.harness.desktop.git.DesktopWorktreePinStore(managedHome.resolve("managed-chat-pins.json")),
        archiveStore = chatArchiveStore,
        archiveAgents = { id ->
            harnessHost.runtime.context.get(im.hikaru.harness.agent.AgentKey)
                ?.beginArchive(im.hikaru.harness.agent.AgentId(id))
                ?: im.hikaru.harness.agent.AgentArchiveBegin.NotRegistered
        },
        restoreChat = im.hikaru.harness.desktop.git.DesktopWorktreeChatRestoreService(harnessHost.runtime.context)::restore,
        ownerValidator = { binding ->
            // Local mode uses the anonymous partition; remote account identity is separate.
            if (binding.access != GitAccess.LocalReadOnly || binding.identity != null ||
                binding.hostId != harnessHost.profile.hostDescription.hostId) null
            else {
                val response = connection.host.describe()
                val described = response.data
                if (response.isSuccess && described != null && described.hostId == binding.hostId &&
                    described.protocolVersion.major == ProtocolVersion.Current.major) {
                    ManagedWorktreeOwner(null, described.hostId, managedHome.toString())
                } else null
            }
        },
    )
    runBlocking {
        harnessHost.runtime.context.provide(
            im.hikaru.harness.session.SessionArchiveKey,
            im.hikaru.harness.desktop.git.DesktopWorktreeChatArchiveGate(
                chatArchiveStore,
                im.hikaru.harness.client.git.ManagedWorktreeOwner(
                    null, harnessHost.profile.hostDescription.hostId, managedHome.toString()),
            ),
        )
    }
    val gitBackend = DesktopManagedGitBackend(originalGitBackend, managedWorktrees)
    val localProjectRepository = im.hikaru.harness.desktop.projects.DesktopLocalProjectRepository(
        localHostId = harnessHost.profile.hostDescription.hostId,
        store = im.hikaru.harness.desktop.projects.DesktopLocalProjectStore(
            resolveDesktopHarnessHome(options.home).directory.resolve("local-projects.json"),
        ),
        expectedConnection = connection,
    )
    val scheduledStore = DesktopScheduledStore(
        resolveDesktopHarnessHome(options.home).directory.resolve("client-scheduled-tasks.json"),
    )
    val scheduleCalendar = DesktopScheduleCalendar()
    val account = resources.account
    val clientHost =
        try {
            runBlocking {
                im.hikaru.harness.ui.slot.ClientHarnessHost.start(
                    catalog = DesktopClientPluginCatalog,
                    entries = DesktopClientEntries,
                    schema = ClientUiSchema,
                )
            }
        } catch (error: Throwable) {
            runBlocking {
                shutdownDesktopResources(account::shutdown, profiledHost::close)
            }
            throw error
    }
    val hostRelay = createDesktopHostRelay(harnessHost)
    val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val scheduledExecution = DesktopScheduledExecution(
        context = harnessHost.runtime.context,
        expectedHostId = harnessHost.profile.hostDescription.hostId,
        connection = connection,
        store = scheduledStore,
        calendar = scheduleCalendar,
        leaderPath = resolveDesktopHarnessHome(options.home).directory.resolve("client-scheduled-leader.lock"),
        executionScope = executionScope,
        config = ScheduledExecutorConfig(verifiedCronExecution = true),
    )
    val scheduledExecutorOwner = scheduledExecution.owner
    var resourcesReleased = false

    try {
        hostRelay.start(account)
        runBlocking { scheduledExecution.start() }
        // `exitProcessOnExit = false` keeps this process under our control: closing the window ends
        // the Compose application through the close request below, the finally block releases the
        // host/client resources, and only then does main exit.
        application(exitProcessOnExit = false) {
            val closeScope = rememberCoroutineScope()
            var closing by remember { mutableStateOf(false) }
            var closeFailure by remember { mutableStateOf<String?>(null) }
            val requestClose: () -> Unit = {
                if (!closing) {
                    closing = true
                    closeFailure = null
                    closeScope.launch {
                        try {
                            when (val result = scheduledExecutorOwner.shutdown()) {
                                ScheduledShutdownResult.Drained -> exitApplication()
                                is ScheduledShutdownResult.UnsafeToRelease -> {
                                    closeFailure = result.reason
                                    closing = false
                                }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            closeFailure = "无法确认定时任务已停止，请重试。"
                            closing = false
                        }
                    }
                }
            }
            val appearanceState by appearance.state.collectAsState()
            val generalState by generalSettings.state.collectAsState()
            DesktopHarnessWindow(
                width = options.windowSize.width,
                height = options.windowSize.height,
                themeMode = appearanceState.settings.mode.themeMode(),
                customization = appearanceState.settings.themeCustomization(),
                closeConfirmation = generalState.settings.closeConfirmation,
                shortcutsRepository = shortcuts,
                onCloseRequest = requestClose,
                closeRequestRejected = closeFailure != null,
            ) {
                HarnessApp(
                    connection = connection,
                    terminalLaunchProvider = terminalLaunchProvider,
                    connectionTransport = ConnectionTransport.Local,
                    gitBackend = gitBackend,
                    managedWorktreeRepository = managedWorktrees,
                    installedPluginRepository = installedPlugins,
                    localProjectRepository = localProjectRepository,
                    scheduledStore = scheduledExecution.store,
                    scheduledExecutorOwner = scheduledExecutorOwner,
                    scheduledExecutionRecoveryEnabled = scheduledExecution.recoveryEnabled,
                    scheduledExecutorConnection = scheduledExecution.connection,
                    scheduleCalendar = scheduledExecution.calendar,
                    gitInitialCwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().toString(),
                    account = account,
                    uiSnapshot = clientHost.snapshot,
                    initialDestination = options.destination,
                    initialAccountMenuOpen = options.accountMenuOpen,
                    themeMode = options.themeMode,
                    appearanceRepository = appearance,
                    modelSettingsRepository = modelSettings,
                    generalSettingsRepository = generalSettings,
                    worktreeSettingsRepository = worktreeSettings,
                    shortcutsRepository = shortcuts,
                    // The fused 44dp title strip belongs to the window: macOS draws the traffic
                    // lights over the rail column and the app titlebar row occupies the strip.
                )
                HarnessConfirmDialog(
                    visible = closing || closeFailure != null,
                    onDismissRequest = { closeFailure = null },
                    title = "等待定时任务停止",
                    description = "正在确认已投递任务的结果。确认停止后才能关闭应用。",
                    cancelLabel = "保留窗口", confirmLabel = "重新检查并关闭",
                    submitState = if (closing) HarnessConfirmSubmitState.Submitting
                        else HarnessConfirmSubmitState.Failed(closeFailure.orEmpty()),
                    onCancel = { closeFailure = null }, onConfirm = requestClose,
                    testTag = "scheduled-close-gate",
                )
            }
        }
    } finally {
        runBlocking {
            val stopped = scheduledExecutorOwner.shutdown()
            if (stopped is ScheduledShutdownResult.UnsafeToRelease) {
                System.err.println(stopped.reason)
                return@runBlocking
            }
            executionScope.cancel()
            try {
                hostRelay.close()
            } finally {
                try {
                    clientHost.close()
                } finally {
                    shutdownDesktopResources(account::shutdown, profiledHost::close)
                    resourcesReleased = true
                }
            }
        }
    }
    // The window close request ended the application and the resources above are released, so the
    // process can finish deterministically instead of relying on AWT thread shutdown.
    if (resourcesReleased) exitProcess(0)
}

/** Desktop 主动连接已配置 RuoYi 后端的 Host 侧入口；它只注册 Host 并转发本机 Session 事件。 */
private fun createDesktopHostRelay(harnessHost: HarnessHost): DesktopHostRelay {
    val engine = platformAccountEngine()
    val client = createAccountHttpClient(engine)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val eventStream = HostEventStream(harnessHost.runtime.context, harnessHost.profile.hostDescription.hostId)
    return DesktopHostRelay(
        client = client,
        closeClient = {
            eventStream.close()
            client.close()
            engine.close()
        },
        gateway = harnessHost.gateway,
        description = harnessHost.profile.hostDescription,
        events = eventStream.events,
        scope = scope,
    )
}

private fun createDesktopAccount(home: Path?): MemberAccount {
    val store = desktopAppSessionStore(resolveDesktopHarnessHome(home).directory)
    return createOwnedAccount(
        createEngine = ::platformAccountEngine,
        createClient = ::createAccountHttpClient,
        createTransport = { engine, client -> KtorMemberTransport(client, engine) },
        createAccount = { transport -> MemberAccount(transport = transport, store = store) },
    )
}

private data class DesktopLaunchOptions(
    val home: Path? = null,
    val patches: List<Path> = emptyList(),
    val windowSize: WindowSize = WindowSize(800, 600),
    val destination: HarnessDestination = HarnessDestination.Harness,
    val themeMode: HarnessThemeMode = HarnessThemeMode.System,
    val themeSpecified: Boolean = false,
    val accountMenuOpen: Boolean = false,
) {
    companion object {
        fun parse(arguments: Array<String>): DesktopLaunchOptions {
            var home: Path? = null
            val patches = mutableListOf<Path>()
            var windowSize = WindowSize(800, 600)
            var destination = HarnessDestination.Harness
            var themeMode = HarnessThemeMode.System
            var themeSpecified = false
            var accountMenuOpen = false
            var index = 0
            while (index < arguments.size) {
                val argument = arguments[index]
                fun value(option: String): String {
                    index += 1
                    require(index < arguments.size) { "$option requires a value" }
                    return arguments[index]
                }
                when {
                    argument == "--home" -> home = Path.of(value(argument))
                    argument.startsWith("--home=") -> home = Path.of(argument.substringAfter('='))
                    argument == "--patch" -> patches.add(Path.of(value(argument)))
                    argument.startsWith("--patch=") -> patches.add(Path.of(argument.substringAfter('=')))
                    argument == "--window-size" -> windowSize = WindowSize.parse(value(argument))
                    argument.startsWith("--window-size=") -> windowSize = WindowSize.parse(argument.substringAfter('='))
                    argument == "--destination" -> destination = HarnessDestination.fromLaunchValue(value(argument))
                    argument.startsWith("--destination=") -> destination = HarnessDestination.fromLaunchValue(argument.substringAfter('='))
                    argument == "--theme" -> { themeMode = parseThemeMode(value(argument)); themeSpecified = true }
                    argument.startsWith("--theme=") -> { themeMode = parseThemeMode(argument.substringAfter('=')); themeSpecified = true }
                    argument == "--account-menu-open" -> accountMenuOpen = true
                    else -> error("Unknown Desktop option: $argument")
                }
                index += 1
            }
            return DesktopLaunchOptions(home, patches, windowSize, destination, themeMode, themeSpecified, accountMenuOpen)
        }
    }
}

/** `--theme system|light|dark`, defaulting to the host appearance. */
private fun parseThemeMode(value: String): HarnessThemeMode =
    when (value.lowercase()) {
        "system" -> HarnessThemeMode.System
        "light" -> HarnessThemeMode.Light
        "dark" -> HarnessThemeMode.Dark
        else -> error("Theme must be system, light or dark, but was: $value")
    }

private data class WindowSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Window size must be positive" }
    }

    companion object {
        fun parse(value: String): WindowSize {
            val match = Regex("(\\d+)x(\\d+)").matchEntire(value)
                ?: error("Window size must use WIDTHxHEIGHT, for example 1440x900")
            return WindowSize(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }
}

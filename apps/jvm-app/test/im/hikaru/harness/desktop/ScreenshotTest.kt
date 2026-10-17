package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.fixture.HarnessReferenceFixture
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.page.ProjectContextPanel
import im.hikaru.harness.client.page.SettingsGeneralPage
import im.hikaru.harness.client.page.ThreadPage
import im.hikaru.harness.client.settings.GeneralSettingsState
import im.hikaru.harness.client.shell.AppShell
import im.hikaru.harness.client.shell.AppRail
import im.hikaru.harness.client.shell.AppShellRailItem
import im.hikaru.harness.client.shell.AppShellState
import im.hikaru.harness.client.shell.AccountMenuState
import im.hikaru.harness.client.shell.ConnectionIndicatorState
import im.hikaru.harness.client.shell.ExploreMenuState
import im.hikaru.harness.client.shell.NavigationState
import im.hikaru.harness.client.shell.PageId
import im.hikaru.harness.client.shell.RecentTaskItem
import im.hikaru.harness.client.shell.SidebarMode
import im.hikaru.harness.client.shell.SidebarState
import im.hikaru.harness.client.shell.SettingsNavigatorState
import im.hikaru.harness.client.shell.ThreadSurfaceState
import im.hikaru.harness.client.shell.WorkspaceNavigatorState
import im.hikaru.harness.client.shell.WorkspaceItemKind
import im.hikaru.harness.client.shell.WorkspaceItemNode
import im.hikaru.harness.client.shell.WorkspaceProjectNode
import im.hikaru.harness.client.shell.harnessRailItems
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessBadge
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the shared shell off-screen at the reference sizes in both palettes so layout and theme
 * regressions show up without a display. ImageComposeScene renders app content only: native macOS
 * title-bar pixels are captured separately by `scripts/capture-desktop-window.sh`.
 */
class ScreenshotTest {
    @Test
    fun renderModelSettingsStates() {
        val dir = File(captureDir(), "models").apply { mkdirs() }
        val configured = ModelUiBackend().snapshot
        val unconfigured = configured.copy(providers = listOf(
            im.hikaru.harness.client.settings.ConfigurableProvider("openai", "OpenAI", credentialWritable = true),
        ))
        val states = listOf(
            "configured" to configured,
            "unconfigured" to unconfigured,
            "unavailable" to im.hikaru.harness.client.settings.ModelSettingsSnapshot(),
        )
        listOf(CaptureSize(1440, 900), CaptureSize(600, 800)).forEach { size ->
            captureThemes.forEach { theme ->
                states.forEach { (slug, snapshot) ->
                    render(dir, "models-" + slug + "-" + theme.slug + "-" + size.fileSuffix + ".png", size) {
                        val page = PageId.Settings(im.hikaru.harness.client.shell.SettingsSection.Models)
                        AppShell(
                            state = AppShellState(navigation = NavigationState(currentPage = page),
                                settingsNavigator = SettingsNavigatorState(selectedSlug = "models", mode = if (size.width < 800) SidebarMode.Collapsed else SidebarMode.Expanded)),
                            navItems = navItems(page), onNavSelect = {}, navigator = WorkspaceNavigatorState(),
                            projectNodes = emptyList(), recentTasks = emptyList(),
                            onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {}, onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},

                            content = {
                                im.hikaru.harness.client.page.SettingsModelsPage(
                                    im.hikaru.harness.client.settings.ModelSettingsState(snapshot = snapshot, loaded = true, loading = false),
                                    {}, { _, _ -> }, { _, _ -> }, { _, _ -> },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun renderAppearanceSettingsStates() {
        val dir = File(captureDir(), "appearance").apply { mkdirs() }
        listOf(CaptureSize(1440, 900), CaptureSize(600, 800), CaptureSize(1440, 2400)).forEach { size ->
            captureThemes.forEach { theme ->
                listOf(false, true).forEach { advanced ->
                    render(dir, "appearance-" + (if (advanced) "advanced-" else "visual-") + theme.slug + "-" + size.fileSuffix + ".png", size) {
                        val page = PageId.Settings(im.hikaru.harness.client.shell.SettingsSection.Appearance)
                        val settings = im.hikaru.harness.client.settings.AppearanceSettings(mode = im.hikaru.harness.client.settings.AppearanceMode.from(theme.mode))
                        AppShell(
                            state = AppShellState(navigation = NavigationState(currentPage = page),
                                settingsNavigator = SettingsNavigatorState(selectedSlug = "appearance", mode = if (size.width < 800) SidebarMode.Collapsed else SidebarMode.Expanded)),
                            navItems = navItems(page), onNavSelect = {}, navigator = WorkspaceNavigatorState(),
                            projectNodes = emptyList(), recentTasks = emptyList(),
                            onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {}, onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},

                            content = {
                                im.hikaru.harness.client.page.SettingsAppearancePage(
                                    im.hikaru.harness.client.settings.AppearanceState(settings, loading = false, durable = true), {}, {}, advanced,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun renderScopedNavigationSnapshots() {
        val dir = File(captureDir(), "scope").apply { mkdirs() }
        listOf(CaptureSize(1440, 900), CaptureSize(600, 800)).forEach { size ->
            captureThemes.forEach { theme ->
                render(dir, "scheduled-" + theme.slug + "-" + size.fileSuffix + ".png", size) {
                    HarnessApp(connection = null, initialDestination = HarnessDestination.ScheduledTasks, themeMode = theme.mode)
                }
                render(dir, "more-" + theme.slug + "-" + size.fileSuffix + ".png", size) {
                    ExploreMenuShell(theme.mode)
                }
            }
        }
    }

    @Test
    fun renderReferenceScreens() {
        val dir = captureDir()
        val written = mutableListOf<String>()
        captureSizes.forEach { size ->
            val suffix = size.fileSuffix
            captureThemes.forEach { theme ->
                written += render(dir, "reference-thread-" + theme.slug + "-$suffix.png", size) { ReferenceThreadShell(theme.mode) }
                written += render(dir, "home-" + theme.slug + "-$suffix.png", size) { HomeShell(theme.mode) }
                written += render(dir, "account-" + theme.slug + "-$suffix.png", size) { AccountShell(theme.mode) }
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "screenshot not written: " + path)
        }
        println("SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Rail acceptance: the rail must stay mounted and unchanged while the workspace navigator is
     * expanded, and stay mounted when the navigator is collapsed. Both states render the same thread
     * page at the reference size so the rail strip can be compared against the Codex window capture.
     */
    @Test
    fun renderRailNavigatorStates() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(1440, 900)
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            // The original light files keep their names; the dark pass adds the -dark variant.
            val suffix = if (theme.slug == "light") "" else "-" + theme.slug
            listOf("expanded" to SidebarMode.Expanded, "collapsed" to SidebarMode.Collapsed).forEach { (state, mode) ->
                written +=
                    render(dir, "rail-navigator-" + state + suffix + "-1440x900.png", size) {
                        ReferenceThreadShell(theme.mode, mode)
                    }
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "rail screenshot not written: " + path)
        }
        println("RAIL SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Compact width class: below the rail breakpoint the fixed rail is replaced by the bottom bar
     * and the workspace navigator becomes a drawer. Both compact states render off-screen, so a
     * regression in the swapped shell shows up as a crash or an empty image without a display.
     */
    @Test
    fun renderCompactShellStates() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(600, 800)
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            written += render(dir, "rail-bottombar-" + theme.slug + "-600x800.png", size) {
                ReferenceThreadShell(theme.mode, SidebarMode.Collapsed)
            }
            written += render(dir, "rail-bottombar-navigator-" + theme.slug + "-600x800.png", size) {
                ReferenceThreadShell(theme.mode, SidebarMode.Expanded)
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "compact screenshot not written: " + path)
        }
        println("COMPACT SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Floating drawer: the scrim and the drawer above the content, in both palettes. The drawer is
     * forced with [SidebarMode.TemporarilyExpanded] because at 1440dp a rail presentation pushes
     * instead of floating.
     */
    @Test
    fun renderFloatingNavigatorScrim() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(1440, 900)
        val written =
            captureThemes.map { theme ->
                render(dir, "rail-drawer-scrim-" + theme.slug + "-1440x900.png", size) {
                    ReferenceThreadShell(theme.mode, SidebarMode.TemporarilyExpanded)
                }
            }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "scrim screenshot not written: " + path)
        }
        println("SCRIM SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Rail component sheet: the rail alone, rendered once per selected destination, so the 36dp
     * item with its 10dp corner, the solid selected glyph, the 24px group rule and the bottom account
     * chip can be inspected without the navigator, titlebar or main surface around them.
     */
    @Test
    fun renderRailComponentSheet() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            val name = if (theme.slug == "light") "rail-component-sheet-1440x900.png" else "rail-component-sheet-" + theme.slug + "-1440x900.png"
            written +=
                render(dir, name, CaptureSize(1440, 900)) {
                    HarnessTheme(themeMode = theme.mode) {
                        Row(modifier = Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)) {
                            listOf(
                                PageId.Home,
                                PageId.Library,
                                PageId.Projects(),
                                PageId.Plugins,
                                PageId.Account,
                            ).forEach { selection ->
                                AppRail(navItems = navItems(selection), onNavSelect = {})
                            }
                        }
                    }
                }
        }
        written.forEach { path ->
            assertTrue(File(path).isFile && File(path).length() > 0, "rail sheet not written: " + path)
        }
        println("RAIL SHEET: " + written.joinToString(", "))
    }

    /**
     * 探索 pin-menu acceptance: the 探索 button opens its own overlay menu (not the account chip
     * menu) listing the pinnable destinations, in both palettes, with a top-left crop.
     */
    @Test
    fun renderExploreMenuStates() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(1440, 900)
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            val name = "explore-menu-open-" + theme.slug + "-1440x900"
            val full = render(dir, name + ".png", size) { ExploreMenuShell(theme.mode) }
            written += full
            val crop = File(dir, name + "-crop.png")
            cropRegion(full, crop.absolutePath, x = 0, y = 180, width = 380, height = 300)
            written += crop.absolutePath
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "rail overflow screenshot not written: " + path)
        }
        println("RAIL OVERFLOW SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Page-owned navigator acceptance: Settings, Plugins and Hosts each mount their own
     * dedicated navigator instead of the Workspace project tree, and Account mounts none at all.
     * Rendering the shell once per page off-screen proves the mounted navigator follows the page's
     * pageShellSpec rather than a single global Workspace tree.
     */
    @Test
    fun renderPageOwnedNavigators() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(1440, 900)
        val pages =
            listOf(
                Triple("settings", PageId.Settings(), "设置"),
                Triple("plugins", PageId.Plugins, "插件"),
                Triple("account", PageId.Account, "账号"),
            )
        val written =
            pages.map { (slug, page, title) ->
                render(dir, "page-navigator-" + slug + "-light-1440x900.png", size) {
                    PageOwnedNavigatorShell(page, HarnessThemeMode.Light, title)
                }
            }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "page navigator screenshot not written: " + path)
        }
        println("PAGE NAVIGATOR SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Workspace Navigator batch-1 acceptance. Renders the fixed [CodexWorkspaceNavigatorFixture] in
     * both palettes for the expanded, collapsed and header-search-open states at 1440x900, and also
     * writes a 340px component crop (rail 52 + navigator 288) per state so the navigator can be
     * compared component-by-component against the Codex reference capture.
     */
    @Test
    fun renderWorkspaceNavigatorStates() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(1440, 900)
        val states =
            listOf(
                NavigatorCapture("expanded", SidebarMode.Expanded, searchOpen = false, query = ""),
                NavigatorCapture("collapsed", SidebarMode.Collapsed, searchOpen = false, query = ""),
                NavigatorCapture("search-open", SidebarMode.Expanded, searchOpen = true, query = "DSH"),
            )
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            states.forEach { capture ->
                val name = "workspace-navigator-" + capture.name + "-" + theme.slug + "-1440x900"
                val full =
                    render(dir, name + ".png", size) {
                        WorkspaceNavigatorShell(theme.mode, capture.mode, capture.searchOpen, capture.query)
                    }
                written += full
                val crop = File(dir, name + "-crop.png")
                cropLeft(full, crop.absolutePath, width = 340)
                written += crop.absolutePath
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "workspace navigator screenshot not written: " + path)
        }
        println("WORKSPACE NAVIGATOR SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Account/Profile menu acceptance. The overlay is actually open (the avatar menu, not the Account
     * page) and rendered over PageId.Home in both palettes, plus the signed-out structure. Each state
     * also writes a bottom-left crop containing the rail avatar and the anchored menu.
     */
    @Test
    fun renderAccountMenuStates() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val size = CaptureSize(1440, 900)
        val states =
            listOf(
                AccountMenuCapture("light", HarnessThemeMode.Light, signedIn = true),
                AccountMenuCapture("dark", HarnessThemeMode.Dark, signedIn = true),
                AccountMenuCapture("signed-out-light", HarnessThemeMode.Light, signedIn = false),
            )
        val written = mutableListOf<String>()
        states.forEach { capture ->
            val name = "account-menu-" + capture.name + "-1440x900"
            val full =
                render(dir, name + ".png", size) {
                    AccountMenuShell(capture.mode, capture.signedIn)
                }
            written += full
            val crop = File(dir, name + "-crop.png")
            cropRegion(full, crop.absolutePath, x = 0, y = 520, width = 360, height = 380)
            written += crop.absolutePath
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "account menu screenshot not written: " + path)
        }
        println("ACCOUNT MENU SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Navigator-affordance acceptance for a [PageId.Account] page, which mounts [NavigatorKind.None].
     * A page that owns no navigator must draw no titlebar hamburger and no compact bottom-bar
     * navigator entry, at both the desktop (1440x900, rail) and compact (600x800, bottom bar) widths,
     * while every other page keeps its toggle. Rendered off-screen in both palettes.
     */
    @Test
    fun renderNavigatorNoneAffordances() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            listOf(CaptureSize(1440, 900), CaptureSize(600, 800)).forEach { size ->
                written +=
                    render(dir, "account-no-navigator-" + theme.slug + "-" + size.fileSuffix + ".png", size) {
                        PageOwnedNavigatorShell(PageId.Account, theme.mode, "账号")
                    }
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "account affordance screenshot not written: " + path)
        }
        println("ACCOUNT AFFORDANCE SCREENSHOTS: " + written.joinToString(", "))
    }

    /**
     * Settings-navigator acceptance. Codex's Settings page owns a `sidebar-navigation` (app.asar
     * `settings-page-b07b6e77dc14.js`: `settings.nav.title`, `settings.search.*`), so this renders that
     * page with its navigator open and collapsed at the desktop (1440x900, rail) and compact (600x800,
     * bottom bar drawer) widths, in both palettes. The navigator body is the 2026-10-06 capture's
     * title + search + grouped rows; it draws no back row and no collapse control.
     */
    @Test
    fun renderSettingsNavigatorStates() {
        val dir = File(captureDir(), "rail").apply { mkdirs() }
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            listOf("expanded" to SidebarMode.Expanded, "collapsed" to SidebarMode.Collapsed).forEach { (state, mode) ->
                listOf(CaptureSize(1440, 900), CaptureSize(600, 800)).forEach { size ->
                    written +=
                        render(dir, "settings-navigator-" + state + "-" + theme.slug + "-" + size.fileSuffix + ".png", size) {
                            SettingsNavigatorShell(theme.mode, mode)
                        }
                }
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "settings navigator screenshot not written: " + path)
        }
        println("SETTINGS NAVIGATOR SCREENSHOTS: " + written.joinToString(", "))
    }

    /** Settings General acceptance at the supplied desktop and compact reference sizes. */
    @Test
    fun renderSettingsGeneralStates() {
        val dir = File(captureDir(), "general-runtime").apply { mkdirs() }
        val written = mutableListOf<String>()
        captureThemes.forEach { theme ->
            listOf(CaptureSize(2560, 1440), CaptureSize(1440, 900), CaptureSize(600, 800), CaptureSize(1440, 1800)).forEach { size ->
                written +=
                    render(dir, "general-" + theme.slug + "-" + size.fileSuffix + ".png", size) {
                        SettingsGeneralShell(theme.mode, if (size.width < 800) SidebarMode.Collapsed else SidebarMode.Expanded)
                    }
            }
        }
        written.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0, "screenshot not written: " + path)
        }
        println("SETTINGS GENERAL SCREENSHOTS: " + written.joinToString(", "))
    }

    private fun render(
        dir: File,
        name: String,
        size: CaptureSize,
        content: @Composable () -> Unit,
    ): String {
        val scene =
            ImageComposeScene(
                width = size.width,
                height = size.height,
                density = Density(1f),
                content = content,
            )
        try {
            scene.render()
            val image = scene.render()
            val data = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "encode failed" }
            val file = File(dir, name)
            file.writeBytes(data.bytes)
            return file.absolutePath
        } finally {
            scene.close()
        }
    }

    private data class CaptureSize(
        val width: Int,
        val height: Int,
    ) {
        val fileSuffix: String get() = "$width" + "x" + height
    }

    private data class CaptureTheme(
        val slug: String,
        val mode: HarnessThemeMode,
    )

    private companion object {
        val captureSizes =
            listOf(
                CaptureSize(2560, 1440),
                CaptureSize(1440, 900),
                CaptureSize(1280, 800),
                CaptureSize(1024, 768),
            )

        val captureThemes =
            listOf(
                CaptureTheme("light", HarnessThemeMode.Light),
                CaptureTheme("dark", HarnessThemeMode.Dark),
            )
    }

    private fun captureDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val current = dir ?: return@repeat
            if (File(current, "docs/desktop-ui").isDirectory) {
                val target = File(current, "docs/desktop-ui/captures")
                target.mkdirs()
                return target
            }
            dir = current.parentFile
        }
        return File("/tmp/kmp-harness-captures").apply { mkdirs() }
    }

    /** Left-column component crop: the fixed rail (52px) plus the workspace navigator (288px). */
    private fun cropLeft(sourcePath: String, destinationPath: String, width: Int) {
        val source = ImageIO.read(File(sourcePath))
        val crop = source.getSubimage(0, 0, width, source.height)
        ImageIO.write(crop, "png", File(destinationPath))
    }

    /** Arbitrary component crop, used for the anchored account menu in the window's bottom-left. */
    private fun cropRegion(sourcePath: String, destinationPath: String, x: Int, y: Int, width: Int, height: Int) {
        val source = ImageIO.read(File(sourcePath))
        val crop = source.getSubimage(x, y, width, height)
        ImageIO.write(crop, "png", File(destinationPath))
    }
}

/** One account-menu capture state: file slug, palette and whether the account is signed in. */
private data class AccountMenuCapture(
    val name: String,
    val mode: HarnessThemeMode,
    val signedIn: Boolean,
)

/** One workspace-navigator capture state: name, drawer mode and the header search row. */
private data class NavigatorCapture(
    val name: String,
    val mode: SidebarMode,
    val searchOpen: Boolean,
    val query: String,
)

@Composable
private fun ReferenceThreadShell(
    themeMode: HarnessThemeMode,
    sidebarMode: SidebarMode = SidebarMode.Expanded,
) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = sidebarMode),
                navigation =
                    NavigationState(
                        currentPage = PageId.Thread(fixture.title),
                        backStack = listOf(PageId.Projects(), PageId.Project("kmp-harness")),
                        selectedThreadId = "view-project-status",
                        expandedProjectIds = setOf("kmp-harness"),
                    ),
                thread = ThreadSurfaceState(threadId = "view-project-status", title = fixture.title),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(PageId.Projects()),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地", mode = sidebarMode),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},
        onOpenProjectContext = {},

        composer = { ReferenceLiveComposer(thread = true) },
        contextPanel = { ProjectContextPanel(thread = fixture) },
        content = { ThreadPage(thread = fixture) },
    )
}

@Composable
private fun HomeShell(themeMode: HarnessThemeMode) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = SidebarMode.Expanded),
                navigation =
                    NavigationState(
                        currentPage = PageId.Home,
                        expandedProjectIds = setOf("kmp-harness"),
                    ),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(PageId.Home),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {
            im.hikaru.harness.client.app.LiveHomeSurface(fixture.projectName) { ReferenceLiveComposer(thread = false) }
        },
    )
}

@Composable
private fun ReferenceLiveComposer(thread: Boolean) {
    im.hikaru.harness.client.app.LiveConversationComposer(
        state = im.hikaru.harness.client.conversation.ConversationState(
            selectedSessionId = if (thread) im.hikaru.harness.client.connection.SessionId("screenshot") else null,
        ),
        onDraftChange = {}, onSubmit = {}, onCancel = {}, models = emptyList(), selectedModel = null,
        onModelSelect = {}, modelUnavailableReason = null, submitUnavailableReason = null,
        permission = im.hikaru.harness.client.app.ComposerPermission.Ask, onPermissionSelect = {},
    )
}
@Composable
private fun AccountShell(themeMode: HarnessThemeMode) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = SidebarMode.Expanded),
                navigation =
                    NavigationState(
                        currentPage = PageId.Account,
                        expandedProjectIds = setOf("kmp-harness"),
                    ),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(PageId.Account),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
            ) {
                HarnessText("账号", color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.titleLarge)
                HarnessSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four),
                        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HarnessBadge("在线", color = HarnessTokens.Colors.Success)
                        Column(modifier = Modifier.weight(1f)) {
                            HarnessText("连接状态", color = HarnessTokens.Colors.Text)
                            HarnessText("KMP Harness Desktop", color = HarnessTokens.Colors.TextMuted)
                        }
                    }
                }
                HarnessText("本地模式无需登录", color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.titleMedium)
                HarnessText("连接本地 Harness 不需要远程账号。", color = HarnessTokens.Colors.TextMuted)
                // The account panel is the shell overflow: Host and Settings open from here.
                AccountEntryRow("Host", HarnessIconName.Host)
                AccountEntryRow("设置", HarnessIconName.Settings)
                HarnessSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four),
                        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                    ) {
                        HarnessText("工作区", color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium)
                        HarnessText(fixture.context.workspacePath, color = HarnessTokens.Colors.Text)
                        HarnessText("Host：" + fixture.context.host, color = HarnessTokens.Colors.TextMuted)
                    }
                }
            }
        },
    )
}

/**
 * A page whose navigator is not the Workspace tree: Settings, Plugins, Hosts and Account all
 * mount through this fixture so the shell renders the navigator the page owns. The Workspace
 * navigator/project nodes are still passed, because [AppShell] requires them, but a non-Workspace
 * page never mounts them.
 */
@Composable
private fun PageOwnedNavigatorShell(
    page: PageId,
    themeMode: HarnessThemeMode,
    title: String,
) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = SidebarMode.Expanded),
                navigation = NavigationState(currentPage = page),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(page),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {
            Column(modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four)) {
                HarnessText(title, color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

/**
 * The Settings page with its own navigator, so the capture proves the page-owned frame (header title,
 * search field, three grouped row runs) and that no Workspace tree, New Chat or recent-task state
 * leaks in. [mode] drives both the shell presentation and the navigator drawer.
 */
@Composable
private fun SettingsNavigatorShell(
    themeMode: HarnessThemeMode,
    mode: SidebarMode,
) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = mode),
                navigation =
                    NavigationState(
                        currentPage = PageId.Settings(),
                        backStack = listOf(PageId.Home),
                    ),
                settingsNavigator =
                    SettingsNavigatorState(
                        mode = mode,
                        selectedSlug = "general",
                    ),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(PageId.Settings()),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {
            Column(modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four)) {
                HarnessText("设置", color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.titleLarge)
            }
        },
    )
}

@Composable
private fun SettingsGeneralShell(themeMode: HarnessThemeMode, navigatorMode: SidebarMode) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = SidebarMode.Expanded),
                navigation = NavigationState(currentPage = PageId.Settings()),
                settingsNavigator = SettingsNavigatorState(selectedSlug = "general", mode = navigatorMode),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(PageId.Settings()),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {
            SettingsGeneralPage(
                state = GeneralSettingsState(closePolicyAvailable = true, durable = true, loading = false, loaded = true),
                onOpenPlugins = {},
            )
        },
    )
}

/**
 * Opens the rail account-menu overlay over the Home page. The current page is deliberately not the
 * Account page, so the capture proves the menu is an overlay anchored to the rail avatar.
 */
@Composable
private fun AccountMenuShell(
    themeMode: HarnessThemeMode,
    signedIn: Boolean,
) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = SidebarMode.Expanded),
                navigation = NavigationState(currentPage = PageId.Home),
                connection = ConnectionIndicatorState.Local,
                accountMenu =
                    AccountMenuState(
                        isOpen = true,
                        identityLabel = if (signedIn) "Codex 用户" else "本地模式",
                        modeLabel = "本地",
                        connectionLabel = "已连接",
                        isLoggedIn = signedIn,
                        canLogin = !signedIn,
                    ),
            ),
        navItems = navItems(PageId.Home),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {},
    )
}

/** Rail More overflow fixture: the rail overflow menu actually open over PageId.Home. */
@Composable
private fun ExploreMenuShell(themeMode: HarnessThemeMode) {
    val fixture = HarnessReferenceFixture.thread
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = SidebarMode.Expanded),
                navigation = NavigationState(currentPage = PageId.Home),
                connection = ConnectionIndicatorState.Local,
                exploreMenu = ExploreMenuState(isOpen = true),
            ),
        navItems = navItems(PageId.Home),
        onNavSelect = {},
        navigator = WorkspaceNavigatorState(workspaceLabel = fixture.projectName, workspaceId = "本地"),
        projectNodes = fixture.projects,
        recentTasks = fixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {},
    )
}

/**
 * Fixed Workspace Navigator fixture. Every capture reuses the same projects, recents, selection and
 * expansion, so the navigator states are reproducible and can be compared to the Codex reference.
 */
private object CodexWorkspaceNavigatorFixture {
    const val workspaceLabel: String = "Codex"
    const val selectedThreadId: String = "view-project-status"
    val expandedProjectIds: Set<String> = setOf("kmp-harness")
    val projects: List<WorkspaceProjectNode> =
        listOf(
            WorkspaceProjectNode(
                projectId = "kmp-harness",
                name = "KMP Harness",
                pinned = true,
                children =
                    listOf(
                        WorkspaceItemNode("koog-slots", "实施 DSH Koog 同步与 UI Slots", kind = WorkspaceItemKind.Thread),
                        WorkspaceItemNode("view-project-status", "查看项目状态", kind = WorkspaceItemKind.Task),
                    ),
            ),
            WorkspaceProjectNode(projectId = "hackintosh", name = "黑苹果"),
        )
    val recentTasks: List<RecentTaskItem> =
        listOf(
            RecentTaskItem("koog-slots", "实施 DSH Koog 同步与 UI Slots"),
            RecentTaskItem("view-project-status", "查看项目状态"),
            RecentTaskItem("clash-rule", "添加 Clash IP 直连规则"),
            RecentTaskItem("rog-shortcut", "修复 ROG 快捷键"),
            RecentTaskItem("codex-migrate", "请帮我安全迁移本机 Codex 的历史会话记录"),
        )
}

/** Renders the Workspace navigator inside the shell at one fixed state, for the navigator captures. */
@Composable
private fun WorkspaceNavigatorShell(
    themeMode: HarnessThemeMode,
    mode: SidebarMode,
    searchOpen: Boolean,
    query: String,
) {
    AppShell(
        themeMode = themeMode,
        state =
            AppShellState(
                sidebar = SidebarState(mode = mode),
                navigation =
                    NavigationState(
                        currentPage = PageId.Projects(),
                        selectedThreadId = CodexWorkspaceNavigatorFixture.selectedThreadId,
                        expandedProjectIds = CodexWorkspaceNavigatorFixture.expandedProjectIds,
                    ),
                connection = ConnectionIndicatorState.Local,
            ),
        navItems = navItems(PageId.Projects()),
        onNavSelect = {},
        navigator =
            WorkspaceNavigatorState(
                workspaceLabel = CodexWorkspaceNavigatorFixture.workspaceLabel,
                workspaceId = "本地",
                mode = mode,
                searchQuery = query,
            ),
        projectNodes = CodexWorkspaceNavigatorFixture.projects,
        recentTasks = CodexWorkspaceNavigatorFixture.recentTasks,
        onToggleSidebar = {},
        onNewTask = {},
        onNavigatorSearch = {},
        navigatorSearchOpen = searchOpen,
        onToggleProject = {},
        onSelectNode = {},
        onSelectRecent = {},

        content = {},
    )
}

/** The shipped Codex rail, so the sheet and the app render the same six slots plus the account chip. */
private fun navItems(selected: PageId): List<AppShellRailItem> = harnessRailItems(selected, accountInitials = "KH")

@Composable
private fun AccountEntryRow(
    label: String,
    icon: HarnessIconName,
) {
    HarnessSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessIcon(icon, tint = HarnessTokens.Colors.TextMuted)
            HarnessText(label, modifier = Modifier.weight(1f), color = HarnessTokens.Colors.Text)
        }
    }
}

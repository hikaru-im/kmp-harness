package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch B audit of the page -> navigator matrix.
 *
 * The [matrix] below is the audit anchor: one instance per `PageId` shape together with the navigator
 * the shell mounts for it. It is deliberately explicit so that adding a page shape, or moving a page to
 * another navigator, is a reviewed edit instead of a silent fallback. In particular no page-specific
 navigator may be folded onto [NavigatorKind.Workspace]; Scheduled Tasks has its own page and state,
 * so nothing here may reuse [WorkspaceNavigatorState] for it.
 */
class PageNavigatorMatrixAuditTest {
    /** One representative instance per `PageId` shape, with the navigator the shell mounts for it. */
    private val matrix: List<Pair<PageId, NavigatorKind>> =
        listOf(
            PageId.Home to NavigatorKind.Workspace,
            PageId.Library to NavigatorKind.None,
            PageId.Projects() to NavigatorKind.None,
            PageId.Project("kmp-harness") to NavigatorKind.None,
            PageId.Thread("thread-1") to NavigatorKind.Workspace,
            PageId.ScheduledTasks() to NavigatorKind.Scheduled,
            PageId.Settings() to NavigatorKind.Settings,
            PageId.Plugins to NavigatorKind.Plugins,
            PageId.PluginDetail("desktop-client") to NavigatorKind.Plugins,
            PageId.Account to NavigatorKind.None,
        )

    /**
     * The audit covers every `PageId` shape exactly once. `PageId` currently has ten shapes; adding
     * one without extending this list fails here, which keeps the page -> navigator map auditable.
     */
    @Test
    fun auditCoversEveryPageShapeExactlyOnce() {
        assertEquals(10, matrix.size, "one audit row per in-scope PageId shape")
        val pages = matrix.map { it.first }
        assertEquals(pages.size, pages.toSet().size, "no PageId shape is audited twice")
    }

    /** Every page resolves to its audited navigator, and the affordance flag follows the navigator kind. */
    @Test
    fun everyPageResolvesToItsAuditedNavigator() {
        matrix.forEach { (page, kind) ->
            val spec = pageShellSpec(page)
            assertEquals(kind, spec.navigator, "mounted navigator for " + page)
            assertEquals(kind != NavigatorKind.None, spec.mountsNavigator, "affordance flag for " + page)
            assertEquals(spec.mountsNavigator, navigatorTestTag(kind) != null, "semantic tag for " + page)
        }
    }

    /**
     * Every navigator kind except `None` is mounted by at least one page, and `None` is mounted only by
     * the pages that carry their own in-page layout. A new kind added without a page, or a page quietly
     * moved onto `None`, fails here.
     */
    @Test
    fun everyNavigatorKindExceptNoneIsMountedByAPage() {
        assertEquals(NavigatorKind.entries.toSet(), matrix.map { it.second }.toSet())
        assertEquals(
            setOf(PageId.Account, PageId.Library, PageId.Projects(), PageId.Project("kmp-harness")),
            matrix.filter { it.second == NavigatorKind.None }.map { it.first }.toSet(),
            "only the in-page-layout pages own no navigator",
        )
    }

    /**
     * The set of pages that own the shared Workspace tree is pinned. A page-specific navigator, and a
     * scheduled-tasks page, must never be added to this set: it has its own navigator kind and state
     * object, not [WorkspaceNavigatorState].
     */
    @Test
    fun onlyTheWorkspacePagesOwnTheWorkspaceNavigator() {
        val workspacePages = matrix.filter { it.second == NavigatorKind.Workspace }.map { it.first }.toSet()
        assertEquals(
            setOf(
                PageId.Home,
                PageId.Thread("thread-1"),
            ),
            workspacePages,
        )
        listOf(PageId.ScheduledTasks(), PageId.Settings(), PageId.Plugins).forEach { page ->
            assertFalse(
                pageShellSpec(page).navigator == NavigatorKind.Workspace,
                "page-specific navigator must not resolve to Workspace for " + page,
            )
        }
    }

    /** A `NavigatorKind.None` page draws no navigator affordance at all. */
    @Test
    fun nonePageDrawsNoNavigatorAffordance() {
        val spec = pageShellSpec(PageId.Account)
        assertEquals(NavigatorKind.None, spec.navigator)
        assertFalse(spec.mountsNavigator)
        assertFalse(spec.resizable)
        assertNull(navigatorTestTag(spec.navigator))
    }

    /**
     * The account menu, the rail More overflow and the thread context panel are overlays. Opening or
     * closing any of them never replaces the current page, so it never changes the mounted navigator.
     */
    @Test
    fun overlaysNeverChangeTheMountedNavigator() {
        var navigation = NavigationState(currentPage = PageId.Settings())
        val mounted = pageShellSpec(navigation.currentPage).navigator

        assertTrue(AccountMenuState().open().isOpen)
        assertTrue(ExploreMenuState().open().isOpen)

        navigation = navigation.openOverlay(OverlayId.ProjectContext("project-1"))
        assertEquals(PageId.Settings(), navigation.currentPage)
        assertEquals(mounted, pageShellSpec(navigation.currentPage).navigator)

        navigation = navigation.openOverlay(OverlayId.ModelPicker)
        assertEquals(PageId.Settings(), navigation.currentPage)
        assertEquals(mounted, pageShellSpec(navigation.currentPage).navigator)

        assertEquals(PageId.Settings(), navigation.closeOverlay().currentPage)
    }

    /**
     * A page-specific navigator state is a distinct value object and never carries the Workspace tree.
     * This is the state-level guard for the scheduled-tasks navigator as well.
     */
    @Test
    fun pageSpecificNavigatorStatesAreDistinctFromTheWorkspaceState() {
        val state =
            AppShellState(
                navigation = NavigationState(currentPage = PageId.Plugins),
                workspace = WorkspaceNavigatorState(searchQuery = "ws", mode = SidebarMode.Collapsed),
                settingsNavigator = SettingsNavigatorState(query = "settings"),
                pluginsNavigator = PluginsNavigatorState(query = "plugins"),
                scheduledTasksNavigator = im.hikaru.harness.client.scheduled.ScheduledTasksState.seed().copy(query = "scheduled"),
            )

        assertEquals("ws", state.workspace.searchQuery)
        assertEquals("settings", state.settingsNavigator.query)
        assertEquals("plugins", state.pluginsNavigator.query)
        assertEquals("scheduled", state.scheduledTasksNavigator.query)
        assertEquals(SidebarMode.Collapsed, state.workspace.mode)
        assertEquals(SidebarMode.Expanded, state.settingsNavigator.mode)
    }
}

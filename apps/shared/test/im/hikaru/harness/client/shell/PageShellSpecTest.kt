package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The page -> navigator matrix. The shell mounts the navigator the current page owns, and a page with
 * [NavigatorKind.None] has no drawer, so Settings/Account/Plugins can never show the Workspace
 * project tree.
 */
class PageShellSpecTest {
    @Test
    fun eachPageOwnsTheNavigatorTheShellMounts() {
        val workspacePages: List<PageId> =
            listOf(
                PageId.Home,
                PageId.Thread("thread-1"),
                PageId.NewTab,
            )
        workspacePages.forEach { page ->
            assertEquals(NavigatorKind.Workspace, pageShellSpec(page).navigator, "Workspace navigator for " + page)
        }

        assertEquals(NavigatorKind.Settings, pageShellSpec(PageId.Settings()).navigator)
        assertEquals(NavigatorKind.Plugins, pageShellSpec(PageId.Plugins).navigator)
        assertEquals(NavigatorKind.Plugins, pageShellSpec(PageId.PluginDetail("desktop-client")).navigator)
        assertEquals(NavigatorKind.Scheduled, pageShellSpec(PageId.ScheduledTasks()).navigator)
        // The reference mounts no navigator on the Library/Project pages or Account; those pages carry
        // their own in-page layout, so the Workspace project tree never leaks into them.
        val noNavigatorPages: List<PageId> =
            listOf(
                PageId.Library,
                PageId.Projects(),
                PageId.Project("kmp-harness"),
                PageId.Account,
            )
        noNavigatorPages.forEach { page ->
            assertEquals(NavigatorKind.None, pageShellSpec(page).navigator, "no navigator for " + page)
            if (page != PageId.Account) assertEquals(PageShellSpec.NoNavigator, pageShellSpec(page), "no navigator spec for " + page)
        }
    }

    @Test
    fun everyMountedNavigatorIsResizable() {
        assertTrue(pageShellSpec(PageId.Thread("thread-1")).resizable)
        assertTrue(pageShellSpec(PageId.Settings()).resizable)
        assertTrue(pageShellSpec(PageId.Plugins).resizable)
        assertTrue(pageShellSpec(PageId.ScheduledTasks()).resizable)
        assertFalse(pageShellSpec(PageId.Account).resizable)
    }

    /**
     * 新标签页 draws its own 768 column, which is wider than the shared cap the shell lays the other
     * pages out at (672), so it is the one page that asks for the whole surface. Every other page keeps
     * the shared column, so the Settings/thread layouts cannot be handed a bare surface by accident.
     */
    @Test
    fun workspaceManagementPagesAndNewTabOwnTheirContentColumns() {
        assertTrue(pageShellSpec(PageId.NewTab).ownContentColumn)
        listOf(PageId.Plugins, PageId.PluginDetail("desktop-client"), PageId.Account).forEach {
            assertTrue(pageShellSpec(it).ownContentColumn)
            assertFalse(pageShellSpec(it).docksComposer)
        }

        val sharedColumnPages: List<PageId> =
            listOf(
                PageId.Home,
                PageId.Thread("thread-1"),
                PageId.Settings(),
                PageId.ScheduledTasks(),
                PageId.Library,
                PageId.Projects(),
                PageId.Project("kmp-harness"),
            )
        sharedColumnPages.forEach { page ->
            assertFalse(pageShellSpec(page).ownContentColumn, "shared content column for " + page)
        }
    }

    @Test
    fun everyNavigatorHasItsOwnSemanticTagAndNoneHasNoTag() {
        val tags = NavigatorKind.entries.mapNotNull { navigatorTestTag(it) }

        assertNull(navigatorTestTag(NavigatorKind.None))
        // One tag per navigator kind except None, and no two kinds share a tag.
        assertEquals(NavigatorKind.entries.size - 1, tags.size)
        assertEquals(tags.size, tags.toSet().size)
        assertEquals("workspace-navigator", navigatorTestTag(NavigatorKind.Workspace))
        assertEquals("settings-navigator", navigatorTestTag(NavigatorKind.Settings))
        assertEquals("plugins-navigator", navigatorTestTag(NavigatorKind.Plugins))
        assertEquals("scheduled-navigator", navigatorTestTag(NavigatorKind.Scheduled))
    }

    @Test
    fun pageNavigatorStatesDoNotShareWorkspaceData() {
        val state =
            AppShellState(
                navigation = NavigationState(currentPage = PageId.Settings()),
                workspace = WorkspaceNavigatorState(searchQuery = "ws-search", mode = SidebarMode.Collapsed),
                settingsNavigator = SettingsNavigatorState(selectedSlug = "plugins", query = "settings-search"),
                pluginsNavigator = PluginsNavigatorState(query = "plugins-search", enabledOnly = true),
            )

        // Each navigator keeps its own data; none of them reads the Workspace search.
        assertEquals("ws-search", state.workspace.searchQuery)
        assertEquals("settings-search", state.settingsNavigator.query)
        assertEquals("plugins-search", state.pluginsNavigator.query)
        assertEquals("plugins", state.settingsNavigator.selectedSlug)
        assertTrue(state.pluginsNavigator.enabledOnly)
        assertTrue(state.workspace.mode == SidebarMode.Collapsed)

        // One navigator mode for the whole app. The app holds the mode and writes it into every
        // navigator it hands the shell, so collapsing on one page collapses all of them; the content
        // above the mode stays page-owned.
        val collapsed = state.workspace.mode.toggled()
        val shared =
            state.copy(
                workspace = state.workspace.copy(mode = collapsed),
                settingsNavigator = state.settingsNavigator.copy(mode = collapsed),
                pluginsNavigator = state.pluginsNavigator.copy(mode = collapsed),
            )

        assertEquals(SidebarMode.Expanded, shared.workspace.mode)
        assertEquals(shared.workspace.mode, shared.settingsNavigator.mode)
        assertEquals(shared.workspace.mode, shared.pluginsNavigator.mode)
        // Every page still owns its own content while sharing the one mode.
        assertEquals("settings-search", shared.settingsNavigator.query)
        assertEquals("plugins-search", shared.pluginsNavigator.query)
        assertEquals("plugins", shared.settingsNavigator.selectedSlug)
        // The original state is unchanged, because each navigator is a value object.
        assertEquals(SidebarMode.Collapsed, state.workspace.mode)
        assertEquals(SidebarMode.Expanded, state.settingsNavigator.mode)
    }

    /**
     * A page that mounts [NavigatorKind.None] has nothing to collapse, so the shell must draw none of
     * the navigator affordances for it: no titlebar toggle, no compact bottom-bar navigator entry, no
     * drawer/scrim and no resize handle. Every page that does mount a navigator keeps them.
     */
    @Test
    fun navigatorNonePageDrawsNoNavigatorAffordance() {
        val nonePages: List<PageId> =
            listOf(
                PageId.Account,
                PageId.Library,
                PageId.Projects(),
                PageId.Project("kmp-harness"),
            )
        nonePages.forEach { page ->
            val spec = pageShellSpec(page)
            assertFalse(spec.mountsNavigator, page.toString() + " must mount no navigator")
            assertFalse(spec.resizable, page.toString() + " has no resize handle")
            assertNull(navigatorTestTag(spec.navigator), page.toString() + " has no navigator tag")
        }

        val mountedPages: List<PageId> =
            listOf(
                PageId.Home,
                PageId.Thread("thread-1"),
                PageId.NewTab,
                PageId.Settings(),
                PageId.Plugins,
                PageId.PluginDetail("desktop-client"),
                PageId.ScheduledTasks(),
            )
        mountedPages.forEach { page ->
            val spec = pageShellSpec(page)
            assertTrue(spec.mountsNavigator, "navigator affordance must stay for " + page)
            assertTrue(navigatorTestTag(spec.navigator) != null, "navigator tag must stay for " + page)
        }
    }

    /**
     * The shell reads the navigator from the current page on every frame, so walking a page sequence
     * never leaves the previous page's navigator mounted. This is the state-level guard against a
     * stale Workspace tree leaking into Settings/Plugins/Account.
     */
    @Test
    fun switchingPagesResolvesTheNavigatorFromTheCurrentPageOnly() {
        val steps =
            listOf(
                PageId.Home to NavigatorKind.Workspace,
                PageId.Settings() to NavigatorKind.Settings,
                PageId.Plugins to NavigatorKind.Plugins,
                PageId.PluginDetail("desktop-client") to NavigatorKind.Plugins,
                PageId.ScheduledTasks() to NavigatorKind.Scheduled,
                PageId.Account to NavigatorKind.None,
                PageId.Thread("thread-1") to NavigatorKind.Workspace,
            )
        var navigation = NavigationState()
        steps.forEach { (page, expected) ->
            navigation = navigation.navigate(page)
            assertEquals(page, navigation.currentPage)
            assertEquals(expected, pageShellSpec(navigation.currentPage).navigator, "mounted navigator for " + page)
        }
    }

    /**
     * The rail More overflow and the account/profile menu are their own state objects, and the thread
     * project-context panel is a [NavigationState] overlay. None of them replaces the current page, so
     * opening or closing one never changes which navigator is mounted.
     */
    @Test
    fun overlaysNeverChangeTheMountedNavigator() {
        val navigation = NavigationState(currentPage = PageId.Home)
        val mountedBefore = pageShellSpec(navigation.currentPage).navigator

        assertTrue(ExploreMenuState().open().isOpen)
        assertTrue(AccountMenuState().open().isOpen)

        val withOverlay = navigation.openOverlay(OverlayId.ProjectContext("project-1"))
        assertEquals(PageId.Home, withOverlay.currentPage)
        assertEquals(mountedBefore, pageShellSpec(withOverlay.currentPage).navigator)
        assertEquals(PageId.Home, withOverlay.closeOverlay().currentPage)
    }
}

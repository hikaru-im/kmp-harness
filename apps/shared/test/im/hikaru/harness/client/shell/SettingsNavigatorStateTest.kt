package im.hikaru.harness.client.shell

import im.hikaru.harness.ui.foundation.SettingsNavIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsNavigatorStateTest {
    @Test
    fun visibleRowsMatchTheConfirmedScope() {
        assertEquals(listOf("个人", "集成", "编码"), SettingsNavigatorModel.groups.map { it.heading })
        assertEquals(
            listOf("general", "appearance", "models", "keyboard-shortcuts", "plugins", "connections", "git", "worktrees"),
            SettingsNavigatorModel.allItems.map { it.slug },
        )
        assertTrue(SettingsNavigatorModel.allItems.none { it.external })
        assertFalse(SettingsNavigatorModel.hasSlug("codex-cloud"))
        assertFalse(SettingsNavigatorModel.hasSlug("usage-billing"))
    }

    @Test
    fun visibleRowsUseTheExtractedGlyphs() {
        val glyphs = mapOf(
            "general" to SettingsNavIcon.General,
            "appearance" to SettingsNavIcon.Appearance,
            "keyboard-shortcuts" to SettingsNavIcon.KeyboardShortcuts,
            "plugins" to SettingsNavIcon.Plugins,
            "connections" to SettingsNavIcon.Connections,
            "git" to SettingsNavIcon.Git,
            "worktrees" to SettingsNavIcon.Worktrees,
        )
        glyphs.forEach { (slug, glyph) -> assertEquals(glyph, SettingsNavigatorModel.item(slug)?.icon) }
    }

    @Test
    fun eachRowHasADistinctDestinationAndSelectionRoundTrips() {
        val items = SettingsNavigatorModel.allItems
        assertEquals(items.size, items.map { it.toPageId() }.toSet().size)
        assertEquals(PageId.Plugins, SettingsNavigatorModel.item("plugins")!!.toPageId())
        items.filterNot { it.slug == "plugins" }.forEach { item ->
            val page = item.toPageId() as PageId.Settings
            assertEquals(item.slug, page.section.navigatorSlug())
        }
        val navigation = NavigationState(currentPage = PageId.Settings()).navigate(PageId.Settings(SettingsSection.Git))
        assertEquals(PageId.Settings(SettingsSection.Git), navigation.currentPage)
        assertEquals(PageId.Settings(), navigation.back().currentPage)
    }

    @Test
    fun searchDropsExcludedRowsAndEmptyGroups() {
        assertEquals(SettingsNavigatorModel.groups, settingsNavigatorGroups(SettingsNavigatorState(query = "  ")))
        assertEquals(listOf("plugins"), settingsNavigatorGroups(SettingsNavigatorState(query = "插件")).single().items.map { it.slug })
        assertEquals(3, settingsNavigatorGroups(SettingsNavigatorState(query = "编码")).single().items.size)
        assertTrue(settingsNavigatorGroups(SettingsNavigatorState(query = "云端")).isEmpty())
        assertTrue(settingsNavigatorGroups(SettingsNavigatorState(query = "没有这个设置")).isEmpty())
    }

    /**
     * The Settings filter is page-owned and never reads the Workspace search. The navigator *mode* is
     * the opposite: one value for the whole app, so both navigators report the same mode and only the
     * content above it differs.
     */
    @Test
    fun settingsFilterIsPageOwnedWhileTheNavigatorModeIsShared() {
        val state = AppShellState(
            navigation = NavigationState(currentPage = PageId.Settings()),
            workspace = WorkspaceNavigatorState(searchQuery = "ws-search"),
            settingsNavigator = SettingsNavigatorState(query = "Git"),
        )
        // The app writes the one shared mode into the Settings navigator and the Workspace navigator.
        val collapsed = state.workspace.mode.toggled()
        val shared = state.copy(
            workspace = state.workspace.copy(mode = collapsed),
            settingsNavigator = state.settingsNavigator.copy(mode = collapsed),
        )
        assertEquals(SidebarMode.Collapsed, shared.workspace.mode)
        assertEquals(shared.workspace.mode, shared.settingsNavigator.mode)
        // Filtering Settings leaves the Workspace search exactly as it was.
        assertEquals("ws-search", shared.workspace.searchQuery)
        assertEquals(listOf("git"), settingsNavigatorGroups(shared.settingsNavigator).single().items.map { it.slug })
    }
}

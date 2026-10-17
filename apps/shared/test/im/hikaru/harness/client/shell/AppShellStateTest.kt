package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppShellStateTest {
    @Test
    fun stateCarriesTypedShellRegionsWithoutPlatformObjects() {
        val state =
            AppShellState(
                sidebar = SidebarState(SidebarMode.Collapsed, selectedSessionId = "session-alpha"),
                destination = ShellDestination.Conversation,
                connection = ConnectionIndicatorState.Online,
                panel = PanelState(PanelKind.ToolDetails, isOpen = true),
            )

        assertEquals(SidebarMode.Collapsed, state.sidebar.mode)
        assertEquals("session-alpha", state.sidebar.selectedSessionId)
        assertTrue(state.panel.isOpen)
    }

    @Test
    fun navigationAndWorkspaceRegionsCarryPageIdentity() {
        val state =
            AppShellState(
                navigation = NavigationState(currentPage = PageId.Thread("thread-1")),
                workspace = WorkspaceNavigatorState(workspaceLabel = "Local", mode = SidebarMode.Collapsed, searchQuery = "harness"),
                thread = ThreadSurfaceState(threadId = "thread-1", title = "Thread 1", isRunning = true),
            )

        assertEquals(PageId.Thread("thread-1"), state.navigation.currentPage)
        assertEquals(SidebarMode.Collapsed, state.workspace.mode)
        assertEquals("harness", state.workspace.searchQuery)
        assertEquals("Thread 1", state.thread?.title)
        assertTrue(state.thread?.isRunning == true)
    }

    @Test
    fun accountMenuIsAnIndependentShellState() {
        val state =
            AppShellState(
                navigation = NavigationState(currentPage = PageId.Thread("thread-1")),
                accountMenu =
                    AccountMenuState(
                        identityLabel = "42",
                        modeLabel = "远程账号",
                        connectionLabel = "在线",
                        hostLabel = "Soul Host",
                        availableHostCount = 2,
                        isLoggedIn = true,
                    ).open(),
            )

        assertTrue(state.accountMenu.isOpen)
        assertEquals("42", state.accountMenu.identityLabel)
        assertEquals("在线", state.accountMenu.connectionLabel)
        assertEquals(2, state.accountMenu.availableHostCount)
        assertEquals(PageId.Thread("thread-1"), state.navigation.currentPage)
        assertTrue(state.accountMenu.close().isOpen.not())
    }

    @Test
    fun workspaceNavigatorOwnsDrawerAndSearchState() {
        val pageState = NavigationState(currentPage = PageId.Thread("thread-1"))
        val navigator = WorkspaceNavigatorState().close().withSearchQuery("harness")

        assertEquals(PageId.Thread("thread-1"), pageState.currentPage)
        assertEquals(SidebarMode.Collapsed, navigator.mode)
        assertEquals("harness", navigator.searchQuery)
        assertEquals(SidebarMode.Expanded, navigator.toggle().mode)
    }
}

package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationStateTest {
    @Test
    fun navigatePushesBackStackAndSelectsThreadIdentity() {
        var state = NavigationState()
        state = state.navigate(PageId.Projects(query = "harness"))
        state = state.navigate(PageId.Project(projectId = "project-1"))
        state = state.selectThread(threadId = "thread-1", taskId = "task-1")

        assertEquals(PageId.Thread("thread-1", "task-1"), state.currentPage)
        assertEquals("thread-1", state.selectedThreadId)
        assertEquals("task-1", state.selectedTaskId)
        assertEquals(
            listOf(PageId.Home, PageId.Projects(query = "harness"), PageId.Project(projectId = "project-1")),
            state.backStack,
        )

        state = state.back()
        assertEquals(PageId.Project(projectId = "project-1"), state.currentPage)
    }

    @Test
    fun pagesIsTheNavigation3BackStackWithCurrentPageLastAndNeverEmpty() {
        // NavDisplay requires a non-empty stack; the exposed list always ends with the current page.
        val home = NavigationState()
        assertEquals(listOf(PageId.Home), home.pages)

        val deep = home
            .navigate(PageId.Projects(query = "harness"))
            .navigate(PageId.Project(projectId = "project-1"))
        assertEquals(
            listOf(PageId.Home, PageId.Projects(query = "harness"), PageId.Project(projectId = "project-1")),
            deep.pages,
        )
        assertEquals(deep.currentPage, deep.pages.last())

        val back = deep.back()
        assertEquals(listOf(PageId.Home, PageId.Projects(query = "harness")), back.pages)
    }

    @Test
    fun overlayDoesNotReplaceCurrentPage() {
        var state = NavigationState(currentPage = PageId.Thread("thread-1"))
        state = state.openOverlay(OverlayId.ProjectContext("project-1"))

        assertEquals(PageId.Thread("thread-1"), state.currentPage)
        assertEquals(OverlayId.ProjectContext("project-1"), state.overlay)

        assertEquals(OverlayId.None, state.closeOverlay().overlay)
    }

    @Test
    fun toggleProjectTracksExpansion() {
        val state = NavigationState().toggleProject("project-1")
        assertTrue("project-1" in state.expandedProjectIds)
        assertFalse("project-1" in state.toggleProject("project-1").expandedProjectIds)
    }

    @Test
    fun legacyDestinationMapsToPageId() {
        assertEquals(PageId.Account, ShellDestination.Account.toPageId())
        assertEquals(ShellDestination.PluginInventory, PageId.Plugins.toShellDestination())
        assertEquals(PageId.ScheduledTasks(), ShellDestination.ScheduledTasks.toPageId())
        assertEquals(ShellDestination.ScheduledTasks, PageId.ScheduledTasks().toShellDestination())
    }

    @Test
    fun accountMenuDestinationsKeepHostAndSettingsAsPageRoutes() {
        assertEquals(PageId.Settings(), AccountMenuDestination.Settings.toPageId())
    }

    @Test
    fun scheduledRunCarriesAutomationAndSourceIntoThreadPage() {
        val navigation =
            NavigationState(currentPage = PageId.ScheduledTasks()).selectScheduledRun(
                im.hikaru.harness.client.scheduled.ScheduledRunNavigation(
                    threadId = "thread-run-1",
                    automationId = "automation-1",
                    runId = "run-1",
                ),
            )

        assertEquals(
            PageId.Thread("thread-run-1", automationId = "automation-1", runId = "run-1", navigationSource = "scheduled_page"),
            navigation.currentPage,
        )
        assertEquals(PageId.ScheduledTasks(), navigation.backStack.single())
    }
}

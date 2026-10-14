package im.hikaru.harness.client.scheduled

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduledTasksRepositoryTest {
    @Test
    fun localTaskSearchIsDeterministic() {
        val repository = ScheduledTasksRepository(ScheduledTasksState.seed())
        assertEquals(2, repository.state.visibleTasks.size)

        repository.setQuery("Koog")
        assertEquals(listOf("automation-koog-pr-1703"), repository.state.visibleTasks.map { it.id })
    }

    @Test
    fun expandOlderRunsAndCreateTaskUpdateLocalState() {
        val repository = ScheduledTasksRepository(ScheduledTasksState.seed())
        val task = repository.state.tasks.first()
        repository.toggleTask(task.id)
        assertTrue(task.id in repository.state.expandedTaskIds)
        assertEquals(1, repository.state.visibleRuns(task).size)

        repository.showOlder(task.id)
        assertEquals(2, repository.state.visibleRuns(task).size)
        repository.showOlder(task.id)
        assertEquals(3, repository.state.visibleRuns(task).size)
        assertTrue(!repository.state.hasOlderRuns(repository.state.tasks.first { it.id == task.id }))

        val created = repository.createTask("本地新任务")
        assertEquals(created.id, repository.state.tasks.first().id)
        assertEquals("本地新任务", repository.state.visibleTasks.first().title)
    }

    @Test
    fun drawerModeSurvivesSearchAndTaskOperations() {
        val repository = ScheduledTasksRepository(ScheduledTasksState.seed())
        repository.setMode(im.hikaru.harness.client.shell.SidebarMode.Collapsed)
        repository.setQuery("Koog")
        repository.toggleTask(repository.state.tasks.first().id)
        repository.createTask("另一个任务")
        repository.retry()
        assertEquals(im.hikaru.harness.client.shell.SidebarMode.Collapsed, repository.state.mode)
    }

    @Test
    fun runtimeStartsWithoutInventedTaskRuns() {
        val repository = ScheduledTasksRepository()
        assertTrue(repository.state.tasks.isEmpty())
        assertTrue(repository.createTask().runs.isEmpty())
    }

    @Test
    fun retryAndRunNavigationPreserveThePureStateContract() {
        val repository = ScheduledTasksRepository(ScheduledTasksState.seed())
        repository.beginLoading()
        assertEquals(ScheduledLoadState.Loading, repository.state.loadState)
        repository.failLoading("offline")
        assertEquals(ScheduledLoadState.Error, repository.state.loadState)
        repository.retry()
        assertEquals(ScheduledLoadState.Ready, repository.state.loadState)

        val task = repository.state.tasks.first()
        val run = task.runs.first()
        val navigation = assertNotNull(repository.openRun(task, run))
        assertEquals(task.id, navigation.automationId)
        assertEquals(run.id, navigation.runId)
        assertEquals("scheduled_page", navigation.navigationSource)
        assertEquals(navigation, repository.state.lastRunNavigation)
    }

    @Test
    fun runWithoutRealChatReferenceCannotNavigate() {
        val repository = ScheduledTasksRepository(ScheduledTasksState.seed())
        val task = repository.state.tasks.first()
        assertNull(repository.openRun(task, task.runs.first().copy(chatRef = null)))
        assertNull(repository.openRun(task, task.runs.first().copy(chatRef = " ")))
        assertNull(repository.state.lastRunNavigation)
    }
}

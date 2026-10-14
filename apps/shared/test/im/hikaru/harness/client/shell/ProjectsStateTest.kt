package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProjectsStateTest {
    @Test
    fun createLocalProjectAppendsDeterministicEmptyNode() {
        val existing =
            WorkspaceProjectNode(
                projectId = "existing",
                name = "现有项目",
                pinned = true,
                children = listOf(WorkspaceItemNode(id = "thread-1", title = "已有线程")),
            )

        val next = ProjectsState(projects = listOf(existing)).createLocalProject()

        assertEquals(listOf(existing, WorkspaceProjectNode("local-project-1", "新项目 1")), next.projects)
        assertEquals(2, next.nextLocalProjectNumber)
        assertEquals(existing, next.findProject("existing"))
        assertNotNull(next.findProject("local-project-1"))
    }

    @Test
    fun repeatedCreationKeepsProjectIdsStableAndPreservesChildrenContract() {
        val state =
            ProjectsState(
                projects =
                    listOf(
                        WorkspaceProjectNode(
                            projectId = "fixture",
                            name = "Fixture",
                            children = listOf(WorkspaceItemNode("task-1", "任务", WorkspaceItemKind.Task)),
                        ),
                    ),
            )

        val next = state.createLocalProject().createLocalProject()

        assertEquals(listOf("fixture", "local-project-1", "local-project-2"), next.projects.map { it.projectId })
        assertEquals(listOf("task-1"), next.findProject("fixture")?.children?.map { it.id })
    }
}

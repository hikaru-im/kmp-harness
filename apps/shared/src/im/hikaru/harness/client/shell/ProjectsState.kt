package im.hikaru.harness.client.shell

/**
 * Deterministic local project catalog used by the visual client until a remote project repository is
 * connected. The catalog owns only project mutations; [WorkspaceProjectNode] remains the shared
 * projection consumed by the shell and page surfaces.
 */
public data class ProjectsState(
    val projects: List<WorkspaceProjectNode> = emptyList(),
    val nextLocalProjectNumber: Int = 1,
) {
    init {
        require(nextLocalProjectNumber > 0) { "The next local project number must be positive" }
    }

    /** Adds one empty local project and returns the updated catalog. */
    public fun createLocalProject(): ProjectsState {
        val number = nextLocalProjectNumber
        val project =
            WorkspaceProjectNode(
                projectId = "local-project-$number",
                name = "新项目 $number",
            )
        return copy(
            projects = projects + project,
            nextLocalProjectNumber = number + 1,
        )
    }

    public fun findProject(projectId: String): WorkspaceProjectNode? =
        projects.firstOrNull { it.projectId == projectId }
}

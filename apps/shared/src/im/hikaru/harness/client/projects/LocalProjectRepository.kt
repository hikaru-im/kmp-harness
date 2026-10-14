package im.hikaru.harness.client.projects

import im.hikaru.harness.client.connection.SessionSummary

/** isCurrent fences suspension and the last point before durable commit. Cancellation propagates. */
public interface LocalProjectRepository {
    public val available: Boolean get() = true
    public suspend fun list(binding: LocalProjectBinding, isCurrent: () -> Boolean = { true }): LocalProjectsResult
    public suspend fun create(binding: LocalProjectBinding, request: CreateLocalProject, isCurrent: () -> Boolean = { true }): LocalProjectWriteResult
    public suspend fun bindDirectory(binding: LocalProjectBinding, id: LocalProjectId, directory: String, isCurrent: () -> Boolean = { true }): LocalProjectWriteResult
    public suspend fun validateRoot(binding: LocalProjectBinding, id: LocalProjectId, root: LocalProjectRoot, isCurrent: () -> Boolean = { true }): LocalProjectRootResult
    public suspend fun attachSession(target: ValidatedLocalProjectTarget, session: SessionSummary, isCurrent: () -> Boolean = { true }): LocalProjectWriteResult
}

public object UnavailableLocalProjectRepository : LocalProjectRepository {
    override val available: Boolean = false
    private val failure = LocalProjectFailure.Unavailable(
        LocalProjectUnavailableReason.ProjectServiceNotExposed, "当前 Host 未接入本机项目目录与持久登记服务。",
    )
    override suspend fun list(binding: LocalProjectBinding, isCurrent: () -> Boolean): LocalProjectsResult = LocalProjectsResult.Failed(failure)
    override suspend fun create(binding: LocalProjectBinding, request: CreateLocalProject, isCurrent: () -> Boolean): LocalProjectWriteResult = LocalProjectWriteResult.Failed(failure)
    override suspend fun bindDirectory(binding: LocalProjectBinding, id: LocalProjectId, directory: String, isCurrent: () -> Boolean): LocalProjectWriteResult = LocalProjectWriteResult.Failed(failure)
    override suspend fun validateRoot(binding: LocalProjectBinding, id: LocalProjectId, root: LocalProjectRoot, isCurrent: () -> Boolean): LocalProjectRootResult = LocalProjectRootResult.Failed(failure)
    override suspend fun attachSession(target: ValidatedLocalProjectTarget, session: SessionSummary, isCurrent: () -> Boolean): LocalProjectWriteResult = LocalProjectWriteResult.Failed(failure)
}

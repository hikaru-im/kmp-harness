package im.hikaru.harness.desktop.projects

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.connection.LocalConnectionException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only the verified in-process Host may read local files. Does not create sessions or directories. */
public class DesktopLocalProjectRepository(
    private val localHostId: HostId,
    private val store: DesktopLocalProjectStore,
    private val directoryAccess: DesktopProjectDirectoryAccess = DesktopProjectDirectoryAccess(),
    private val expectedConnection: Connection? = null,
) : LocalProjectRepository {
    override suspend fun list(binding: LocalProjectBinding, isCurrent: () -> Boolean): LocalProjectsResult =
        guarded<LocalProjectsResult>({ LocalProjectsResult.Failed(it) }) {
            verify(binding, isCurrent)
            val read = read(binding, isCurrent)
            LocalProjectsResult.Ready(read.snapshot)
        }

    override suspend fun create(binding: LocalProjectBinding, request: CreateLocalProject, isCurrent: () -> Boolean): LocalProjectWriteResult =
        guarded<LocalProjectWriteResult>({ LocalProjectWriteResult.Failed(it) }) {
            verify(binding, isCurrent)
            if (request.requestId.isBlank() || request.name.isBlank() || request.directory.isBlank()) reject("创建请求必须包含稳定 requestId、名称和已有目录。")
            val read = read(binding, isCurrent)
            val receipt = read.snapshot.createReceipts.firstOrNull { it.request.requestId == request.requestId }
            if (receipt != null) {
                if (receipt.request != request) reject("同一 requestId 已用于不同的创建内容。")
                // Replay is checked before directory access: a committed request remains confirmed after directory removal.
                return@guarded LocalProjectWriteResult.Committed(read.snapshot, receipt.projectId, replayed = true)
            }
            val root = directory(request.directory, isCurrent)
            val id = LocalProjectId(UUID.randomUUID().toString())
            val project = LocalProject(id, request.name.trim(), binding.owner, listOf(root))
            commit(read, read.snapshot.copy(revision = nextRevision(read.snapshot), projects = read.snapshot.projects + project,
                createReceipts = read.snapshot.createReceipts + LocalProjectCreateReceipt(request, id)), id, isCurrent)
        }

    override suspend fun bindDirectory(binding: LocalProjectBinding, id: LocalProjectId, directory: String, isCurrent: () -> Boolean): LocalProjectWriteResult =
        guarded<LocalProjectWriteResult>({ LocalProjectWriteResult.Failed(it) }) {
            verify(binding, isCurrent)
            val read = read(binding, isCurrent)
            val project = read.snapshot.project(id) ?: reject("项目不属于当前本机 Host。")
            val root = directory(directory, isCurrent)
            if (project.roots.single() == root) return@guarded LocalProjectWriteResult.Committed(read.snapshot, id, replayed = true)
            commit(read, read.snapshot.copy(revision = nextRevision(read.snapshot), projects = read.snapshot.projects.map {
                if (it.id == id) it.copy(roots = listOf(root)) else it
            }), id, isCurrent)
        }

    override suspend fun validateRoot(binding: LocalProjectBinding, id: LocalProjectId, root: LocalProjectRoot, isCurrent: () -> Boolean): LocalProjectRootResult =
        guarded<LocalProjectRootResult>({ LocalProjectRootResult.Failed(it) }) {
            verify(binding, isCurrent)
            val read = read(binding, isCurrent)
            val project = read.snapshot.project(id) ?: reject("所选项目不存在或不属于当前 Host。")
            if (project.roots.single() != root) reject("项目目录已被替换，请重新选择。")
            if (directory(root.path, isCurrent) != root) reject("目录的真实路径已改变，请重新绑定目录。")
            checkCurrent(isCurrent)
            LocalProjectRootResult.Ready(ValidatedLocalProjectTarget(binding, id, root))
        }

    override suspend fun attachSession(target: ValidatedLocalProjectTarget, session: SessionSummary, isCurrent: () -> Boolean): LocalProjectWriteResult =
        guarded<LocalProjectWriteResult>({ LocalProjectWriteResult.Failed(it) }) {
            verify(target.binding, isCurrent)
            val actual = try { target.binding.connection.session.list() }
                catch (error: LocalConnectionException) {
                    if ((error.code == GatewayResultCodes.NotFound && error.message == "Harness API endpoint 'session.list' 未注册") ||
                        (error.code == GatewayResultCodes.ConfigurationError && error.message == "Harness Session API Service 未注册")) {
                        fail(LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.SessionServiceNotExposed, "当前本机连接未提供真实 session 列表，不能确认项目关联。"))
                    }
                    throw error
                }
            checkCurrent(isCurrent)
            if (actual.singleOrNull { it.id == session.id } != session || session.createdAt < 0) reject("SessionSummary 未通过当前 Host 的真实 session 列表验证。")
            val cwd = session.cwd ?: reject("已创建 session 没有确认 cwd。")
            if (directory(cwd, isCurrent) != target.root) reject("已创建 session 的真实 cwd 与提交时目录不匹配。")
            val read = read(target.binding, isCurrent)
            val project = read.snapshot.project(target.projectId) ?: reject("提交时项目已不存在。")
            if (project.roots.single() != target.root) reject("提交后项目 root 已替换；不能关联旧目录的 session。")
            val membership = LocalProjectMembership(target.projectId, session)
            val existing = read.snapshot.memberships.firstOrNull { it.session.id == session.id }
            if (existing != null) {
                if (existing != membership) reject("Session 已有不同的项目归属或摘要。")
                return@guarded LocalProjectWriteResult.Committed(read.snapshot, target.projectId, replayed = true)
            }
            commit(read, read.snapshot.copy(revision = nextRevision(read.snapshot), memberships = read.snapshot.memberships + membership),
                target.projectId, isCurrent)
        }

    private suspend fun verify(binding: LocalProjectBinding, isCurrent: () -> Boolean) {
        checkCurrent(isCurrent)
        if (binding.transport != ConnectionTransport.Local || binding.connection !is LocalConnection) {
            fail(LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.LocalOnly, "项目目录只允许 verified LocalConnection；Relay 不可访问本机目录。"))
        }
        if (binding.scope.hostId != localHostId || (expectedConnection != null && binding.connection !== expectedConnection)) {
            fail(LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.HostNotVerified, "连接实例或 Host 与本机项目服务不匹配。"))
        }
        val description = binding.connection.host.describe()
        checkCurrent(isCurrent)
        if (!description.isSuccess || description.data?.hostId != localHostId) {
            fail(LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.HostNotVerified, "未取得匹配的本机 Host 描述。"))
        }
    }

    private suspend fun read(binding: LocalProjectBinding, isCurrent: () -> Boolean): DesktopLocalProjectReadResult.Ready {
        val result = store.read(binding.owner)
        checkCurrent(isCurrent)
        return when (result) {
            is DesktopLocalProjectReadResult.Ready -> result
            is DesktopLocalProjectReadResult.Failed -> fail(result.failure)
        }
    }

    private suspend fun directory(path: String, isCurrent: () -> Boolean): LocalProjectRoot {
        val result = directoryAccess.validate(path)
        checkCurrent(isCurrent)
        return when (result) {
            is DesktopProjectDirectoryResult.Ready -> result.root
            is DesktopProjectDirectoryResult.Failed -> fail(result.failure)
        }
    }

    private suspend fun commit(read: DesktopLocalProjectReadResult.Ready, snapshot: LocalProjectSnapshot, id: LocalProjectId,
        isCurrent: () -> Boolean): LocalProjectWriteResult {
        checkCurrent(isCurrent)
        return when (val result = store.compareAndSet(read.token, snapshot, isCurrent)) {
            is DesktopLocalProjectStoreWriteResult.Committed -> LocalProjectWriteResult.Committed(result.snapshot, id, durabilityWarning = result.durabilityWarning)
            is DesktopLocalProjectStoreWriteResult.Failed -> LocalProjectWriteResult.Failed(result.failure)
        }
    }

    private suspend fun checkCurrent(isCurrent: () -> Boolean) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) fail(LocalProjectFailure.StaleBinding)
    }

    private fun nextRevision(snapshot: LocalProjectSnapshot): Long {
        if (snapshot.revision == Long.MAX_VALUE) reject("项目登记版本已耗尽。")
        return snapshot.revision + 1
    }

    private suspend fun <T> guarded(failed: (LocalProjectFailure) -> T, block: suspend () -> T): T = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: ProjectOperationFailure) { failed(error.failure) }
        catch (_: Exception) { failed(LocalProjectFailure.Error("本机项目操作失败；创建请使用原 requestId 重试核对。")) }

    private class ProjectOperationFailure(val failure: LocalProjectFailure) : RuntimeException()
    private fun fail(failure: LocalProjectFailure): Nothing = throw ProjectOperationFailure(failure)
    private fun reject(message: String): Nothing = fail(LocalProjectFailure.Rejected(message))
}

package im.hikaru.harness.client.projects

import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** UI calls are serialized on its coroutine dispatcher, like the existing conversation controller. */
public class LocalProjectsController(
    private val coroutineScope: CoroutineScope,
    private val repository: LocalProjectRepository = UnavailableLocalProjectRepository,
) {
    private val mutableState = MutableStateFlow(LocalProjectsState())
    public val state: StateFlow<LocalProjectsState> = mutableState.asStateFlow()
    @Volatile private var binding: LocalProjectBinding? = null
    @Volatile private var epoch = 0L
    @Volatile private var selection = 0L
    @Volatile private var readRequest = 0L
    private var readJob: Job? = null
    private val mutationJobs = mutableSetOf<Job>()
    private val mutationMutex = Mutex()
    @Volatile private var closed = false

    public fun isBoundTo(value: LocalProjectBinding?): Boolean = !closed && when {
        value == null -> binding == null
        else -> binding?.sameOwner(value) == true
    }

    public fun bind(value: LocalProjectBinding?) {
        check(!closed) { "Project controller is closed" }
        if (isBoundTo(value)) return
        epoch++; selection++; readRequest++
        readJob?.cancel(); mutationJobs.toList().forEach { it.cancel() }; mutationJobs.clear()
        readJob = null; binding = value
        mutableState.value = LocalProjectsState(binding = value)
        if (value != null) {
            val failure = gate(value)
            if (failure != null) loadFailure(failure) else refresh()
        }
    }

    public fun setQuery(query: String) { mutableState.value = state.value.copy(query = query) }

    public fun select(id: LocalProjectId?) {
        if (id != null && state.value.projects.none { it.id == id }) return
        selection++
        mutableState.value = state.value.copy(selectedProjectId = id)
    }

    public fun refresh(): Job? {
        val owner = binding ?: return null
        if (closed) return null
        gate(owner)?.let { loadFailure(it); return null }
        val capturedEpoch = epoch
        val request = ++readRequest
        readJob?.cancel()
        mutableState.value = state.value.copy(phase = LocalProjectsPhase.Loading, failure = null, canRefresh = true)
        val current = { valid(owner, capturedEpoch) && request == readRequest }
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.list(owner, current)
                currentCoroutineContext().ensureActive()
                if (current()) when (result) {
                    is LocalProjectsResult.Ready -> publish(result.snapshot)
                    is LocalProjectsResult.Failed -> loadFailure(result.failure)
                }
            } catch (cancelled: CancellationException) {
                if (current()) loadFailure(LocalProjectFailure.Error("项目读取已取消，请重试。"))
                throw cancelled
            } catch (_: Exception) {
                if (current()) loadFailure(LocalProjectFailure.Error("无法读取本机项目。"))
            }
        }
        readJob = job; job.start(); return job
    }

    /** Retain this exact request across cancellation/ambiguous failure; retryCreate reuses it. */
    public fun create(request: CreateLocalProject): Job? {
        val pending = state.value.pendingCreate
        if (pending != null && pending != request) {
            mutableState.value = state.value.copy(mutationFailure = LocalProjectFailure.Conflict("先使用原 requestId 核对待确认的项目创建。"))
            return null
        }
        return mutate(LocalProjectMutation.Create, pending = request) { owner, current ->
            repository.create(owner, request, current)
        }
    }

    public fun retryCreate(): Job? = state.value.pendingCreate?.let(::create)

    public fun bindDirectory(id: LocalProjectId, directory: String): Job? =
        mutate(LocalProjectMutation.BindDirectory) { owner, current -> repository.bindDirectory(owner, id, directory, current) }

    /** Call before new-session submit. Does not create a directory, session, or Agent. */
    public suspend fun prepareSelectedRoot(): LocalProjectRootResult {
        val owner = binding ?: return LocalProjectRootResult.Failed(LocalProjectFailure.StaleBinding)
        gate(owner)?.let { return LocalProjectRootResult.Failed(it) }
        val project = state.value.selectedProject
            ?: return LocalProjectRootResult.Failed(LocalProjectFailure.Rejected("请先选择已加载的本机项目。"))
        val root = project.roots.single()
        val capturedEpoch = epoch
        val capturedSelection = selection
        val current = { valid(owner, capturedEpoch) && selection == capturedSelection }
        return try {
            val result = repository.validateRoot(owner, project.id, root, current)
            currentCoroutineContext().ensureActive()
            if (!current()) LocalProjectRootResult.Failed(LocalProjectFailure.StaleBinding)
            else when (result) {
                is LocalProjectRootResult.Failed -> result
                is LocalProjectRootResult.Ready -> {
                    val target = result.target
                    if (!target.binding.sameOwner(owner) || target.projectId != project.id || target.root != root)
                        LocalProjectRootResult.Failed(LocalProjectFailure.Rejected("目录验证返回的项目或绑定不匹配。"))
                    else LocalProjectRootResult.Ready(target.copy(controllerEpoch = capturedEpoch))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { LocalProjectRootResult.Failed(LocalProjectFailure.Error("无法验证项目目录。")) }
    }

    /** Explicit retry after reconnect revalidates the original project, not the current picker. */
    public suspend fun revalidateCreatedSessionTarget(target: ValidatedLocalProjectTarget): LocalProjectRootResult {
        val owner = binding ?: return LocalProjectRootResult.Failed(LocalProjectFailure.StaleBinding)
        if (owner.owner != target.binding.owner || owner.transport != target.binding.transport || closed)
            return LocalProjectRootResult.Failed(LocalProjectFailure.StaleBinding)
        gate(owner)?.let { return LocalProjectRootResult.Failed(it) }
        val capturedEpoch = epoch
        val current = { valid(owner, capturedEpoch) }
        return try {
            val result = repository.validateRoot(owner, target.projectId, target.root, current)
            currentCoroutineContext().ensureActive()
            if (!current()) LocalProjectRootResult.Failed(LocalProjectFailure.StaleBinding)
            else when (result) {
                is LocalProjectRootResult.Failed -> result
                is LocalProjectRootResult.Ready -> {
                    val validated = result.target
                    if (!validated.binding.sameOwner(owner) || validated.projectId != target.projectId || validated.root != target.root)
                        LocalProjectRootResult.Failed(LocalProjectFailure.Rejected("目录验证返回的项目或绑定不匹配。"))
                    else LocalProjectRootResult.Ready(validated.copy(controllerEpoch = capturedEpoch))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { LocalProjectRootResult.Failed(LocalProjectFailure.Error("无法重新验证项目目录。")) }
    }

    /** Attach at create confirmation, even if the user subsequently selected another project. */
    public fun attachCreatedSession(target: ValidatedLocalProjectTarget, summary: SessionSummary): Job? {
        val owner = binding ?: return null
        if (!target.binding.sameOwner(owner) || target.controllerEpoch != epoch || closed) {
            mutableState.value = state.value.copy(mutationFailure = LocalProjectFailure.StaleBinding)
            return null
        }
        return mutate(LocalProjectMutation.AttachSession) { _, current -> repository.attachSession(target, summary, current) }
    }

    public fun close() {
        if (closed) return
        epoch++; readRequest++; closed = true
        readJob?.cancel(); mutationJobs.toList().forEach { it.cancel() }; mutationJobs.clear()
        binding = null; mutableState.value = LocalProjectsState()
    }

    private fun mutate(
        kind: LocalProjectMutation, pending: CreateLocalProject? = null,
        operation: suspend (LocalProjectBinding, () -> Boolean) -> LocalProjectWriteResult,
    ): Job? {
        val owner = binding ?: return null
        if (closed) return null
        gate(owner)?.let { mutableState.value = state.value.copy(mutationFailure = it); return null }
        val capturedEpoch = epoch
        val current = { valid(owner, capturedEpoch) }
        if (pending != null) mutableState.value = state.value.copy(pendingCreate = pending)
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
          mutationMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (!current()) return@withLock
            mutableState.value = state.value.copy(mutation = kind, mutationFailure = null, durabilityWarning = null)
            try {
                val result = operation(owner, current)
                currentCoroutineContext().ensureActive()
                if (current()) when (result) {
                    is LocalProjectWriteResult.Committed -> {
                        readRequest++; readJob?.cancel()
                        publish(result.snapshot)
                        mutableState.value = state.value.copy(
                            pendingCreate = if (kind == LocalProjectMutation.Create) null else state.value.pendingCreate,
                            durabilityWarning = result.durabilityWarning,
                        )
                    }
                    is LocalProjectWriteResult.Failed -> {
                        val definitive = result.failure is LocalProjectFailure.Rejected ||
                            result.failure is LocalProjectFailure.AccessDenied ||
                            result.failure is LocalProjectFailure.UnsupportedVersion ||
                            result.failure is LocalProjectFailure.Unavailable
                        mutableState.value = state.value.copy(mutationFailure = result.failure,
                            pendingCreate = if (kind == LocalProjectMutation.Create && definitive) null else state.value.pendingCreate)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (current()) mutableState.value = state.value.copy(mutationFailure = LocalProjectFailure.Error("操作已取消；项目创建请使用原 requestId 重试确认。"))
                throw cancelled
            } catch (_: Exception) {
                if (current()) mutableState.value = state.value.copy(mutationFailure = LocalProjectFailure.Error("本机项目操作失败；创建结果可能已写入，请使用原 requestId 重试。"))
            } finally {
                if (current()) mutableState.value = state.value.copy(mutation = null)
            }
          }
        }
        mutationJobs.add(job)
        job.invokeOnCompletion { mutationJobs.remove(job) }
        job.start(); return job
    }

    private fun valid(owner: LocalProjectBinding, capturedEpoch: Long): Boolean =
        !closed && epoch == capturedEpoch && binding?.sameOwner(owner) == true

    private fun gate(owner: LocalProjectBinding): LocalProjectFailure? = when {
        owner.transport != ConnectionTransport.Local -> LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.LocalOnly, "Relay 不提供本机项目目录。")
        !repository.available -> LocalProjectFailure.Unavailable(LocalProjectUnavailableReason.ProjectServiceNotExposed, "当前 Host 未接入本机项目持久登记服务。")
        else -> null
    }

    private fun publish(snapshot: LocalProjectSnapshot) {
        if (snapshot.owner != binding?.owner || snapshot.revision < 0 || snapshot.projects.any { it.owner != snapshot.owner }) {
            loadFailure(LocalProjectFailure.Rejected("项目快照 owner 或 revision 不匹配。")); return
        }
        val previous = state.value.snapshot
        val accepted = if (previous != null && previous.revision > snapshot.revision) previous else snapshot
        mutableState.value = state.value.copy(snapshot = accepted,
            phase = if (accepted.projects.isEmpty()) LocalProjectsPhase.Empty else LocalProjectsPhase.Ready,
            selectedProjectId = state.value.selectedProjectId?.takeIf { accepted.project(it) != null }, failure = null, canRefresh = true)
    }

    private fun loadFailure(failure: LocalProjectFailure) {
        val phase = when (failure) {
            is LocalProjectFailure.Unavailable -> LocalProjectsPhase.Unavailable
            is LocalProjectFailure.UnsupportedVersion -> LocalProjectsPhase.Unsupported
            is LocalProjectFailure.AccessDenied -> LocalProjectsPhase.AccessDenied
            else -> LocalProjectsPhase.Error
        }
        mutableState.value = state.value.copy(phase = phase, failure = failure,
            canRefresh = binding?.transport == ConnectionTransport.Local && repository.available)
    }
}

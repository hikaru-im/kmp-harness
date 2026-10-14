package im.hikaru.harness.client.scheduling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Page controller. The parent owns the optional executor and its lifecycle. */
public class ScheduledRepository(
    private val store: ScheduledStore = UnavailableScheduledStore(),
    private val engine: ScheduleEngine,
    private val executor: ScheduledExecutor? = null,
    /** Durable write clock. Injected so tests can assert the exact stored task timestamp. */
    private val clock: ScheduledClock = SystemScheduledClock,
) {
    private val operation = Mutex()
    private val bindingToken = MutableStateFlow(Any())
    private val pendingManualRequests = mutableMapOf<String, String>()
    private val mutableState = MutableStateFlow(ScheduledManagementState())
    public val state: StateFlow<ScheduledManagementState> = mutableState.asStateFlow()

    /** Immediately clears private state and fences every outstanding load/save response. */
    public fun clear() {
        bindingToken.value = Any()
        pendingManualRequests.clear()
        mutableState.value = ScheduledManagementState()
    }

    public fun unbind() { clear() }

    /** Binding and synchronous draft actions belong to the caller's UI dispatcher. */
    public fun bind(scope: ScheduledScope, context: ScheduledValidationContext, connected: Boolean = true) {
        val previous = state.value
        val catalog = context.copy(targets = context.targets.toList(), models = context.models.toList())
        if (previous.scope == scope && previous.context == catalog && previous.connected == connected) return
        bindingToken.value = Any()
        if (previous.scope == scope) {
            mutableState.value = previous.copy(context = catalog, connected = connected, mutation = null,
                effective = previous.effective.takeIf { connected },
                selectedTaskId = previous.selectedTaskId.takeIf { connected },
                executionUnavailableReason = ScheduledExecutorUnavailable,
                phase = if (previous.phase == ScheduledLoadPhase.Loading || previous.mutation != null ||
                    previous.connected != connected || previous.context != catalog) ScheduledLoadPhase.NotLoaded else previous.phase)
        } else {
            pendingManualRequests.clear()
            mutableState.value = ScheduledManagementState(scope = scope, context = catalog, connected = connected,
                phase = ScheduledLoadPhase.NotLoaded)
        }
    }

    /** Real read on every invocation; invalid input never becomes an editable empty snapshot. */
    public suspend fun load() {
        val fence = fence() ?: return
        operation.withLock {
            if (!current(fence)) return
            mutableState.update { it.copy(phase = ScheduledLoadPhase.Loading, loadError = null,
                executionUnavailableReason = ScheduledExecutorUnavailable) }
            try {
                val result = store.read(fence.scope.owner)
                if (!current(fence)) return
                when (result) {
                    ScheduledReadResult.Missing -> loaded(ScheduledSnapshot(fence.scope.owner))
                    is ScheduledReadResult.Ready -> {
                        val error = ScheduledValidator.snapshotError(result.snapshot)
                            ?: result.snapshot.tasks.firstNotNullOfOrNull { engine.validationError(it.schedule) }
                        if (result.snapshot.owner != fence.scope.owner || error != null) {
                            loadFailure(error ?: "任务归属与当前 Host 不一致。")
                        } else loaded(result.snapshot.snapshot())
                    }
                    is ScheduledReadResult.Invalid -> loadFailure(result.message)
                    is ScheduledReadResult.UnsupportedVersion -> loadFailure("配置版本 "+result.version+" 不受支持；原文件保持不变。")
                    is ScheduledReadResult.IoFailure -> loadFailure(result.message)
                }
            } catch (cancelled: CancellationException) {
                if (current(fence)) mutableState.update { it.copy(phase = ScheduledLoadPhase.NotLoaded,
                    executionUnavailableReason = ScheduledExecutorUnavailable) }
                throw cancelled
            } catch (error: Exception) {
                if (current(fence)) loadFailure(error.message ?: "无法读取配置；原文件保持不变。")
            }
        }
    }

    public suspend fun retry() { load() }

    /** Parent launches this on the UI dispatcher for its binding lifetime. Page never owns it. */
    public suspend fun observeExecution() {
        executor?.state?.collect { execution ->
            mutableState.update { current ->
                if (!current.connected || current.scope != execution.scope) current.copy(executionUnavailableReason = ScheduledExecutorUnavailable)
                else {
                    val snapshot = execution.snapshot?.takeIf { current.phase == ScheduledLoadPhase.Ready &&
                        snapshotError(it, current.scope!!) == null && it.revision >= (current.effective?.revision ?: 0) }
                    current.copy(effective = snapshot?.snapshot() ?: current.effective,
                        selectedTaskId = current.selectedTaskId?.takeIf { id ->
                            (snapshot ?: current.effective)?.tasks?.any { it.id == id } == true
                        },
                        executionUnavailableReason = executionReason(current, execution))
                }
            }
        }
    }

    public fun selectTask(taskId: String?) {
        if (taskId != null && state.value.tasks.none { it.id == taskId }) return
        mutableState.update { it.copy(selectedTaskId = taskId) }
    }

    public fun beginCreate(target: ScheduledTarget? = null) {
        val previous = state.value
        if (!previous.canMutate || previous.draft != null) return
        val id = newTaskId(previous.effective ?: return)
        mutableState.value = previous.copy(draft = ScheduledDraft(id, null, target = target,
            schedule = ScheduledRuleDraft(anchorEpochMillis = clock.nowEpochMillis())),
            validationErrors = emptyMap(), mutationError = null)
    }

    public fun beginEdit(taskId: String) {
        val previous = state.value
        if (!previous.canMutate || previous.draft != null) return
        val task = previous.tasks.firstOrNull { it.id == taskId } ?: return
        mutableState.value = previous.copy(selectedTaskId = taskId,
            draft = ScheduledDraft(task.id, task.revision, name = task.name, prompt = task.prompt,
                target = task.target, schedule = task.schedule.toDraft(), model = task.model),
            validationErrors = emptyMap(), mutationError = null)
    }

    /** Remains editable during save. Identity and optimistic revision cannot be forged by the form. */
    public fun updateDraft(update: (ScheduledDraft) -> ScheduledDraft) {
        val previous = state.value
        val draft = previous.draft ?: return
        val changed = update(draft).copy(taskId = draft.taskId, expectedTaskRevision = draft.expectedTaskRevision,
            draftRevision = draft.draftRevision + 1)
        mutableState.value = previous.copy(draft = changed, validationErrors = emptyMap(), mutationError = null)
    }

    public fun cancelDraft() {
        if (state.value.mutation != null) return
        mutableState.update { it.copy(draft = null, validationErrors = emptyMap(), mutationError = null) }
    }

    public fun draftErrors(): Map<String, String> = state.value.draft?.let {
        ScheduledValidator.draftErrors(it, state.value.context, engine)
    }.orEmpty()

    public fun preview(draft: ScheduledDraft, afterEpochMillis: Long): Long? = draft.schedule.rule()?.let {
        engine.nextOccurrence(it, afterEpochMillis)
    }

    public suspend fun save(): Boolean {
        val previous = state.value
        val fence = fence() ?: return false
        val draft = previous.draft ?: return false
        if (!previous.canMutate || !operation.tryLock()) return false
        try {
            if (!current(fence)) return false
            val errors = ScheduledValidator.draftErrors(draft, previous.context, engine)
            if (errors.isNotEmpty()) {
                mutableState.update { it.copy(validationErrors = errors) }
                return false
            }
            val old = previous.effective ?: return false
            val oldTask = old.tasks.firstOrNull { it.id == draft.taskId }
            if (oldTask?.revision != draft.expectedTaskRevision || draft.taskId in old.deletedTaskIds) {
                return mutationFailure("任务已修改或删除；请重新读取并确认草稿。")
            }
            if (old.revision == Long.MAX_VALUE || oldTask?.revision == Long.MAX_VALUE) return mutationFailure("任务版本已超出支持范围。")
            val task = ScheduledTask(draft.taskId, (oldTask?.revision ?: 0) + 1, draft.name.trim(), draft.prompt,
                draft.target!!, draft.schedule.rule()!!, draft.model, ScheduledTaskStatus.Paused, clock.nowEpochMillis())
            val candidate = old.copy(revision = old.revision + 1,
                tasks = if (oldTask == null) old.tasks + task else old.tasks.map { if (it.id == task.id) task else it },
                cursors = old.cursors.filterNot { it.taskId == task.id })
            mutableState.update { it.copy(mutation = ScheduledMutation.Save, mutationError = null) }
            val result = store.compareAndSet(fence.scope.owner, old.revision, candidate) { current(fence) }
            if (!current(fence)) return false
            if (result !is ScheduledWriteResult.Committed) return mutationFailure(writeFailure(result))
            committedError(result.snapshot, candidate)?.let {
                mutableState.update { state -> state.copy(phase = ScheduledLoadPhase.NotLoaded) }
                return mutationFailure(it)
            }
            mutableState.update { latest ->
                val pending = latest.draft?.takeIf { it.taskId == draft.taskId }
                latest.copy(effective = newerSnapshot(latest.effective, result.snapshot), selectedTaskId = task.id, mutation = null,
                    mutationError = null, durabilityWarning = result.durabilityWarning, validationErrors = emptyMap(),
                    draft = if (pending?.draftRevision == draft.draftRevision) null
                        else pending?.copy(expectedTaskRevision = task.revision))
            }
            return true
        } catch (cancelled: CancellationException) {
            if (current(fence)) mutableState.update { it.copy(mutation = null, phase = ScheduledLoadPhase.NotLoaded) }
            throw cancelled
        } catch (error: Exception) {
            return if (current(fence)) mutationFailure(error.message ?: "保存失败；旧有效任务和最新草稿均保留。") else false
        } finally { operation.unlock() }
    }

    public suspend fun setPaused(taskId: String, paused: Boolean): Boolean {
        // A durable pause remains available without execution; ready executors own both transitions.
        if (!paused || executionReason(state.value) == null) {
            val fence = fence() ?: return false
            val previous = state.value
            val task = previous.tasks.firstOrNull { it.id == taskId } ?: return false
            if (!previous.canMutate || previous.draft != null || !operation.tryLock()) return false
            try {
                val execution = executor ?: return mutationFailure(ScheduledExecutorUnavailable)
                if (!current(fence)) return false
                executionReason(state.value)?.let { return mutationFailure(it) }
                if (!paused) taskExecutionReason(task)?.let { return mutationFailure(it) }
                mutableState.update { it.copy(mutation = ScheduledMutation.Pause, mutationError = null) }
                val result = execution.activate(taskId, task.revision, !paused)
                if (!current(fence)) return false
                return when (result) {
                    is ScheduledActivationResult.Refused -> mutationFailure(result.reason)
                    is ScheduledActivationResult.Saved -> {
                        snapshotError(result.snapshot, fence.scope)?.let { return mutationFailure(it) }
                        loaded(result.snapshot); true
                    }
                }
            } catch (cancelled: CancellationException) {
                if (current(fence)) mutableState.update { it.copy(phase = ScheduledLoadPhase.NotLoaded, executionUnavailableReason = ScheduledExecutorUnavailable) }
                throw cancelled
            } catch (error: Exception) {
                return if (current(fence)) mutationFailure(error.message ?: "执行服务未确认任务变更；请重新读取。") else false
            } finally { if (current(fence)) mutableState.update { it.copy(mutation = null) }; operation.unlock() }
        }
        return mutateTask(taskId, ScheduledMutation.Pause) { old, task ->
            old.copy(revision = old.revision + 1, tasks = old.tasks.map {
                if (it.id == task.id) task.copy(revision = task.revision + 1, status = ScheduledTaskStatus.Paused,
                    updatedAt = clock.nowEpochMillis()) else it
            }, cursors = old.cursors.filterNot { it.taskId == taskId })
        }
    }

    public suspend fun delete(taskId: String): Boolean = mutateTask(taskId, ScheduledMutation.Delete) { old, task ->
        old.copy(revision = old.revision + 1, tasks = old.tasks.filterNot { it.id == task.id },
            deletedTaskIds = old.deletedTaskIds + task.id, cursors = old.cursors.filterNot { it.taskId == task.id })
    }

    /** Retains the default dedupe key until a claim is acknowledged, including ambiguous retries. */
    public suspend fun runNow(taskId: String, requestId: String = manualRequestId(taskId)): Boolean {
        val previous = state.value
        val fence = fence() ?: return false
        if (!previous.canMutate || previous.draft != null || previous.tasks.none { it.id == taskId } || !operation.tryLock()) return false
        try {
            val execution = executor ?: return mutationFailure(ScheduledExecutorUnavailable)
            if (!current(fence)) return false
            executionReason(state.value)?.let { return mutationFailure(it) }
            taskExecutionReason(previous.tasks.first { it.id == taskId })?.let { return mutationFailure(it) }
            mutableState.update { it.copy(mutation = ScheduledMutation.Run, mutationError = null) }
            val result = execution.runNow(taskId, requestId)
            if (!current(fence)) return false
            return when (result) {
                is ScheduledClaimResult.Refused -> {
                    if (pendingManualRequests[taskId] == requestId) pendingManualRequests.remove(taskId)
                    mutationFailure(result.reason)
                }
                is ScheduledClaimResult.Claimed -> {
                    val snapshot = execution.state.value.takeIf { it.scope == fence.scope }?.snapshot
                        ?: return mutationFailure("运行已认领，尚未取得当前归属的真实账本；请重新读取。")
                    snapshotError(snapshot, fence.scope)?.let { return mutationFailure(it) }
                    loaded(snapshot)
                    if (pendingManualRequests[taskId] == requestId) pendingManualRequests.remove(taskId)
                    true
                }
            }
        } catch (cancelled: CancellationException) {
            if (current(fence)) mutableState.update { it.copy(phase = ScheduledLoadPhase.NotLoaded, executionUnavailableReason = ScheduledExecutorUnavailable) }
            throw cancelled
        } catch (error: Exception) {
            return if (current(fence)) mutationFailure(error.message ?: "执行服务未确认认领；请重新读取，重试沿用原请求 ID。") else false
        } finally { if (current(fence)) mutableState.update { it.copy(mutation = null) }; operation.unlock() }
    }

    public fun runSessionRef(runId: String): im.hikaru.harness.client.connection.SessionId? =
        state.value.takeIf { it.connected && it.phase == ScheduledLoadPhase.Ready && it.effective?.owner == it.scope?.owner }
            ?.effective?.runs?.firstOrNull { it.id == runId }?.sessionRef

    private suspend fun mutateTask(
        taskId: String, mutation: ScheduledMutation,
        transform: (ScheduledSnapshot, ScheduledTask) -> ScheduledSnapshot,
    ): Boolean {
        val previous = state.value
        val fence = fence() ?: return false
        // A displayed draft must be explicitly cancelled before deleting or pausing its task.
        if (!previous.canMutate || previous.draft != null || !operation.tryLock()) return false
        try {
            if (!current(fence)) return false
            val old = previous.effective ?: return false
            val task = old.tasks.firstOrNull { it.id == taskId } ?: return false
            if (old.revision == Long.MAX_VALUE || task.revision == Long.MAX_VALUE) return mutationFailure("任务版本已超出支持范围。")
            val candidate = transform(old, task)
            mutableState.update { it.copy(mutation = mutation, mutationError = null) }
            val result = store.compareAndSet(fence.scope.owner, old.revision, candidate) { current(fence) }
            if (!current(fence)) return false
            if (result !is ScheduledWriteResult.Committed) return mutationFailure(writeFailure(result))
            committedError(result.snapshot, candidate)?.let {
                mutableState.update { state -> state.copy(phase = ScheduledLoadPhase.NotLoaded) }
                return mutationFailure(it)
            }
            mutableState.update { latest -> latest.copy(effective = newerSnapshot(latest.effective, result.snapshot), mutation = null,
                mutationError = null, durabilityWarning = result.durabilityWarning,
                selectedTaskId = latest.selectedTaskId?.takeIf { id -> result.snapshot.tasks.any { it.id == id } }) }
            return true
        } catch (cancelled: CancellationException) {
            if (current(fence)) mutableState.update { it.copy(mutation = null, phase = ScheduledLoadPhase.NotLoaded) }
            throw cancelled
        } catch (error: Exception) {
            return if (current(fence)) mutationFailure(error.message ?: "保存失败；旧有效任务保持不变。") else false
        } finally { operation.unlock() }
    }

    private fun loaded(snapshot: ScheduledSnapshot) {
        mutableState.update { previous ->
            val effective = newerSnapshot(previous.effective, snapshot)
            val ready = previous.copy(phase = ScheduledLoadPhase.Ready, effective = effective)
            ready.copy(
                loadError = null, mutationError = null, durabilityWarning = null,
                executionUnavailableReason = executionReason(ready),
                selectedTaskId = previous.selectedTaskId?.takeIf { id -> effective.tasks.any { task -> task.id == id } })
        }
    }

    private fun executionReason(
        current: ScheduledManagementState, execution: ScheduledExecutorState? = executor?.state?.value,
    ): String? {
        val scope = current.scope ?: return ScheduledExecutorUnavailable
        if (!current.connected || current.phase != ScheduledLoadPhase.Ready || current.effective?.owner != scope.owner ||
            execution == null || execution.scope != scope) return ScheduledExecutorUnavailable
        if (!execution.accepting) return execution.error ?: execution.unavailableReason ?: ScheduledExecutorUnavailable
        execution.error?.let { return it }
        val snapshot = execution.snapshot ?: return ScheduledExecutorUnavailable
        return snapshotError(snapshot, scope)
    }

    private fun snapshotError(snapshot: ScheduledSnapshot, scope: ScheduledScope): String? =
        if (snapshot.owner != scope.owner) "任务归属与当前 Host 不一致。"
        else ScheduledValidator.snapshotError(snapshot) ?: snapshot.tasks.firstNotNullOfOrNull { engine.validationError(it.schedule) }

    private fun taskExecutionReason(task: ScheduledTask): String? = when {
        task.target !in state.value.context.targets -> "任务目标或模型不在当前 Host 的真实可用目录中。"
        // A heartbeat reuses the session's own model, so it has no model to resolve in this catalog.
        task.model != null && task.model !in state.value.context.models -> "任务目标或模型不在当前 Host 的真实可用目录中。"
        else -> null
    }

    private fun loadFailure(message: String) { mutableState.update { it.copy(phase = ScheduledLoadPhase.Error, loadError = message,
        executionUnavailableReason = ScheduledExecutorUnavailable) } }
    private fun mutationFailure(message: String): Boolean {
        mutableState.update { it.copy(mutation = null, mutationError = message) }
        return false
    }

    private fun fence(): Fence? = state.value.scope?.takeIf { state.value.connected }?.let { Fence(it, bindingToken.value) }
    private fun current(fence: Fence): Boolean = bindingToken.value === fence.token && state.value.scope == fence.scope && state.value.connected
    private data class Fence(val scope: ScheduledScope, val token: Any)

    private fun committedError(actual: ScheduledSnapshot, expected: ScheduledSnapshot): String? =
        if (actual != expected || ScheduledValidator.snapshotError(actual) != null) "保存响应与请求不一致；请重新读取实际存储。" else null

    private fun writeFailure(result: ScheduledWriteResult): String = when (result) {
        ScheduledWriteResult.StaleScope -> "Host 或登录身份已切换；本次写入已隔离。"
        is ScheduledWriteResult.Failed -> result.message
        is ScheduledWriteResult.Committed -> error("Committed is not a failure")
    }

    private fun newTaskId(snapshot: ScheduledSnapshot): String {
        var value: String
        do { value = "task-" + buildString { repeat(26) { append("0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)]) } } }
        while (snapshot.tasks.any { it.id == value } || value in snapshot.deletedTaskIds)
        return value
    }
    private fun newManualRequestId(): String = "manual-" + buildString { repeat(32) { append("0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)]) } }
    private fun manualRequestId(taskId: String): String = pendingManualRequests.getOrPut(taskId) { newManualRequestId() }
    private fun newerSnapshot(current: ScheduledSnapshot?, result: ScheduledSnapshot): ScheduledSnapshot =
        if (current != null && current.owner == result.owner && current.revision > result.revision) current else result.snapshot()
}

internal fun ScheduledSnapshot.snapshot(): ScheduledSnapshot = copy(tasks = tasks.toList(), deletedTaskIds = deletedTaskIds.toList(),
    runs = runs.toList(), cursors = cursors.toList())

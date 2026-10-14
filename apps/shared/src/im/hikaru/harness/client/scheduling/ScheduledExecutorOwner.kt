package im.hikaru.harness.client.scheduling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

public data class ScheduledExecutorOwnerState(
    val executor: ScheduledExecutor,
    val binding: ScheduledScope? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val invalidated: Boolean = false,
    val stopped: Boolean = false,
)

/** One verified connection lifetime. Recovery is explicit and never changes its scope or gate. */
public class ScheduledExecutorOwner(private val factory: () -> ScheduledExecutor) {
    private val mutableState = MutableStateFlow(ScheduledExecutorOwnerState(factory()))
    public val state: StateFlow<ScheduledExecutorOwnerState> = mutableState.asStateFlow()
    private val operation = Mutex()
    // Includes unpublished replacements until their shutdown proves Drained. Synchronous fences
    // see this registry without waiting for a suspended operation to release the mutex.
    private val retained = MutableStateFlow(listOf(state.value.executor))

    public suspend fun start(binding: ScheduledScope): ScheduledStartResult {
        if (!operation.tryLock()) return refuse("执行器生命周期操作正在进行。")
        try {
            closedReason()?.let { return refuse(it) }
            val current = state.value
            if (current.binding != null) {
                if (current.binding != binding) return refuse("Owner 已绑定其他连接或身份；不能切换 scope。")
                if (current.executor.state.value.accepting) return ScheduledStartResult.Started
                return refuse("初始启动已尝试；请显式恢复失败执行器。")
            }
            while (true) {
                val before = state.value
                closedReason(before)?.let { return refuse(it) }
                if (mutableState.compareAndSet(before, before.copy(binding = binding, busy = true))) break
            }
            closedReason()?.let { return refuse(it) }
            return startAndObserve(current.executor, binding)
        } finally {
            mutableState.update { it.copy(busy = false) }
            operation.unlock()
        }
    }

    public suspend fun recover(binding: ScheduledScope, timeoutMillis: Long = 5_000): ScheduledStartResult {
        require(timeoutMillis >= 0)
        if (!operation.tryLock()) return refuse("执行器生命周期操作正在进行。")
        var replacement: ScheduledExecutor? = null
        var published = false
        try {
            closedReason()?.let { return refuse(it) }
            val current = state.value
            if (current.binding == null || current.binding != binding)
                return refuse("恢复仅允许原先核验的完整 scope。")
            if (current.executor.state.value.accepting) return refuse("执行器仍在接受请求，无需恢复。")
            mutableState.update { it.copy(busy = true, error = null) }
            when (val drained = drainRetained(timeoutMillis)) {
                is ScheduledShutdownResult.UnsafeToRelease -> return refuse(drained.reason)
                ScheduledShutdownResult.Drained -> Unit
            }
            closedReason()?.let { return refuse(it) }
            val next = factory()
            if (retained.value.any { it === next }) return refuse("factory 必须返回新的执行器实例。")
            replacement = next
            retained.update { it + next }
            // factory may itself invoke invalidate; registration precedes every start attempt.
            closedReason()?.let { return refuse(it) }
            val result = next.start(binding)
            while (true) {
                val before = state.value
                if (before.invalidated || before.stopped) return refuse(closedReason()!!)
                val error = (result as? ScheduledStartResult.Unavailable)?.reason
                if (mutableState.compareAndSet(before, before.copy(executor = next, error = error))) {
                    published = true
                    // Every predecessor was proven Drained, so only the new instance is retained.
                    retained.update { listOf(next) }
                    return result
                }
            }
        } catch (cancelled: CancellationException) {
            refuse("恢复等待已取消；原实例与未确认释放的实例仍由 Owner 保留。")
            throw cancelled
        } catch (failure: Exception) {
            return refuse("恢复失败：" + (failure.message ?: failure::class.simpleName ?: "未知异常"))
        } finally {
            try {
                replacement?.takeUnless { published }?.let { retireUnpublished(it, timeoutMillis) }
            } finally {
                mutableState.update { it.copy(busy = false) }
                operation.unlock()
            }
        }
    }

    /** Permanent synchronous admission fence; binding remains the identity of this lifetime. */
    public fun invalidate() {
        mutableState.update { it.copy(invalidated = true, error = "连接或身份已失效；Owner 永久停止准入。") }
        retained.value.forEach { it.invalidate() }
    }

    /** Sets the permanent stop before waiting; a later explicit call may recheck an Unsafe drain. */
    public suspend fun shutdown(timeoutMillis: Long = 5_000): ScheduledShutdownResult {
        require(timeoutMillis >= 0)
        mutableState.update { it.copy(stopped = true) }
        retained.value.forEach { it.invalidate() }
        val result = withTimeoutOrNull(timeoutMillis.coerceAtLeast(1)) {
            operation.lock()
            try {
                mutableState.update { it.copy(busy = true) }
                drainRetained(timeoutMillis)
            } finally {
                mutableState.update { it.copy(busy = false) }
                operation.unlock()
            }
        } ?: ScheduledShutdownResult.UnsafeToRelease("Owner 操作或关闭尚未收敛；保留执行器与 runtime。")
        mutableState.update { it.copy(error = (result as? ScheduledShutdownResult.UnsafeToRelease)?.reason ?: closedReason(it)) }
        return result
    }

    private suspend fun startAndObserve(executor: ScheduledExecutor, binding: ScheduledScope): ScheduledStartResult {
        try {
            val result = executor.start(binding)
            closedReason()?.let { return refuse(it) }
            mutableState.update { it.copy(error = closedReason(it) ?: (result as? ScheduledStartResult.Unavailable)?.reason) }
            return result
        } catch (cancelled: CancellationException) {
            // start owns its request independently of this caller. Fence it, retain it, and only
            // observe shutdown; cancelling this await must never allow a late accepting start.
            executor.invalidate()
            withContext(NonCancellable) { observeShutdown(executor, 5_000) }
            refuse("启动等待已取消；执行器仍由 Owner 保留。")
            throw cancelled
        } catch (failure: Exception) {
            return refuse("启动失败：" + (failure.message ?: "未知异常"))
        }
    }

    private suspend fun drainRetained(timeoutMillis: Long): ScheduledShutdownResult =
        withTimeoutOrNull(timeoutMillis.coerceAtLeast(1)) {
            val errors = mutableListOf<String>()
            for (executor in retained.value) {
                val result = observeShutdown(executor, timeoutMillis)
                if (result is ScheduledShutdownResult.UnsafeToRelease) errors += result.reason
            }
            if (errors.isEmpty()) ScheduledShutdownResult.Drained
            else ScheduledShutdownResult.UnsafeToRelease(errors.joinToString("；"))
        } ?: ScheduledShutdownResult.UnsafeToRelease("关闭等待超时；保留原执行器、领导锁与 runtime。")

    private suspend fun observeShutdown(executor: ScheduledExecutor, timeoutMillis: Long): ScheduledShutdownResult =
        try { executor.shutdown(timeoutMillis) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            ScheduledShutdownResult.UnsafeToRelease("关闭失败：" + (failure.message ?: "未知异常"))
        }

    private suspend fun retireUnpublished(executor: ScheduledExecutor, timeoutMillis: Long) {
        executor.invalidate()
        withContext(NonCancellable) {
            when (val result = observeShutdown(executor, timeoutMillis)) {
                ScheduledShutdownResult.Drained -> retained.update { instances -> instances.filterNot { it === executor } }
                is ScheduledShutdownResult.UnsafeToRelease -> mutableState.update {
                    it.copy(error = listOfNotNull(closedReason(it), it.error, result.reason).distinct().joinToString("；"))
                }
            }
        }
    }

    private fun closedReason(current: ScheduledExecutorOwnerState = state.value): String? = when {
        current.invalidated -> "连接或身份已失效；Owner 永久停止准入。"
        current.stopped -> "Owner 已永久停止。"
        else -> null
    }

    private fun refuse(reason: String): ScheduledStartResult.Unavailable {
        mutableState.update { it.copy(error = closedReason(it) ?: reason) }
        return ScheduledStartResult.Unavailable(closedReason() ?: reason)
    }
}

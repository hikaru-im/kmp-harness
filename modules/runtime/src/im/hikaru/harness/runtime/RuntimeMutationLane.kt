package im.hikaru.harness.runtime

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Runtime 的单写入者生命周期通道。
 *
 * 所有 suspend mutation 都按调用顺序串行执行；同一条生命周期调用链中的
 * provide / dispose / reconcile 可以重入，而不会再次等待同一把 Mutex。
 */
internal class RuntimeMutationLane {

    private val mutex =
        Mutex()

    suspend fun <T> run(
        block: suspend () -> T,
    ): T {
        if (
            currentCoroutineContext()[MutationMarker]
                ?.lane === this
        ) {
            return block()
        }

        mutex.lock()

        return try {
            val outcome: MutationOutcome<T> =
                withContext(
                    MutationMarker(this)
                ) {
                    try {
                        MutationOutcome.Success(
                            block()
                        )
                    } catch (error: Throwable) {
                        MutationOutcome.Failure(
                            error
                        )
                    }
                }

            when (outcome) {
                is MutationOutcome.Success ->
                    outcome.value

                is MutationOutcome.Failure ->
                    throw outcome.error
            }
        } finally {
            mutex.unlock()
        }
    }

    /**
     * 异常先作为值穿过 withContext 边界，再在调用方 Context 中重新抛出。
     * 这样不会被 coroutine stack-trace recovery 复制，也不会丢失 suppressed errors。
     */
    private sealed interface MutationOutcome<out T> {

        data class Success<T>(
            val value: T,
        ) : MutationOutcome<T>

        data class Failure(
            val error: Throwable,
        ) : MutationOutcome<Nothing>
    }

    private class MutationMarker(
        val lane: RuntimeMutationLane,
    ) : AbstractCoroutineContextElement(Key) {

        companion object Key :
            CoroutineContext.Key<MutationMarker>
    }
}

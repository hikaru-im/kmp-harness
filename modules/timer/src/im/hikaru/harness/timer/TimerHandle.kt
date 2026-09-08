package im.hikaru.harness.timer

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.Job
import kotlin.time.Duration
import kotlin.time.TimeSource

interface TimerHandle : Disposable {

    val isActive: Boolean

    fun cancel()
}

internal class JobTimerHandle(
    private val job: Job,
) : TimerHandle {

    override val isActive: Boolean
        get() = job.isActive

    override fun cancel() {
        job.cancel()
    }

    override suspend fun dispose() {
        cancel()
    }
}

interface TimerTrigger<in T : Any> : Disposable {

    val isDisposed: Boolean

    /** dispose 后返回 false，不再接受新的触发。 */
    suspend fun submit(value: T): Boolean
}

suspend fun TimerTrigger<Unit>.trigger(): Boolean =
    submit(Unit)

data class ThrottleOptions(
    val trailing: Boolean = true,
)

enum class IntervalFailurePolicy {
    STOP,
    CONTINUE,
}

fun interface TimerFailureHandler {

    suspend fun onFailure(error: Throwable)
}

fun interface TimerClock {

    fun now(): Duration
}

object MonotonicTimerClock : TimerClock {

    private val origin =
        TimeSource.Monotonic.markNow()

    override fun now(): Duration =
        origin.elapsedNow()
}

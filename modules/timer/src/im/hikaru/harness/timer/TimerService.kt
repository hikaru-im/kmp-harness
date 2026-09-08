package im.hikaru.harness.timer

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Coroutine timer 调度器。
 *
 * serviceScope 由 TimerPlugin 生命周期控制；每个返回 handle 又注册到调用方 Context，
 * 任意一侧 dispose 都会取消实际 Job。
 */
class TimerService internal constructor(
    private val serviceScope: CoroutineScope,
    private val failureHandler: TimerFailureHandler,
    internal val clock: TimerClock,
) : Disposable {

    internal val isActive: Boolean
        get() = serviceScope.isActive

    fun timeout(
        owner: Context,
        delay: Duration,
        callback: suspend Context.() -> Unit,
    ): TimerHandle {
        require(delay >= ZERO) {
            "Timeout delay must not be negative"
        }

        return schedule(owner) {
            delay(delay)
            invokeCallback(owner, callback)
        }
    }

    fun interval(
        owner: Context,
        period: Duration,
        initialDelay: Duration = period,
        failurePolicy: IntervalFailurePolicy = IntervalFailurePolicy.STOP,
        callback: suspend Context.() -> Unit,
    ): TimerHandle {
        require(period > ZERO) {
            "Interval period must be positive"
        }

        require(initialDelay >= ZERO) {
            "Interval initialDelay must not be negative"
        }

        return schedule(owner) {
            if (initialDelay > ZERO) {
                delay(initialDelay)
            }

            while (currentCoroutineContext().isActive) {
                val succeeded =
                    invokeCallback(owner, callback)

                if (
                    !succeeded &&
                    failurePolicy == IntervalFailurePolicy.STOP
                ) {
                    break
                }

                delay(period)
            }
        }
    }

    fun <T : Any> debounce(
        owner: Context,
        delay: Duration,
        callback: suspend Context.(T) -> Unit,
    ): TimerTrigger<T> {
        require(delay >= ZERO) {
            "Debounce delay must not be negative"
        }

        check(serviceScope.isActive) {
            "TimerService is disposed"
        }

        val trigger =
            DebounceTrigger(
                owner = owner,
                delay = delay,
                service = this,
                callback = callback,
            )

        owner.effect(trigger)
        return trigger
    }

    fun <T : Any> throttle(
        owner: Context,
        window: Duration,
        options: ThrottleOptions = ThrottleOptions(),
        callback: suspend Context.(T) -> Unit,
    ): TimerTrigger<T> {
        require(window > ZERO) {
            "Throttle window must be positive"
        }

        check(serviceScope.isActive) {
            "TimerService is disposed"
        }

        val trigger =
            ThrottleTrigger(
                owner = owner,
                window = window,
                options = options,
                service = this,
                callback = callback,
            )

        owner.effect(trigger)
        return trigger
    }

    override suspend fun dispose() {
        serviceScope.cancel()
    }

    internal fun launch(
        block: suspend CoroutineScope.() -> Unit,
    ): Job =
        serviceScope.launch(
            start = CoroutineStart.LAZY,
            block = block,
        )

    internal suspend fun <T : Any> invokeCallback(
        owner: Context,
        value: T,
        callback: suspend Context.(T) -> Unit,
    ): Boolean =
        invokeCallback(owner) {
            callback(value)
        }

    private fun schedule(
        owner: Context,
        block: suspend CoroutineScope.() -> Unit,
    ): TimerHandle {
        check(serviceScope.isActive) {
            "TimerService is disposed"
        }

        val job =
            launch(block)

        val handle =
            JobTimerHandle(job)

        try {
            owner.effect(handle)
        } catch (error: Throwable) {
            job.cancel()
            throw error
        }

        job.start()
        return handle
    }

    private suspend fun invokeCallback(
        owner: Context,
        callback: suspend Context.() -> Unit,
    ): Boolean {
        return try {
            owner.callback()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failureHandler.onFailure(error)
            false
        }
    }
}

private class DebounceTrigger<T : Any>(
    private val owner: Context,
    private val delay: Duration,
    private val service: TimerService,
    private val callback: suspend Context.(T) -> Unit,
) : TimerTrigger<T> {

    private val mutex =
        Mutex()

    private var pending: Job? =
        null

    private var disposed =
        false

    override val isDisposed: Boolean
        get() = disposed || !service.isActive

    override suspend fun submit(value: T): Boolean {
        return mutex.withLock {
            if (disposed || !service.isActive) {
                return@withLock false
            }

            pending?.cancel()

            val next =
                service.launch {
                    delay(delay)
                    service.invokeCallback(
                        owner = owner,
                        value = value,
                        callback = callback,
                    )
                }

            pending =
                next

            next.start()
            true
        }
    }

    override suspend fun dispose() {
        mutex.withLock {
            if (!disposed) {
                disposed =
                    true

                pending?.cancel()
                pending =
                    null
            }
        }
    }
}

private class ThrottleTrigger<T : Any>(
    private val owner: Context,
    private val window: Duration,
    private val options: ThrottleOptions,
    private val service: TimerService,
    private val callback: suspend Context.(T) -> Unit,
) : TimerTrigger<T> {

    private val stateMutex =
        Mutex()

    private val executionMutex =
        Mutex()

    private var lastStart: Duration? =
        null

    private var trailingJob: Job? =
        null

    private var trailingValue: T? =
        null

    private var disposed =
        false

    override val isDisposed: Boolean
        get() = disposed || !service.isActive

    override suspend fun submit(value: T): Boolean {
        return stateMutex.withLock {
            if (disposed || !service.isActive) {
                return@withLock false
            }

            val elapsed =
                lastStart?.let { start ->
                    (service.clock.now() - start)
                        .coerceAtLeast(ZERO)
                }

            if (elapsed == null || elapsed >= window) {
                lastStart =
                    service.clock.now()

                launchExecution(value)
                return@withLock true
            }

            if (!options.trailing) {
                return@withLock true
            }

            trailingValue =
                value

            if (trailingJob == null) {
                val remaining =
                    window - elapsed

                val next =
                    service.launch {
                        delay(remaining)

                        val pending =
                            stateMutex.withLock {
                                if (disposed) {
                                    return@launch
                                }

                                val result =
                                    checkNotNull(trailingValue)

                                trailingValue =
                                    null

                                trailingJob =
                                    null

                                lastStart =
                                    service.clock.now()

                                result
                            }

                        execute(pending)
                    }

                trailingJob =
                    next

                next.start()
            }

            true
        }
    }

    override suspend fun dispose() {
        stateMutex.withLock {
            if (!disposed) {
                disposed =
                    true

                trailingJob?.cancel()
                trailingJob =
                    null

                trailingValue =
                    null
            }
        }
    }

    private fun launchExecution(value: T) {
        service.launch {
            execute(value)
        }.start()
    }

    private suspend fun execute(value: T) {
        executionMutex.withLock {
            service.invokeCallback(
                owner = owner,
                value = value,
                callback = callback,
            )
        }
    }
}

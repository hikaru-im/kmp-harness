package im.hikaru.harness.timer

import im.hikaru.harness.runtime.Context
import kotlin.time.Duration

fun Context.timeout(
    delay: Duration,
    callback: suspend Context.() -> Unit,
): TimerHandle =
    require(TimerKey).timeout(
        owner = this,
        delay = delay,
        callback = callback,
    )

fun Context.interval(
    period: Duration,
    initialDelay: Duration = period,
    failurePolicy: IntervalFailurePolicy = IntervalFailurePolicy.STOP,
    callback: suspend Context.() -> Unit,
): TimerHandle =
    require(TimerKey).interval(
        owner = this,
        period = period,
        initialDelay = initialDelay,
        failurePolicy = failurePolicy,
        callback = callback,
    )

fun <T : Any> Context.debounce(
    delay: Duration,
    callback: suspend Context.(T) -> Unit,
): TimerTrigger<T> =
    require(TimerKey).debounce(
        owner = this,
        delay = delay,
        callback = callback,
    )

fun <T : Any> Context.throttle(
    window: Duration,
    options: ThrottleOptions = ThrottleOptions(),
    callback: suspend Context.(T) -> Unit,
): TimerTrigger<T> =
    require(TimerKey).throttle(
        owner = this,
        window = window,
        options = options,
        callback = callback,
    )

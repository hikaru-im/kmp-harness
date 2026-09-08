@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package im.hikaru.harness.timer

import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimerServiceTest {

    @Test
    fun timeoutShouldRunOnceAfterDelay() = runTest {
        val fixture =
            installTimer()

        var calls =
            0

        val handle =
            fixture.runtime.context.timeout(1.seconds) {
                calls++
            }

        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, calls)
        assertTrue(handle.isActive)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, calls)
        assertFalse(handle.isActive)

        fixture.runtime.context.dispose()
    }

    @Test
    fun callerContextDisposeShouldCancelOwnedTimer() = runTest {
        val fixture =
            installTimer()

        val child =
            fixture.runtime.context.child()

        var calls =
            0

        val handle =
            child.timeout(1.seconds) {
                calls++
            }

        child.dispose()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, calls)
        assertFalse(handle.isActive)

        fixture.runtime.context.dispose()
    }

    @Test
    fun timerPluginDisposeShouldCancelAllTimers() = runTest {
        val fixture =
            installTimer()

        var calls =
            0

        val handle =
            fixture.runtime.context.timeout(1.seconds) {
                calls++
            }

        val trigger =
            fixture.runtime.context.debounce<Unit>(1.seconds) {}

        fixture.runtime.uninstall(fixture.fiber)

        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, calls)
        assertFalse(handle.isActive)
        assertTrue(trigger.isDisposed)
        assertFalse(trigger.trigger())
    }

    @Test
    fun intervalShouldUseFixedDelayWithoutOverlap() = runTest {
        val fixture =
            installTimer()

        val starts =
            mutableListOf<Long>()

        val handle =
            fixture.runtime.context.interval(
                period = 1.seconds,
            ) {
                starts +=
                    testScheduler.currentTime

                delay(500.milliseconds)
            }

        advanceTimeBy(3_500)
        runCurrent()

        assertEquals(
            listOf(1_000L, 2_500L),
            starts,
        )

        handle.dispose()
        fixture.runtime.context.dispose()
    }

    @Test
    fun intervalFailurePolicyShouldStopOrContinue() = runTest {
        val stopped =
            installTimer()

        var stopCalls =
            0

        stopped.runtime.context.interval(
            period = 1.seconds,
            initialDelay = 0.seconds,
            failurePolicy = IntervalFailurePolicy.STOP,
        ) {
            stopCalls++
            error("stop")
        }

        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(1, stopCalls)
        assertEquals(listOf("stop"), stopped.errors.map { it.message })

        stopped.runtime.context.dispose()

        val continued =
            installTimer()

        var continueCalls =
            0

        val handle =
            continued.runtime.context.interval(
                period = 1.seconds,
                initialDelay = 0.seconds,
                failurePolicy = IntervalFailurePolicy.CONTINUE,
            ) {
                continueCalls++
                error("continue")
            }

        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(3, continueCalls)
        assertEquals(3, continued.errors.size)

        handle.dispose()
        continued.runtime.context.dispose()
    }

    @Test
    fun debounceShouldOnlyDeliverLatestValue() = runTest {
        val fixture =
            installTimer()

        val values =
            mutableListOf<String>()

        val trigger =
            fixture.runtime.context.debounce<String>(
                delay = 1.seconds,
            ) { value ->
                values += value
            }

        assertTrue(trigger.submit("first"))
        advanceTimeBy(500)
        assertTrue(trigger.submit("second"))
        advanceTimeBy(999)
        runCurrent()
        assertTrue(values.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("second"), values)

        trigger.dispose()
        assertFalse(trigger.submit("ignored"))

        fixture.runtime.context.dispose()
    }

    @Test
    fun throttleShouldRunLeadingAndLatestTrailingValue() = runTest {
        val fixture =
            installTimer()

        val values =
            mutableListOf<Int>()

        val trigger =
            fixture.runtime.context.throttle<Int>(
                window = 1.seconds,
            ) { value ->
                values += value
            }

        trigger.submit(1)
        runCurrent()

        advanceTimeBy(100)
        trigger.submit(2)

        advanceTimeBy(100)
        trigger.submit(3)

        advanceTimeBy(800)
        runCurrent()

        assertEquals(listOf(1, 3), values)

        trigger.dispose()
        assertTrue(trigger.isDisposed)
        assertFalse(trigger.submit(4))

        fixture.runtime.context.dispose()
    }

    @Test
    fun throttleWithoutTrailingShouldDropWindowCalls() = runTest {
        val fixture =
            installTimer()

        val values =
            mutableListOf<Int>()

        val trigger =
            fixture.runtime.context.throttle<Int>(
                window = 1.seconds,
                options = ThrottleOptions(
                    trailing = false,
                ),
            ) { value ->
                values += value
            }

        trigger.submit(1)
        runCurrent()
        trigger.submit(2)

        advanceTimeBy(1_000)
        trigger.submit(3)
        runCurrent()

        assertEquals(listOf(1, 3), values)

        trigger.dispose()
        fixture.runtime.context.dispose()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.installTimer(): Fixture {
        val runtime =
            Runtime()

        val errors =
            mutableListOf<Throwable>()

        val fiber =
            runtime.install(
                TimerPlugin(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    clock = TimerClock {
                        testScheduler.currentTime.milliseconds
                    },
                    failureHandler =
                        TimerFailureHandler { error ->
                            errors += error
                        },
                )
            )

        return Fixture(
            runtime = runtime,
            fiber = fiber,
            errors = errors,
        )
    }

    private data class Fixture(
        val runtime: Runtime,
        val fiber: im.hikaru.harness.runtime.plugin.Fiber<Unit>,
        val errors: MutableList<Throwable>,
    )
}

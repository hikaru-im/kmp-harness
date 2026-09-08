package im.hikaru.harness.runtime.event

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * EventsService 基础行为测试。
 */
class EventsServiceTest {

    private data class TestEvent(
        val value: String,
    )

    private object TestEventKey :
        EventKey<TestEvent>("test")

    private object TestParallelKey :
        ParallelEventKey<TestEvent>("test.parallel")

    private object TestSequentialKey :
        SequentialEventKey<TestEvent>("test.sequential")

    private object TestSerialKey :
        SerialEventKey<TestEvent, Any>("test.serial")

    private object TestBailKey :
        BailEventKey<TestEvent, Any>("test.bail")

    private object TestWaterfallKey :
        WaterfallEventKey<Int, Int>("test.waterfall")

    private object TestSuspendWaterfallKey :
        SuspendWaterfallEventKey<Int, Int>("test.suspend-waterfall")

    private object TestPipelineKey :
        PipelineEventKey<String>("test.pipeline")

    /**
     * emit 后，
     * 已注册 Listener 应该收到事件。
     */
    @Test
    fun listenerShouldReceiveEmittedEvent() = runTest {
        val events =
            EventsService()

        var received:
            TestEvent? = null

        events.on(
            TestEventKey,
        ) { event ->
            received = event
        }

        val event =
            TestEvent(
                value = "hello",
            )

        events.emit(
            TestEventKey,
            event,
        )

        assertEquals(
            event,
            received,
        )
    }

    /**
     * Listener 应该按照注册顺序执行。
     */
    @Test
    fun listenersShouldRunInRegistrationOrder() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(
            TestEventKey,
        ) {
            calls += "first"
        }

        events.on(
            TestEventKey,
        ) {
            calls += "second"
        }

        events.on(
            TestEventKey,
        ) {
            calls += "third"
        }

        events.emit(
            TestEventKey,
            TestEvent("hello"),
        )

        assertEquals(
            listOf(
                "first",
                "second",
                "third",
            ),
            calls,
        )
    }

    /**
     * EventsService.on() 返回的 Disposable
     * 应该能够注销 Listener。
     */
    @Test
    fun disposedListenerShouldNotReceiveEvents() = runTest {
        val events =
            EventsService()

        var callCount = 0

        val disposable =
            events.on(
                TestEventKey,
            ) {
                callCount += 1
            }

        /**
         * 第一次 emit：
         *
         * Listener 还存在。
         */
        events.emit(
            TestEventKey,
            TestEvent("first"),
        )

        assertEquals(
            1,
            callCount,
        )

        /**
         * 注销 Listener。
         */
        disposable.dispose()

        /**
         * 第二次 emit：
         *
         * Listener 已不存在。
         */
        events.emit(
            TestEventKey,
            TestEvent("second"),
        )

        assertEquals(
            1,
            callCount,
        )
    }

    @Test
    fun prependAndOnceShouldControlListenerOrderAndLifetime() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(TestEventKey) {
            calls += "normal"
        }

        events.once(
            key = TestEventKey,
            options = EventOptions(prepend = true),
        ) {
            calls += "once"
        }

        events.emit(TestEventKey, TestEvent("first"))
        events.emit(TestEventKey, TestEvent("second"))

        assertEquals(
            listOf("once", "normal", "normal"),
            calls,
        )
    }

    @Test
    fun dispatchShouldUseListenerSnapshot() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        var secondRegistered =
            false

        events.on(TestEventKey) {
            calls += "first"

            if (!secondRegistered) {
                secondRegistered = true
                events.on(TestEventKey) {
                    calls += "second"
                }
            }
        }

        events.emit(TestEventKey, TestEvent("first"))
        events.emit(TestEventKey, TestEvent("second"))

        assertEquals(
            listOf("first", "first", "second"),
            calls,
        )
    }

    @Test
    fun emitShouldBeSynchronousAndStopOnFailure() {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(TestEventKey) {
            calls += "first"
        }

        events.on(TestEventKey) {
            calls += "failure"
            error("emit failure")
        }

        events.on(TestEventKey) {
            calls += "late"
        }

        val error =
            assertFailsWith<IllegalStateException> {
                events.emit(TestEventKey, TestEvent("emit"))
            }

        assertEquals("emit failure", error.message)
        assertEquals(listOf("first", "failure"), calls)
    }

    @Test
    fun parallelShouldRunAllListenersAndAggregateFailures() = runTest {
        val events =
            EventsService()

        val firstStarted =
            CompletableDeferred<Unit>()

        val secondStarted =
            CompletableDeferred<Unit>()

        val release =
            CompletableDeferred<Unit>()

        events.on(TestParallelKey) {
            firstStarted.complete(Unit)
            release.await()
            error("first failure")
        }

        events.on(TestParallelKey) {
            secondStarted.complete(Unit)
            release.await()
            throw IllegalArgumentException("second failure")
        }

        val dispatch =
            async {
                assertFailsWith<IllegalStateException> {
                    events.parallel(
                        TestParallelKey,
                        TestEvent("parallel"),
                    )
                }
            }

        firstStarted.await()
        secondStarted.await()
        assertFalse(dispatch.isCompleted)

        release.complete(Unit)

        val error =
            dispatch.await()

        assertEquals("first failure", error.message)
        assertEquals(1, error.suppressedExceptions.size)
        assertEquals(
            "second failure",
            error.suppressedExceptions.single().message,
        )
    }

    @Test
    fun sequentialShouldAwaitEveryListenerInRegistrationOrder() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        val firstStarted =
            CompletableDeferred<Unit>()

        val releaseFirst =
            CompletableDeferred<Unit>()

        events.on(TestSequentialKey) {
            calls += "first.before"
            firstStarted.complete(Unit)
            releaseFirst.await()
            calls += "first.after"
        }

        events.on(TestSequentialKey) {
            calls += "second"
        }

        val dispatch =
            async {
                events.sequential(
                    TestSequentialKey,
                    TestEvent("sequential"),
                )
            }

        firstStarted.await()

        assertEquals(listOf("first.before"), calls)
        assertFalse(dispatch.isCompleted)

        releaseFirst.complete(Unit)
        dispatch.await()

        assertEquals(
            listOf("first.before", "first.after", "second"),
            calls,
        )
    }

    @Test
    fun sequentialOnceShouldBeConsumedOnlyWhenReached() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.once(TestSequentialKey) {
            calls += "once"
        }

        events.on(TestSequentialKey) {
            calls += "persistent"
        }

        events.sequential(TestSequentialKey, TestEvent("first"))
        events.sequential(TestSequentialKey, TestEvent("second"))

        assertEquals(
            listOf("once", "persistent", "persistent"),
            calls,
        )
    }

    @Test
    fun sequentialShouldStopOnFailure() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(TestSequentialKey) {
            calls += "failure"
            error("sequential failure")
        }

        events.on(TestSequentialKey) {
            calls += "late"
        }

        val error =
            assertFailsWith<IllegalStateException> {
                events.sequential(
                    TestSequentialKey,
                    TestEvent("failure"),
                )
            }

        assertEquals("sequential failure", error.message)
        assertEquals(listOf("failure"), calls)
    }

    @Test
    fun sequentialShouldPropagateCancellationWithoutCallingLaterListeners() = runTest {
        val events =
            EventsService()

        val started =
            CompletableDeferred<Unit>()

        var lateCalled = false

        events.on(TestSequentialKey) {
            started.complete(Unit)
            awaitCancellation()
        }

        events.on(TestSequentialKey) {
            lateCalled = true
        }

        val dispatch =
            async {
                events.sequential(
                    TestSequentialKey,
                    TestEvent("cancel"),
                )
            }

        started.await()
        dispatch.cancel()

        assertFailsWith<CancellationException> {
            dispatch.await()
        }
        assertFalse(lateCalled)
    }

    @Test
    fun serialShouldIgnoreNullAndFalseThenReturnFirstBailValue() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(TestSerialKey) {
            calls += "first"
            null
        }

        events.on(TestSerialKey) {
            calls += "second"
            false
        }

        events.on(TestSerialKey) {
            calls += "third"
            "accepted"
        }

        events.on(TestSerialKey) {
            calls += "late"
            "late"
        }

        val result =
            events.serial(
                TestSerialKey,
                TestEvent("result"),
            )

        assertEquals("accepted", result)
        assertEquals(listOf("first", "second", "third"), calls)
    }

    @Test
    fun bailShouldSynchronouslyReturnFirstBailValue() {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(TestBailKey) {
            calls += "null"
            null
        }

        events.on(TestBailKey) {
            calls += "false"
            false
        }

        events.on(TestBailKey) {
            calls += "accepted"
            "accepted"
        }

        events.on(TestBailKey) {
            calls += "late"
            "late"
        }

        assertEquals(
            "accepted",
            events.bail(TestBailKey, TestEvent("bail")),
        )
        assertEquals(listOf("null", "false", "accepted"), calls)
    }

    @Test
    fun waterfallShouldRunAsSynchronousOnionMiddleware() {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        events.on(TestWaterfallKey) { value, next ->
            calls += "outer.before"
            val result = next()
            calls += "outer.after"
            value + result
        }

        events.on(TestWaterfallKey) { value, next ->
            calls += "inner.before"
            val result = next()
            calls += "inner.after"
            value + result
        }

        assertEquals(
            4,
            events.waterfall(TestWaterfallKey, 1) { value ->
                calls += "terminal"
                value * 2
            },
        )
        assertEquals(
            listOf(
                "outer.before",
                "inner.before",
                "terminal",
                "inner.after",
                "outer.after",
            ),
            calls,
        )
    }

    @Test
    fun waterfallShortCircuitShouldNotConsumeUnreachedOnceListener() {
        val events =
            EventsService()

        var shortCircuit = true

        events.on(TestWaterfallKey) { value, next ->
            if (shortCircuit) value else value + next()
        }

        events.once(TestWaterfallKey) { value, next ->
            value * 10 + next()
        }

        assertEquals(
            1,
            events.waterfall(TestWaterfallKey, 1) { 2 },
        )

        shortCircuit = false

        assertEquals(
            13,
            events.waterfall(TestWaterfallKey, 1) { 2 },
        )
        assertEquals(
            3,
            events.waterfall(TestWaterfallKey, 1) { 2 },
        )
    }

    @Test
    fun suspendWaterfallShouldAwaitAsynchronousOnionMiddleware() = runTest {
        val events =
            EventsService()

        val calls =
            mutableListOf<String>()

        val terminalStarted =
            CompletableDeferred<Unit>()

        val releaseTerminal =
            CompletableDeferred<Unit>()

        events.on(TestSuspendWaterfallKey) { value, next ->
            calls += "outer.before"
            val result = next()
            calls += "outer.after"
            value + result
        }

        events.on(TestSuspendWaterfallKey) { value, next ->
            calls += "inner.before"
            val result = next()
            calls += "inner.after"
            value + result
        }

        val dispatch =
            async {
                events.waterfall(TestSuspendWaterfallKey, 1) { value ->
                    calls += "terminal.before"
                    terminalStarted.complete(Unit)
                    releaseTerminal.await()
                    calls += "terminal.after"
                    value * 2
                }
            }

        terminalStarted.await()

        assertEquals(
            listOf("outer.before", "inner.before", "terminal.before"),
            calls,
        )
        assertFalse(dispatch.isCompleted)

        releaseTerminal.complete(Unit)

        assertEquals(4, dispatch.await())
        assertEquals(
            listOf(
                "outer.before",
                "inner.before",
                "terminal.before",
                "terminal.after",
                "inner.after",
                "outer.after",
            ),
            calls,
        )
    }

    @Test
    fun suspendWaterfallShortCircuitShouldNotConsumeUnreachedOnceListener() = runTest {
        val events =
            EventsService()

        var shortCircuit = true

        events.on(TestSuspendWaterfallKey) { value, next ->
            if (shortCircuit) value else value + next()
        }

        events.once(TestSuspendWaterfallKey) { value, next ->
            value * 10 + next()
        }

        assertEquals(
            1,
            events.waterfall(TestSuspendWaterfallKey, 1) { 2 },
        )

        shortCircuit = false

        assertEquals(
            13,
            events.waterfall(TestSuspendWaterfallKey, 1) { 2 },
        )
        assertEquals(
            3,
            events.waterfall(TestSuspendWaterfallKey, 1) { 2 },
        )
    }

    @Test
    fun suspendWaterfallShouldPropagateFailureAndCancellation() = runTest {
        val failureEvents =
            EventsService()

        failureEvents.on(TestSuspendWaterfallKey) { _, _ ->
            error("waterfall failure")
        }

        val error =
            assertFailsWith<IllegalStateException> {
                failureEvents.waterfall(TestSuspendWaterfallKey, 1) { 2 }
            }

        assertEquals("waterfall failure", error.message)

        val cancellationEvents =
            EventsService()

        val started =
            CompletableDeferred<Unit>()

        var terminalCalled = false

        cancellationEvents.on(TestSuspendWaterfallKey) { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }

        val dispatch =
            async {
                cancellationEvents.waterfall(TestSuspendWaterfallKey, 1) {
                    terminalCalled = true
                    2
                }
            }

        started.await()
        dispatch.cancel()

        assertFailsWith<CancellationException> {
            dispatch.await()
        }
        assertFalse(terminalCalled)
    }

    @Test
    fun pipelineShouldFeedEachListenerOutputForward() = runTest {
        val events =
            EventsService()

        events.on(TestPipelineKey) { value ->
            "$value-a"
        }

        events.on(TestPipelineKey) { value ->
            "$value-b"
        }

        assertEquals(
            "start-a-b",
            events.pipeline(
                TestPipelineKey,
                "start",
            ),
        )
    }
}

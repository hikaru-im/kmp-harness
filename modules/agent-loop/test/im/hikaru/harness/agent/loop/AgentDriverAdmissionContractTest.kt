package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentAttemptResume
import im.hikaru.harness.agent.AgentAttemptResumer
import im.hikaru.harness.agent.AgentEvents
import im.hikaru.harness.agent.AgentHandle
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentSendTarget
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.AgentStatusNotice
import im.hikaru.harness.agent.AgentWakeup
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CompletedTurnEndReason
import im.hikaru.harness.session.SessionAdmissionException
import im.hikaru.harness.session.SessionAdmissionRejection
import im.hikaru.harness.session.SessionErrorCode
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.SessionException
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.SessionReservationBusyReason
import im.hikaru.harness.session.SessionReservationResult
import im.hikaru.harness.session.StepStartEvent
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.UserMessageEvent
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real dispatchers and public lifecycle/admission APIs only; no network or launch injection. */
class AgentDriverAdmissionContractTest {
    private class Fixture(val runtime: Runtime, val handle: AgentHandle) {
        val agent: Agent get() = handle.agent
        val resumer: AgentAttemptResumer get() = assertIs<AgentAttemptResumer>(agent)

        suspend fun assertBusy() {
            assertEquals(
                SessionReservationResult.Busy(SessionReservationBusyReason.TARGET_ACTIVITY),
                runtime.context.sessions.admission.reserve(agent.session),
            )
        }

        suspend fun assertReleased() {
            val result = runtime.context.sessions.admission.reserve(agent.session)
            assertIs<SessionReservationResult.Reserved>(result).reservation.dispose()
        }

        suspend fun dispose() {
            withContext(NonCancellable) {
                var failure: Throwable? = null
                try {
                    if (agent.context.isDisposed) {
                        // Complete child disposal retires its exact Session.
                        assertTrue(agent.context.isDisposed)
                        val error = assertFailsWith<SessionException> { handle.dispose() }
                        if (error.code != SessionErrorCode.CLOSED_LIFECYCLE || error.suppressedExceptions.isNotEmpty()) {
                            throw error
                        }
                        assertEquals(SessionErrorCode.CLOSED_LIFECYCLE, error.code)
                    } else {
                        handle.dispose()
                    }
                } catch (error: Throwable) {
                    failure = error
                }
                try {
                    runtime.context.dispose()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
                }
                failure?.let { throw it }
            }
        }
    }

    private fun driverTest(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking {
        withTimeout(15_000) { block() }
    }

    private suspend fun fixture(
        body: suspend FlowCollector<StreamChunk>.(GenerateOptions) -> Unit = { answer() },
    ): Fixture {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.context.llm.registerAdapter(
            providers = listOf("driver-contract"),
            adapter = object : LlmAdapter {
                override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow { body(options) }
            },
        )
        runtime.install(AgentLoopPlugin())
        return Fixture(
            runtime,
            runtime.context.agents.create(options = AgentOptions(provider = "driver-contract", model = "local")),
        )
    }

    private suspend fun FlowCollector<StreamChunk>.answer() {
        emit(BlockStartChunk(0, "text"))
        emit(TextDeltaChunk(0, "local answer"))
        emit(FinishChunk(StopFinishReason))
    }

    private fun message(text: String) = createUserMessage(listOf(TextBlock(text)))

    // EventsService contains synchronous listener errors. Propagate assertions
    // through a Deferred as well, so containment cannot turn a failed check green.
    private fun observe(result: CompletableDeferred<Unit>, block: suspend () -> Unit) {
        try {
            runBlocking { withTimeout(10_000) { block() } }
            result.complete(Unit)
        } catch (error: Throwable) {
            result.completeExceptionally(error)
            throw error
        }
    }

    @Test
    fun closingRejectsSendCancelAndResumeBeforeStateMutation() = driverTest {
        val f = fixture()
        var beforeAttemptCalls = 0
        val notices = MutableStateFlow(0)
        val listener = f.runtime.context.on(AgentEvents.Status) { notices.value += 1 }
        try {
            val events = f.agent.session.events()
            val inbox = f.agent.inbox.snapshot()
            val status = f.agent.status
            val reservation = assertIs<SessionReservationResult.Reserved>(
                f.runtime.context.sessions.admission.reserve(f.agent.session),
            ).reservation
            try {
                val operations: List<suspend () -> Unit> = listOf(
                    { f.agent.followup(message("rejected")); Unit },
                    { f.agent.cancel(keepInbox = false) },
                    { f.resumer.resumeAttempt(AgentAttemptResume(1L, 1L, 2) { beforeAttemptCalls++ }) },
                )
                for (operation in operations) {
                    val error = assertFailsWith<SessionAdmissionException> { operation() }
                    assertEquals(SessionAdmissionRejection.CLOSING, error.reason)
                    assertEquals(events, f.agent.session.events())
                    assertEquals(inbox, f.agent.inbox.snapshot())
                    assertEquals(status, f.agent.status)
                    assertTrue(reservation.isActive())
                }
                assertEquals(0, notices.value)
                assertEquals(0, beforeAttemptCalls)
            } finally {
                reservation.dispose()
            }
            f.agent.followup(message("admitted"))
            f.agent.awaitIdle()
            assertEquals(1, f.agent.session.events().count { it.type == SessionEventNames.TURN_START })
            f.assertReleased()
        } finally {
            listener.dispose()
            f.dispose()
        }
    }

    @Test
    fun cancelledInitializationCanRetryFromPersistedTurnNumber() = driverTest {
        val f = fixture()
        val holdingHistory = CompletableDeferred<Unit>()
        val releaseHistory = CompletableDeferred<Unit>()
        val historyObserved = CompletableDeferred<Unit>()
        val listener = f.runtime.context.on(SessionEvents.Appended) { notice ->
            if (notice.session === f.agent.session && notice.event.type == SessionEventNames.TURN_START) {
                observe(historyObserved) {
                    holdingHistory.complete(Unit)
                    releaseHistory.await()
                }
            }
        }
        try {
            val persisting = async(Dispatchers.Default) {
                f.agent.session.append(SessionEventKeys.TurnStart, TurnStartEvent(41L))
            }
            holdingHistory.await()
            // Session.events must wait on the real append mutex. Both callers
            // reach the shared initialization result before that mutex opens.
            val loader = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                f.agent.followup(message("cancelled loader"))
            }
            val follower = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                f.agent.followup(message("same result"))
            }
            assertFalse(loader.isCompleted)
            assertFalse(follower.isCompleted)
            loader.cancelAndJoin()
            assertFailsWith<CancellationException> { follower.await() }
            assertEquals(AgentStatus.IDLE, f.agent.status)
            assertTrue(f.agent.inbox.snapshot().nextTurn.isEmpty())
            releaseHistory.complete(Unit)
            persisting.await()
            historyObserved.await()
            listener.dispose()
            f.agent.session.append(SessionEventKeys.TurnEnd, TurnEndEvent(41L, CompletedTurnEndReason))
            f.assertReleased()
            f.agent.followup(message("retry initialization"))
            f.agent.awaitIdle()
            val turns = f.agent.session.events().filter { it.type == SessionEventNames.TURN_START }
            assertEquals(listOf(41L, 42L), turns.map { it.data.jsonObject.getValue("turn").jsonPrimitive.long })
            f.assertReleased()
        } finally {
            releaseHistory.complete(Unit)
            listener.dispose()
            f.dispose()
        }
    }

    @Test
    fun driverPermitCoversRunningBodyAndIdleNotification() = driverTest {
        val bodyEntered = CompletableDeferred<Unit>()
        val releaseBody = CompletableDeferred<Unit>()
        val f = fixture {
            bodyEntered.complete(Unit)
            releaseBody.await()
            answer()
        }
        val runningEntered = CompletableDeferred<Unit>()
        val releaseRunning = CompletableDeferred<Unit>()
        val idleEntered = CompletableDeferred<Unit>()
        val releaseIdle = CompletableDeferred<Unit>()
        val runningChecked = CompletableDeferred<Unit>()
        val idleChecked = CompletableDeferred<Unit>()
        val listener = f.runtime.context.on(AgentEvents.Status) { notice ->
            if (notice.agent === f.agent) when (notice.status) {
                AgentStatus.RUNNING -> observe(runningChecked) {
                    f.assertBusy()
                    runningEntered.complete(Unit)
                    releaseRunning.await()
                    f.assertBusy()
                }
                AgentStatus.IDLE -> observe(idleChecked) {
                    f.assertBusy()
                    idleEntered.complete(Unit)
                    releaseIdle.await()
                    f.assertBusy()
                }
            }
        }
        try {
            val sending = async(Dispatchers.Default) { f.agent.followup(message("run")) }
            runningEntered.await()
            sending.await() // The public operation's activity is gone.
            f.assertBusy()
            releaseRunning.complete(Unit)
            runningChecked.await()
            bodyEntered.await()
            f.assertBusy()
            releaseBody.complete(Unit)
            idleEntered.await()
            f.assertBusy()
            assertEquals(AgentStatus.IDLE, f.agent.status)
            releaseIdle.complete(Unit)
            idleChecked.await()
            f.agent.awaitIdle()
            f.assertReleased()
        } finally {
            releaseRunning.complete(Unit)
            releaseBody.complete(Unit)
            releaseIdle.complete(Unit)
            listener.dispose()
            f.dispose()
        }
    }

    @Test
    fun idleFollowupHandoffKeepsContinuousSessionActivity() = driverTest {
        val calls = MutableStateFlow(0)
        val firstBody = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondBody = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val stopObserving = CompletableDeferred<Unit>()
        val observing = CompletableDeferred<Unit>()
        val f = fixture {
            calls.value += 1
            if (calls.value == 1) { firstBody.complete(Unit); releaseFirst.await() }
            else { secondBody.complete(Unit); releaseSecond.await() }
            answer()
        }
        val followupChecked = CompletableDeferred<Unit>()
        val enqueued = MutableStateFlow(false)
        val listener = f.runtime.context.on(AgentEvents.Status) { notice ->
            if (notice.agent === f.agent && notice.status == AgentStatus.IDLE && enqueued.compareAndSet(false, true)) {
                observe(followupChecked) {
                    f.assertBusy()
                    f.agent.followup(message("from IDLE"))
                    f.assertBusy()
                }
            }
        }
        try {
            f.agent.followup(message("first"))
            firstBody.await()
            // A competing reservation samples the whole handoff, including the
            // interval after the public followup has returned.
            val observer = async(Dispatchers.Default) {
                while (!stopObserving.isCompleted) {
                    val result = f.runtime.context.sessions.admission.reserve(f.agent.session)
                    if (result is SessionReservationResult.Reserved) {
                        result.reservation.dispose()
                        error("Handoff exposed a zero-activity reservation window")
                    }
                    assertEquals(SessionReservationResult.Busy(SessionReservationBusyReason.TARGET_ACTIVITY), result)
                    observing.complete(Unit)
                    yield()
                }
            }
            observing.await()
            releaseFirst.complete(Unit)
            followupChecked.await()
            secondBody.await()
            f.assertBusy()
            stopObserving.complete(Unit)
            observer.await()
            releaseSecond.complete(Unit)
            f.agent.awaitIdle()
            assertEquals(2, calls.value)
            assertTrue(f.agent.inbox.snapshot().nextTurn.isEmpty())
            f.assertReleased()
        } finally {
            stopObserving.complete(Unit)
            releaseFirst.complete(Unit)
            releaseSecond.complete(Unit)
            listener.dispose()
            f.dispose()
        }
    }

    @Test
    fun runningNoticeCanCheckAlreadyRunningWithoutMutexDeadlock() = driverTest {
        val f = fixture()
        val checked = CompletableDeferred<Unit>()
        val listener = f.runtime.context.on(AgentEvents.Status) { notice ->
            if (notice.agent === f.agent && notice.status == AgentStatus.RUNNING) {
                observe(checked) {
                    val error = assertFailsWith<IllegalStateException> {
                        f.resumer.resumeAttempt(AgentAttemptResume(1L, 1L, 2))
                    }
                    assertEquals("Agent is already running", error.message)
                    f.assertBusy()
                }
            }
        }
        try {
            f.agent.followup(message("reentrant notice"))
            checked.await()
            f.agent.awaitIdle()
            assertEquals(1, f.agent.session.events().count { it.type == SessionEventNames.TURN_START })
            f.assertReleased()
        } finally {
            listener.dispose()
            f.dispose()
        }
    }

    @Test
    fun closedContextNotificationFailureStillReleasesDriverPermit() = driverTest {
        val f = fixture()
        val containedError = CompletableDeferred<Unit>()
        val runningEntered = CompletableDeferred<Unit>()
        val releaseRunning = CompletableDeferred<Unit>()
        val runningChecked = CompletableDeferred<Unit>()
        val disposalEntered = CompletableDeferred<Unit>()
        val releaseDisposal = CompletableDeferred<Unit>()
        val throwing = f.runtime.context.on(AgentEvents.Status) { notice ->
            if (notice.agent === f.agent && notice.status == AgentStatus.RUNNING) {
                containedError.complete(Unit)
                error("contained listener failure")
            }
        }
        val blocking = f.runtime.context.on(AgentEvents.Status) { notice ->
            if (notice.agent === f.agent && notice.status == AgentStatus.RUNNING) {
                observe(runningChecked) {
                    runningEntered.complete(Unit)
                    releaseRunning.await()
                }
            }
        }
        // Reverse effect disposal reaches this public effect before shutdown.
        // The child Context is closing, while its owned Job and Session remain
        // live. No Runtime owner-stop or mutation-lane safety claim is made.
        f.agent.context.effect {
            disposalEntered.complete(Unit)
            withTimeout(10_000) { releaseDisposal.await() }
        }
        try {
            val sending = async(Dispatchers.Default) { f.agent.inject(message("NEXT_STEP only")) }
            runningEntered.await()
            containedError.await()
            sending.await()
            f.assertBusy()
            val closing = async(Dispatchers.Default) { f.agent.context.dispose() }
            disposalEntered.await()
            assertFalse(closing.isCompleted)
            val error = assertFailsWith<IllegalStateException> {
                f.agent.context.emitContained(AgentEvents.Status, AgentStatusNotice(f.agent, AgentStatus.IDLE))
            }
            assertEquals("Context is disposed", error.message)
            releaseRunning.complete(Unit)
            runningChecked.await()
            // NEXT_TURN is empty: driver finalization itself attempts the IDLE
            // notification against the now-closing Context and throws.
            f.agent.awaitIdle()
            assertEquals(AgentStatus.IDLE, f.agent.status)
            assertTrue(f.agent.session.events().none { it.type == SessionEventNames.TURN_START })
            f.assertReleased()
            releaseDisposal.complete(Unit)
            closing.await()
        } finally {
            releaseRunning.complete(Unit)
            releaseDisposal.complete(Unit)
            throwing.dispose()
            blocking.dispose()
            f.dispose()
        }
    }

    @Test
    fun cancelledDriverAndDisposedScopeNeverRestartQueuedTurns() = driverTest {
        val calls = MutableStateFlow(0)
        var beforeAttemptCalls = 0
        val entered = CompletableDeferred<Unit>()
        val cancelDriver = CompletableDeferred<Unit>()
        val f = fixture {
            calls.value += 1
            entered.complete(Unit)
            cancelDriver.await()
            // Cancel the real driver, without setting Agent stopping: reading
            // NonCancellable.isActive in finally would restart the queued turn.
            currentCoroutineContext()[Job]!!.cancel()
            awaitCancellation()
        }
        try {
            f.agent.followup(message("first"))
            entered.await()
            val queued = f.agent.followup(message("kept"))
            cancelDriver.complete(Unit)
            f.agent.awaitIdle()
            assertEquals(1, calls.value)
            assertEquals(listOf(queued), f.agent.inbox.snapshot().nextTurn.map { it.id })
            f.assertReleased()
            val keptInbox = f.agent.inbox.snapshot()
            f.agent.context.dispose()
            assertTrue(f.agent.context.isDisposed)
            val error = assertFailsWith<SessionException> {
                f.resumer.resumeAttempt(AgentAttemptResume(1L, 1L, 2) { beforeAttemptCalls++ })
            }
            assertEquals(SessionErrorCode.CLOSED_LIFECYCLE, error.code)
            f.agent.awaitIdle()
            assertEquals(
                SessionReservationResult.Rejected(SessionAdmissionRejection.CLOSED_SESSION),
                f.runtime.context.sessions.admission.reserve(f.agent.session),
            )
            assertEquals(1, calls.value)
            assertEquals(0, beforeAttemptCalls)
            assertEquals(listOf(queued), f.agent.inbox.snapshot().nextTurn.map { it.id })
            assertEquals(keptInbox, f.agent.inbox.snapshot())
        } finally {
            cancelDriver.complete(Unit)
            f.dispose()
        }
    }

    @Test
    fun concurrentCancelsCannotReleaseEachOthersStoppingState() = driverTest {
        val calls = MutableStateFlow(0)
        val bodyEntered = CompletableDeferred<Unit>()
        val stoppingEntered = CompletableDeferred<Unit>()
        val releaseStopping = CompletableDeferred<Unit>()
        val clearEntered = CompletableDeferred<Unit>()
        val releaseClear = CompletableDeferred<Unit>()
        val clearChecked = CompletableDeferred<Unit>()
        val f = fixture {
            calls.value += 1
            if (calls.value == 1) { bodyEntered.complete(Unit); awaitCancellation() }
            answer()
        }
        val stoppingListener = f.runtime.context.on(AgentEvents.TurnStopping) { notice ->
            if (notice.agent === f.agent && notice.turn == 1L) {
                stoppingEntered.complete(Unit)
                releaseStopping.await()
            }
        }
        val clearListener = f.runtime.context.on(AgentEvents.InboxDiscarded) { notice ->
            if (notice.agent === f.agent) observe(clearChecked) {
                clearEntered.complete(Unit)
                releaseClear.await()
            }
        }
        try {
            f.agent.followup(message("first"))
            bodyEntered.await()
            f.agent.followup(message("will be cleared"))
            val first = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                f.agent.cancel(keepInbox = true)
            }
            stoppingEntered.await()
            val second = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                f.agent.cancel(keepInbox = false)
            }
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            releaseStopping.complete(Unit)
            clearEntered.await()
            first.await()
            assertFalse(second.isCompleted)
            f.assertBusy()
            val error = assertFailsWith<IllegalStateException> {
                f.resumer.resumeAttempt(AgentAttemptResume(1L, 1L, 2))
            }
            assertEquals("Agent is stopping", error.message)
            // This real wakeup waits for the clear's Inbox mutex. Whether it
            // inserts before or after stop release, reconciliation must see it.
            val arriving = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                f.agent.followup(message("after clear"))
            }
            assertFalse(arriving.isCompleted)
            releaseClear.complete(Unit)
            clearChecked.await()
            second.await()
            arriving.await()
            f.agent.awaitIdle()
            assertEquals(2, calls.value)
            assertTrue(f.agent.inbox.snapshot().nextTurn.isEmpty())
            f.assertReleased()
        } finally {
            releaseStopping.complete(Unit)
            releaseClear.complete(Unit)
            stoppingListener.dispose()
            clearListener.dispose()
            f.dispose()
        }
    }

    @Test
    fun restoredAttemptOwnsIndependentActivityUntilItsFinallyCompletes() = driverTest {
        val calls = MutableStateFlow(0)
        val f = fixture { calls.value += 1; answer() }
        val beforeEntered = CompletableDeferred<Unit>()
        val releaseBefore = CompletableDeferred<Unit>()
        val stoppingEntered = CompletableDeferred<Unit>()
        val releaseStopping = CompletableDeferred<Unit>()
        val listener = f.runtime.context.on(AgentEvents.TurnStopping) { notice ->
            if (notice.agent === f.agent && notice.turn == 7L) {
                f.assertBusy()
                stoppingEntered.complete(Unit)
                releaseStopping.await()
                f.assertBusy()
            }
        }
        try {
            f.agent.session.append(SessionEventKeys.TurnStart, TurnStartEvent(7L))
            f.agent.session.append(SessionEventKeys.StepStart, StepStartEvent(7L, 3L))
            f.agent.session.append(SessionEventKeys.UserMessage, UserMessageEvent(7L, message("restored")))
            val resuming = async(Dispatchers.Default) {
                f.resumer.resumeAttempt(AgentAttemptResume(7L, 3L, 2) {
                    beforeEntered.complete(Unit)
                    releaseBefore.await()
                    currentCoroutineContext()[Job]!!.cancel()
                    awaitCancellation()
                })
            }
            resuming.await()
            beforeEntered.await()
            f.assertBusy()
            releaseBefore.complete(Unit)
            stoppingEntered.await()
            // No public cancel permit can hide premature release of the
            // restored driver's own activity during its suspended finally.
            val finishing = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { f.agent.awaitIdle() }
            assertFalse(finishing.isCompleted)
            f.assertBusy()
            assertEquals(0, calls.value)
            releaseStopping.complete(Unit)
            finishing.await()
            f.agent.awaitIdle()
            val restored = f.agent.session.events()
            assertEquals(1, restored.count { it.type == SessionEventNames.TURN_START })
            assertEquals(1, restored.count { it.type == SessionEventNames.STEP_START })
            assertEquals(1, restored.count { it.type == SessionEventNames.STEP_END })
            assertEquals(1, restored.count { it.type == SessionEventNames.TURN_END })
            assertTrue(restored.none { it.type == SessionEventNames.REQUEST_HEADER })
            f.assertReleased()
            f.agent.followup(message("next persisted turn"))
            f.agent.awaitIdle()
            val turns = f.agent.session.events().filter { it.type == SessionEventNames.TURN_START }
            assertEquals(listOf(7L, 8L), turns.map { it.data.jsonObject.getValue("turn").jsonPrimitive.long })
            assertEquals(1, calls.value)
            f.assertReleased()
        } finally {
            releaseBefore.complete(Unit)
            releaseStopping.complete(Unit)
            listener.dispose()
            f.dispose()
        }
    }
}

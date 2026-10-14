package im.hikaru.harness.agent

import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionAdmissionException
import im.hikaru.harness.session.SessionAdmissionRejection
import im.hikaru.harness.session.SessionClock
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.SessionReservation
import im.hikaru.harness.session.SessionReservationBusyReason
import im.hikaru.harness.session.SessionReservationResult
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AgentRegistryAdmissionContractTest {
    @Test
    fun closingRejectsCreateAndRestoreBeforeFactoryOrReplay() = runTest {
        withFixture { fixture ->
            val loaded = fixture.load("closing-restore")
            val appended = CompletableDeferred<Unit>()
            val releaseAppend = CompletableDeferred<Unit>()
            var factories = 0
            var notices = 0
            fixture.registry.registerFactory(AgentFactory { request ->
                factories++
                TestAgent(request)
            })
            fixture.runtime.context.on(AgentEvents.Created) { notices++ }
            fixture.runtime.context.on(SessionEvents.Appended) { notice ->
                if (notice.session === loaded) {
                    appended.complete(Unit)
                    blockReal(releaseAppend)
                }
            }
            val append = operation { loaded.append(SessionEventKeys.TurnStart, TurnStartEvent(1L)) }
            val jobs = mutableListOf(append.job)
            var reservation: SessionReservation? = null
            try {
                awaitReal(appended)
                // Appended holds the real Session mutex: a premature events()
                // replay would block rather than return Closing.
                reservation = fixture.reserve()
                val create = operation { fixture.registry.create(AgentId("closing-create")) }
                val automatic = operation { fixture.registry.create() }
                val restore = operation { fixture.registry.restore(loaded) }
                jobs += listOf(create.job, automatic.job, restore.job)
                for (attempt in listOf(create, automatic, restore)) {
                    assertClosing(awaitReal(attempt.outcome).exceptionOrNull())
                }
                assertEquals(0, factories)
                assertEquals(0, notices)
                assertTrue(fixture.registry.list().isEmpty())
            } finally {
                releaseAppend.complete(Unit)
                reservation?.dispose()
                stopJobs(*jobs.toTypedArray())
            }
            assertTrue(loaded.isLive())
            assertEquals(listOf(0L), loaded.events().map { it.seq })
            val restored = fixture.registry.restore(loaded)
            assertEquals(1, factories)
            restored.dispose()
            val created = fixture.registry.create(AgentId("closing-create"))
            assertEquals(2, factories)
            created.dispose()
            fixture.assertReleased()
        }
    }

    @Test
    fun catalogPermitCoversCreateFactoryPublicationAndNotices() = runTest {
        withFixture { fixture ->
            val missing = assertFailsWith<AgentException> { fixture.registry.create() }
            assertEquals(AgentErrorCode.NO_FACTORY, missing.code)
            fixture.assertReleased()
            val factoryEntered = CompletableDeferred<AgentFactoryRequest>()
            val releaseFactory = CompletableDeferred<Unit>()
            val created = NoticeGate(fixture)
            val started = NoticeGate(fixture)
            val status = NoticeGate(fixture)
            val notices = mutableListOf<String>()
            fixture.registry.registerFactory(AgentFactory { request ->
                factoryEntered.complete(request)
                awaitReal(releaseFactory)
                TestAgent(request)
            })
            fixture.runtime.context.on(AgentEvents.Created) {
                notices += "created"
                created.pause(it.agent)
            }
            fixture.runtime.context.on(AgentEvents.SessionStart) {
                notices += "session-start"
                started.pause(it.agent)
            }
            fixture.runtime.context.on(AgentEvents.Status) {
                notices += "status"
                status.pause(it.agent)
            }
            // Exercise publication bookkeeping for a generated id too.
            val create = operation { fixture.registry.create() }
            try {
                val request = awaitReal(factoryEntered)
                assertNull(fixture.registry.get(request.id))
                assertSame(request.session, fixture.store.get(request.session.id))
                fixture.assertBusy()
                val duplicate = assertFailsWith<AgentException> { fixture.registry.create(request.id) }
                assertEquals(AgentErrorCode.DUPLICATE_AGENT, duplicate.code)
                fixture.assertBusy()
                releaseFactory.complete(Unit)
                for (gate in listOf(created, started, status)) {
                    val observed = awaitReal(gate.entered)
                    assertCatalogBusy(observed.reservation)
                    assertSame(observed.agent, fixture.registry.get(request.id))
                    assertSame(request.session, observed.agent.session)
                    fixture.assertBusy()
                    gate.release.complete(Unit)
                }
                val handle = awaitReal(create.outcome).getOrThrow()
                assertEquals(listOf("created", "session-start", "status"), notices)
                assertSame(handle.agent, fixture.registry.get(request.id))
                fixture.assertReleased()
                handle.dispose()
                assertNull(fixture.registry.get(request.id))
                assertTrue(handle.agent.context.isDisposed)
                fixture.assertReleased()
            } finally {
                releaseFactory.complete(Unit)
                for (gate in listOf(created, started, status)) gate.release.complete(Unit)
                stopJobs(create.job)
            }
        }
    }

    @Test
    fun catalogPermitCoversRestoreListenerAndRollback() = runTest {
        withFixture { fixture ->
            val loaded = fixture.load("restore-rollback")
            val listenerEntered = CompletableDeferred<Agent>()
            val releaseListener = CompletableDeferred<Unit>()
            val cleanupEntered = CompletableDeferred<SessionReservationResult>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val listenerFailure = IllegalArgumentException("restore listener")
            val cleanupFailure = IllegalStateException("restore cleanup")
            var notices = 0
            fixture.runtime.context.on(AgentEvents.Created) { notices++ }
            fixture.runtime.context.on(AgentEvents.SessionStart) { notices++ }
            fixture.runtime.context.on(AgentEvents.Status) { notices++ }
            fixture.registry.registerFactory(AgentFactory { request ->
                if (request.session === loaded) {
                    request.context.effect {
                        cleanupEntered.complete(fixture.store.admission.reserve(fixture.probe))
                        awaitReal(releaseCleanup)
                        throw cleanupFailure
                    }
                }
                TestAgent(request)
            })
            fixture.runtime.context.on(AgentEvents.Restore) { event ->
                if (event.agent.session === loaded) {
                    listenerEntered.complete(event.agent)
                    awaitReal(releaseListener)
                    throw listenerFailure
                }
            }
            val restore = operation { fixture.registry.restore(loaded) }
            try {
                val published = awaitReal(listenerEntered)
                assertSame(published, fixture.registry.get(published.id))
                assertSame(loaded, published.session)
                fixture.assertBusy()
                assertEquals(0, notices)
                releaseListener.complete(Unit)
                assertCatalogBusy(awaitReal(cleanupEntered))
                assertNull(fixture.registry.get(published.id))
                assertSame(loaded, fixture.store.get(loaded.id))
                assertFalse(published.context.isDisposed)
                fixture.assertBusy()
                releaseCleanup.complete(Unit)
                val failure = awaitReal(restore.outcome).exceptionOrNull()
                assertSame(listenerFailure, failure)
                assertTrue(listenerFailure.suppressedExceptions.any { it === cleanupFailure })
                assertTrue(published.context.isDisposed)
                assertFalse(loaded.isLive())
                assertNull(fixture.store.get(loaded.id))
                assertEquals(0, notices)
                fixture.assertReleased()
                val replacement = fixture.load(loaded.id.value)
                val handle = fixture.registry.restore(replacement)
                assertSame(replacement, handle.agent.session)
                assertEquals(3, notices)
                handle.dispose()
                fixture.assertReleased()
            } finally {
                releaseListener.complete(Unit)
                releaseCleanup.complete(Unit)
                stopJobs(restore.job)
            }
        }
    }

    @Test
    fun cancelledCreateAndRestoreRemoveOnlyTheirOwnPublishedIdentity() = runTest {
        withFixture { fixture ->
            fixture.registry.registerFactory(AgentFactory { TestAgent(it) })
            val survivor = fixture.registry.create(AgentId("survivor"))
            for (restoring in listOf(false, true)) {
                val loaded = if (restoring) fixture.load("cancelled-restore") else null
                val notice = NoticeGate(fixture)
                val cleanupEntered = CompletableDeferred<SessionReservationResult>()
                val releaseCleanup = CompletableDeferred<Unit>()
                val cleanupFailure = IllegalStateException("cancelled cleanup $restoring")
                fixture.registry.replaceFactory(AgentFactory { request ->
                    request.context.effect {
                        cleanupEntered.complete(fixture.store.admission.reserve(fixture.probe))
                        awaitReal(releaseCleanup)
                        throw cleanupFailure
                    }
                    TestAgent(request)
                })
                // Status is the final synchronous notice. Cancellation here
                // must be observed by the last explicit ensureActive().
                val observer = fixture.runtime.context.on(AgentEvents.Status) { notice.pause(it.agent) }
                val attempt = operation {
                    if (loaded == null) fixture.registry.create() else fixture.registry.restore(loaded)
                }
                try {
                    val observed = awaitReal(notice.entered)
                    val published = observed.agent
                    assertCatalogBusy(observed.reservation)
                    assertSame(published, fixture.registry.get(published.id))
                    assertSame(survivor.agent, fixture.registry.get(survivor.agent.id))
                    val cancellation = CancellationException("cancel after publication $restoring")
                    attempt.job.cancel(cancellation)
                    notice.release.complete(Unit)
                    assertCatalogBusy(awaitReal(cleanupEntered))
                    assertNull(fixture.registry.get(published.id))
                    assertSame(survivor.agent, fixture.registry.get(survivor.agent.id))
                    fixture.assertBusy()
                    releaseCleanup.complete(Unit)
                    val failure = assertIs<CancellationException>(awaitReal(attempt.outcome).exceptionOrNull())
                    assertSame(cancellation, failure)
                    assertTrue(failure.suppressedExceptions.any { it === cleanupFailure })
                    assertTrue(attempt.job.isCancelled)
                    assertTrue(published.context.isDisposed)
                    assertNull(fixture.store.get(published.session.id))
                    fixture.assertReleased()
                    observer.dispose()
                    fixture.registry.replaceFactory(AgentFactory { TestAgent(it) })
                    val replacement =
                        if (restoring) fixture.registry.restore(fixture.load(published.id.value))
                        else fixture.registry.create(published.id)
                    assertSame(replacement.agent, fixture.registry.get(published.id))
                    assertSame(survivor.agent, fixture.registry.get(survivor.agent.id))
                    assertFalse(survivor.agent.context.isDisposed)
                    replacement.dispose()
                } finally {
                    notice.release.complete(Unit)
                    releaseCleanup.complete(Unit)
                    observer.dispose()
                    stopJobs(attempt.job)
                }
            }
            // A foreign factory result is never adopted or disposed. Only the
            // failed request's own child and Session may be rolled back.
            fixture.registry.replaceFactory(AgentFactory { survivor.agent })
            val invalid = assertFailsWith<AgentException> { fixture.registry.create(AgentId("invalid")) }
            assertEquals(AgentErrorCode.INVALID_FACTORY, invalid.code)
            assertNull(fixture.registry.get(AgentId("invalid")))
            assertNull(fixture.store.get(SessionId("invalid")))
            val invalidLoaded = fixture.load("invalid-restore")
            val invalidRestore = assertFailsWith<AgentException> { fixture.registry.restore(invalidLoaded) }
            assertEquals(AgentErrorCode.INVALID_FACTORY, invalidRestore.code)
            assertNull(fixture.registry.get(AgentId(invalidLoaded.id.value)))
            assertNull(fixture.store.get(invalidLoaded.id))
            assertFalse(invalidLoaded.isLive())
            assertSame(survivor.agent, fixture.registry.get(survivor.agent.id))
            assertFalse(survivor.agent.context.isDisposed)
            assertEquals(0, (survivor.agent as TestAgent).cancellations)
            fixture.assertReleased()
            survivor.dispose()
        }
    }

    @Test
    fun restoreSetupCancellationReleasesIdWithoutClosingUnadoptedSession() = runTest {
        withFixture { fixture ->
            var factories = 0
            fixture.registry.registerFactory(AgentFactory { request -> factories++; TestAgent(request) })
            val loaded = fixture.load("setup-replay")
            val appendEntered = CompletableDeferred<Unit>()
            val releaseAppend = CompletableDeferred<Unit>()
            val appendObserver = fixture.runtime.context.on(SessionEvents.Appended) { notice ->
                if (notice.session === loaded) {
                    appendEntered.complete(Unit)
                    blockReal(releaseAppend)
                }
            }
            val append = operation { loaded.append(SessionEventKeys.TurnStart, TurnStartEvent(1L)) }
            val jobs = mutableListOf(append.job)
            try {
                awaitReal(appendEntered)
                // Undispatched launch reaches the held events() mutex before
                // returning. The child exists, but attach has not run.
                val cancelled = operation(CoroutineStart.UNDISPATCHED) { fixture.registry.restore(loaded) }
                jobs += cancelled.job
                assertFalse(cancelled.job.isCompleted)
                assertNull(fixture.registry.get(AgentId(loaded.id.value)))
                assertEquals(0, factories)
                fixture.assertBusy()
                cancelled.job.cancel(CancellationException("cancel replay setup"))
                assertIs<CancellationException>(awaitReal(cancelled.outcome).exceptionOrNull())
                stopJobs(cancelled.job)
                assertSame(loaded, fixture.store.get(loaded.id))
                fixture.assertReleased()
                val retry = operation(CoroutineStart.UNDISPATCHED) { fixture.registry.restore(loaded) }
                jobs += retry.job
                assertFalse(retry.job.isCompleted)
                assertEquals(0, factories)
                fixture.assertBusy()
                releaseAppend.complete(Unit)
                awaitReal(append.outcome).getOrThrow()
                val restored = awaitReal(retry.outcome).getOrThrow()
                assertSame(loaded, restored.agent.session)
                assertEquals(1, factories)
                assertEquals(listOf(0L), loaded.events().map { it.seq })
                fixture.assertReleased()
                restored.dispose()
            } finally {
                releaseAppend.complete(Unit)
                appendObserver.dispose()
                stopJobs(*jobs.toTypedArray())
            }

            // Exercise cancellation while attach waits for the real Store
            // mutex, independently of replay's Session mutex.
            val attaching = fixture.load("setup-attach")
            val announceEntered = CompletableDeferred<Unit>()
            val releaseAnnounce = CompletableDeferred<Unit>()
            val announceObserver = fixture.runtime.context.on(SessionEvents.Created) { notice ->
                if (notice.session.id == SessionId("attach-blocker")) {
                    announceEntered.complete(Unit)
                    blockReal(releaseAnnounce)
                }
            }
            val blocker = operation { fixture.store.create(SessionId("attach-blocker")) }
            var waiting: Operation<AgentHandle>? = null
            try {
                awaitReal(announceEntered)
                val attempt = operation(CoroutineStart.UNDISPATCHED) { fixture.registry.restore(attaching) }
                waiting = attempt
                assertFalse(attempt.job.isCompleted)
                assertEquals(1, factories)
                fixture.assertBusy()
                attempt.job.cancel(CancellationException("cancel attach setup"))
                assertIs<CancellationException>(awaitReal(attempt.outcome).exceptionOrNull())
                stopJobs(attempt.job)
                assertTrue(attaching.isLive())
                releaseAnnounce.complete(Unit)
                val blockerHandle = awaitReal(blocker.outcome).getOrThrow()
                assertSame(attaching, fixture.store.get(attaching.id))
                blockerHandle.dispose()
                fixture.assertReleased()
                val retry = fixture.registry.restore(attaching)
                assertSame(attaching, retry.agent.session)
                assertEquals(2, factories)
                retry.dispose()
                fixture.assertReleased()
            } finally {
                releaseAnnounce.complete(Unit)
                announceObserver.dispose()
                stopJobs(blocker.job)
                waiting?.let { stopJobs(it.job) }
            }
        }
    }

    @Test
    fun closingHandleRemainsRetryableAndStaleIdentityCannotDetachReplacement() = runTest {
        withFixture { fixture ->
            val cancelEntered = CompletableDeferred<SessionReservationResult>()
            val releaseCancel = CompletableDeferred<Unit>()
            val cleanupEntered = CompletableDeferred<SessionReservationResult>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val cancelFailure = IllegalArgumentException("cancel cleanup")
            val contextFailure = IllegalStateException("context cleanup")
            val disposed = NoticeGate(fixture)
            fixture.registry.registerFactory(AgentFactory { request ->
                request.context.effect {
                    cleanupEntered.complete(fixture.store.admission.reserve(fixture.probe))
                    awaitReal(releaseCleanup)
                    throw contextFailure
                }
                TestAgent(request) {
                    cancelEntered.complete(fixture.store.admission.reserve(fixture.probe))
                    awaitReal(releaseCancel)
                    throw cancelFailure
                }
            })
            val old = fixture.registry.create(AgentId("retry-handle"))
            val oldAgent = old.agent as TestAgent
            val observer = fixture.runtime.context.on(AgentEvents.Disposed) { notice ->
                if (notice.agent === oldAgent) disposed.pause(notice.agent)
            }
            val reservation = assertIs<SessionReservationResult.Reserved>(
                fixture.store.admission.reserve(oldAgent.session),
            ).reservation
            try {
                repeat(2) {
                    assertClosing(assertFailsWith<SessionAdmissionException> { old.dispose() })
                    assertSame(oldAgent, fixture.registry.get(oldAgent.id))
                    assertSame(oldAgent.session, fixture.store.get(oldAgent.session.id))
                    assertFalse(oldAgent.context.isDisposed)
                    assertEquals(0, oldAgent.cancellations)
                    assertFalse(cancelEntered.isCompleted)
                    assertFalse(cleanupEntered.isCompleted)
                    assertFalse(disposed.entered.isCompleted)
                    assertTrue(reservation.isActive())
                }
            } finally {
                reservation.dispose()
            }
            // Close the exact Session first. A target-bound outer catalog
            // permit would now strand this still-live Agent Context.
            fixture.store.attach(oldAgent.session).dispose()
            assertFalse(oldAgent.session.isLive())
            assertFalse(oldAgent.context.isDisposed)
            val detach = operation { old.dispose() }
            try {
                assertCatalogBusy(awaitReal(cancelEntered))
                assertNull(fixture.registry.get(oldAgent.id))
                assertEquals(1, oldAgent.cancellations)
                fixture.assertBusy()
                // Cancellation after removal must not skip any cleanup or
                // notification, even when both cleanup steps throw.
                detach.job.cancel(CancellationException("cancel detach after removal"))
                releaseCancel.complete(Unit)
                assertCatalogBusy(awaitReal(cleanupEntered))
                fixture.assertBusy()
                releaseCleanup.complete(Unit)
                assertCatalogBusy(awaitReal(disposed.entered).reservation)
                assertTrue(oldAgent.context.isDisposed)
                fixture.assertBusy()
                disposed.release.complete(Unit)
                val failure = awaitReal(detach.outcome).exceptionOrNull()
                assertSame(cancelFailure, failure)
                assertTrue(cancelFailure.suppressedExceptions.any { it === contextFailure })
                stopJobs(detach.job)
                assertTrue(detach.job.isCancelled)
                fixture.assertReleased()
                val replacementCancelEntered = CompletableDeferred<Unit>()
                val releaseReplacementCancel = CompletableDeferred<Unit>()
                fixture.registry.replaceFactory(AgentFactory { request ->
                    TestAgent(request) {
                        replacementCancelEntered.complete(Unit)
                        awaitReal(releaseReplacementCancel)
                    }
                })
                val replacement = fixture.registry.create(oldAgent.id)
                val replacementAgent = replacement.agent as TestAgent
                val replacementReservation = assertIs<SessionReservationResult.Reserved>(
                    fixture.store.admission.reserve(replacementAgent.session),
                ).reservation
                try {
                    // The failed dispose never set Handle.disposed: this call
                    // must take the Registry's stale identity path.
                    old.dispose()
                    assertSame(replacementAgent, fixture.registry.get(oldAgent.id))
                    assertSame(replacementAgent.session, fixture.store.get(replacementAgent.session.id))
                    assertFalse(replacementAgent.context.isDisposed)
                    assertEquals(0, replacementAgent.cancellations)
                    assertEquals(1, oldAgent.cancellations)
                    assertTrue(replacementReservation.isActive())
                    assertClosing(assertFailsWith<SessionAdmissionException> { replacement.dispose() })
                } finally {
                    replacementReservation.dispose()
                }
                // Concurrent callers reach the Registry identity check; only
                // one can remove and cancel this replacement.
                val first = operation { replacement.dispose() }
                var second: Operation<Unit>? = null
                try {
                    awaitReal(replacementCancelEntered)
                    assertNull(fixture.registry.get(replacementAgent.id))
                    assertFalse(replacementAgent.context.isDisposed)
                    fixture.assertBusy()
                    val concurrent = operation { replacement.dispose() }
                    second = concurrent
                    awaitReal(concurrent.outcome).getOrThrow()
                    assertFalse(first.job.isCompleted)
                    assertEquals(1, replacementAgent.cancellations)
                    releaseReplacementCancel.complete(Unit)
                    awaitReal(first.outcome).getOrThrow()
                    assertEquals(1, replacementAgent.cancellations)
                    assertNull(fixture.registry.get(replacementAgent.id))
                    assertTrue(replacementAgent.context.isDisposed)
                    fixture.assertReleased()
                } finally {
                    releaseReplacementCancel.complete(Unit)
                    stopJobs(first.job)
                    second?.let { stopJobs(it.job) }
                }
            } finally {
                releaseCancel.complete(Unit)
                releaseCleanup.complete(Unit)
                disposed.release.complete(Unit)
                observer.dispose()
                stopJobs(detach.job)
            }
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin(clock = SessionClock { 100L }))
            runtime.install(AgentPlugin())
            val probeHandle = runtime.context.sessions.create(SessionId("catalog-probe"))
            runtime.context.effect(probeHandle)
            block(Fixture(runtime, probeHandle.session))
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                withTimeout(10_000) { runtime.context.dispose() }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val probe: Session) {
        val store: SessionStore = runtime.context.sessions
        val registry: AgentRegistry = runtime.context.agents

        suspend fun load(id: String): Session {
            val prepared = store.prepare(SessionId(id))
            val handle = store.load(prepared.header, emptyList())
            runtime.context.effect(handle)
            return handle.session
        }

        suspend fun reserve(): SessionReservation =
            assertIs<SessionReservationResult.Reserved>(store.admission.reserve(probe)).reservation

        suspend fun assertBusy() = assertCatalogBusy(store.admission.reserve(probe))

        suspend fun assertReleased() {
            val reservation = reserve()
            try {
                assertTrue(reservation.isActive())
            } finally {
                reservation.dispose()
            }
        }
    }

    private class TestAgent(
        request: AgentFactoryRequest,
        private val onCancel: suspend () -> Unit = {},
    ) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context: Context = request.context
        override val status = AgentStatus.IDLE
        var cancellations = 0
            private set

        override suspend fun awaitIdle() = Unit

        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) {
            cancellations++
            onCancel()
        }

        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String =
            inbox.insert(message, target, wakeup)
    }

    private data class ObservedNotice(val agent: Agent, val reservation: SessionReservationResult)

    private class NoticeGate(private val fixture: Fixture) {
        val entered = CompletableDeferred<ObservedNotice>()
        val release = CompletableDeferred<Unit>()

        fun pause(agent: Agent) {
            runBlocking(Dispatchers.Default) {
                withTimeout(10_000) {
                    // Assertions live outside contained observers so failures
                    // cannot be swallowed by emitContained.
                    entered.complete(ObservedNotice(agent, fixture.store.admission.reserve(fixture.probe)))
                    release.await()
                }
            }
        }
    }

    private data class Operation<T>(val job: Job, val outcome: Deferred<Result<T>>)

    private fun <T> CoroutineScope.operation(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend () -> T,
    ): Operation<T> {
        val outcome = CompletableDeferred<Result<T>>()
        val job = launch(Dispatchers.Default, start = start) {
            outcome.complete(runCatching { block() })
        }
        return Operation(job, outcome)
    }

    private suspend fun <T> awaitReal(deferred: Deferred<T>): T =
        withContext(Dispatchers.Default) { withTimeout(10_000) { deferred.await() } }

    private fun blockReal(release: Deferred<Unit>) {
        runBlocking(Dispatchers.Default) { withTimeout(10_000) { release.await() } }
    }

    private suspend fun stopJobs(vararg jobs: Job) {
        withContext(NonCancellable + Dispatchers.Default) {
            withTimeout(10_000) { for (job in jobs) job.cancelAndJoin() }
        }
    }

    private companion object {
        fun assertCatalogBusy(result: SessionReservationResult) {
            assertEquals(SessionReservationResult.Busy(SessionReservationBusyReason.CATALOG_ACTIVITY), result)
        }

        fun assertClosing(error: Throwable?) {
            assertEquals(SessionAdmissionRejection.CLOSING, assertIs<SessionAdmissionException>(error).reason)
        }
    }
}

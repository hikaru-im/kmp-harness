package im.hikaru.harness.session

import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionAdmissionContractTest {
    private class CountingIds : SessionIdFactory {
        var allocated = 0
        var onCreate: () -> Unit = {}

        override fun create(): SessionId {
            onCreate()
            return SessionId("admission-" + ++allocated)
        }
    }

    private suspend fun installStore(
        runtime: Runtime,
        ids: CountingIds = CountingIds(),
        clock: SessionClock = SessionClock { 100L },
        failures: SessionObserverFailureHandler = SessionObserverFailureHandler {},
    ): SessionStore {
        runtime.install(SessionPlugin(idFactory = ids, clock = clock, observerFailureHandler = failures))
        return runtime.context.sessions
    }

    private suspend fun reserve(store: SessionStore, session: Session): SessionReservation =
        assertIs<SessionReservationResult.Reserved>(store.admission.reserve(session)).reservation

    private suspend fun assertBusy(store: SessionStore, session: Session, reason: SessionReservationBusyReason) {
        assertEquals(SessionReservationResult.Busy(reason), store.admission.reserve(session))
    }

    private suspend fun assertClosing(block: suspend () -> Unit) {
        val error = assertFailsWith<SessionAdmissionException> { block() }
        assertEquals(SessionAdmissionRejection.CLOSING, error.reason)
    }

    private suspend fun append(session: Session, turn: Long = 1L): SessionEventEnvelope =
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(turn))

    @Test
    fun inFlightAppendMakesReservationBusyThroughNotice() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val session = store.create().session
        val other = store.create().session
        val noticed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val listener = runtime.context.on(SessionEvents.Appended) { notice ->
            if (notice.session === session) {
                runBlocking {
                    assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
                    noticed.complete(Unit)
                    withTimeout(10_000) { release.await() }
                    assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
                }
            }
        }
        val appending = async(Dispatchers.Default) { append(session) }
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { noticed.await() } }
            assertFalse(appending.isCompleted)
            assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
            // Another Session's activity does not make this target globally busy.
            val otherReservation = reserve(store, other)
            assertClosing { append(other) }
            otherReservation.dispose()
        } finally {
            release.complete(Unit)
        }
        assertEquals(0L, appending.await().seq)
        listener.dispose()
        val reservation = reserve(store, session)
        assertTrue(reservation.isActive())
        assertSame(session, reservation.session)
        reservation.dispose()
        assertEquals(listOf(0L), session.events().map { it.seq })
        runtime.context.dispose()
    }

    @Test
    fun reservedSessionRejectsAppendRestoreAndLifecycleWithoutMutation() = runTest {
        val runtime = Runtime()
        val ids = CountingIds()
        val store = installStore(runtime, ids)
        val liveHandle = store.create()
        val live = liveHandle.session
        val prepared = store.prepare()
        val otherHandle = store.create()
        val other = otherHandle.session
        val source = store.prepare()
        val history = listOf(append(source))
        var restoreReads = 0
        val unreadableHistory = object : AbstractList<SessionEventEnvelope>() {
            override val size: Int
                get() { restoreReads++; return history.size }

            override fun get(index: Int): SessionEventEnvelope {
                restoreReads++
                return history[index]
            }
        }
        val notices = mutableListOf<String>()
        runtime.context.on(SessionEvents.Created) { notices += "created" }
        runtime.context.on(SessionEvents.Disposed) { notices += "disposed" }
        runtime.context.on(SessionEvents.Appended) { notices += "append" }

        for (target in listOf(prepared, live)) {
            val logBefore = target.events()
            val liveBefore = store.list()
            val idsBefore = ids.allocated
            val reservation = reserve(store, target)
            val blocked: List<Pair<String, suspend () -> Unit>> = listOf(
                "append" to suspend { append(target); Unit },
                "encode" to suspend {
                    target.append(SessionEventKey("admission/invalid", JsonElement.serializer()), JsonPrimitive(Double.NaN))
                    Unit
                },
                "restore" to suspend { target.restore(unreadableHistory) },
                "prepare" to suspend { store.prepare(); Unit },
                "create" to suspend { store.create(); Unit },
                "load" to suspend { store.load(target.header, unreadableHistory); Unit },
                "enter" to suspend { store.enter(target); Unit },
                "announce" to suspend { store.announce(target) },
                "attach" to suspend { store.attach(target); Unit },
                "detach" to suspend { liveHandle.dispose() },
                "other lifecycle" to suspend { otherHandle.dispose() },
                "flush" to suspend { store.flush() },
            )
            try {
                for ((name, operation) in blocked) {
                    assertClosing(operation)
                    assertEquals(logBefore, target.events(), name)
                    assertEquals(liveBefore, store.list(), name)
                    assertEquals(idsBefore, ids.allocated, name)
                    assertEquals(0, restoreReads, name)
                    assertTrue(notices.isEmpty(), name)
                }
                assertSame(live, store.get(live.id))
                assertTrue(target.deriveMessages().isEmpty())
                assertNull(target.requestHeader())
                assertNull(target.requestContext())
                assertBusy(store, other, SessionReservationBusyReason.RESERVATION_ACTIVE)
                append(other)
                assertEquals(listOf("append"), notices)
            } finally {
                reservation.dispose()
            }
            notices.clear()
        }
        prepared.restore(history)
        assertEquals(history, prepared.events())
        append(live)
        assertEquals(1, live.events().size)
        runtime.context.dispose()
    }

    @Test
    fun catalogPermitProtectsCreateLoadAndFlush() = runTest {
        val runtime = Runtime()
        val ids = CountingIds()
        var clockProbe: () -> Unit = {}
        val store = installStore(runtime, ids, SessionClock { clockProbe(); 100L })
        val target = store.create(SessionId("target")).session
        val source = store.prepare(SessionId("load-source"))
        val history = listOf(append(source))
        val phases = mutableListOf<String>()
        ids.onCreate = {
            runBlocking { assertBusy(store, target, SessionReservationBusyReason.CATALOG_ACTIVITY) }
            phases += "id"
        }
        clockProbe = {
            runBlocking { assertBusy(store, target, SessionReservationBusyReason.CATALOG_ACTIVITY) }
            phases += "construct"
        }
        val createdListener = runtime.context.on(SessionEvents.Created) {
            runBlocking { assertBusy(store, target, SessionReservationBusyReason.CATALOG_ACTIVITY) }
            phases += "announce"
        }
        store.create()
        assertEquals(listOf("id", "construct", "announce"), phases)
        phases.clear()
        store.prepare()
        assertEquals(listOf("id", "construct"), phases)
        clockProbe = {}
        phases.clear()
        val observedHistory = object : AbstractList<SessionEventEnvelope>() {
            override val size: Int
                get() = history.size

            override fun get(index: Int): SessionEventEnvelope {
                runBlocking { assertBusy(store, target, SessionReservationBusyReason.CATALOG_ACTIVITY) }
                phases += "restore"
                return history[index]
            }
        }
        val loaded = store.load(source.header, observedHistory).session
        assertEquals(listOf("restore", "announce"), phases)
        assertEquals(history, loaded.events())
        createdListener.dispose()

        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val flushed = mutableListOf<SessionId>()
        val flushListener = runtime.context.on(SessionEvents.Flush) { notice ->
            assertBusy(store, target, SessionReservationBusyReason.CATALOG_ACTIVITY)
            if (notice.session === target) {
                started.complete(Unit)
                release.await()
            }
            flushed += notice.session.id
        }
        val snapshot = store.list().map { it.id }
        val flushing = async { store.flush() }
        try {
            started.await()
            assertFalse(flushing.isCompleted)
            assertBusy(store, target, SessionReservationBusyReason.CATALOG_ACTIVITY)
        } finally {
            release.complete(Unit)
        }
        flushing.await()
        assertEquals(snapshot, flushed)
        flushListener.dispose()
        reserve(store, target).dispose()
        runtime.context.dispose()
    }

    @Test
    fun callbackReservationAndIndependentMutationDoNotHoldStateLock() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val first = store.create().session
        val second = store.create().session
        val callbacks = mutableListOf<SessionId>()
        val listener = runtime.context.on(SessionEvents.Appended) { notice ->
            callbacks += notice.session.id
            runBlocking {
                assertBusy(store, notice.session, SessionReservationBusyReason.TARGET_ACTIVITY)
                if (notice.session === first) append(second)
            }
        }
        // The synchronous observer recursively writes an independent Session;
        // neither callback can depend on release of the first Session's permit.
        append(first)
        assertEquals(listOf(first.id, second.id), callbacks)
        assertEquals(listOf(0L), first.events().map { it.seq })
        assertEquals(listOf(0L), second.events().map { it.seq })
        listener.dispose()
        reserve(store, first).dispose()
        reserve(store, second).dispose()
        runtime.context.dispose()
    }

    @Test
    fun cancellationAndFailuresReleaseOnlyOwnPermits() = runTest {
        val runtime = Runtime()
        val reporting = CompletableDeferred<Unit>()
        val reportRelease = CompletableDeferred<Unit>()
        lateinit var store: SessionStore
        lateinit var session: Session
        store = installStore(runtime, failures = SessionObserverFailureHandler {
            runBlocking {
                assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
                reporting.complete(Unit)
                withTimeout(10_000) { reportRelease.await() }
            }
        })
        session = store.create().session
        val throwing = runtime.context.on(SessionEvents.Appended) { error("observer failure") }
        val appending = async(Dispatchers.Default) { append(session) }
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { reporting.await() } }
            // This real append is admitted and waits on the first append's mutex.
            val waiting = launch(start = CoroutineStart.UNDISPATCHED) { append(session, 2L) }
            assertFalse(waiting.isCompleted)
            waiting.cancelAndJoin()
            assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
        } finally {
            reportRelease.complete(Unit)
        }
        assertEquals(0L, appending.await().seq)
        throwing.dispose()
        reserve(store, session).dispose()

        val firstPermit = store.admission.acquireOperation(session)
        val secondPermit = store.admission.acquireOperation(session)
        firstPermit.dispose()
        firstPermit.dispose()
        assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
        secondPermit.dispose()
        reserve(store, session).dispose()
        val catalogOne = store.admission.acquireCatalog()
        val catalogTwo = store.admission.acquireCatalog()
        catalogOne.dispose()
        catalogOne.dispose()
        assertBusy(store, session, SessionReservationBusyReason.CATALOG_ACTIVITY)
        catalogTwo.dispose()

        val held = store.admission.acquireOperation(session)
        assertFailsWith<SessionException> {
            session.append(SessionEventKey("admission/number", JsonElement.serializer()), JsonPrimitive(Double.NaN))
        }
        assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
        assertFailsWith<SessionException> {
            session.append(SessionEventKeys.TurnStart, TurnStartEvent(3L), sourceEventSeqs = listOf(9L))
        }
        assertEquals(listOf(0L), session.events().map { it.seq })
        assertBusy(store, session, SessionReservationBusyReason.TARGET_ACTIVITY)
        held.dispose()
        reserve(store, session).dispose()

        val prepared = store.prepare()
        assertFailsWith<SessionException> { prepared.restore(listOf(session.events().single().copy(seq = 5L))) }
        assertTrue(prepared.events().isEmpty())
        reserve(store, prepared).dispose()

        val rolledBack = mutableListOf<Session>()
        val disposedListener = runtime.context.on(SessionEvents.Disposed) { notice ->
            runBlocking { assertBusy(store, session, SessionReservationBusyReason.CATALOG_ACTIVITY) }
            rolledBack += notice.session
        }
        val veto = runtime.context.on(SessionEvents.Created) { error("created veto") }
        assertFailsWith<IllegalStateException> { store.create() }
        assertFailsWith<IllegalStateException> { store.load(prepared.header, emptyList()) }
        veto.dispose()
        assertEquals(2, rolledBack.size)
        for (closed in rolledBack) assertFalse(closed.isLive())
        assertEquals(listOf(session), store.list())
        reserve(store, session).dispose()

        val creationJob = CompletableDeferred<kotlinx.coroutines.Job>()
        val cancelCreated = runtime.context.on(SessionEvents.Created) { runBlocking { creationJob.await().cancel() } }
        val cancelledCreation = async(start = CoroutineStart.LAZY) { store.create() }
        creationJob.complete(cancelledCreation)
        cancelledCreation.start()
        assertFailsWith<CancellationException> { cancelledCreation.await() }
        cancelCreated.dispose()
        assertEquals(3, rolledBack.size)
        assertEquals(listOf(session), store.list())
        reserve(store, session).dispose()

        val loadJob = CompletableDeferred<kotlinx.coroutines.Job>()
        val cancelLoaded = runtime.context.on(SessionEvents.Created) { runBlocking { loadJob.await().cancel() } }
        val cancelledLoad = async(start = CoroutineStart.LAZY) { store.load(prepared.header, emptyList()) }
        loadJob.complete(cancelledLoad)
        cancelledLoad.start()
        assertFailsWith<CancellationException> { cancelledLoad.await() }
        cancelLoaded.dispose()
        assertEquals(4, rolledBack.size)
        assertEquals(listOf(session), store.list())
        reserve(store, session).dispose()
        disposedListener.dispose()

        val flushFailure = runtime.context.on(SessionEvents.Flush) { error("flush failure") }
        assertFailsWith<IllegalStateException> { store.flush() }
        flushFailure.dispose()
        reserve(store, session).dispose()

        val flushStarted = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        val cancelledListener = runtime.context.on(SessionEvents.Flush) {
            flushStarted.complete(Unit)
            neverReleased.await()
        }
        val cancelledFlush = launch { store.flush() }
        flushStarted.await()
        assertBusy(store, session, SessionReservationBusyReason.CATALOG_ACTIVITY)
        cancelledFlush.cancelAndJoin()
        cancelledListener.dispose()
        reserve(store, session).dispose()
        runtime.context.dispose()
    }

    @Test
    fun releasedParentPermitCannotAuthorizeInheritedChild() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val session = store.create().session
        val ready = CompletableDeferred<Unit>()
        val releaseChild = CompletableDeferred<Unit>()
        val lateChild = CompletableDeferred<SessionAdmissionException>()
        val parent = launch {
            append(session)
            // The same parent context that just completed an admitted append is
            // inherited by a child whose write starts only after reservation.
            launch(currentCoroutineContext()) {
                ready.complete(Unit)
                releaseChild.await()
                lateChild.complete(assertFailsWith<SessionAdmissionException> { append(session, 2L) })
            }
        }
        ready.await()
        val reservation = reserve(store, session)
        try {
            releaseChild.complete(Unit)
            assertEquals(SessionAdmissionRejection.CLOSING, lateChild.await().reason)
            parent.join()
            assertEquals(listOf(0L), session.events().map { it.seq })
            assertTrue(reservation.isActive())
        } finally {
            reservation.dispose()
        }
        append(session, 3L)
        assertEquals(listOf(0L, 1L), session.events().map { it.seq })
        runtime.context.dispose()
    }

    @Test
    fun staleHandleOrReservationCannotAffectSameIdReplacement() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val id = SessionId("same-id")
        val old = store.create(id)
        val oldAttachment = store.attach(old.session)
        val sameIdPrepared = store.prepare(id)
        val oldReservation = reserve(store, old.session)
        // Equal ids do not grant or deny operation rights on a different instance.
        append(sameIdPrepared)
        assertClosing { append(old.session) }
        assertEquals(1, sameIdPrepared.events().size)
        oldReservation.dispose()
        old.dispose()
        val replacement = store.create(id)
        assertNotSame(old.session, replacement.session)
        val reservation = reserve(store, replacement.session)
        try {
            old.dispose()
            oldAttachment.dispose()
            oldReservation.dispose()
            oldReservation.dispose()
            assertFalse(oldReservation.isActive())
            assertTrue(reservation.isActive())
            assertSame(replacement.session, store.get(id))
            assertEquals(listOf(replacement.session), store.list())
            assertClosing { append(replacement.session) }
            assertEquals(
                SessionReservationResult.Rejected(SessionAdmissionRejection.CLOSED_SESSION),
                store.admission.reserve(old.session),
            )
            assertEquals(SessionErrorCode.CLOSED_LIFECYCLE, assertFailsWith<SessionException> { append(old.session) }.code)
        } finally {
            reservation.dispose()
        }
        val next = reserve(store, replacement.session)
        reservation.dispose()
        assertTrue(next.isActive())
        assertClosing { append(replacement.session) }
        next.dispose()

        val otherRuntime = Runtime()
        val otherStore = installStore(otherRuntime)
        val sameIdElsewhere = otherStore.create(id).session
        assertEquals(
            SessionReservationResult.Rejected(SessionAdmissionRejection.FOREIGN_SESSION),
            store.admission.reserve(sameIdElsewhere),
        )
        assertEquals(SessionErrorCode.NOT_LIVE, assertFailsWith<SessionException> { store.enter(sameIdElsewhere) }.code)
        val otherReservation = reserve(otherStore, sameIdElsewhere)
        oldReservation.dispose()
        assertTrue(otherReservation.isActive())
        assertClosing { append(sameIdElsewhere) }
        otherReservation.dispose()
        append(replacement.session)
        runtime.context.dispose()
        otherRuntime.context.dispose()
    }

    @Test
    fun ownerDisposeInvalidatesReservationAndKeepsOldReferencesClosed() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val first = store.create(SessionId("first"))
        val second = store.create(SessionId("second"))
        val prepared = store.prepare(SessionId("prepared"))
        append(first.session)
        val history = first.session.events()
        val disposed = mutableListOf<SessionId>()
        runtime.context.on(SessionEvents.Disposed) { notice ->
            disposed += notice.session.id
            runBlocking {
                assertEquals(
                    SessionReservationResult.Rejected(SessionAdmissionRejection.OWNER_STOPPED),
                    store.admission.reserve(notice.session),
                )
            }
        }
        val latePermit = store.admission.acquireOperation(second.session)
        val reservation = reserve(store, first.session)
        store.dispose()
        assertFalse(reservation.isActive())
        assertFalse(first.session.isLive())
        assertFalse(second.session.isLive())
        assertEquals(listOf(second.session.id, first.session.id), disposed)
        for (session in listOf(first.session, second.session, prepared)) {
            assertEquals(SessionErrorCode.STORE_DISPOSED, assertFailsWith<SessionException> { append(session) }.code)
            assertEquals(SessionErrorCode.STORE_DISPOSED, assertFailsWith<SessionException> { session.restore(history) }.code)
            assertEquals(SessionReservationResult.Rejected(SessionAdmissionRejection.OWNER_STOPPED), store.admission.reserve(session))
        }
        val blocked: List<suspend () -> Unit> = listOf(
            { store.prepare(); Unit },
            { store.create(); Unit },
            { store.load(first.session.header, history); Unit },
            { store.enter(prepared); Unit },
            { store.attach(first.session); Unit },
            { store.announce(first.session) },
            { store.flush() },
        )
        for (operation in blocked) {
            assertEquals(SessionErrorCode.STORE_DISPOSED, assertFailsWith<SessionException> { operation() }.code)
        }
        reservation.dispose()
        reservation.dispose()
        latePermit.dispose()
        latePermit.dispose()
        first.dispose()
        second.dispose()
        store.dispose()
        assertEquals(listOf(second.session.id, first.session.id), disposed)
        assertEquals(history, first.session.events())

        val replacementRuntime = Runtime()
        val replacementStore = installStore(replacementRuntime)
        val replacement = replacementStore.create(first.session.id).session
        val newReservation = reserve(replacementStore, replacement)
        reservation.dispose()
        assertTrue(newReservation.isActive())
        assertClosing { append(replacement) }
        assertEquals(SessionErrorCode.STORE_DISPOSED, assertFailsWith<SessionException> { append(first.session) }.code)
        newReservation.dispose()
        append(replacement)
        runtime.context.dispose()
        replacementRuntime.context.dispose()
    }
}

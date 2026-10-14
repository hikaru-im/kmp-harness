package im.hikaru.harness.session

import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SessionArchiveContractTest {
    private suspend fun install(
        runtime: Runtime,
        gate: SessionArchiveGate? = null,
    ): SessionStore {
        if (gate != null) runtime.context.provide(SessionArchiveKey, gate)
        runtime.install(SessionPlugin(clock = SessionClock { 100L }))
        return runtime.context.sessions
    }

    @Test
    fun archiveGateFencesLoadAndAttachAtTheEntryLayer() = runTest {
        val runtime = Runtime()
        val archived = SessionId("archived")
        val store = install(runtime, object : SessionArchiveGate {
            override suspend fun isArchived(header: SessionHeader): Boolean = header.id == archived
        })
        val live = store.create(SessionId("live")).session
        val error = assertFailsWith<SessionException> { store.load(live.header.copy(id = archived), emptyList()) }
        assertEquals(SessionErrorCode.ARCHIVED, error.code)
        assertTrue(store.list().none { it.id == archived })
        val gated = store.create(archived)
        val attachError = assertFailsWith<SessionException> { store.attach(gated.session) }
        assertEquals(SessionErrorCode.ARCHIVED, attachError.code)
        // A gate that reports nothing archived leaves the existing load path untouched.
        val admitted = store.load(store.prepare().header, emptyList())
        assertTrue(admitted.session.isLive())
        runtime.context.dispose()
    }

    @Test
    fun withoutAGateTheEntryLayerIsUnchanged() = runTest {
        val runtime = Runtime()
        val store = install(runtime)
        val prepared = store.prepare(SessionId("plain"))
        val loaded = store.load(prepared.header, emptyList())
        assertTrue(loaded.session.isLive())
        assertSame(loaded.session, store.get(SessionId("plain")))
        runtime.context.dispose()
    }

    @Test
    fun archiveOwnershipClosesOperationPermitsButKeepsCatalogAvailable() = runTest {
        val runtime = Runtime()
        val store = install(runtime)
        val session = store.create().session
        val token = assertIs<SessionArchiveAcquire.Granted>(store.admission.acquireArchive(session)).token
        try {
            val error = assertFailsWith<SessionAdmissionException> {
                session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
            }
            assertEquals(SessionAdmissionRejection.CLOSING, error.reason)
            // The owner can still take a catalog permit, so it can stop the Agent and detach the Session.
            store.admission.acquireCatalog().dispose()
            // Another Session is unaffected.
            store.create().session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
        } finally {
            token.dispose()
        }
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(2L))
        assertEquals(listOf(0L), session.events().map { it.seq })
        runtime.context.dispose()
    }

    @Test
    fun archiveOwnershipIsBusyInsteadOfQueueingAndIsExclusive() = runTest {
        val runtime = Runtime()
        val store = install(runtime)
        val session = store.create().session
        val permit = session.acquireOperationPermit()
        try {
            assertEquals(
                SessionArchiveAcquire.Busy(SessionReservationBusyReason.TARGET_ACTIVITY),
                store.admission.acquireArchive(session),
            )
        } finally {
            permit.dispose()
        }
        val reservation = assertIs<SessionReservationResult.Reserved>(store.admission.reserve(session)).reservation
        try {
            assertEquals(
                SessionArchiveAcquire.Busy(SessionReservationBusyReason.RESERVATION_ACTIVE),
                store.admission.acquireArchive(session),
            )
        } finally {
            reservation.dispose()
        }
        val token = assertIs<SessionArchiveAcquire.Granted>(store.admission.acquireArchive(session)).token
        try {
            assertEquals(
                SessionArchiveAcquire.Busy(SessionReservationBusyReason.RESERVATION_ACTIVE),
                store.admission.acquireArchive(session),
            )
        } finally {
            token.dispose()
        }
        runtime.context.dispose()
    }

    @Test
    fun stoppedStoreRejectsArchiveOwnership() = runTest {
        val runtime = Runtime()
        val store = install(runtime)
        val session = store.create().session
        runtime.context.dispose()
        assertEquals(
            SessionArchiveAcquire.Rejected(SessionAdmissionRejection.OWNER_STOPPED),
            store.admission.acquireArchive(session),
        )
    }
}

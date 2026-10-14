package im.hikaru.harness.agent

import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionAdmissionException
import im.hikaru.harness.session.SessionAdmissionRejection
import im.hikaru.harness.session.SessionClock
import im.hikaru.harness.session.SessionErrorCode
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionException
import im.hikaru.harness.session.SessionHandle
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.SessionReservation
import im.hikaru.harness.session.SessionReservationBusyReason
import im.hikaru.harness.session.SessionReservationResult
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InboxAdmissionContractTest {
    @Test
    fun reservedInboxRejectsEveryWriteBeforeMutation() = runTest {
        withFixture { fixture ->
            val changes = mutableListOf<InboxSpliceOperation>()
            val populated = fixture.create("reserved") { changes += it }
            val emptyChanges = mutableListOf<InboxSpliceOperation>()
            val empty = fixture.create("empty") { emptyChanges += it }
            val other = fixture.create("other")
            val populatedNotices = observeNotices(fixture.runtime, populated.agent)
            val emptyNotices = observeNotices(fixture.runtime, empty.agent)
            assertEquals("reserved:0", populated.agent.followup(message("turn")))
            assertEquals("reserved:1", populated.agent.steer(message("step")))
            var reads = 0
            val unreadableContent = object : AbstractList<ContentBlock>() {
                override val size: Int
                    get() { reads++; error("Reserved insert must not copy content") }
                override fun get(index: Int): ContentBlock {
                    reads++
                    error("Reserved insert must not copy content")
                }
            }
            val unreadableItems = object : AbstractList<InboxItem>() {
                override val size: Int
                    get() { reads++; error("Reserved replace must not copy items") }
                override fun get(index: Int): InboxItem {
                    reads++
                    error("Reserved replace must not copy items")
                }
            }
            val blockedMessage = message("blocked").copy(content = unreadableContent)
            for ((live, callbacks, notices) in listOf(
                Triple(populated, changes, populatedNotices),
                Triple(empty, emptyChanges, emptyNotices),
            )) {
                val agent = live.agent
                val eventsBefore = agent.session.events()
                val snapshotBefore = agent.inbox.snapshot()
                val callbacksBefore = callbacks.toList()
                val noticesBefore = notices.toList()
                val reservation = reserve(fixture.store, agent.session)
                try {
                    for ((queue, target) in queues) {
                        assertClosing { agent.inbox.insert(blockedMessage, target, AgentWakeup.NONE) }
                        assertClosing { agent.inbox.claim(queue) }
                        assertClosing { agent.inbox.replace(queue, unreadableItems) }
                        assertClosing { agent.inbox.clear(queue) }
                        assertEquals(eventsBefore, agent.session.events())
                        assertEquals(snapshotBefore, agent.inbox.snapshot())
                        assertEquals(callbacksBefore, callbacks)
                        assertEquals(noticesBefore, notices)
                        assertEquals(0, reads)
                    }
                    other.agent.followup(message("independent"))
                    assertTrue(reservation.isActive())
                    assertEquals(eventsBefore, agent.session.events())
                    assertEquals(snapshotBefore, agent.inbox.snapshot())
                } finally {
                    reservation.dispose()
                }
                val nextId = if (live === populated) 2 else 0
                assertEquals(agent.id.value + ":" + nextId, agent.followup(message("released")))
                assertProjection(agent, (1L..(snapshotBefore.version + 1L)).toList())
            }
            assertProjection(other.agent, listOf(1L, 2L))
        }
    }

    @Test
    fun noticeKeepsReservationBusyThroughProjectionAndNotification() = runTest {
        withFixture { fixture ->
            val changed = CompletableDeferred<InboxSnapshot>()
            val releaseChanged = CompletableDeferred<Unit>()
            val noticed = CompletableDeferred<Result<InboxSnapshot>>()
            val releaseNotice = CompletableDeferred<Unit>()
            lateinit var agent: TestAgent
            agent = fixture.create("notice") {
                assertBusy(fixture.store, agent.session)
                agent.session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
                changed.complete(Inbox.replay(agent.session.events(), agent.id))
                awaitReal(releaseChanged)
                assertBusy(fixture.store, agent.session)
            }.agent
            fixture.runtime.context.on(AgentEvents.InboxInserted) { notice ->
                if (notice.agent === agent) error("Contained observer failure")
            }
            fixture.runtime.context.on(AgentEvents.InboxInserted) { notice ->
                if (notice.agent === agent) {
                    // Contained listener failures must be checked outside the listener.
                    noticed.complete(runCatching {
                        runBlocking {
                            assertBusy(fixture.store, agent.session)
                            Inbox.replay(agent.session.events(), agent.id)
                        }
                    })
                    runBlocking { awaitReal(releaseNotice) }
                }
            }
            val inserting = async(Dispatchers.Default) { agent.followup(message("visible")) }
            try {
                val duringChange = awaitReal(changed)
                assertEquals(1L, duringChange.version)
                assertEquals(listOf("notice:0"), duringChange.nextTurn.map { it.id })
                assertFalse(inserting.isCompleted)
                assertBusy(fixture.store, agent.session)
                releaseChanged.complete(Unit)
                assertEquals(duringChange, awaitReal(noticed).getOrThrow())
                assertFalse(inserting.isCompleted)
                assertBusy(fixture.store, agent.session)
                releaseNotice.complete(Unit)
                assertEquals("notice:0", awaitReal(inserting))
                assertEquals(duringChange, agent.inbox.snapshot())
                assertEquals(2, agent.session.events().size)
                assertProjection(agent, listOf(1L))
                assertReleased(fixture.store, agent.session)
            } finally {
                releaseChanged.complete(Unit)
                releaseNotice.complete(Unit)
                stopJobs(inserting)
            }
        }
    }

    @Test
    fun concurrentMutationsKeepContiguousVersionsAndUniqueIds() = runTest {
        withFixture { fixture ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val changes = mutableListOf<InboxSpliceOperation>()
            lateinit var agent: TestAgent
            agent = fixture.create("parallel") { operation ->
                changes += operation
                assertBusy(fixture.store, agent.session)
                if (changes.size == 1) {
                    entered.complete(Unit)
                    awaitReal(release)
                }
            }.agent
            val notices = observeNotices(fixture.runtime, agent)
            val jobs = mutableListOf<Job>()
            val first = async(Dispatchers.Default) { agent.followup(message("first")) }
            jobs += first
            try {
                awaitReal(entered)
                // Undispatched starts reach the held Inbox mutex before returning.
                val second = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    agent.followup(message("second"))
                }
                jobs += second
                val step = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    agent.steer(message("step"))
                }
                jobs += step
                val claimed = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    agent.inbox.claim(InboxQueue.NEXT_TURN)
                }
                jobs += claimed
                val replacement = InboxItem(
                    "parallel:replacement", message("replacement"), AgentSendTarget.NEXT_STEP, AgentWakeup.NONE,
                )
                val replacing = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    agent.inbox.replace(InboxQueue.NEXT_STEP, listOf(replacement))
                }
                jobs += replacing
                val clearing = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    agent.inbox.clear(InboxQueue.NEXT_STEP)
                }
                jobs += clearing
                assertTrue(jobs.none { it.isCompleted })
                assertBusy(fixture.store, agent.session)
                release.complete(Unit)
                val ids = listOf(awaitReal(first), awaitReal(second), awaitReal(step))
                assertEquals(listOf("parallel:0", "parallel:1", "parallel:2"), ids)
                assertEquals(ids.size, ids.toSet().size)
                assertEquals(listOf("parallel:0"), awaitReal(claimed).map { it.id })
                awaitReal(replacing)
                awaitReal(clearing)
                val operations = listOf(
                    InboxSpliceOperation.INSERT, InboxSpliceOperation.INSERT, InboxSpliceOperation.INSERT,
                    InboxSpliceOperation.CLAIM, InboxSpliceOperation.REPLACE, InboxSpliceOperation.CLEAR,
                )
                assertEquals(operations, changes)
                assertEquals(operations, splices(agent.session).map { it.operation })
                assertEquals(listOf(
                    InboxSpliceOperation.INSERT, InboxSpliceOperation.INSERT, InboxSpliceOperation.INSERT,
                    InboxSpliceOperation.CLAIM, InboxSpliceOperation.CLEAR, InboxSpliceOperation.CLEAR,
                ), notices)
                assertProjection(agent, (1L..6L).toList())
                assertEquals(listOf("parallel:1"), agent.inbox.snapshot().nextTurn.map { it.id })
                assertTrue(agent.inbox.snapshot().nextStep.isEmpty())
                assertReleased(fixture.store, agent.session)
            } finally {
                release.complete(Unit)
                stopJobs(*jobs.toTypedArray())
            }
        }
    }

    @Test
    fun cancelledMutexWaiterDoesNotCommitAndReleasesPermit() = runTest {
        withFixture { fixture ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var changes = 0
            val agent = fixture.create("waiter") {
                changes++
                if (changes == 1) {
                    entered.complete(Unit)
                    awaitReal(release)
                }
            }.agent
            val notices = observeNotices(fixture.runtime, agent)
            val jobs = mutableListOf<Job>()
            val first = async(Dispatchers.Default) { agent.followup(message("first")) }
            jobs += first
            try {
                awaitReal(entered)
                val waiting = CompletableDeferred<Unit>()
                val second = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    waiting.complete(Unit)
                    agent.steer(message("cancelled waiter"))
                }
                jobs += second
                awaitReal(waiting)
                // The first callback holds Inbox's mutex; admission's mutex is free.
                assertFalse(second.isCompleted)
                assertBusy(fixture.store, agent.session)
                stopJobs(second)
                assertTrue(second.isCancelled)
                assertEquals(listOf(1L), splices(agent.session).map { it.version })
                assertEquals(1, changes)
                assertTrue(notices.isEmpty())
                assertBusy(fixture.store, agent.session)
                release.complete(Unit)
                assertEquals("waiter:0", awaitReal(first))
                assertReleased(fixture.store, agent.session)
                assertEquals(listOf(InboxSpliceOperation.INSERT), notices)
                assertEquals("waiter:1", agent.steer(message("next")))
                assertEquals(2, changes)
                assertEquals(2, notices.size)
                assertProjection(agent, listOf(1L, 2L))
                assertReleased(fixture.store, agent.session)
            } finally {
                release.complete(Unit)
                stopJobs(*jobs.toTypedArray())
            }
        }
    }

    @Test
    fun cancellationDuringCommitKeepsReplayProjectionAndIdsConsistent() = runTest {
        withFixture { fixture ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val completedChange = CompletableDeferred<Unit>()
            var changes = 0
            val agent = fixture.create("cancelled") {
                changes++
                if (changes == 1) {
                    entered.complete(Unit)
                    awaitReal(release)
                    currentCoroutineContext().ensureActive()
                    completedChange.complete(Unit)
                }
            }.agent
            val notices = observeNotices(fixture.runtime, agent)
            val inserting = launch(Dispatchers.Default) { agent.followup(message("committed")) }
            try {
                awaitReal(entered)
                assertEquals(listOf(1L), splices(agent.session).map { it.version })
                inserting.cancel()
                assertFalse(inserting.isCompleted)
                assertBusy(fixture.store, agent.session)
                assertTrue(notices.isEmpty())
                release.complete(Unit)
                withContext(Dispatchers.Default) { withTimeout(10_000) { inserting.join() } }
                awaitReal(completedChange)
                assertTrue(inserting.isCancelled)
                assertEquals(listOf(InboxSpliceOperation.INSERT), notices)
                assertProjection(agent, listOf(1L))
                assertEquals(listOf("cancelled:0"), agent.inbox.snapshot().nextTurn.map { it.id })
                assertReleased(fixture.store, agent.session)
                assertEquals("cancelled:1", agent.followup(message("next")))
                assertEquals(2, changes)
                assertEquals(2, notices.size)
                assertProjection(agent, listOf(1L, 2L))
                assertEquals(listOf("cancelled:0", "cancelled:1"), agent.inbox.snapshot().nextTurn.map { it.id })
                assertReleased(fixture.store, agent.session)
            } finally {
                release.complete(Unit)
                stopJobs(inserting)
            }
        }
    }

    // Extra instance state prevents coroutine stacktrace recovery from copying this test error.
    private class CallbackFailure(val marker: Any) : IllegalArgumentException(
        "onChanged failed after commit",
    )

    @Test
    fun callbackFailureCannotReuseCommittedItemId() = runTest {
        withFixture { fixture ->
            val failure = CallbackFailure(Any())
            var fail = true
            val agent = fixture.create("callback") { if (fail) throw failure }.agent
            val notices = observeNotices(fixture.runtime, agent)
            assertSame(failure, assertFailsWith<IllegalArgumentException> { agent.followup(message("committed")) })
            assertProjection(agent, listOf(1L))
            assertEquals(listOf("callback:0"), agent.inbox.snapshot().nextTurn.map { it.id })
            assertTrue(notices.isEmpty())
            assertReleased(fixture.store, agent.session)
            fail = false
            assertEquals("callback:1", agent.followup(message("next")))
            assertProjection(agent, listOf(1L, 2L))
            assertEquals(listOf("callback:0", "callback:1"), agent.inbox.snapshot().nextTurn.map { it.id })
            assertEquals(listOf(InboxSpliceOperation.INSERT), notices)
            assertReleased(fixture.store, agent.session)
        }
    }

    @Test
    fun preCommitFailureReleasesPermitWithoutConsumingIdentity() = runTest {
        var time = 100L
        withFixture(clock = SessionClock { time }) { fixture ->
            var changes = 0
            val agent = fixture.create("clock") { changes++ }.agent
            val notices = observeNotices(fixture.runtime, agent)
            val eventsBefore = agent.session.events()
            val snapshotBefore = agent.inbox.snapshot()
            time = -1L
            for ((queue, target) in queues) {
                val item = InboxItem("clock:external", message("replacement"), target, AgentWakeup.NONE)
                val insertError = assertFailsWith<SessionException> {
                    agent.inbox.insert(message("invalid clock"), target, AgentWakeup.NONE)
                }
                assertEquals(SessionErrorCode.INVALID_EVENT, insertError.code)
                val replaceError = assertFailsWith<SessionException> { agent.inbox.replace(queue, listOf(item)) }
                assertEquals(SessionErrorCode.INVALID_EVENT, replaceError.code)
                assertEquals(eventsBefore, agent.session.events())
                assertEquals(snapshotBefore, agent.inbox.snapshot())
                assertEquals(0, changes)
                assertTrue(notices.isEmpty())
                assertReleased(fixture.store, agent.session)
            }
            time = 100L
            assertEquals("clock:0", agent.followup(message("valid")))
            assertProjection(agent, listOf(1L))
            assertEquals(1, changes)
            assertEquals(listOf(InboxSpliceOperation.INSERT), notices)
            assertReleased(fixture.store, agent.session)
        }
    }

    @Test
    fun staleInboxCannotMutateSameIdReplacement() = runTest {
        withFixture { fixture ->
            val store = fixture.store
            val old = fixture.create("same")
            old.agent.followup(message("old"))
            old.agent.steer(message("old step"))
            val oldEvents = old.agent.session.events()
            val oldSnapshot = old.agent.inbox.snapshot()
            val oldNotices = observeNotices(fixture.runtime, old.agent)
            val staleReservation = reserve(store, old.agent.session)
            staleReservation.dispose()
            old.handle.dispose()
            val replacement = fixture.create("same")
            val notices = observeNotices(fixture.runtime, replacement.agent)
            assertFalse(old.agent.session === replacement.agent.session)
            val before = replacement.agent.session.events()
            val snapshotBefore = replacement.agent.inbox.snapshot()
            val reservation = reserve(store, replacement.agent.session)
            try {
                for ((queue, target) in queues) {
                    val operations: List<suspend () -> Unit> = listOf(
                        { old.agent.inbox.insert(message("stale"), target, AgentWakeup.NONE); Unit },
                        { old.agent.inbox.claim(queue); Unit },
                        { old.agent.inbox.replace(queue, emptyList()) },
                        { old.agent.inbox.clear(queue) },
                    )
                    for (operation in operations) {
                        val error = assertFailsWith<SessionException> { operation() }
                        assertEquals(SessionErrorCode.CLOSED_LIFECYCLE, error.code)
                        assertEquals(oldEvents, old.agent.session.events())
                        assertEquals(oldSnapshot, old.agent.inbox.snapshot())
                        assertEquals(before, replacement.agent.session.events())
                        assertEquals(snapshotBefore, replacement.agent.inbox.snapshot())
                        assertTrue(oldNotices.isEmpty())
                        assertTrue(notices.isEmpty())
                        assertTrue(reservation.isActive())
                    }
                }
                staleReservation.dispose()
                old.handle.dispose()
                assertFalse(staleReservation.isActive())
                assertTrue(reservation.isActive())
                assertSame(replacement.agent.session, store.get(SessionId("same")))
                assertClosing { replacement.agent.followup(message("still reserved")) }
            } finally {
                reservation.dispose()
            }
            assertEquals("same:0", replacement.agent.followup(message("replacement")))
            assertProjection(replacement.agent, listOf(1L))
            assertEquals(listOf(InboxSpliceOperation.INSERT), notices)
            assertReleased(store, replacement.agent.session)
            val stoppedEvents = replacement.agent.session.events()
            val stoppedSnapshot = replacement.agent.inbox.snapshot()
            fixture.runtime.context.dispose()
            for ((queue, target) in queues) {
                val operations: List<suspend () -> Unit> = listOf(
                    { replacement.agent.inbox.insert(message("stopped"), target, AgentWakeup.NONE); Unit },
                    { replacement.agent.inbox.claim(queue); Unit },
                    { replacement.agent.inbox.replace(queue, emptyList()) },
                    { replacement.agent.inbox.clear(queue) },
                )
                for (operation in operations) {
                    val error = assertFailsWith<SessionException> { operation() }
                    assertEquals(SessionErrorCode.STORE_DISPOSED, error.code)
                }
            }
            assertEquals(stoppedEvents, replacement.agent.session.events())
            assertEquals(stoppedSnapshot, replacement.agent.inbox.snapshot())
            assertEquals(listOf(InboxSpliceOperation.INSERT), notices)
        }
    }

    private suspend fun withFixture(
        clock: SessionClock = SessionClock { 100L },
        block: suspend (Fixture) -> Unit,
    ) {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin(clock = clock))
            runtime.install(AgentPlugin())
            block(Fixture(runtime))
        } finally {
            withContext(NonCancellable) { runtime.context.dispose() }
        }
    }

    private class Fixture(val runtime: Runtime) {
        val store: SessionStore = runtime.context.sessions

        suspend fun create(
            id: String,
            onChanged: suspend (InboxSpliceOperation) -> Unit = {},
        ): LiveAgent {
            val handle = store.create(SessionId(id))
            runtime.context.effect(handle)
            val inbox = Inbox(AgentId(id), handle.session, eventContext = runtime.context, onChanged = onChanged)
            val agent = TestAgent(AgentId(id), handle.session, inbox, runtime.context)
            inbox.bindAgent(agent)
            return LiveAgent(handle, agent)
        }
    }

    private data class LiveAgent(val handle: SessionHandle, val agent: TestAgent)

    private class TestAgent(
        override val id: AgentId,
        override val session: Session,
        override val inbox: Inbox,
        override val context: Context,
    ) : Agent {
        override val options = AgentOptions()
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) {
            if (!keepInbox) {
                inbox.clear(InboxQueue.NEXT_TURN)
                inbox.clear(InboxQueue.NEXT_STEP)
            }
        }
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String =
            inbox.insert(message, target, wakeup)
    }

    private fun message(text: String): Message = createUserMessage(listOf(TextBlock(text)))

    private fun observeNotices(runtime: Runtime, agent: Agent): MutableList<InboxSpliceOperation> {
        val notices = mutableListOf<InboxSpliceOperation>()
        runtime.context.on(AgentEvents.InboxInserted) {
            if (it.agent === agent) notices += InboxSpliceOperation.INSERT
        }
        runtime.context.on(AgentEvents.InboxClaimed) {
            if (it.agent === agent) notices += InboxSpliceOperation.CLAIM
        }
        runtime.context.on(AgentEvents.InboxDiscarded) {
            if (it.agent === agent) notices += InboxSpliceOperation.CLEAR
        }
        return notices
    }

    private suspend fun reserve(store: SessionStore, session: Session): SessionReservation =
        assertIs<SessionReservationResult.Reserved>(store.admission.reserve(session)).reservation

    private suspend fun assertBusy(store: SessionStore, session: Session) {
        assertEquals(
            SessionReservationResult.Busy(SessionReservationBusyReason.TARGET_ACTIVITY),
            store.admission.reserve(session),
        )
    }

    private suspend fun assertReleased(store: SessionStore, session: Session) {
        val reservation = reserve(store, session)
        try {
            assertTrue(reservation.isActive())
        } finally {
            reservation.dispose()
        }
    }

    private suspend fun assertClosing(block: suspend () -> Unit) {
        val error = assertFailsWith<SessionAdmissionException> { block() }
        assertEquals(SessionAdmissionRejection.CLOSING, error.reason)
    }

    private suspend fun splices(session: Session): List<InboxSpliceEvent> =
        session.events().filter { it.type == AgentSessionEventNames.INBOX_SPLICED }.map {
            Json.decodeFromJsonElement(InboxSpliceEvent.serializer(), it.data)
        }

    private suspend fun assertProjection(agent: Agent, versions: List<Long>) {
        val events = agent.session.events()
        assertEquals(versions, splices(agent.session).map { it.version })
        assertEquals(events.indices.map { it.toLong() }, events.map { it.seq })
        assertEquals(Inbox.replay(events, agent.id), agent.inbox.snapshot())
    }

    private suspend fun <T> awaitReal(deferred: Deferred<T>): T =
        withContext(Dispatchers.Default) { withTimeout(10_000) { deferred.await() } }

    private suspend fun stopJobs(vararg jobs: Job) {
        withContext(NonCancellable + Dispatchers.Default) {
            withTimeout(10_000) { for (job in jobs) job.cancelAndJoin() }
        }
    }

    private val queues = listOf(
        InboxQueue.NEXT_TURN to AgentSendTarget.NEXT_TURN,
        InboxQueue.NEXT_STEP to AgentSendTarget.NEXT_STEP,
    )
}

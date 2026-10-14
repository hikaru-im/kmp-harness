package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopScheduledSessionEvidenceReadOnlyTest {
    private val host = HostId("scheduled-readonly-evidence")
    private val scope = ScheduledScope(host, null, 1L)

    @Test
    fun repeatedHistoricalOpenTurnInspectionIsPendingAndDoesNotRepairRoomOrLoad() = runBlocking {
        withFixture { fixture ->
            val writer = fixture.open()
            val session = writer.context.createSession(SessionId("historical-open"))
            appendOpenTurn(session)
            writer.context.persistence.flush(session)
            writer.context.dispose()
            val before = fixture.rows()
            val runtime = fixture.open()
            val counts = observe(runtime)
            val provider = RecordingPersistence(runtime.context.persistence)
            withProvider(runtime, provider) { evidence ->
                repeat(4) { assertEquals(ScheduledEvidenceResult.Pending, evidence.inspect(scope, run(session.id))) }
            }
            assertEquals(List(4) { "read:${session.id.value}" }, provider.calls)
            assertEquals(emptyList(), runtime.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
            assertEquals(before, fixture.rows())
            assertFalse(before.second.flatten().any { it.type == SessionEventNames.TURN_END })
        }
    }

    @Test
    fun historicalCompletedAndOldInterruptedRequireTheExactDurableInput() = runBlocking {
        withFixture { fixture ->
            val writer = fixture.open()
            val sessions = listOf(CompletedTurnEndReason, InterruptedTurnEndReason).mapIndexed { index, reason ->
                val session = writer.context.createSession(SessionId("historical-terminal-$index"))
                appendOpenTurn(session)
                appendEnd(session, reason)
                writer.context.persistence.flush(session)
                session
            }
            writer.context.dispose()
            val before = fixture.rows()
            val runtime = fixture.open()
            val counts = observe(runtime)
            val provider = RecordingPersistence(runtime.context.persistence)
            withProvider(runtime, provider) { evidence ->
                sessions.forEachIndexed { index, session ->
                    val result = assertIs<ScheduledEvidenceResult.Terminal>(evidence.inspect(scope, run(session.id)))
                    assertEquals(if (index == 0) ScheduledRunPhase.Succeeded else ScheduledRunPhase.Interrupted, result.evidence.outcome)
                    assertEquals(if (index == 0) "completed" else "interrupted", result.evidence.reason)
                    assertEquals(1L, result.evidence.turn)
                    assertEquals(2L, result.evidence.userMessageSeq)
                    assertEquals(4L, result.evidence.turnEndSeq)
                    assertEquals(42L, result.evidence.endedAt)
                    assertEquals(ScheduledEvidenceResult.Pending, evidence.inspect(scope, run(session.id).copy(inputMessageId = "wrong-input")))
                }
            }
            assertEquals(sessions.flatMap { session -> List(2) { "read:${session.id.value}" } }, provider.calls)
            assertEquals(emptyList(), runtime.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
            assertEquals(before, fixture.rows())
        }
    }

    @Test
    fun unsupportedReadCapabilityIsUnavailableAndNeverFallsBackToLoad() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val before = fixture.rows()
            val provider = RecordingPersistence(runtime.context.persistence).apply { readAction = { null } }
            withProvider(runtime, provider) { evidence ->
                val result = assertIs<ScheduledEvidenceResult.Unavailable>(evidence.inspect(scope, run(SessionId("unsupported"))))
                assertTrue(result.reason.contains("只读"))
                assertTrue(result.reason.contains("Unknown"))
            }
            assertEquals(listOf("read:unsupported"), provider.calls)
            assertEquals(emptyList(), runtime.context.sessions.list())
            assertEquals(before, fixture.rows())
        }
    }

    @Test
    fun liveOpenOrWrongInputHasNoFlushAndNoDurableRead() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("live-pending"))
            appendOpenTurn(session)
            runtime.context.persistence.flush(session)
            val before = fixture.rows()
            val provider = RecordingPersistence(runtime.context.persistence)
            withProvider(runtime, provider) { evidence ->
                assertEquals(ScheduledEvidenceResult.Pending, evidence.inspect(scope, run(session.id)))
                appendEnd(session, CompletedTurnEndReason)
                val counts = observe(runtime)
                assertEquals(ScheduledEvidenceResult.Pending, evidence.inspect(scope, run(session.id).copy(inputMessageId = "wrong-input")))
                assertEquals(listOf(0, 0, 0, 0), counts.toList())
            }
            assertEquals(emptyList(), provider.calls)
            assertEquals(before, fixture.rows())
            assertEquals(listOf(session), runtime.context.sessions.list())
        }
    }

    @Test
    fun liveExactTerminalIsReallyFlushedBeforeDurableEvidenceIsRead() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("live-terminal"))
            appendOpenTurn(session)
            val durable = runtime.context.persistence
            durable.flush(session)
            val oldRows = fixture.rows()
            appendEnd(session, CompletedTurnEndReason)
            assertFalse(oldRows.second.single().any { it.type == SessionEventNames.TURN_END })
            val counts = observe(runtime)
            val provider = RecordingPersistence(durable)
            provider.readAction = { id ->
                assertEquals(listOf("flush:${id.value}", "read:${id.value}"), provider.calls)
                assertEquals(SessionEventNames.TURN_END, fixture.rows().second.single().last().type)
                durable.readEvents(id)
            }
            withProvider(runtime, provider) { evidence ->
                val result = assertIs<ScheduledEvidenceResult.Terminal>(evidence.inspect(scope, run(session.id)))
                assertEquals(DesktopScheduledSessionEvidence.terminalEvidence(assertNotNull(durable.readEvents(session.id)), INPUT), result.evidence)
                assertEquals(ScheduledRunPhase.Succeeded, result.evidence.outcome)
            }
            assertEquals(listOf("flush:live-terminal", "read:live-terminal"), provider.calls)
            assertEquals(session.events(), durable.readEvents(session.id))
            assertEquals(oldRows.second.single().size + 2, fixture.rows().second.single().size)
            assertEquals(listOf(session), runtime.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun inMemoryTerminalCannotReplaceMissingDurableTerminalAfterFlushReturns() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("uncommitted-terminal"))
            appendOpenTurn(session)
            runtime.context.persistence.flush(session)
            val before = fixture.rows()
            appendEnd(session, CompletedTurnEndReason)
            assertNotNull(DesktopScheduledSessionEvidence.terminalEvidence(session.events(), INPUT))
            // Fault seam: this provider returns from flush without persisting the new tail.
            // readEvents still reads the genuine older Room rows, not a fabricated snapshot.
            val provider = RecordingPersistence(runtime.context.persistence).apply { flushAction = { } }
            val counts = observe(runtime)
            withProvider(runtime, provider) { evidence ->
                assertEquals(ScheduledEvidenceResult.Pending, evidence.inspect(scope, run(session.id)))
            }
            assertEquals(listOf("flush:uncommitted-terminal", "read:uncommitted-terminal"), provider.calls)
            assertEquals(before, fixture.rows())
            assertEquals(listOf(session), runtime.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun flushAndHistoricalOrLiveReadFailuresAreUnavailableWithoutLoadFallback() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("failure-live"))
            appendOpenTurn(session)
            appendEnd(session, CompletedTurnEndReason)
            runtime.context.persistence.flush(session)
            val before = fixture.rows()
            val counts = observe(runtime)
            for (failAt in listOf("flush", "read")) {
                val provider = RecordingPersistence(runtime.context.persistence)
                if (failAt == "flush") provider.flushAction = { throw IllegalStateException("flush failed") }
                else provider.readAction = { throw IllegalStateException("read failed") }
                withProvider(runtime, provider) { evidence ->
                    val result = assertIs<ScheduledEvidenceResult.Unavailable>(evidence.inspect(scope, run(session.id)))
                    assertTrue(result.reason.contains("Unknown"))
                }
                assertEquals(if (failAt == "flush") listOf("flush:failure-live") else
                    listOf("flush:failure-live", "read:failure-live"), provider.calls)
                assertEquals(before, fixture.rows())
            }
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
            runtime.context.dispose()
            val restarted = fixture.open()
            val restartedCounts = observe(restarted)
            val provider = RecordingPersistence(restarted.context.persistence).apply {
                readAction = { throw IllegalStateException("historical read failed") }
            }
            withProvider(restarted, provider) { evidence ->
                assertIs<ScheduledEvidenceResult.Unavailable>(evidence.inspect(scope, run(session.id)))
            }
            assertEquals(listOf("read:failure-live"), provider.calls)
            assertEquals(emptyList(), restarted.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), restartedCounts.toList())
            assertEquals(before, fixture.rows())
        }
    }

    @Test
    fun flushAndReadCancellationArePropagatedInsteadOfUnavailable() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("cancel-propagation"))
            appendOpenTurn(session)
            appendEnd(session, CompletedTurnEndReason)
            runtime.context.persistence.flush(session)
            val before = fixture.rows()
            for (cancelAt in listOf("flush", "read")) {
                val cancelled = CancellationException("cancel $cancelAt")
                val provider = RecordingPersistence(runtime.context.persistence)
                if (cancelAt == "flush") provider.flushAction = { throw cancelled }
                else provider.readAction = { throw cancelled }
                withProvider(runtime, provider) { evidence ->
                    assertSame(cancelled, assertFailsWith<CancellationException> { evidence.inspect(scope, run(session.id)) })
                }
                assertEquals(if (cancelAt == "flush") listOf("flush:cancel-propagation") else
                    listOf("flush:cancel-propagation", "read:cancel-propagation"), provider.calls)
                assertEquals(before, fixture.rows())
            }
        }
    }

    @Test
    fun cancellingSuspendedHistoricalReadCannotReturnAResultOrLoadHistory() = runBlocking {
        withFixture { fixture ->
            val writer = fixture.open()
            val session = writer.context.createSession(SessionId("cancel-suspended"))
            appendOpenTurn(session)
            writer.context.persistence.flush(session)
            writer.context.dispose()
            val before = fixture.rows()
            val runtime = fixture.open()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val provider = RecordingPersistence(runtime.context.persistence).apply {
                readAction = { id -> entered.complete(Unit); release.await(); durable.readEvents(id) }
            }
            val counts = observe(runtime)
            withProvider(runtime, provider) { evidence ->
                coroutineScope {
                    var returned = false
                    val inspecting = async(start = CoroutineStart.UNDISPATCHED) {
                        evidence.inspect(scope, run(session.id)).also { returned = true }
                    }
                    try {
                        withTimeout(10_000) { entered.await() }
                        assertFalse(inspecting.isCompleted)
                        inspecting.cancel(CancellationException("cancel evidence read"))
                        assertFailsWith<CancellationException> { inspecting.await() }
                        assertFalse(returned)
                        assertTrue(inspecting.isCancelled)
                    } finally {
                        release.complete(Unit)
                        inspecting.cancel()
                        inspecting.join()
                    }
                }
            }
            assertEquals(listOf("read:cancel-suspended"), provider.calls)
            assertEquals(emptyList(), runtime.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
            assertEquals(before, fixture.rows())
        }
    }

    @Test
    fun cancellationAfterReadAndBeforePendingInspectionCannotReturnSuccess() = runBlocking {
        withFixture { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("cancel-return"))
            appendOpenTurn(session)
            runtime.context.persistence.flush(session)
            val before = fixture.rows()
            val provider = RecordingPersistence(runtime.context.persistence)
            withProvider(runtime, provider) { evidence ->
                coroutineScope {
                    var returned = false
                    val inspecting = async {
                        currentCoroutineContext().cancel(CancellationException("cancel pending inspection"))
                        evidence.inspect(scope, run(session.id)).also { returned = true }
                    }
                    assertFailsWith<CancellationException> { inspecting.await() }
                    assertFalse(returned)
                }
            }
            assertEquals(emptyList(), provider.calls)
            appendEnd(session, CompletedTurnEndReason)
            provider.readAction = { id ->
                val events = provider.durable.readEvents(id)
                currentCoroutineContext().cancel(CancellationException("cancel after evidence read"))
                events
            }
            withProvider(runtime, provider) { evidence ->
                coroutineScope {
                    var returned = false
                    val inspecting = async { evidence.inspect(scope, run(session.id)).also { returned = true } }
                    assertFailsWith<CancellationException> { inspecting.await() }
                    assertFalse(returned)
                    assertTrue(inspecting.isCancelled)
                }
            }
            assertEquals(listOf("flush:cancel-return", "read:cancel-return"), provider.calls)
            assertEquals(before.second.single().size + 2, fixture.rows().second.single().size)
            assertEquals(listOf(session), runtime.context.sessions.list())
        }
    }

    private fun run(id: SessionId): ScheduledRun {
        val task = ScheduledTask("task", 1L, "read-only evidence", "prompt",
            ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Hourly(1, 0L),
            ScheduledModelRef("scripted", "test"))
        return ScheduledRun("run-${id.value}", task, "request-${id.value}", null, 0L,
            ClientSessionId(id.value), INPUT, phase = ScheduledRunPhase.Accepted,
            sessionRef = ClientSessionId(id.value), receiptId = "inbox-receipt", deliveryIntent = true)
    }

    private suspend fun appendOpenTurn(session: Session) {
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
        session.append(SessionEventKeys.StepStart, StepStartEvent(1L, 1L))
        session.append(SessionEventKeys.UserMessage, UserMessageEvent(1L,
            createUserMessage(listOf(TextBlock("prompt"))).copy(id = MessageId(INPUT))))
    }

    private suspend fun appendEnd(session: Session, reason: TurnEndReason) {
        session.append(SessionEventKeys.StepEnd, StepEndEvent(1L, 1L))
        session.append(SessionEventKeys.TurnEnd, TurnEndEvent(1L, reason))
    }

    private fun observe(runtime: Runtime): IntArray {
        val counts = IntArray(4)
        runtime.context.on(SessionEvents.Created) { counts[0] += 1 }
        runtime.context.on(SessionEvents.Appended) { counts[1] += 1 }
        runtime.context.on(SessionEvents.Flush) { counts[2] += 1 }
        runtime.context.on(SessionEvents.Disposed) { counts[3] += 1 }
        return counts
    }

    private suspend fun <T> withProvider(
        runtime: Runtime, provider: SessionPersistence, block: suspend (DesktopScheduledSessionEvidence) -> T,
    ): T {
        // isolate supplies a new persistence slot while inheriting the real live SessionStore.
        val context = runtime.context.isolate(SessionPersistenceKey)
        try {
            context.provide(SessionPersistenceKey, provider)
            return block(DesktopScheduledSessionEvidence(context, host))
        } finally { withContext(NonCancellable) { context.dispose() } }
    }

    private class RecordingPersistence(val durable: SessionPersistence) : SessionPersistence by durable {
        val calls = mutableListOf<String>()
        var flushAction: suspend (Session) -> Unit = { durable.flush(it) }
        var readAction: suspend (SessionId) -> List<SessionEventEnvelope>? = { durable.readEvents(it) }
        override suspend fun flush(session: Session) { calls += "flush:${session.id.value}"; flushAction(session) }
        override suspend fun readEvents(id: SessionId): List<SessionEventEnvelope>? {
            calls += "read:${id.value}"
            return readAction(id)
        }
        override suspend fun load(id: SessionId): Session {
            calls += "load:${id.value}"
            error("Evidence must never restore a live session")
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture(Files.createTempDirectory("harness-readonly-evidence-"))
        try { block(fixture) } finally { withContext(NonCancellable) { fixture.close() } }
    }

    private class Fixture(private val root: Path) {
        private val path = root.resolve("sessions.db")
        private val runtimes = mutableListOf<Runtime>()
        private var activeDatabase: SessionDatabaseAccess? = null
        suspend fun open(): Runtime {
            val runtime = Runtime().also { runtimes.add(it) }
            runtime.install(SessionPlugin(clock = SessionClock { 42L }))
            runtime.install(SessionPersistencePlugin(SessionPersistenceDatabaseFactory {
                val database = jvmSessionPersistenceDatabaseFactory(path.toFile()).create()
                activeDatabase = database
                object : SessionDatabaseAccess by database {
                    override suspend fun dispose() {
                        try { database.dispose() } finally {
                            if (activeDatabase === database) activeDatabase = null
                        }
                    }
                }
            }))
            return runtime
        }
        suspend fun rows(): Pair<List<SessionRow>, List<List<SessionEventRow>>> {
            // Reuse the Runtime owner while it is alive; registry ownership stays real.
            activeDatabase?.let { return readRows(it) }
            val database = jvmSessionPersistenceDatabaseFactory(path.toFile()).create()
            try { return readRows(database) } finally { database.dispose() }
        }
        private suspend fun readRows(database: SessionDatabaseAccess): Pair<List<SessionRow>, List<List<SessionEventRow>>> {
            val headers = database.dao.listSessions()
            return headers to headers.map { database.dao.findEvents(it.sessionId) }
        }
        suspend fun close() {
            try { runtimes.asReversed().forEach { it.context.dispose() } }
            finally { root.toFile().deleteRecursively() }
        }
    }

    private companion object { const val INPUT = "scheduled-input" }
}

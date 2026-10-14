package im.hikaru.harness.session.persistence

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.InboxSnapshot
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionPersistenceReadOnlyEventsTest {
    @Test
    fun repeatedOpenTurnReadsDoNotRepairRowsLoadSessionsOrPublishLifecycle() = runTest {
        withFixture { fixture ->
            val writer = fixture.open()
            val session = writer.context.createSession(SessionId("open-turn"))
            appendOpenTurn(session)
            val expected = session.events()
            writer.context.persistence.flush(session)
            writer.context.dispose()
            val headers = fixture.dao { it.listSessions() }
            val rows = fixture.dao { it.findEvents(session.id.value) }
            val reader = fixture.open()
            val counts = observe(reader)
            repeat(4) {
                val events = assertNotNull(reader.context.persistence.readEvents(session.id))
                assertEquals(expected, events)
                assertFalse(events.any { it.type == SessionEventNames.TURN_END })
            }
            assertEquals(emptyList(), reader.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
            assertEquals(headers, fixture.dao { it.listSessions() })
            assertEquals(rows, fixture.dao { it.findEvents(session.id.value) })
        }
    }

    @Test
    fun durableCompletedInterruptedAndIgnorableEventsRemainReadableSnapshots() = runTest {
        withFixture { fixture ->
            val writer = fixture.open()
            val expected = listOf(CompletedTurnEndReason, InterruptedTurnEndReason).mapIndexed { index, reason ->
                val session = writer.context.createSession(SessionId("terminal-$index"))
                appendOpenTurn(session)
                session.append(SessionEventKeys.StepEnd, StepEndEvent(1L, 1L))
                session.append(SessionEventKeys.TurnEnd, TurnEndEvent(1L, reason))
                writer.context.persistence.flush(session)
                session.id to session.events()
            }
            writer.context.dispose()
            val id = expected.first().first
            val unknown = SessionEventRow(id.value, expected.first().second.size.toLong(),
                "future/observation", 42L, """{"value":true}""", null, null, true)
            fixture.dao { it.upsertEvents(listOf(unknown)) }
            val before = fixture.dao { dao -> expected.map { dao.findEvents(it.first.value) } }
            val reader = fixture.open()
            val counts = observe(reader)
            val first = assertNotNull(reader.context.persistence.readEvents(id))
            assertEquals(expected.first().second, first.dropLast(1))
            assertEquals(unknown.type, first.last().type)
            assertEquals(true, first.last().ignorable)
            assertEquals(unknown.seq, first.last().seq)
            assertEquals(expected.last().second, reader.context.persistence.readEvents(expected.last().first))
            @Suppress("UNCHECKED_CAST")
            (first as MutableList<SessionEventEnvelope>).clear()
            assertEquals(expected.first().second.size + 1, assertNotNull(reader.context.persistence.readEvents(id)).size)
            assertEquals(before, fixture.dao { dao -> expected.map { dao.findEvents(it.first.value) } })
            assertEquals(emptyList(), reader.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun defaultCapabilityMissingSessionEmptyLogAndDisposedAreDistinct() = runTest {
        val legacy = object : SessionPersistence {
            override suspend fun flush(session: Session) = error("Not used")
            override suspend fun load(id: SessionId): Session = error("Not used")
            override suspend fun inbox(id: SessionId, agentId: AgentId): InboxSnapshot = error("Not used")
            override suspend fun dispose() = Unit
        }
        assertNull(legacy.readEvents(SessionId("legacy")))
        withFixture { fixture ->
            val runtime = fixture.open()
            val provider = runtime.context.persistence
            val missing = assertFailsWith<SessionException> { provider.readEvents(SessionId("missing")) }
            assertEquals(SessionErrorCode.NOT_LIVE, missing.code)
            assertEquals(emptyList(), runtime.context.sessions.list())
            assertEquals(emptyList(), fixture.dao { it.listSessions() })
            val empty = runtime.context.createSession(SessionId("empty-log"))
            provider.flush(empty)
            assertEquals(emptyList(), provider.readEvents(empty.id))
            runtime.context.dispose()
            val failure = assertFailsWith<IllegalStateException> { provider.readEvents(empty.id) }
            assertEquals("Session persistence is disposed", failure.message)
        }
    }

    @Test
    fun malformedNoncontiguousAndIllegalEnvelopesFailWithoutChangingRoom() = runTest {
        withFixture { fixture ->
            val writer = fixture.open()
            val session = writer.context.createSession(SessionId("bad-events"))
            session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
            writer.context.persistence.flush(session)
            writer.context.dispose()
            val valid = fixture.dao { it.findEvents(session.id.value).single() }
            val invalid = listOf(
                valid.copy(data = "{broken"), valid.copy(seq = 1L), valid.copy(time = -1L),
                valid.copy(surfaceOp = "APPEND"), valid.copy(surfaceOp = "INVALID"),
                valid.copy(sourceEventSeqs = "[0]"), valid.copy(ignorable = true),
                valid.copy(type = "future/required", data = "{}", ignorable = null),
            )
            val reader = fixture.open()
            val counts = observe(reader)
            val headers = fixture.dao { it.listSessions() }
            for (row in invalid) {
                fixture.dao { dao ->
                    dao.deleteEventsFrom(session.id.value, 0L)
                    dao.upsertEvents(listOf(row))
                }
                val failure = assertFailsWith<SessionException> { reader.context.persistence.readEvents(session.id) }
                assertEquals(SessionErrorCode.INVALID_EVENT, failure.code)
                assertEquals(listOf(row), fixture.dao { it.findEvents(session.id.value) })
                assertEquals(headers, fixture.dao { it.listSessions() })
                assertEquals(emptyList(), reader.context.sessions.list())
                assertEquals(listOf(0, 0, 0, 0), counts.toList())
            }
            fixture.dao { dao ->
                dao.deleteEventsFrom(session.id.value, 0L)
                dao.upsertEvents(listOf(valid))
            }
            assertEquals(session.events(), reader.context.persistence.readEvents(session.id))
        }
    }

    @Test
    fun mismatchedHeaderIdIsRejectedWithoutLoadingOrWriting() = runTest {
        withFixture { fixture ->
            val writer = fixture.open()
            val session = writer.context.createSession(SessionId("requested-id"))
            writer.context.persistence.flush(session)
            writer.context.dispose()
            val before = fixture.dao { it.listSessions() }
            val reader = fixture.open(decorate = { dao ->
                object : SessionPersistenceDao by dao {
                    override suspend fun findSession(sessionId: String): SessionRow? =
                        dao.findSession(sessionId)?.copy(sessionId = "another-valid-id")
                }
            })
            val counts = observe(reader)
            val failure = assertFailsWith<SessionException> { reader.context.persistence.readEvents(session.id) }
            assertEquals(SessionErrorCode.INVALID_HEADER, failure.code)
            assertEquals(before, fixture.dao { it.listSessions() })
            assertEquals(emptyList(), fixture.dao { it.findEvents(session.id.value) })
            assertEquals(emptyList(), reader.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun daoCancellationPropagatesAndOtherReadFailuresKeepTheirCause() = runTest {
        withFixture { fixture ->
            var failure: Throwable? = null
            val runtime = fixture.open(decorate = { dao ->
                object : SessionPersistenceDao by dao {
                    override suspend fun findEvents(sessionId: String): List<SessionEventRow> {
                        failure?.let { throw it }
                        return dao.findEvents(sessionId)
                    }
                }
            })
            val session = runtime.context.createSession(SessionId("dao-failures"))
            appendOpenTurn(session)
            runtime.context.persistence.flush(session)
            val before = fixture.dao { it.findEvents(session.id.value) }
            val liveBefore = runtime.context.sessions.list()
            val counts = observe(runtime)
            val cancelled = CancellationException("DAO cancelled")
            failure = cancelled
            assertSame(cancelled, assertFailsWith<CancellationException> { runtime.context.persistence.readEvents(session.id) })
            val broken = IllegalStateException("DAO read failed")
            failure = broken
            val wrapped = assertFailsWith<SessionException> { runtime.context.persistence.readEvents(session.id) }
            assertEquals(SessionErrorCode.INVALID_EVENT, wrapped.code)
            assertSame(broken, wrapped.cause)
            failure = null
            assertEquals(session.events(), runtime.context.persistence.readEvents(session.id))
            assertEquals(before, fixture.dao { it.findEvents(session.id.value) })
            assertEquals(liveBefore, runtime.context.sessions.list())
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun cancellationWhileWaitingForDaoDoesNotPublishASnapshot() = runTest {
        withFixture { fixture ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var gated = true
            val runtime = fixture.open(decorate = { dao ->
                object : SessionPersistenceDao by dao {
                    override suspend fun findEvents(sessionId: String): List<SessionEventRow> {
                        if (gated) { entered.complete(Unit); release.await() }
                        return dao.findEvents(sessionId)
                    }
                }
            })
            val session = runtime.context.createSession(SessionId("waiting-dao"))
            appendOpenTurn(session)
            runtime.context.persistence.flush(session)
            val before = fixture.dao { it.findEvents(session.id.value) }
            val counts = observe(runtime)
            coroutineScope {
                var returned = false
                val reading = async(start = CoroutineStart.UNDISPATCHED) {
                    runtime.context.persistence.readEvents(session.id).also { returned = true }
                }
                try {
                    entered.await()
                    assertFalse(reading.isCompleted)
                    reading.cancel(CancellationException("cancel waiting DAO"))
                    assertFailsWith<CancellationException> { reading.await() }
                    assertFalse(returned)
                } finally {
                    release.complete(Unit)
                    reading.cancel()
                    reading.join()
                }
            }
            gated = false
            assertEquals(session.events(), runtime.context.persistence.readEvents(session.id))
            assertEquals(before, fixture.dao { it.findEvents(session.id.value) })
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun cancelledReadWaitingForRealFlushCannotEscapeItsMutexOrCommit() = runTest {
        withFixture { fixture ->
            val written = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val runtime = fixture.open(beforeCommit = { written.complete(Unit); release.await() })
            val session = runtime.context.createSession(SessionId("waiting-flush"))
            appendOpenTurn(session)
            val counts = observe(runtime)
            coroutineScope {
                val flushing = launch { runtime.context.persistence.flush(session) }
                try {
                    written.await()
                    var returned = false
                    val reading = async(start = CoroutineStart.UNDISPATCHED) {
                        runtime.context.persistence.readEvents(session.id).also { returned = true }
                    }
                    assertFalse(reading.isCompleted, "Read must wait for the real uncommitted flush")
                    reading.cancel(CancellationException("cancel mutex wait"))
                    assertFailsWith<CancellationException> { reading.await() }
                    assertFalse(returned)
                    assertTrue(reading.isCancelled)
                } finally {
                    release.complete(Unit)
                    flushing.join()
                }
            }
            assertEquals(session.events(), runtime.context.persistence.readEvents(session.id))
            assertEquals(session.events().size, fixture.dao { it.findEvents(session.id.value).size })
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    @Test
    fun cancellationAfterDaoReturnsCannotPublishValidatedEvents() = runTest {
        withFixture { fixture ->
            var cancelAfterRead = true
            val runtime = fixture.open(decorate = { dao ->
                object : SessionPersistenceDao by dao {
                    override suspend fun findEvents(sessionId: String): List<SessionEventRow> {
                        val rows = dao.findEvents(sessionId)
                        if (cancelAfterRead) currentCoroutineContext().cancel(CancellationException("cancel after read"))
                        return rows
                    }
                }
            })
            val session = runtime.context.createSession(SessionId("after-dao"))
            appendOpenTurn(session)
            runtime.context.persistence.flush(session)
            val before = fixture.dao { it.findEvents(session.id.value) }
            val counts = observe(runtime)
            coroutineScope {
                var returned = false
                val reading = async { runtime.context.persistence.readEvents(session.id).also { returned = true } }
                assertFailsWith<CancellationException> { reading.await() }
                assertFalse(returned)
                assertTrue(reading.isCancelled)
            }
            cancelAfterRead = false
            assertEquals(session.events(), runtime.context.persistence.readEvents(session.id))
            assertEquals(before, fixture.dao { it.findEvents(session.id.value) })
            assertEquals(listOf(0, 0, 0, 0), counts.toList())
        }
    }

    private suspend fun appendOpenTurn(session: Session) {
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
        session.append(SessionEventKeys.StepStart, StepStartEvent(1L, 1L))
        session.append(SessionEventKeys.UserMessage, UserMessageEvent(1L, createUserMessage(listOf(TextBlock("prompt")))))
    }

    private fun observe(runtime: Runtime): IntArray {
        val counts = IntArray(4)
        runtime.context.on(SessionEvents.Created) { counts[0] += 1 }
        runtime.context.on(SessionEvents.Appended) { counts[1] += 1 }
        runtime.context.on(SessionEvents.Flush) { counts[2] += 1 }
        runtime.context.on(SessionEvents.Disposed) { counts[3] += 1 }
        return counts
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture(Files.createTempDirectory("harness-readonly-events-"))
        try { block(fixture) } finally { withContext(NonCancellable) { fixture.close() } }
    }

    private class Fixture(private val root: Path) {
        private val path = root.resolve("sessions.db").toString()
        private val runtimes = mutableListOf<Runtime>()

        suspend fun open(
            decorate: (SessionPersistenceDao) -> SessionPersistenceDao = { it },
            beforeCommit: (suspend () -> Unit)? = null,
        ): Runtime {
            val runtime = Runtime().also { runtimes.add(it) }
            runtime.install(SessionPlugin(clock = SessionClock { 42L }))
            runtime.install(SessionPersistencePlugin(SessionPersistenceDatabaseFactory {
                val database = buildDatabase()
                object : SessionDatabaseAccess {
                    override val dao = decorate(database.sessions())
                    override suspend fun <T> transaction(block: suspend () -> T): T =
                        database.withWriteTransaction {
                            val result = block()
                            beforeCommit?.invoke()
                            result
                        }
                    override suspend fun dispose() { database.close() }
                }
            }))
            return runtime
        }

        suspend fun <T> dao(block: suspend (SessionPersistenceDao) -> T): T {
            val database = buildDatabase()
            try { return block(database.sessions()) } finally { database.close() }
        }

        private fun buildDatabase(): SessionPersistenceDatabase =
            Room.databaseBuilder<SessionPersistenceDatabase>(path)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        suspend fun close() {
            try { runtimes.asReversed().forEach { it.context.dispose() } }
            finally { root.toFile().deleteRecursively() }
        }
    }
}

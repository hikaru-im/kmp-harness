package im.hikaru.harness.session.persistence

import androidx.room3.Room
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.InboxSnapshot
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionClock
import im.hikaru.harness.session.SessionErrorCode
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.SessionException
import im.hikaru.harness.session.SessionHeader
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionOrigin
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.StepStartEvent
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.UserMessageEvent
import im.hikaru.harness.session.createSession
import im.hikaru.harness.session.sessions
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionPersistenceHeaderCatalogTest {
    @Test
    fun legacyCapabilityIsNullWhileRoomEmptyCatalogIsAvailable() = runTest {
        val legacy = object : SessionPersistence {
            override suspend fun flush(session: Session) = error("Not used")
            override suspend fun load(id: SessionId): Session = error("Not used")
            override suspend fun inbox(id: SessionId, agentId: AgentId): InboxSnapshot = error("Not used")
            override suspend fun dispose() = Unit
        }
        assertNull(legacy.listHeaders())
        withCatalog { fixture ->
            val runtime = fixture.open()
            assertEquals(emptyList(), assertNotNull(runtime.context.persistence.listHeaders()))
            assertEquals(emptyList(), runtime.context.sessions.list())
        }
    }

    @Test
    fun onlyFlushedLiveHeadersAppearAndRestartDoesNotLoadHistory() = runTest {
        withCatalog { fixture ->
            val first = fixture.open()
            val session = first.context.createSession(SessionId("persisted"), CreateSessionOptions(cwd = "/worktree/a"))
            val unflushed = first.context.createSession(SessionId("unflushed"))
            assertEquals(emptyList(), first.context.persistence.listHeaders())
            first.context.persistence.flush(session)
            assertEquals(listOf(session.header), first.context.persistence.listHeaders())
            assertEquals(listOf(session, unflushed), first.context.sessions.list())
            first.context.dispose()

            val second = fixture.open()
            assertEquals(emptyList(), second.context.sessions.list())
            assertEquals(listOf(session.header), second.context.persistence.listHeaders())
            assertEquals(emptyList(), second.context.sessions.list())
        }
    }

    @Test
    fun headersPreserveAllFieldsAndSortByCreationTimeThenId() = runTest {
        withCatalog { fixture ->
            val runtime = fixture.open()
            val parent = runtime.context.createSession(SessionId("z-parent"), CreateSessionOptions(cwd = "/worktree/a"))
            val child = runtime.context.createSession(
                SessionId("a-child"),
                CreateSessionOptions(
                    cwd = "/worktree/b", parentSession = parent.id, seedLength = 7L,
                    origin = SessionOrigin.SUBAGENT, delegationDepth = 2L, agentPreset = "reviewer",
                ),
            )
            val noCwd = runtime.context.createSession(SessionId("m-no-cwd"))
            listOf(parent, noCwd, child).forEach { runtime.context.persistence.flush(it) }
            // Insert an earlier valid header using the same real Room tables.
            val earlier = parent.header.copy(id = SessionId("zz-earlier"), createdAt = 1L, version = 3L)
            fixture.database { it.sessions().upsertSession(earlier.row()) }
            val expected = listOf(earlier, child.header, noCwd.header, parent.header)
            assertEquals(expected, runtime.context.persistence.listHeaders())
            assertEquals(1, listOf(parent, child, noCwd).map { it.header.createdAt }.distinct().size)
            runtime.context.dispose()
            val restarted = fixture.open()
            assertEquals(expected, restarted.context.persistence.listHeaders())
            assertEquals(emptyList(), restarted.context.sessions.list())
        }
    }

    @Test
    fun callerMutationCannotChangeTheNextCatalogSnapshot() = runTest {
        withCatalog { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("snapshot"))
            runtime.context.persistence.flush(session)
            val first = assertNotNull(runtime.context.persistence.listHeaders())
            @Suppress("UNCHECKED_CAST")
            (first as MutableList<SessionHeader>).clear()
            assertEquals(emptyList(), first)
            assertEquals(listOf(session.header), runtime.context.persistence.listHeaders())
            assertEquals(session.header.row(), fixture.database { it.sessions().findSession(session.id.value) })
        }
    }

    @Test
    fun anyInvalidHeaderFailsTheWholeCatalogWithOriginalCause() = runTest {
        withCatalog { fixture ->
            val runtime = fixture.open()
            val session = runtime.context.createSession(SessionId("a-valid"))
            runtime.context.persistence.flush(session)
            val valid = session.header.copy(id = SessionId("z-invalid")).row()
            val invalidRows = listOf(
                valid.copy(version = -1L), valid.copy(createdAt = -1L), valid.copy(cwd = "relative/path"),
                valid.copy(seedLength = -1L), valid.copy(delegationDepth = -1L),
                valid.copy(agentPreset = " "), valid.copy(origin = "unknown-origin"),
                valid.copy(parentSession = " "),
            )
            for (invalid in invalidRows) {
                fixture.database { it.sessions().upsertSession(invalid) }
                val failure = assertFailsWith<SessionException> { runtime.context.persistence.listHeaders() }
                assertEquals(SessionErrorCode.INVALID_HEADER, failure.code)
                assertIs<IllegalArgumentException>(failure.cause)
                assertEquals(listOf(session), runtime.context.sessions.list())
                assertEquals(invalid, fixture.database { it.sessions().findSession(valid.sessionId) })
            }
            fixture.database { it.sessions().upsertSession(valid) }
            assertEquals(listOf(session.header, session.header.copy(id = SessionId(valid.sessionId))), runtime.context.persistence.listHeaders())
        }
    }

    @Test
    fun catalogDoesNotParseRepairOrChangeInterruptedAndMalformedEvents() = runTest {
        withCatalog { fixture ->
            val first = fixture.open()
            val interrupted = first.context.createSession(SessionId("interrupted"))
            interrupted.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
            interrupted.append(SessionEventKeys.StepStart, StepStartEvent(1L, 1L))
            val malformed = first.context.createSession(SessionId("malformed"))
            first.context.sessions.flush()
            first.context.dispose()
            val invalidEvent = SessionEventRow(
                malformed.id.value, 0L, "user/message", 1L, " { definitely-not-json \n",
                "APPEND", "not-json-either", null,
            )
            fixture.database { it.sessions().upsertEvents(listOf(invalidEvent)) }
            val ids = listOf(interrupted.id, malformed.id)
            val beforeEvents = fixture.database { db -> ids.map { db.sessions().findEvents(it.value) } }
            val beforeHeaders = fixture.database { it.sessions().listSessions() }
            assertEquals(2, beforeEvents.first().size)
            assertEquals(listOf(invalidEvent), beforeEvents.last())
            val runtime = fixture.open()
            val counts = observe(runtime)
            val live = runtime.context.sessions.list()
            val beforeCounts = counts.toList()
            repeat(3) {
                assertEquals(listOf(interrupted.header, malformed.header), runtime.context.persistence.listHeaders())
            }
            assertEquals(live, runtime.context.sessions.list())
            assertEquals(emptyList(), live)
            assertEquals(beforeCounts, counts.toList())
            assertEquals(beforeHeaders, fixture.database { it.sessions().listSessions() })
            assertEquals(beforeEvents, fixture.database { db -> ids.map { db.sessions().findEvents(it.value) } })
        }
    }

    @Test
    fun readingCatalogDoesNotPublishLifecycleEventsOrChangeLiveSessions() = runTest {
        withCatalog { fixture ->
            val runtime = fixture.open()
            val counts = observe(runtime)
            val persisted = runtime.context.createSession(SessionId("persisted-live"))
            persisted.append(SessionEventKeys.UserMessage, UserMessageEvent(1L, createUserMessage(listOf(TextBlock("hello")))))
            runtime.context.sessions.flush()
            runtime.context.createSession(SessionId("live-only"))
            assertEquals(listOf(2, 1, 1, 0), counts.toList())
            val beforeCounts = counts.toList()
            val beforeLive = runtime.context.sessions.list()
            val beforeEvents = fixture.database { it.sessions().findEvents(persisted.id.value) }
            val beforeHeaders = fixture.database { it.sessions().listSessions() }
            repeat(4) { assertEquals(listOf(persisted.header), runtime.context.persistence.listHeaders()) }
            assertEquals(beforeCounts, counts.toList())
            assertEquals(beforeLive, runtime.context.sessions.list())
            assertEquals(beforeHeaders, fixture.database { it.sessions().listSessions() })
            assertEquals(beforeEvents, fixture.database { it.sessions().findEvents(persisted.id.value) })
        }
    }

    @Test
    fun disposedProviderRejectsCatalogReads() = runTest {
        withCatalog { fixture ->
            val runtime = fixture.open()
            val provider = runtime.context.persistence
            assertEquals(emptyList(), provider.listHeaders())
            runtime.context.dispose()
            val failure = assertFailsWith<IllegalStateException> { provider.listHeaders() }
            assertEquals("Session persistence is disposed", failure.message)
        }
    }

    @Test
    fun cancelledCatalogWaitingForRealFlushStaysCancelledAndNextReadWorks() = runTest {
        withCatalog { fixture ->
            val written = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val runtime = fixture.openGated(written, release)
            val session = runtime.context.createSession(SessionId("cancelled-read"))
            coroutineScope {
                val flushing = launch { runtime.context.persistence.flush(session) }
                try {
                    written.await()
                    val reading = async(start = CoroutineStart.UNDISPATCHED) { runtime.context.persistence.listHeaders() }
                    assertFalse(reading.isCompleted, "Catalog must wait for the active real flush")
                    reading.cancel(CancellationException("cancel catalog"))
                    assertFailsWith<CancellationException> { reading.await() }
                    assertTrue(reading.isCancelled)
                } finally {
                    release.complete(Unit)
                    flushing.join()
                }
            }
            assertEquals(listOf(session.header), runtime.context.persistence.listHeaders())
            assertEquals(session.header.row(), fixture.database { it.sessions().findSession(session.id.value) })
        }
    }

    @Test
    fun concurrentCatalogAndRealFlushObserveOnlyCommittedCompleteHeaders() = runTest {
        withCatalog { fixture ->
            val written = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val runtime = fixture.openGated(written, release)
            val session = runtime.context.createSession(
                SessionId("concurrent"),
                CreateSessionOptions(cwd = "/worktree/concurrent", parentSession = SessionId("parent"),
                    seedLength = 3L, origin = SessionOrigin.SUBAGENT, delegationDepth = 1L, agentPreset = "worker"),
            )
            session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
            assertEquals(emptyList(), runtime.context.persistence.listHeaders())
            coroutineScope {
                val flushing = launch { runtime.context.persistence.flush(session) }
                try {
                    written.await()
                    val reading = async(start = CoroutineStart.UNDISPATCHED) { runtime.context.persistence.listHeaders() }
                    assertFalse(reading.isCompleted, "Header cannot escape an uncommitted provider flush")
                    release.complete(Unit)
                    assertEquals(listOf(session.header), reading.await())
                } finally {
                    release.complete(Unit)
                    flushing.join()
                }
            }
            val beforeHeader = fixture.database { it.sessions().findSession(session.id.value) }
            val beforeEvents = fixture.database { it.sessions().findEvents(session.id.value) }
            assertEquals(session.header.row(), beforeHeader)
            assertEquals(1, beforeEvents.size)
            coroutineScope {
                val writer = launch { repeat(8) { runtime.context.persistence.flush(session) } }
                val reader = launch { repeat(8) { assertEquals(listOf(session.header), runtime.context.persistence.listHeaders()) } }
                writer.join()
                reader.join()
            }
            assertEquals(beforeHeader, fixture.database { it.sessions().findSession(session.id.value) })
            assertEquals(beforeEvents, fixture.database { it.sessions().findEvents(session.id.value) })
            assertEquals(listOf(session), runtime.context.sessions.list())
        }
    }

    private fun observe(runtime: Runtime): IntArray {
        val counts = IntArray(4)
        runtime.context.on(SessionEvents.Created) { counts[0] += 1 }
        runtime.context.on(SessionEvents.Appended) { counts[1] += 1 }
        runtime.context.on(SessionEvents.Flush) { counts[2] += 1 }
        runtime.context.on(SessionEvents.Disposed) { counts[3] += 1 }
        return counts
    }

    private suspend fun withCatalog(block: suspend (CatalogFixture) -> Unit) {
        val fixture = CatalogFixture(Files.createTempDirectory("harness-header-catalog-"))
        try {
            block(fixture)
        } finally {
            withContext(NonCancellable) { fixture.close() }
        }
    }

    private class CatalogFixture(private val root: Path) {
        private val path = root.resolve("harness-sessions.db").toString()
        private val runtimes = mutableListOf<Runtime>()

        suspend fun open(): Runtime {
            val runtime = Runtime().also { runtimes.add(it) }
            runtime.install(SessionPlugin(clock = SessionClock { 42L }))
            runtime.install(sessionPersistencePluginForTest(path))
            return runtime
        }

        suspend fun openGated(written: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>): Runtime {
            val runtime = Runtime().also { runtimes.add(it) }
            runtime.install(SessionPlugin(clock = SessionClock { 42L }))
            // Real Room DAO and transaction; gate after writes but before commit, with no sleeps or fake results.
            runtime.install(SessionPersistencePlugin(SessionPersistenceDatabaseFactory {
                val database = buildDatabase()
                object : SessionDatabaseAccess {
                    override val dao = database.sessions()
                    override suspend fun <T> transaction(block: suspend () -> T): T =
                        database.withWriteTransaction {
                            val result = block()
                            written.complete(Unit)
                            release.await()
                            result
                        }
                    override suspend fun dispose() { database.close() }
                }
            }))
            return runtime
        }

        suspend fun <T> database(block: suspend (SessionPersistenceDatabase) -> T): T {
            val database = buildDatabase()
            try {
                return block(database)
            } finally {
                database.close()
            }
        }

        private fun buildDatabase(): SessionPersistenceDatabase =
            Room.databaseBuilder<SessionPersistenceDatabase>(path)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        suspend fun close() {
            try {
                runtimes.asReversed().forEach { it.context.dispose() }
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }

    private fun SessionHeader.row() = SessionRow(
        id.value, version, createdAt, cwd, parentSession?.value, seedLength, origin?.name, delegationDepth, agentPreset,
    )
}

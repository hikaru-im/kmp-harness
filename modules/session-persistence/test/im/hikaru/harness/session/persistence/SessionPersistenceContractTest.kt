package im.hikaru.harness.session.persistence

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.NormalRetryPolicy
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.retry.LlmRetryClock
import im.hikaru.harness.llm.retry.LlmRetryPlugin
import im.hikaru.harness.llm.retry.LlmRetrySessionEvents
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionEventKeys
import im.hikaru.harness.session.SessionEventKey
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.StepStartEvent
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.UserMessageEvent
import im.hikaru.harness.session.createSession
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.SessionException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionPersistenceContractTest {
    @Test
    fun roomRegistryRejectsDuplicateLocationAndReopensAfterClose() = runTest {
        val registry = SessionDatabaseRegistry<String>()
        var creations = 0
        val firstOwner = Any()
        val secondOwner = Any()
        fun open(key: String, owner: Any = firstOwner): SessionDatabaseAccess =
            registry.acquire(key, owner) {
                creations += 1
                FakeSessionDatabaseAccess()
        }

        val first = open("harness-sessions.db")
        assertEquals(1, creations)
        assertFailsWith<IllegalStateException> {
            open("harness-sessions.db")
        }
        assertFailsWith<IllegalStateException> {
            open("harness-sessions.db", secondOwner)
        }

        assertEquals(true, registry.beginClose("harness-sessions.db", first))
        registry.finishClose("harness-sessions.db", first)
        val reopened = open("harness-sessions.db", secondOwner)
        assertEquals(2, creations)
        check(reopened !== first) { "A closed access must not be reused" }

        val different = open("other.db")
        check(different !== reopened) { "Different locations must remain isolated" }
        assertEquals(3, creations)
    }

    private class FakeSessionDatabaseAccess : SessionDatabaseAccess {
        override val dao: SessionPersistenceDao
            get() = error("DAO is not used by the registry contract")

        override suspend fun <T> transaction(block: suspend () -> T): T = block()

        override suspend fun dispose() = Unit
    }

    @Test
    fun repeatedAndConcurrentFlushesRemainOneContiguousSnapshot() = runTest {
        val root = Files.createTempDirectory("harness-session-idempotent-")
        val id = SessionId("idempotent")
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val session = runtime.context.createSession(id)
        session.append(SessionEventKeys.UserMessage, UserMessageEvent(1, createUserMessage(listOf(TextBlock("hello")))))
        coroutineScope {
            repeat(16) { launch { runtime.context.sessions.flush() } }
        }
        runtime.context.sessions.flush()
        runtime.context.dispose()
        val database = Room.databaseBuilder<SessionPersistenceDatabase>(root.resolve("harness-sessions.db").toString())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        assertEquals(1L, database.sessions().countEvents(id.value))
        database.close()
        Files.list(root).use { paths ->
            assertEquals(emptyList(), paths.filter { it.fileName.toString().endsWith(".session.json") }.toList())
        }
        root.toFile().deleteRecursively()
    }

    @Test
    fun flushAndLoadRebuildsEventsAndMessages() = runTest {
        val root = Files.createTempDirectory("harness-session-")
        val id = SessionId("persisted")
        val first = Runtime()
        first.install(SessionPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val session = first.context.createSession(id)
        val message = createUserMessage(listOf(TextBlock("hello")))
        session.append(SessionEventKeys.UserMessage, UserMessageEvent(1, message))
        first.context.sessions.flush()
        val events = session.events()
        val history = session.deriveMessages()
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val restored = second.context.persistence.load(id)
        assertEquals(events, restored.events().take(events.size))
        assertEquals(history, restored.deriveMessages())
        second.context.dispose()

        val database = Room.databaseBuilder<SessionPersistenceDatabase>(root.resolve("harness-sessions.db").toString())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        database.sessions().upsertEvents(
            listOf(SessionEventRow(id.value, events.size.toLong(), "unknown/required", 1L, "{\"value\":\"x\"}", null, null, null))
        )
        database.close()
        val third = Runtime()
        third.install(SessionPlugin())
        third.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        assertFailsWith<SessionException> { third.context.persistence.load(id) }
        assertEquals(0, third.context.sessions.list().size)
        third.context.dispose()
        root.toFile().deleteRecursively()
    }

    @Test
    fun openTurnRepairIsFlushedAndInboxProjectionCanBeLoaded() = runTest {
        val root = Files.createTempDirectory("harness-session-repair-")
        val id = SessionId("repair")
        val first = Runtime()
        first.install(SessionPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val session = first.context.createSession(id)
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(1))
        session.append(SessionEventKeys.StepStart, StepStartEvent(1, 1))
        first.context.sessions.flush()
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val repaired = second.context.persistence.load(id)
        val repairedCount = repaired.events().count { it.type == im.hikaru.harness.session.SessionEventNames.TURN_END }
        assertEquals(1, repairedCount)
        assertEquals(0, second.context.persistence.inbox(id).version)
        second.context.dispose()

        val third = Runtime()
        third.install(SessionPlugin())
        third.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val loadedAgain = third.context.persistence.load(id)
        assertEquals(repairedCount, loadedAgain.events().count { it.type == im.hikaru.harness.session.SessionEventNames.TURN_END })
        third.context.dispose()
        root.toFile().deleteRecursively()
    }

    @Test
    fun malformedKnownExtensionEventIsRejectedBeforeSessionLoad() = runTest {
        val root = Files.createTempDirectory("harness-session-extension-")
        val id = SessionId("extension")
        val first = Runtime()
        first.install(SessionPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val session = first.context.createSession(id)
        first.context.sessions.flush()
        val database = Room.databaseBuilder<SessionPersistenceDatabase>(root.resolve("harness-sessions.db").toString())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        database.sessions().upsertEvents(
            listOf(SessionEventRow(id.value, session.events().size.toLong(), "tool/call", 1L, "{\"turn\":\"not-a-number\"}", null, null, null))
        )
        database.close()
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        assertFailsWith<SessionException> { second.context.persistence.load(id) }
        assertEquals(0, second.context.sessions.list().size)
        second.context.dispose()
        root.toFile().deleteRecursively()
    }

    @Test
    fun unknownIgnorableEventSurvivesFlushLoadRoundTrip() = runTest {
        val root = Files.createTempDirectory("harness-session-ignorable-")
        val id = SessionId("ignorable")
        val optionalEvent =
            SessionEventKey(
                name = "extension/optional",
                serializer = JsonObject.serializer(),
                ignorable = true,
            )
        val payload = buildJsonObject { put("value", "preserved") }

        val first = Runtime()
        first.install(SessionPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val session = first.context.createSession(id)
        val original = session.append(optionalEvent, payload)
        first.context.sessions.flush()
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val restored = second.context.persistence.load(id)
        assertEquals(listOf(original), restored.events())
        second.context.sessions.flush()
        second.context.dispose()

        val third = Runtime()
        third.install(SessionPlugin())
        third.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        assertEquals(listOf(original), third.context.persistence.load(id).events())
        third.context.dispose()
        root.toFile().deleteRecursively()
    }

    @Test
    fun pendingRetryResumesAcrossRuntimeRestartWithoutRepeatingCommittedAttempt() = runTest {
        val root = Files.createTempDirectory("harness-llm-retry-")
        val id = AgentId("retry-restart")
        var now = 0L
        val clock = LlmRetryClock { now }
        var calls = 0

        fun scriptedAdapter(): LlmAdapter =
            object : LlmAdapter {
                override fun providerRetryPolicy(provider: String) =
                    NormalRetryPolicy(
                        maxRetries = 1,
                        initialDelayMs = 1_000,
                        maxDelayMs = 1_000,
                        jitterRatio = 0.0,
                    )

                override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                    calls += 1
                    return if (calls == 1) {
                        flowOf(FinishChunk(ErrorFinishReason(LlmException("server", "SERVER").failure)))
                    } else {
                        flowOf(
                            BlockStartChunk(0, "text"),
                            TextDeltaChunk(0, "ok"),
                            FinishChunk(StopFinishReason),
                        )
                    }
                }
            }

        val first = Runtime()
        first.install(SessionPlugin())
        first.install(LlmPlugin())
        first.install(AgentPlugin())
        first.context.llm.registerAdapter(listOf("scripted"), scriptedAdapter())
        first.install(AgentLoopPlugin("scripted", "test"))
        first.install(LlmRetryPlugin(clock))
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val firstHandle = first.context.agents.create(id)
        val retryPersisted = CompletableDeferred<Unit>()
        first.context.on(SessionEvents.Appended) { notice ->
            if (notice.event.type == LlmRetrySessionEvents.Retry.name) retryPersisted.complete(Unit)
        }
        firstHandle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        retryPersisted.await()
        assertEquals(1, calls)
        assertEquals(1, firstHandle.agent.session.events().count { it.type == LlmRetrySessionEvents.Retry.name })
        first.context.sessions.flush()

        // Simulate process time passing while the durable snapshot remains at
        // the retry decision. Clean shutdown events stay in memory and are not
        // flushed, matching the file left behind by a crash.
        first.context.dispose()
        now = 1_000L

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(LlmPlugin())
        second.install(AgentPlugin())
        second.context.llm.registerAdapter(listOf("scripted"), scriptedAdapter())
        second.install(AgentLoopPlugin("scripted", "test"))
        second.install(LlmRetryPlugin(clock))
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val restoredSession = second.context.persistence.load(SessionId(id.value))
        val restoredHandle = second.context.agents.restore(restoredSession)
        restoredHandle.agent.awaitIdle()

        assertEquals(2, calls)
        val events = restoredHandle.agent.session.events()
        val types = events.map { it.type }
        assertEquals(1, types.count { it == SessionEventNames.TURN_START })
        assertEquals(1, types.count { it == SessionEventNames.STEP_START })
        assertEquals(2, types.count { it == SessionEventNames.REQUEST_HEADER })
        assertEquals(1, types.count { it == LlmRetrySessionEvents.Retry.name })
        assertEquals(1, types.count { it == LlmRetrySessionEvents.RetryStarted.name })
        assertEquals(1, types.count { it == LlmRetrySessionEvents.Terminal.name })
        assertEquals(1, types.count { it == SessionEventNames.STEP_END })
        assertEquals(1, types.count { it == SessionEventNames.TURN_END })
        assertEquals(2, restoredHandle.agent.session.deriveMessages().size)

        second.context.sessions.flush()
        second.context.dispose()

        val third = Runtime()
        third.install(SessionPlugin())
        third.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val loadedAgain = third.context.persistence.load(SessionId(id.value))
        assertEquals(events, loadedAgain.events())
        third.context.dispose()
        root.toFile().deleteRecursively()
    }
}

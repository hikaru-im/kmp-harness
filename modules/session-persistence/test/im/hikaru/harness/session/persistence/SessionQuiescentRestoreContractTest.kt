package im.hikaru.harness.session.persistence

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentQuiescentRestore
import im.hikaru.harness.agent.AgentSendTarget
import im.hikaru.harness.agent.AgentWakeup
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
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionEvents
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.StepStartEvent
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.createSession
import im.hikaru.harness.session.sessions
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionQuiescentRestoreContractTest {
    @Test
    fun quiescentLoadRestoresExactHistoryAndAttachesADeclaredLoopAgentWithoutRequests() = runTest {
        val root = Files.createTempDirectory("harness-quiescent-")
        val id = SessionId("quiescent")
        var calls = 0
        fun adapter(): LlmAdapter = object : LlmAdapter {
            override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                calls += 1
                return flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
            }
        }

        val first = Runtime()
        first.install(SessionPlugin())
        first.install(LlmPlugin())
        first.install(AgentPlugin())
        first.context.llm.registerAdapter(listOf("scripted"), adapter())
        first.install(AgentLoopPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val handle = first.context.agents.create(AgentId(id.value), options = AgentOptions(provider = "scripted", model = "test"))
        handle.agent.followup(createUserMessage(listOf(TextBlock("hello"))))
        handle.agent.awaitIdle()
        assertEquals(1, calls)
        val durable = handle.agent.session.events()
        assertTrue(durable.any { it.type == SessionEventNames.TURN_END })
        first.context.sessions.flush()
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(LlmPlugin())
        second.install(AgentPlugin())
        second.context.llm.registerAdapter(listOf("scripted"), adapter())
        second.install(AgentLoopPlugin())
        second.install(LlmRetryPlugin(LlmRetryClock { 0L }))
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val loaded = assertIs<SessionQuiescentLoad.Loaded>(second.context.persistence.loadQuiescent(id))
        assertEquals(durable, loaded.session.events())
        assertEquals(1, calls)
        val restored = assertIs<AgentQuiescentRestore.Granted>(
            second.context.agents.restoreQuiescent(loaded.session, AgentOptions(provider = "scripted", model = "test")))
        restored.handle.agent.awaitIdle()
        // No model request, no new turn/attempt event and no inbox drain on the quiescent path.
        assertEquals(1, calls)
        assertEquals(durable, restored.handle.agent.session.events())
        assertEquals(0, second.context.persistence.inbox(id).nextTurn.size)
        assertEquals(0, second.context.persistence.inbox(id).nextStep.size)
        second.context.dispose()

        val database = Room.databaseBuilder<SessionPersistenceDatabase>(root.resolve("harness-sessions.db").toString())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        assertEquals(durable.size.toLong(), database.sessions().countEvents(id.value))
        database.close()
        root.toFile().deleteRecursively()
    }

    @Test
    fun openTurnIsRejectedInsteadOfBeingRepaired() = runTest {
        val root = Files.createTempDirectory("harness-quiescent-open-")
        val id = SessionId("open-turn")
        val first = Runtime()
        first.install(SessionPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val session = first.context.createSession(id)
        session.append(SessionEventKeys.TurnStart, TurnStartEvent(1))
        session.append(SessionEventKeys.StepStart, StepStartEvent(1, 1))
        val durable = session.events()
        first.context.sessions.flush()
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val rejected = assertIs<SessionQuiescentLoad.Rejected>(second.context.persistence.loadQuiescent(id))
        assertEquals(SessionQuiescentRejection.OPEN_TURN, rejected.reason)
        assertEquals(0, second.context.sessions.list().size)
        second.context.dispose()

        val database = Room.databaseBuilder<SessionPersistenceDatabase>(root.resolve("harness-sessions.db").toString())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        assertEquals(durable.size.toLong(), database.sessions().countEvents(id.value))
        assertEquals(0, database.sessions().findEvents(id.value).count { it.type == SessionEventNames.TURN_END })
        database.close()
        root.toFile().deleteRecursively()
    }

    @Test
    fun pendingInboxIsRejectedInsteadOfBeingCleared() = runTest {
        val root = Files.createTempDirectory("harness-quiescent-inbox-")
        val id = AgentId("queued")
        var calls = 0
        val adapter = object : LlmAdapter {
            override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                calls += 1
                return flowOf(FinishChunk(StopFinishReason))
            }
        }
        val first = Runtime()
        first.install(SessionPlugin())
        first.install(LlmPlugin())
        first.install(AgentPlugin())
        first.context.llm.registerAdapter(listOf("scripted"), adapter)
        first.install(AgentLoopPlugin())
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val handle = first.context.agents.create(id, options = AgentOptions(provider = "scripted", model = "test"))
        handle.agent.inbox.insert(createUserMessage(listOf(TextBlock("queued"))), AgentSendTarget.NEXT_TURN, AgentWakeup.NONE)
        first.context.sessions.flush()
        assertEquals(1, first.context.persistence.inbox(SessionId(id.value)).nextTurn.size)
        first.context.dispose()

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val rejected = assertIs<SessionQuiescentLoad.Rejected>(second.context.persistence.loadQuiescent(SessionId(id.value)))
        assertEquals(SessionQuiescentRejection.PENDING_INBOX, rejected.reason)
        assertEquals(1, second.context.persistence.inbox(SessionId(id.value)).nextTurn.size)
        assertEquals(0, calls)
        second.context.dispose()
        root.toFile().deleteRecursively()
    }

    @Test
    fun pendingRetryIsRejectedInsteadOfResuming() = runTest {
        val root = Files.createTempDirectory("harness-quiescent-retry-")
        val id = AgentId("retry-quiescent")
        var now = 0L
        val clock = LlmRetryClock { now }
        var calls = 0
        fun adapter(): LlmAdapter = object : LlmAdapter {
            override fun providerRetryPolicy(provider: String) =
                NormalRetryPolicy(maxRetries = 1, initialDelayMs = 1_000, maxDelayMs = 1_000, jitterRatio = 0.0)

            override fun stream(options: GenerateOptions): Flow<StreamChunk> {
                calls += 1
                return if (calls == 1) {
                    flowOf(FinishChunk(ErrorFinishReason(LlmException("server", "SERVER").failure)))
                } else {
                    flowOf(BlockStartChunk(0, "text"), TextDeltaChunk(0, "ok"), FinishChunk(StopFinishReason))
                }
            }
        }

        val first = Runtime()
        first.install(SessionPlugin())
        first.install(LlmPlugin())
        first.install(AgentPlugin())
        first.context.llm.registerAdapter(listOf("scripted"), adapter())
        first.install(AgentLoopPlugin())
        first.install(LlmRetryPlugin(clock))
        first.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val handle = first.context.agents.create(id, options = AgentOptions(provider = "scripted", model = "test"))
        val retryPersisted = CompletableDeferred<Unit>()
        first.context.on(SessionEvents.Appended) { notice ->
            if (notice.event.type == LlmRetrySessionEvents.Retry.name) retryPersisted.complete(Unit)
        }
        handle.agent.followup(createUserMessage(listOf(TextBlock("hi"))))
        retryPersisted.await()
        assertEquals(1, calls)
        first.context.sessions.flush()
        first.context.dispose()
        now = 1_000L

        val second = Runtime()
        second.install(SessionPlugin())
        second.install(LlmPlugin())
        second.install(AgentPlugin())
        second.context.llm.registerAdapter(listOf("scripted"), adapter())
        second.install(AgentLoopPlugin())
        second.install(LlmRetryPlugin(clock))
        second.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val rejected = assertIs<SessionQuiescentLoad.Rejected>(second.context.persistence.loadQuiescent(SessionId(id.value)))
        assertEquals(SessionQuiescentRejection.PENDING_RETRY, rejected.reason)
        assertEquals(1, calls)
        assertEquals(0, second.context.sessions.list().size)
        second.context.dispose()
        root.toFile().deleteRecursively()
    }

    @Test
    fun missingSessionIsRejectedAsNotFound() = runTest {
        val root = Files.createTempDirectory("harness-quiescent-missing-")
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(sessionPersistencePluginForTest(root.resolve("harness-sessions.db").toString()))
        val rejected = assertIs<SessionQuiescentLoad.Rejected>(runtime.context.persistence.loadQuiescent(SessionId("absent")))
        assertEquals(SessionQuiescentRejection.NOT_FOUND, rejected.reason)
        assertEquals(0, runtime.context.sessions.list().size)
        runtime.context.dispose()
        root.toFile().deleteRecursively()
    }
}

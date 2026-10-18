package im.hikaru.harness.session.api

import im.hikaru.harness.agent.*
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.*
import java.nio.file.Files
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DurableConversationTest {
    @Test
    fun createReturnsTheSameManagementCapabilitiesAsTheCatalog() = runTest {
        val path = Files.createTempDirectory("durable-create-summary-").resolve("sessions.db").toFile()
        val runtime = open(path)
        try {
            val api = runtime.context.sessionApi
            val created = api.create(agentOptions = AgentOptions(provider = "scripted", model = "test"))
            assertEquals(api.list().single(), created)
            assertTrue(created.managementAvailable)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun catalogUsesTheFirstHumanMessageAfterInternalUserRoleMessages() = runTest {
        val path = Files.createTempDirectory("durable-human-title-").resolve("sessions.db").toFile()
        val runtime = open(path)
        try {
            val session = runtime.context.createSession(SessionId("human-title"))
            val human = createUserMessage(listOf(TextBlock("Human title")))
            session.append(SessionEventKeys.UserMessage, UserMessageEvent(1,
                human.copy(source = PluginMessageSource("internal"), content = listOf(TextBlock("Internal instruction")))))
            session.append(SessionEventKeys.UserMessage, UserMessageEvent(1, human))
            assertEquals("Human title", runtime.context.sessionApi.list().single().title)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun automaticDurabilityDoesNotRewriteAQuiescentRestore() = runTest {
        val path = Files.createTempDirectory("durable-read-only-restore-").resolve("sessions.db").toFile()
        val first = open(path)
        val id: SessionId
        try {
            id = first.context.sessionApi.create(agentOptions = AgentOptions(provider = "scripted", model = "test")).id
            first.context.sessionApi.prompt(id, createUserMessage(listOf(TextBlock("Preserve history")))).awaitIdle()
        } finally { first.context.dispose() }
        var transactions = 0
        val second = open(path) { transactions++ }
        try {
            val events = second.context.persistence.readEvents(id)
            val loaded = assertIs<SessionQuiescentLoad.Loaded>(second.context.persistence.loadQuiescent(id))
            assertEquals(0, transactions, "Publishing an already-durable session must not write a snapshot")
            assertEquals(events, loaded.session.events())
            assertEquals(events, second.context.persistence.readEvents(id))
            loaded.handle.dispose()
        } finally { second.context.dispose() }
    }

    @Test
    fun automaticDurabilityAvoidsApiSnapshotsAndCatalogHistoryReads() = runTest {
        val path = Files.createTempDirectory("durable-no-redundant-flush-").resolve("sessions.db").toFile()
        val runtime = open(path)
        val isolated = runtime.context.isolate(SessionPersistenceKey)
        try {
            val persistence = runtime.context.persistence
            var flushes = 0
            var historyReads = 0
            isolated.provide(SessionPersistenceKey, object : SessionPersistence by persistence {
                override suspend fun flush(session: Session) { flushes++; persistence.flush(session) }
                override suspend fun readEvents(id: SessionId): List<SessionEventEnvelope>? {
                    historyReads++; return persistence.readEvents(id)
                }
            })
            val api = DefaultSessionApi(isolated)
            val id = api.create(agentOptions = AgentOptions(provider = "scripted", model = "test")).id
            api.prompt(id, createUserMessage(listOf(TextBlock("Small catalog")))).awaitIdle()
            val row = api.list().single()
            api.updateConversation(ConversationUpdate(id, row.revision, "New title", true, row.lastEventSeq))
            api.cancel(id)
            assertEquals(0, flushes)
            assertEquals(0, historyReads)
            assertEquals("New title", api.list().single().title)
        } finally { isolated.dispose(); runtime.context.dispose() }
    }

    @Test
    fun deleteRemovesLiveConversationAndCannotResurrectAfterRestart() = runTest {
        val path = Files.createTempDirectory("durable-delete-").resolve("sessions.db").toFile()
        val first = open(path)
        val id: SessionId
        try {
            val api = first.context.sessionApi
            id = api.create(agentOptions = AgentOptions(provider = "scripted", model = "test")).id
            api.prompt(id, createUserMessage(listOf(TextBlock("Delete this")))).awaitIdle()
            assertFailsWith<IllegalStateException> { api.deleteConversation(id, 99) }
            assertEquals(id, api.list().single().id)
            api.deleteConversation(id, api.list().single().revision)
            assertTrue(api.list().isEmpty())
            assertTrue(first.context.agents.list().isEmpty())
            assertNull(first.context.sessions.get(id))
        } finally { first.context.dispose() }
        val second = open(path)
        try {
            assertTrue(second.context.sessionApi.list().isEmpty())
            assertFailsWith<IllegalStateException> { second.context.sessionApi.history(id) }
            assertFailsWith<IllegalStateException> {
                second.context.sessionApi.prompt(id, createUserMessage(listOf(TextBlock("must not resume"))))
            }
            assertTrue(second.context.persistence.metadata(id)!!.deleted)
            assertTrue(second.context.agents.list().isEmpty())
        } finally { second.context.dispose() }
    }

    @Test
    fun archiveUsesTheExistingGateAndRestoresOnlyAfterUnarchive() = runTest {
        val path = Files.createTempDirectory("durable-archive-").resolve("sessions.db").toFile()
        val runtime = open(path)
        val archived = mutableSetOf<SessionId>()
        runtime.context.provide(SessionArchiveKey, object : SessionArchiveGate {
            override val supportsManagement = true
            override suspend fun isArchived(header: SessionHeader) = header.id in archived
            override suspend fun setArchived(header: SessionHeader, desired: Boolean): String? {
                if (desired) archived.add(header.id) else archived.remove(header.id)
                return null
            }
        })
        try {
            val api = runtime.context.sessionApi
            val id = api.create(agentOptions = AgentOptions(provider = "scripted", model = "test")).id
            api.prompt(id, createUserMessage(listOf(TextBlock("Archive me")))).awaitIdle()
            api.archiveConversation(id, true)
            assertTrue(api.list().single().archived)
            assertNull(runtime.context.agents.get(AgentId(id.value)))
            assertNull(runtime.context.sessions.get(id))
            assertEquals(2, api.history(id).size)
            assertFailsWith<IllegalStateException> { api.prompt(id, createUserMessage(listOf(TextBlock("blocked")))) }
            api.archiveConversation(id, false)
            assertFalse(api.list().single().archived)
            assertTrue(runtime.context.agents.list().isEmpty())
            api.prompt(id, createUserMessage(listOf(TextBlock("Continue after archive")))).awaitIdle()
            assertEquals(4, api.history(id).size)
            api.deleteConversation(id, api.list().single().revision)
            assertFailsWith<IllegalStateException> { api.archiveConversation(id, true) }
            assertFailsWith<IllegalStateException> { api.archiveConversation(id, false) }
            assertTrue(archived.isEmpty(), "Deleted identities must not create new archive records")
        } finally { runtime.context.dispose() }
    }

    private suspend fun open(path: java.io.File, onTransaction: () -> Unit = {}): Runtime = Runtime().also { runtime ->
        runtime.install(SessionPlugin())
        runtime.install(SessionPersistencePlugin(SessionPersistenceDatabaseFactory {
            val database = jvmSessionPersistenceDatabaseFactory(path).create()
            object : SessionDatabaseAccess by database {
                override suspend fun <T> transaction(block: suspend () -> T): T {
                    onTransaction()
                    return database.transaction(block)
                }
            }
        }, autoSave = true))
        runtime.install(LlmPlugin())
        runtime.install(AgentPlugin())
        runtime.context.llm.registerAdapter(listOf("scripted"), object : LlmAdapter {
            override fun stream(options: GenerateOptions) = flowOf(BlockStartChunk(0, "text"),
                TextDeltaChunk(0, "response"), FinishChunk(StopFinishReason))
        })
        runtime.install(AgentLoopPlugin())
        runtime.install(SessionApiPlugin())
    }

    @Test
    fun restartReadsHistoryWithoutAgentsAndRestoresOnlyOnSend() = runTest {
        val path = Files.createTempDirectory("durable-conversation-").resolve("sessions.db").toFile()
        val first = open(path)
        val id: SessionId
        try {
            val api = first.context.sessionApi
            id = api.create(agentOptions = AgentOptions(provider = "scripted", model = "test")).id
            api.prompt(id, createUserMessage(listOf(TextBlock("First conversation")))).awaitIdle()
            val summary = api.list().single()
            assertEquals("First conversation", summary.title)
            assertTrue(summary.unread)
            api.updateConversation(ConversationUpdate(id, summary.revision, "Renamed", true, summary.lastEventSeq))
        } finally { first.context.dispose() }

        val second = open(path)
        try {
            val api = second.context.sessionApi
            assertTrue(second.context.agents.list().isEmpty())
            val summary = api.list().single()
            assertEquals("Renamed", summary.title)
            assertTrue(summary.pinned)
            assertFalse(summary.unread)
            assertFalse(summary.live)
            assertEquals(2, api.history(id).size)
            assertTrue(second.context.agents.list().isEmpty(), "Reading must not restore execution")
            assertFailsWith<IllegalStateException> {
                api.updateConversation(ConversationUpdate(id, 0, "stale", false, -1))
            }
            api.prompt(id, createUserMessage(listOf(TextBlock("Continue")))).awaitIdle()
            assertEquals(4, api.history(id).size)
            assertEquals(1, second.context.agents.list().size)
            assertTrue(api.list().single().unread)
        } finally { second.context.dispose() }
    }

    @Test
    fun openTurnCanBeReadAfterRestartButCannotExecute() = runTest {
        val path = Files.createTempDirectory("durable-open-turn-").resolve("sessions.db").toFile()
        val first = open(path)
        val id = SessionId("unfinished")
        try {
            val session = first.context.createSession(id)
            session.append(SessionEventKeys.UserMessage, UserMessageEvent(1, createUserMessage(listOf(TextBlock("pending")))))
            session.append(SessionEventKeys.TurnStart, TurnStartEvent(1))
            // No explicit flush: the production durability hook must have persisted both events.
        } finally { first.context.dispose() }
        val second = open(path)
        try {
            assertEquals("pending", second.context.sessionApi.list().single().title)
            assertEquals(1, second.context.sessionApi.history(id).size)
            assertFailsWith<IllegalStateException> {
                second.context.sessionApi.prompt(id, createUserMessage(listOf(TextBlock("must not run"))))
            }
            assertNull(second.context.sessions.get(id))
            assertTrue(second.context.agents.list().isEmpty())
            assertEquals(2, second.context.persistence.readEvents(id)!!.size)
        } finally { second.context.dispose() }
    }
}

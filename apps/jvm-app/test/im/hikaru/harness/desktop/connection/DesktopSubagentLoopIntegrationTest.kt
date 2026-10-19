package im.hikaru.harness.desktop.connection

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.agents
import im.hikaru.harness.bundle.desktop.startDesktopProfile
import im.hikaru.harness.client.connection.AgentOptions as ClientAgentOptions
import im.hikaru.harness.client.connection.CreateSessionOptions as ClientCreateSessionOptions
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.connection.ChildHistoryResult
import im.hikaru.harness.client.connection.DescendantCoverage
import im.hikaru.harness.client.connection.DescendantStatus
import im.hikaru.harness.client.connection.SessionDescendantsResult
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.llm.*
import im.hikaru.harness.profile.file.ProfiledHarnessHost
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.persistence
import im.hikaru.harness.session.persistence.sessionPersistencePluginForTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import kotlin.test.*

/** Default Desktop bundle, real producer/loops, local gateway and Room. Only LLM output is scripted.
 * No manual session appends or agent restore. Idle is synchronization, never completion evidence.
 * This covers the shared repository projection, not Compose rendering or remote transport. */
class DesktopSubagentLoopIntegrationTest {
    @Test
    fun defaultBundleSpawnRunsChildLoopAndPersistsExactHistoryForFreshReader() = runBlocking {
        withTimeout(60_000) {
            fixture(1) { fixture ->
                val spawned = spawn(fixture)
                assertProjection(fixture, spawned, DescendantStatus.RUNNING, ChildAgentStatus.Active)
                assertHistory(fixture, spawned, listOf(spawned.input), listOf(CHILD_PROMPT))
                assertTrue(spawned.child.session.events().none { it.type == SessionEventNames.TURN_END },
                    "An accepted spawn and a suspended provider are not completion evidence")

                fixture.adapter.turns[0].release.complete(Unit)
                withTimeout(10_000) { spawned.child.awaitIdle() }
                assertCompletedTurn(spawned.parent.session, spawned.parentInput, PARENT_ANSWER)
                assertCompletedTurn(spawned.child.session, spawned.input, FIRST_ANSWER)
                assertHistory(fixture, spawned, listOf(spawned.input), listOf(CHILD_PROMPT, FIRST_ANSWER))
                assertEquals(2, fixture.adapter.parentCalls.get())
                assertEquals(1, fixture.adapter.childCalls.get())

                val snapshots = flush(fixture, spawned.parent.session, spawned.child.session)
                // Fully close the first Room owner before constructing the reader Runtime.
                fixture.close()
                assertTrue(fixture.profile.host.runtime.context.isDisposed)
                assertFreshReader(fixture.home, snapshots)
            }
        }
    }

    @Test
    fun idleChildRemainsUnknownAndRealFollowupRefreshesHistoryWithoutPermanentDone() = runBlocking {
        withTimeout(60_000) {
            fixture(2) { fixture ->
                val spawned = spawn(fixture)
                fixture.adapter.turns[0].release.complete(Unit)
                withTimeout(10_000) { spawned.child.awaitIdle() }
                val first = assertCompletedTurn(spawned.child.session, spawned.input, FIRST_ANSWER)
                assertEquals(AgentStatus.IDLE, spawned.child.status)
                assertProjection(fixture, spawned, DescendantStatus.UNKNOWN, ChildAgentStatus.Unknown)
                val firstHistory = assertHistory(fixture, spawned, listOf(spawned.input),
                    listOf(CHILD_PROMPT, FIRST_ANSWER))

                val followup = createUserMessage(listOf(TextBlock(SECOND_PROMPT)))
                val prompt = fixture.connection.session.prompt(spawned.childId, toClientMessage(followup))
                assertEquals(spawned.childId, prompt.receipt.sessionId)
                assertNotEquals(followup.id.value, prompt.receipt.messageId)
                val request = withTimeout(10_000) { fixture.adapter.turns[1].started.await() }
                assertEquals(spawned.childId.value, assertNotNull(request.sessionId).value)
                assertEquals(listOf(spawned.input, followup.id.value),
                    request.messages.filter { it.source == UserMessageSource }.map { it.id.value })
                assertProjection(fixture, spawned, DescendantStatus.RUNNING, ChildAgentStatus.Active)
                assertHistory(fixture, spawned, listOf(spawned.input, followup.id.value),
                    listOf(CHILD_PROMPT, FIRST_ANSWER, SECOND_PROMPT))

                fixture.adapter.turns[1].release.complete(Unit)
                withTimeout(10_000) { prompt.awaitIdle() }
                val second = assertCompletedTurn(spawned.child.session, followup.id.value, SECOND_ANSWER)
                assertTrue(second.turn > first.turn)
                assertNotEquals(first.assistantId, second.assistantId)
                assertEquals(AgentStatus.IDLE, spawned.child.status)
                assertProjection(fixture, spawned, DescendantStatus.UNKNOWN, ChildAgentStatus.Unknown)
                val refreshed = assertHistory(fixture, spawned, listOf(spawned.input, followup.id.value),
                    listOf(CHILD_PROMPT, FIRST_ANSWER, SECOND_PROMPT, SECOND_ANSWER))
                assertEquals(firstHistory, refreshed.take(firstHistory.size))
                assertEquals(2, fixture.profile.host.runtime.context.agents.list().size,
                    "Followup uses the same child, not a replacement producer")
                assertEquals(2, fixture.adapter.childCalls.get())
                assertEquals(2, fixture.adapter.parentCalls.get())
                assertCompletedTurn(spawned.parent.session, spawned.parentInput, PARENT_ANSWER)

                val snapshots = flush(fixture, spawned.parent.session, spawned.child.session)
                fixture.close()
                assertFreshReader(fixture.home, snapshots)
            }
        }
    }

    private suspend fun spawn(fixture: Fixture): Spawned {
        val context = fixture.profile.host.runtime.context
        val parent = fixture.connection.session.create(
            options = ClientCreateSessionOptions(cwd = fixture.home.toString()),
            agentOptions = ClientAgentOptions(provider = PARENT_PROVIDER, model = MODEL),
        )
        val input = createUserMessage(listOf(TextBlock(PARENT_PROMPT)))
        val prompt = fixture.connection.session.prompt(parent.id, toClientMessage(input))
        val result = withTimeout(10_000) { fixture.adapter.spawnResult.await() }
        assertEquals("ACCEPTED", result.string("outcome"))
        assertEquals("ACCEPTED", result.string("delivery"))
        assertFalse(result.getValue("completionVerified").jsonPrimitive.boolean)
        assertEquals(parent.id.value, result.string("parentSessionId"))
        val childId = ClientSessionId(result.string("childSessionId"))
        val childInput = result.string("inputMessageId")
        assertNotEquals(childInput, result.string("receiptId"))
        assertNotEquals(parent.id, childId)
        val request = withTimeout(10_000) { fixture.adapter.turns[0].started.await() }
        assertEquals(childId.value, assertNotNull(request.sessionId).value)
        assertEquals(CHILD_PROVIDER, request.provider)
        assertEquals(MODEL, request.model)
        val actualInput = request.messages.single { it.id.value == childInput }
        assertEquals(UserMessageSource, actualInput.source)
        assertEquals(listOf(TextBlock(CHILD_PROMPT)), actualInput.content)
        withTimeout(10_000) { prompt.awaitIdle() }
        val parentAgent = assertNotNull(context.agents.get(AgentId(parent.id.value)))
        val child = assertNotNull(context.agents.get(AgentId(childId.value)))
        assertEquals(2, context.agents.list().size)
        assertEquals(SessionOrigin.SUBAGENT, child.session.header.origin)
        assertEquals(parentAgent.session.id, child.session.header.parentSession)
        assertEquals(fixture.home.toString(), child.session.header.cwd)
        assertEquals(CHILD_PROVIDER, child.options.provider)
        assertEquals(MODEL, child.options.model)
        val description = fixture.connection.host.describe()
        assertTrue(description.isSuccess)
        val owner = ThreadPanelsOwner(fixture.connection,
            ConversationHostScope(assertNotNull(description.data).hostId, generation = 1, identity = null), parent.id)
        return Spawned(parentAgent, input.id.value, child, childId, childInput, owner)
    }

    private suspend fun assertProjection(fixture: Fixture, spawned: Spawned,
        status: DescendantStatus, panelStatus: ChildAgentStatus) {
        val result = assertIs<SessionDescendantsResult.Available>(
            fixture.connection.session.descendants(spawned.owner.parentSessionId))
        assertEquals(spawned.owner.parentSessionId, result.parent)
        assertEquals(DescendantCoverage.LIVE_REGISTRY, result.coverage)
        assertFalse(result.complete)
        val descendant = result.children.single()
        assertEquals(spawned.childId, descendant.child)
        assertEquals(spawned.owner.parentSessionId, descendant.parentSession)
        assertEquals(listOf(spawned.owner.parentSessionId, spawned.childId), descendant.verifiedAncestorPath)
        assertEquals(status, descendant.status)
        val overview = assertIs<PanelLoad.Ready<SubagentOverview>>(fixture.panels.readOverview(spawned.owner))
        assertEquals(spawned.owner.scope, overview.scope)
        assertEquals(SubagentOverviewCompleteness.LiveOnlyIncomplete, overview.value.completeness)
        val summary = overview.value.children.single()
        assertEquals(ChildAgentKey(spawned.owner.scope, spawned.childId), summary.key)
        assertEquals(descendant.verifiedAncestorPath, summary.verifiedAncestorPath)
        assertEquals(panelStatus, summary.status)
        assertEquals(ChildHistoryAvailability.Available, summary.historyAvailability)
        assertEquals(descendant.objective, summary.objective)
        assertEquals(MODEL, summary.model)
        assertFalse(summary.canInteract)
        assertTrue(overview.value.children(SubagentSection.Done).isEmpty())
        if (panelStatus == ChildAgentStatus.Unknown) assertEquals(listOf(summary), overview.value.unknown)
    }

    private suspend fun assertHistory(fixture: Fixture, spawned: Spawned, inputs: List<String>,
        text: List<String>): List<ChildTranscriptEntry> {
        val history = assertIs<ChildHistoryResult.Available>(fixture.connection.session.readChildHistory(
            spawned.owner.parentSessionId, spawned.childId))
        assertEquals(spawned.owner.parentSessionId, history.parent)
        assertEquals(spawned.childId, history.child)
        assertEquals(listOf(spawned.owner.parentSessionId, spawned.childId), history.verifiedAncestorPath)
        val actual = spawned.child.session.deriveMessages()
        assertEquals(actual.map(::toClientMessage), history.messages)
        assertEquals(inputs, actual.filter { it.source == UserMessageSource }.map { it.id.value })
        assertEquals(text, actual.map { (it.content.single() as TextBlock).text })
        val key = ChildAgentKey(spawned.owner.scope, spawned.childId)
        val detail = assertIs<PanelLoad.Ready<ChildAgentDetail>>(fixture.panels.readDetail(spawned.owner, key))
        assertEquals(spawned.owner.scope, detail.scope)
        assertEquals(key, detail.value.key)
        assertFalse(detail.value.canInteract)
        assertEquals(actual.map { ChildTranscriptEntry(it.id.value,
            if (it.role == MessageRole.USER) ChildTranscriptRole.User else ChildTranscriptRole.Assistant,
            (it.content.single() as TextBlock).text) }, detail.value.entries)
        return detail.value.entries
    }

    /** Exact input identity, model message, same turn and log order; idle cannot satisfy this. */
    private suspend fun assertCompletedTurn(session: Session, inputId: String, answer: String): TurnProof {
        val events = session.events()
        val inputEnvelope = events.single { it.type == SessionEventNames.USER_MESSAGE &&
            Json.decodeFromJsonElement(UserMessageEvent.serializer(), it.data).message.id.value == inputId }
        val input = Json.decodeFromJsonElement(UserMessageEvent.serializer(), inputEnvelope.data)
        assertEquals(UserMessageSource, input.message.source)
        val assistantEnvelope = events.single { it.type == SessionEventNames.ASSISTANT_MESSAGE &&
            Json.decodeFromJsonElement(AssistantMessageEvent.serializer(), it.data).let { event ->
                event.turn == input.turn && event.message.content == listOf(TextBlock(answer))
            } }
        val assistant = Json.decodeFromJsonElement(AssistantMessageEvent.serializer(), assistantEnvelope.data)
        assertNull(assistant.interrupted)
        assertEquals(ModelMessageSource(if (answer == PARENT_ANSWER) PARENT_PROVIDER else CHILD_PROVIDER, MODEL),
            assistant.message.source)
        val endEnvelope = events.single { it.type == SessionEventNames.TURN_END &&
            Json.decodeFromJsonElement(TurnEndEvent.serializer(), it.data).turn == input.turn }
        val end = Json.decodeFromJsonElement(TurnEndEvent.serializer(), endEnvelope.data)
        assertEquals(CompletedTurnEndReason, end.reason)
        assertTrue(inputEnvelope.seq < assistantEnvelope.seq && assistantEnvelope.seq < endEnvelope.seq)
        return TurnProof(input.turn, assistant.message.id.value)
    }

    private suspend fun flush(fixture: Fixture, vararg sessions: Session): List<Snapshot> = sessions.map { session ->
        fixture.profile.host.runtime.context.persistence.flush(session)
        Snapshot(session.header, session.events(), session.deriveMessages())
    }

    private suspend fun assertFreshReader(home: Path, snapshots: List<Snapshot>) {
        assertTrue(Files.exists(home.resolve("harness-sessions.db")))
        val reader = Runtime()
        try {
            reader.install(SessionPlugin())
            reader.install(AgentPlugin()) // Empty registry only; no factory, loop, LLM or restore.
            reader.install(sessionPersistencePluginForTest(home.resolve("harness-sessions.db").toString()))
            assertTrue(reader.context.agents.list().isEmpty())
            for (snapshot in snapshots) {
                val persisted = reader.context.persistence.load(snapshot.header.id)
                assertEquals(snapshot.header, persisted.header)
                // Includes actual UserMessage/Assistant/TurnEnd and detects load-time synthetic repair.
                assertEquals(snapshot.events, persisted.events())
                assertEquals(snapshot.messages, persisted.deriveMessages())
            }
            assertTrue(reader.context.agents.list().isEmpty(), "Reading Room must never restore an Agent")
        } finally {
            withContext(NonCancellable) { withTimeout(10_000) { reader.context.dispose() } }
        }
    }

    private suspend fun fixture(childTurns: Int, block: suspend (Fixture) -> Unit) {
        val home = Files.createTempDirectory("desktop-subagent-loop-")
        var fixture: Fixture? = null
        try {
            val profile = startDesktopProfile(harnessHome = home, pollMillis = 60_000)
            val adapter = ControlledAdapter(childTurns)
            val current = Fixture(home, profile, adapter)
            fixture = current
            listOf("session", "agent", "llm", "tools", "tool-subagent", "tool-subagent-control",
                "tool-subagent-control/list-agents", "agent-loop", "session-api",
                "session-persistence").forEach { name ->
                assertEquals(FiberState.Active, profile.host.loader.fiber(name)?.state, name)
            }
            assertTrue(profile.host.runtime.context.llm.listProviders().isEmpty(),
                "The temporary default profile must keep network providers dormant")
            profile.host.runtime.context.llm.registerAdapter(listOf(PARENT_PROVIDER, CHILD_PROVIDER), adapter)
            block(current)
        } finally {
            // Releasing a test gate during failure is resource cleanup, not completion evidence.
            try { fixture?.close() } finally {
                Files.walk(home).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
    }

    private class Fixture(val home: Path, val profile: ProfiledHarnessHost, val adapter: ControlledAdapter) {
        val connection = LocalConnection(profile.host.gateway)
        val panels = ConnectionThreadPanelsRepository()
        private var closed = false
        suspend fun close() {
            if (closed) return
            withContext(NonCancellable) {
                adapter.turns.forEach { it.release.complete(Unit) }
                withTimeout(10_000) { profile.close() }
                closed = true
            }
        }
    }

    private data class Spawned(val parent: Agent, val parentInput: String, val child: Agent,
        val childId: ClientSessionId, val input: String, val owner: ThreadPanelsOwner)
    private data class TurnProof(val turn: Long, val assistantId: String)
    private data class Snapshot(val header: SessionHeader, val events: List<SessionEventEnvelope>,
        val messages: List<Message>)
    private class ChildTurn {
        val started = CompletableDeferred<GenerateOptions>()
        val release = CompletableDeferred<Unit>()
    }

    /** Gates only suspend provider consumption; IDs/receipts/evidence come from production. */
    private class ControlledAdapter(childTurns: Int) : LlmAdapter {
        val turns = List(childTurns) { ChildTurn() }
        val parentCalls = AtomicInteger()
        val childCalls = AtomicInteger()
        val spawnResult = CompletableDeferred<JsonObject>()
        override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
            check(options.model == MODEL)
            when (options.provider) {
                PARENT_PROVIDER -> when (parentCalls.incrementAndGet()) {
                    1 -> {
                        check(options.tools.orEmpty().any { it.name == "subagent" })
                        check(options.messages.any { it.content == listOf(TextBlock(PARENT_PROMPT)) })
                        val arguments = buildJsonObject {
                            put("description", "Bounded child task")
                            put("prompt", CHILD_PROMPT)
                            put("provider", CHILD_PROVIDER)
                            put("model", MODEL)
                        }.toString()
                        emit(BlockStartChunk(0, "tool-call"))
                        emit(BlockEndChunk(0, ToolCallBlock(CallId("real-spawn-call"), "subagent", arguments)))
                        emit(FinishChunk(ToolCallsFinishReason))
                    }
                    2 -> {
                        val result = options.messages.flatMap { it.content }.filterIsInstance<ToolResultBlock>().single()
                        check(result.toolCallId == CallId("real-spawn-call") && !result.isError)
                        spawnResult.complete(Json.parseToJsonElement((result.content.single() as TextBlock).text).jsonObject)
                        emit(BlockStartChunk(0, "text"))
                        emit(TextDeltaChunk(0, PARENT_ANSWER))
                        emit(FinishChunk(StopFinishReason))
                    }
                    else -> error("Unexpected extra parent call")
                }
                CHILD_PROVIDER -> {
                    val index = childCalls.getAndIncrement()
                    val turn = turns.getOrNull(index) ?: error("Unexpected extra child call")
                    turn.started.complete(options)
                    withTimeout(20_000) { turn.release.await() }
                    emit(BlockStartChunk(0, "text"))
                    emit(TextDeltaChunk(0, if (index == 0) FIRST_ANSWER else SECOND_ANSWER))
                    emit(FinishChunk(StopFinishReason))
                }
                else -> error("Unexpected provider: " + options.provider)
            }
        }
    }

    private fun JsonObject.string(name: String): String {
        val value = getValue(name).jsonPrimitive
        assertTrue(value.isString && value.content.isNotBlank(), name)
        return value.content
    }

    private companion object {
        const val PARENT_PROVIDER = "controlled-parent"
        const val CHILD_PROVIDER = "controlled-child"
        const val MODEL = "controlled-model"
        const val PARENT_PROMPT = "Delegate the bounded child task"
        const val CHILD_PROMPT = "Execute the first child turn"
        const val SECOND_PROMPT = "Execute another turn in the same child"
        const val PARENT_ANSWER = "Real spawn result received"
        const val FIRST_ANSWER = "First child loop answer"
        const val SECOND_ANSWER = "Second child loop answer"
    }
}

package im.hikaru.harness.client.panels

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlin.test.*

class ConnectionThreadPanelsRepositoryTest {
    @Test
    fun mapsActualDescendantsIncludingGrandchildrenWithExplicitIncompleteCoverage() = runTest {
        val fixture = Fixture()
        val direct = descendant(fixture, "direct", DescendantStatus.RUNNING)
        val grandchildId = SessionId("grandchild")
        val grandchild = SessionDescendant(
            child = grandchildId, parentSession = direct.child,
            verifiedAncestorPath = listOf(fixture.owner.parentSessionId, direct.child, grandchildId),
            status = DescendantStatus.UNKNOWN, startedAtEpochMillis = 23,
            model = "real-model", reasoningEffort = "high", objective = "real objective",
        )
        fixture.descendants = SessionDescendantsResult.Available(fixture.owner.parentSessionId, listOf(direct, grandchild))
        val ready = ConnectionThreadPanelsRepository().readOverview(fixture.owner) as PanelLoad.Ready
        assertEquals(fixture.owner.parentSessionId, fixture.overviewParent)
        assertEquals(fixture.owner.scope, ready.scope)
        assertEquals(SubagentOverviewCompleteness.LiveOnlyIncomplete, ready.value.completeness)
        assertEquals(ChildAgentStatus.Active, ready.value.children.first().status)
        val unknown = ready.value.unknown.single()
        assertEquals(ChildAgentStatus.Unknown, unknown.status)
        assertEquals(grandchild.verifiedAncestorPath, unknown.verifiedAncestorPath)
        assertEquals("real-model", unknown.model)
        assertEquals("high", unknown.reasoningEffort)
        assertEquals("real objective", unknown.objective)
        assertEquals(23L, unknown.startedAtEpochMillis)
        assertNull(unknown.lastAssistantAtEpochMillis)
        assertTrue(ready.value.children(SubagentSection.Done).isEmpty())
        assertTrue(ready.value.children.all { !it.canInteract })
    }

    @Test
    fun unavailableAndSuccessfulLiveEmptyAreNotInterchangeable() = runTest {
        val fixture = Fixture()
        val repository = ConnectionThreadPanelsRepository()
        val unavailable = repository.readOverview(fixture.owner) as PanelLoad.Unavailable
        assertEquals(PanelUnavailableReason.ChildrenServiceMissing, unavailable.reason)
        fixture.descendants = SessionDescendantsResult.Available(fixture.owner.parentSessionId, emptyList())
        val empty = repository.readOverview(fixture.owner) as PanelLoad.Ready
        assertTrue(empty.value.children.isEmpty())
        assertEquals(SubagentOverviewCompleteness.LiveOnlyIncomplete, empty.value.completeness)
    }

    @Test
    fun callsVerifiedChildHistoryWithParentAndDisplaysOnlyActualMessages() = runTest {
        val fixture = Fixture()
        val child = ChildAgentKey(fixture.owner.scope, SessionId("child"))
        fixture.history = ChildHistoryResult.Available(
            fixture.owner.parentSessionId, child.sessionId, listOf(fixture.owner.parentSessionId, child.sessionId),
            listOf(
                Message("user", MessageRole.USER, listOf(TextContent("question")), UserMessageSource),
                Message("assistant", MessageRole.ASSISTANT, listOf(ReasoningContent("reasoning"),
                    TextContent("answer"), ToolCallContent("call", "read", "{}")), ModelMessageSource("provider", "model")),
                Message("tool", MessageRole.USER, listOf(ToolResultContent("call", listOf(TextContent("actual output")))),
                    ToolMessageSource("call")),
            ),
        )
        val ready = ConnectionThreadPanelsRepository().readDetail(fixture.owner, child) as PanelLoad.Ready
        assertEquals(fixture.owner.parentSessionId to child.sessionId, fixture.historyRequest)
        assertEquals(child, ready.value.key)
        assertEquals(listOf("user", "assistant", "tool"), ready.value.entries.map { it.id })
        assertEquals(listOf(ChildTranscriptRole.User, ChildTranscriptRole.Assistant, ChildTranscriptRole.Tool),
            ready.value.entries.map { it.role })
        assertEquals("question", ready.value.entries[0].text)
        assertEquals("reasoning\nanswer\nread: {}", ready.value.entries[1].text)
        assertEquals("actual output", ready.value.entries[2].text)
        assertFalse(ready.value.canInteract)
        assertEquals((fixture.history as ChildHistoryResult.Available).messages, ready.value.typedMessages)
    }

    @Test
    fun unavailableHistoryIsNotARealEmptyTranscript() = runTest {
        val fixture = Fixture()
        val child = ChildAgentKey(fixture.owner.scope, SessionId("child"))
        val repository = ConnectionThreadPanelsRepository()
        fixture.history = ChildHistoryResult.Unavailable(fixture.owner.parentSessionId, child.sessionId)
        assertTrue(repository.readDetail(fixture.owner, child) is PanelLoad.Unavailable)
        fixture.history = ChildHistoryResult.Available(fixture.owner.parentSessionId, child.sessionId,
            listOf(fixture.owner.parentSessionId, child.sessionId), emptyList())
        val ready = repository.readDetail(fixture.owner, child) as PanelLoad.Ready
        assertTrue(ready.value.entries.isEmpty())
        assertEquals(emptyList(), ready.value.typedMessages)
    }

    @Test
    fun adapterRejectsWrongOwnerParentAndChildRatherThanPublishingForeignHistory() = runTest {
        val fixture = Fixture()
        val repository = ConnectionThreadPanelsRepository()
        fixture.descendants = SessionDescendantsResult.Unavailable(SessionId("wrong-parent"))
        assertFailsWith<IllegalArgumentException> { repository.readOverview(fixture.owner) }
        val child = ChildAgentKey(fixture.owner.scope, SessionId("child"))
        val foreign = child.copy(scope = child.scope.copy(hostId = HostId("foreign")))
        assertFailsWith<IllegalArgumentException> { repository.readDetail(fixture.owner, foreign) }
        assertNull(fixture.historyRequest, "Foreign scope must fail before IO")
        fixture.history = ChildHistoryResult.Unavailable(fixture.owner.parentSessionId, SessionId("wrong-child"))
        assertFailsWith<IllegalArgumentException> { repository.readDetail(fixture.owner, child) }
    }

    @Test
    fun lambdaBoundaryCopiesExternalCollectionsAndNeverEnablesComposer() = runTest {
        val fixture = Fixture()
        val ancestry = mutableListOf(fixture.owner.parentSessionId, SessionId("child"))
        val row = ChildAgentSummary(ChildAgentKey(fixture.owner.scope, ancestry.last()), ancestry,
            ChildAgentStatus.Unknown, canInteract = true, historyAvailability = ChildHistoryAvailability.Available)
        val rows = mutableListOf(row)
        val entries = mutableListOf(ChildTranscriptEntry("real", ChildTranscriptRole.Assistant, "text"))
        val repository = ReadOnlyThreadPanelsRepository(
            { PanelLoad.Ready(fixture.owner.scope, SubagentOverview(fixture.owner.scope, rows)) },
            { _, key -> PanelLoad.Ready(key.scope, ChildAgentDetail(key, entries, canInteract = true)) },
        )
        val overview = repository.readOverview(fixture.owner) as PanelLoad.Ready
        val detail = repository.readDetail(fixture.owner, row.key) as PanelLoad.Ready
        rows.clear()
        ancestry.clear()
        entries.clear()
        assertEquals(1, overview.value.children.size)
        assertEquals(2, overview.value.children.single().verifiedAncestorPath.size)
        assertEquals(1, detail.value.entries.size)
        assertFalse(overview.value.children.single().canInteract)
        assertFalse(detail.value.canInteract)
    }

    @Test
    fun typedHistoryPreservesEveryFieldAndRecursivelySnapshotsBothReadBoundaries() = runTest {
        val fixture = Fixture()
        val child = ChildAgentKey(fixture.owner.scope, SessionId("child"))
        val nested = mutableListOf<MessageContent>(TextContent("nested error\nraw tail"))
        val results = mutableListOf<MessageContent>(TextContent("outer"),
            ToolResultContent("nested-call", nested, isError = true))
        val replayArray = mutableListOf<JsonElement>(JsonPrimitive("replay-item"))
        val replayMap = mutableMapOf<String, JsonElement>("items" to JsonArray(replayArray))
        val assistantContent = mutableListOf<MessageContent>(ReasoningContent("raw reasoning"),
            ToolCallContent("call-id", "read-file", "{\"path\":\"a\"}"), TextContent("answer"))
        val messages = mutableListOf(
            Message("human", MessageRole.USER, listOf(TextContent("question")), UserMessageSource),
            Message("assistant", MessageRole.ASSISTANT, assistantContent,
                ModelMessageSource("actual-provider", "actual-model", JsonObject(replayMap))),
            Message("tool", MessageRole.USER, listOf(ToolResultContent("call-id", results)), ToolMessageSource("call-id")),
            Message("plugin", MessageRole.USER, listOf(TextContent("plugin text")), PluginMessageSource("actual-plugin")),
        )
        fixture.history = ChildHistoryResult.Available(fixture.owner.parentSessionId, child.sessionId,
            listOf(fixture.owner.parentSessionId, child.sessionId), messages)
        val serializer = ListSerializer(Message.serializer())
        val expected = Json.encodeToString(serializer, messages)
        val connected = (ConnectionThreadPanelsRepository().readDetail(fixture.owner, child) as PanelLoad.Ready).value
        val external = connected.copy(typedMessages = messages, canInteract = true)
        val boundary = ReadOnlyThreadPanelsRepository(
            { PanelLoad.Unavailable(it.scope, PanelUnavailableReason.ChildrenServiceMissing) },
            { _, _ -> PanelLoad.Ready(child.scope, external) },
        )
        val copied = (boundary.readDetail(fixture.owner, child) as PanelLoad.Ready).value
        assertEquals(fixture.owner.parentSessionId to child.sessionId, fixture.historyRequest)
        for (detail in listOf(connected, copied)) {
            val typed = assertNotNull(detail.typedMessages)
            assertEquals(messages.map { it.id }, typed.map { it.id })
            assertEquals(messages.map { it.role }, typed.map { it.role })
            assertEquals(messages.map { it.source }, typed.map { it.source })
            assertEquals(assistantContent, typed[1].content)
            val source = typed[1].source as ModelMessageSource
            assertEquals("actual-provider", source.provider); assertEquals("actual-model", source.model)
            assertEquals(JsonObject(replayMap), source.replayState)
            val call = typed[1].content[1] as ToolCallContent
            assertEquals("call-id", call.id); assertEquals("read-file", call.name)
            assertEquals("{\"path\":\"a\"}", call.arguments)
            val outer = typed[2].content.single() as ToolResultContent
            assertEquals("call-id", outer.toolCallId); assertFalse(outer.isError)
            val inner = outer.content[1] as ToolResultContent
            assertEquals("nested-call", inner.toolCallId); assertTrue(inner.isError)
            assertEquals(nested, inner.content)
            assertFalse(detail.canInteract)
        }
        messages.clear(); assistantContent.clear(); results.clear(); nested.clear()
        replayArray.clear(); replayMap.clear()
        for (detail in listOf(connected, copied)) {
            assertEquals(expected, Json.encodeToString(serializer, assertNotNull(detail.typedMessages)),
                "All external message/content/result/replay collections must be detached")
            assertEquals(listOf("human", "assistant", "tool", "plugin"), detail.entries.map { it.id })
        }
    }

    @Test
    fun typedContractRejectsBlankDuplicateAndOutOfOrderMessageIds() {
        val child = ChildAgentKey(Fixture().owner.scope, SessionId("child"))
        val first = Message("first", MessageRole.USER, emptyList(), UserMessageSource)
        val second = first.copy(id = "second")
        val entries = listOf(ChildTranscriptEntry("first", ChildTranscriptRole.User, ""),
            ChildTranscriptEntry("second", ChildTranscriptRole.User, ""))
        assertFailsWith<IllegalArgumentException> { ChildAgentDetail(child, entries, false, listOf(first.copy(id = ""), second)) }
        assertFailsWith<IllegalArgumentException> { ChildAgentDetail(child, entries, false, listOf(first, first)) }
        assertFailsWith<IllegalArgumentException> { ChildAgentDetail(child, entries, false, listOf(second, first)) }
        assertFailsWith<IllegalArgumentException> { ChildAgentDetail(child, entries, false, emptyList()) }
        assertNull(ChildAgentDetail(child, entries, false).typedMessages, "Legacy callers remain nullable")
        assertEquals(emptyList(), ChildAgentDetail(child, emptyList(), false, emptyList()).typedMessages)
    }

    @Test
    fun typedSnapshotStillRejectsForeignScopeChildAndInvalidLineage() = runTest {
        val fixture = Fixture()
        val child = ChildAgentKey(fixture.owner.scope, SessionId("child"))
        val message = Message("real", MessageRole.USER, listOf(TextContent("raw")), UserMessageSource)
        val detail = ChildAgentDetail(child, listOf(ChildTranscriptEntry("real", ChildTranscriptRole.User, "raw")), true, listOf(message))
        val foreign = child.copy(scope = child.scope.copy(hostId = HostId("foreign")))
        val boundary = ReadOnlyThreadPanelsRepository(
            { PanelLoad.Unavailable(it.scope, PanelUnavailableReason.ChildrenServiceMissing) },
            { _, _ -> PanelLoad.Ready(foreign.scope, detail) },
        )
        assertFailsWith<IllegalArgumentException> { boundary.readDetail(fixture.owner, child) }
        val wrongChild = ReadOnlyThreadPanelsRepository(
            { PanelLoad.Unavailable(it.scope, PanelUnavailableReason.ChildrenServiceMissing) },
            { _, _ -> PanelLoad.Ready(child.scope, detail.copy(key = child.copy(sessionId = SessionId("other")))) },
        )
        assertFailsWith<IllegalArgumentException> { wrongChild.readDetail(fixture.owner, child) }
        val ancestry = mutableListOf(fixture.owner.parentSessionId, child.sessionId)
        fixture.history = ChildHistoryResult.Available(fixture.owner.parentSessionId, child.sessionId, ancestry, listOf(message))
        ancestry[0] = SessionId("wrong-parent")
        assertFailsWith<IllegalArgumentException> { ConnectionThreadPanelsRepository().readDetail(fixture.owner, child) }
        ancestry[0] = fixture.owner.parentSessionId
        ancestry.add(1, child.sessionId)
        assertFailsWith<IllegalArgumentException> { ConnectionThreadPanelsRepository().readDetail(fixture.owner, child) }
    }

    private fun descendant(fixture: Fixture, id: String, status: DescendantStatus): SessionDescendant =
        SessionDescendant(SessionId(id), fixture.owner.parentSessionId,
            listOf(fixture.owner.parentSessionId, SessionId(id)), status)

    /** Every legacy operation fails: these tests prove only the two new read APIs are consumed. */
    private class Fixture {
        private val parent = SessionId("parent")
        var descendants: SessionDescendantsResult = SessionDescendantsResult.Unavailable(parent)
        var history: ChildHistoryResult = ChildHistoryResult.Unavailable(parent, SessionId("child"))
        var overviewParent: SessionId? = null
        var historyRequest: Pair<SessionId, SessionId>? = null
        private val connection = object : Connection {
            override val host: HostApi = object : HostApi {
                override suspend fun describe() = error("Parent verifies Host")
            }
            override val session: SessionApi = object : SessionApi {
                override suspend fun descendants(parent: SessionId): SessionDescendantsResult {
                    overviewParent = parent
                    return this@Fixture.descendants
                }
                override suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult {
                    historyRequest = parent to child
                    return this@Fixture.history
                }
                override suspend fun list(): List<SessionSummary> = error("No unrelated sessions")
                override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
                    error("No Agent creation")
                override suspend fun history(id: SessionId): List<Message> = error("No unrestricted history")
                override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("No composer")
                override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("No mutations")
            }
        }
        val owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("host"), 1, null), parent)
    }
}

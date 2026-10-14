package im.hikaru.harness.desktop.connection

import im.hikaru.harness.agent.*
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.ChildHistoryRequest
import im.hikaru.harness.api.gateway.session.SessionChildHistoryEndpoint
import im.hikaru.harness.api.gateway.session.SessionDescendantsEndpoint
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.connection.ChildHistoryResult
import im.hikaru.harness.client.connection.DescendantStatus
import im.hikaru.harness.client.connection.SessionDescendantsResult
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.ToolResultContent
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.SessionApiPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class SessionDescendantsConnectionTest {
    @Test
    fun localAndRelayReadTheSameVerifiedLiveChildrenAndActualTranscript() = runTest {
        val fixture = fixture()
        val json = Json
        try {
            fixture.create("root")
            val empty = assertIs<SessionDescendantsResult.Available>(fixture.connection.session.descendants(ClientSessionId("root")))
            assertTrue(empty.children.isEmpty())
            assertFalse(empty.complete)
            val child = fixture.create("child", "root", SessionOrigin.SUBAGENT)
            fixture.create("ordinary-fork", "root")
            val session = child.agent.session
            val call = CallId("verified-call")
            session.append(SessionEventKeys.AssistantMessage, AssistantMessageEvent(
                turn = 1, step = 1, message = createAssistantMessage(
                    listOf(ReasoningBlock("actual reasoning"), ToolCallBlock(call, "lookup", "{}")),
                    provider = "test-provider", model = "test-model",
                ),
            ))
            session.append(SessionEventKeys.UserMessage, UserMessageEvent(
                turn = 1, message = createToolResultMessage(call, listOf(TextBlock("actual result")), false),
            ))
            val parent = ClientSessionId("root")
            val childId = ClientSessionId("child")
            val overview = assertIs<SessionDescendantsResult.Available>(fixture.connection.session.descendants(parent))
            assertEquals(listOf(childId), overview.children.map { it.child })
            assertEquals(DescendantStatus.UNKNOWN, overview.children.single().status)
            assertEquals("test-provider", overview.children.single().provider)
            assertEquals("test-model", overview.children.single().model)
            assertEquals(session.header.createdAt, overview.children.single().startedAtEpochMillis)
            val history = assertIs<ChildHistoryResult.Available>(fixture.connection.session.readChildHistory(parent, childId))
            assertEquals(session.deriveMessages().map(::toClientMessage), history.messages)
            assertIs<ReasoningContent>(history.messages[0].content[0])
            assertIs<ToolCallContent>(history.messages[0].content[1])
            assertIs<ToolResultContent>(history.messages[1].content.single())
            val wireOverview = invokeSessionDescendantsRead(fixture.gateway, SessionDescendantsEndpoint.method,
                json.encodeToJsonElement(ClientSessionId.serializer(), parent), json)
            assertTrue(wireOverview.isSuccess)
            assertEquals(overview, json.decodeFromJsonElement(SessionDescendantsResult.serializer(), requireNotNull(wireOverview.data)))
            val wireHistory = invokeSessionDescendantsRead(fixture.gateway, SessionChildHistoryEndpoint.method,
                json.encodeToJsonElement(ChildHistoryRequest.serializer(), ChildHistoryRequest(SessionId("root"), SessionId("child"))), json)
            assertTrue(wireHistory.isSuccess)
            assertEquals(history, json.decodeFromJsonElement(ChildHistoryResult.serializer(), requireNotNull(wireHistory.data)))
            // An ordinary client fork is not a subagent, even though it shares parentSession.
            assertEquals(422, assertFailsWith<LocalConnectionException> {
                fixture.connection.session.readChildHistory(parent, ClientSessionId("ordinary-fork"))
            }.code)
            child.dispose()
            assertEquals(422, assertFailsWith<LocalConnectionException> {
                fixture.connection.session.readChildHistory(parent, childId)
            }.code)
            assertEquals(404, assertFailsWith<LocalConnectionException> {
                fixture.connection.session.descendants(ClientSessionId("no-longer-live"))
            }.code)
        } finally {
            fixture.runtime.context.dispose()
        }
    }

    @Test
    fun onlyMissingEndpointOrServiceReturnsUnavailableLocallyAndOverRelay() = runTest {
        val gateway = ApiGateway()
        val connection = LocalConnection(gateway)
        val parent = ClientSessionId("parent")
        val child = ClientSessionId("child")
        assertEquals(SessionDescendantsResult.Unavailable(parent), connection.session.descendants(parent))
        assertEquals(ChildHistoryResult.Unavailable(parent, child), connection.session.readChildHistory(parent, child))
        val owner = Context()
        val registration = gateway.registerSessionApi(owner)
        try {
            assertEquals(SessionDescendantsResult.Unavailable(parent), connection.session.descendants(parent))
            assertEquals(ChildHistoryResult.Unavailable(parent, child), connection.session.readChildHistory(parent, child))
            val json = Json
            val result = invokeSessionDescendantsRead(gateway, SessionDescendantsEndpoint.method,
                json.encodeToJsonElement(ClientSessionId.serializer(), parent), json)
            assertTrue(result.isSuccess)
            assertEquals(SessionDescendantsResult.Unavailable(parent),
                json.decodeFromJsonElement(SessionDescendantsResult.serializer(), requireNotNull(result.data)))
            val childResult = invokeSessionDescendantsRead(gateway, SessionChildHistoryEndpoint.method,
                json.encodeToJsonElement(ChildHistoryRequest.serializer(), ChildHistoryRequest(SessionId("parent"), SessionId("child"))), json)
            assertTrue(childResult.isSuccess)
            assertEquals(ChildHistoryResult.Unavailable(parent, child),
                json.decodeFromJsonElement(ChildHistoryResult.serializer(), requireNotNull(childResult.data)))
            registration.dispose()
            assertEquals(SessionDescendantsResult.Unavailable(parent), connection.session.descendants(parent))
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun relayRejectsMalformedLineageAndPreservesCancellation() = runTest {
        val fixture = fixture()
        try {
            fixture.create("root")
            fixture.create("ordinary-fork", "root")
            val json = Json
            assertEquals(422, invokeSessionDescendantsRead(fixture.gateway, SessionChildHistoryEndpoint.method, JsonObject(emptyMap()), json).code)
            assertEquals(422, invokeSessionDescendantsRead(fixture.gateway, SessionChildHistoryEndpoint.method, null, json).code)
            val result = invokeSessionDescendantsRead(fixture.gateway, SessionChildHistoryEndpoint.method,
                json.encodeToJsonElement(ChildHistoryRequest.serializer(), ChildHistoryRequest(SessionId("root"), SessionId("ordinary-fork"))), json)
            assertEquals(422, result.code)
            assertNull(result.data)
            val isolatedGateway = ApiGateway()
            val cancelled = CancellationException("cancel read")
            isolatedGateway.register(fixture.runtime.context, SessionDescendantsEndpoint) { throw cancelled }
            assertSame(cancelled, assertFailsWith<CancellationException> {
                invokeSessionDescendantsRead(isolatedGateway, SessionDescendantsEndpoint.method,
                    json.encodeToJsonElement(SessionId.serializer(), SessionId("root")), json)
            })
        } finally {
            fixture.runtime.context.dispose()
        }
    }

    private suspend fun fixture(): Fixture {
        val runtime = Runtime()
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.context.agents.registerFactory(AgentFactory { request ->
            ReadOnlyTestAgent(request.id, request.options, request.session, request.inbox, request.context)
        })
        runtime.install(SessionApiPlugin())
        val gateway = ApiGateway()
        gateway.registerSessionApi(runtime.context)
        return Fixture(runtime, gateway)
    }

    private class Fixture(val runtime: Runtime, val gateway: ApiGateway) {
        val connection = LocalConnection(gateway)
        suspend fun create(name: String, parent: String? = null, origin: SessionOrigin? = null): AgentHandle =
            runtime.context.agents.create(AgentId(name), AgentOptions(provider = "test-provider", model = "test-model"),
                CreateSessionOptions(parentSession = parent?.let(::SessionId), origin = origin))
    }

    private class ReadOnlyTestAgent(
        override val id: AgentId,
        override val options: AgentOptions,
        override val session: Session,
        override val inbox: Inbox,
        override val context: Context,
    ) : Agent {
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String =
            inbox.insert(message, target, wakeup)
    }
}

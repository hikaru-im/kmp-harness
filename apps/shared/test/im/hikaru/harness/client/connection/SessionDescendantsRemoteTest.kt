package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.harness.relay.RequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.*

class SessionDescendantsRemoteTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val parent = SessionId("parent")
    private val child = SessionId("child")

    @Test
    fun typedReadsCarryExactParentChildAndPreserveRealMessagesAndUnknownStatus() = runTest {
        val descendant = SessionDescendant(child, parent, listOf(parent, child), DescendantStatus.UNKNOWN,
            provider = "real-provider", model = "real-model", reasoningEffort = "high")
        val overview: SessionDescendantsResult = SessionDescendantsResult.Available(parent, listOf(descendant))
        val message = userMessage("real transcript")
        val history: ChildHistoryResult = ChildHistoryResult.Available(parent, child, listOf(parent, child), listOf(message))
        val calls = mutableListOf<String>()
        val reader = RemoteSessionDescendants(json) { method, payload ->
            calls += method
            when (method) {
                "session.descendants" -> {
                    assertEquals(parent, json.decodeFromJsonElement(SessionId.serializer(), assertNotNull(payload)))
                    response(json.encodeToJsonElement(SessionDescendantsResult.serializer(), overview))
                }
                "session.child.history" -> {
                    assertEquals(WireChildHistoryRequest(parent, child), json.decodeFromJsonElement(WireChildHistoryRequest.serializer(), assertNotNull(payload)))
                    response(json.encodeToJsonElement(ChildHistoryResult.serializer(), history))
                }
                else -> error("Unexpected endpoint")
            }
        }
        assertEquals(overview, reader.descendants(parent))
        assertEquals(history, reader.readChildHistory(parent, child))
        assertEquals(listOf("session.descendants", "session.child.history"), calls)
    }

    @Test
    fun onlyAbsentEndpointMeansUnavailableAndSuccessfulEmptyIsNotUnavailable() = runTest {
        for (message in listOf("Harness endpoint unavailable", "Harness API endpoint 'session.descendants' 未注册")) {
            val reader = RemoteSessionDescendants(json) { _, _ -> response(null, 404, message) }
            assertEquals(SessionDescendantsResult.Unavailable(parent), reader.descendants(parent))
        }
        val oldHost = RemoteSessionDescendants(json) { _, _ -> response(null, 404, "Harness endpoint unavailable") }
        assertEquals(ChildHistoryResult.Unavailable(parent, child), oldHost.readChildHistory(parent, child))
        val empty: SessionDescendantsResult = SessionDescendantsResult.Available(parent, emptyList())
        val current = RemoteSessionDescendants(json) { _, _ -> response(json.encodeToJsonElement(SessionDescendantsResult.serializer(), empty)) }
        assertEquals(empty, current.descendants(parent))
        for (code in listOf(403, 404, 422, 500)) {
            val reader = RemoteSessionDescendants(json) { _, _ -> response(null, code, "Session operation unavailable") }
            assertEquals(code, assertFailsWith<RemoteConnectionException> { reader.readChildHistory(parent, child) }.code)
        }
        val missingData = RemoteSessionDescendants(json) { _, _ -> response(null) }
        assertFailsWith<RemoteConnectionException> { missingData.descendants(parent) }
    }

    @Test
    fun mismatchedPayloadScopesAndCancellationCannotPublish() = runTest {
        val wrong: SessionDescendantsResult = SessionDescendantsResult.Available(SessionId("old-parent"), emptyList())
        val wrongParent = RemoteSessionDescendants(json) { _, _ -> response(json.encodeToJsonElement(SessionDescendantsResult.serializer(), wrong)) }
        assertFailsWith<IllegalArgumentException> { wrongParent.descendants(parent) }
        val wrongChild: ChildHistoryResult = ChildHistoryResult.Available(parent, SessionId("old-child"), listOf(parent, SessionId("old-child")), emptyList())
        val history = RemoteSessionDescendants(json) { _, _ -> response(json.encodeToJsonElement(ChildHistoryResult.serializer(), wrongChild)) }
        assertFailsWith<IllegalArgumentException> { history.readChildHistory(parent, child) }
        val cancellation = CancellationException("cancelled")
        val cancelled = RemoteSessionDescendants(json) { _, _ -> throw cancellation }
        assertSame(cancellation, assertFailsWith<CancellationException> { cancelled.descendants(parent) })
    }

    @Test
    fun legacySessionFakeNeedsNoNewImplementation() = runTest {
        val legacy = object : SessionApi {
            override suspend fun list(): List<SessionSummary> = error("Unexpected list")
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
            override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
        }
        assertEquals(SessionDescendantsResult.Unavailable(parent), legacy.descendants(parent))
        assertEquals(ChildHistoryResult.Unavailable(parent, child), legacy.readChildHistory(parent, child))
    }

    private fun response(data: JsonElement?, code: Int = ApiResult.SUCCESS_CODE, message: String = ""): RelayResponse =
        RelayResponse(requestId = RequestId("read-1"), hostId = HostId("host"), result = ApiResult(code, message, data))
}

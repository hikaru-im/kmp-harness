package im.hikaru.harness.api.gateway.session

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SessionDescendantsGatewayTest {
    @Test
    fun missingServiceAndCompatibleDefaultAreUnavailableWhileSuccessfulEmptyIsAvailable() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        val registration = gateway.registerSessionApi(owner)
        val parent = SessionId("parent")
        val child = SessionId("child")
        try {
            assertEquals(SessionDescendantsResult.Unavailable(parent), gateway.invoke(SessionDescendantsEndpoint, parent).data)
            assertEquals(ChildHistoryResult.Unavailable(parent, child), gateway.invoke(SessionChildHistoryEndpoint, ChildHistoryRequest(parent, child)).data)
            val compatible = owner.provide(SessionApiKey, LegacyApi())
            assertEquals(SessionDescendantsResult.Unavailable(parent), gateway.invoke(SessionDescendantsEndpoint, parent).data)
            compatible.dispose()
            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun descendants(parent: SessionId): SessionDescendantsResult =
                    SessionDescendantsResult.Available(parent, emptyList())
            })
            val result = gateway.invoke(SessionDescendantsEndpoint, parent)
            assertTrue(result.isSuccess)
            val available = assertIs<SessionDescendantsResult.Available>(result.data)
            assertTrue(available.children.isEmpty())
            assertFalse(available.complete)
            registration.dispose()
            assertFalse(SessionDescendantsEndpoint.method in gateway.methods())
            assertFalse(SessionChildHistoryEndpoint.method in gateway.methods())
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun lineageRejectionAndStaleIdentityStayErrorsAndCancellationPropagates() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        var failure: Throwable = IllegalArgumentException("private invalid lineage")
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult = throw failure
        })
        try {
            val request = ChildHistoryRequest(SessionId("parent"), SessionId("child"))
            val rejected = gateway.invoke(SessionChildHistoryEndpoint, request)
            assertEquals(422, rejected.code)
            assertNull(rejected.data)
            assertFalse(rejected.msg.orEmpty().contains("private"))
            failure = IllegalStateException("private old parent")
            assertEquals(404, gateway.invoke(SessionChildHistoryEndpoint, request).code)
            failure = CancellationException("cancelled")
            assertSame(failure, assertFailsWith<CancellationException> { gateway.invoke(SessionChildHistoryEndpoint, request) })
        } finally {
            owner.dispose()
        }
    }

    private open class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected unscoped history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }
}

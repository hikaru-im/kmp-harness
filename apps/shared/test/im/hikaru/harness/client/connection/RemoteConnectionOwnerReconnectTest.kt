package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.AppSession
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteConnectionOwnerReconnectTest {
    @Test
    fun reconnectLoopIsBoundedAndEndsDisconnected() = runBlocking {
        val payload =
            Json.encodeToString(
                ApiResult.serializer(ListSerializer(HostDescription.serializer())),
                ApiResult(
                    ApiResult.SUCCESS_CODE,
                    "",
                    listOf(
                        HostDescription(
                            protocolVersion = ProtocolVersion.Current,
                            hostId = HostId("desktop-local"),
                            displayName = "Desktop",
                        ),
                    ),
                ),
            )
        val engine =
            MockEngine {
                respond(
                    content = payload,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; explicitNulls = false })
                }
                install(WebSockets)
            }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner =
            RemoteConnectionOwner(
                client = client,
                closeClient = { client.close() },
                scope = scope,
                policy = ReconnectPolicy(initialDelayMillis = 1L, maxDelayMillis = 2L, maxAttempts = 3),
            )
        try {
            owner.start(session(), "test-client")

            val status = withTimeout(20_000L) { owner.status.first { it == RemoteConnectionStatus.DISCONNECTED } }

            assertEquals(RemoteConnectionStatus.DISCONNECTED, status)
            assertTrue(engine.requestHistory.size >= 3, "expected repeated discovery attempts before giving up")
        } finally {
            owner.close()
        }
    }

    @Test
    fun subscriptionsAreRememberedForReconnectAndDroppedOnLogout() = runBlocking {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) })
        val owner =
            RemoteConnectionOwner(
                client = client,
                closeClient = { client.close() },
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
        try {
            owner.subscribe(StreamId("session-1"))
            owner.subscribe(StreamId("session-2"))
            assertEquals(
                setOf(StreamId("session-1"), StreamId("session-2")),
                owner.subscribedStreams(),
                "a subscription must be remembered so a reconnect can rebuild it",
            )

            owner.unsubscribe(StreamId("session-1"))
            assertEquals(setOf(StreamId("session-2")), owner.subscribedStreams())

            owner.stop()
            assertTrue(owner.subscribedStreams().isEmpty(), "logging out must drop the subscriptions")
        } finally {
            owner.close()
        }
    }
    private fun session() =
        AppSession(
            identity = MemberIdentity(target = BackendTenant("https://example.com", 1L), userId = 7L),
            accessToken = "token",
            refreshToken = null,
            expiresAt = null,
        )
}
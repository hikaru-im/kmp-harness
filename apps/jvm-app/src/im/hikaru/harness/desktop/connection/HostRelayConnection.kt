package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.handshake.HandshakeRequest
import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.relay.*
import im.hikaru.contracts.websocket.WebSocketMessage
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.*
import im.hikaru.harness.client.connection.handshake.HandshakeNegotiationResult
import im.hikaru.harness.client.connection.handshake.HandshakeNegotiator
import im.hikaru.harness.llm.Message
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.SessionPromptReceipt
import im.hikaru.harness.session.api.SessionSummary
import im.hikaru.harness.agent.AgentOptions
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Desktop-side outbound Host connection. Mobile never creates this type. */
public class HostRelayConnection private constructor(
    private val socket: WebSocketSession,
    private val gateway: ApiGateway,
    private val description: HostDescription,
    private val events: Flow<RelayEvent>,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val subscriptions = HostStreamSubscriptions()
    private val closed = CompletableDeferred<Unit>()
    private val reader = scope.launch { readLoop() }
    private val publisher = scope.launch { publishEvents() }

    /**
     * Publishes local Session events for the streams a client subscribed to. The RuoYi Relay applies the
     * same filter again, so a Host defect cannot widen the audience on its own.
     */
    private suspend fun publishEvents() {
        try {
            events.collect { event ->
                if (!subscriptions.accepts(event)) return@collect
                val scoped = event.copy(hostId = description.hostId)
                val envelope =
                    WebSocketMessage(
                        HarnessWebSocketMessageTypes.RelayEvent,
                        json.encodeToString(RelayEvent.serializer(), scoped),
                    )
                socket.send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(), envelope)))
            }
        } catch (_: CancellationException) {
            // Closing the connection cancels the publisher.
        }
    }

    private suspend fun readLoop() {
        try {
            for (frame in socket.incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val outer = runCatching { json.decodeFromString(WebSocketMessage.serializer(), text) }.getOrNull() ?: continue
                if (outer.type != HarnessWebSocketMessageTypes.RelayRequest) continue
                val request = runCatching { json.decodeFromString(RelayRequest.serializer(), outer.content) }.getOrNull() ?: continue
                val result = invoke(request)
                val response = RelayResponse(request.requestId, request.hostId, request.correlationId, result)
                socket.send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(), WebSocketMessage(HarnessWebSocketMessageTypes.RelayResponse, json.encodeToString(RelayResponse.serializer(), response)))))
            }
        } catch (_: CancellationException) {
        } finally {
            closed.complete(Unit)
        }
    }

    private suspend fun invoke(request: RelayRequest): ApiResult<JsonElement> {
        if (request.hostId != description.hostId || request.method in FORBIDDEN || request.method.startsWith("file.")) {
            return ApiResult(403, "Remote method is forbidden")
        }
        return try {
            when (request.method) {
                HostDescribeEndpoint.method -> encode(gateway.invoke(HostDescribeEndpoint, Unit))
                "host.handshake" -> {
                    val input = json.decodeFromJsonElement(
                        HandshakeRequest.serializer(),
                        request.payload ?: error("missing payload"),
                    )
                    when (val result = HandshakeNegotiator.negotiate(input, description)) {
                        is HandshakeNegotiationResult.Accepted -> ApiResult(
                            code = ApiResult.SUCCESS_CODE,
                            msg = "",
                            data = json.encodeToJsonElement(HandshakeResponse.serializer(), result.response),
                        )
                        is HandshakeNegotiationResult.Rejected -> ApiResult(
                            code = 409,
                            msg = result.rejection.message,
                        )
                    }
                }
                SessionListEndpoint.method -> encode(gateway.invoke(SessionListEndpoint, Unit))
                SessionCreateEndpoint.method -> {
                    val input = json.decodeFromJsonElement(SessionCreateRequest.serializer(), request.payload ?: error("missing payload"))
                    encode(gateway.invoke(SessionCreateEndpoint, input))
                }
                SessionHistoryEndpoint.method -> {
                    val input = json.decodeFromJsonElement(SessionId.serializer(), request.payload ?: error("missing payload"))
                    encode(gateway.invoke(SessionHistoryEndpoint, input))
                }
                SessionPromptEndpoint.method -> {
                    val input = json.decodeFromJsonElement(SessionPromptRequest.serializer(), request.payload ?: error("missing payload"))
                    val prompt = gateway.invoke(SessionPromptEndpoint, input)
                    ApiResult(
                        code = prompt.code,
                        msg = prompt.msg,
                        data = prompt.data?.receipt?.let { json.encodeToJsonElement(SessionPromptReceipt.serializer(), it) },
                    )
                }
                SessionCancelEndpoint.method -> {
                    val input = json.decodeFromJsonElement(SessionCancelRequest.serializer(), request.payload ?: error("missing payload"))
                    encode(gateway.invoke(SessionCancelEndpoint, input))
                }
                SUBSCRIBE_METHOD -> {
                    val stream = (request.payload as? JsonPrimitive)?.contentOrNull
                    if (stream.isNullOrBlank()) {
                        ApiResult(400, "Missing stream id")
                    } else {
                        subscriptions.subscribe(stream)
                        ApiResult(ApiResult.SUCCESS_CODE, "", JsonPrimitive(stream))
                    }
                }
                UNSUBSCRIBE_METHOD -> {
                    val stream = (request.payload as? JsonPrimitive)?.contentOrNull
                    if (stream.isNullOrBlank()) {
                        ApiResult(400, "Missing stream id")
                    } else {
                        subscriptions.unsubscribe(stream)
                        ApiResult(ApiResult.SUCCESS_CODE, "", JsonPrimitive(stream))
                    }
                }
                else -> ApiResult(404, "Harness endpoint unavailable")
            }
        } catch (_: CancellationException) { throw CancellationException() }
        catch (_: Throwable) { ApiResult(500, "Harness endpoint failed") }
    }

    private inline fun <reified T : Any> encode(result: ApiResult<T>): ApiResult<JsonElement> =
        ApiResult(result.code, result.msg, result.data?.let { json.encodeToJsonElement(kotlinx.serialization.serializer(), it) })

    override fun close() {
        if (closed.isCompleted) return
        reader.cancel()
        publisher.cancel()
        subscriptions.clear()
        socket.cancel()
        closed.complete(Unit)
    }

    /** Suspends until this connection reaches a terminal state, so the relay supervisor can react. */
    public suspend fun awaitClosed() {
        closed.await()
    }

    public companion object {
        private val FORBIDDEN = setOf("credentials.set", "credentials.unset", "settings.secret.set", "llm.models.discover-temporary-key")
        private const val SUBSCRIBE_METHOD = "session.events.subscribe"
        private const val UNSUBSCRIBE_METHOD = "session.events.unsubscribe"
        public suspend fun connect(client: HttpClient, baseUrl: String, token: String, tenantId: Long, host: HostDescription, gateway: ApiGateway, events: Flow<RelayEvent>, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)): HostRelayConnection {
            val socket = client.webSocketSession {
                url(baseUrl.trimEnd('/') + "/ws")
                header(HttpHeaders.Authorization, "Bearer $token")
                header("tenant-id", tenantId)
            }
            val connection = HostRelayConnection(socket, gateway, host, events, scope)
            socket.send(Frame.Text(connection.json.encodeToString(WebSocketMessage.serializer(), WebSocketMessage(HarnessWebSocketMessageTypes.HostRegister, connection.json.encodeToString(HostRegistrationRequest.serializer(), HostRegistrationRequest(host))))))
            return connection
        }
    }
}

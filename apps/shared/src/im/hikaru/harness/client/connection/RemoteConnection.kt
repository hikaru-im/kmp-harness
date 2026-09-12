package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.handshake.HandshakeRequest
import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.identity.ClientDescription
import im.hikaru.contracts.harness.identity.ClientId
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.*
import im.hikaru.contracts.websocket.WebSocketMessage
import im.hikaru.harness.client.connection.handshake.HandshakeNegotiationResult
import im.hikaru.harness.client.connection.handshake.HandshakeNegotiator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonPrimitive

/**
 * Authenticated RuoYi WebSocket connection used by Android, iOS and Desktop clients.
 * It never retries an execution command whose result is uncertain.
 */
public class RemoteConnection private constructor(
    private val socket: WebSocketSession,
    private val hostDescription: HostDescription,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long,
) : Connection, AutoCloseable {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val nextRequest = AtomicRequestId()
    private val pending = mutableMapOf<String, CompletableDeferred<RelayResponse>>()
    private val pendingMutex = Mutex()
    private val sequences = SequenceTracker()
    private val recoveries = mutableSetOf<String>()
    private val recoveryMutex = Mutex()
    private val closed = CompletableDeferred<Unit>()
    private val mutableEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 64)
    private val mutableSequenceGaps = MutableSharedFlow<SequenceGap>(extraBufferCapacity = 16)
    private val mutableHistoryRecoveries = MutableSharedFlow<HistoryRecovery>(extraBufferCapacity = 16)
    public val events: Flow<RelayEvent> = mutableEvents.asSharedFlow()
    public val sequenceGaps: Flow<SequenceGap> = mutableSequenceGaps.asSharedFlow()

    /** History snapshots pulled after a detected sequence gap, so the caller can reconcile its view. */
    public val historyRecoveries: Flow<HistoryRecovery> = mutableHistoryRecoveries.asSharedFlow()
    private var reader: Job = scope.launch { readLoop() }

    override val host: HostApi = object : HostApi {
        override suspend fun describe(): ApiResult<HostDescription> {
            val response = invoke("host.describe", null)
            return ApiResult(
                code = response.result.code,
                msg = response.result.msg,
                data = response.result.data?.let {
                    json.decodeFromJsonElement(HostDescription.serializer(), it)
                },
            )
        }
    }

    override val session: SessionApi = object : SessionApi {
        override suspend fun list(): List<SessionSummary> =
            invoke("session.list", null).decodeResult<List<SessionSummary>>()

        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
            val request = WireCreateSessionRequest(id, options, agentOptions)
            return invoke("session.create", json.encodeToJsonElement(WireCreateSessionRequest.serializer(), request)).decodeResult()
        }

        override suspend fun history(id: SessionId): List<Message> =
            invoke("session.history", json.encodeToJsonElement(SessionId.serializer(), id)).decodeResult()

        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
            val request = WirePromptRequest(id, message)
            val receipt: SessionPromptReceipt = invoke("session.prompt", json.encodeToJsonElement(WirePromptRequest.serializer(), request)).decodeResult()
            return object : SessionPrompt {
                override val receipt = receipt
                override suspend fun awaitIdle() = Unit
            }
        }

        override suspend fun cancel(id: SessionId, keepInbox: Boolean) {
            invoke("session.cancel", json.encodeToJsonElement(WireCancelRequest.serializer(), WireCancelRequest(id, keepInbox))).decodeResult<Unit>()
        }
    }

    public suspend fun subscribe(streamId: StreamId) {
        invoke("session.events.subscribe", JsonPrimitive(streamId.value)).decodeResult<String>()
    }

    public suspend fun unsubscribe(streamId: StreamId) {
        invoke("session.events.unsubscribe", JsonPrimitive(streamId.value)).decodeResult<String>()
        sequences.forget(streamId)
    }

    public suspend fun recoverHistory(id: SessionId): List<Message> = session.history(id)

    private suspend fun invoke(method: String, payload: JsonElement?): RelayResponse {
        require(method !in FORBIDDEN_METHODS && !method.startsWith("file.")) { "Remote method is forbidden" }
        val id = nextRequest.next()
        val deferred = CompletableDeferred<RelayResponse>()
        pendingMutex.withLock { pending[id.value] = deferred }
        try {
            val request = RelayRequest(id, hostDescription.hostId, method = method, payload = payload)
            socket.send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(), WebSocketMessage(HarnessWebSocketMessageTypes.RelayRequest, json.encodeToString(RelayRequest.serializer(), request)))))
            return withTimeout(timeoutMillis) { deferred.await() }.also { response ->
                if (response.hostId != hostDescription.hostId || response.requestId != id) throw RemoteConnectionException("Stale relay response")
            }
        } finally {
            pendingMutex.withLock { pending.remove(id.value) }
        }
    }

    private suspend fun readLoop() {
        try {
            for (frame in socket.incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val outer = runCatching { json.decodeFromString(WebSocketMessage.serializer(), text) }.getOrNull() ?: continue
                when (outer.type) {
                    HarnessWebSocketMessageTypes.RelayResponse -> {
                        val response = runCatching { json.decodeFromString(RelayResponse.serializer(), outer.content) }.getOrNull() ?: continue
                        pendingMutex.withLock { pending[response.requestId.value]?.complete(response) }
                    }
                    HarnessWebSocketMessageTypes.RelayEvent -> {
                        val event = runCatching { json.decodeFromString(RelayEvent.serializer(), outer.content) }.getOrNull() ?: continue
                        if (event.hostId != hostDescription.hostId) continue
                        when (val outcome = sequences.accept(event)) {
                            SequenceOutcome.Duplicate -> Unit
                            is SequenceOutcome.Gap -> {
                                mutableSequenceGaps.tryEmit(
                                    SequenceGap(event.streamId, outcome.expected, outcome.received),
                                )
                                mutableEvents.tryEmit(event)
                                recoverHistoryFor(event.streamId)
                            }

                            SequenceOutcome.Deliver -> mutableEvents.tryEmit(event)
                        }
                    }
                }
            }
        } finally {
            val error = RemoteConnectionException("Remote connection closed")
            pendingMutex.withLock { pending.values.forEach { it.completeExceptionally(error) }; pending.clear() }
            closed.complete(Unit)
        }
    }

    /** Suspends until this connection reaches a terminal state, so reconnect supervisors can react. */
    public suspend fun awaitClosed() {
        closed.await()
    }

    /**
     * Reconciles a stream after a detected gap. The gap stays observable even when recovery fails, and a
     * later event or reconnect retries; recovery never re-sends an execution command.
     */
    private suspend fun recoverHistoryFor(streamId: StreamId) {
        val key = streamId.value
        val started = recoveryMutex.withLock { recoveries.add(key) }
        if (!started) return
        scope.launch {
            try {
                val messages = session.history(SessionId(key))
                mutableHistoryRecoveries.tryEmit(HistoryRecovery(streamId, messages))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Leave the gap observable; the next gap or reconnect retries.
            } finally {
                recoveryMutex.withLock { recoveries.remove(key) }
            }
        }
    }

    override fun close() {
        reader.cancel()
        socket.cancel()
        closed.complete(Unit)
    }

    private inline fun <reified T> RelayResponse.decodeResult(): T {
        if (!result.isSuccess) throw RemoteConnectionException(result.msg ?: "Remote request rejected", result.code)
        val value = result.data ?: throw RemoteConnectionException("Remote response has no data", result.code)
        return json.decodeFromJsonElement(serializer(), value)
    }

    public companion object {
        private val FORBIDDEN_METHODS = setOf("credentials.set", "credentials.unset", "settings.secret.set", "llm.models.discover-temporary-key")

        public suspend fun connect(
            client: HttpClient,
            baseUrl: String,
            accessToken: String,
            tenantId: Long,
            host: HostDescription,
            clientId: String,
            timeoutMillis: Long = 15_000,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ): RemoteConnection {
            require(host.protocolVersion.major == ProtocolVersion.Current.major) { "Incompatible Harness protocol" }
            val target = baseUrl.trimEnd('/') + "/ws"
            val socket = client.webSocketSession {
                url(target)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("tenant-id", tenantId)
            }
            val connection = RemoteConnection(socket, host, scope, timeoutMillis)
            val handshake = HandshakeRequest(ClientDescription(ProtocolVersion.Current, ClientId(clientId), "Harness Client"))
            val described = connection.host.describe()
            val remoteHost = described.data ?: run {
                connection.close()
                throw RemoteConnectionException(described.msg ?: "Host handshake failed", described.code)
            }
            when (val negotiation = HandshakeNegotiator.negotiate(handshake, remoteHost)) {
                is HandshakeNegotiationResult.Accepted -> Unit
                is HandshakeNegotiationResult.Rejected -> {
                    connection.close()
                    throw RemoteConnectionException(negotiation.rejection.message)
                }
            }
            val remoteHandshake: HandshakeResponse = connection.run {
                invoke(
                    "host.handshake",
                    json.encodeToJsonElement(HandshakeRequest.serializer(), handshake),
                ).decodeResult()
            }
            require(remoteHandshake.host.hostId == host.hostId) { "Remote handshake Host mismatch" }
            require(remoteHandshake.negotiatedProtocolVersion.major == ProtocolVersion.Current.major) {
                "Remote handshake protocol is incompatible"
            }
            return connection
        }
    }
}

private class AtomicRequestId {
    private var value = 0L
    private val mutex = Mutex()
    suspend fun next(): RequestId = mutex.withLock { RequestId("remote-${++value}") }
}

public class RemoteConnectionException(message: String, public val code: Int? = null) : IllegalStateException(message)

public data class SequenceGap(
    val streamId: StreamId,
    val expectedSequence: Long,
    val receivedSequence: Long,
)

/** A history snapshot pulled after a sequence gap so the caller can reconcile its view. */
public data class HistoryRecovery(
    val streamId: StreamId,
    val messages: List<Message>,
)

@kotlinx.serialization.Serializable
private data class WireCreateSessionRequest(
    val id: SessionId? = null,
    val options: CreateSessionOptions = CreateSessionOptions(),
    val agentOptions: AgentOptions = AgentOptions(),
)

@kotlinx.serialization.Serializable
private data class WirePromptRequest(val id: SessionId, val message: Message)

@kotlinx.serialization.Serializable
private data class WireCancelRequest(val id: SessionId, val keepInbox: Boolean = false)

package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.HostRegistrationInfo
import im.hikaru.contracts.harness.relay.HostRegistrationRequest
import im.hikaru.contracts.harness.relay.HostRegistrationResponse
import im.hikaru.contracts.harness.relay.RelayRequest
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.ruoyi.framework.common.exception.ErrorCode
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketSessionLifecycleListener
import im.hikaru.ruoyi.framework.websocket.core.sender.WebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager
import im.hikaru.ruoyi.module.harness.enums.ErrorCodeConstants
import im.hikaru.ruoyi.module.harness.websocket.HarnessProtocolJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.beans.factory.DisposableBean

/**
 * RuoYi WebSocket Session 与 Harness Relay contract 之间的路由服务。
 *
 * 该服务只拥有传输相关性，不调用 Harness Runtime 或 API Gateway。
 */
@Service
class HarnessRelayService(
    private val hostRegistry: HostConnectionRegistry,
    private val pendingRegistry: PendingRelayRegistry,
    private val sessionManager: WebSocketSessionManager,
    private val messageSender: WebSocketMessageSender,
) : WebSocketSessionLifecycleListener, DisposableBean {
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<String, MutableSet<RelaySubscription>>()
    private val timeoutExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "harness-relay-timeouts").apply { isDaemon = true }
    }

    fun registerHost(session: WebSocketSession, request: HostRegistrationRequest) {
        val principal = HarnessPrincipal.from(session)
        if (principal == null) {
            sendRegistrationError(session.id, GlobalErrorCodeConstants.UNAUTHORIZED)
            return
        }

        val registration = hostRegistry.register(principal, session.id, request.host)
        registration.displacedConnections.forEach { connection ->
            failPending(connection, ErrorCodeConstants.HOST_REPLACED)
        }
        val response =
            HostRegistrationResponse(
                result =
                    ApiResult(
                        code = ApiResult.SUCCESS_CODE,
                        msg = "",
                        data =
                            HostRegistrationInfo(
                                hostId = registration.connection.description.hostId,
                                generation = registration.connection.generation,
                            ),
                    ),
            )
        messageSender.send(
            session.id,
            HarnessWebSocketMessageTypes.HostRegistered,
            HarnessProtocolJson.encodeToString(response),
        )
    }

    fun routeRequest(session: WebSocketSession, request: RelayRequest) {
        val principal = HarnessPrincipal.from(session)
        if (principal == null) {
            sendRelayError(session.id, request, GlobalErrorCodeConstants.UNAUTHORIZED)
            return
        }

        if (request.method in FORBIDDEN_REMOTE_METHODS || request.method.startsWith("file.")) {
            sendRelayError(session.id, request, ErrorCodeConstants.METHOD_FORBIDDEN)
            return
        }
        val connection = hostRegistry.find(principal, request.hostId)
        if (connection == null) {
            sendRelayError(session.id, request, ErrorCodeConstants.HOST_NOT_FOUND)
            return
        }
        if (request.method == SUBSCRIBE_METHOD || request.method == UNSUBSCRIBE_METHOD) {
            val stream: String? = (request.payload as? JsonPrimitive)?.contentOrNull
            if (stream.isNullOrBlank()) {
                sendRelayError(session.id, request, ErrorCodeConstants.METHOD_FORBIDDEN)
                return
            }
            if (request.method == SUBSCRIBE_METHOD) {
                subscriptions.computeIfAbsent(session.id) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
                    .add(RelaySubscription(principal, connection.description.hostId, connection.generation, stream))
            } else {
                subscriptions[session.id]?.removeIf { it.streamId == stream && it.hostId == request.hostId }
            }
            // 继续下发：Host 记录自己的订阅视图并回执，Relay 只负责按所有权转发。
        }
        val hostSession = sessionManager.getSession(connection.sessionId)
        if (hostSession?.isOpen != true) {
            hostRegistry.unregister(connection.sessionId)?.let { staleConnection ->
                failPending(staleConnection, ErrorCodeConstants.HOST_DISCONNECTED)
            }
            sendRelayError(session.id, request, ErrorCodeConstants.HOST_NOT_CONNECTED)
            return
        }

        val pending =
            PendingRelayRequest(
                principal = principal,
                request = request,
                clientSessionId = session.id,
                hostSessionId = connection.sessionId,
                hostGeneration = connection.generation,
            )
        if (!pendingRegistry.register(pending)) {
            sendRelayError(session.id, request, ErrorCodeConstants.DUPLICATE_REQUEST)
            return
        }
        timeoutExecutor.schedule({
            pendingRegistry.remove(pending)?.let { sendRelayError(it.clientSessionId, it.request, ErrorCodeConstants.REQUEST_TIMEOUT) }
        }, 15, java.util.concurrent.TimeUnit.SECONDS)
        val currentConnection = hostRegistry.find(principal, request.hostId)
        if (currentConnection != connection) {
            val errorCode =
                if (currentConnection == null) {
                    ErrorCodeConstants.HOST_DISCONNECTED
                } else {
                    ErrorCodeConstants.HOST_REPLACED
                }
            failPending(connection, errorCode)
            return
        }
        messageSender.send(
            connection.sessionId,
            HarnessWebSocketMessageTypes.RelayRequest,
            HarnessProtocolJson.encodeToString(request),
        )
    }

    fun routeResponse(session: WebSocketSession, response: RelayResponse) {
        val principal = HarnessPrincipal.from(session) ?: return
        val connection = hostRegistry.findBySession(session.id)
        if (connection == null ||
            connection.principal != principal ||
            connection.description.hostId != response.hostId
        ) {
            log.warn(
                "[routeResponse][拒绝非当前 Host 连接的响应，session({}) hostId({}) requestId({})]",
                session.id,
                response.hostId,
                response.requestId,
            )
            return
        }
        val pending = pendingRegistry.complete(connection, response)
        if (pending == null) {
            log.warn(
                "[routeResponse][未找到当前 generation 的 pending 请求，session({}) requestId({})]",
                session.id,
                response.requestId,
            )
            return
        }
        messageSender.send(
            pending.clientSessionId,
            HarnessWebSocketMessageTypes.RelayResponse,
            HarnessProtocolJson.encodeToString(response),
        )
    }

    fun routeEvent(session: WebSocketSession, event: im.hikaru.contracts.harness.relay.RelayEvent) {
        val principal = HarnessPrincipal.from(session) ?: return
        val connection = hostRegistry.findBySession(session.id) ?: return
        if (connection.principal != principal || connection.description.hostId != event.hostId) return
        subscriptions.forEach { (clientSession, streams) ->
            if (streams.any {
                it.principal == principal &&
                    it.hostId == event.hostId &&
                    it.hostGeneration == connection.generation &&
                    it.streamId == event.streamId.value
            }) {
                messageSender.send(clientSession, HarnessWebSocketMessageTypes.RelayEvent, HarnessProtocolJson.encodeToString(event))
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        hostRegistry.unregister(session.id)?.let { connection ->
            failPending(connection, ErrorCodeConstants.HOST_DISCONNECTED)
        }
        pendingRegistry.removeByClientSession(session.id)
        subscriptions.remove(session.id)
    }

    private fun failPending(connection: HostConnection, errorCode: ErrorCode) {
        pendingRegistry.removeByHost(connection).forEach { pending ->
            sendRelayError(pending.clientSessionId, pending.request, errorCode)
        }
        subscriptions.values.forEach { subscriptions ->
            subscriptions.removeIf {
                it.principal == connection.principal &&
                    it.hostId == connection.description.hostId &&
                    it.hostGeneration == connection.generation
            }
        }
    }

    private fun sendRegistrationError(sessionId: String, errorCode: ErrorCode) {
        val response =
            HostRegistrationResponse(
                result =
                    ApiResult(
                        code = errorCode.code,
                        msg = errorCode.msg,
                    ),
            )
        messageSender.send(
            sessionId,
            HarnessWebSocketMessageTypes.HostRegistered,
            HarnessProtocolJson.encodeToString(response),
        )
    }

    private fun sendRelayError(
        sessionId: String,
        request: RelayRequest,
        errorCode: ErrorCode,
    ) {
        val response =
            RelayResponse(
                requestId = request.requestId,
                hostId = request.hostId,
                correlationId = request.correlationId,
                result =
                    ApiResult<JsonElement>(
                        code = errorCode.code,
                        msg = errorCode.msg,
                    ),
            )
        messageSender.send(
            sessionId,
            HarnessWebSocketMessageTypes.RelayResponse,
            HarnessProtocolJson.encodeToString(response),
        )
    }

    companion object {
        private val FORBIDDEN_REMOTE_METHODS = setOf(
            "credentials.set", "credentials.unset", "settings.secret.set", "llm.models.discover-temporary-key",
        )
        private const val SUBSCRIBE_METHOD = "session.events.subscribe"
        private const val UNSUBSCRIBE_METHOD = "session.events.unsubscribe"
        private val log = LoggerFactory.getLogger(HarnessRelayService::class.java)
    }

    override fun destroy() {
        timeoutExecutor.shutdownNow()
        subscriptions.clear()
    }

    private data class RelaySubscription(
        val principal: HarnessPrincipal,
        val hostId: im.hikaru.contracts.harness.identity.HostId,
        val hostGeneration: Long,
        val streamId: String,
    )
}

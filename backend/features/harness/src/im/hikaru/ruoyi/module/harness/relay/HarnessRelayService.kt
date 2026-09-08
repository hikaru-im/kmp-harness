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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession

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
) : WebSocketSessionLifecycleListener {

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

        val connection = hostRegistry.find(principal, request.hostId)
        if (connection == null) {
            sendRelayError(session.id, request, ErrorCodeConstants.HOST_NOT_FOUND)
            return
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

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        hostRegistry.unregister(session.id)?.let { connection ->
            failPending(connection, ErrorCodeConstants.HOST_DISCONNECTED)
        }
        pendingRegistry.removeByClientSession(session.id)
    }

    private fun failPending(connection: HostConnection, errorCode: ErrorCode) {
        pendingRegistry.removeByHost(connection).forEach { pending ->
            sendRelayError(pending.clientSessionId, pending.request, errorCode)
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
        private val log = LoggerFactory.getLogger(HarnessRelayService::class.java)
    }
}

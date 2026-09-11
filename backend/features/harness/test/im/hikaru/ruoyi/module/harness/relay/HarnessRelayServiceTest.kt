package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.HostRegistrationRequest
import im.hikaru.contracts.harness.relay.RelayRequest
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.harness.relay.RequestId
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.websocket.core.sender.WebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManagerImpl
import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import im.hikaru.ruoyi.module.harness.enums.ErrorCodeConstants
import im.hikaru.ruoyi.module.harness.websocket.HarnessProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession

class HarnessRelayServiceTest {
    private lateinit var sessionManager: WebSocketSessionManagerImpl
    private lateinit var sender: RecordingMessageSender
    private lateinit var service: HarnessRelayService

    @BeforeEach
    fun setUp() {
        sessionManager = WebSocketSessionManagerImpl()
        sender = RecordingMessageSender()
        service =
            HarnessRelayService(
                hostRegistry = DefaultHostConnectionRegistry(),
                pendingRegistry = DefaultPendingRelayRegistry(),
                sessionManager = sessionManager,
                messageSender = sender,
            )
    }

    @Test
    fun `request and response follow registered host generation`() {
        val hostSession = session("host", tenantId = 1, userId = 7)
        val clientSession = session("client", tenantId = 1, userId = 7)
        addSessions(hostSession, clientSession)
        service.registerHost(hostSession, registration())
        sender.messages.clear()

        val request = request("request-1")
        service.routeRequest(clientSession, request)

        assertEquals(
            SentMessage("host", HarnessWebSocketMessageTypes.RelayRequest, HarnessProtocolJson.encodeToString(request)),
            sender.messages.single(),
        )

        sender.messages.clear()
        val response = successResponse(request)
        service.routeResponse(hostSession, response)

        assertEquals("client", sender.messages.single().sessionId)
        assertEquals(HarnessWebSocketMessageTypes.RelayResponse, sender.messages.single().type)
        assertEquals(response, HarnessProtocolJson.decodeFromString<RelayResponse>(sender.messages.single().content))
    }

    @Test
    fun `host lookup cannot cross authenticated principal`() {
        val hostSession = session("host", tenantId = 1, userId = 7)
        val otherClient = session("other-client", tenantId = 1, userId = 8)
        addSessions(hostSession, otherClient)
        service.registerHost(hostSession, registration())
        sender.messages.clear()

        service.routeRequest(otherClient, request("request-2"))

        val sent = sender.messages.single()
        val response = HarnessProtocolJson.decodeFromString<RelayResponse>(sent.content)
        assertEquals("other-client", sent.sessionId)
        assertEquals(ErrorCodeConstants.HOST_NOT_FOUND.code, response.result.code)
    }

    @Test
    fun `duplicate request id is rejected while first request remains pending`() {
        val hostSession = session("host", tenantId = 1, userId = 7)
        val clientSession = session("client", tenantId = 1, userId = 7)
        addSessions(hostSession, clientSession)
        service.registerHost(hostSession, registration())
        sender.messages.clear()
        val request = request("duplicate")

        service.routeRequest(clientSession, request)
        service.routeRequest(clientSession, request)

        assertEquals(2, sender.messages.size)
        assertEquals("host", sender.messages[0].sessionId)
        val duplicate = HarnessProtocolJson.decodeFromString<RelayResponse>(sender.messages[1].content)
        assertEquals(ErrorCodeConstants.DUPLICATE_REQUEST.code, duplicate.result.code)
    }

    @Test
    fun `replacement fails old generation and rejects stale response`() {
        val oldHost = session("old-host", tenantId = 1, userId = 7)
        val newHost = session("new-host", tenantId = 1, userId = 7)
        val client = session("client", tenantId = 1, userId = 7)
        addSessions(oldHost, newHost, client)
        service.registerHost(oldHost, registration())
        sender.messages.clear()
        val request = request("request-3")
        service.routeRequest(client, request)
        sender.messages.clear()

        service.registerHost(newHost, registration())

        val failure = sender.messages.first { it.sessionId == "client" }
        val response = HarnessProtocolJson.decodeFromString<RelayResponse>(failure.content)
        assertEquals(ErrorCodeConstants.HOST_REPLACED.code, response.result.code)
        assertTrue(sender.messages.any { it.sessionId == "new-host" && it.type == HarnessWebSocketMessageTypes.HostRegistered })

        sender.messages.clear()
        service.routeResponse(oldHost, successResponse(request))
        assertTrue(sender.messages.isEmpty())
    }

    @Test
    fun `host disconnect fails all requests pinned to its generation`() {
        val hostSession = session("host", tenantId = 1, userId = 7)
        val clientSession = session("client", tenantId = 1, userId = 7)
        addSessions(hostSession, clientSession)
        service.registerHost(hostSession, registration())
        sender.messages.clear()
        service.routeRequest(clientSession, request("request-4"))
        sender.messages.clear()

        service.afterConnectionClosed(hostSession, CloseStatus.NORMAL)

        val response = HarnessProtocolJson.decodeFromString<RelayResponse>(sender.messages.single().content)
        assertEquals(ErrorCodeConstants.HOST_DISCONNECTED.code, response.result.code)
    }

    @Test
    fun `sensitive remote methods are rejected before reaching host`() {
        val hostSession = session("host", tenantId = 1, userId = 7)
        val clientSession = session("client", tenantId = 1, userId = 7)
        addSessions(hostSession, clientSession)
        service.registerHost(hostSession, registration())
        sender.messages.clear()

        service.routeRequest(
            clientSession,
            request("sensitive").copy(method = "credentials.set"),
        )

        val response = HarnessProtocolJson.decodeFromString<RelayResponse>(sender.messages.single().content)
        assertEquals(ErrorCodeConstants.METHOD_FORBIDDEN.code, response.result.code)
        assertEquals("client", sender.messages.single().sessionId)
    }

    private fun addSessions(vararg sessions: WebSocketSession) {
        sessions.forEach(sessionManager::addSession)
    }

    private fun registration() =
        HostRegistrationRequest(
            host =
                HostDescription(
                    protocolVersion = ProtocolVersion(1, 0),
                    hostId = HostId("desktop-main"),
                    displayName = "Main Desktop",
                ),
        )

    private fun request(id: String) =
        RelayRequest(
            requestId = RequestId(id),
            hostId = HostId("desktop-main"),
            method = "host.describe",
        )

    private fun successResponse(request: RelayRequest) =
        RelayResponse(
            requestId = request.requestId,
            hostId = request.hostId,
            correlationId = request.correlationId,
            result =
                ApiResult<JsonElement>(
                    code = ApiResult.SUCCESS_CODE,
                    msg = "",
                    data = JsonPrimitive("ok"),
                ),
        )

    private fun session(
        id: String,
        tenantId: Long,
        userId: Long,
        userType: Int = 1,
    ): WebSocketSession {
        val session = mock(WebSocketSession::class.java)
        val attributes = mutableMapOf<String, Any>()
        WebSocketFrameworkUtils.setLoginUser(
            LoginUser().apply {
                this.id = userId
                this.userType = userType
                this.tenantId = tenantId
            },
            attributes,
        )
        `when`(session.id).thenReturn(id)
        `when`(session.attributes).thenReturn(attributes)
        `when`(session.isOpen).thenReturn(true)
        return session
    }
}

private data class SentMessage(
    val sessionId: String,
    val type: String,
    val content: String,
)

private class RecordingMessageSender : WebSocketMessageSender {
    val messages = mutableListOf<SentMessage>()

    override fun send(userType: Int?, userId: Long?, messageType: String, messageContent: String) =
        error("Harness Relay 不应按用户广播消息")

    override fun send(userType: Int?, messageType: String, messageContent: String) =
        error("Harness Relay 不应按用户类型广播消息")

    override fun send(sessionId: String, messageType: String, messageContent: String) {
        messages += SentMessage(sessionId, messageType, messageContent)
    }
}

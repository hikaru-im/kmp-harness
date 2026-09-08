package im.hikaru.contracts.harness.relay

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.websocket.WebSocketMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RelayContractTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @Test
    fun shouldEncodeRelayRequestInsideRuoyiWebSocketMessage() {
        val request =
            RelayRequest(
                requestId = RequestId("request-1"),
                hostId = HostId("desktop-main"),
                correlationId = CorrelationId("turn-1"),
                method = "host.describe",
                payload = JsonPrimitive("payload"),
            )
        val outer =
            WebSocketMessage(
                type = HarnessWebSocketMessageTypes.RelayRequest,
                content = json.encodeToString(request),
            )

        val decodedOuter = json.decodeFromString<WebSocketMessage>(json.encodeToString(outer))

        assertEquals(outer, decodedOuter)
        assertEquals(request, json.decodeFromString<RelayRequest>(decodedOuter.content))
    }

    @Test
    fun shouldRoundTripHostRegistrationResult() {
        val request =
            HostRegistrationRequest(
                host =
                    HostDescription(
                        protocolVersion = ProtocolVersion(1, 0),
                        hostId = HostId("desktop-main"),
                        displayName = "Main Desktop",
                    ),
            )
        val response =
            HostRegistrationResponse(
                result =
                    ApiResult(
                        code = ApiResult.SUCCESS_CODE,
                        msg = "",
                        data = HostRegistrationInfo(request.host.hostId, generation = 2),
                    ),
            )

        assertEquals(request, json.decodeFromString(json.encodeToString(request)))
        assertEquals(response, json.decodeFromString(json.encodeToString(response)))
    }

    @Test
    fun shouldUseRuoyiApiResultForRelayResponse() {
        val response =
            RelayResponse(
                requestId = RequestId("request-1"),
                hostId = HostId("desktop-main"),
                correlationId = CorrelationId("turn-1"),
                result =
                    ApiResult(
                        code = ApiResult.SUCCESS_CODE,
                        msg = "",
                        data = JsonPrimitive("ok"),
                    ),
            )

        assertEquals(response, json.decodeFromString(json.encodeToString(response)))
    }

    @Test
    fun shouldRoundTripOrderedRelayEvent() {
        val event =
            RelayEvent(
                eventId = EventId("event-1"),
                hostId = HostId("desktop-main"),
                streamId = StreamId("session-events"),
                sequence = 4,
                event = "session.updated",
                payload = JsonPrimitive("payload"),
            )

        assertEquals(event, json.decodeFromString(json.encodeToString(event)))
    }

    @Test
    fun shouldRejectInvalidRelayMessages() {
        assertFailsWith<IllegalArgumentException> {
            HostRegistrationInfo(
                hostId = HostId("desktop-main"),
                generation = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RelayRequest(
                requestId = RequestId("request-1"),
                hostId = HostId("desktop-main"),
                method = " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RelayResponse(
                requestId = RequestId("request-1"),
                hostId = HostId("desktop-main"),
                result = ApiResult(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RelayEvent(
                eventId = EventId("event-1"),
                hostId = HostId("desktop-main"),
                streamId = StreamId("session-events"),
                sequence = -1,
                event = "session.updated",
                payload = JsonPrimitive("payload"),
            )
        }
    }
}

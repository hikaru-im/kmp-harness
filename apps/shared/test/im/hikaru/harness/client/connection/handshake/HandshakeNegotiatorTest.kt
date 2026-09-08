package im.hikaru.harness.client.connection.handshake

import im.hikaru.contracts.harness.handshake.HandshakeRequest
import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.identity.ClientDescription
import im.hikaru.contracts.harness.identity.ClientId
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.CapabilityId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HandshakeNegotiatorTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @Test
    fun shouldRoundTripHandshakeRequestAndResponse() {
        val request = request()
        val response =
            assertIs<HandshakeNegotiationResult.Accepted>(
                HandshakeNegotiator.negotiate(request, host()),
            ).response

        assertEquals(request, json.decodeFromString(json.encodeToString(request)))
        assertEquals(response, json.decodeFromString(json.encodeToString(response)))
    }

    @Test
    fun shouldNegotiateProtocolAndCapabilityVersions() {
        val response =
            assertIs<HandshakeNegotiationResult.Accepted>(
                HandshakeNegotiator.negotiate(request(), host()),
            ).response

        assertEquals(ProtocolVersion(major = 1, minor = 2), response.negotiatedProtocolVersion)
        assertEquals(
            listOf(
                Capability(CapabilityId("session.events"), version = 2),
                Capability(CapabilityId("artifact.read"), version = 1),
            ),
            response.negotiatedCapabilities,
        )
        assertTrue(response.supports(CapabilityId("session.events"), minimumVersion = 2))
        assertFalse(response.supports(CapabilityId("host.only")))
        assertFalse(response.supports(CapabilityId("client.only")))
    }

    @Test
    fun shouldUseClientMinorVersionWhenItIsLower() {
        val client =
            client().copy(
                protocolVersion = ProtocolVersion(major = 1, minor = 1),
            )
        val result =
            assertIs<HandshakeNegotiationResult.Accepted>(
                HandshakeNegotiator.negotiate(HandshakeRequest(client), host()),
            )

        assertEquals(
            ProtocolVersion(major = 1, minor = 1),
            result.response.negotiatedProtocolVersion,
        )
    }

    @Test
    fun shouldRejectDifferentProtocolMajorVersions() {
        val incompatibleClient =
            client().copy(
                protocolVersion = ProtocolVersion(major = 2, minor = 0),
            )
        val result =
            assertIs<HandshakeNegotiationResult.Rejected>(
                HandshakeNegotiator.negotiate(
                    request = HandshakeRequest(incompatibleClient),
                    host = host(),
                ),
            )

        assertEquals(
            HandshakeRejectionReason.IncompatibleProtocolVersion,
            result.rejection.reason,
        )
        assertEquals(incompatibleClient.protocolVersion, result.rejection.clientProtocolVersion)
        assertEquals(ProtocolVersion(major = 1, minor = 2), result.rejection.hostProtocolVersion)
    }

    @Test
    fun shouldRejectInvalidNegotiatedCapabilities() {
        assertFailsWith<IllegalArgumentException> {
            HandshakeResponse(
                host = host(),
                negotiatedProtocolVersion = ProtocolVersion(major = 1, minor = 2),
                negotiatedCapabilities =
                    listOf(
                        Capability(CapabilityId("client.only"), version = 1),
                    ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HandshakeResponse(
                host = host(),
                negotiatedProtocolVersion = ProtocolVersion(major = 1, minor = 2),
                negotiatedCapabilities =
                    listOf(
                        Capability(CapabilityId("session.events"), version = 4),
                    ),
            )
        }
    }

    private fun request(): HandshakeRequest = HandshakeRequest(client())

    private fun client(): ClientDescription =
        ClientDescription(
            protocolVersion = ProtocolVersion(major = 1, minor = 4),
            clientId = ClientId("mobile-main"),
            displayName = "Hikaru Mobile",
            capabilities =
                listOf(
                    Capability(CapabilityId("session.events"), version = 3),
                    Capability(CapabilityId("artifact.read"), version = 1),
                    Capability(CapabilityId("client.only"), version = 1),
                ),
        )

    private fun host(): HostDescription =
        HostDescription(
            protocolVersion = ProtocolVersion(major = 1, minor = 2),
            hostId = HostId("desktop-main"),
            displayName = "Hikaru Desktop",
            capabilities =
                listOf(
                    Capability(CapabilityId("host.only"), version = 1),
                    Capability(CapabilityId("session.events"), version = 2),
                    Capability(CapabilityId("artifact.read"), version = 3),
                ),
        )
}

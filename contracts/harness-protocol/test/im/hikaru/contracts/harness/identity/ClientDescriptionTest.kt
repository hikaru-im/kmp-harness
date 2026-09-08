package im.hikaru.contracts.harness.identity

import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.CapabilityId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientDescriptionTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @Test
    fun shouldRoundTripClientDescription() {
        val description =
            ClientDescription(
                protocolVersion = ProtocolVersion.Current,
                clientId = ClientId("mobile-main"),
                displayName = "Hikaru Mobile",
                capabilities =
                    listOf(
                        Capability(CapabilityId("session.events"), version = 2),
                    ),
            )

        val encoded = json.encodeToString(description)

        assertEquals(description, json.decodeFromString(encoded))
        assertTrue(description.supports(CapabilityId("session.events"), minimumVersion = 2))
        assertFalse(description.supports(CapabilityId("session.events"), minimumVersion = 3))
    }

    @Test
    fun shouldRejectDuplicateCapabilityIds() {
        assertFailsWith<IllegalArgumentException> {
            ClientDescription(
                protocolVersion = ProtocolVersion.Current,
                clientId = ClientId("mobile-main"),
                displayName = "Hikaru Mobile",
                capabilities =
                    listOf(
                        Capability(CapabilityId("session.events"), version = 1),
                        Capability(CapabilityId("session.events"), version = 2),
                    ),
            )
        }
    }
}

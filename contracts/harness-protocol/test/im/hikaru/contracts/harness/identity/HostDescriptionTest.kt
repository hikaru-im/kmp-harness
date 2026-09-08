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

class HostDescriptionTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @Test
    fun shouldRoundTripHostDescription() {
        val description =
            HostDescription(
                protocolVersion = ProtocolVersion.Current,
                hostId = HostId("desktop-main"),
                displayName = "Hikaru Desktop",
                capabilities =
                    listOf(
                        Capability(CapabilityId("session.events"), version = 2),
                        Capability(CapabilityId("artifact.read")),
                    ),
            )

        assertEquals(description, json.decodeFromString(json.encodeToString(description)))
    }

    @Test
    fun shouldResolveCapabilityByMinimumVersion() {
        val description =
            HostDescription(
                protocolVersion = ProtocolVersion.Current,
                hostId = HostId("desktop-main"),
                displayName = "Hikaru Desktop",
                capabilities =
                    listOf(
                        Capability(CapabilityId("session.events"), version = 2),
                    ),
            )

        assertTrue(description.supports(CapabilityId("session.events")))
        assertTrue(description.supports(CapabilityId("session.events"), minimumVersion = 2))
        assertFalse(description.supports(CapabilityId("session.events"), minimumVersion = 3))
        assertFalse(description.supports(CapabilityId("artifact.read")))
    }

    @Test
    fun shouldRejectDuplicateCapabilityIds() {
        assertFailsWith<IllegalArgumentException> {
            HostDescription(
                protocolVersion = ProtocolVersion.Current,
                hostId = HostId("desktop-main"),
                displayName = "Hikaru Desktop",
                capabilities =
                    listOf(
                        Capability(CapabilityId("session.events"), version = 1),
                        Capability(CapabilityId("session.events"), version = 2),
                    ),
            )
        }
    }

    @Test
    fun shouldIgnoreFutureHandshakeFields() {
        val description =
            json.decodeFromString<HostDescription>(
                """
                {
                  "protocolVersion": { "major": 1, "minor": 0 },
                  "hostId": "desktop-main",
                  "displayName": "Hikaru Desktop",
                  "capabilities": [
                    { "id": "future.feature", "version": 1, "metadata": "ignored" }
                  ],
                  "futureField": true
                }
                """.trimIndent(),
            )

        assertTrue(description.supports(CapabilityId("future.feature")))
    }
}

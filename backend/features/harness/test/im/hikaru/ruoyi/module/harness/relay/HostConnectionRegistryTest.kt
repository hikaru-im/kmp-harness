package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HostConnectionRegistryTest {
    private val owner = HarnessPrincipal(tenantId = 1, userType = 1, userId = 7)
    private val otherOwner = HarnessPrincipal(tenantId = 1, userType = 1, userId = 8)

    @Test
    fun `same host id is isolated by authenticated principal`() {
        val registry = DefaultHostConnectionRegistry()
        val ownerConnection = registry.register(owner, "owner-session", host()).connection
        val otherConnection = registry.register(otherOwner, "other-session", host()).connection

        assertSame(ownerConnection, registry.find(owner, HostId("desktop-main")))
        assertSame(otherConnection, registry.find(otherOwner, HostId("desktop-main")))
    }

    @Test
    fun `same session refresh preserves generation`() {
        val registry = DefaultHostConnectionRegistry()
        val first = registry.register(owner, "host-session", host("First"))
        val refreshed = registry.register(owner, "host-session", host("Renamed"))

        assertEquals(first.connection.generation, refreshed.connection.generation)
        assertEquals("Renamed", refreshed.connection.description.displayName)
        assertEquals(emptyList<HostConnection>(), refreshed.displacedConnections)
    }

    @Test
    fun `new session replaces host without letting stale close remove it`() {
        val registry = DefaultHostConnectionRegistry()
        val first = registry.register(owner, "old-session", host()).connection
        val secondRegistration = registry.register(owner, "new-session", host())

        assertEquals(listOf(first), secondRegistration.displacedConnections)
        assertEquals(first.generation + 1, secondRegistration.connection.generation)
        assertNull(registry.unregister("old-session"))
        assertSame(secondRegistration.connection, registry.find(owner, HostId("desktop-main")))
    }

    private fun host(displayName: String = "Desktop") =
        HostDescription(
            protocolVersion = ProtocolVersion(1, 0),
            hostId = HostId("desktop-main"),
            displayName = displayName,
        )
}

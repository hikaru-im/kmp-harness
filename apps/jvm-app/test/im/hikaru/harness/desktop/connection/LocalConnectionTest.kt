package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.boot.DesktopProfile
import im.hikaru.harness.boot.HarnessHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalConnectionTest {

    @Test
    fun shouldReturnTheSameDescriptionWithoutSerializationRoundTrip() = runTest {
        val description =
            HostDescription(
                protocolVersion = ProtocolVersion(major = 1, minor = 0),
                hostId = HostId("desktop-local-test"),
                displayName = "Local Desktop Test",
            )
        val host =
            HarnessHost.start(
                DesktopProfile(hostDescription = description)
            )

        try {
            val connection = LocalConnection(host.gateway)
            val result = connection.host.describe()

            assertTrue(result.isSuccess)
            assertSame(description, result.data)
        } finally {
            host.close()
        }
    }
}

package im.hikaru.harness.client.app

import im.hikaru.harness.ui.slot.ClientHarnessHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class ClientUiCatalogTest {
    @Test
    fun desktopCatalogShouldInstallRootHeaderAndSidebarContributions() = runTest {
        val host =
            ClientHarnessHost.start(
                catalog = DesktopClientPluginCatalog,
                entries = DesktopClientEntries,
                schema = ClientUiSchema,
            )

        assertNotNull(host.snapshot.single(ClientUiSlots.Root))
        assertNotNull(host.snapshot.single(ClientUiSlots.SessionHeader))
        // The desktop catalog installs the root, session and workspace headers; it contributes no
        // sidebar action yet (the rail's own entries are drawn by the shell, not through this slot).
        assertEquals(
            emptyList(),
            host.snapshot.list(ClientUiSlots.SidebarActions).map { it.id },
        )
        assertNotNull(host.snapshot.single(ClientUiSlots.WorkspaceHeader))
        assertEquals(emptyList(), host.snapshot.list(ClientUiSlots.ThreadTitlebarActions))
        host.close()
    }
}

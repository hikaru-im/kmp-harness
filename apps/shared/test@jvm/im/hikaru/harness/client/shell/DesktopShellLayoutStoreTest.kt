package im.hikaru.harness.client.shell

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopShellLayoutStoreTest {
    @Test
    fun saveThenReopenRestoresEveryLayoutFieldWithoutTemporaryFiles() = runBlocking {
        val root = Files.createTempDirectory("harness-layout-")
        try {
            val path = root.resolve("nested/client-layout.json")
            val store = DesktopShellLayoutStore(path)
            assertNull(store.read())
            assertFalse(Files.exists(path.parent))
            val settings = ShellLayoutSettings(
                navigatorMode = SidebarMode.TemporarilyExpanded,
                navigatorWidth = 410f,
                pinnedRailDestinations = setOf(RailDestination.Library, RailDestination.Projects),
                summaryExpandedSections = mapOf("sources" to true, "plan" to false),
            )
            store.write(settings)
            assertEquals(settings, DesktopShellLayoutStore(path).read())
            Files.list(path.parent).use {
                assertEquals(listOf("client-layout.json"), it.map { file -> file.fileName.toString() }.toList())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun corruptDocumentLoadsDefaultsButTheNextSaveReplacesIt() = runBlocking {
        val root = Files.createTempDirectory("harness-layout-corrupt-")
        try {
            val path = root.resolve("client-layout.json")
            Files.writeString(path, "{broken-json")
            val repository = ShellLayoutRepository(DesktopShellLayoutStore(path))

            repository.load()

            assertNotNull(repository.state.value.loadError)
            assertEquals(ShellLayoutSettings(), repository.state.value.settings)
            assertTrue(repository.save(ShellLayoutSettings(navigatorWidth = 360f)))
            assertEquals(360f, DesktopShellLayoutStore(path).read()?.navigatorWidth)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

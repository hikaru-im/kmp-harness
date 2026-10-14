package im.hikaru.harness.client.settings

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopWorktreeSettingsStoreTest {
    @Test
    fun saveThenReopenRestoresRootWithoutTemporaryFiles() = runBlocking {
        val root = Files.createTempDirectory("harness-worktrees-")
        try {
            val path = root.resolve("nested/client-worktrees.json")
            val store = DesktopWorktreeSettingsStore(path)
            assertTrue(store.durable)
            assertNull(store.read())
            assertFalse(Files.exists(path.parent))
            listOf("/tmp/managed", "", "C:/worktrees").forEach { value ->
                val settings = WorktreeSettings(value)
                store.write(settings)
                assertEquals(settings, DesktopWorktreeSettingsStore(path).read())
            }
            // The Environment rows round-trip alongside the root.
            val environment = WorktreeSettings("/tmp/managed", autoCleanupEnabled = false, keepCount = 20,
                upstreamRefreshMode = WorktreeUpstreamRefreshMode.BestEffort)
            store.write(environment)
            assertEquals(environment, DesktopWorktreeSettingsStore(path).read())
            Files.list(path.parent).use {
                assertEquals(listOf("client-worktrees.json"), it.map { file -> file.fileName.toString() }.toList())
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun corruptDocumentIsNotOverwritten() = runBlocking {
        val root = Files.createTempDirectory("harness-worktrees-corrupt-")
        try {
            val path = root.resolve("client-worktrees.json")
            val document = "{broken-json"
            Files.writeString(path, document)
            val repository = WorktreeSettingsRepository(DesktopWorktreeSettingsStore(path))
            repository.load()
            assertNotNull(repository.state.value.loadError)
            assertFalse(repository.save(WorktreeSettings("/tmp/x")))
            assertEquals(document, Files.readString(path))
        } finally { root.toFile().deleteRecursively() }
    }
}

package im.hikaru.harness.client.settings

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopGeneralSettingsStoreTest {
    @Test
    fun saveThenReopenRestoresEveryPolicyWithoutTemporaryFiles() = runBlocking {
        val root = Files.createTempDirectory("harness-general-")
        try {
            val path = root.resolve("nested/client-general.json")
            val store = DesktopGeneralSettingsStore(path)
            assertNull(store.read())
            assertFalse(Files.exists(path.parent))
            CloseConfirmationPolicy.entries.forEach { policy ->
                val settings = GeneralSettings(policy)
                store.write(settings)
                assertEquals(settings, DesktopGeneralSettingsStore(path).read())
            }
            Files.list(path.parent).use {
                assertEquals(listOf("client-general.json"), it.map { file -> file.fileName.toString() }.toList())
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun corruptOrUnknownPolicyDocumentIsNotOverwritten() = runBlocking {
        val root = Files.createTempDirectory("harness-general-corrupt-")
        try {
            val path = root.resolve("client-general.json")
            listOf("{broken-json", """{"closeConfirmation":"unexpected"}""").forEach { document ->
                Files.writeString(path, document)
                val repository = GeneralSettingsRepository(DesktopGeneralSettingsStore(path), true)
                repository.load()
                assertNotNull(repository.state.value.loadError)
                assertFalse(repository.save(GeneralSettings(CloseConfirmationPolicy.Always)))
                assertEquals(document, Files.readString(path))
            }
        } finally { root.toFile().deleteRecursively() }
    }
}

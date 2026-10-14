package im.hikaru.harness.client.settings

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopAppearanceStoreTest {
    @Test
    fun saveThenReopenRestoresIndependentThemeAndFontValues() = runBlocking {
        val root = Files.createTempDirectory("harness-appearance-test-")
        val path = root.resolve("nested/client-appearance.json")
        val store = DesktopAppearanceStore(path)
        assertNull(store.read())
        assertFalse(Files.exists(path.parent))
        val settings = AppearanceSettings(mode = AppearanceMode.Dark, light = AppearanceVariant(accent = "#abcdef"),
            dark = AppearanceVariant(background = "#121212", codeFont = AppearanceFont.Serif), uiFontSize = 15, codeFontSize = 20)
        store.write(settings)
        assertEquals(settings, DesktopAppearanceStore(path).read())
        Files.list(path.parent).use { assertEquals(listOf("client-appearance.json"), it.map { entry -> entry.fileName.toString() }.toList()) }
    }

    @Test
    fun corruptDocumentRemainsUntouchedWhenRepositoryRefusesEdits() = runBlocking {
        val path = Files.createTempDirectory("harness-appearance-corrupt-").resolve("client-appearance.json")
        val bytes = "{broken-json"
        Files.writeString(path, bytes)
        val repository = AppearanceRepository(DesktopAppearanceStore(path))
        repository.load()
        assertNotNull(repository.state.value.loadError)
        assertFalse(repository.save(AppearanceSettings(mode = AppearanceMode.Dark)))
        assertEquals(bytes, Files.readString(path))
    }

    @Test
    fun invalidWriteCannotReplaceTheExistingDocument() = runBlocking {
        val path = Files.createTempDirectory("harness-appearance-validation-").resolve("client-appearance.json")
        val store = DesktopAppearanceStore(path)
        val settings = AppearanceSettings(mode = AppearanceMode.Light)
        store.write(settings)
        assertFailsWith<IllegalArgumentException> { store.write(settings.copy(uiFontSize = 999)) }
        assertEquals(settings, store.read())
    }
}

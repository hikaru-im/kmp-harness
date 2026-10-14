package im.hikaru.harness.client.settings

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopShortcutsStoreTest {
    private val platform = ShortcutPlatform.Mac

    @Test
    fun isolatedAtomicDocumentRoundTripPreservesMultipleAndEmptyOverrides() = runBlocking {
        val root = Files.createTempDirectory("harness-shortcuts-")
        try {
            val path = root.resolve("nested/client-shortcuts.json")
            val store = DesktopShortcutsStore(path, platform)
            assertNull(store.read())
            assertFalse(Files.exists(path.parent))
            val custom = ShortcutsSettings()
                .withBindings(ShortcutCommand.CloseWindow, emptyList())
                .withBindings(ShortcutCommand.ManageTasks, listOf(
                    ShortcutChord(ShortcutKey.K, meta = true), ShortcutChord(ShortcutKey.T, meta = true, shift = true)))
            store.write(custom)
            assertEquals(custom, DesktopShortcutsStore(path, platform).read())
            val repository = ShortcutsRepository(DesktopShortcutsStore(path, platform), platform).also { it.load() }
            assertTrue(repository.reset(ShortcutCommand.CloseWindow))
            assertEquals(ShortcutRegistry.defaults(ShortcutCommand.CloseWindow, platform),
                DesktopShortcutsStore(path, platform).read()!!.bindings(ShortcutCommand.CloseWindow, platform))
            Files.list(path.parent).use { files ->
                assertEquals(listOf("client-shortcuts.json"), files.map { it.fileName.toString() }.toList())
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun brokenUnknownAndUnexecutableDocumentsRemainByteForByteUnchanged() = runBlocking {
        val root = Files.createTempDirectory("harness-shortcuts-invalid-")
        try {
            val path = root.resolve("client-shortcuts.json")
            val documents = listOf(
                "{broken", "{}", "null",
                """{"schemaVersion":2,"overrides":{}}""",
                """{"schemaVersion":1,"overrides":{},"unknown":true}""",
                """{"schemaVersion":1,"overrides":{"unknownCommand":[]}}""",
                """{"schemaVersion":1,"overrides":{"manageTasks":[{"key":"Unknown","meta":true}]}}""",
                """{"schemaVersion":1,"overrides":{"manageTasks":[{"key":"K"}]}}""",
                """{"schemaVersion":1,"overrides":{"manageTasks":[{"key":"F","meta":true}]}}""",
                """{"schemaVersion":1,"overrides":{"manageTasks":[{"key":"Comma","meta":true}]}}""",
                """{"schemaVersion":1,"overrides":{"manageTasks":[{"key":"K","meta":true,"sequence":["T"]}]}}""",
            )
            documents.forEach { document ->
                Files.writeString(path, document)
                val store = DesktopShortcutsStore(path, platform)
                val repository = ShortcutsRepository(store, platform).also { it.load() }
                assertNotNull(repository.state.value.loadError, document)
                assertFalse(repository.state.value.canEdit)
                assertFalse(repository.save(ShortcutsSettings()))
                assertFailsWith<IllegalStateException> { store.write(ShortcutsSettings()) }
                assertEquals(document, Files.readString(path))
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun externalEditFailsSaveWithoutReplacingEffectiveMappingOrExternalDocument() = runBlocking {
        val root = Files.createTempDirectory("harness-shortcuts-external-")
        try {
            val path = root.resolve("client-shortcuts.json")
            val repository = ShortcutsRepository(DesktopShortcutsStore(path, platform), platform).also { it.load() }
            assertTrue(repository.save(ShortcutsSettings()))
            val previous = Files.readString(path)
            val external = previous + "\n"
            Files.writeString(path, external)
            val candidate = ShortcutsSettings().withBindings(ShortcutCommand.CloseWindow, emptyList())
            assertFalse(repository.save(candidate))
            assertEquals(ShortcutsSettings(), repository.state.value.settings)
            assertEquals(candidate, repository.state.value.draft)
            assertEquals(external, Files.readString(path))
            assertNotNull(repository.state.value.saveError)
            Files.list(root).use { assertEquals(1L, it.count()) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun writeRequiresSuccessfulReadAndClientFilesAreIndependent() = runBlocking {
        val root = Files.createTempDirectory("harness-shortcuts-isolated-")
        try {
            val general = root.resolve("client-general.json")
            val appearance = root.resolve("client-appearance.json")
            Files.writeString(general, "untouched general")
            Files.writeString(appearance, "untouched appearance")
            val store = DesktopShortcutsStore(root.resolve("client-shortcuts.json"), platform)
            assertFailsWith<IllegalStateException> { store.write(ShortcutsSettings()) }
            store.read()
            store.write(ShortcutsSettings())
            assertEquals("untouched general", Files.readString(general))
            assertEquals("untouched appearance", Files.readString(appearance))
        } finally { root.toFile().deleteRecursively() }
    }
}

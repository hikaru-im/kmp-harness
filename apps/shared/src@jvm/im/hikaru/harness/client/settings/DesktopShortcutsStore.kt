package im.hikaru.harness.client.settings

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Independent client-shortcuts.json. Unsupported atomic replacement fails without a fallback. */
public class DesktopShortcutsStore(
    private val path: Path,
    private val platform: ShortcutPlatform,
) : ShortcutsStore {
    override val durable: Boolean = true
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private var observed = false
    private var expectedSource: String? = null

    override suspend fun read(): ShortcutsSettings? = withContext(Dispatchers.IO) {
        val source = source()
        val settings = source?.let {
            val root = json.parseToJsonElement(it) as? JsonObject ?: error("Invalid root")
            require(root.keys == setOf("schemaVersion", "overrides"))
            json.decodeFromString(ShortcutsSettings.serializer(), it).also { candidate ->
                require(ShortcutRegistry.validationError(candidate, platform) == null)
            }
        }
        expectedSource = source
        observed = true
        settings
    }

    override suspend fun write(settings: ShortcutsSettings) = withContext(Dispatchers.IO) {
        check(observed) { "Read a valid document before writing" }
        require(ShortcutRegistry.validationError(settings, platform) == null)
        check(source() == expectedSource) { "Shortcut document changed externally" }
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".shortcuts-", ".tmp")
        val encoded = json.encodeToString(ShortcutsSettings.serializer(), settings)
        try {
            Files.writeString(temporary, encoded)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            currentCoroutineContext().ensureActive()
            check(source() == expectedSource) { "Shortcut document changed externally" }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            expectedSource = encoded
        } finally { Files.deleteIfExists(temporary) }
        Unit
    }

    private fun source(): String? = if (Files.notExists(path)) null else Files.readString(path)
}

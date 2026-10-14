package im.hikaru.harness.client.settings

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Stores only this client's appearance document; no Codex files or Host configuration are edited. */
public class DesktopAppearanceStore(private val path: Path) : AppearanceStore {
    override val durable: Boolean = true
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun read(): AppearanceSettings? = withContext(Dispatchers.IO) {
        if (!Files.exists(path)) return@withContext null
        json.decodeFromString<AppearanceSettings>(Files.readString(path)).also { it.validate() }
    }

    override suspend fun write(settings: AppearanceSettings) = withContext(Dispatchers.IO) {
        settings.validate()
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".appearance-", ".tmp")
        try {
            Files.writeString(temporary, json.encodeToString(settings))
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
        Unit
    }
}

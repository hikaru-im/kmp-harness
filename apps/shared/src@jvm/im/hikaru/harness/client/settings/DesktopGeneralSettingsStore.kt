package im.hikaru.harness.client.settings

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

public class DesktopGeneralSettingsStore(private val path: Path) : GeneralSettingsStore {
    override val durable: Boolean = true
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    override suspend fun read(): GeneralSettings? = withContext(Dispatchers.IO) {
        if (!Files.exists(path)) null else json.decodeFromString<GeneralSettings>(Files.readString(path))
    }
    override suspend fun write(settings: GeneralSettings) = withContext(Dispatchers.IO) {
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".general-", ".tmp")
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

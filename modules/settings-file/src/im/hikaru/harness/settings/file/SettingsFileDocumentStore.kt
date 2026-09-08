package im.hikaru.harness.settings.file

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.settings.SettingsDocumentStore
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.SettingsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.yaml.snakeyaml.Yaml
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name

data class SettingsFileConfig(
    val path: Path,
    val watch: Boolean = true,
    val debounceMillis: Long = 100,
) {
    init {
        require(debounceMillis >= 0) {
            "Settings file debounce must not be negative"
        }
    }
}

/**
 * JVM settings document store. It owns only the raw document; namespace
 * validation and merge semantics stay in SettingsService.
 */
class FileSettingsDocumentStore(
    config: SettingsFileConfig,
) : SettingsDocumentStore {
    private val path =
        config.path.absolute()

    private val lockPath =
        path.resolveSibling("${path.name}.lock")

    private val format =
        when (path.extension.lowercase()) {
            "yaml", "yml" -> Format.YAML
            "json" -> Format.JSON
            else -> error("Settings file must use .yaml, .yml, or .json: $path")
        }

    private val watchEnabled =
        config.watch

    private val debounceMillis =
        config.debounceMillis

    @Volatile
    private var lastText: String? =
        null

    private val watchers =
        mutableListOf<Disposable>()

    private var disposed =
        false

    override suspend fun read(): JsonObject =
        withContext(Dispatchers.IO) {
            initializeIfMissing()
            val text = Files.readString(path)
            assertOwnerOnly(path)
            val document = parse(text)
            lastText = text
            document
        }

    override suspend fun write(document: JsonObject) {
        withContext(Dispatchers.IO) {
            check(!disposed) {
                "Settings file store is disposed"
            }
            path.parent?.createDirectories()
            setOwnerOnly(path.parent)
            FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.lock().use {
                    val text = render(document)
                    val temporary =
                        Files.createTempFile(
                            path.parent,
                            path.name,
                            ".tmp",
                        )
                    try {
                        Files.writeString(
                            temporary,
                            text,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                        )
                        setOwnerOnly(temporary)
                        try {
                            Files.move(
                                temporary,
                                path,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        } catch (_: AtomicMoveNotSupportedException) {
                            Files.move(
                                temporary,
                                path,
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        }
                        setOwnerOnly(path)
                        lastText = text
                    } finally {
                        Files.deleteIfExists(temporary)
                    }
                }
            }
        }
    }

    override fun watch(
        listener: suspend (JsonObject) -> Unit,
    ): Disposable {
        if (!watchEnabled) return Disposable {}
        val parent =
            path.parent ?: Path.of(".").absolute()
        parent.createDirectories()
        val service =
            FileSystems.getDefault().newWatchService()
        parent.register(
            service,
            java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
            java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
            java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
        )
        val scope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job =
            scope.launch {
                while (isActive) {
                    val key =
                        try {
                            service.take()
                        } catch (_: ClosedWatchServiceException) {
                            break
                        } catch (_: InterruptedException) {
                            break
                        }
                    var relevant = false
                    key.pollEvents().forEach { event ->
                        if (event.context() == path.fileName) {
                            relevant = true
                        }
                    }
                    if (!key.reset()) break
                    if (!relevant) continue
                    if (debounceMillis > 0) delay(debounceMillis)
                    if (!Files.exists(path)) continue
                    try {
                        val text = Files.readString(path)
                        if (text == lastText) continue
                        assertOwnerOnly(path)
                        val document = parse(text)
                        lastText = text
                        listener(document)
                    } catch (_: Throwable) {
                        // Invalid external documents are rejected by the
                        // service without replacing its last good snapshot.
                    }
                }
            }
        val disposable =
            Disposable {
                job.cancel()
                service.close()
                scope.coroutineContext[Job]?.cancel()
            }
        watchers += disposable
        return disposable
    }

    override suspend fun dispose() {
        disposed = true
        watchers.toList().asReversed().forEach { it.dispose() }
        watchers.clear()
    }

    private fun initializeIfMissing() {
        if (Files.exists(path)) return
        path.parent?.createDirectories()
        setOwnerOnly(path.parent)
        val created =
            try {
                Files.createFile(
                    path,
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        setOf(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                        )
                    ),
                )
                true
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                false
            } catch (_: UnsupportedOperationException) {
                try {
                    Files.createFile(path)
                    true
                } catch (_: java.nio.file.FileAlreadyExistsException) {
                    false
                }
            }
        if (created) {
            setOwnerOnly(path)
            Files.writeString(
                path,
                render(buildJsonObject {}),
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        }
    }

    private fun parse(text: String): JsonObject {
        if (text.isBlank()) return buildJsonObject {}
        val element =
            when (format) {
                Format.JSON -> Json.parseToJsonElement(text)
                Format.YAML -> anyToJson(Yaml().load<Any?>(text))
            }
        return element as? JsonObject
            ?: error("Settings document root must be an object: $path")
    }

    private fun render(document: JsonObject): String =
        when (format) {
            Format.JSON ->
                JSON_FORMAT.encodeToString(JsonObject.serializer(), document) + "\n"

            Format.YAML ->
                Yaml().dump(jsonToAny(document))
        }

    private enum class Format {
        YAML,
        JSON,
    }

    private companion object {
        val JSON_FORMAT =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
    }
}

class SettingsFilePlugin(
    private val config: SettingsFileConfig,
) : im.hikaru.harness.runtime.plugin.SimplePlugin {
    override suspend fun apply(
        context: im.hikaru.harness.runtime.Context,
        scope: im.hikaru.harness.runtime.effect.EffectScope,
    ) {
        val service =
            SettingsService(
                FileSettingsDocumentStore(config),
            ).also { it.start() }
        scope.add(context.provide(SettingsKey, service))
        scope.add(service)
    }
}

private fun anyToJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is Map<*, *> ->
            buildJsonObject {
                value.forEach { (key, child) ->
                    require(key is String) {
                        "YAML settings object keys must be strings"
                    }
                    put(key, anyToJson(child))
                }
            }

        is Iterable<*> ->
            buildJsonArray {
                value.forEach { add(anyToJson(it)) }
            }

        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> error("Unsupported YAML settings value: ${value::class.qualifiedName}")
    }

private fun jsonToAny(value: JsonElement): Any? =
    when (value) {
        JsonNull -> null
        is JsonObject ->
            linkedMapOf<String, Any?>().also { target ->
                value.forEach { (key, child) ->
                    target[key] = jsonToAny(child)
                }
            }

        is JsonArray ->
            value.map(::jsonToAny)

        is JsonPrimitive ->
            when {
                value.isString -> value.content
                value.content == "true" -> true
                value.content == "false" -> false
                value.content.contains('.') -> value.content.toDoubleOrNull() ?: value.content
                else -> value.content.toLongOrNull() ?: value.content
            }
    }

private fun assertOwnerOnly(path: Path) {
    val permissions =
        runCatching { Files.getPosixFilePermissions(path) }.getOrNull()
            ?: return
    val forbidden =
        permissions.filter {
            it in setOf(
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE,
            )
        }
    require(forbidden.isEmpty()) {
        "Settings file must be owner-only: $path"
    }
}

private fun setOwnerOnly(path: Path?) {
    if (path == null || !Files.exists(path)) return
    runCatching {
        Files.setPosixFilePermissions(
            path,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ).filter { permission ->
                Files.isDirectory(path) || permission != PosixFilePermission.OWNER_EXECUTE
            }.toSet(),
        )
    }
}

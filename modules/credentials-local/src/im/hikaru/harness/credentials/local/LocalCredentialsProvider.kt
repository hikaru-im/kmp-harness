package im.hikaru.harness.credentials.local

import im.hikaru.harness.credentials.CredentialInfo
import im.hikaru.harness.credentials.CredentialProvider
import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialUpdateListener
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.credentials.ResolvedCredential
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import kotlin.io.path.name

data class LocalCredentialsConfig(
    val path: Path,
    val projectDir: Path = Path.of("").absolute(),
    val userEnvPath: Path? = null,
    val environment: (String) -> String? = System::getenv,
    val watch: Boolean = true,
    val debounceMillis: Long = 100,
) {
    init {
        require(debounceMillis >= 0) {
            "Credentials debounce must not be negative"
        }
    }
}

/**
 * Local credential provider matching DSH's source precedence:
 * environment > managed credentials file > project .env > user .env.
 */
class LocalCredentialsProvider(
    config: LocalCredentialsConfig,
) : CredentialProvider {
    private val path =
        config.path.absolute()

    private val lockPath =
        path.resolveSibling("${path.name}.lock")

    private val projectEnv =
        config.projectDir.resolve(".env")

    private val userEnv =
        config.userEnvPath?.absolute()

    private val environment =
        config.environment

    private val watchEnabled =
        config.watch

    private val debounceMillis =
        config.debounceMillis

    private val values =
        linkedMapOf<String, String>()

    private val listeners =
        mutableListOf<CredentialUpdateListener>()

    private val watchers =
        mutableListOf<Disposable>()

    private var disposed =
        false

    @Volatile
    private var managedText: String? =
        null

    init {
        loadManaged()
    }

    override suspend fun resolve(reference: CredentialRef): ResolvedCredential? {
        environment(reference.name)?.takeIf(String::isNotEmpty)?.let {
            return ResolvedCredential(it, "env")
        }
        values[reference.name]?.takeIf(String::isNotEmpty)?.let {
            return ResolvedCredential(it, "file")
        }
        readDotEnv(projectEnv)[reference.name]?.takeIf(String::isNotEmpty)?.let {
            return ResolvedCredential(it, "project-env")
        }
        userEnv?.let(::readDotEnv)?.get(reference.name)?.takeIf(String::isNotEmpty)?.let {
            return ResolvedCredential(it, "user-env")
        }
        return null
    }

    override suspend fun describe(reference: CredentialRef): CredentialInfo {
        if (environment(reference.name)?.isNullOrEmpty() == false) {
            return CredentialInfo(
                configured = true,
                source = "env",
                writable = false,
            )
        }
        if (values[reference.name]?.isNotEmpty() == true) {
            return CredentialInfo(
                configured = true,
                source = "file",
                writable = true,
            )
        }
        if (readDotEnv(projectEnv)[reference.name]?.isNullOrEmpty() == false) {
            return CredentialInfo(
                configured = true,
                source = "project-env",
                writable = true,
            )
        }
        if (userEnv?.let(::readDotEnv)?.get(reference.name)?.isNullOrEmpty() == false) {
            return CredentialInfo(
                configured = true,
                source = "user-env",
                writable = true,
            )
        }
        return CredentialInfo(
            configured = false,
            writable = true,
        )
    }

    override suspend fun set(
        reference: CredentialRef,
        value: String,
    ) {
        require(value.isNotEmpty()) {
            "An empty credential cannot be stored; use unset"
        }
        assertEnvironmentDoesNotShadow(reference)
        write(reference.name, value)
    }

    override suspend fun unset(reference: CredentialRef) {
        assertEnvironmentDoesNotShadow(reference)
        write(reference.name, null)
    }

    override fun watch(listener: CredentialUpdateListener): Disposable {
        listeners += listener
        return Disposable {
            listeners.remove(listener)
        }
    }

    override suspend fun dispose() {
        disposed = true
        watchers.toList().asReversed().forEach { it.dispose() }
        watchers.clear()
        listeners.clear()
        values.clear()
    }

    fun startWatching(): Disposable {
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
                while (isActive && !disposed) {
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
                        if (event.context() == path.fileName) relevant = true
                    }
                    if (!key.reset()) break
                    if (!relevant) continue
                    if (debounceMillis > 0) delay(debounceMillis)
                    val text =
                        if (Files.exists(path)) Files.readString(path) else null
                    if (text == managedText) continue
                    val before = values.toMap()
                    try {
                        loadManaged()
                    } catch (_: Throwable) {
                        // Keep the last good credential snapshot alive until
                        // the external document is valid again.
                        continue
                    }
                    val changed =
                        (before.keys + values.keys).filter { before[it] != values[it] }
                    managedText = text
                    changed.forEach { name ->
                        runCatching {
                            listeners.toList().forEach { listener ->
                                listener.onUpdated(CredentialRef(name))
                            }
                        }
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

    private suspend fun write(
        name: String,
        value: String?,
    ) {
        withContext(Dispatchers.IO) {
            check(!disposed) {
                "Credential provider is disposed"
            }
            path.parent?.createDirectories()
            setOwnerOnly(path.parent)
            FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.lock().use {
                    loadManaged()
                    if (value == null) {
                        values.remove(name)
                    } else {
                        values[name] = value
                    }
                    val temporary =
                        Files.createTempFile(path.parent, path.name, ".tmp")
                    try {
                        Files.writeString(
                            temporary,
                            render(values),
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
                        managedText = Files.readString(path)
                    } finally {
                        Files.deleteIfExists(temporary)
                    }
                }
            }
            listeners.toList().forEach { listener ->
                listener.onUpdated(CredentialRef(name))
            }
        }
    }

    private fun assertEnvironmentDoesNotShadow(reference: CredentialRef) {
        require(environment(reference.name).isNullOrEmpty()) {
            "Credential '${reference.name}' is supplied by the launching environment and is read-only"
        }
    }

    private fun loadManaged() {
        if (!Files.exists(path)) {
            values.clear()
            managedText = null
            return
        }
        assertOwnerOnly(path)
        val text = Files.readString(path)
        val parsed =
            try {
                Yaml().load<Any?>(text)
            } catch (_: Throwable) {
                error("Invalid credentials document: $path")
            }
        val next =
            linkedMapOf<String, String>()
        val mapping =
            parsed as? Map<*, *> ?: emptyMap<Any?, Any?>()
        mapping.forEach { (key, rawValue) ->
            require(key is String && CREDENTIAL_NAME_PATTERN.matches(key)) {
                "Invalid credential reference in $path"
            }
            require(rawValue is String && rawValue.isNotEmpty()) {
                "Credential values must be non-empty strings in $path"
            }
            next[key] = rawValue
        }
        values.clear()
        values.putAll(next)
        managedText = text
    }

    private fun readDotEnv(file: Path): Map<String, String> {
        if (!Files.exists(file)) return emptyMap()
        return Files.readAllLines(file).mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val withoutExport =
                trimmed.removePrefix("export ").trimStart()
            val separator =
                withoutExport.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = withoutExport.substring(0, separator).trim()
            if (!CREDENTIAL_NAME_PATTERN.matches(key)) return@mapNotNull null
            val value =
                withoutExport.substring(separator + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
            key to value
        }.toMap()
    }

    private fun render(values: Map<String, String>): String =
        Yaml().dump(values)

    private companion object {
        val CREDENTIAL_NAME_PATTERN =
            Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}

class CredentialsLocalPlugin(
    private val config: LocalCredentialsConfig,
) : SimplePlugin {
    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val provider =
            LocalCredentialsProvider(config)
        scope.add(context.provide(CredentialsKey, provider))
        scope.add(provider.startWatching())
        scope.add(provider)
    }
}

private fun assertOwnerOnly(path: Path) {
    if (!Files.exists(path)) return
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
        "Credentials file must be owner-only: $path"
    }
}

private fun setOwnerOnly(path: Path?) {
    if (path == null || !Files.exists(path)) return
    runCatching {
        Files.setPosixFilePermissions(
            path,
            if (Files.isDirectory(path)) {
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                )
            } else {
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                )
            },
        )
    }
}

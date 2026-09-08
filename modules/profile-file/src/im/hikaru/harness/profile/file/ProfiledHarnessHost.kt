package im.hikaru.harness.profile.file

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.boot.DesktopProfile
import im.hikaru.harness.boot.HarnessHost
import im.hikaru.harness.loader.PluginCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class ProfileReloadFailure(
    val occurredAt: Instant,
    val cause: Throwable,
)

/** Host-owned JVM bootstrap session for one file-backed profile. */
public class ProfiledHarnessHost private constructor(
    public val host: HarnessHost,
    private val profileLoader: FileProfileLoader,
    private val request: ProfileLoadRequest,
    initial: LoadedFileProfile,
    private val pollMillis: Long,
    private val onReloadFailure: (ProfileReloadFailure) -> Unit,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    @Volatile
    public var current: LoadedFileProfile = initial
        private set

    @Volatile
    public var lastReloadFailure: ProfileReloadFailure? = null
        private set

    public val isWatching: Boolean
        get() = watcher?.isActive == true

    init {
        require(pollMillis > 0) { "Profile watcher poll interval must be positive" }
        if (initial.manifest.patchReload == im.hikaru.harness.profile.ProfilePatchReload.Live) {
            startWatcher()
        }
    }

    /** Reloads and transactionally reconciles one complete profile snapshot. */
    public suspend fun refresh(): LoadedFileProfile =
        mutex.withLock {
            val next = profileLoader.load(request)
            host.reconcileProfile(next.entries)
            current = next
            lastReloadFailure = null
            next
        }

    public suspend fun close() {
        watcher?.cancel()
        watcher = null
        scope.cancel()
        host.close()
    }

    private fun startWatcher() {
        watcher =
            scope.launch {
                var observed = fingerprint(current.watchedFiles)
                while (isActive) {
                    delay(pollMillis)
                    if (current.manifest.patchReload != im.hikaru.harness.profile.ProfilePatchReload.Live) {
                        break
                    }
                    val nextFingerprint = fingerprint(current.watchedFiles)
                    if (nextFingerprint == observed) continue
                    observed = nextFingerprint
                    try {
                        refresh()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        val failure = ProfileReloadFailure(Instant.now(), error)
                        lastReloadFailure = failure
                        onReloadFailure(failure)
                    }
                }
            }
    }

    private fun fingerprint(paths: List<Path>): List<FileFingerprint> =
        paths.map { path ->
            if (!Files.exists(path)) {
                FileFingerprint(path, false, 0)
            } else {
                val content =
                    runCatching { Files.readAllBytes(path).contentHashCode() }
                        .getOrElse { error -> error::class.qualifiedName.hashCode() }
                FileFingerprint(path, true, content)
            }
        }

    private data class FileFingerprint(
        val path: Path,
        val exists: Boolean,
        val contentHash: Int,
    )

    public companion object {
        public suspend fun start(
            profileLoader: FileProfileLoader,
            request: ProfileLoadRequest,
            catalog: PluginCatalog,
            hostDescription: HostDescription,
            pollMillis: Long = 250,
            onReloadFailure: (ProfileReloadFailure) -> Unit = {},
        ): ProfiledHarnessHost {
            val loaded = profileLoader.load(request)
            val host =
                HarnessHost.start(
                    DesktopProfile(
                        hostDescription = hostDescription,
                        catalog = catalog,
                        entries = loaded.entries,
                    )
                )
            return try {
                ProfiledHarnessHost(
                    host = host,
                    profileLoader = profileLoader,
                    request = request,
                    initial = loaded,
                    pollMillis = pollMillis,
                    onReloadFailure = onReloadFailure,
                )
            } catch (error: Throwable) {
                try {
                    host.close()
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                throw error
            }
        }
    }
}

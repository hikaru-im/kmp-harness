package im.hikaru.harness.client.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public interface ShellLayoutStore {
    public val durable: Boolean
    public suspend fun read(): ShellLayoutSettings?
    public suspend fun write(settings: ShellLayoutSettings)
}

public class MemoryShellLayoutStore(initial: ShellLayoutSettings? = null) : ShellLayoutStore {
    public var current: ShellLayoutSettings? = initial
        private set

    override val durable: Boolean = false

    override suspend fun read(): ShellLayoutSettings? = current

    override suspend fun write(settings: ShellLayoutSettings) {
        current = settings
    }
}

public data class ShellLayoutState(
    val settings: ShellLayoutSettings = ShellLayoutSettings(),
    val durable: Boolean = false,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val saveRevision: Long = 0,
)

/**
 * Layout writes are coalesced by the caller (one write after a drag settles), so this repository
 * only owns load/save ordering and failure handling. A damaged document falls back to defaults but
 * keeps [ShellLayoutState.loaded] true: the next user change replaces it instead of leaving the
 * shell permanently unwritable.
 */
public class ShellLayoutRepository(
    private val store: ShellLayoutStore = MemoryShellLayoutStore(),
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ShellLayoutState(durable = store.durable))
    public val state: StateFlow<ShellLayoutState> = mutableState.asStateFlow()
    private var lastSaved: ShellLayoutSettings? = null

    public suspend fun load() = mutex.withLock {
        mutableState.value = mutableState.value.copy(loading = true, loadError = null)
        try {
            val settings = (store.read() ?: mutableState.value.settings).sanitized()
            lastSaved = settings
            mutableState.value = mutableState.value.copy(
                settings = settings,
                loading = false,
                loaded = true,
                saveError = null,
            )
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(loading = false)
            throw cancelled
        } catch (_: Exception) {
            val defaults = mutableState.value.settings.sanitized()
            lastSaved = null
            mutableState.value = mutableState.value.copy(
                settings = defaults,
                loading = false,
                loaded = true,
                loadError = "无法读取布局设置，已使用默认值。",
            )
        }
    }

    public suspend fun save(settings: ShellLayoutSettings): Boolean {
        if (!mutableState.value.loaded) return false
        val sanitized = settings.sanitized()
        return mutex.withLock {
            if (sanitized == lastSaved) return@withLock true
            try {
                store.write(sanitized)
                lastSaved = sanitized
                mutableState.value = mutableState.value.copy(
                    settings = sanitized,
                    saveError = null,
                    saveRevision = mutableState.value.saveRevision + 1,
                )
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last good settings on screen; the caller retries on the next change.
                mutableState.value = mutableState.value.copy(saveError = "无法保存布局设置。")
                false
            }
        }
    }
}

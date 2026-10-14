package im.hikaru.harness.client.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public interface WorktreeSettingsStore {
    public val durable: Boolean
    public suspend fun read(): WorktreeSettings?
    public suspend fun write(settings: WorktreeSettings)
}

public class MemoryWorktreeSettingsStore(initial: WorktreeSettings? = null) : WorktreeSettingsStore {
    override val durable: Boolean = false
    private var value = initial
    override suspend fun read(): WorktreeSettings? = value
    override suspend fun write(settings: WorktreeSettings) { value = settings }
}

public data class WorktreeSettingsState(
    val settings: WorktreeSettings = WorktreeSettings(),
    val durable: Boolean = false,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val saveRevision: Long = 0,
) {
    /** Only an editable store may be written; a read-only placeholder keeps the field disabled. */
    public val canEdit: Boolean get() = durable && loaded && !loading && !saving && loadError == null
}

/** Mirrors [GeneralSettingsRepository]: failed writes never replace the value used by the page. */
public class WorktreeSettingsRepository(
    private val store: WorktreeSettingsStore = MemoryWorktreeSettingsStore(),
    editable: Boolean = store.durable,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(WorktreeSettingsState(durable = editable))
    public val state: StateFlow<WorktreeSettingsState> = mutableState.asStateFlow()
    private var failedSettings: WorktreeSettings? = null

    public suspend fun load() = mutex.withLock {
        mutableState.value = mutableState.value.copy(loading = true, loadError = null)
        try {
            val settings = store.read() ?: mutableState.value.settings
            failedSettings = null
            mutableState.value = mutableState.value.copy(settings = settings, loading = false, loaded = true, saveError = null)
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(loading = false)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(loading = false, loadError = "无法读取工作树设置，请重试。")
        }
    }

    public suspend fun save(settings: WorktreeSettings): Boolean {
        if (!mutableState.value.canEdit || !mutex.tryLock()) return false
        try {
            mutableState.value = mutableState.value.copy(saving = true, saveError = null)
            store.write(settings)
            failedSettings = null
            mutableState.value = mutableState.value.copy(settings = settings, saving = false, saveRevision = mutableState.value.saveRevision + 1)
            return true
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(saving = false)
            throw cancelled
        } catch (_: Exception) {
            failedSettings = settings
            mutableState.value = mutableState.value.copy(saving = false, saveError = "无法保存工作树设置，请重试。")
            return false
        } finally { mutex.unlock() }
    }

    public suspend fun retry() {
        if (!mutableState.value.loaded || mutableState.value.loadError != null) load()
        else failedSettings?.let { save(it) }
    }
}

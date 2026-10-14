package im.hikaru.harness.client.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public interface AppearanceStore {
    public val durable: Boolean
    public suspend fun read(): AppearanceSettings?
    public suspend fun write(settings: AppearanceSettings)
}

public class MemoryAppearanceStore(initial: AppearanceSettings? = null) : AppearanceStore {
    override val durable: Boolean = false
    private var value: AppearanceSettings? = initial
    override suspend fun read(): AppearanceSettings? = value
    override suspend fun write(settings: AppearanceSettings) { settings.validate(); value = settings }
}

public data class AppearanceState(
    val settings: AppearanceSettings = AppearanceSettings(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val durable: Boolean = false,
    val saveRevision: Long = 0,
    val loaded: Boolean = !loading,
) {
    public val canEdit: Boolean get() = loaded && !loading && !saving && loadError == null
}

/** Publishes confirmed saved values; a failed write leaves the active theme intact. */
public class AppearanceRepository(
    private val store: AppearanceStore,
    initial: AppearanceSettings = AppearanceSettings(),
    private val launchMode: AppearanceMode? = null,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AppearanceState(settings = initial, durable = store.durable))
    public val state: StateFlow<AppearanceState> = mutableState.asStateFlow()
    private var failedSettings: AppearanceSettings? = null
    private var initialLoad = true

    public suspend fun load() {
        mutex.withLock {
            mutableState.value = mutableState.value.copy(loading = true, loadError = null)
            try {
                val stored = store.read() ?: mutableState.value.settings
                val settings = if (initialLoad && launchMode != null) stored.copy(mode = launchMode) else stored
                settings.validate()
                initialLoad = false
                failedSettings = null
                mutableState.value = mutableState.value.copy(settings = settings, loaded = true, loading = false, saveError = null)
            } catch (cancelled: CancellationException) {
                mutableState.value = mutableState.value.copy(loading = false)
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, loadError = error.message ?: "无法读取外观设置")
            }
        }
    }

    public suspend fun save(settings: AppearanceSettings): Boolean {
        if (!mutableState.value.canEdit || !mutex.tryLock()) return false
        try {
            settings.validate()
            mutableState.value = mutableState.value.copy(saving = true, saveError = null)
            store.write(settings)
            failedSettings = null
            mutableState.value = mutableState.value.copy(settings = settings, saving = false, saveRevision = mutableState.value.saveRevision + 1)
            return true
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(saving = false)
            throw cancelled
        } catch (error: Exception) {
            failedSettings = settings
            mutableState.value = mutableState.value.copy(saving = false, saveError = error.message ?: "无法保存外观设置")
            return false
        } finally { mutex.unlock() }
    }

    public suspend fun retry() {
        if (!mutableState.value.loaded || mutableState.value.loadError != null) load() else failedSettings?.let { save(it) }
    }
}

package im.hikaru.harness.client.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public interface GeneralSettingsStore {
    public val durable: Boolean
    public suspend fun read(): GeneralSettings?
    public suspend fun write(settings: GeneralSettings)
}

public class MemoryGeneralSettingsStore(initial: GeneralSettings? = null) : GeneralSettingsStore {
    override val durable: Boolean = false
    private var value = initial
    override suspend fun read(): GeneralSettings? = value
    override suspend fun write(settings: GeneralSettings) { value = settings }
}

public data class GeneralSettingsState(
    val settings: GeneralSettings = GeneralSettings(),
    val closePolicyAvailable: Boolean = false,
    val durable: Boolean = false,
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val saveRevision: Long = 0,
) {
    public val canEdit: Boolean get() = closePolicyAvailable && loaded && !loading && !saving && loadError == null
}

/** Failed writes never replace the policy used by the real window. */
public class GeneralSettingsRepository(
    private val store: GeneralSettingsStore = MemoryGeneralSettingsStore(),
    closePolicyAvailable: Boolean = false,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(GeneralSettingsState(
        closePolicyAvailable = closePolicyAvailable, durable = store.durable,
    ))
    public val state: StateFlow<GeneralSettingsState> = mutableState.asStateFlow()
    private var failedSettings: GeneralSettings? = null

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
            mutableState.value = mutableState.value.copy(loading = false, loadError = "无法读取常规设置，请重试。")
        }
    }

    public suspend fun save(settings: GeneralSettings): Boolean {
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
            mutableState.value = mutableState.value.copy(saving = false, saveError = "无法保存常规设置，请重试。")
            return false
        } finally { mutex.unlock() }
    }

    public suspend fun retry() {
        if (!mutableState.value.loaded || mutableState.value.loadError != null) load()
        else failedSettings?.let { save(it) }
    }
}

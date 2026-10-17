package im.hikaru.harness.client.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

public data class ModelSettingsState(
    val snapshot: ModelSettingsSnapshot = ModelSettingsSnapshot(),
    val loading: Boolean = true, val saving: Boolean = false, val loaded: Boolean = false,
    val loadError: String? = null, val saveError: String? = null,
    val refreshRequired: Boolean = false,
    val saveRevision: Long = 0,
    val completedOperation: String? = null,
    val confirmedProviderConfiguration: ProviderConfiguration? = null,
    val discoveringProviderId: String? = null,
) {
    public val canEdit: Boolean get() = loaded && snapshot.available && snapshot.writable && !loading && !saving && !refreshRequired
}

/** Owns confirmed Host writes and refreshes. Drafts and API key values remain in the page. */
public class ModelSettingsRepository(private val backend: ModelSettingsBackend = UnavailableModelSettingsBackend()) {
    private val mutableState = MutableStateFlow(ModelSettingsState())
    public val state: StateFlow<ModelSettingsState> = mutableState.asStateFlow()
    private val mutex = Mutex()

    public suspend fun load() {
        if (!mutex.tryLock()) return
        try {
            mutableState.value = mutableState.value.copy(loading = true, loadError = null)
            val snapshot = backend.read()
            mutableState.value = mutableState.value.copy(snapshot = snapshot, loading = false, loaded = true,
                loadError = null, saveError = null, refreshRequired = false)
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(loading = false)
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(loading = false, loadError = error.message ?: "无法读取模型设置", refreshRequired = true)
        } finally { mutex.unlock() }
    }

    public suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long = state.value.snapshot.revision): Boolean = mutate("provider:" + configuration.id, configuration = configuration) {
        configuration.validate()
        backend.save(configuration, expectedRevision)
    }

    public suspend fun remove(providerId: String, expectedRevision: Long = state.value.snapshot.revision): Boolean = mutate("remove:" + providerId) {
        require(mutableState.value.snapshot.providers.any { it.id == providerId && it.canRemove }) { "此 Provider 配置不能删除" }
        backend.remove(providerId, expectedRevision)
    }

    public suspend fun setCredential(name: String, value: String): Boolean = mutate("credential:" + name, secret = true) {
        require(validCredentialName(name) && value.isNotBlank())
        backend.setCredential(name, value)
    }

    public suspend fun retryApplication(): Boolean = mutate("apply") { backend.retryApplication() }

    public suspend fun discoverModels(providerId: String, expectedRevision: Long): Boolean {
        if (!state.value.canEdit) return false
        mutableState.value = state.value.copy(discoveringProviderId = providerId)
        try {
            return mutate("models:$providerId") { backend.discoverModels(providerId, expectedRevision) }
        } finally {
            mutableState.value = state.value.copy(discoveringProviderId = null)
        }
    }

    private suspend fun mutate(operation: String, secret: Boolean = false, configuration: ProviderConfiguration? = null, write: suspend () -> Unit): Boolean {
        if (!mutableState.value.canEdit || !mutex.tryLock()) return false
        var committed = false
        try {
            mutableState.value = mutableState.value.copy(saving = true, saveError = null, completedOperation = null, confirmedProviderConfiguration = null)
            write()
            committed = true
            mutableState.value = mutableState.value.copy(saveRevision = mutableState.value.saveRevision + 1, completedOperation = operation, confirmedProviderConfiguration = configuration)
            val snapshot = backend.read()
            mutableState.value = mutableState.value.copy(snapshot = snapshot, saving = false, refreshRequired = false)
            return true
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(saving = false, refreshRequired = committed)
            throw cancelled
        } catch (error: Exception) {
            val message = when {
                committed -> "已保存，但刷新状态失败。请刷新后继续编辑。"
                secret -> "无法保存凭据，请检查本地凭据服务后重试。"
                else -> error.message ?: "无法保存模型配置"
            }
            mutableState.value = mutableState.value.copy(saving = false, saveError = message,
                refreshRequired = committed || error is ProviderRevisionConflict)
            return false
        } finally { mutex.unlock() }
    }
}

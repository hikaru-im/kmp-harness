package im.hikaru.harness.client.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Call on the UI dispatcher. Owns only its requests, never the caller's CoroutineScope. */
public class InstalledPluginsController(
    private val coroutineScope: CoroutineScope,
    private val repository: InstalledPluginRepository = UnavailablePluginRepository,
) {
    private val mutableState = MutableStateFlow(InstalledPluginsState())
    public val state: StateFlow<InstalledPluginsState> = mutableState.asStateFlow()
    private var listJob: Job? = null
    private var detailJob: Job? = null
    private val management = repository as? LocalPluginManagementRepository
    private var mutationJob: Job? = null
    // This lock survives bind(): a replacement scope must not overlap the previous write.
    private var mutationBusy = false
    private var epoch: Long = 0
    private var listRequest: Long = 0
    private var detailRequest: Long = 0
    private var closed = false

    /** Binding the same scope is idempotent. New host/account/generation clears all old data. */
    public fun bind(scope: PluginScope?) {
        if (closed || mutableState.value.scope == scope) return
        invalidate()
        mutableState.value = if (scope == null) {
            InstalledPluginsState(mutationBusy = mutationBusy)
        } else {
            InstalledPluginsState(scope = scope, hasReadAdapter = repository.canRead,
                hasManagementAdapter = management?.canManage == true, mutationBusy = mutationBusy,
                hasLocalUpdateAdapter = management?.canUpdateLocal == true)
        }
        if (scope != null) load()
    }

    public fun dispatch(action: InstalledPluginsAction) {
        when (action) {
            InstalledPluginsAction.Refresh -> refresh()
            is InstalledPluginsAction.Search -> updateSearch(action.query)
            is InstalledPluginsAction.SetEnabledOnly -> setEnabledOnly(action.enabledOnly)
            is InstalledPluginsAction.Select -> select(action.id)
            InstalledPluginsAction.Back -> back()
            InstalledPluginsAction.RetryDetail -> {
                if (mutableState.value.canRetryDetail) mutableState.value.selectedId?.let { select(it) }
            }
            InstalledPluginsAction.BeginRegistration -> {
                if (!closed && state.value.canManage) mutableState.value = state.value.copy(
                    registrationDirectory = "", removeConfirmation = null)
            }
            is InstalledPluginsAction.ChangeRegistrationDirectory -> {
                if (!closed && !mutationBusy && state.value.registrationDirectory != null) {
                    mutableState.value = state.value.copy(registrationDirectory = action.directory)
                }
            }
            InstalledPluginsAction.CancelManagementDialog -> {
                if (!closed && !mutationBusy) mutableState.value = state.value.copy(
                    registrationDirectory = null, removeConfirmation = null)
            }
            InstalledPluginsAction.RegisterLocal -> registerLocal()
            is InstalledPluginsAction.SetEnabled -> setEnabled(action.id, action.enabled)
            is InstalledPluginsAction.UpdateLocal -> updateLocal(action.id)
            is InstalledPluginsAction.RequestRemove -> {
                val detail = (state.value.detail as? PluginLoadState.Success)?.value
                if (!closed && state.value.canManageDetail && detail?.plugin?.id == action.id) {
                    mutableState.value = state.value.copy(removeConfirmation = detail.plugin, registrationDirectory = null)
                }
            }
            InstalledPluginsAction.RemoveRegistration -> removeRegistration()
        }
    }

    public fun load(): Unit = loadList(clearMutationFailure = false)

    private fun loadList(clearMutationFailure: Boolean) {
        val scope = mutableState.value.scope ?: return
        if (closed) return
        val fenceEpoch = epoch
        val request = ++listRequest
        listJob?.cancel()
        val canManage = management?.canManage == true
        mutableState.value = state.value.copy(
            hasManagementAdapter = canManage, hasLocalUpdateAdapter = management?.canUpdateLocal == true,
            revision = PluginLoadState.NotLoaded,
            mutationRequiresRefresh = if (clearMutationFailure && !mutationBusy) false else state.value.mutationRequiresRefresh,
            mutationError = if (clearMutationFailure && !mutationBusy) null else state.value.mutationError,
        )
        if (!repository.canRead) {
            mutableState.value = mutableState.value.copy(
                list = PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
                searchQuery = "",
            )
            return
        }
        mutableState.value = mutableState.value.copy(list = PluginLoadState.Loading, searchQuery = "",
            revision = if (canManage) PluginLoadState.Loading else PluginLoadState.NotLoaded)
        listJob = coroutineScope.launch {
            val result = readSafely { repository.listInstalled(scope) }
            fun current(): Boolean = !closed && epoch == fenceEpoch && listRequest == request && state.value.scope == scope
            if (!current()) return@launch
            val revision = if (result is PluginResult.Success && management?.canManage == true) {
                readSafely { management.readRevision(scope) }.asLoadState()
            } else PluginLoadState.NotLoaded
            if (current()) {
                mutableState.value = mutableState.value.copy(list = result.asLoadState(), revision = revision,
                    hasManagementAdapter = management?.canManage == true,
                    hasLocalUpdateAdapter = management?.canUpdateLocal == true)
            }
        }
    }

    public fun refresh() {
        if (!closed && mutableState.value.canRefresh) {
            loadList(clearMutationFailure = true)
            state.value.selectedId?.let { select(it) }
        }
    }

    public fun updateSearch(query: String) {
        if (!closed && mutableState.value.canSearch) {
            mutableState.value = mutableState.value.copy(searchQuery = query)
        }
    }

    public fun setEnabledOnly(enabledOnly: Boolean) {
        if (!closed && mutableState.value.canSearch) {
            mutableState.value = mutableState.value.copy(enabledOnly = enabledOnly)
        }
    }

    /** Supports an authoritative detail route even when its id is absent from the installed list. */
    public fun select(id: PluginId) {
        val scope = mutableState.value.scope ?: return
        if (closed) return
        val fenceEpoch = epoch
        val request = ++detailRequest
        detailJob?.cancel()
        if (!repository.canRead) {
            mutableState.value = mutableState.value.copy(
                selectedId = id,
                detail = PluginLoadState.Unavailable(PluginUnavailableReason.PluginServiceNotExposed),
            )
            return
        }
        mutableState.value = mutableState.value.copy(selectedId = id, detail = PluginLoadState.Loading,
            removeConfirmation = if (state.value.selectedId == id) state.value.removeConfirmation else null)
        detailJob = coroutineScope.launch {
            val result = readSafely { repository.readDetail(scope, id) }
            val current = mutableState.value
            if (!closed && epoch == fenceEpoch && detailRequest == request && current.scope == scope && current.selectedId == id) {
                val detail = if (result is PluginResult.Success && result.value.plugin.id != id) {
                    PluginLoadState.Error(PluginRepositoryError("插件详情响应的 ID 与当前请求不匹配。"))
                } else result.asLoadState()
                mutableState.value = current.copy(detail = detail)
            }
        }
    }

    public fun back() {
        if (closed) return
        ++detailRequest
        detailJob?.cancel()
        mutableState.value = mutableState.value.copy(selectedId = null, detail = PluginLoadState.NotLoaded,
            removeConfirmation = null)
    }

    private fun registerLocal() {
        val current = state.value
        if (!current.canRegisterLocal) return
        val directory = current.registrationDirectory?.trim() ?: return
        mutate("已添加本地插件登记，默认停用。") { adapter, scope, revision ->
            adapter.registerLocal(scope, directory, revision)
        }
    }

    private fun setEnabled(id: PluginId, enabled: Boolean) {
        val current = state.value
        val plugin = (current.detail as? PluginLoadState.Success)?.value?.plugin ?: return
        if (!current.canManageDetail || plugin.id != id || plugin.enabled == PluginEnabledState.Unknown ||
            (plugin.enabled == PluginEnabledState.Enabled) == enabled) return
        mutate(if (enabled) "已启用；Skills 将用于下一次 Agent 请求。" else "已停用；下一次 Agent 请求将不再使用这些 Skills。") { adapter, scope, revision ->
            adapter.setEnabled(scope, id, enabled, revision)
        }
    }

    private fun updateLocal(id: PluginId) {
        val current = state.value
        val plugin = (current.detail as? PluginLoadState.Success)?.value?.plugin ?: return
        if (!current.canUpdateLocal || plugin.id != id || management?.canUpdateLocal != true) return
        mutate("已从源目录更新 Skills 快照，保留启用状态；下一次 Agent 请求生效。") { adapter, scope, revision ->
            adapter.updateLocal(scope, plugin.id, revision)
        }
    }

    private fun removeRegistration() {
        val current = state.value
        val plugin = current.removeConfirmation ?: return
        if (!current.canManageDetail || current.selectedId != plugin.id) return
        mutate("已移除登记；源目录和源文件均已保留。", removedId = plugin.id) { adapter, scope, revision ->
            adapter.removeRegistration(scope, plugin.id, revision)
        }
    }

    /** Freeze all write inputs. Only an explicit refresh can unlock a failed mutation. */
    private fun mutate(
        notice: String,
        removedId: PluginId? = null,
        operation: suspend (LocalPluginManagementRepository, PluginScope, String) -> PluginResult<PluginMutationReceipt>,
    ) {
        val current = state.value
        val adapter = management ?: return
        val scope = current.scope ?: return
        val revision = (current.revision as? PluginLoadState.Success)?.value ?: return
        if (closed || mutationBusy || !current.canManage || !adapter.canManage) return
        val fenceEpoch = epoch
        fun isCurrent(): Boolean = !closed && epoch == fenceEpoch && state.value.scope == scope
        mutationBusy = true
        mutableState.value = current.copy(mutationBusy = true, mutationError = null,
            mutationReceipt = null, managementNotice = null, revision = PluginLoadState.NotLoaded)
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                // bindAccess in the adapter additionally guards scope replacement before commit.
                if (!isCurrent() || !adapter.canManage) return@launch
                val result = readSafely { operation(adapter, scope, revision) }
                if (!isCurrent()) return@launch
                invalidateReads()
                when (result) {
                    is PluginResult.Success -> {
                        mutableState.value = state.value.copy(mutationReceipt = result.value, managementNotice = notice,
                            registrationDirectory = null, removeConfirmation = null, mutationRequiresRefresh = false)
                        if (removedId != null && state.value.selectedId == removedId) back()
                        loadList(clearMutationFailure = false)
                        state.value.selectedId?.let { select(it) }
                    }
                    else -> {
                        val message = when (result) {
                            is PluginResult.Error -> result.error.message
                            is PluginResult.Unavailable -> result.reason.message
                            is PluginResult.AuthRequired -> result.message
                            is PluginResult.UnsupportedHost -> result.message
                            is PluginResult.Success -> error("Handled above")
                        }
                        val invalidRead = PluginLoadState.Error(PluginRepositoryError("读取已失效，请显式刷新插件信息。"))
                        mutableState.value = state.value.copy(revision = PluginLoadState.NotLoaded,
                            mutationRequiresRefresh = true, mutationError = message + " 请显式刷新后再操作；不会自动重试。",
                            list = if (state.value.list is PluginLoadState.Loading) invalidRead else state.value.list,
                            detail = if (state.value.detail is PluginLoadState.Loading) invalidRead else state.value.detail)
                    }
                }
            } finally {
                mutationBusy = false
                mutationJob = null
                if (!closed) mutableState.value = state.value.copy(mutationBusy = false)
            }
        }
        mutationJob = job
        job.start()
    }

    public fun close() {
        if (closed) return
        closed = true
        invalidate()
        mutationJob?.cancel()
        mutableState.value = InstalledPluginsState()
    }

    private fun invalidate() {
        ++epoch
        invalidateReads()
    }

    private fun invalidateReads() {
        ++listRequest
        ++detailRequest
        listJob?.cancel()
        detailJob?.cancel()
    }

    private suspend fun <T> readSafely(read: suspend () -> PluginResult<T>): PluginResult<T> = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        PluginResult.Error(PluginRepositoryError(failure.message ?: "插件服务读取失败。"))
    }
}

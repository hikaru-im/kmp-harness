package im.hikaru.harness.client.plugins

import im.hikaru.contracts.harness.identity.HostId
import kotlin.jvm.JvmInline

@JvmInline
public value class PluginAccountId(public val value: String) {
    init { require(value.isNotBlank()) }
}

@JvmInline
public value class PluginId(public val value: String) {
    init { require(value.isNotBlank()) }
}

/**
 * Generation must change when a connection is replaced, even for the same host/account.
 * Parent maps the complete MemberIdentity.toString() to accountId, including tenant identity.
 */
public data class PluginScope(
    val hostId: HostId,
    val generation: Long,
    val accountId: PluginAccountId? = null,
)

public enum class PluginEnabledState { Enabled, Disabled, Unknown }

/** Counts may be displayed only after an adapter actually reads the plugin's Apps. */
public sealed interface PluginAppsState {
    public data object NotLoaded : PluginAppsState
    public data class Available(val names: List<String>) : PluginAppsState
    public data class Unavailable(val reason: String) : PluginAppsState
}

/** User-installed plugin data; the Harness runtime startup catalog is not a source for this DTO. */
public data class InstalledPlugin(
    val id: PluginId,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val source: String? = null,
    val enabled: PluginEnabledState = PluginEnabledState.Unknown,
)

public enum class PluginCapabilityKind { McpServer, Hook, Script }

/** Names read from a plugin manifest; configuration and execution remain outside this read-only DTO. */
public data class PluginCapabilitySummary(
    val kind: PluginCapabilityKind,
    val names: List<String>,
)

public data class InstalledPluginDetail(
    val plugin: InstalledPlugin,
    val apps: PluginAppsState = PluginAppsState.NotLoaded,
    val skills: PluginSkillsState = PluginSkillsState.NotLoaded,
    val unsupportedAssets: List<String> = emptyList(),
    val capabilities: List<PluginCapabilitySummary> = emptyList(),
)

public enum class PluginUnavailableReason(public val message: String) {
    PluginServiceNotExposed("当前 Harness Connection 未暴露用户插件的列表和详情读取服务。运行时启动插件目录不能作为已安装用户插件数据。"),
}

public data class PluginRepositoryError(val message: String)

/** A repository returns a result for the supplied scope; the controller fences every response. */
public sealed interface PluginResult<out T> {
    public data class Success<T>(val value: T) : PluginResult<T>
    public data class Error(val error: PluginRepositoryError) : PluginResult<Nothing>
    public data class Unavailable(val reason: PluginUnavailableReason) : PluginResult<Nothing>
    public data class AuthRequired(val message: String) : PluginResult<Nothing>
    public data class UnsupportedHost(val message: String) : PluginResult<Nothing>
}

public sealed interface PluginLoadState<out T> {
    public data object NotLoaded : PluginLoadState<Nothing>
    public data object Loading : PluginLoadState<Nothing>
    public data class Success<T>(val value: T) : PluginLoadState<T>
    public data class Error(val error: PluginRepositoryError) : PluginLoadState<Nothing>
    public data class Unavailable(val reason: PluginUnavailableReason) : PluginLoadState<Nothing>
    public data class AuthRequired(val message: String) : PluginLoadState<Nothing>
    public data class UnsupportedHost(val message: String) : PluginLoadState<Nothing>
}

public data class InstalledPluginsState(
    val scope: PluginScope? = null,
    val list: PluginLoadState<List<InstalledPlugin>> = PluginLoadState.NotLoaded,
    val detail: PluginLoadState<InstalledPluginDetail> = PluginLoadState.NotLoaded,
    val selectedId: PluginId? = null,
    val searchQuery: String = "",
    val enabledOnly: Boolean = false,
    val hasReadAdapter: Boolean = false,
    val hasManagementAdapter: Boolean = false,
    val revision: PluginLoadState<String> = PluginLoadState.NotLoaded,
    val mutationBusy: Boolean = false,
    val mutationRequiresRefresh: Boolean = false,
    val mutationError: String? = null,
    val mutationReceipt: PluginMutationReceipt? = null,
    val managementNotice: String? = null,
    val registrationDirectory: String? = null,
    val removeConfirmation: InstalledPlugin? = null,
    val hasLocalUpdateAdapter: Boolean = false,
) {
    public val canManage: Boolean
        get() = scope != null && hasManagementAdapter && list is PluginLoadState.Success &&
            revision is PluginLoadState.Success && !mutationBusy && !mutationRequiresRefresh
    public val canManageDetail: Boolean
        get() = canManage && detail is PluginLoadState.Success && detail.value.plugin.id == selectedId
    public val canUpdateLocal: Boolean get() = hasLocalUpdateAdapter && canManageDetail
    public val registrationDirectoryIsAbsolute: Boolean
        get() {
            val path = registrationDirectory?.trim() ?: return false
            return path.isNotEmpty() && path.none { it == '\u0000' || it == '\n' || it == '\r' } &&
                (path.startsWith("/") || path.startsWith("\\\\") ||
                    Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(path))
        }
    public val canRegisterLocal: Boolean get() = canManage && registrationDirectoryIsAbsolute
    public val canSearch: Boolean get() = list is PluginLoadState.Success
    public val canRefresh: Boolean
        get() = scope != null && hasReadAdapter && list !is PluginLoadState.Loading &&
            list !is PluginLoadState.Unavailable && list !is PluginLoadState.UnsupportedHost
    public val canRetryDetail: Boolean
        get() = scope != null && selectedId != null && hasReadAdapter && detail is PluginLoadState.Error

    /** Null means there is no successful list to filter; it must never become an empty success. */
    public val visiblePlugins: List<InstalledPlugin>?
        get() {
            val loaded = (list as? PluginLoadState.Success)?.value ?: return null
            val query = searchQuery.trim()
            return loaded.filter {
                (!enabledOnly || it.enabled == PluginEnabledState.Enabled) &&
                    (query.isEmpty() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.description?.contains(query, ignoreCase = true) == true ||
                        it.source?.contains(query, ignoreCase = true) == true)
            }
        }
}

public sealed interface InstalledPluginsAction {
    public data object Refresh : InstalledPluginsAction
    public data class Search(val query: String) : InstalledPluginsAction
    public data class SetEnabledOnly(val enabledOnly: Boolean) : InstalledPluginsAction
    public data class Select(val id: PluginId) : InstalledPluginsAction
    public data object Back : InstalledPluginsAction
    public data object RetryDetail : InstalledPluginsAction
    public data object BeginRegistration : InstalledPluginsAction
    public data class ChangeRegistrationDirectory(val directory: String) : InstalledPluginsAction
    public data object CancelManagementDialog : InstalledPluginsAction
    public data object RegisterLocal : InstalledPluginsAction
    public data class SetEnabled(val id: PluginId, val enabled: Boolean) : InstalledPluginsAction
    public data class UpdateLocal(val id: PluginId) : InstalledPluginsAction
    public data class RequestRemove(val id: PluginId) : InstalledPluginsAction
    public data object RemoveRegistration : InstalledPluginsAction
}

internal fun <T> PluginResult<T>.asLoadState(): PluginLoadState<T> = when (this) {
    is PluginResult.Success -> PluginLoadState.Success(value)
    is PluginResult.Error -> PluginLoadState.Error(error)
    is PluginResult.Unavailable -> PluginLoadState.Unavailable(reason)
    is PluginResult.AuthRequired -> PluginLoadState.AuthRequired(message)
    is PluginResult.UnsupportedHost -> PluginLoadState.UnsupportedHost(message)
}

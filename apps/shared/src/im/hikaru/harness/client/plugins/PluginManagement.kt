package im.hikaru.harness.client.plugins

import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport

/** Supplied only after the app has verified the connection's Host description. */
public data class PluginAccessBinding(
    val scope: PluginScope,
    val connection: Connection,
    val transport: ConnectionTransport,
)

/** Optional local management capability, separate from the read-only repository. */
public interface LocalPluginManagementRepository : InstalledPluginRepository {
    public fun bindAccess(binding: PluginAccessBinding?)
    public val canManage: Boolean
    public val canUpdateLocal: Boolean get() = false
    public suspend fun updateLocal(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        PluginResult.UnsupportedHost("当前插件服务不支持从源目录更新。")
    public suspend fun readRevision(scope: PluginScope): PluginResult<String>
    public suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String): PluginResult<PluginMutationReceipt>
    public suspend fun setEnabled(scope: PluginScope, id: PluginId, enabled: Boolean, expectedRevision: String): PluginResult<PluginMutationReceipt>
    public suspend fun removeRegistration(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt>
}

public data class PluginMutationReceipt(val revision: String)

public data class PluginSkillSummary(val name: String, val description: String? = null)

public sealed interface PluginSkillsState {
    public data object NotLoaded : PluginSkillsState
    public data class Available(val skills: List<PluginSkillSummary>) : PluginSkillsState
    public data class Unavailable(val reason: String) : PluginSkillsState
}

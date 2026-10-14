package im.hikaru.harness.desktop.plugins

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.bundle.desktop.DesktopUserPlugin
import im.hikaru.harness.bundle.desktop.DesktopUserPlugins
import im.hikaru.harness.bundle.desktop.DesktopUserPluginsConflict
import im.hikaru.harness.bundle.desktop.DesktopUserPluginsException
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.desktop.connection.LocalConnection
import kotlinx.coroutines.CancellationException

/** Owns access to one in-process Host and its Harness-managed Skills snapshots. */
public class DesktopInstalledPluginRepository(
    private val localHostId: HostId,
    private val expectedConnection: LocalConnection,
    private val service: DesktopUserPlugins,
) : LocalPluginManagementRepository {
    @Volatile private var access: PluginAccessBinding? = null
    @Volatile private var listedRevision: Pair<PluginAccessBinding, String>? = null
    override val canRead: Boolean = true
    override val canManage: Boolean get() = access != null
    override val canUpdateLocal: Boolean get() = canManage

    override fun bindAccess(binding: PluginAccessBinding?) {
        val accepted = binding?.takeIf {
            it.transport == ConnectionTransport.Local && it.connection === expectedConnection &&
                it.scope.hostId == localHostId
        }
        // Keep an in-flight write's lease valid when an effect repeats the same binding.
        if (access != accepted) {
            access = accepted
            listedRevision = null
        }
    }

    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> =
        guarded(scope) { binding ->
            val snapshot = service.read()
            if (current(binding)) listedRevision = binding to snapshot.revision
            snapshot.plugins.map { it.toModel() }
        }

    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> =
        guarded(scope) {
            val plugin = service.read().plugins.singleOrNull { it.id == id.value }
                ?: throw DesktopUserPluginsException("插件登记已不存在，请刷新列表。")
            InstalledPluginDetail(
                plugin = plugin.toModel(),
                // Inline app names and the manifest-declared apps file are consumed; an apps directory
                // or an unrecognized apps declaration stays unavailable instead of being guessed.
                apps = when {
                    plugin.applications.isNotEmpty() -> PluginAppsState.Available(plugin.applications)
                    "apps (unsupported)" in plugin.unsupportedAssets ->
                        PluginAppsState.Unavailable("此插件的 Apps 声明无法在本地枚举（例如 apps 目录或未识别的 apps 文件），本地服务尚未读取。")
                    else -> PluginAppsState.Available(emptyList())
                },
                skills = PluginSkillsState.Available(plugin.skills.map { PluginSkillSummary(it.name, it.description) }),
                unsupportedAssets = plugin.unsupportedAssets,
                capabilities = buildList {
                    if (plugin.capabilities.mcpServers.isNotEmpty()) {
                        add(PluginCapabilitySummary(PluginCapabilityKind.McpServer, plugin.capabilities.mcpServers))
                    }
                    if (plugin.capabilities.hooks.isNotEmpty()) {
                        add(PluginCapabilitySummary(PluginCapabilityKind.Hook, plugin.capabilities.hooks))
                    }
                    if (plugin.capabilities.scripts.isNotEmpty()) {
                        add(PluginCapabilitySummary(PluginCapabilityKind.Script, plugin.capabilities.scripts))
                    }
                },
            )
        }

    override suspend fun readRevision(scope: PluginScope): PluginResult<String> =
        guarded(scope) { binding ->
            // The CAS token belongs to the rendered list, even if another writer changes the file
            // between these two calls. A fresh unrelated revision would bless stale UI inputs.
            listedRevision?.takeIf { it.first === binding }?.second
                ?: throw DesktopUserPluginsException("请先刷新插件列表再操作。")
        }

    override suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        guarded(scope) { binding ->
            PluginMutationReceipt(service.registerLocal(directory, expectedRevision) { current(binding) }.revision)
        }

    override suspend fun setEnabled(scope: PluginScope, id: PluginId, enabled: Boolean, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        guarded(scope) { binding ->
            PluginMutationReceipt(service.setEnabled(id.value, enabled, expectedRevision) { current(binding) }.revision)
        }

    override suspend fun updateLocal(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        guarded(scope) { binding ->
            PluginMutationReceipt(service.updateLocal(id.value, expectedRevision) { current(binding) }.revision)
        }

    override suspend fun removeRegistration(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        guarded(scope) { binding ->
            PluginMutationReceipt(service.removeRegistration(id.value, expectedRevision) { current(binding) }.revision)
        }

    private fun current(binding: PluginAccessBinding): Boolean =
        access === binding && binding.connection === expectedConnection && binding.transport == ConnectionTransport.Local

    private suspend fun <T> guarded(scope: PluginScope, operation: suspend (PluginAccessBinding) -> T): PluginResult<T> {
        val binding = access?.takeIf { it.scope == scope }
            ?: return PluginResult.UnsupportedHost("插件管理仅用于当前已验证的本机 Host。")
        return try {
            val response = expectedConnection.host.describe()
            val description = response.data
            if (!current(binding) || !response.isSuccess || description?.hostId != localHostId ||
                description.protocolVersion.major != ProtocolVersion.Current.major) {
                PluginResult.UnsupportedHost("本机 Host 连接已改变，请重新连接。")
            } else {
                val value = operation(binding)
                if (current(binding)) PluginResult.Success(value)
                else PluginResult.UnsupportedHost("Host 已切换；旧操作结果不会用于当前页面。")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: DesktopUserPluginsConflict) {
            PluginResult.Error(PluginRepositoryError("插件配置已改变，请刷新后重新操作。"))
        } catch (error: DesktopUserPluginsException) {
            PluginResult.Error(PluginRepositoryError(error.message ?: "插件文件读取或保存失败。"))
        } catch (_: Exception) {
            PluginResult.Error(PluginRepositoryError("无法读取或保存本机插件，请检查连接和文件权限。"))
        }
    }
}

private fun DesktopUserPlugin.toModel(): InstalledPlugin = InstalledPlugin(
    id = PluginId(id), name = name, description = description, version = version, source = source,
    enabled = if (enabled) PluginEnabledState.Enabled else PluginEnabledState.Disabled,
)

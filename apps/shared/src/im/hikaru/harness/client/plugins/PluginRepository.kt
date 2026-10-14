package im.hikaru.harness.client.plugins

/** Read-only boundary. Implementations must read an actual user-plugin service for each scope. */
public interface InstalledPluginRepository {
    /** True only when this implementation has a real list/detail consumer. */
    public val canRead: Boolean
    public suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>>
    public suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail>
}

/** Production default until Connection exposes a user-plugin service. */
public object UnavailablePluginRepository : InstalledPluginRepository {
    override val canRead: Boolean = false
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> =
        PluginResult.Unavailable(PluginUnavailableReason.PluginServiceNotExposed)
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> =
        PluginResult.Unavailable(PluginUnavailableReason.PluginServiceNotExposed)
}

package im.hikaru.harness.mcp.client

import im.hikaru.contracts.harness.mcp.McpAuthStatus
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MCP_REGISTRY_PLUGIN_NAME = "mcp-registry"
object McpRegistryKey : ServiceKey<McpRegistry>("mcp-registry")

enum class McpConnectionPhase { Connecting, Connected, Reconnecting, Failed }
data class McpServerSnapshot(
    val serverName: String,
    val phase: McpConnectionPhase,
    val generation: Long = 0,
    /** null means not discovered, not a fabricated zero count. */
    val toolCount: Int? = null,
    val resourcesAvailable: Boolean = false,
    val failureCode: String? = null,
    /** Auth metadata comes from the Host; a successful connection alone never proves authorization. */
    val authStatus: McpAuthStatus = McpAuthStatus.Unknown,
    val schemaCompatibility: String = "sdk-0.11-partial-top-level-schema",
)

/** Host-local, effect-owned registry. No SDK client or resolved secrets appear in snapshots. */
class McpRegistry : Disposable {
    private val lock = Any()
    private val owners = linkedMapOf<String, Any>()
    private val providers = linkedMapOf<String, McpSession>()
    private var disposed = false
    private val mutable = MutableStateFlow<Map<String, McpServerSnapshot>>(emptyMap())
    val states: StateFlow<Map<String, McpServerSnapshot>> = mutable.asStateFlow()

    fun reserve(name: String): Lease = synchronized(lock) {
        check(!disposed) { "MCP registry is disposed" }
        check(name !in owners) { "MCP server namespace is already reserved" }
        val token = Any()
        owners[name] = token
        mutable.value = mutable.value + (name to McpServerSnapshot(name, McpConnectionPhase.Connecting))
        Lease(name, token)
    }

    fun resourceProviders(): Map<String, McpSession> = synchronized(lock) { providers.toMap() }

    inner class Lease internal constructor(private val name: String, private val token: Any) : Disposable {
        fun publish(snapshot: McpServerSnapshot, session: McpSession? = null) = synchronized(lock) {
            check(!disposed && owners[name] === token) { "MCP server lease is disposed" }
            require(snapshot.serverName == name)
            if (snapshot.phase == McpConnectionPhase.Connected && session?.supportsResources == true) providers[name] = session
            else providers.remove(name)
            mutable.value = mutable.value + (name to snapshot)
        }
        override suspend fun dispose() = synchronized(lock) {
            if (owners[name] === token) {
                owners.remove(name)
                providers.remove(name)
                mutable.value = mutable.value - name
            }
            Unit
        }
    }

    override suspend fun dispose() = synchronized(lock) {
        disposed = true
        owners.clear()
        providers.clear()
        mutable.value = emptyMap()
    }
}

class McpRegistryPlugin : SimplePlugin {
    override suspend fun apply(context: Context, scope: EffectScope) {
        val registry = McpRegistry()
        scope.add(registry)
        scope.add(context.provide(McpRegistryKey, registry))
    }
}
val McpRegistryPluginDefinition = pluginDefinition(MCP_REGISTRY_PLUGIN_NAME, McpRegistryPlugin())

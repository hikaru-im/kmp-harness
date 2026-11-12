package im.hikaru.harness.mcp.resources

import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.mcp.client.McpRegistryKey
import im.hikaru.harness.mcp.client.projectMcpResource
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.tools.*
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.*

const val MCP_RESOURCES_PLUGIN_NAME = "mcp-resources"

/** Resource tools share the client plugin's connection; they never spawn or fetch URIs themselves. */
class McpResourcesPlugin : SimplePlugin {
    override val inject = setOf(InjectSpec.required(ToolsKey), InjectSpec.required(McpRegistryKey))

    override suspend fun apply(context: Context, scope: EffectScope) {
        val registry = context.require(McpRegistryKey)
        val batch = context.require(ToolsKey).registrationBatch()
        scope.add(batch)
        val registrations = listOf(
            registration("list_mcp_resources", "List resources from connected MCP servers", false, context),
            registration("list_mcp_resource_templates", "List resource templates from connected MCP servers", false, context),
            registration("read_mcp_resource", "Read an MCP resource by its server and URI", true, context),
        )
        fun sync() { batch.replace(if (registry.resourceProviders().isEmpty()) emptyList() else registrations) }
        sync()
        val watcher = context.managedScope().launch { registry.states.collect { sync() } }
        scope.add { watcher.cancelAndJoin() }
    }
}

private fun registration(name: String, description: String, read: Boolean, context: Context): ToolRegistration {
    val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("server", buildJsonObject { put("type", "string") })
            if (read) put("uri", buildJsonObject { put("type", "string") })
        })
        if (read) put("required", buildJsonArray { add("server"); add("uri") })
        put("additionalProperties", false)
    }
    return ToolRegistration(ToolSchema(name, description, parameters), object : ContextualToolHandler {
        override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
            require(execution.context.root === context.root) { "MCP resource execution belongs to another Host" }
            require(request.scope == null || request.scope == execution.session.id.value) { "MCP scope mismatch" }
            val arguments = try { Json.parseToJsonElement(request.arguments) as? JsonObject } catch (_: Exception) { null }
                ?: return failure("MCP arguments must be a JSON object")
            if (arguments.keys.any { it != "server" && !(read && it == "uri") }) return failure("Unknown MCP resource argument")
            fun string(key: String): String? = (arguments[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val server = string("server")
            if ("server" in arguments && server.isNullOrBlank()) return failure("Invalid MCP server")
            val providers = context.require(McpRegistryKey).resourceProviders()
            val selected = if (server != null) providers.filterKeys { it == server } else providers
            if (selected.isEmpty()) return failure("MCP resource provider is unavailable")
            return try {
                if (read) {
                    val uri = string("uri")?.takeIf { it.isNotBlank() && it.length <= 16_384 && '\u0000' !in it }
                        ?: return failure("MCP resource URI is required")
                    if (server == null) return failure("MCP server is required")
                    val provider = selected.getValue(server)
                    val result = provider.readResource(uri)
                    if (context.require(McpRegistryKey).resourceProviders()[server] !== provider) return failure("MCP resource provider changed")
                    projectMcpResource(result)
                } else {
                    val entries = selected.flatMap { (id, provider) ->
                        val values = if (name == "list_mcp_resources") provider.listResources().map { McpJson.encodeToJsonElement(it) }
                            else provider.listResourceTemplates().map { McpJson.encodeToJsonElement(it) }
                        if (context.require(McpRegistryKey).resourceProviders()[id] !== provider) return failure("MCP resource provider changed")
                        values.map { value -> buildJsonObject { put("server", id); put("resource", value) } }
                    }
                    ToolExecutionResult(listOf(TextBlock(JsonArray(entries).toString())))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure("MCP resource operation failed") }
        }
    })
}

private fun failure(message: String) = ToolExecutionResult(listOf(TextBlock(message)), true)
val McpResourcesPluginDefinition = pluginDefinition(MCP_RESOURCES_PLUGIN_NAME, McpResourcesPlugin())

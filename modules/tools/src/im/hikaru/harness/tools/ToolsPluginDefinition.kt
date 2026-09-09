package im.hikaru.harness.tools

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

public const val TOOLS_PLUGIN_NAME: String = "tools"

public val ToolsPluginDefinition: PluginDefinition =
    pluginDefinition(
        name = TOOLS_PLUGIN_NAME,
        plugin = ConfiguredToolsPlugin,
        config = jsonObjectConfig(allowMissing = true, ::decodeToolsConfig),
    )

private object ConfiguredToolsPlugin : Plugin<ToolsConfig> {
    override suspend fun apply(context: Context, config: ToolsConfig, scope: EffectScope) {
        ToolsPlugin(config).apply(context, scope)
    }
}

private fun decodeToolsConfig(value: JsonObject): ToolsConfig {
    val allowed = setOf("maxConcurrentCalls")
    require(value.keys.all(allowed::contains)) {
        "tools config contains unknown fields: ${(value.keys - allowed).sorted().joinToString()}"
    }
    val maxConcurrentCalls = value["maxConcurrentCalls"]?.let { element ->
        (element as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull
            ?: error("tools maxConcurrentCalls must be an integer")
    } ?: 1
    return ToolsConfig(maxConcurrentCalls)
}

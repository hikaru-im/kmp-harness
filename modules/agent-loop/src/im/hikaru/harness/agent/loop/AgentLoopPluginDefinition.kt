package im.hikaru.harness.agent.loop

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

public val AgentLoopPluginDefinition: PluginDefinition =
    pluginDefinition(
        name = AGENT_LOOP_PLUGIN_NAME,
        plugin = ConfiguredAgentLoopPlugin,
        config = jsonObjectConfig(allowMissing = true, ::decodeAgentLoopConfig),
    )

private object ConfiguredAgentLoopPlugin : Plugin<AgentLoopConfig> {
    override val inject = AgentLoopPlugin().inject

    override suspend fun apply(context: Context, config: AgentLoopConfig, scope: EffectScope) {
        AgentLoopPlugin(config).apply(context, scope)
    }
}

private fun decodeAgentLoopConfig(value: JsonObject): AgentLoopConfig {
    val allowed = setOf("maxParallelToolCalls")
    require(value.keys.all(allowed::contains)) {
        "agent-loop config contains unknown fields: ${(value.keys - allowed).sorted().joinToString()}"
    }
    val maxParallelToolCalls = value["maxParallelToolCalls"]?.let { element ->
        (element as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull
            ?: error("agent-loop maxParallelToolCalls must be an integer")
    }
    return AgentLoopConfig(
        maxParallelToolCalls = maxParallelToolCalls ?: AgentLoopConfig().maxParallelToolCalls,
    )
}

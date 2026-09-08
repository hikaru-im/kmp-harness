package im.hikaru.harness.agent

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin

const val AGENT_PLUGIN_NAME: String = "agent"

class AgentPlugin : SimplePlugin {
    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val registry = AgentRegistry(context)
        scope.add(context.provide(AgentKey, registry))
        scope.add(registry)
    }
}

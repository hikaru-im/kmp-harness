package im.hikaru.harness.agent

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.SessionKey

const val AGENT_PLUGIN_NAME: String = "agent"

class AgentPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(InjectSpec.required(SessionKey))

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val registry = AgentRegistry(context)
        scope.add(context.provide(AgentKey, registry))
        scope.add(registry)
    }
}

package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.AgentFactory
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.llm.LlmKey
import im.hikaru.harness.tools.ToolsKey
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin

const val AGENT_LOOP_PLUGIN_NAME: String = "agent-loop"

/** Installs the default factory; creating an agent remains an explicit operation. */
class AgentLoopPlugin(
    private val config: AgentLoopConfig = AgentLoopConfig(),
) : SimplePlugin {
    override val inject: Set<InjectSpec> =
        setOf(
            InjectSpec.required(AgentKey),
            InjectSpec.required(LlmKey),
            InjectSpec.optional(ToolsKey),
        )

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val registry: AgentRegistry = context.require(AgentKey)
        val handle =
            registry.registerFactory(
                AgentFactory { request ->
                    val callConfig = request.options.requireCallConfig()
                    AgentLoopAgent(
                        request = request,
                        llm = context.require(LlmKey),
                        config = config,
                        callConfig = callConfig,
                    )
                }
            )
        scope.add(handle)
    }
}

package im.hikaru.harness.agent.loop

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentFactoryRequest
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.agent.QuiescentRestorableFactory
import im.hikaru.harness.llm.LlmRuntime
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
                LoopAgentFactory(context.require(LlmKey), config)
            )
        scope.add(handle)
    }
}

/**
 * The default loop Agent starts a driver only from an explicit [Agent.send] or an attempt resume,
 * so it declares quiescent-restorable support: attaching it to a restored Session issues no model
 * request until the user acts.
 */
private class LoopAgentFactory(
    private val llm: LlmRuntime,
    private val config: AgentLoopConfig,
) : QuiescentRestorableFactory {
    override suspend fun create(request: AgentFactoryRequest): Agent {
        val callConfig = request.options.requireCallConfig()
        return AgentLoopAgent(
            request = request,
            llm = llm,
            config = config,
            callConfig = callConfig,
        )
    }
}

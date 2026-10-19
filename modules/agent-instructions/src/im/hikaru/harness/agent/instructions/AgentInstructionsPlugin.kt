package im.hikaru.harness.agent.instructions

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.system.prompt.PromptSection
import im.hikaru.harness.system.prompt.PromptSectionOrders
import im.hikaru.harness.system.prompt.SystemPromptKey

public const val AGENT_INSTRUCTIONS_PLUGIN_NAME: String = "agent-instructions"

/**
 * Publishes one [AgentInstructionsService] and contributes its fresh snapshot
 * as an ordered system-prompt section. Instructions are read at assembly time,
 * so each step observes the source's current state without accumulation.
 */
public class AgentInstructionsPlugin(
    private val source: AgentInstructionsService,
) : SimplePlugin {
    override val inject: Set<InjectSpec> =
        setOf(
            InjectSpec.required(SystemPromptKey),
        )

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        scope.add(context.provide(AgentInstructionsKey, source))
        val prompt = context.require(SystemPromptKey)
        scope.add(
            prompt.section(
                PromptSection(
                    name = AGENT_INSTRUCTIONS_PLUGIN_NAME,
                    order = PromptSectionOrders.AgentInstructions,
                    text = { assembly ->
                        assembly.agent
                            ?.let { agent -> source.readInstructions(agent) }
                            ?.takeIf { it.isNotBlank() }
                            ?: ""
                    },
                ),
            ),
        )
    }
}

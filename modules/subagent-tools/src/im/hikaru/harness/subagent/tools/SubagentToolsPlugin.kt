package im.hikaru.harness.subagent.tools

import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.sessions
import im.hikaru.harness.tools.ToolsKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val SUBAGENT_TOOLS_PLUGIN_NAME: String = "subagent-tools"
const val SPAWN_AGENT_TOOL_NAME: String = "spawn_agent"

/** Flat primitive schema supported by the production Koog tool mapper. */
val SpawnAgentToolSchema: ToolSchema = ToolSchema(
    name = SPAWN_AGENT_TOOL_NAME,
    description = "Create a child agent of the calling live agent and enqueue its prompt. " +
        "Specify an explicit provider and model. Acceptance does not prove completion. " +
        "On prompt failure keep the returned child ID; do not automatically spawn another child.",
    parameters = Json.parseToJsonElement(
        """{"type":"object","properties":{"prompt":{"type":"string","description":"Child task prompt"},"provider":{"type":"string","description":"Explicit provider ID"},"model":{"type":"string","description":"Explicit model ID"}},"required":["prompt","provider","model"]}""",
    ).jsonObject,
)

class SubagentToolsPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey), InjectSpec.required(SessionKey), InjectSpec.required(ToolsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val handler = SpawnAgentHandler(context, context.agents, context.sessions)
        // Stop admissions and release owned handles after unregistering the tool.
        scope.add(handler)
        scope.add(context.require(ToolsKey).register(SpawnAgentToolSchema, handler))
    }
}

val SubagentToolsPluginDefinition: PluginDefinition =
    pluginDefinition(SUBAGENT_TOOLS_PLUGIN_NAME, SubagentToolsPlugin())

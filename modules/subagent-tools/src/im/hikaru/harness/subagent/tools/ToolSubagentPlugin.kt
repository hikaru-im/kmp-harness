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

const val TOOL_SUBAGENT_PLUGIN_NAME: String = "tool-subagent"
const val SUBAGENT_TOOL_NAME: String = "subagent"

/** Delegation entry point. Argument names and defaults follow DSH tool-subagent. */
val SubagentToolSchema: ToolSchema = ToolSchema(
    name = SUBAGENT_TOOL_NAME,
    description = "Delegate a self-contained task to a subagent (a separate agent that works in its own " +
        "context) to offload focused, independent work — research, a scoped implementation, an analysis — " +
        "so it does not consume this conversation's context. The subagent returns its result, not its " +
        "intermediate steps. This tool starts an independently managed subagent and immediately returns its " +
        "id. Use send_message to steer it while running or continue its conversation after it finishes.",
    parameters = Json.parseToJsonElement(
        """{"type":"object","properties":{"description":{"type":"string","description":"A short (3-5 word) description of the delegated task, for display."},"prompt":{"type":"string","description":"The complete, self-contained task for the subagent. It does not share this conversation's context, so include everything it needs."},"cwd":{"type":"string","description":"Initial child working directory. Omitted inherits the calling agent's directory."},"provider":{"type":"string","description":"LLM provider route for the child. Supply together with model; omit both to inherit the parent route."},"model":{"type":"string","description":"Model id interpreted by provider. Supply together with provider; omit both to inherit the parent route."},"reasoning_effort":{"type":"string","description":"Adapter-owned reasoning effort for the child route. Omit to inherit the parent route's effort."}},"required":["description","prompt"]}""",
    ).jsonObject,
)

class ToolSubagentPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey), InjectSpec.required(SessionKey), InjectSpec.required(ToolsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val handler = SubagentToolHandler(context, context.agents, context.sessions)
        // Stop admissions and release owned handles after unregistering the tool.
        scope.add(handler)
        scope.add(context.require(ToolsKey).register(SubagentToolSchema, handler))
    }
}

val ToolSubagentPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_SUBAGENT_PLUGIN_NAME, ToolSubagentPlugin())

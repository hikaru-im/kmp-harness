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
const val DEFAULT_SUBAGENT_MAX_DEPTH: Long = 1L
const val DEFAULT_MAX_ACTIVE_SUBAGENTS: Int = 8

/** Delegation entry point. Argument names and defaults follow DSH tool-subagent. */
val SubagentToolSchema: ToolSchema = ToolSchema(
    name = SUBAGENT_TOOL_NAME,
    description = "Delegate a self-contained task to a subagent (a separate agent that works in its own " +
        "context) to offload focused, independent work — research, a scoped implementation, an analysis — " +
        "so it does not consume this conversation's context. The subagent returns its result, not its " +
        "intermediate steps. This tool starts an independently managed subagent and immediately returns its " +
        "id. Use send_message to steer it while running or continue its conversation after it finishes.",
    parameters = Json.parseToJsonElement(
        """{
          "type":"object",
          "properties":{
            "description":{"type":"string","description":"A short (3-5 word) description of the delegated task, for display."},
            "prompt":{"type":"string","description":"The complete, self-contained task for the subagent. It does not share this conversation's context, so include everything it needs."},
            "cwd":{"type":"string","description":"Initial child working directory. Omitted inherits the calling agent's directory."},
            "provider":{"type":"string","description":"LLM provider route for the child. Supply together with model; omit both to inherit the parent route."},
            "model":{"type":"string","description":"Model id interpreted by provider. Supply together with provider; omit both to inherit the parent route."},
            "reasoning_effort":{"type":"string","description":"Legacy effort override. Cannot be combined with reasoning."},
            "reasoning":{
              "type":"object",
              "description":"Unified reasoning override. Omit to inherit; an empty object explicitly follows provider defaults. Options must be supported by the selected model.",
              "properties":{
                "mode":{"type":"string","description":"provider-default, disabled, enabled, adaptive, or a registered model-specific mode."},
                "effort":{"type":"string"},
                "budgetTokens":{"type":"integer","minimum":0},
                "executionModeId":{"type":"string"}
              },
              "additionalProperties":false
            }
          },
          "required":["description","prompt"],
          "additionalProperties":false
        }""",
    ).jsonObject,
)

/** Delegation limits mirror DSH's `maxDepth` / `maxActiveSubagents` service config. */
class ToolSubagentPlugin(
    private val maxDepth: Long = DEFAULT_SUBAGENT_MAX_DEPTH,
    private val maxActiveSubagents: Int = DEFAULT_MAX_ACTIVE_SUBAGENTS,
) : SimplePlugin {
    init {
        require(maxDepth >= 0L) { "maxDepth must not be negative" }
        require(maxActiveSubagents >= 1) { "maxActiveSubagents must be positive" }
    }

    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey), InjectSpec.required(SessionKey), InjectSpec.required(ToolsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val settlement = SubagentSettlement(context, context.agents)
        // Handlers release their children first; the settlement watchers stop last.
        scope.add(settlement)
        val handler = SubagentToolHandler(
            hostContext = context,
            registry = context.agents,
            store = context.sessions,
            settlement = settlement,
            maxDepth = maxDepth,
            maxActiveSubagents = maxActiveSubagents,
        )
        // Stop admissions and release owned handles after unregistering the tool.
        scope.add(handler)
        scope.add(context.require(ToolsKey).register(SubagentToolSchema, handler))
    }
}

val ToolSubagentPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_SUBAGENT_PLUGIN_NAME, ToolSubagentPlugin())

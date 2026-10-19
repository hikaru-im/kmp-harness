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
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.api.sessionApi
import im.hikaru.harness.session.sessions
import im.hikaru.harness.tools.ToolsKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

const val TOOL_SUBAGENT_CONTROL_PLUGIN_NAME: String = "tool-subagent-control"
const val LIST_AGENTS_PLUGIN_NAME: String = "tool-subagent-control/list-agents"
const val SEND_MESSAGE_TOOL_NAME: String = "send_message"
const val INTERRUPT_AGENT_TOOL_NAME: String = "interrupt_agent"
const val LIST_AGENTS_TOOL_NAME: String = "list_agents"

val SendMessageToolSchema: ToolSchema = ToolSchema(
    name = SEND_MESSAGE_TOOL_NAME,
    description = "Send a message to an agent. A working agent receives it at its next step; an idle agent " +
        "starts a new turn with it. Returns delivery confirmation, not the agent's answer.",
    parameters = Json.parseToJsonElement(
        """{"type":"object","properties":{"agent_id":{"type":"string","description":"The agent id of your direct child, or your direct parent when you are a child agent."},"message":{"type":"string","description":"The message to deliver to the agent."}},"required":["agent_id","message"]}""",
    ).jsonObject,
)

val InterruptAgentToolSchema: ToolSchema = ToolSchema(
    name = INTERRUPT_AGENT_TOOL_NAME,
    description = "Ask a subagent to stop its current work. Queued messages and the subagents it started " +
        "are kept. You can continue a local child's conversation later with send_message.",
    parameters = Json.parseToJsonElement(
        """{"type":"object","properties":{"agent_id":{"type":"string","description":"The id of a live agent created under you: your direct child or a deeper descendant."}},"required":["agent_id"]}""",
    ).jsonObject,
)

val ListAgentsToolSchema: ToolSchema = ToolSchema(
    name = LIST_AGENTS_TOOL_NAME,
    description = "List subagents you started, with their ids, labels, and status. running means it is " +
        "working; inactive means it is not currently working. Use send_message to continue the conversation.",
    parameters = Json.parseToJsonElement(
        """{"type":"object","properties":{"scope":{"type":"string","description":"children (default) lists direct children; descendants lists the whole tree below you with each entry's parent session id and depth. Entries deeper than 1 accept only interrupt_agent."}},"required":[]}""",
    ).jsonObject,
)

class ToolSubagentControlPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey), InjectSpec.required(SessionKey),
        InjectSpec.required(SessionApiKey), InjectSpec.required(ToolsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val checks = SubagentControlChecks(context, context.agents, context.sessions)
        val tools = context.require(ToolsKey)
        val send = SendMessageHandler(checks, context.agents)
        val interrupt = InterruptAgentHandler(checks, context.agents, context.sessionApi)
        scope.add(tools.register(SendMessageToolSchema, send))
        scope.add(tools.register(InterruptAgentToolSchema, interrupt))
    }
}

/** Separately loadable, matching DSH's optional tool-subagent-control/list-agents plugin. */
class ListAgentsPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(AgentKey), InjectSpec.required(SessionKey),
        InjectSpec.required(SessionApiKey), InjectSpec.required(ToolsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val checks = SubagentControlChecks(context, context.agents, context.sessions)
        val handler = ListAgentsHandler(checks, context.agents, context.sessionApi)
        scope.add(context.require(ToolsKey).register(ListAgentsToolSchema, handler))
    }
}

val ToolSubagentControlPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_SUBAGENT_CONTROL_PLUGIN_NAME, ToolSubagentControlPlugin())

val ListAgentsPluginDefinition: PluginDefinition =
    pluginDefinition(LIST_AGENTS_PLUGIN_NAME, ListAgentsPlugin())

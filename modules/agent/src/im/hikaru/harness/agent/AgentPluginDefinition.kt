package im.hikaru.harness.agent

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public val AgentPluginDefinition: PluginDefinition =
    pluginDefinition(AGENT_PLUGIN_NAME, AgentPlugin())

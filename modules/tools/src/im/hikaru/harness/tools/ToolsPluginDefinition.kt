package im.hikaru.harness.tools

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public const val TOOLS_PLUGIN_NAME: String = "tools"

/** Native tool presentation has no configuration; PTC is a separate change. */
public val ToolsPluginDefinition: PluginDefinition =
    pluginDefinition(
        name = TOOLS_PLUGIN_NAME,
        plugin = ToolsPlugin(),
    )

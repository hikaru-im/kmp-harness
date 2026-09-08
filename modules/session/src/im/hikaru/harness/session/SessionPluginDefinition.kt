package im.hikaru.harness.session

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

const val SESSION_PLUGIN_NAME: String = "session"

val SessionPluginDefinition: PluginDefinition =
    pluginDefinition(SESSION_PLUGIN_NAME, SessionPlugin())

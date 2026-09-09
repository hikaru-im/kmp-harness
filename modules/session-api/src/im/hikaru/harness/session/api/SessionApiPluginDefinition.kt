package im.hikaru.harness.session.api

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public val SessionApiPluginDefinition: PluginDefinition =
    pluginDefinition(SESSION_API_PLUGIN_NAME, SessionApiPlugin())

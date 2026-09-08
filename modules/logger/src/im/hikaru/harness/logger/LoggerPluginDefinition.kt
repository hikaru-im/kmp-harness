package im.hikaru.harness.logger

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public const val LOGGER_PLUGIN_NAME: String = "logger"

public val LoggerPluginDefinition: PluginDefinition =
    pluginDefinition(LOGGER_PLUGIN_NAME, LoggerPlugin)

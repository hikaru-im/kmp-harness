package im.hikaru.harness.llm

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public const val LLM_PLUGIN_NAME: String = "llm"

public val LlmPluginDefinition: PluginDefinition =
    pluginDefinition(LLM_PLUGIN_NAME, LlmPlugin())

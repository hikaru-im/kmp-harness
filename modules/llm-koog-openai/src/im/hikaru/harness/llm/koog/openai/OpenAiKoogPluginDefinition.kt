package im.hikaru.harness.llm.koog.openai

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public const val OPENAI_KOOG_PLUGIN_NAME: String = "llm-koog-openai"

/** Provider deployment data remains in settings.yaml; this entry has no config. */
public val OpenAiKoogPluginDefinition: PluginDefinition =
    pluginDefinition(OPENAI_KOOG_PLUGIN_NAME, OpenAiKoogPlugin())

package im.hikaru.harness.llm.retry

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition

public val LlmRetryPluginDefinition: PluginDefinition =
    pluginDefinition(LLM_RETRY_PLUGIN_NAME, LlmRetryPlugin())

package im.hikaru.harness.system.prompt

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.SimplePlugin

public const val SYSTEM_PROMPT_PLUGIN_NAME: String = "system-prompt"

/** Provides the prompt registry with DSH's identity/persona defaults. */
public class SystemPromptPlugin(
    private val config: SystemPromptConfig = SystemPromptConfig(),
) : SimplePlugin {
    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val service = DefaultSystemPromptService(config)
        scope.add(context.provide(SystemPromptKey, service))
    }
}

public val SystemPromptPluginDefinition: PluginDefinition =
    pluginDefinition(
        name = SYSTEM_PROMPT_PLUGIN_NAME,
        plugin = ConfiguredSystemPromptPlugin,
        config = jsonObjectConfig(allowMissing = true, ::decodeSystemPromptConfig),
    )

private object ConfiguredSystemPromptPlugin : Plugin<SystemPromptConfig> {
    override suspend fun apply(context: Context, config: SystemPromptConfig, scope: EffectScope) {
        SystemPromptPlugin(config).apply(context, scope)
    }
}

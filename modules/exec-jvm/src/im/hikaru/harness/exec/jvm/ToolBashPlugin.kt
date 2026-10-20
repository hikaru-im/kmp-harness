package im.hikaru.harness.exec.jvm

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.api.BackgroundProcessesKey
import im.hikaru.harness.tools.ToolsKey

public const val TOOL_BASH_PLUGIN_NAME: String = "tool-bash"

public val ToolBashPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_BASH_PLUGIN_NAME, ToolBashPlugin())

/** POSIX shell tool mounted by the default profile on non-Windows hosts. */
public class ToolBashPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(ToolsKey), InjectSpec.required(SubprocessKey),
        InjectSpec.required(BackgroundProcessesKey), InjectSpec.required(JobsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val handler = ShellToolHandler(
            BASH_TOOL_NAME,
            "bash",
            { command -> listOf("bash", "-c", command) },
            context.require(SubprocessKey),
            context.require(BackgroundProcessesKey),
            context.require(JobsKey),
        )
        scope.add(context.require(ToolsKey).register(BashToolSchema, handler))
    }
}

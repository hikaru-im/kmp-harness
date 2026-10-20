package im.hikaru.harness.exec.jvm

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.api.BackgroundProcessesKey
import im.hikaru.harness.tools.ToolsKey

public const val TOOL_PWSH_PLUGIN_NAME: String = "tool-pwsh"

public val ToolPwshPluginDefinition: PluginDefinition =
    pluginDefinition(TOOL_PWSH_PLUGIN_NAME, ToolPwshPlugin())

/** PowerShell tool mounted by the default profile on Windows hosts. */
public class ToolPwshPlugin : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(ToolsKey), InjectSpec.required(SubprocessKey),
        InjectSpec.required(BackgroundProcessesKey), InjectSpec.required(JobsKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val executable = PwshExecutable.resolve()
        val handler = ShellToolHandler(
            PWSH_TOOL_NAME,
            "pwsh",
            { command ->
                listOf(executable, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", PWSH_ENCODING_PREAMBLE + command)
            },
            context.require(SubprocessKey),
            context.require(BackgroundProcessesKey),
            context.require(JobsKey),
        )
        scope.add(context.require(ToolsKey).register(PwshToolSchema, handler))
    }
}

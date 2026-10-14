package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.api.BackgroundProcessesKey
import im.hikaru.harness.session.api.SessionTerminalHostKey
import im.hikaru.harness.tools.ToolsKey
import java.nio.file.Path

public const val EXEC_PROCESS_PLUGIN_NAME: String = "exec-process"

/** Compiled local JVM producer. The launcher installs this only in its actual local Host. */
public val ExecProcessPluginDefinition: PluginDefinition =
    pluginDefinition(EXEC_PROCESS_PLUGIN_NAME, ExecProcessPlugin())

public data class ExecProcessConfig(
    val maxOutputChars: Int = 256 * 1024,
    val maxProcesses: Int = 128,
    val defaultTimeoutMillis: Long = 60_000,
    val maxTimeoutMillis: Long = 24 * 60 * 60 * 1000L,
    val shutdownTimeoutMillis: Long = 2_000,
    /** Optional launcher restriction in addition to each Session's real cwd subtree. */
    val allowedRoots: List<Path> = emptyList(),
    val maxInputBytes: Int = 4096,
    val inputTimeoutMillis: Long = 1_000,
    val maxTerminalBindings: Int = 16,
    val terminalLimits: JvmPtyTerminalSessions.Limits = JvmPtyTerminalSessions.Limits(),
) {
    init {
        require(maxOutputChars >= 2 && maxProcesses > 0)
        require(defaultTimeoutMillis > 0 && maxTimeoutMillis >= defaultTimeoutMillis)
        require(shutdownTimeoutMillis > 0)
        require(maxTimeoutMillis <= Long.MAX_VALUE / 1_000_000)
        require(shutdownTimeoutMillis <= Long.MAX_VALUE - maxTimeoutMillis)
        require(allowedRoots.all { it.isAbsolute })
        require(maxInputBytes > 0 && inputTimeoutMillis > 0)
        require(maxTerminalBindings > 0)
    }
}

public class ExecProcessPlugin(private val config: ExecProcessConfig = ExecProcessConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(ToolsKey), InjectSpec.required(SessionKey), InjectSpec.required(AgentKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val producer = ExecProcessProducer(context, config)
        // Register cleanup first so partial installation is also owned. Reverse disposal
        // unregisters the tool/read service before stopping the owned OS processes.
        scope.add(producer)
        scope.add(context.provide(BackgroundProcessesKey, producer))
        scope.add(context.require(ToolsKey).register(ExecProcessProducer.schema, producer))
        val terminalHost = JvmSessionTerminalHost(context, config.maxTerminalBindings, config.terminalLimits, config.allowedRoots)
        scope.add(terminalHost)
        scope.add(context.provide(SessionTerminalHostKey, terminalHost))
    }
}

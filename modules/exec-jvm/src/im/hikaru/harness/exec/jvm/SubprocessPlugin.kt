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
import java.nio.file.Path

public const val SUBPROCESS_PLUGIN_NAME: String = "subprocess"

/** Compiled local JVM provider. The launcher installs this only in its actual local Host. */
public val SubprocessPluginDefinition: PluginDefinition =
    pluginDefinition(SUBPROCESS_PLUGIN_NAME, SubprocessPlugin())

public data class SubprocessConfig(
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

/**
 * Local subprocess Provider. It exposes the execution seam consumed by the
 * shell tools, the background process inventory used by the UI, and the PTY
 * terminal host. Shell syntax and tool schemas stay in `tool-bash`/`tool-pwsh`.
 */
public class SubprocessPlugin(private val config: SubprocessConfig = SubprocessConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(SessionKey), InjectSpec.required(AgentKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val subprocess = LocalSubprocess(context, config)
        // Register cleanup first so partial installation is also owned. Reverse disposal
        // unregisters the services before stopping the owned OS processes.
        scope.add(subprocess)
        scope.add(context.provide(SubprocessKey, subprocess))
        scope.add(context.provide(BackgroundProcessesKey, subprocess))
        val terminalHost = JvmSessionTerminalHost(context, config.maxTerminalBindings, config.terminalLimits, config.allowedRoots)
        scope.add(terminalHost)
        scope.add(context.provide(SessionTerminalHostKey, terminalHost))
    }
}

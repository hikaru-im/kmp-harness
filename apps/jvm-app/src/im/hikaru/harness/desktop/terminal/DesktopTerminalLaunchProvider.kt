package im.hikaru.harness.desktop.terminal

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.TerminalDiscoveryResult
import im.hikaru.harness.client.panels.TerminalLaunchPlan
import im.hikaru.harness.client.panels.TerminalLaunchPreparation
import im.hikaru.harness.client.panels.TerminalLaunchProvider
import im.hikaru.harness.client.panels.ThreadPanelsOwner
import im.hikaru.harness.desktop.connection.LocalConnection
import java.nio.file.Files
import java.nio.file.Path

/** Bound to the actual main LocalConnection, never to a matching Host ID alone. */
public class DesktopTerminalLaunchProvider(
    private val expectedHostId: HostId,
    private val connection: LocalConnection,
    private val readEnvironment: () -> Map<String, String> = { System.getenv().toMap() },
    private val executable: (String) -> Boolean = { value ->
        val path = Path.of(value)
        path.isAbsolute && Files.isRegularFile(path) && Files.isExecutable(path)
    },
    private val osName: String = System.getProperty("os.name"),
    private val osArch: String = System.getProperty("os.arch"),
) : TerminalLaunchProvider {
    override fun availability(owner: ThreadPanelsOwner): String? = when {
        owner.connection !== connection || owner.host.hostId != expectedHostId -> "启动能力不属于当前本机连接。"
        osName != "Mac OS X" || osArch !in setOf("x86_64", "amd64") -> "交互终端仅在 macOS x86_64 验收。"
        else -> null
    }

    override fun prepare(owner: ThreadPanelsOwner, discovery: TerminalDiscoveryResult.Available): TerminalLaunchPreparation {
        availability(owner)?.let { return TerminalLaunchPreparation.Unavailable(it) }
        if (discovery.session != owner.parentSessionId || discovery.owner.session != owner.parentSessionId)
            return TerminalLaunchPreparation.Unavailable("终端发现结果不属于当前会话。")
        return try {
            val environment = readEnvironment().toMutableMap()
            val candidates = listOfNotNull(environment["SHELL"], "/bin/zsh", "/bin/sh").distinct()
            val shell = candidates.firstOrNull { candidate ->
                candidate.startsWith("/") && try { executable(candidate) } catch (_: Exception) { false }
            }
                ?: return TerminalLaunchPreparation.Unavailable("没有可执行的绝对路径 shell。")
            environment["TERM"] = "xterm-256color"
            environment["COLORTERM"] = "truecolor"
            if (environment["PATH"].isNullOrBlank()) environment["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
            TerminalLaunchPreparation.Ready(TerminalLaunchPlan(discovery.owner.copy(), listOf(shell, "-i"),
                environment.toMap(), discovery.canonicalCreationCwd))
        } catch (_: Exception) {
            TerminalLaunchPreparation.Unavailable("无法准备本机 shell 启动参数。")
        }
    }
}

package im.hikaru.harness.client.panels

import im.hikaru.harness.client.connection.TerminalCreateRequest
import im.hikaru.harness.client.connection.TerminalDiscoveryResult
import im.hikaru.harness.client.connection.TerminalOwner
import im.hikaru.harness.client.connection.TerminalSize

/** Preparation is read only. Only the controller's explicit create action starts a process. */
public interface TerminalLaunchProvider {
    public fun availability(owner: ThreadPanelsOwner): String?
    public fun prepare(owner: ThreadPanelsOwner, discovery: TerminalDiscoveryResult.Available): TerminalLaunchPreparation
}

public sealed interface TerminalLaunchPreparation {
    public class Ready(public val plan: TerminalLaunchPlan) : TerminalLaunchPreparation {
        override fun toString(): String = "Ready(launch plan)"
    }
    public data class Unavailable(val reason: String) : TerminalLaunchPreparation
}

/** Environment is deliberately excluded from equality, diagnostics and string representations. */
public class TerminalLaunchPlan(
    public val owner: TerminalOwner, argv: List<String>, environment: Map<String, String>, public val cwd: String,
) {
    private val frozenArgv = argv.toList()
    private val frozenEnvironment = environment.toMap()
    init { require(frozenArgv.isNotEmpty() && cwd.isNotBlank()) }
    public fun request(size: TerminalSize): TerminalCreateRequest = TerminalCreateRequest(
        owner.copy(), frozenArgv.toList(), frozenEnvironment.toMap(), size.copy(), cwd,
    )
    override fun toString(): String = "TerminalLaunchPlan(redacted)"
}

public object UnavailableTerminalLaunchProvider : TerminalLaunchProvider {
    override fun availability(owner: ThreadPanelsOwner): String = "当前连接没有本机交互 shell 启动能力。"
    override fun prepare(owner: ThreadPanelsOwner, discovery: TerminalDiscoveryResult.Available): TerminalLaunchPreparation =
        TerminalLaunchPreparation.Unavailable(availability(owner))
}

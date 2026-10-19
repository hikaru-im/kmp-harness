package im.hikaru.harness.exec.jvm

import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.api.HostTerminalCreateResult
import im.hikaru.harness.session.api.HostTerminalDiscoveryResult
import im.hikaru.harness.session.api.SessionTerminalHostKey
import im.hikaru.harness.session.api.SessionTerminalHostService
import im.hikaru.harness.session.api.TerminalCreateRequest
import im.hikaru.harness.session.api.TerminalKey
import im.hikaru.harness.session.api.TerminalListResult
import im.hikaru.harness.session.api.TerminalLifecycle
import im.hikaru.harness.session.api.TerminalLineSnapshot
import im.hikaru.harness.session.api.TerminalMetadata
import im.hikaru.harness.session.api.TerminalMutationResult
import im.hikaru.harness.session.api.TerminalReadResult
import im.hikaru.harness.session.api.TerminalSessionId
import im.hikaru.harness.session.api.TerminalSize
import im.hikaru.harness.session.api.TerminalSnapshot
import im.hikaru.harness.tools.ToolExecutionContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

public const val TERMINAL_PLUGIN_NAME: String = "terminal"

public enum class TerminalSignal { SIGINT, SIGTERM, SIGKILL, SIGTSTP, SIGHUP }

public enum class TerminalWaitReason { STDIN_READ, INFERRED_IDLE, TIMEOUT, SESSION_EXIT }

public data class TerminalOpened(
    val sessionId: TerminalSessionId,
    val name: String?,
    val type: String,
    val cwd: String,
    val lifecycle: TerminalLifecycle,
    val motd: String,
    val truncated: Boolean,
)

public data class TerminalSent(
    val viewport: String,
    val waitReason: TerminalWaitReason,
    val lifecycle: TerminalLifecycle,
    val truncated: Boolean,
)

public data class TerminalPage(
    val text: String,
    val totalLines: Int,
    val lineBegin: Int,
    val lineEnd: Int,
    val truncated: Boolean,
)

public data class TerminalSignalResult(val signal: TerminalSignal, val delivered: Boolean)

public data class TerminalCloseResult(val sessionId: TerminalSessionId, val alreadyClosing: Boolean)

public data class TerminalSummary(
    val sessionId: TerminalSessionId,
    val name: String?,
    val type: String,
    val lifecycle: TerminalLifecycle,
)

public interface TerminalsService {
    public suspend fun open(
        execution: ToolExecutionContext,
        type: String,
        name: String? = null,
        cwd: String? = null,
    ): TerminalOpened

    public suspend fun send(
        execution: ToolExecutionContext,
        sessionId: TerminalSessionId,
        text: String,
        submit: Boolean = true,
        timeoutMillis: Long = 30_000,
    ): TerminalSent

    public suspend fun read(
        execution: ToolExecutionContext,
        sessionId: TerminalSessionId,
        offset: Int = 0,
        count: Int = 500,
    ): TerminalPage

    public suspend fun signal(
        execution: ToolExecutionContext,
        sessionId: TerminalSessionId,
        signal: TerminalSignal,
    ): TerminalSignalResult

    public suspend fun close(execution: ToolExecutionContext, sessionId: TerminalSessionId): TerminalCloseResult
    public suspend fun list(execution: ToolExecutionContext): List<TerminalSummary>
}

public object TerminalsKey : ServiceKey<TerminalsService>("terminal.local")

public data class TerminalConfig(
    val pollMillis: Long = 25,
    val idleSilenceMillis: Long = 300,
    val startupPollMillis: Long = 250,
    val readLinesMax: Int = 5_000,
) {
    init {
        require(pollMillis in 1..1_000)
        require(idleSilenceMillis in pollMillis..10_000)
        require(startupPollMillis in 0..10_000)
        require(readLinesMax in 1..20_000)
    }
}

/**
 * Owner-scoped terminal facade over the Host's PTY service. It adds the
 * shell launch plan, name/type presentation, bounded output waits, and the
 * six operations the model tools expose. Process lifecycle stays in
 * [SessionTerminalHostService].
 */
public class LocalTerminal(
    private val context: Context,
    private val host: SessionTerminalHostService,
    private val config: TerminalConfig = TerminalConfig(),
) : TerminalsService, Disposable {
    private val store = context.require(SessionKey)
    private val stopped = AtomicBoolean(false)
    private val metadata = ConcurrentHashMap<TerminalSessionId, Meta>()

    private data class Meta(val name: String?, val type: String)

    override suspend fun open(
        execution: ToolExecutionContext,
        type: String,
        name: String?,
        cwd: String?,
    ): TerminalOpened {
        requireExecution(execution)
        require(type == "shell") { "Unknown terminal backend type '$type'; the local backend registers 'shell'" }
        val discovery = owner(execution.session.id)
        val result = host.create(
            TerminalCreateRequest(
                owner = discovery.owner,
                argv = shellArgv(),
                environment = shellEnvironment(),
                size = TerminalSize(),
                cwd = cwd,
            )
        )
        val created = when (result) {
            is HostTerminalCreateResult.Created -> result
            is HostTerminalCreateResult.Rejected -> error("Terminal create was rejected: ${result.reason}")
            is HostTerminalCreateResult.Unavailable -> error("Terminal is unavailable: ${result.reason}")
            is HostTerminalCreateResult.UnknownOutcome -> error("Terminal create outcome is unknown: ${result.reason}")
        }
        metadata[created.metadata.key.terminal] = Meta(name?.takeIf(String::isNotBlank), type)
        if (config.startupPollMillis > 0) delay(config.startupPollMillis)
        val snapshot = snapshot(created.metadata.key)
        return TerminalOpened(
            sessionId = created.metadata.key.terminal,
            name = name?.takeIf(String::isNotBlank),
            type = type,
            cwd = created.metadata.canonicalCwd,
            lifecycle = snapshot.metadata.lifecycle,
            motd = viewport(snapshot),
            truncated = snapshot.truncated,
        )
    }

    override suspend fun send(
        execution: ToolExecutionContext,
        sessionId: TerminalSessionId,
        text: String,
        submit: Boolean,
        timeoutMillis: Long,
    ): TerminalSent {
        requireExecution(execution)
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        require('\u0000' !in text) { "Terminal text must not contain NUL" }
        val key = key(execution, sessionId)
        val before = snapshot(key)
        val payload = if (submit) "$text\r" else text
        val write = host.write(key, payload)
        check(write is TerminalMutationResult.Applied) {
            "Terminal write was not applied: ${mutationReason(write)}"
        }
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var revision = before.revision
        var changed = false
        var lastChange = System.nanoTime()
        while (System.nanoTime() < deadline) {
            delay(config.pollMillis)
            val current = snapshot(key)
            if (current.metadata.lifecycle is TerminalLifecycle.Exited) {
                return TerminalSent(viewport(current), TerminalWaitReason.SESSION_EXIT, current.metadata.lifecycle, current.truncated)
            }
            if (current.revision != revision) {
                revision = current.revision
                changed = true
                lastChange = System.nanoTime()
            } else if (changed && System.nanoTime() - lastChange >= config.idleSilenceMillis * 1_000_000) {
                return TerminalSent(viewport(current), TerminalWaitReason.INFERRED_IDLE, current.metadata.lifecycle, current.truncated)
            }
        }
        val current = snapshot(key)
        return TerminalSent(viewport(current), TerminalWaitReason.TIMEOUT, current.metadata.lifecycle, current.truncated)
    }

    override suspend fun read(
        execution: ToolExecutionContext,
        sessionId: TerminalSessionId,
        offset: Int,
        count: Int,
    ): TerminalPage {
        requireExecution(execution)
        require(offset >= 0) { "offset must not be negative" }
        require(count > 0) { "count must be positive" }
        val snapshot = snapshot(key(execution, sessionId))
        val lines = allLines(snapshot)
        val end = (lines.size - offset).coerceIn(0, lines.size)
        val begin = (end - count.coerceAtMost(config.readLinesMax)).coerceAtLeast(0)
        val text = lines.subList(begin, end).joinToString("\n") { it.text.trimEnd() }
        return TerminalPage(
            text = text,
            totalLines = lines.size,
            lineBegin = if (end == 0) 0 else begin + 1,
            lineEnd = end,
            truncated = begin > 0 || snapshot.truncated,
        )
    }

    override suspend fun signal(
        execution: ToolExecutionContext,
        sessionId: TerminalSessionId,
        signal: TerminalSignal,
    ): TerminalSignalResult {
        requireExecution(execution)
        val control = when (signal) {
            TerminalSignal.SIGINT -> "\u0003"
            TerminalSignal.SIGTSTP -> "\u001A"
            TerminalSignal.SIGKILL -> throw IllegalArgumentException(
                "SIGKILL targets the shell itself and is rejected; use terminal_close",
            )
            TerminalSignal.SIGTERM, TerminalSignal.SIGHUP -> throw IllegalArgumentException(
                "The local PTY backend cannot address the foreground process group for $signal; use terminal_close",
            )
        }
        val key = key(execution, sessionId)
        val result = host.write(key, control)
        check(result is TerminalMutationResult.Applied) {
            "Terminal signal was not delivered: ${mutationReason(result)}"
        }
        return TerminalSignalResult(signal, delivered = true)
    }

    override suspend fun close(execution: ToolExecutionContext, sessionId: TerminalSessionId): TerminalCloseResult {
        requireExecution(execution)
        val key = key(execution, sessionId)
        val result = host.close(key)
        return when (result) {
            is TerminalMutationResult.Applied -> {
                metadata.remove(sessionId)
                TerminalCloseResult(sessionId, alreadyClosing = false)
            }
            is TerminalMutationResult.NotFound -> {
                metadata.remove(sessionId)
                TerminalCloseResult(sessionId, alreadyClosing = true)
            }
            is TerminalMutationResult.Rejected -> error("Terminal close was rejected: ${result.reason}")
            is TerminalMutationResult.Unavailable -> error("Terminal is unavailable: ${result.reason}")
            is TerminalMutationResult.UnknownOutcome -> error("Terminal close outcome is unknown: ${result.reason}")
        }
    }

    override suspend fun list(execution: ToolExecutionContext): List<TerminalSummary> {
        requireExecution(execution)
        val owner = owner(execution.session.id).owner
        return when (val result = host.list(owner)) {
            is TerminalListResult.Available -> result.terminals.map { summary(it) }
            is TerminalListResult.Rejected -> error("Terminal list was rejected: ${result.reason}")
            is TerminalListResult.Unavailable -> error("Terminal is unavailable: ${result.reason}")
        }
    }

    private fun summary(value: TerminalMetadata): TerminalSummary {
        val meta = metadata[value.key.terminal]
        return TerminalSummary(value.key.terminal, meta?.name, meta?.type ?: "shell", value.lifecycle)
    }

    private fun allLines(snapshot: TerminalSnapshot): List<TerminalLineSnapshot> =
        snapshot.history + snapshot.screen

    private fun viewport(snapshot: TerminalSnapshot): String {
        val lines = allLines(snapshot).map { it.text.trimEnd() }
        val last = lines.indexOfLast(String::isNotEmpty)
        return if (last < 0) "" else lines.subList(0, last + 1).joinToString("\n")
    }

    private suspend fun snapshot(key: TerminalKey): TerminalSnapshot =
        when (val result = host.read(key)) {
            is TerminalReadResult.Available -> result.snapshot
            is TerminalReadResult.NotFound -> error("Terminal session '${key.terminal.value}' was not found")
            is TerminalReadResult.Rejected -> error("Terminal read was rejected: ${result.reason}")
            is TerminalReadResult.Unavailable -> error("Terminal is unavailable: ${result.reason}")
        }

    private suspend fun owner(session: SessionId): HostTerminalDiscoveryResult.Available =
        when (val result = host.discover(session)) {
            is HostTerminalDiscoveryResult.Available -> result
            is HostTerminalDiscoveryResult.Rejected -> error("Session has no terminal owner: ${result.reason}")
            is HostTerminalDiscoveryResult.Unavailable -> error("Terminal is unavailable: ${result.reason}")
        }

    private suspend fun key(execution: ToolExecutionContext, sessionId: TerminalSessionId): TerminalKey {
        requireExecution(execution)
        return TerminalKey(owner(execution.session.id).owner, sessionId)
    }

    private suspend fun requireExecution(execution: ToolExecutionContext) {
        check(!stopped.get()) { "Terminal service is disposed" }
        require(execution.context.root === context.root) { "Execution belongs to another Host" }
        require(execution.context.require(SessionKey) === store) { "Execution uses another store" }
        require(store.get(execution.session.id) === execution.session && execution.session.isLive()) {
            "Session is no longer live"
        }
    }

    private fun mutationReason(result: TerminalMutationResult): String = when (result) {
        is TerminalMutationResult.Rejected -> result.reason
        is TerminalMutationResult.Unavailable -> result.reason
        is TerminalMutationResult.UnknownOutcome -> result.reason
        is TerminalMutationResult.NotFound -> "terminal session was not found"
        is TerminalMutationResult.Applied -> "applied"
    }

    override suspend fun dispose() {
        stopped.set(true)
        metadata.clear()
    }

    private companion object {
        fun shellArgv(): List<String> {
            val candidates = listOfNotNull(System.getenv("SHELL"), "/bin/zsh", "/bin/sh").distinct()
            val shell = candidates.firstOrNull { candidate ->
                try {
                    val path = Path.of(candidate)
                    path.isAbsolute && Files.isRegularFile(path) && Files.isExecutable(path)
                } catch (_: Exception) {
                    false
                }
            } ?: "/bin/sh"
            return listOf(shell, "-i")
        }

        fun shellEnvironment(): Map<String, String> {
            val environment = System.getenv().toMutableMap()
            environment["TERM"] = "xterm-256color"
            environment["COLORTERM"] = "truecolor"
            environment["PAGER"] = "cat"
            environment["GIT_PAGER"] = "cat"
            if (environment["PATH"].isNullOrBlank()) environment["PATH"] = "/usr/bin:/bin:/usr/sbin:/sbin"
            return environment
        }
    }
}

public class TerminalPlugin(private val config: TerminalConfig = TerminalConfig()) : SimplePlugin {
    override val inject: Set<InjectSpec> = setOf(
        InjectSpec.required(SessionKey), InjectSpec.required(SessionTerminalHostKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val terminal = LocalTerminal(context, context.require(SessionTerminalHostKey), config)
        scope.add(terminal)
        scope.add(context.provide(TerminalsKey, terminal))
    }
}

public val TerminalPluginDefinition: PluginDefinition =
    pluginDefinition(TERMINAL_PLUGIN_NAME, TerminalPlugin())

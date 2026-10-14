package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentRegistry
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.api.*
import im.hikaru.harness.tools.ContextualToolHandler
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
public data class ExecCommand(
    val argv: List<String>,
    val cwd: String? = null,
    val background: Boolean = false,
    val timeoutMillis: Long? = null,
    /** Keep stdin open only for explicitly interactive background commands. */
    val stdin: Boolean = false,
)

@Serializable
private data class ExecReceipt(
    val process: BackgroundProcess,
    val background: Boolean,
    val output: String? = null,
    val truncated: Boolean = false,
    val timedOut: Boolean = false,
)

/** All authority is in-process. A matching textual scope alone never grants execution. */
public class ExecProcessProducer(
    private val context: Context,
    private val config: ExecProcessConfig = ExecProcessConfig(),
) : ContextualToolHandler, BackgroundProcessesService, Disposable {
    private val store: SessionStore = context.require(SessionKey)
    private val agents: AgentRegistry = context.require(AgentKey)
    private val incarnation = UUID.randomUUID().toString()
    private val json = Json { encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val entries = linkedMapOf<BackgroundProcessId, Entry>()
    private val claimed = mutableSetOf<Pair<Session, CallId>>()
    private val disposed = AtomicBoolean(false)

    override suspend fun execute(request: ToolCallRequest, execution: ToolExecutionContext): ToolExecutionResult {
        require(request.name == "exec") { "Unsupported tool" }
        require(request.scope == execution.session.id.value) { "Tool scope does not match the trusted Session" }
        val command = json.decodeFromString<ExecCommand>(request.arguments)
        require(command.argv.isNotEmpty() && command.argv.first().isNotBlank()) { "argv must contain an executable" }
        require(command.argv.all { '\u0000' !in it }) { "argv contains NUL" }
        require(!command.stdin || command.background) { "stdin requires a background command" }
        val timeout = command.timeoutMillis ?: config.defaultTimeoutMillis
        require(timeout in 1L..config.maxTimeoutMillis) { "timeoutMillis is outside the configured limit" }
        val entry = withContext(Dispatchers.IO) {
            gate.withLock {
                check(!disposed.get()) { "Exec producer is disposed" }
                val agent = requireOwner(execution)
                val cwd = resolveCwd(execution.session, command.cwd)
                requireCurrent(agent, execution.session)
                currentCoroutineContext().ensureActive()
                check(!disposed.get()) { "Exec producer is disposed" }
                check((execution.session to request.callId) !in claimed) { "Exec call already created a process" }
                makeRoom()
                // Start + registry publication cannot be cancelled midway. Dispose shares
                // this gate; cancellation after publication leaves a Host-owned record.
                withContext(NonCancellable) {
                    val process = ProcessBuilder(command.argv).directory(cwd.toFile()).redirectErrorStream(true).start()
                    // Once start succeeds a retry may never spawn this call again,
                    // including failure during the subsequent ownership recheck.
                    claimed += execution.session to request.callId
                    val created = Entry(
                        BackgroundProcess(
                            BackgroundProcessId("$incarnation:${UUID.randomUUID()}"), execution.session.id,
                            request.callId, json.encodeToString(command.argv), cwd.toString(),
                            System.currentTimeMillis(), incarnation, BackgroundProcessLifecycle.Running,
                            inputState = if (command.stdin) BackgroundInputState.Open else BackgroundInputState.Unsupported,
                        ), process, agent, execution.session, config.maxOutputChars,
                    )
                    try {
                        if (!command.stdin) process.outputStream.close()
                        requireCurrent(agent, execution.session)
                        check(!disposed.get()) { "Exec producer stopped during process creation" }
                        entries[created.metadata.process] = created
                        created.worker = scope.launch { monitor(created, timeout) }
                        created
                    } catch (error: Throwable) {
                        terminate(created)
                        closePipes(created)
                        throw error
                    }
                }
            }
        }
        if (command.background) {
            // Tool result acknowledges a handle, not process completion.
            return ToolExecutionResult(listOf(TextBlock(json.encodeToString(ExecReceipt(entry.metadataSnapshot(), true)))))
        }
        try {
            val drained = withTimeoutOrNull(timeout + config.shutdownTimeoutMillis) { entry.worker.join(); true } == true
            if (!drained) {
                // An escaped descendant may retain a pipe beyond the command deadline.
                // Bound this tool invocation while keeping cleanup owned by the Host.
                entry.timedOut = true
                entry.unknown()
                withContext(NonCancellable + Dispatchers.IO) { terminate(entry); entry.worker.cancel() }
            }
            currentCoroutineContext().ensureActive()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { terminate(entry); entry.worker.cancel() }
            throw cancelled
        }
        val snapshot = entry.snapshot()
        val lifecycle = snapshot.lifecycle
        val failed = lifecycle !is BackgroundProcessLifecycle.Exited || lifecycle.exitCode != 0 || entry.timedOut
        return ToolExecutionResult(
            listOf(TextBlock(json.encodeToString(ExecReceipt(entry.metadataSnapshot(), false, snapshot.buffer, snapshot.truncated, entry.timedOut)))),
            isError = failed,
        )
    }

    private suspend fun requireOwner(execution: ToolExecutionContext): Agent {
        require(execution.context.root === context.root) { "Execution belongs to another Host" }
        require(execution.context.require(SessionKey) === store && execution.context.require(AgentKey) === agents) {
            "Execution uses another store or registry"
        }
        val agent = agents.get(AgentId(execution.session.id.value)) ?: error("Session has no live Agent")
        require(agent.context === execution.context && agent.session === execution.session) { "Execution owner identity mismatch" }
        requireCurrent(agent, execution.session)
        return agent
    }

    private suspend fun requireCurrent(agent: Agent, session: Session) {
        check(context.require(SessionKey) === store && context.require(AgentKey) === agents) { "Producer dependencies changed" }
        check(agent.context.root === context.root && agent.context.require(SessionKey) === store && agent.context.require(AgentKey) === agents)
        check(agents.get(agent.id) === agent && agent.session === session && agent.id.value == session.id.value) { "Agent was replaced" }
        check(store.get(session.id) === session && session.isLive()) { "Session is no longer the exact live store member" }
        check(agents.get(agent.id) === agent && !agent.context.isDisposed) { "Agent changed during Session validation" }
    }

    private fun resolveCwd(session: Session, requested: String?): Path {
        val declared = session.header.cwd ?: error("Exec requires a real Session working directory")
        val root = Path.of(declared).toRealPath()
        require(Files.isDirectory(root)) { "Session cwd is not a directory" }
        val supplied = requested?.let(Path::of)
        val candidate = when {
            supplied == null -> root
            supplied.isAbsolute -> supplied
            else -> root.resolve(supplied)
        }.toRealPath()
        require(Files.isDirectory(candidate) && candidate.startsWith(root)) { "cwd must be inside the real Session directory" }
        require(config.allowedRoots.isEmpty() || config.allowedRoots.any { candidate.startsWith(it.toRealPath()) }) {
            "cwd is outside the Host's configured roots"
        }
        return candidate
    }

    private fun makeRoom() {
        while (entries.size >= config.maxProcesses) {
            val expired = entries.entries.firstOrNull { it.value.worker.isCompleted && !it.value.hasLiveProcesses() }?.key
                ?: error("Owned process limit reached")
            entries.remove(expired)
        }
    }

    private suspend fun monitor(entry: Entry, timeoutMillis: Long) {
        try {
            coroutineScope {
                val started = System.nanoTime()
                val watchdog = launch {
                    while (isActive) {
                        rememberDescendants(entry)
                        if (System.nanoTime() - started >= timeoutMillis * 1_000_000) {
                            entry.timedOut = true
                            terminate(entry)
                            // An inherited pipe can remain open after the direct process exits.
                            closePipes(entry)
                            break
                        }
                        delay(20)
                    }
                }
                try {
                    val drain = async {
                        runInterruptible(Dispatchers.IO) {
                            InputStreamReader(entry.process.inputStream, StandardCharsets.UTF_8).use { reader ->
                                val chunk = CharArray(4096)
                                while (true) {
                                    val count = reader.read(chunk)
                                    if (count < 0) break
                                    entry.append(chunk, count)
                                }
                            }
                        }
                        entry.eof()
                    }
                    val exitCode = runInterruptible(Dispatchers.IO) { entry.process.waitFor() }
                    drain.await()
                    entry.exited(exitCode) // Exit is authoritative only after EOF and waitFor.
                } finally {
                    watchdog.cancel()
                }
            }
        } catch (_: CancellationException) {
            entry.unknown()
        } catch (_: Exception) {
            entry.unknown() // Failed drain does not pretend output is complete.
        } finally {
            terminate(entry)
            closePipes(entry)
        }
    }

    override suspend fun list(parent: SessionId): BackgroundProcessesResult = gate.withLock {
        if (disposed.get()) return@withLock BackgroundProcessesResult.Unavailable(parent, "Exec producer is disposed")
        val owner = currentParent(parent)
        val owned = entries.values.filter { it.session === owner.session && it.agent === owner }
        val snapshot = owned.map { it.metadataSnapshot() }
        requireCurrent(owner, owner.session)
        BackgroundProcessesResult.Available(parent, snapshot)
    }

    override suspend fun read(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult = gate.withLock {
        if (disposed.get()) return@withLock BackgroundOutputResult.Unavailable(parent, process, "Exec producer is disposed")
        val owner = currentParent(parent)
        val entry = entries[process]
        if (entry == null || entry.session !== owner.session || entry.agent !== owner) {
            return@withLock BackgroundOutputResult.NotFound(parent, process)
        }
        val snapshot = entry.snapshot()
        requireCurrent(owner, owner.session)
        snapshot
    }

    override suspend fun write(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
        if ('\u0000' in input) return BackgroundInputResult.Rejected(parent, process, "Terminal input contains NUL")
        if (input.length > config.maxInputBytes) return BackgroundInputResult.Rejected(parent, process, "Terminal input exceeds the byte limit")
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > config.maxInputBytes) return BackgroundInputResult.Rejected(parent, process, "Terminal input exceeds the byte limit")
        val entry = gate.withLock {
            if (disposed.get()) return BackgroundInputResult.Unavailable(parent, process, "Exec producer is disposed")
            val owner = currentParent(parent)
            val entry = entries[process]
            if (entry == null || entry.session !== owner.session || entry.agent !== owner) {
                return BackgroundInputResult.NotFound(parent, process)
            }
            requireCurrent(owner, owner.session)
            entry.beginInput()?.let { return BackgroundInputResult.Rejected(parent, process, it) }
            entry
        }
        val delivered = runInput(entry, eof = false) {
            entry.process.outputStream.write(bytes)
            entry.process.outputStream.flush()
        }
        return if (delivered) BackgroundInputResult.Accepted(parent, process, bytes.size)
        else BackgroundInputResult.UnknownOutcome(parent, process, "Input delivery is unknown; do not resend")
    }

    override suspend fun closeInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
        val entry = gate.withLock {
            if (disposed.get()) return BackgroundControlResult.Unavailable(parent, process, "Exec producer is disposed")
            val owner = currentParent(parent)
            val entry = entries[process]
            if (entry == null || entry.session !== owner.session || entry.agent !== owner) {
                return BackgroundControlResult.NotFound(parent, process)
            }
            requireCurrent(owner, owner.session)
            entry.beginInput()?.let { return BackgroundControlResult.Rejected(parent, process, it) }
            entry
        }
        return if (runInput(entry, eof = true) { entry.process.outputStream.close() }) {
            BackgroundControlResult.Applied(parent, process)
        } else BackgroundControlResult.UnknownOutcome(parent, process, "EOF delivery is unknown; do not repeat")
    }

    /** Blocking pipe IO belongs to the producer, never to the registry lock or request job. */
    private suspend fun runInput(entry: Entry, eof: Boolean, operation: () -> Unit): Boolean {
        val completion = CompletableDeferred<Boolean>()
        val worker = scope.launch {
            try {
                operation()
                entry.finishInput(eof, unknown = false)
                completion.complete(true)
            } catch (_: Exception) {
                entry.finishInput(eof, unknown = true)
                completion.complete(false)
            }
        }
        worker.invokeOnCompletion { cause ->
            if (cause != null) {
                entry.finishInput(eof, unknown = true)
                completion.complete(false)
            }
        }
        return try {
            val delivered = withTimeoutOrNull(config.inputTimeoutMillis) { completion.await() }
            if (delivered != true) entry.markInputUnknown()
            delivered == true
        } catch (cancelled: CancellationException) {
            entry.markInputUnknown()
            throw cancelled
        }
    }

    override suspend fun cancel(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
        val entry = gate.withLock {
            if (disposed.get()) return BackgroundControlResult.Unavailable(parent, process, "Exec producer is disposed")
            val owner = currentParent(parent)
            val entry = entries[process]
            if (entry == null || entry.session !== owner.session || entry.agent !== owner) {
                return BackgroundControlResult.NotFound(parent, process)
            }
            requireCurrent(owner, owner.session)
            if (entry.lifecycle() is BackgroundProcessLifecycle.Exited) {
                return BackgroundControlResult.Rejected(parent, process, "Process has already exited")
            }
            entry
        }
        return withContext(NonCancellable + Dispatchers.IO) {
            try {
                terminate(entry)
                // A request acknowledgment is not confirmed process exit; read() supplies that evidence.
                BackgroundControlResult.Applied(parent, process)
            } catch (_: Exception) {
                BackgroundControlResult.UnknownOutcome(parent, process, "Cancellation outcome is unknown")
            }
        }
    }

    private suspend fun currentParent(parent: SessionId): Agent {
        val agent = agents.get(AgentId(parent.value)) ?: error("Parent Session has no live Agent")
        requireCurrent(agent, agent.session)
        return agent
    }

    override suspend fun dispose() = withContext(NonCancellable + Dispatchers.IO) {
        // Fence new starts before waiting for suspended ownership checks or start().
        disposed.set(true)
        var pending: List<Entry>? = null
        // Destroy first, then let waitFor + EOF finish naturally. Cancelling a worker
        // between terminal publication and cleanup can turn a confirmed exit into
        // Unknown and interrupt its reader/watchdog shutdown. Pipe close stays on
        // the worker: a JDK pipe close may wait behind a blocking read.
        val drained = withTimeoutOrNull(config.shutdownTimeoutMillis) {
            val owned = gate.withLock { entries.values.toList() }
            pending = owned
            owned.forEach { terminate(it) }
            owned.map { it.worker }.joinAll()
            // destroyForcibly only requests termination; even a completed monitor
            // may have just killed a remembered descendant in its finally block.
            while (owned.any { it.hasLiveProcesses() }) {
                owned.filter { it.hasLiveProcesses() }.forEach { terminate(it) }
                delay(10)
            }
            scope.cancel()
            scope.coroutineContext.job.join()
            true
        } == true
        if (!drained) {
            scope.cancel() // Signal stuck workers without an unbounded cancelAndJoin.
            val evidence = pending?.joinToString { entry ->
                "pid=${entry.process.pid()} workerCompleted=${entry.worker.isCompleted} " +
                    "processAlive=${entry.process.isAlive} liveDescendants=" +
                    entry.descendants.values.filter { it.isAlive }.map { it.pid() }
            } ?: "process inventory gate did not become available"
            error("Owned exec processes did not stop within the shutdown budget; cleanup is not verified: $evidence")
        }
    }

    private fun rememberDescendants(entry: Entry) {
        entry.process.descendants().use { stream -> stream.forEach { entry.descendants[it.pid()] = it } }
    }

    private fun terminate(entry: Entry) {
        rememberDescendants(entry)
        entry.descendants.values.forEach { if (it.isAlive) it.destroyForcibly() }
        if (entry.process.isAlive) entry.process.destroyForcibly()
    }

    private fun closePipes(entry: Entry) {
        runCatching { entry.process.inputStream.close() }
        runCatching { entry.process.errorStream.close() }
        runCatching { entry.process.outputStream.close() }
    }

    private class Entry(
        val metadata: BackgroundProcess,
        val process: Process,
        val agent: Agent,
        val session: Session,
        private val limit: Int,
    ) {
        lateinit var worker: Job
        val descendants = ConcurrentHashMap<Long, ProcessHandle>()
        @Volatile var timedOut: Boolean = false
        private var buffer = ""
        private var revision = 0L
        private var truncated = false
        private var lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Running
        private var reachedEof = false
        private var pendingHighSurrogate = ""
        private var inputState = metadata.inputState
        private var inputBusy = false

        fun hasLiveProcesses(): Boolean = process.isAlive || descendants.values.any(ProcessHandle::isAlive)

        @Synchronized fun lifecycle(): BackgroundProcessLifecycle = lifecycle
        @Synchronized fun inputState(): BackgroundInputState = inputState

        @Synchronized fun beginInput(): String? {
            if (lifecycle !is BackgroundProcessLifecycle.Running) return "Process is not running"
            if (inputState != BackgroundInputState.Open) return "Process stdin is not open"
            if (inputBusy) return "Another input operation is pending"
            inputBusy = true
            return null
        }

        @Synchronized fun markInputUnknown() {
            if (inputState != BackgroundInputState.Unknown) {
                inputState = BackgroundInputState.Unknown
                revision++
            }
        }

        @Synchronized fun finishInput(eof: Boolean, unknown: Boolean) {
            inputBusy = false
            if (unknown) markInputUnknown()
            else if (eof && inputState == BackgroundInputState.Open) {
                inputState = BackgroundInputState.Closed
                revision++
            }
        }

        @Synchronized fun append(chunk: CharArray, count: Int) {
            var incoming = pendingHighSurrogate + String(chunk, 0, count)
            pendingHighSurrogate = if (incoming.lastOrNull()?.isHighSurrogate() == true) incoming.takeLast(1) else ""
            if (pendingHighSurrogate.isNotEmpty()) incoming = incoming.dropLast(1)
            val combined = buffer + incoming
            var start = (combined.length - limit).coerceAtLeast(0)
            if (start > 0 && combined[start].isLowSurrogate() && combined[start - 1].isHighSurrogate()) start++
            truncated = truncated || start > 0
            buffer = combined.substring(start)
            revision++ // Also advances when the bounded tail has the same content.
        }
        @Synchronized fun eof() { reachedEof = true; revision++ }
        @Synchronized fun exited(exitCode: Int) {
            check(reachedEof)
            lifecycle = BackgroundProcessLifecycle.Exited(exitCode)
            if (inputState == BackgroundInputState.Open) inputState = BackgroundInputState.Closed
            revision++
        }
        @Synchronized fun unknown() {
            // Once waitFor and EOF have confirmed an exit, cleanup cancellation
            // cannot invalidate that process/output evidence.
            if (lifecycle !is BackgroundProcessLifecycle.Exited) {
                lifecycle = BackgroundProcessLifecycle.Unknown
                revision++
            }
        }
        @Synchronized fun metadataSnapshot(): BackgroundProcess = metadata.copy(
            lifecycle = lifecycle,
            inputState = inputState(),
        )
        @Synchronized fun snapshot(): BackgroundOutputResult.Available = BackgroundOutputResult.Available(
            session.id, metadata.process, session.id, buffer, revision, truncated, lifecycle, inputState(),
        )
    }

    public companion object {
        public val schema: ToolSchema = ToolSchema(
            "exec", "Execute argv in the real Session directory. Background returns an owned process handle, not completion. Set stdin=true to keep UTF-8 input open; EOF closes it. No PTY.",
            Json.parseToJsonElement("""{"type":"object","additionalProperties":false,"required":["argv"],"properties":{"argv":{"type":"array","minItems":1,"items":{"type":"string"}},"cwd":{"type":"string","description":"Existing directory within the real Session cwd"},"background":{"type":"boolean","default":false},"timeoutMillis":{"type":"integer","minimum":1},"stdin":{"type":"boolean","default":false}}}""") as JsonObject,
        )
    }
}

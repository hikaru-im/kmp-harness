package im.hikaru.harness.exec.jvm

import com.jediterm.core.Color
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TerminalOutputStream
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Supplier
import kotlin.concurrent.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Standalone capability: no runtime registration, Host authorization or UI wiring occurs here. */
public class JvmPtyTerminalSessions(
    scope: TerminalScope,
    session: SessionId,
    canonicalRoot: Path,
    private val limits: Limits = Limits(),
) : SessionTerminalsService {
    public data class Limits(
        val capacity: Int = 8,
        val historyLines: Int = 2_000,
        val rawTailChars: Int = 262_144,
        val inputBytes: Int = 65_536,
        val commandBytes: Int = 65_536,
        val ioWaitMillis: Long = 2_000,
        val shutdownWaitMillis: Long = 5_000,
    ) {
        init {
            require(capacity in 1..32 && historyLines in 1..2_000 && rawTailChars in 2..262_144)
            require(inputBytes in 1..65_536 && commandBytes in 1..65_536)
            require(ioWaitMillis in 1..10_000 && shutdownWaitMillis in 1..10_000)
        }
    }

    public val owner: TerminalOwner = TerminalOwner(scope, session, TerminalIncarnation(UUID.randomUUID().toString()))
    private val root = canonicalRoot.toRealPath().also { require(java.nio.file.Files.isDirectory(it)) }
    private val registryLock = Any()
    private val entries = LinkedHashMap<TerminalSessionId, Entry>()
    private var stopped = false
    private val supported = System.getProperty("os.name") == "Mac OS X" &&
        System.getProperty("os.arch") in setOf("x86_64", "amd64")

    /** Test seam runs only AFTER the real native write; never substitutes a process or transport. */
    internal var afterNativeWrite: ((TerminalKey) -> Unit)? = null

    internal data class Diagnostics(
        val process: ProcessHandle?,
        val descendants: List<ProcessHandle>,
        val workers: List<Thread>,
        val queuedIoTasks: Int,
    )
    internal fun diagnostics(key: TerminalKey): Diagnostics? = find(key)?.diagnostics()

    override suspend fun create(request: TerminalCreateRequest): TerminalCreateResult {
        if (request.owner != owner) return TerminalCreateResult.Rejected(request.owner, "Stale terminal owner")
        val captured = request.copy(argv = request.argv.toList(), environment = request.environment.toMap())
        validate(captured)?.let { return TerminalCreateResult.Rejected(request.owner, it) }
        val caller = coroutineContext[Job]
        val cwd = try {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    val supplied = captured.cwd?.let { Path.of(it) } ?: root
                    (if (supplied.isAbsolute) supplied else root.resolve(supplied)).toRealPath().also {
                        require(it.startsWith(root) && java.nio.file.Files.isDirectory(it))
                    }
                }
            }
        } catch (_: Exception) {
            return TerminalCreateResult.Rejected(request.owner, "cwd must resolve to a directory within the bound Session root")
        }
        if (!supported) return TerminalCreateResult.Unavailable(request.owner, "PTY capability is verified only on macOS x86_64")
        if (caller?.isActive == false) return TerminalCreateResult.Rejected(request.owner, "Caller cancelled before creation")
        // Reserve ownership before native startup, including startup which outlives the caller's deadline.
        val entry = synchronized(registryLock) {
            if (stopped || entries.size >= limits.capacity) null else {
                val key = TerminalKey(owner, TerminalSessionId(UUID.randomUUID().toString()))
                Entry(key, cwd, captured.argv, captured.size).also { entries[key.terminal] = it }
            }
        } ?: return TerminalCreateResult.Rejected(request.owner, "Service stopped or terminal capacity exhausted")
        val future = entry.submit {
            val process = PtyProcessBuilder(captured.argv.toTypedArray())
                .setEnvironment(captured.environment).setDirectory(cwd.toString())
                .setInitialColumns(captured.size.columns).setInitialRows(captured.size.rows).start()
            entry.attach(process)
            if (entry.closing.get()) throw IOException("Service closed during native startup")
            entry.startReaders()
        }
        val failure = await(future, caller, limits.ioWaitMillis)
        if (failure != null) {
            entry.startCleanup()
            return TerminalCreateResult.UnknownOutcome(request.owner, entry.key, failure)
        }
        return TerminalCreateResult.Created(request.owner, entry.metadata())
    }

    override suspend fun list(owner: TerminalOwner): TerminalListResult {
        if (owner != this.owner) return TerminalListResult.Rejected(owner, "Stale terminal owner")
        if (!supported) return TerminalListResult.Unavailable(owner, "Unsupported PTY platform")
        return withContext(Dispatchers.IO) {
            TerminalListResult.Available(owner, synchronized(registryLock) { entries.values.toList() }.map { it.metadata() })
        }
    }

    override suspend fun read(key: TerminalKey): TerminalReadResult {
        if (key.owner != owner) return TerminalReadResult.Rejected(key, "Stale terminal owner")
        if (!supported) return TerminalReadResult.Unavailable(key, "Unsupported PTY platform")
        val entry = find(key) ?: return TerminalReadResult.NotFound(key)
        return withContext(Dispatchers.IO) { TerminalReadResult.Available(key, entry.snapshot()) }
    }

    override suspend fun write(key: TerminalKey, input: String): TerminalMutationResult {
        rejectKey(key)?.let { return it }
        if ('\u0000' in input || input.toByteArray(UTF_8).size > limits.inputBytes || malformedUtf16(input)) {
            return TerminalMutationResult.Rejected(key, "Invalid or oversized input")
        }
        val entry = find(key) ?: return TerminalMutationResult.NotFound(key)
        if (!entry.canInput()) return TerminalMutationResult.Rejected(key, "Terminal input is closed or outcome is unknown")
        val caller = coroutineContext[Job]
        if (caller?.isActive == false) return TerminalMutationResult.Rejected(key, "Caller cancelled before IO")
        val bytes = input.toByteArray(UTF_8)
        val future = entry.submit { entry.nativeWrite(bytes) }
        val failure = await(future, caller, limits.ioWaitMillis)
        if (failure != null) {
            entry.inputUnknown.set(true)
            return TerminalMutationResult.UnknownOutcome(key, failure)
        }
        return TerminalMutationResult.Applied(key, bytes.size)
    }

    override suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult {
        rejectKey(key)?.let { return it }
        val entry = find(key) ?: return TerminalMutationResult.NotFound(key)
        if (!entry.canInput()) return TerminalMutationResult.Rejected(key, "Terminal input is closed or outcome is unknown")
        val caller = coroutineContext[Job]
        if (caller?.isActive == false) return TerminalMutationResult.Rejected(key, "Caller cancelled before IO")
        val captured = when (input) {
            is TerminalInput.KeyPress -> TerminalInput.KeyPress(input.key, input.modifiers, input.character)
            else -> input // Text/Paste contain immutable whole strings.
        }
        val future = entry.submitInput(captured, caller)
        return withContext(NonCancellable) {
            val result = withContext(Dispatchers.IO) {
                try {
                    future.get(limits.ioWaitMillis, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    entry.inputUnknown.set(true)
                    future.cancel(true)
                    val reason = (if (e is ExecutionException) e.cause else e)?.message ?:
                        "Native semantic input did not complete within the deadline"
                    TerminalMutationResult.UnknownOutcome(key, reason)
                }
            }
            // A known pre-delivery refusal remains Rejected even if the caller cancelled.
            if (result is TerminalMutationResult.Applied && caller?.isActive == false) {
                entry.inputUnknown.set(true)
                TerminalMutationResult.UnknownOutcome(key, "Caller cancelled during native IO; delivery is unknown")
            } else result
        }
    }

    override suspend fun resize(key: TerminalKey, size: TerminalSize): TerminalMutationResult {
        rejectKey(key)?.let { return it }
        val entry = find(key) ?: return TerminalMutationResult.NotFound(key)
        if (!entry.canInput()) return TerminalMutationResult.Rejected(key, "Terminal is closed or outcome is unknown")
        val caller = coroutineContext[Job]
        if (caller?.isActive == false) return TerminalMutationResult.Rejected(key, "Caller cancelled before resize")
        val future = entry.submit {
            try {
                check(entry.canInput())
                val process = entry.process ?: throw IOException("PTY startup is incomplete")
                process.setWinSize(WinSize(size.columns, size.rows))
                entry.gate.withLock {
                    entry.terminal.resize(TermSize(size.columns, size.rows), RequestOrigin.User)
                    entry.revision++
                    check(entry.buffer.width == size.columns && entry.buffer.height == size.rows)
                }
                val actual = process.winSize // kernel query must not block the snapshot/model gate
                check(actual.columns == size.columns && actual.rows == size.rows)
            } catch (e: Exception) {
                entry.inputUnknown.set(true) // fence queued IO before this worker can take its next task
                throw e
            }
        }
        val failure = await(future, caller, limits.ioWaitMillis)
        if (failure != null) {
            entry.inputUnknown.set(true)
            return TerminalMutationResult.UnknownOutcome(key, failure)
        }
        return TerminalMutationResult.Applied(key, size = size)
    }

    override suspend fun close(key: TerminalKey): TerminalMutationResult {
        rejectKey(key)?.let { return it }
        val entry = find(key) ?: return TerminalMutationResult.NotFound(key)
        entry.startCleanup()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.shutdownWaitMillis)
        val clean = withContext(NonCancellable) {
            withContext(Dispatchers.IO) { entry.confirmCleanup(deadline) }
        }
        if (!clean) return TerminalMutationResult.UnknownOutcome(key, "Owned native process or workers have not been confirmed stopped")
        synchronized(registryLock) { if (entries[key.terminal] === entry) entries.remove(key.terminal) }
        return TerminalMutationResult.Applied(key)
    }

    override suspend fun shutdown(owner: TerminalOwner): TerminalShutdownResult {
        if (owner != this.owner) return TerminalShutdownResult.Rejected(owner, "Stale terminal owner")
        val owned = synchronized(registryLock) { stopped = true; entries.values.toList() }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.shutdownWaitMillis)
        owned.forEach { it.startCleanup() }
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                owned.forEach { entry ->
                    if (entry.confirmCleanup(deadline)) synchronized(registryLock) {
                        if (entries[entry.key.terminal] === entry) entries.remove(entry.key.terminal)
                    }
                }
            }
        }
        val remaining = synchronized(registryLock) { entries.values.map { it.key } }
        return if (remaining.isEmpty()) TerminalShutdownResult.Applied(owner) else TerminalShutdownResult.UnknownOutcome(owner, remaining)
    }

    private fun find(key: TerminalKey): Entry? = if (key.owner != owner) null else synchronized(registryLock) { entries[key.terminal] }
    private fun rejectKey(key: TerminalKey): TerminalMutationResult? = when {
        key.owner != owner -> TerminalMutationResult.Rejected(key, "Stale terminal owner")
        !supported -> TerminalMutationResult.Unavailable(key, "Unsupported PTY platform")
        else -> null
    }

    private fun validate(request: TerminalCreateRequest): String? {
        if (request.argv.isEmpty() || request.argv.first().isBlank()) return "argv must contain an explicit executable"
        val strings = request.argv + request.environment.flatMap { listOf(it.key, it.value) } + listOfNotNull(request.cwd)
        if (strings.any { '\u0000' in it || malformedUtf16(it) }) return "NUL or malformed UTF-16 in request"
        if (request.environment.keys.any { it.isEmpty() || '=' in it }) return "Invalid environment name"
        if (strings.sumOf { it.toByteArray(UTF_8).size.toLong() } > limits.commandBytes) return "Command/environment budget exhausted"
        return null
    }

    /** No cancellable dispatcher handoff after native delivery: return the explicit unknown result. */
    private suspend fun await(future: Future<*>, caller: Job?, millis: Long): String? = withContext(NonCancellable) {
        // The IO round trip targets the active outer NonCancellable Job, never the cancelled caller Job.
        val failure = withContext(Dispatchers.IO) {
            try {
                future.get(millis, TimeUnit.MILLISECONDS)
                null
            } catch (e: Exception) {
                future.cancel(true)
                (if (e is ExecutionException) e.cause else e)?.message ?: "Native IO did not complete within the deadline"
            }
        }
        if (caller?.isActive == false) "Caller cancelled during native IO; delivery is unknown" else failure
    }

    private data class OwnedProcess(val handle: ProcessHandle, val started: Instant) {
        fun alive(): Boolean = handle.isAlive && handle.info().startInstant().orElse(null) == started
        fun confirmedGone(): Boolean = !handle.isAlive || handle.info().startInstant().orElse(null)?.let { it != started } == true
        fun kill() { if (alive()) handle.destroyForcibly() }
    }

    private inner class Entry(val key: TerminalKey, val cwd: Path, val argv: List<String>, size: TerminalSize) {
        // Global ordering: model gate, then TerminalTextBuffer lock. Native read releases the model gate.
        val gate = ReentrantLock(true)
        val closing = AtomicBoolean(false)
        val inputUnknown = AtomicBoolean(false)
        private val workers = CopyOnWriteArrayList<Thread>()
        private val descendants = ConcurrentHashMap<Long, OwnedProcess>()
        private val cleanupUncertain = AtomicBoolean(false)
        @Volatile var process: PtyProcess? = null
        @Volatile private var rootProcess: OwnedProcess? = null
        @Volatile private var reader: InputStreamReader? = null
        @Volatile private var parser: Thread? = null
        @Volatile private var watcher: Thread? = null
        @Volatile private var cleanup: Thread? = null
        @Volatile private var exitCode: Int? = null
        @Volatile private var parserFinished = false
        @Volatile private var realEof = false
        @Volatile private var parserFailure: String? = null
        private val defaultForeground = TerminalColor(Supplier { Color(255, 255, 255) })
        private val defaultBackground = TerminalColor(Supplier { Color(0, 0, 0) })
        private val styles = StyleState().apply {
            setDefaultStyle(TextStyle(this@Entry.defaultForeground, this@Entry.defaultBackground))
            reset()
        }
        val buffer = TerminalTextBuffer(size.columns, size.rows, styles, limits.historyLines)
        private val display = HeadlessDisplay()
        val terminal = JvmTerminalInputAdapter(display, buffer, styles)
        var revision = 0L
        private val raw = StringBuilder()
        private var truncated = false
        private val io = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(8),
            { task -> worker("io", task) }, ThreadPoolExecutor.AbortPolicy())

        fun worker(role: String, task: Runnable): Thread = Thread(task, "harness-pty-" + key.terminal.value + "-" + role)
            .apply { isDaemon = true; workers.add(this) }

        fun submit(action: () -> Unit): Future<*> = try {
            io.submit(Callable { action() })
        } catch (e: Exception) {
            java.util.concurrent.CompletableFuture<Unit>().also { it.completeExceptionally(e) }
        }

        fun submitInput(input: TerminalInput, caller: Job?): Future<TerminalMutationResult> = try {
            io.submit(Callable<TerminalMutationResult> {
                val bytes = gate.withLock {
                    if (!canInput()) return@Callable TerminalMutationResult.Rejected(key, "Input fenced before encoding")
                    if (caller?.isActive == false) return@Callable TerminalMutationResult.Rejected(key, "Caller cancelled before delivery")
                    encodeInput(input) ?: return@Callable TerminalMutationResult.Rejected(key, "Invalid, unsupported or oversized semantic input")
                }
                // The same worker directly writes after releasing the gate. It never
                // submits to itself, nor holds the model gate across native IO.
                if (!canInput()) return@Callable TerminalMutationResult.Rejected(key, "Input fenced before delivery")
                if (caller?.isActive == false) return@Callable TerminalMutationResult.Rejected(key, "Caller cancelled before delivery")
                try {
                    nativeWrite(bytes)
                    if (caller?.isActive == false) {
                        inputUnknown.set(true) // fence before this worker takes its next queued task
                        TerminalMutationResult.UnknownOutcome(key, "Caller cancelled during native IO; delivery is unknown")
                    } else TerminalMutationResult.Applied(key, bytes.size)
                } catch (e: Exception) {
                    inputUnknown.set(true)
                    TerminalMutationResult.UnknownOutcome(key, e.message ?: "Native semantic input outcome is unknown")
                }
            })
        } catch (_: RejectedExecutionException) {
            java.util.concurrent.CompletableFuture.completedFuture<TerminalMutationResult>(
                TerminalMutationResult.Rejected(key, "Terminal IO queue is full or closed"))
        }

        private fun encodeInput(input: TerminalInput): ByteArray? {
            check(gate.isHeldByCurrentThread)
            val bytes = when (input) {
                is TerminalInput.KeyPress -> terminal.encode(input) ?: return null
                is TerminalInput.Text -> {
                    if (invalidSemanticText(input.text)) return null
                    input.text.toByteArray(UTF_8)
                }
                is TerminalInput.Paste -> {
                    if (invalidSemanticText(input.text)) return null
                    val normalized = input.text.replace("\r\n", "\n").replace('\n', '\r')
                    val text = if (display.bracketedPaste) "\u001b[200~" + normalized + "\u001b[201~" else normalized
                    text.toByteArray(UTF_8)
                }
            }
            return bytes.takeIf { it.size <= limits.inputBytes }
        }

        private fun invalidSemanticText(text: String): Boolean =
            text.isEmpty() || '\u0000' in text || malformedUtf16(text)

        fun attach(value: PtyProcess) {
            process = value // published even if a cancelled startup finishes late
            // pty4j overrides pid(), not Process.toHandle(). Capture the actual PID's birth identity.
            val handle = ProcessHandle.of(value.pid()).orElse(null)
            val started = handle?.info()?.startInstant()?.orElse(null)
            if (handle != null && started != null) rootProcess = OwnedProcess(handle, started)
            else if (value.isAlive) cleanupUncertain.set(true)
            rememberDescendants()
            reader = InputStreamReader(value.inputStream, UTF_8)
        }

        fun canInput(): Boolean = !closing.get() && !inputUnknown.get() && exitCode == null && parserFailure == null && process?.isAlive == true

        fun nativeWrite(bytes: ByteArray) {
            try {
                if (bytes.size > limits.inputBytes) throw IOException("Native input budget exhausted")
                if (!canInput()) throw IOException("Input fenced before delivery")
                val output = process?.outputStream ?: throw IOException("No native output stream")
                output.write(bytes)
                afterNativeWrite?.invoke(key)
                output.flush()
            } catch (e: Exception) {
                inputUnknown.set(true)
                throw e
            }
        }

        private val connector = object : TtyConnector {
            override fun read(chars: CharArray, offset: Int, length: Int): Int {
                check(gate.isHeldByCurrentThread)
                gate.unlock()
                val count = try { reader?.read(chars, offset, length) ?: throw IOException("Reader not attached") }
                finally { gate.lock() }
                if (count < 0) realEof = true
                if (count > 0) {
                    raw.append(chars, offset, count)
                    if (raw.length > limits.rawTailChars) {
                        var cut = raw.length - limits.rawTailChars
                        if (raw[cut].isLowSurrogate()) cut++
                        raw.delete(0, cut)
                        truncated = true
                    }
                    revision++
                }
                return count
            }
            override fun write(bytes: ByteArray) {
                // Device replies share the same bounded native IO worker. Never wait while holding the model gate.
                check(gate.isHeldByCurrentThread)
                gate.unlock()
                val future = submit { nativeWrite(bytes) }
                try { future.get(limits.ioWaitMillis, TimeUnit.MILLISECONDS) }
                catch (e: Exception) { inputUnknown.set(true); future.cancel(true); throw IOException("Device reply outcome unknown", e) }
                finally { gate.lock() }
            }
            override fun write(string: String) = write(string.toByteArray(UTF_8))
            override fun isConnected(): Boolean = process?.isAlive == true && !closing.get()
            override fun waitFor(): Int = process?.waitFor() ?: throw IOException("No PTY")
            override fun ready(): Boolean = false // readiness must not take InputStreamReader's blocking monitor
            override fun getName(): String = key.terminal.value
            override fun close() { startCleanup() }
        }

        fun startReaders() {
            terminal.setTerminalOutput(object : TerminalOutputStream {
                override fun sendBytes(bytes: ByteArray, userInput: Boolean) = connector.write(bytes)
                override fun sendString(string: String, userInput: Boolean) = connector.write(string)
            })
            parser = worker("parser", Runnable {
                gate.withLock {
                    try {
                        val emulator = JediEmulator(TtyBasedArrayDataStream(connector), terminal)
                        while (emulator.hasNext() && !closing.get()) { emulator.next(); revision++ }
                    } catch (e: Exception) {
                        parserFailure = e.message ?: e.javaClass.simpleName
                        inputUnknown.set(true)
                    } finally { parserFinished = true; revision++ }
                }
            }).also { it.start() }
            watcher = worker("waiter", Runnable {
                try {
                    val native = process ?: return@Runnable
                    while (!native.waitFor(25, TimeUnit.MILLISECONDS)) rememberDescendants()
                    val code = native.exitValue()
                    gate.withLock { exitCode = code; revision++ }
                } catch (e: Exception) {
                    gate.withLock {
                        parserFailure = e.message ?: "Native wait failed"
                        inputUnknown.set(true)
                        revision++
                    }
                }
            }).also { it.start() }
        }

        fun metadata(): TerminalMetadata = gate.withLock { metadataLocked() }
        private fun metadataLocked(): TerminalMetadata {
            val lifecycle = when {
                exitCode != null && parserFinished && realEof && parserFailure == null -> TerminalLifecycle.Exited(exitCode!!)
                parserFailure != null || process == null || closing.get() -> TerminalLifecycle.Unknown
                else -> TerminalLifecycle.Running
            }
            val input = when {
                inputUnknown.get() -> TerminalInputState.Unknown
                closing.get() || exitCode != null -> TerminalInputState.Closed
                else -> TerminalInputState.Open
            }
            return TerminalMetadata(key.copy(owner = owner.copy()), cwd.toString(), argv.toList(), lifecycle, input)
        }

        fun snapshot(): TerminalSnapshot = gate.withLock {
            buffer.lock()
            try {
                val dimensions = TerminalSize(buffer.height, buffer.width)
                var tail = raw.toString()
                if (tail.lastOrNull()?.isHighSurrogate() == true) tail = tail.dropLast(1)
                TerminalSnapshot(metadataLocked(), dimensions,
                    List(buffer.height) { copyLine(buffer.getLine(it), buffer.width) },
                    List(buffer.historyLinesCount) { copyLine(buffer.getLine(it - buffer.historyLinesCount), buffer.width) },
                    TerminalCursor((terminal.cursorX - 1).coerceIn(0, buffer.width),
                        (terminal.cursorY - 1).coerceIn(0, buffer.height - 1), display.visible),
                    buffer.isUsingAlternateBuffer, revision, tail, truncated)
            } finally { buffer.unlock() }
        }

        private fun copyLine(line: TerminalLine, columns: Int): TerminalLineSnapshot {
            var column = 0
            val runs = ArrayList<TerminalTextRun>()
            for (entry in line.entries) {
                val count = minOf(entry.length, columns - column)
                if (count <= 0) break
                val full = entry.text.toString()
                val text = StringBuilder()
                val offsets = ArrayList<Int>()
                var index = 0
                while (index < count) {
                    val char = full[index]
                    when {
                        char == CharUtils.DWC -> index++
                        char.isHighSurrogate() && index + 1 < entry.length && full[index + 1].isLowSurrogate() -> {
                            // A viewport cut through a pair keeps both background and native span.
                            if (index + 1 < count) {
                                offsets.add(index); text.append(char)
                                offsets.add(index + 1); text.append(full[index + 1])
                            }
                            index += 2
                        }
                        else -> {
                            offsets.add(index)
                            text.append(when {
                                char == '\u0000' -> ' '
                                char.isSurrogate() -> '\uFFFD'
                                else -> char
                            })
                            index++
                        }
                    }
                }
                offsets.add(count)
                runs.add(TerminalTextRun(column, count, text.toString(), copyStyle(entry.style), offsets.toList()))
                column += count
            }
            return TerminalLineSnapshot(runs.toList(), line.isWrapped)
        }

        private fun copyStyle(style: TextStyle): TerminalTextStyle {
            val attributes = TextStyle.Option.values().filter { style.hasOption(it) }.map {
                when (it) {
                    TextStyle.Option.BOLD -> TerminalTextAttribute.Bold
                    TextStyle.Option.ITALIC -> TerminalTextAttribute.Italic
                    TextStyle.Option.DIM -> TerminalTextAttribute.Dim
                    TextStyle.Option.INVERSE -> TerminalTextAttribute.Inverse
                    TextStyle.Option.UNDERLINED -> TerminalTextAttribute.Underlined
                    TextStyle.Option.HIDDEN -> TerminalTextAttribute.Hidden
                    TextStyle.Option.SLOW_BLINK -> TerminalTextAttribute.SlowBlink
                    TextStyle.Option.RAPID_BLINK -> TerminalTextAttribute.RapidBlink
                }
            }.toSet()
            return TerminalTextStyle(color(style.foreground), color(style.background), attributes)
        }

        private fun color(value: TerminalColor?): TerminalColorValue = when {
            value == null || value === defaultForeground || value === defaultBackground -> TerminalColorValue.Default
            value.isIndexed -> TerminalColorValue.Indexed(value.colorIndex)
            else -> value.toColor().let { TerminalColorValue.Rgb(it.red, it.green, it.blue) }
        }

        private fun rememberDescendants() {
            val native = process ?: return
            val root = rootProcess ?: return
            if (!root.alive()) {
                if (native.isAlive && !root.confirmedGone()) cleanupUncertain.set(true)
                return
            }
            try {
                descendants.entries.removeIf { it.value.confirmedGone() }
                root.handle.descendants().use { stream -> stream.forEach { handle ->
                    val started = handle.info().startInstant().orElse(null)
                    if (started == null && handle.isAlive) cleanupUncertain.set(true)
                    else if (started != null) descendants.putIfAbsent(handle.pid(), OwnedProcess(handle, started))
                } }
            } catch (_: Exception) { cleanupUncertain.set(true) }
        }

        @Synchronized fun startCleanup() {
            closing.set(true)
            if (cleanup?.isAlive == true) return
            workers.removeIf { it.state == Thread.State.TERMINATED }
            cleanup = worker("cleanup", Runnable {
                // Native startup runs on io: terminate the executor, then wait for any late owned process.
                io.shutdownNow()
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.shutdownWaitMillis)
                try {
                    while (!io.isTerminated && System.nanoTime() < deadline) {
                        stopNative(deadline)
                        io.awaitTermination(25, TimeUnit.MILLISECONDS)
                    }
                    stopNative(deadline)
                } catch (_: Exception) { cleanupUncertain.set(true) }
            }).also { it.start() }
        }

        private fun stopNative(deadline: Long) {
            val native = process ?: return
            rememberDescendants()
            descendants.values.forEach { it.kill() }
            // Let the still-owned parent reap its children before terminating it, within the same close budget.
            val reapDeadline = minOf(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100))
            for (child in descendants.values) {
                val remaining = reapDeadline - System.nanoTime()
                if (child.alive() && remaining > 0) try {
                    child.handle.onExit().get(remaining, TimeUnit.NANOSECONDS)
                } catch (_: java.util.concurrent.TimeoutException) { /* actual liveness is checked before release */ }
            }
            if (native.isAlive) native.destroyForcibly()
            // Close the streams, not the Reader: Reader.close can wait on its blocked read monitor.
            val closers = listOf<() -> Unit>(
                { native.inputStream.close() }, { native.outputStream.close() }, { native.errorStream.close() })
            for (close in closers) try { close() } catch (_: IOException) { cleanupUncertain.set(true) }
            parser?.interrupt()
        }

        fun confirmCleanup(deadline: Long): Boolean {
            // Join actual Thread objects and consult actual native handles; no cached "closed" flag proves release.
            for (thread in workers.toList()) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0 && thread.isAlive) return false
                if (thread.isAlive) thread.join(maxOf(1, TimeUnit.NANOSECONDS.toMillis(remaining)))
            }
            val native = process
            return io.isTerminated && workers.none { it.isAlive } && native?.isAlive != true &&
                descendants.values.all { it.confirmedGone() } && !cleanupUncertain.get()
        }

        fun diagnostics(): Diagnostics = Diagnostics(rootProcess?.handle, descendants.values.map { it.handle }, workers.toList(), io.queue.size)
    }

    private class HeadlessDisplay : TerminalDisplay {
        var visible = true
        var bracketedPaste = false
        private var title = ""
        override fun setCursor(x: Int, y: Int) = Unit
        override fun setCursorShape(cursorShape: CursorShape?) = Unit
        override fun beep() = Unit
        override fun scrollArea(top: Int, size: Int, dy: Int) = Unit
        override fun setCursorVisible(isCursorVisible: Boolean) { visible = isCursorVisible }
        override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) = Unit
        override fun getWindowTitle(): String = title
        override fun setWindowTitle(windowTitle: String) { title = windowTitle }
        override fun setBracketedPasteMode(enabled: Boolean) { bracketedPaste = enabled }
        override fun getSelection(): TerminalSelection? = null
        override fun terminalMouseModeSet(mouseMode: MouseMode) = Unit
        override fun setMouseFormat(mouseFormat: MouseFormat) = Unit
        override fun ambiguousCharsAreDoubleWidth(): Boolean = false
    }

    private fun malformedUtf16(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (char.isLowSurrogate()) return true
            if (char.isHighSurrogate() && (index == text.length || !text[index++].isLowSurrogate())) return true
        }
        return false
    }
}

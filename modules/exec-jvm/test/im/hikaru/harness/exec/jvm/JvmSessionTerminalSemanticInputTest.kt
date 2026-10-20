package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.*
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.*
import im.hikaru.harness.tools.ToolsPlugin
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*

class JvmSessionTerminalSemanticInputTest {
    @Test
    fun realHostSemanticInputUsesParserModesAndExactByteCounts() = runBlocking<Unit> {
        withFixture(inputBytes = 16) { f ->
            val phases = listOf(
                Phase("\u001b[?1l", TerminalInput.Text("π中🙂"), "π中🙂"),
                Phase("\u001b[?2004h", TerminalInput.Paste("a\r\nb"), "\u001b[200~a\rb\u001b[201~"),
                Phase("\u001b[?1h", TerminalInput.KeyPress(TerminalKeyName.Up), "\u001bOA"),
                Phase("\u001b[?1h", TerminalInput.KeyPress(TerminalKeyName.Up, setOf(TerminalModifier.Control)), "\u001b[1;5A"),
                Phase("\u001b[?1l", TerminalInput.KeyPress(TerminalKeyName.Up), "\u001b[A"),
                Phase("", TerminalInput.Text("OK"), "OK"),
            )
            val capture = f.capture(phases)
            for (index in phases.indices) {
                f.ready(capture, index)
                val before = f.writes.get()
                if (index == 1) {
                    for (denied in listOf<TerminalInput>(TerminalInput.Text(""), TerminalInput.Text("\u0000"),
                        TerminalInput.Text("x".repeat(17)), TerminalInput.Paste("abcde"),
                        TerminalInput.KeyPress(TerminalKeyName.Up, setOf(TerminalModifier.Meta)))) {
                        assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(capture.key, denied)).key)
                        assertEquals(before, f.writes.get())
                        assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
                    }
                }
                f.deliver(capture, index)
            }
            f.finish(capture)
            assertEquals(6, f.writes.get())
            assertEquals(1, f.pids().size)
            assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
        }
    }

    @Test
    fun staleOwnerAndSessionReplacementRejectSemanticDispatch() = runBlocking<Unit> {
        withFixture { f ->
            val phase = Phase("", TerminalInput.Text("VALID"), "VALID")
            val capture = f.capture(listOf(phase))
            f.ready(capture, 0)
            val key = capture.key
            val stale = listOf(key.copy(owner = key.owner.copy(scope = TerminalScope("foreign"))),
                key.copy(owner = key.owner.copy(session = SessionId("foreign"))),
                key.copy(owner = key.owner.copy(incarnation = TerminalIncarnation("foreign"))))
            for (requested in stale) for (input in listOf<TerminalInput>(TerminalInput.Text("BAD"),
                TerminalInput.Paste("BAD"), TerminalInput.KeyPress(TerminalKeyName.Enter))) {
                assertEquals(requested, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(requested, input)).key)
            }
            val missing = key.copy(terminal = TerminalSessionId("missing"))
            assertEquals(missing, assertIs<TerminalMutationResult.NotFound>(f.api.inputTerminal(missing, phase.input)).key)
            assertEquals(0, f.writes.get())
            assertEquals(1, f.pids().size)
            f.until("stale input leaves native capture empty") { Files.exists(f.binary(capture, 0)) }
            assertEquals(0L, Files.size(f.binary(capture, 0)))
            assertEquals(listOf(key), assertIs<TerminalListResult.Available>(f.api.terminals(key.owner)).terminals.map { it.key })
            // Replace the live Session object behind the same textual id while the Agent still retains the old object.
            bounded { f.runtime.context.sessions.attach(f.handle.agent.session).dispose() }
            val replacement = f.runtime.context.sessions.create(id = f.handle.agent.session.id,
                options = CreateSessionOptions(cwd = f.cwd.toString()))
            val replacementSession = replacement.session
            assertEquals(f.handle.agent.session.id, replacementSession.id)
            assertNotSame(f.handle.agent.session, replacementSession)
            assertSame(f.handle.agent, f.runtime.context.agents.get(f.handle.agent.id))
            assertEquals(key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(key, phase.input)).key)
            assertEquals(0, f.writes.get())
            assertEquals(1, f.pids().size)
            bounded { f.handle.dispose() }
            assertSame(replacementSession, f.runtime.context.sessions.get(replacementSession.id))
            val fresh = f.runtime.context.agents.restore(replacementSession, AgentOptions(cwd = f.cwd.toString()))
            assertEquals(key.owner.session, fresh.agent.session.id)
            assertNotSame(f.handle.agent.session, fresh.agent.session)
            val owner = assertIs<HostTerminalDiscoveryResult.Available>(f.api.terminalCapability(fresh.agent.session.id)).owner
            assertEquals(key.owner.scope, owner.scope)
            assertNotEquals(key.owner.incarnation, owner.incarnation)
            assertEquals(key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(key, phase.input)).key)
            val next = f.capture(listOf(phase), owner)
            f.deliver(next, 0)
            f.finish(next)
            assertEquals(1, f.writes.get())
            assertEquals(2, f.pids().size)
        }
    }

    @Test
    fun hostSemanticCancellationAndNativeFailureHaveDistinctFences() = runBlocking<Unit> {
        withFixture { f ->
            val capture = f.capture(listOf(Phase("", TerminalInput.Text("ONCE"), "ONCE"),
                Phase("", TerminalInput.Text("OPEN"), "OPEN")))
            f.ready(capture, 0)
            coroutineScope {
                val observed = CompletableDeferred<Result<TerminalMutationResult>>()
                val cancelled = launch(Dispatchers.Default) {
                    coroutineContext[Job]!!.cancel()
                    observed.complete(runCatching { f.api.inputTerminal(capture.key, TerminalInput.Text("BEFORE")) })
                }
                assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(bounded { observed.await() }.getOrThrow()).key)
                bounded { cancelled.join() }
                assertEquals(0, f.writes.get())
            }
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.host.afterNativeWrite = {
                assertEquals(capture.key, it)
                f.writes.incrementAndGet()
                entered.countDown()
                assertTrue(release.await(10, TimeUnit.SECONDS))
            }
            coroutineScope {
                val observed = CompletableDeferred<Result<TerminalMutationResult>>()
                val writer = launch(Dispatchers.Default) {
                    observed.complete(runCatching { f.api.inputTerminal(capture.key, capture.phases[0].input) })
                }
                try {
                    awaitLatch(entered)
                    assertEquals(1, f.writes.get())
                    writer.cancel()
                    release.countDown()
                    assertEquals(capture.key, assertIs<TerminalMutationResult.UnknownOutcome>(bounded { observed.await() }.getOrThrow()).key)
                    bounded { writer.join() }
                    f.expect(capture, 0)
                } finally {
                    release.countDown()
                    withContext(NonCancellable) { bounded { writer.join() } }
                    f.countWrites()
                }
            }
            // Host's outer NonCancellable boundary lost only the acknowledgement: native remains Open.
            assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
            f.deliver(capture, 1)
            f.finish(capture)
            assertEquals(2, f.writes.get())
        }
        withFixture { f ->
            val capture = f.capture(listOf(Phase("", TerminalInput.Text("ONCE🙂"), "ONCE🙂")))
            f.ready(capture, 0)
            f.host.afterNativeWrite = { written ->
                assertEquals(capture.key, written)
                f.writes.incrementAndGet()
                throw IOException("Native acknowledgement failed after real delivery")
            }
            assertEquals(capture.key, assertIs<TerminalMutationResult.UnknownOutcome>(f.api.inputTerminal(capture.key, capture.phases[0].input)).key)
            f.expect(capture, 0)
            f.finish(capture)
            assertEquals(TerminalInputState.Unknown, f.snapshot(capture.key).metadata.input)
            assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(capture.key, TerminalInput.Text("RETRY")))
            assertIs<TerminalMutationResult.Rejected>(f.api.writeTerminal(capture.key, "RAW_RETRY"))
            assertIs<TerminalMutationResult.Rejected>(f.api.resizeTerminal(capture.key, TerminalSize(30, 100)))
            assertEquals(1, f.writes.get())
            assertContentEquals("ONCE🙂".toByteArray(UTF_8), Files.readAllBytes(f.binary(capture, 0)))
            assertEquals(0L, Files.size(f.extra(capture)))
        }
    }

    @Test
    fun hostCapturesModifiersAndDisposesOwnedPtyWorkers() = runBlocking<Unit> {
        withFixture { f ->
            val original = mutableSetOf(TerminalModifier.Control, TerminalModifier.Shift)
            val input = TerminalInput.KeyPress(TerminalKeyName.Up, original)
            val capture = f.capture(listOf(Phase("\u001b[?1h", TerminalInput.Text("GATE"), "GATE"),
                Phase("\u001b[?1h", input, "\u001b[1;6A")))
            f.ready(capture, 0)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.host.afterNativeWrite = {
                f.writes.incrementAndGet()
                if (f.writes.get() == 1) { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
            }
            coroutineScope {
                val first = async(Dispatchers.Default) { f.api.inputTerminal(capture.key, capture.phases[0].input) }
                var second: Deferred<TerminalMutationResult>? = null
                try {
                    awaitLatch(entered)
                    // UNDISPATCHED enters Host synchronously and captures before waiting on its mutation gate.
                    val queued = async(start = CoroutineStart.UNDISPATCHED) { f.api.inputTerminal(capture.key, input) }
                    second = queued
                    original.clear()
                    @Suppress("UNCHECKED_CAST")
                    (input.modifiers as MutableSet<TerminalModifier>).clear()
                    release.countDown()
                    assertEquals(4, assertIs<TerminalMutationResult.Applied>(bounded { first.await() }).bytes)
                    assertEquals(6, assertIs<TerminalMutationResult.Applied>(bounded { queued.await() }).bytes)
                    f.expect(capture, 0)
                    f.expect(capture, 1)
                    f.finish(capture)
                } finally {
                    release.countDown()
                    withContext(NonCancellable) { bounded { first.join(); second?.join() } }
                    f.countWrites()
                }
            }
            f.until("actual owned dd descendant") { f.host.diagnostics(capture.key)?.descendants?.any { it.isAlive } == true }
            val owned = assertNotNull(f.host.diagnostics(capture.key))
            f.observed += owned
            assertNotNull(owned.process)
            assertTrue(owned.workers.isNotEmpty())
            assertTrue(owned.descendants.any { it.isAlive })
            bounded { f.runtime.context.dispose() }
            assertTrue(f.runtime.context.isDisposed)
            assertEquals(0, f.host.bindingCount())
            f.assertStopped(owned)
            assertEquals(capture.key, assertIs<TerminalMutationResult.Unavailable>(f.host.input(capture.key, TerminalInput.Text("AFTER"))).key)
            bounded { f.runtime.context.dispose(); f.host.dispose() }
            f.assertStopped(owned)
            assertEquals(2, f.writes.get())
        }
    }

    private data class Phase(val modes: String, val input: TerminalInput, val expected: String)
    private data class Capture(val key: TerminalKey, val token: String, val phases: List<Phase>)

    private suspend fun withFixture(inputBytes: Int = 65_536, body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-semantic-host-chain-").toRealPath()
        val runtime = Runtime()
        var fixture: Fixture? = null
        try {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
            runtime.install(SubprocessPlugin(SubprocessConfig(terminalLimits = JvmPtyTerminalSessions.Limits(
                inputBytes = inputBytes, ioWaitMillis = 10_000))))
            runtime.install(SessionApiPlugin())
            val handle = runtime.context.agents.create(AgentId("owner"), AgentOptions(cwd = cwd.toString()))
            val f = Fixture(runtime, cwd, handle)
            fixture = f
            body(f)
        } finally {
            withContext(NonCancellable) {
                try {
                    try { bounded { runtime.context.dispose() } }
                    finally {
                        fixture?.let { f ->
                            assertEquals(0, f.host.bindingCount())
                            for (owned in f.observed) f.assertStopped(owned)
                            f.until("all owned PID exits") { f.pids().none { pid -> ProcessHandle.of(pid).map { it.isAlive }.orElse(false) } }
                        }
                    }
                } finally { Files.walk(cwd).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val cwd: Path, val handle: AgentHandle) {
        val api = runtime.context.sessionApi
        val host = runtime.context.require(SessionTerminalHostKey) as JvmSessionTerminalHost
        val writes = AtomicInteger()
        val observed = mutableListOf<JvmPtyTerminalSessions.Diagnostics>()
        private val home = Files.createDirectory(cwd.resolve("home"))
        init { countWrites() }
        fun countWrites() { host.afterNativeWrite = { writes.incrementAndGet() } }
        private fun quote(text: String) = "'" + text.replace("'", "'\"'\"'") + "'"
        private fun display(text: String) = "printf '%s' " + quote("\u001b[2J\u001b[H" + text)
        fun binary(capture: Capture, index: Int): Path = cwd.resolve(capture.token + "_" + index + ".bin")
        fun extra(capture: Capture): Path = cwd.resolve(capture.token + "_extra.bin")
        fun pids(): List<Long> = if (Files.exists(cwd.resolve("started-pids")))
            Files.readAllLines(cwd.resolve("started-pids")).filter { it.isNotBlank() }.map { it.toLong() } else emptyList()

        suspend fun capture(phases: List<Phase>, owner: TerminalOwner? = null): Capture {
            val requested = owner ?: assertIs<HostTerminalDiscoveryResult.Available>(api.terminalCapability(handle.agent.session.id)).owner
            val token = "HS_" + UUID.randomUUID().toString().replace("-", "")
            val dollar = 36.toChar()
            val script = buildString {
                append("test -t 0 && test -t 1 && test -t 2 || exit 70; stty raw -echo || exit 71; ")
                append("printf '%s\n' \"").append(dollar).append(dollar).append("\" >> started-pids; ")
                for ((index, phase) in phases.withIndex()) {
                    append("printf '%s' ").append(quote(phase.modes)).append("; ")
                    append(display(token + "_READY_" + index)).append("; ")
                    val file = token + "_" + index + ".bin"
                    append("dd bs=1 count=").append(phase.expected.toByteArray(UTF_8).size)
                        .append(" of=").append(file).append(" 2>/dev/null || exit 72; ")
                    append("od -An -v -tx1 ").append(file).append(" > ").append(file).append(".hex || exit 73; ")
                }
                append(display(token + "_DRAIN")).append("; dd bs=1 of=").append(token).append("_extra.bin 2>/dev/null; exit 0")
            }
            val result = assertIs<HostTerminalCreateResult.Created>(api.createTerminal(TerminalCreateRequest(requested,
                listOf("/bin/sh", "-c", script), mapOf("HOME" to home.toString(), "PATH" to "/usr/bin:/bin",
                    "TERM" to "xterm-256color", "LC_ALL" to "C"), cwd = cwd.toString())))
            assertEquals(requested, result.owner)
            val key = result.metadata.key
            observed += assertNotNull(host.diagnostics(key))
            return Capture(key, token, phases)
        }

        suspend fun ready(capture: Capture, index: Int) {
            until("parser screen marker") { snapshot(capture.key).screen.any { it.text.contains(capture.token + "_READY_" + index) } }
            until("owned native dd and byte file ready") {
                val owned = host.diagnostics(capture.key)
                if (owned != null) observed += owned
                Files.exists(binary(capture, index)) && owned?.descendants?.any { it.isAlive } == true
            }
            val owned = assertNotNull(host.diagnostics(capture.key))
            observed += owned
            assertTrue(owned.process?.isAlive == true)
            assertTrue(owned.workers.map { it.name.substringAfterLast('-') }.containsAll(listOf("io", "parser", "waiter")))
            assertTrue(owned.descendants.any { it.isAlive })
        }
        suspend fun deliver(capture: Capture, index: Int) {
            ready(capture, index)
            val phase = capture.phases[index]
            val before = writes.get()
            val applied = assertIs<TerminalMutationResult.Applied>(api.inputTerminal(capture.key, phase.input))
            assertEquals(capture.key, applied.key)
            assertEquals(phase.expected.toByteArray(UTF_8).size, applied.bytes)
            assertEquals(before + 1, writes.get())
            expect(capture, index)
        }
        suspend fun expect(capture: Capture, index: Int) {
            val expected = capture.phases[index].expected.toByteArray(UTF_8)
            val file = binary(capture, index)
            val hex = Path.of(file.toString() + ".hex")
            val expectedHex = expected.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            until("native dd/od capture") { Files.exists(file) && Files.size(file) == expected.size.toLong() &&
                Files.exists(hex) && Files.readString(hex).filterNot { it.isWhitespace() } == expectedHex }
            assertContentEquals(expected, Files.readAllBytes(file))
            assertEquals(expectedHex, Files.readString(hex).filterNot { it.isWhitespace() })
        }
        suspend fun finish(capture: Capture) {
            until("parser drain and extra collector") { snapshot(capture.key).screen.any { it.text.contains(capture.token + "_DRAIN") } &&
                Files.exists(extra(capture)) }
            assertEquals(0L, Files.size(extra(capture)))
            observed += assertNotNull(host.diagnostics(capture.key))
        }
        suspend fun snapshot(key: TerminalKey) = assertIs<TerminalReadResult.Available>(api.readTerminal(key)).snapshot
        suspend fun until(label: String, predicate: suspend () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            withTimeout(10_000) {
                while (!predicate()) {
                    assertTrue(System.nanoTime() < deadline, "Timed out waiting for " + label)
                    delay(5)
                }
            }
        }
        suspend fun assertStopped(owned: JvmPtyTerminalSessions.Diagnostics) {
            until("owned native process, descendants and Thread objects stopped") {
                owned.process?.isAlive != true && owned.descendants.none { it.isAlive } && owned.workers.none { it.isAlive }
            }
            assertFalse(owned.process?.isAlive == true)
            assertTrue(owned.descendants.none { it.isAlive })
            assertTrue(owned.workers.isNotEmpty())
            assertTrue(owned.workers.all { it.state == Thread.State.TERMINATED })
        }
    }

    private class ControlledAgent(request: AgentFactoryRequest) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context: Context = request.context
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String = inbox.insert(message, target, wakeup)
    }

    private suspend fun awaitLatch(latch: CountDownLatch) {
        assertTrue(withContext(Dispatchers.IO) { latch.await(10, TimeUnit.SECONDS) })
    }
    private suspend fun <T> bounded(body: suspend () -> T): T = withTimeout(10_000) { body() }
}

package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.*
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.*
import im.hikaru.harness.tools.ToolsPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JvmSessionTerminalHostTest {
    private val dollar = 36.toChar()

    @Test
    fun discoveryAndInventoryNeverStartShellAndCapacityIsBounded() = runBlocking {
        withFixture(config = { ExecProcessConfig(maxTerminalBindings = 1,
            terminalLimits = JvmPtyTerminalSessions.Limits(capacity = 1)) }) { f ->
            val available = f.discover()
            assertEquals(f.cwd.toRealPath().toString(), available.canonicalCwd)
            assertEquals(1, available.capacity)
            val owner = available.owner
            val missing = TerminalKey(owner, TerminalSessionId("not-created"))
            repeat(3) {
                assertEquals(available, f.discover())
                assertTrue(f.inventory(owner).isEmpty())
                assertIs<TerminalReadResult.NotFound>(f.api.readTerminal(missing))
                assertNull(f.host.diagnostics(missing))
                assertFalse(Files.exists(f.cwd.resolve("started-pids")))
            }
            assertEquals(1, f.host.bindingCount())
            val second = f.runtime.context.agents.create(AgentId("second"), AgentOptions(cwd = f.cwd.toString()))
            assertIs<HostTerminalDiscoveryResult.Rejected>(f.api.terminalCapability(second.agent.session.id))
            val key = f.create(owner, "printf 'CAPACITY_READY\n'; IFS= read -r stop")
            f.untilSnapshot(key) { it.rawTail.contains("CAPACITY_READY") }
            assertIs<HostTerminalCreateResult.Rejected>(f.api.createTerminal(f.request(owner,
                "printf 'FORBIDDEN\n'", pidFile = "forbidden-start")))
            assertFalse(Files.exists(f.cwd.resolve("forbidden-start")))
            assertEquals(1, Files.readAllLines(f.cwd.resolve("started-pids")).size)
            assertEquals(listOf(key), f.inventory(owner).map { it.key })
            assertIs<TerminalMutationResult.Applied>(f.api.closeTerminal(key))
            assertTrue(f.inventory(owner).isEmpty())
            // A live Session binding still owns its registry slot even with no terminal.
            assertIs<HostTerminalDiscoveryResult.Rejected>(f.api.terminalCapability(second.agent.session.id))
            f.handle.dispose()
            val replacement = assertIs<HostTerminalDiscoveryResult.Available>(f.api.terminalCapability(second.agent.session.id))
            assertEquals(owner.scope, replacement.owner.scope)
            assertNotEquals(owner.incarnation, replacement.owner.incarnation)
            assertEquals(1, f.host.bindingCount())
            assertTrue(f.inventory(replacement.owner).isEmpty())
        }
    }

    @Test
    fun onlyExactLiveSessionCanDiscoverAndStart() = runBlocking {
        withFixture(config = { ExecProcessConfig(allowedRoots = listOf(it)) }) { f ->
            val missing = SessionId("not-live")
            assertEquals(missing, assertIs<HostTerminalDiscoveryResult.Rejected>(f.api.terminalCapability(missing)).session)
            val outside = Files.createDirectory(f.temp.resolve("outside"))
            val file = Files.writeString(f.cwd.resolve("not-directory"), "file")
            val link = Files.createSymbolicLink(f.cwd.resolve("escape"), outside)
            val invalidCwds = listOf(null, f.cwd.resolve("missing").toString(), file.toString(),
                outside.toString(), link.toString())
            invalidCwds.forEachIndexed { index, cwd ->
                val handle = f.runtime.context.agents.create(AgentId("invalid-$index"), AgentOptions(cwd = cwd))
                assertEquals(handle.agent.session.id,
                    assertIs<HostTerminalDiscoveryResult.Rejected>(f.api.terminalCapability(handle.agent.session.id)).session)
            }
            assertEquals(0, f.host.bindingCount())
            assertFalse(Files.exists(f.cwd.resolve("started-pids")))
            val owner = f.discover().owner
            val fake = owner.copy(incarnation = TerminalIncarnation("text-does-not-grant-authority"))
            assertIs<HostTerminalCreateResult.Rejected>(f.api.createTerminal(f.request(fake, "printf BAD")))
            for (cwd in listOf(outside.toString(), "escape", "../outside")) {
                assertIs<HostTerminalCreateResult.Rejected>(f.api.createTerminal(f.request(owner,
                    "printf BAD", pidFile = "forbidden-cwd").copy(cwd = cwd)))
            }
            assertTrue(f.inventory(owner).isEmpty())
            assertFalse(Files.exists(f.cwd.resolve("forbidden-cwd")))
            assertFalse(Files.exists(outside.resolve("forbidden-cwd")))
            val key = f.create(owner, "printf 'LIVE_READY\n'; IFS= read -r stop")
            f.untilSnapshot(key) { it.rawTail.contains("LIVE_READY") }
            // Registry membership alone is insufficient after the exact Session leaves its store.
            val attachment = f.runtime.context.sessions.attach(f.handle.agent.session)
            attachment.dispose()
            assertSame(f.handle.agent, f.runtime.context.agents.get(f.handle.agent.id))
            assertIs<HostTerminalDiscoveryResult.Rejected>(f.api.terminalCapability(f.handle.agent.session.id))
            assertIs<TerminalMutationResult.Rejected>(f.api.writeTerminal(key, "BAD\n"))
            assertIs<HostTerminalCreateResult.Rejected>(f.api.createTerminal(f.request(owner, "printf BAD")))
            assertEquals(0, f.host.bindingCount())
            assertEquals(1, Files.readAllLines(f.cwd.resolve("started-pids")).size)
        }
    }

    @Test
    fun sameTextSessionReplacementRejectsOldOwnerAndRetainsCleanup() = runBlocking {
        withFixture(config = { ExecProcessConfig(maxTerminalBindings = 1,
            terminalLimits = JvmPtyTerminalSessions.Limits(shutdownWaitMillis = 250)) }) { f ->
            val owner = f.discover().owner
            val key = f.create(owner, "printf 'REPLACE_READY\n'; IFS= read -r token; " +
                "printf '%s\n' \"" + dollar + "token\" > delivered; IFS= read -r stop")
            f.untilSnapshot(key) { it.rawTail.contains("REPLACE_READY") }
            val owned = assertNotNull(f.host.diagnostics(key))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val interrupted = AtomicInteger()
            f.host.afterNativeWrite = {
                entered.countDown()
                // Deliberately keep the actual IO worker alive through cancellation and cleanup.
                // Interrupts are recorded, not silently treated as a successful release.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (release.count > 0) {
                    val remaining = deadline - System.nanoTime()
                    check(remaining > 0) { "Test native-write gate exceeded its deadline" }
                    try { release.await(remaining, TimeUnit.NANOSECONDS) }
                    catch (_: InterruptedException) { interrupted.incrementAndGet() }
                }
            }
            coroutineScope {
                val writer = async(Dispatchers.Default) { f.api.writeTerminal(key, "ONCE\n") }
                try {
                    awaitLatch(entered)
                    assertIs<TerminalMutationResult.UnknownOutcome>(withTimeout(10_000) { writer.await() })
                    f.until("real input persisted once") { Files.exists(f.cwd.resolve("delivered")) &&
                        Files.readString(f.cwd.resolve("delivered")) == "ONCE\n" }
                    f.handle.dispose()
                    val replacement = f.runtime.context.agents.create(AgentId("owner"), AgentOptions(cwd = f.cwd.toString()))
                    assertEquals(f.handle.agent.session.id, replacement.agent.session.id)
                    assertNotSame(f.handle.agent.session, replacement.agent.session)
                    assertIs<HostTerminalDiscoveryResult.Rejected>(f.api.terminalCapability(replacement.agent.session.id))
                    assertEquals(1, f.host.bindingCount(), "Unconfirmed cleanup must retain registry capacity")
                    assertNotNull(f.host.diagnostics(key), "Retired owned resources must remain tracked")
                    assertTrue(owned.workers.any { it.isAlive })
                    assertTrue(interrupted.get() > 0, "Native worker must have resisted a real cancel/cleanup interrupt")
                    assertIs<TerminalMutationResult.Rejected>(f.api.writeTerminal(key, "BAD\n"))
                    assertIs<TerminalReadResult.Rejected>(f.api.readTerminal(key))
                    assertIs<HostTerminalCreateResult.Rejected>(f.api.createTerminal(f.request(owner, "printf BAD")))
                    assertEquals(1, Files.readAllLines(f.cwd.resolve("started-pids")).size)
                    release.countDown()
                    f.host.afterNativeWrite = null
                    var fresh: HostTerminalDiscoveryResult.Available? = null
                    f.until("retired native cleanup confirmed before replacement discovery") {
                        fresh = f.api.terminalCapability(replacement.agent.session.id) as? HostTerminalDiscoveryResult.Available
                        fresh != null
                    }
                    assertStopped(owned)
                    val newOwner = assertNotNull(fresh).owner
                    assertEquals(owner.scope, newOwner.scope)
                    assertEquals(owner.session, newOwner.session)
                    assertNotEquals(owner.incarnation, newOwner.incarnation)
                    assertTrue(f.inventory(newOwner).isEmpty())
                    val newKey = f.create(newOwner, "printf 'NEW_OWNER_READY\n'; IFS= read -r stop")
                    f.untilSnapshot(newKey) { it.rawTail.contains("NEW_OWNER_READY") }
                    assertEquals(2, Files.readAllLines(f.cwd.resolve("started-pids")).size)
                    assertEquals("ONCE\n", Files.readString(f.cwd.resolve("delivered")))
                    assertIs<TerminalMutationResult.Applied>(f.api.closeTerminal(newKey))
                } finally {
                    release.countDown()
                    f.host.afterNativeWrite = null
                    withContext(NonCancellable) { withTimeout(10_000) { writer.join() } }
                }
            }
        }
    }

    @Test
    fun foreignRootAndStaleIncarnationCannotMutate() = runBlocking {
        withFixture { f ->
            val owner = f.discover().owner
            val key = f.create(owner, "printf 'IDENTITY_READY\n'; while IFS= read -r token; do " +
                "printf '%s\n' \"" + dollar + "token\" >> delivered; printf 'INPUT_SEEN\n'; done")
            val before = f.untilSnapshot(key) { it.rawTail.contains("IDENTITY_READY") }
            withFixture { other ->
                val foreign = other.discover().owner
                assertEquals(owner.session, foreign.session)
                assertNotEquals(owner.scope, foreign.scope)
                val untrusted = listOf(foreign, owner.copy(incarnation = TerminalIncarnation("stale")),
                    owner.copy(session = SessionId("foreign-session")))
                for (stale in untrusted) {
                    val requested = key.copy(owner = stale)
                    assertEquals(stale, assertIs<HostTerminalCreateResult.Rejected>(f.api.createTerminal(
                        f.request(stale, "printf BAD", pidFile = "forbidden-owner"))).owner)
                    assertEquals(stale, assertIs<TerminalListResult.Rejected>(f.api.terminals(stale)).owner)
                    assertEquals(requested, assertIs<TerminalReadResult.Rejected>(f.api.readTerminal(requested)).key)
                    assertEquals(requested, assertIs<TerminalMutationResult.Rejected>(f.api.writeTerminal(requested, "BAD\n")).key)
                    assertEquals(requested, assertIs<TerminalMutationResult.Rejected>(f.api.resizeTerminal(requested, TerminalSize(30, 100))).key)
                    assertEquals(requested, assertIs<TerminalMutationResult.Rejected>(f.api.closeTerminal(requested)).key)
                }
                val shadow = f.runtime.context.isolate(SessionKey)
                try {
                    shadow.provide(SessionKey, other.runtime.context.sessions)
                    val shadowHost = JvmSessionTerminalHost(shadow)
                    try { assertIs<HostTerminalDiscoveryResult.Rejected>(shadowHost.discover(owner.session)) }
                    finally { shadowHost.dispose() }
                } finally { shadow.dispose() }
                // A real registered Agent with the right id/Session but a foreign Context cannot discover.
                withFixture(agentFactory = { ControlledAgent(it, other.runtime.context) }) { wrongRoot ->
                    assertSame(wrongRoot.handle.agent, wrongRoot.runtime.context.agents.get(wrongRoot.handle.agent.id))
                    assertIs<HostTerminalDiscoveryResult.Rejected>(wrongRoot.api.terminalCapability(wrongRoot.handle.agent.session.id))
                    assertEquals(0, wrongRoot.host.bindingCount())
                    assertFalse(Files.exists(wrongRoot.cwd.resolve("started-pids")))
                }
            }
            assertTrue(assertNotNull(f.host.diagnostics(key)?.process).isAlive)
            assertEquals(before.size, f.snapshot(key).size)
            assertEquals(before.rawTail, f.snapshot(key).rawTail)
            assertFalse(Files.exists(f.cwd.resolve("forbidden-owner")))
            assertFalse(Files.exists(f.cwd.resolve("delivered")))
            val absent = key.copy(terminal = TerminalSessionId("absent"))
            assertIs<TerminalMutationResult.NotFound>(f.api.writeTerminal(absent, "BAD\n"))
            assertIs<TerminalMutationResult.Applied>(f.api.writeTerminal(key, "VALID\n"))
            f.untilSnapshot(key) { it.rawTail.contains("INPUT_SEEN") }
            assertEquals("VALID\n", Files.readString(f.cwd.resolve("delivered")))
            assertEquals(listOf(key), f.inventory(owner).map { it.key })
        }
    }

    @Test
    fun realOwnerChainCreateWriteResizeReadClose() = runBlocking {
        withFixture { f ->
            val capability = f.discover()
            val key = f.create(capability.owner, "test -t 0 && test -t 1 && test -t 2 || exit 71; " +
                "printf 'TTY_OK\n'; stty size; printf '\\033[31mRED\\033[0m\nSIZE_READY\n'; " +
                "IFS= read -r message; printf 'RECEIVED:%s\n' \"" + dollar + "message\"; " +
                "stty size; printf 'RESIZED_READY\n'; IFS= read -r stop")
            val first = f.untilSnapshot(key) { it.rawTail.contains("SIZE_READY") && it.screen.any { line -> line.text.contains("SIZE_READY") } }
            assertEquals(capability.owner, key.owner)
            assertEquals(f.handle.agent.session.id, key.owner.session)
            assertEquals(capability.canonicalCwd, first.metadata.canonicalCwd)
            assertTrue(first.rawTail.contains("TTY_OK"))
            assertTrue(first.rawTail.contains("24 80"), first.rawTail)
            assertEquals(TerminalSize(), first.size)
            assertTrue(first.screen.flatMap { it.runs }.any {
                it.text.contains("RED") && it.style.foreground == TerminalColorValue.Indexed(1)
            })
            val saved = Json.encodeToString(first)
            val size = TerminalSize(30, 100)
            assertEquals(size, assertIs<TerminalMutationResult.Applied>(f.api.resizeTerminal(key, size)).size)
            val input = "π你好🙂\n"
            assertEquals(input.toByteArray(Charsets.UTF_8).size,
                assertIs<TerminalMutationResult.Applied>(f.api.writeTerminal(key, input)).bytes)
            val last = f.untilSnapshot(key) { it.rawTail.contains("RESIZED_READY") && it.screen.any { line -> line.text.contains("RESIZED_READY") } }
            assertTrue(last.rawTail.contains("RECEIVED:π你好🙂"), last.rawTail)
            assertFalse(last.rawTail.contains('\uFFFD'))
            assertTrue(last.rawTail.contains("30 100"), last.rawTail)
            assertEquals(size, last.size)
            assertEquals(30, last.screen.size)
            assertTrue(last.revision > first.revision)
            assertEquals(saved, Json.encodeToString(first), "Published nested snapshots must remain detached")
            val owned = assertNotNull(f.host.diagnostics(key))
            val nativePid = Files.readAllLines(f.cwd.resolve("started-pids")).single().toLong()
            val process = assertNotNull(owned.process)
            assertEquals(nativePid, process.pid())
            assertTrue(process.isAlive)
            assertIs<TerminalMutationResult.Applied>(f.api.closeTerminal(key))
            assertStopped(owned)
            assertIs<TerminalReadResult.NotFound>(f.api.readTerminal(key))
            assertTrue(f.inventory(capability.owner).isEmpty())
        }
    }

    @Test
    fun cancelBeforeDispatchStartsNothingAndAfterDispatchIsUnknown() = runBlocking {
        withFixture { f ->
            val owner = f.discover().owner
            val executor = Executors.newSingleThreadExecutor { Thread(it, "harness-host-test-caller") }
            val dispatcher = executor.asCoroutineDispatcher()
            val callerThread = AtomicReference<Thread>()
            try {
                val before = CompletableDeferred<HostTerminalCreateResult>()
                val cancelled = launch(dispatcher) {
                    coroutineContext[Job]!!.cancel()
                    before.complete(f.api.createTerminal(f.request(owner, "printf BAD", pidFile = "cancelled-start")))
                }
                assertEquals(owner, assertIs<HostTerminalCreateResult.Rejected>(withTimeout(10_000) { before.await() }).owner)
                cancelled.join()
                assertTrue(f.inventory(owner).isEmpty())
                assertFalse(Files.exists(f.cwd.resolve("cancelled-start")))
                val key = f.create(owner, "printf 'CANCEL_READY\n'; while IFS= read -r token; do " +
                    "printf '%s\n' \"" + dollar + "token\" >> delivered; printf 'DELIVERED\n'; done")
                f.untilSnapshot(key) { it.rawTail.contains("CANCEL_READY") }
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val writes = AtomicInteger()
                f.host.afterNativeWrite = {
                    writes.incrementAndGet()
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Native write gate was not released" }
                }
                val acknowledgement = CompletableDeferred<TerminalMutationResult>()
                val writer = launch(dispatcher) {
                    callerThread.set(Thread.currentThread())
                    try { acknowledgement.complete(f.api.writeTerminal(key, "ONCE-π🙂\n")) }
                    catch (failure: Throwable) { acknowledgement.completeExceptionally(failure) }
                }
                try {
                    awaitLatch(entered)
                    assertSame(callerThread.get(), withTimeout(10_000) { withContext(dispatcher) { Thread.currentThread() } },
                        "Nested IO must leave the actual caller dispatcher responsive")
                    assertFalse(acknowledgement.isCompleted)
                    writer.cancel()
                    release.countDown()
                    val unknown = assertIs<TerminalMutationResult.UnknownOutcome>(withTimeout(10_000) { acknowledgement.await() })
                    assertEquals(key, unknown.key)
                    withTimeout(10_000) { writer.join() }
                    f.untilSnapshot(key) { it.rawTail.contains("DELIVERED") }
                    assertEquals("ONCE-π🙂\n", Files.readString(f.cwd.resolve("delivered")))
                    assertEquals(1, writes.get(), "No automatic native write retry")
                    assertEquals(1, Files.readAllLines(f.cwd.resolve("started-pids")).size)
                    assertIs<TerminalMutationResult.Applied>(f.api.closeTerminal(key))
                } finally {
                    release.countDown()
                    f.host.afterNativeWrite = null
                    writer.cancel()
                    withContext(NonCancellable) { withTimeout(10_000) { writer.join() } }
                }
            } finally {
                dispatcher.close()
                assertTrue(withContext(Dispatchers.IO) { executor.awaitTermination(10, TimeUnit.SECONDS) })
                assertFalse(callerThread.get()?.isAlive == true)
            }
        }
    }

    @Test
    fun rootDisposeStopsOwnedProcessesAndWorkersAndFencesNewStarts() = runBlocking {
        withFixture { f ->
            val owner = f.discover().owner
            val script = "mkfifo owned-fifo; /bin/cat owned-fifo >/dev/null & " +
                "printf '%s\n' \"" + dollar + "!\" > child-pid; printf 'OWNED_READY\n'; IFS= read -r stop"
            val first = f.create(owner, script)
            val secondRoot = Files.createDirectory(f.cwd.resolve("second"))
            val secondAgent = f.runtime.context.agents.create(AgentId("second"), AgentOptions(cwd = secondRoot.toString()))
            val secondOwner = assertIs<HostTerminalDiscoveryResult.Available>(f.api.terminalCapability(secondAgent.agent.session.id)).owner
            val second = f.create(secondOwner, script)
            f.untilSnapshot(first) { it.rawTail.contains("OWNED_READY") }
            f.untilSnapshot(second) { it.rawTail.contains("OWNED_READY") }
            val roots = listOf(f.cwd, secondRoot)
            val keys = listOf(first, second)
            val owned = keys.zip(roots).map { (key, cwd) ->
                val pid = Files.readString(cwd.resolve("child-pid")).trim().toLong()
                f.until("Host tracks actual owned descendant") { f.host.diagnostics(key)?.descendants?.any { it.pid() == pid } == true }
                val diagnostics = assertNotNull(f.host.diagnostics(key))
                val child = ProcessHandle.of(pid).orElseThrow()
                assertTrue(child.isAlive)
                assertTrue(assertNotNull(diagnostics.process).isAlive)
                diagnostics to child
            }
            assertEquals(2, f.host.bindingCount())
            f.runtime.context.dispose()
            assertTrue(f.runtime.context.isDisposed)
            assertEquals(0, f.host.bindingCount())
            owned.forEach { (diagnostics, child) -> assertStopped(diagnostics); assertFalse(child.isAlive) }
            // The retained real Host is fenced even after its published service is removed.
            assertIs<HostTerminalDiscoveryResult.Unavailable>(f.host.discover(owner.session))
            assertIs<HostTerminalCreateResult.Unavailable>(f.host.create(f.request(owner,
                "printf BAD", pidFile = "after-dispose")))
            assertIs<TerminalListResult.Unavailable>(f.host.list(owner))
            assertIs<TerminalReadResult.Unavailable>(f.host.read(first))
            assertIs<TerminalMutationResult.Unavailable>(f.host.write(first, "BAD\n"))
            assertIs<TerminalMutationResult.Unavailable>(f.host.resize(first, TerminalSize(30, 100)))
            assertIs<TerminalMutationResult.Unavailable>(f.host.close(first))
            assertFalse(Files.exists(f.cwd.resolve("after-dispose")))
            f.host.dispose()
            assertEquals(0, f.host.bindingCount())
        }
    }

    @Test
    fun naturalExitRetainsSnapshotUntilExplicitClose() = runBlocking {
        withFixture { f ->
            val owner = f.discover().owner
            val key = f.create(owner, "printf 'EXIT_READY\n'; IFS= read -r gate; printf 'FINAL-你好🙂\n'; exit 7")
            f.untilSnapshot(key) { it.rawTail.contains("EXIT_READY") }
            val owned = assertNotNull(f.host.diagnostics(key))
            assertIs<TerminalMutationResult.Applied>(f.api.writeTerminal(key, "finish\n"))
            val final = f.untilSnapshot(key) { it.metadata.lifecycle == TerminalLifecycle.Exited(7) && it.rawTail.contains("FINAL-你好🙂") }
            assertEquals(TerminalInputState.Closed, final.metadata.input)
            f.until("natural native process exit") { owned.process?.isAlive != true }
            assertEquals(listOf(key), f.inventory(owner).map { it.key })
            val saved = Json.encodeToString(final)
            repeat(3) { assertEquals(saved, Json.encodeToString(f.snapshot(key))) }
            assertIs<TerminalMutationResult.Rejected>(f.api.writeTerminal(key, "BAD\n"))
            assertEquals(1, Files.readAllLines(f.cwd.resolve("started-pids")).size)
            assertIs<TerminalMutationResult.Applied>(f.api.closeTerminal(key))
            assertStopped(owned)
            assertIs<TerminalReadResult.NotFound>(f.api.readTerminal(key))
            assertTrue(f.inventory(owner).isEmpty())
        }
    }

    private suspend fun withFixture(
        config: (Path) -> ExecProcessConfig = { ExecProcessConfig() },
        agentFactory: (AgentFactoryRequest) -> Agent = { ControlledAgent(it) },
        body: suspend (Fixture) -> Unit,
    ) {
        val temp = Files.createTempDirectory("harness-terminal-host-test-").toRealPath()
        val cwd = Files.createDirectory(temp.resolve("session"))
        val runtime = Runtime()
        var fixture: Fixture? = null
        try {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            runtime.context.agents.registerFactory(AgentFactory { agentFactory(it) })
            runtime.install(ExecProcessPlugin(config(cwd)))
            runtime.install(SessionApiPlugin())
            val handle = runtime.context.agents.create(AgentId("owner"), AgentOptions(cwd = cwd.toString()))
            val created = Fixture(runtime, temp, cwd, handle)
            fixture = created
            assertSame(runtime.context.require(SessionTerminalHostKey), created.host)
            body(created)
        } finally {
            try {
                withContext(NonCancellable) { runtime.context.dispose() }
                fixture?.let { f ->
                    assertEquals(0, f.host.bindingCount(), "Root cleanup must release every confirmed binding")
                    f.observed.forEach { assertStopped(it) }
                }
            } finally {
                // Delete only this test's own tree; Files.walk does not follow symlinks.
                Files.walk(temp).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val temp: Path, val cwd: Path, val handle: AgentHandle) {
        val api = runtime.context.sessionApi
        val host = runtime.context.require(SessionTerminalHostKey) as JvmSessionTerminalHost
        val observed = mutableListOf<JvmPtyTerminalSessions.Diagnostics>()
        private val home = Files.createDirectory(cwd.resolve("home"))
        private val dollar = 36.toChar()
        suspend fun discover() = assertIs<HostTerminalDiscoveryResult.Available>(api.terminalCapability(handle.agent.session.id))
        fun request(owner: TerminalOwner, script: String, pidFile: String = "started-pids") = TerminalCreateRequest(owner,
            listOf("/bin/sh", "-c", "stty -echo; printf '%s\n' \"" + dollar + dollar + "\" >> " + pidFile + "; " + script),
            mapOf("HOME" to home.toString(), "PATH" to "/usr/bin:/bin", "TERM" to "xterm-256color", "LC_ALL" to "C"))
        suspend fun create(owner: TerminalOwner, script: String): TerminalKey {
            val result = assertIs<HostTerminalCreateResult.Created>(api.createTerminal(request(owner, script)))
            assertEquals(owner, result.owner)
            assertEquals(owner, result.metadata.key.owner)
            val key = result.metadata.key
            observed += assertNotNull(host.diagnostics(key))
            return key
        }
        suspend fun inventory(owner: TerminalOwner) = assertIs<TerminalListResult.Available>(api.terminals(owner)).terminals
        suspend fun snapshot(key: TerminalKey) = assertIs<TerminalReadResult.Available>(api.readTerminal(key)).snapshot
        suspend fun until(label: String, predicate: suspend () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!predicate()) {
                assertTrue(System.nanoTime() < deadline, "Deadline waiting for " + label)
                delay(5)
            }
        }
        suspend fun untilSnapshot(key: TerminalKey, predicate: (TerminalSnapshot) -> Boolean): TerminalSnapshot {
            var latest = snapshot(key)
            until("actual PTY snapshot; latest=" + latest.rawTail) { latest = snapshot(key); predicate(latest) }
            return latest
        }
    }

    private class ControlledAgent(request: AgentFactoryRequest, override val context: Context = request.context) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String = inbox.insert(message, target, wakeup)
    }

    private suspend fun awaitLatch(latch: CountDownLatch) {
        assertTrue(withContext(Dispatchers.IO) { latch.await(10, TimeUnit.SECONDS) }, "Actual native operation did not enter its gate")
    }

    private fun assertStopped(owned: JvmPtyTerminalSessions.Diagnostics) {
        assertFalse(owned.process?.isAlive == true, "Owned native process must stop")
        assertTrue(owned.descendants.none { it.isAlive }, "Owned descendants must stop")
        assertTrue(owned.workers.none { it.isAlive }, owned.workers.joinToString { it.name + ":" + it.state })
    }
}

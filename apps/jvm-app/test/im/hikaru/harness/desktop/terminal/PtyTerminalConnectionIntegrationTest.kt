package im.hikaru.harness.desktop.terminal

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.agent.*
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostApiPlugin
import im.hikaru.harness.api.gateway.host.registerHostApi
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.exec.jvm.ExecProcessConfig
import im.hikaru.harness.exec.jvm.ExecProcessPlugin
import im.hikaru.harness.exec.jvm.JvmPtyTerminalSessions
import im.hikaru.harness.exec.jvm.JvmSessionTerminalHost
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.session.api.SessionTerminalHostKey
import im.hikaru.harness.session.api.SessionApi as CoreApi
import im.hikaru.harness.session.api.TerminalKey as CoreKey
import im.hikaru.harness.session.api.TerminalInput as CoreInput
import im.hikaru.harness.session.api.TerminalMutationResult as CoreMutation
import im.hikaru.harness.session.api.TerminalCreateRequest as CoreCreateRequest
import im.hikaru.harness.session.api.HostTerminalCreateResult as CoreCreateResult
import im.hikaru.harness.session.api.TerminalReadResult as CoreRead
import im.hikaru.harness.session.api.TerminalSize as CoreSize
import im.hikaru.harness.agent.AgentOptions as CoreAgentOptions
import im.hikaru.harness.llm.Message as CoreMessage
import im.hikaru.harness.tools.ToolsPlugin
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** These fixtures use the production registrar, DefaultSessionApi, Host and native PTY. */
class PtyTerminalConnectionIntegrationTest {
    @Test fun controllerCreateTextPasteAndKeysReachExactRealPty() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val phases = listOf(
                TerminalInput.Text("A中😀") to "A中😀",
                TerminalInput.Paste("B中\n") to "\u001b[200~B中\r\u001b[201~",
                TerminalInput.KeyPress(TerminalKeyName.Up) to "\u001b[A",
                TerminalInput.KeyPress(TerminalKeyName.Character, setOf(TerminalModifier.Control), "c") to "\u0003")
            val script = f.captureScript(phases.map { it.second })
            val controller = f.controller(this, script)
            try {
                f.awaitReady(controller)
                assertEquals(0, f.creates.get())
                assertTrue(controller.createTerminal())
                assertFalse(controller.createTerminal())
                f.awaitScreen(controller, "READY_0")
                for ((index, phase) in phases.withIndex()) {
                    f.awaitScreen(controller, "READY_$index")
                    assertTrue(controller.input(phase.first))
                    f.expectBytes(index, phase.second)
                }
                f.awaitScreen(controller, "DRAIN")
                assertEquals(1, f.creates.get())
                assertEquals(phases.size, f.nativeWrites.get())
                assertEquals(phases.map { it.first }, f.inputs.toList())
                assertEquals(0L, Files.size(f.cwd.resolve("extra.bin")))
                assertTrue(f.keys.all { it.owner.session == f.session })
            } finally { controller.close() }
        }
    }

    @Test fun typedMappedScreenHistoryAndKernelResizeReachPageModel() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val controller = f.controller(this, f.pidPrefix() +
                "stty raw -echo; printf '\\033[31mA中x😀\\033[0m\\r\\n'; i=0; while [ \"${'$'}i\" -lt 30 ]; do printf 'history-%02d\\r\\n' \"${'$'}i\"; i=${'$'}((i+1)); done; printf 'READY_SIZE'; dd bs=1 count=1 of=size-trigger.bin 2>/dev/null; stty size > kernel-size; printf '\\r\\nSIZE_DONE'; dd bs=1 of=extra.bin 2>/dev/null")
            try {
                f.awaitReady(controller); assertTrue(controller.createTerminal())
                f.awaitScreen(controller, "READY_SIZE")
                val first = controller.state.value.snapshot!!
                assertTrue(first.history.any { it.text.contains("A中x😀") })
                val mapped = first.history.flatMap { it.runs }.first { it.text.startsWith("A中x😀") }
                assertEquals(listOf(0, 1, 3, 4, 5, 6), mapped.columnOffsets!!.take(6))
                assertIs<TerminalColorValue.Indexed>(mapped.style.foreground)
                val size = TerminalSize(18, 57)
                assertTrue(controller.viewport(size))
                f.until { controller.state.value.confirmedSizes[controller.state.value.selectedKey] == size }
                assertTrue(controller.input(TerminalInput.Text("r")))
                f.until { Files.exists(f.cwd.resolve("kernel-size")) && Files.readString(f.cwd.resolve("kernel-size")).trim() == "18 57" }
                f.awaitScreen(controller, "SIZE_DONE")
                assertEquals(size, controller.state.value.snapshot!!.size)
                assertNull(PtyGridModel.validate(controller.state.value.snapshot!!))
                assertFalse(controller.viewport(size))
            } finally { controller.close() }
        }
    }

    @Test fun hiddenViewReadAndExitRetentionNeverSpawnOrKill() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val controller = f.controller(this, f.pidPrefix() +
                "stty raw -echo; printf 'RUNNING'; dd bs=1 count=1 of=stop.bin 2>/dev/null; printf '\\r\\nFINAL_SCREEN'; exit 7")
            try {
                f.awaitReady(controller); assertTrue(controller.createTerminal()); f.awaitScreen(controller, "RUNNING")
                f.recordOwned(); val process = f.ownedProcesses().single(); assertTrue(process.isAlive)
                controller.setVisible(false); delay(100); val reads = f.reads.get(); delay(100)
                assertEquals(reads, f.reads.get()); assertTrue(process.isAlive); assertEquals(0, f.closes.get())
                controller.setVisible(true); f.awaitScreen(controller, "RUNNING")
                assertEquals(1, f.creates.get())
                assertTrue(controller.input(TerminalInput.Text("q")))
                f.until { controller.state.value.snapshot?.metadata?.lifecycle == TerminalLifecycle.Exited(7) }
                f.awaitScreen(controller, "FINAL_SCREEN")
                delay(100); val exitReads = f.reads.get(); delay(100); assertEquals(exitReads, f.reads.get())
                assertEquals(0, f.closes.get()); assertFalse(process.isAlive)
                assertTrue(controller.closeTerminal()); f.until { f.closes.get() == 1 }
            } finally { controller.close() }
        }
    }

    @Test fun lostAcknowledgementAndOwnerReplacementNeverReplayNativeInput() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val delivered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.afterInput = { key, applied ->
                assertIs<CoreMutation.Applied>(applied); delivered.complete(Unit); release.await()
                CoreMutation.UnknownOutcome(key, "Synthetic lost acknowledgement after native delivery")
            }
            val controller = f.controller(this, f.captureScript(listOf("once")))
            try {
                f.awaitReady(controller); assertTrue(controller.createTerminal()); f.awaitScreen(controller, "READY_0")
                assertTrue(controller.input(TerminalInput.Text("once")))
                withTimeout(10_000) { delivered.await() }; f.expectBytes(0, "once")
                assertTrue(controller.input(TerminalInput.Text("never")))
                release.complete(Unit)
                f.until { controller.state.value.selectedKey in controller.state.value.fencedKeys }
                controller.refresh(); f.until { controller.state.value.inventory is PtyLoad.Ready }
                controller.setVisible(false); controller.setVisible(true)
                assertFalse(controller.input(TerminalInput.Text("replay")))
                assertFalse(controller.viewport(TerminalSize(22, 60)))
                assertEquals(1, f.nativeWrites.get()); assertEquals(1, f.inputs.size)
                assertTrue(controller.state.value.receipts.any { it.outcome == PtyOutcome.Unsent })
                controller.bind(f.owner.copy(host = ConversationHostScope(f.host.hostId, 2, null)))
                assertNull(controller.state.value.selectedKey)
                f.until { controller.state.value.inventory is PtyLoad.Ready }; assertEquals(1, f.creates.get())
                f.until { Files.exists(f.cwd.resolve("extra.bin")) }
                assertEquals(0L, Files.size(f.cwd.resolve("extra.bin")))
            } finally { release.complete(Unit); controller.close() }
        }
        RealPtyPageFixture.use { f ->
            val delivered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val returned = CompletableDeferred<Unit>()
            f.afterInput = { key, applied ->
                assertIs<CoreMutation.Applied>(applied); delivered.complete(Unit); release.await()
                returned.complete(Unit)
                CoreMutation.UnknownOutcome(key, "Late acknowledgement belongs to the old owner")
            }
            val controller = f.controller(this, f.captureScript(listOf("late")))
            try {
                f.awaitReady(controller); assertTrue(controller.createTerminal()); f.awaitScreen(controller, "READY_0")
                assertTrue(controller.input(TerminalInput.Text("late")))
                withTimeout(10_000) { delivered.await() }; f.expectBytes(0, "late")
                assertTrue(controller.input(TerminalInput.Text("discarded-on-rebind")))
                val oldKey = assertNotNull(controller.state.value.selectedKey)
                val replacement = f.owner.copy(host = ConversationHostScope(f.host.hostId, 2, null))
                controller.bind(replacement); controller.setVisible(true); f.awaitReady(controller)
                assertTrue(controller.isBoundTo(replacement)); assertNull(controller.state.value.selectedKey)
                release.complete(Unit); withTimeout(10_000) { returned.await() }; yield()
                assertNull(controller.state.value.selectedKey)
                assertTrue(controller.state.value.fencedKeys.isEmpty())
                assertTrue(controller.state.value.receipts.none { it.outcome == PtyOutcome.Unknown })
                controller.select(oldKey); f.awaitScreen(controller, "DRAIN")
                assertEquals(1, f.nativeWrites.get()); assertEquals(1, f.inputs.size)
                assertEquals(1, f.creates.get()); assertEquals(0L, Files.size(f.cwd.resolve("extra.bin")))
            } finally { release.complete(Unit); controller.close() }
        }
    }
}

/** Shared test-only fixture; production code receives neither scripts nor controlled data. */
internal class RealPtyPageFixture private constructor(
    val runtime: Runtime, val cwd: Path, val host: HostDescription,
) {
    lateinit var connection: LocalConnection
    private var initializedSession: SessionId? = null
    val session: SessionId get() = checkNotNull(initializedSession) { "PTY page fixture session is not initialized" }
    val owner get() = ThreadPanelsOwner(connection, ConversationHostScope(host.hostId, 1, null), session)
    val creates = AtomicInteger(); val reads = AtomicInteger(); val closes = AtomicInteger(); val resizes = AtomicInteger()
    val nativeWrites = AtomicInteger()
    val inputs = CopyOnWriteArrayList<TerminalInput>()
    val keys = CopyOnWriteArrayList<TerminalKey>()
    var afterInput: (suspend (CoreKey, CoreMutation) -> CoreMutation)? = null
    var unavailable = false
    private val processes = linkedSetOf<ProcessHandle>()
    private val descendants = linkedSetOf<ProcessHandle>()
    private val threads = linkedSetOf<Thread>()
    private val controllers = mutableListOf<PtyTerminalController>()
    val shellPrompt = "PTY_PAGE_SHELL_READY"
    val desktopProvider by lazy { DesktopTerminalLaunchProvider(host.hostId, connection, readEnvironment = {
        mapOf("SHELL" to "/bin/sh", "HOME" to cwd.resolve("home").toString(), "PATH" to "/usr/bin:/bin", "LC_ALL" to "C",
            "PS1" to shellPrompt)
    }) }

    fun controller(scope: CoroutineScope, script: String): PtyTerminalController {
        val provider = object : TerminalLaunchProvider {
            override fun availability(owner: ThreadPanelsOwner): String? = null
            override fun prepare(owner: ThreadPanelsOwner, discovery: TerminalDiscoveryResult.Available) =
                TerminalLaunchPreparation.Ready(TerminalLaunchPlan(discovery.owner, listOf("/bin/sh", "-c", script),
                    mapOf("HOME" to cwd.resolve("home").toString(), "PATH" to "/usr/bin:/bin", "TERM" to "xterm-256color", "LC_ALL" to "C"), discovery.canonicalCreationCwd))
        }
        return PtyTerminalController(scope, provider, 20).also { controllers += it; it.bind(owner); it.setVisible(true) }
    }
    suspend fun awaitReady(controller: PtyTerminalController) { until { controller.state.value.discovery is PtyLoad.Ready && controller.state.value.inventory !is PtyLoad.Loading } }
    suspend fun awaitScreen(controller: PtyTerminalController, marker: String) {
        until { controller.state.value.screen is PtyLoad.Ready && controller.state.value.snapshot?.screen?.any { it.text.trimEnd() == marker } == true }
    }
    fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    fun pidPrefix() = "test -t 0 && test -t 1 && test -t 2 || exit 70; printf '%s\\n' \"${'$'}${'$'}\" > owned.pid; "
    fun captureScript(expected: List<String>, captureResize: Boolean = false): String = buildString {
        append(pidPrefix()); append("stty raw -echo; printf '\\033[?2004h'; ")
        for ((index, text) in expected.withIndex()) {
            append("printf '\\033[2J\\033[HREADY_$index\\r\\n'; dd bs=1 count=${text.encodeToByteArray().size} of=bytes-$index.bin 2>/dev/null; ")
            append("od -An -v -tx1 bytes-$index.bin > bytes-$index.hex; ")
        }
        append(": > extra.bin; printf '\\033[2J\\033[HDRAIN\\r\\n'; ")
        if (captureResize) {
            append("dd bs=1 count=1 of=size-trigger.bin 2>/dev/null; od -An -v -tx1 size-trigger.bin > size-trigger.hex; ")
            append("stty size > kernel-size; printf 'SIZE_DONE\\r\\n'; ")
        }
        append("dd bs=1 of=extra.bin 2>/dev/null")
    }
    suspend fun expectBytes(index: Int, expected: String) {
        val bytes = expected.encodeToByteArray()
        val hex = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        until { Files.exists(cwd.resolve("bytes-$index.bin")) && Files.size(cwd.resolve("bytes-$index.bin")) == bytes.size.toLong() &&
            Files.exists(cwd.resolve("bytes-$index.hex")) && Files.readString(cwd.resolve("bytes-$index.hex")).filterNot { it.isWhitespace() } == hex }
        assertContentEquals(bytes, Files.readAllBytes(cwd.resolve("bytes-$index.bin")))
        assertEquals(hex, Files.readString(cwd.resolve("bytes-$index.hex")).filterNot { it.isWhitespace() })
    }
    suspend fun snapshot(key: TerminalKey) = assertIs<TerminalReadResult.Available>(connection.session.readTerminal(key)).snapshot
    suspend fun until(predicate: suspend () -> Boolean) = withTimeout(10_000) { while (!predicate()) delay(5) }
    fun workers(): Set<Thread> = Thread.getAllStackTraces().keys.filter { thread ->
        keys.any { thread.name.startsWith("harness-pty-${it.terminal.value}-") }
    }.toSet()
    fun recordOwned() {
        threads += workers()
        Files.list(cwd).use { files -> files.filter { it.fileName.toString().endsWith(".pid") }.forEach { file ->
            val pid = Files.readString(file).trim().toLongOrNull() ?: return@forEach
            ProcessHandle.of(pid).ifPresent { process ->
                processes += process; process.descendants().use { handles -> handles.forEach { descendants += it } }
            }
        } }
    }
    fun ownedProcesses(): List<ProcessHandle> = processes.toList()
    private suspend fun shutdown() = withContext(NonCancellable) {
        controllers.forEach { it.close() }; recordOwned()
        try { withTimeout(10_000) { runtime.context.dispose() } } finally {
            try {
                until { processes.none { it.isAlive } && descendants.none { it.isAlive } && threads.none { it.isAlive } && workers().none { it.isAlive } }
                assertTrue(processes.none { it.isAlive }); assertTrue(descendants.none { it.isAlive })
                if (keys.isNotEmpty()) assertTrue(threads.isNotEmpty(), "Record actual owned worker objects")
                assertTrue(threads.all { it.state == Thread.State.TERMINATED })
            } finally { Files.walk(cwd).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
        }
    }
    companion object {
        suspend fun use(body: suspend (RealPtyPageFixture) -> Unit) {
            val f = RealPtyPageFixture(Runtime(), Files.createTempDirectory("harness-pty-page-").toRealPath(),
                HostDescription(ProtocolVersion.Current, HostId("pty-page-local"), "PTY page test"))
            try { f.start(); body(f) } finally { f.shutdown() }
        }
    }
    private suspend fun start() {
        Files.createDirectory(cwd.resolve("home"))
        runtime.install(SessionPlugin()); runtime.install(AgentPlugin()); runtime.install(ToolsPlugin())
        runtime.context.agents.registerFactory(AgentFactory { PageAgent(it) })
        runtime.install(ExecProcessPlugin(ExecProcessConfig(terminalLimits = JvmPtyTerminalSessions.Limits(ioWaitMillis = 10_000))))
        runtime.install(SessionApiPlugin()); runtime.install(HostApiPlugin, host)
        val handle = runtime.context.agents.create(AgentId("page-owner"), CoreAgentOptions(cwd = cwd.toString()))
        initializedSession = SessionId(handle.agent.session.id.value)
        val real = runtime.context.require(SessionApiKey)
        val counting = object : CoreApi by real {
            override suspend fun terminalCapability(session: im.hikaru.harness.session.SessionId): im.hikaru.harness.session.api.HostTerminalDiscoveryResult =
                if (unavailable) im.hikaru.harness.session.api.HostTerminalDiscoveryResult.Unavailable(session, "Controlled Unavailable") else real.terminalCapability(session)
            override suspend fun createTerminal(request: CoreCreateRequest): CoreCreateResult {
                creates.incrementAndGet(); val result = real.createTerminal(request)
                if (result is CoreCreateResult.Created) {
                    keys += TerminalKey(TerminalOwner(TerminalScope(result.owner.scope.value), SessionId(result.owner.session.value),
                        TerminalIncarnation(result.owner.incarnation.value)), TerminalSessionId(result.metadata.key.terminal.value))
                    recordOwned()
                }
                return result
            }
            override suspend fun inputTerminal(key: CoreKey, input: CoreInput): CoreMutation {
                inputs += when (input) {
                    is CoreInput.Text -> TerminalInput.Text(input.text)
                    is CoreInput.Paste -> TerminalInput.Paste(input.text)
                    is CoreInput.KeyPress -> TerminalInput.KeyPress(TerminalKeyName.valueOf(input.key.name), input.modifiers.map { TerminalModifier.valueOf(it.name) }.toSet(), input.character)
                }
                val result = real.inputTerminal(key, input)
                return afterInput?.invoke(key, result) ?: result
            }
            override suspend fun readTerminal(key: CoreKey): CoreRead { reads.incrementAndGet(); return real.readTerminal(key) }
            override suspend fun resizeTerminal(key: CoreKey, size: CoreSize): CoreMutation { resizes.incrementAndGet(); return real.resizeTerminal(key, size) }
            override suspend fun closeTerminal(key: CoreKey): CoreMutation { closes.incrementAndGet(); return real.closeTerminal(key) }
        }
        val context = runtime.context.isolate(SessionApiKey).also { it.provide(SessionApiKey, counting) }
        val gateway = ApiGateway(); gateway.registerSessionApi(context); gateway.registerHostApi(runtime.context)
        connection = LocalConnection(gateway)
        val hostService = runtime.context.require(SessionTerminalHostKey) as JvmSessionTerminalHost
        JvmSessionTerminalHost::class.java.getDeclaredField("afterNativeWrite").also { it.isAccessible = true }.set(hostService,
            { _: CoreKey -> nativeWrites.incrementAndGet(); Unit })
    }
    private class PageAgent(request: AgentFactoryRequest) : Agent {
        override val id = request.id; override val options = request.options; override val session = request.session
        override val inbox = request.inbox; override val context: Context = request.context; override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: CoreMessage, target: AgentSendTarget, wakeup: AgentWakeup): String = inbox.insert(message, target, wakeup)
    }
}

package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.agent.*
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.connection.BackgroundOutputResult
import im.hikaru.harness.client.connection.BackgroundProcessId
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundProcessesResult
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.exec.jvm.BASH_TOOL_NAME
import im.hikaru.harness.exec.jvm.ExecCommand
import im.hikaru.harness.exec.jvm.SubprocessKey
import im.hikaru.harness.exec.jvm.SubprocessPlugin
import im.hikaru.harness.exec.jvm.ToolBashPlugin
import im.hikaru.harness.exec.jvm.ToolPwshPlugin
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.BackgroundProcessesKey
import im.hikaru.harness.session.api.BackgroundProcessesService
import im.hikaru.harness.session.api.BackgroundOutputResult as CoreBackgroundOutputResult
import im.hikaru.harness.session.api.BackgroundProcessId as CoreBackgroundProcessId
import im.hikaru.harness.session.api.BackgroundProcessesResult as CoreBackgroundProcessesResult
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.tools.*
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

/** Real local producer -> DefaultSessionApi -> Gateway -> LocalConnection -> panel reads. */
class SessionBackgroundProcessesConnectionTest {
    @Test
    fun successfulEmptyInventoryDiffersFromMissingProducerAndNeverStartsAProcess() = runBlocking {
        withFixture(installProducer = false) { f ->
            val missing = assertIs<BackgroundProcessesResult.Unavailable>(f.connection.session.backgroundProcesses(f.parent))
            assertTrue(missing.reason.isNotBlank())
            assertIs<BackgroundOutputResult.Unavailable>(f.connection.session.readBackgroundOutput(f.parent, BackgroundProcessId("absent")))
            assertIs<PanelLoad.Unavailable>(f.repository.readTerminal(f.owner))
            f.runtime.install(SubprocessPlugin())
            f.runtime.install(ToolBashPlugin())
            f.runtime.install(ToolPwshPlugin())
            repeat(3) {
                val empty = assertIs<BackgroundProcessesResult.Available>(f.connection.session.backgroundProcesses(f.parent))
                assertTrue(empty.processes.isEmpty())
                assertIs<PanelLoad.Empty>(f.repository.readTerminal(f.owner))
            }
            assertEquals(setOf("bash", "pwsh"), f.tools.schemas().map { it.name }.toSet())
        }
    }

    @Test
    fun trustedForegroundExecProjectsActualUtf8CwdAndConfirmedExitWithoutInventingRevisions() = runBlocking {
        withFixture { f ->
            assertFalse(f.execute("utf8", "utf8").isError)
            val process = f.inventory().processes.single()
            assertEquals(f.parent, process.ownerSession)
            assertEquals("utf8", process.callId)
            assertEquals(f.cwd.toString(), process.cwd)
            assertTrue(process.process.value.startsWith(process.producerIncarnation + ":"))
            assertEquals(BackgroundProcessLifecycle.Exited(0), process.lifecycle)
            val output = assertIs<BackgroundOutputResult.Available>(f.connection.session.readBackgroundOutput(f.parent, process.process))
            val panel = assertIs<PanelLoad.Ready<BackgroundTerminalOutput>>(f.repository.readTerminal(f.owner)).value
            assertEquals("你好🙂\n${f.cwd}\nlast\n", output.buffer)
            assertEquals(output.buffer, panel.buffer)
            assertEquals(output.revision, panel.revision)
            assertEquals(output.lifecycle, panel.lifecycle)
            assertEquals(process.command, panel.command)
            assertEquals(process.cwd, panel.cwd)
            assertEquals(process.producerIncarnation, panel.key.producerIncarnation)
            assertEquals(process.process.value, panel.key.processId)
            assertFalse(panel.truncated)
            repeat(3) {
                assertEquals(output, f.connection.session.readBackgroundOutput(f.parent, process.process))
                assertEquals(panel, assertIs<PanelLoad.Ready<BackgroundTerminalOutput>>(f.repository.readTerminal(f.owner)).value)
            }
            assertEquals(1, f.inventory().processes.size, "Panel reads must not spawn or restart commands")
        }
    }

    @Test
    fun backgroundHandleRemainsRunningUntilRealProcessExitAndOutputDrain() = runBlocking {
        withFixture { f ->
            val ready = f.cwd.resolve("ready")
            val release = f.cwd.resolve("release")
            assertFalse(f.execute("held", "wait", ready.toString(), release.toString(), background = true).isError)
            val process = f.inventory().processes.single()
            awaitCondition { readPid(ready) != null && f.panel().buffer == "ready\n" }
            val pid = readPid(ready) ?: error("Process fixture did not publish a numeric PID")
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
            val running = f.panel()
            assertEquals(BackgroundProcessLifecycle.Running, running.lifecycle)
            repeat(3) { assertEquals(running, f.panel()) }
            assertEquals(1, f.inventory().processes.size)
            Files.writeString(release, "release")
            awaitCondition { f.panel().lifecycle is BackgroundProcessLifecycle.Exited }
            val exited = f.panel()
            assertEquals(BackgroundProcessLifecycle.Exited(0), exited.lifecycle)
            assertEquals("ready\ntail\n", exited.buffer)
            assertTrue(exited.revision > running.revision)
            assertEquals(process.process.value, exited.key.processId)
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        }
    }

    @Test
    fun nonzeroExitIsARealToolFailureWithReadableOutputRatherThanSuccessfulEmpty() = runBlocking {
        withFixture { f ->
            assertTrue(f.execute("nonzero", "nonzero").isError)
            val panel = f.panel()
            assertEquals(BackgroundProcessLifecycle.Exited(7), panel.lifecycle)
            assertEquals("stdout\nstderr\n", panel.buffer)
            assertEquals(1, f.inventory().processes.size)
        }
    }

    @Test
    fun foreignParentAndReplacedSessionCannotReadOldOutputOrReuseTrustedExecution() = runBlocking {
        withFixture { f ->
            val oldExecution = f.execution
            val oldRequest = f.request("late-old-owner", "utf8")
            assertFalse(f.execute("owned", "utf8").isError)
            val process = f.inventory().processes.single().process
            val foreign = f.runtime.context.agents.create(AgentId("foreign"), AgentOptions(cwd = f.cwd.toString()))
            assertIs<BackgroundOutputResult.NotFound>(f.connection.session.readBackgroundOutput(ClientSessionId("foreign"), process))
            assertIs<PanelLoad.Empty>(f.repository.readTerminal(f.owner.copy(parentSessionId = ClientSessionId("foreign"))))
            assertFailsWith<IllegalArgumentException> {
                f.tools.execute(oldRequest.copy(scope = "foreign"), oldExecution)
            }
            assertFailsWith<IllegalArgumentException> {
                f.tools.execute(oldRequest, ToolExecutionContext(foreign.agent.context, foreign.agent.session))
            }
            assertEquals(404, assertFailsWith<LocalConnectionException> {
                f.connection.session.backgroundProcesses(ClientSessionId("no-live-owner"))
            }.code)
            assertFailsWith<LocalConnectionException> {
                f.repository.readTerminal(f.owner.copy(parentSessionId = ClientSessionId("no-live-owner")))
            }
            f.handle.dispose()
            assertEquals(404, assertFailsWith<LocalConnectionException> {
                f.connection.session.readBackgroundOutput(f.parent, process)
            }.code)
            f.runtime.context.agents.create(AgentId("owner"), AgentOptions(cwd = f.cwd.toString()))
            assertIs<BackgroundOutputResult.NotFound>(f.connection.session.readBackgroundOutput(f.parent, process))
            assertIs<PanelLoad.Empty>(f.repository.readTerminal(f.owner))
            val rejection = assertFails { f.tools.execute(oldRequest, oldExecution) }
            assertTrue(rejection is IllegalStateException || rejection is IllegalArgumentException,
                "Disposed context or replaced execution identity must reject before spawning")
            assertTrue(f.inventory().processes.isEmpty())
        }
    }

    @Test
    fun missingEndpointAndMissingSessionApiAreUnavailableWithAReason() = runBlocking {
        val gateway = ApiGateway()
        val connection = LocalConnection(gateway)
        val parent = ClientSessionId("parent")
        val process = BackgroundProcessId("process")
        val owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("local"), 1, null), parent)
        val repository = ConnectionThreadPanelsRepository()
        suspend fun assertUnavailable() {
            assertTrue(assertIs<BackgroundProcessesResult.Unavailable>(connection.session.backgroundProcesses(parent)).reason.isNotBlank())
            assertTrue(assertIs<BackgroundOutputResult.Unavailable>(connection.session.readBackgroundOutput(parent, process)).reason.isNotBlank())
            assertIs<PanelLoad.Unavailable>(repository.readTerminal(owner))
        }
        assertUnavailable()
        val context = Context()
        try {
            val registration = gateway.registerSessionApi(context)
            assertUnavailable()
            registration.dispose()
            assertUnavailable()
        } finally { context.dispose() }
    }

    @Test
    fun serviceCancellationAndFailuresPropagateAcrossDefaultApiAndGatewayInsteadOfBecomingEmpty() = runBlocking {
        withFixture(installProducer = false) { f ->
            // Deliberately failing service fixtures exercise the production facade/transport.
            for ((failure, expectedCode) in listOf(
                CancellationException("private cancellation") to null,
                IllegalStateException("private stale owner") to 404,
                IllegalArgumentException("private rejected request") to 422,
                IOException("private failed read") to 500,
            )) {
                val binding = f.runtime.context.provide(BackgroundProcessesKey, object : BackgroundProcessesService {
                    override suspend fun list(parent: SessionId): CoreBackgroundProcessesResult = throw failure
                    override suspend fun read(parent: SessionId, process: CoreBackgroundProcessId): CoreBackgroundOutputResult = throw failure
                })
                try {
                    if (failure is CancellationException) {
                        assertSame(failure, assertFailsWith<CancellationException> { f.connection.session.backgroundProcesses(f.parent) })
                        assertSame(failure, assertFailsWith<CancellationException> {
                            f.connection.session.readBackgroundOutput(f.parent, BackgroundProcessId("process"))
                        })
                        assertSame(failure, assertFailsWith<CancellationException> { f.repository.readTerminal(f.owner) })
                    } else {
                        val failures = listOf(
                            assertFailsWith<LocalConnectionException> { f.connection.session.backgroundProcesses(f.parent) },
                            assertFailsWith<LocalConnectionException> {
                                f.connection.session.readBackgroundOutput(f.parent, BackgroundProcessId("process"))
                            },
                            assertFailsWith<LocalConnectionException> { f.repository.readTerminal(f.owner) },
                        )
                        failures.forEach {
                            assertEquals(expectedCode, it.code)
                            assertFalse(it.message.orEmpty().contains("private"))
                        }
                    }
                } finally { binding.dispose() }
            }
        }
    }

    @Test
    fun cancellingActualForegroundCommandRetainsOwnedEvidenceAndCannotRespawnItsCall() = runBlocking {
        withFixture { f ->
            coroutineScope {
                val ready = f.cwd.resolve("cancel-ready")
                val neverRelease = f.cwd.resolve("never-release")
                val call = async { f.execute("cancel", "wait", ready.toString(), neverRelease.toString()) }
                try {
                    awaitCondition { readPid(ready) != null && f.panel().buffer.contains("ready\n") }
                    val pid = readPid(ready) ?: error("Process fixture did not publish a numeric PID")
                    call.cancelAndJoin()
                    awaitCondition { !ProcessHandle.of(pid).map { it.isAlive }.orElse(false) }
                    awaitCondition { f.panel().lifecycle != BackgroundProcessLifecycle.Running }
                    val evidence = f.panel()
                    assertTrue(evidence.buffer.contains("ready\n"))
                    assertEquals(1, f.inventory().processes.size)
                    assertFailsWith<IllegalStateException> {
                        f.execute("cancel", "wait", ready.toString(), neverRelease.toString())
                    }
                    assertEquals(evidence.key, f.panel().key)
                } finally { call.cancelAndJoin() }
            }
        }
    }

    private suspend fun withFixture(installProducer: Boolean = true, body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-background-read-chain-").toRealPath()
        val runtime = Runtime()
        try {
            withTimeout(20_000) {
                runtime.install(SessionPlugin())
                runtime.install(AgentPlugin())
                runtime.install(ToolsPlugin())
                runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
                if (installProducer) {
                    runtime.install(SubprocessPlugin())
                    runtime.install(ToolBashPlugin())
                    runtime.install(ToolPwshPlugin())
                }
                runtime.install(SessionApiPlugin())
                val handle = runtime.context.agents.create(AgentId("owner"), AgentOptions(cwd = cwd.toString()))
                val gateway = ApiGateway()
                gateway.registerSessionApi(runtime.context)
                body(Fixture(runtime, cwd, handle, gateway))
            }
        } finally {
            try { withContext(NonCancellable) { runtime.context.dispose() } }
            finally {
                // Only this test's fresh directory; walk does not follow symlinks.
                Files.walk(cwd).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val cwd: Path, val handle: AgentHandle, gateway: ApiGateway) {
        val connection = LocalConnection(gateway)
        val parent = ClientSessionId(handle.agent.session.id.value)
        val owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("test-local"), 1, null), parent)
        val repository = ConnectionThreadPanelsRepository()
        val tools = runtime.context.require(ToolsKey)
        val subprocess get() = runtime.context.require(SubprocessKey)
        val execution get() = ToolExecutionContext(handle.agent.context, handle.agent.session)
        suspend fun inventory() = assertIs<BackgroundProcessesResult.Available>(connection.session.backgroundProcesses(parent))
        suspend fun panel() = assertIs<PanelLoad.Ready<BackgroundTerminalOutput>>(repository.readTerminal(owner)).value
        fun request(call: String, mode: String, vararg args: String, background: Boolean = false) = ToolCallRequest(
            CallId(call),
            BASH_TOOL_NAME,
            buildJsonObject {
                put("description", "Run the background read-chain fixture")
                put("command", shellCommand(javaCommand(mode, *args)))
                if (background) put("run_in_background", true)
            }.toString(),
            parent.value,
        )
        suspend fun execute(call: String, mode: String, vararg args: String, background: Boolean = false) =
            subprocess.execute(
                ExecCommand(javaCommand(mode, *args), background = background, timeoutMillis = 10_000),
                CallId(call),
                execution,
            )
    }

    private class ControlledAgent(request: AgentFactoryRequest) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context = request.context
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String =
            inbox.insert(message, target, wakeup)
    }

    companion object {
        private suspend fun awaitCondition(predicate: suspend () -> Boolean) {
            withTimeout(10_000) { while (!predicate()) delay(5) }
        }

        private fun readPid(path: Path): Long? =
            runCatching { Files.readString(path).trim().takeIf { it.isNotEmpty() }?.toLong() }.getOrNull()

        /** Single-quoted POSIX shell words for the test fixture argv. */
        private fun shellCommand(argv: List<String>): String =
            argv.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }

        private fun javaCommand(mode: String, vararg args: String): List<String> {
            val paths = linkedSetOf<String>()
            System.getProperty("java.class.path").split(File.pathSeparator).filter { it.isNotBlank() }
                .forEach { paths += Path.of(it).toAbsolutePath().toString() }
            // Desktop test workers may load tests through a URLClassLoader.
            for (type in listOf(BackgroundReadChainProcessFixture::class.java, Unit::class.java)) {
                type.protectionDomain.codeSource?.location?.let { paths += Path.of(it.toURI()).toString() }
                var loader: ClassLoader? = type.classLoader
                while (loader != null) {
                    (loader as? URLClassLoader)?.urLs?.filter { it.protocol == "file" }
                        ?.forEach { paths += Path.of(it.toURI()).toString() }
                    loader = loader.parent
                }
            }
            val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            return listOf(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", paths.joinToString(File.pathSeparator), BackgroundReadChainProcessFixture::class.java.name, mode) + args
        }
    }
}

/** Test-only command, launched by the real exec producer's ProcessBuilder; no shell or LLM. */
object BackgroundReadChainProcessFixture {
    @JvmStatic
    fun main(args: Array<String>) {
        fun write(text: String) { System.out.write(text.toByteArray(Charsets.UTF_8)); System.out.flush() }
        when (args[0]) {
            "utf8" -> write("你好🙂\n" + Path.of(".").toRealPath() + "\nlast\n")
            "nonzero" -> {
                write("stdout\n")
                System.err.write("stderr\n".toByteArray(Charsets.UTF_8)); System.err.flush()
                kotlin.system.exitProcess(7)
            }
            "wait" -> {
                Files.writeString(Path.of(args[1]), ProcessHandle.current().pid().toString())
                write("ready\n")
                val deadline = System.nanoTime() + 10_000_000_000L
                while (!Files.exists(Path.of(args[2]))) {
                    check(System.nanoTime() < deadline) { "Test release deadline expired" }
                    Thread.sleep(5)
                }
                write("tail\n")
            }
            else -> error("Unknown test process mode")
        }
    }
}

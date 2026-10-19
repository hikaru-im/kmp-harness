package im.hikaru.harness.desktop.connection

import im.hikaru.harness.agent.*
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.connection.BackgroundControlResult
import im.hikaru.harness.client.connection.BackgroundInputResult
import im.hikaru.harness.client.connection.BackgroundInputState
import im.hikaru.harness.client.connection.BackgroundOutputResult
import im.hikaru.harness.client.connection.BackgroundProcess
import im.hikaru.harness.client.connection.BackgroundProcessId
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundProcessesResult
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.exec.jvm.ExecCommand
import im.hikaru.harness.exec.jvm.SubprocessKey
import im.hikaru.harness.exec.jvm.SubprocessPlugin
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.tools.*
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Real JVM child -> producer -> DefaultSessionApi -> Gateway -> LocalConnection; no panel dependency. */
class SessionBackgroundMutationsProcessTest {
    @Test
    fun localGatewayChainDeliversUtf8BytesAndEofToTheExactRealProcess() = runBlocking {
        withFixture { f ->
            val (metadata, pid) = f.start("echo")
            assertEquals(BackgroundInputState.Open, metadata.inputState)
            val foreignHandle = f.runtime.context.agents.create(AgentId("foreign"), AgentOptions(cwd = f.cwd.toString()))
            val foreign = ClientSessionId(foreignHandle.agent.session.id.value)
            val absent = BackgroundProcessId("absent:process")
            assertIs<BackgroundInputResult.NotFound>(f.connection.session.writeBackgroundInput(foreign, metadata.process, "foreign"))
            assertIs<BackgroundControlResult.NotFound>(f.connection.session.closeBackgroundInput(foreign, metadata.process))
            assertIs<BackgroundControlResult.NotFound>(f.connection.session.cancelBackgroundProcess(foreign, metadata.process))
            assertIs<BackgroundInputResult.NotFound>(f.connection.session.writeBackgroundInput(f.parent, absent, "absent"))
            assertIs<BackgroundControlResult.NotFound>(f.connection.session.closeBackgroundInput(f.parent, absent))
            assertIs<BackgroundControlResult.NotFound>(f.connection.session.cancelBackgroundProcess(f.parent, absent))
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
            assertEquals("ready\n", f.output(metadata.process).buffer)

            assertEquals(BackgroundInputResult.Accepted(f.parent, metadata.process, 0),
                f.connection.session.writeBackgroundInput(f.parent, metadata.process, ""))
            val text = "你好🙂\nsecond line\n"
            val accepted = assertIs<BackgroundInputResult.Accepted>(f.connection.session.writeBackgroundInput(f.parent, metadata.process, text))
            assertEquals(text.toByteArray(StandardCharsets.UTF_8).size, accepted.bytes)
            awaitCondition { f.output(metadata.process).buffer == "ready\n$text" }
            assertEquals(BackgroundInputState.Open, f.output(metadata.process).inputState)
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)

            assertEquals(BackgroundControlResult.Applied(f.parent, metadata.process),
                f.connection.session.closeBackgroundInput(f.parent, metadata.process))
            awaitCondition { f.output(metadata.process).lifecycle == BackgroundProcessLifecycle.Exited(0) }
            val output = f.output(metadata.process)
            assertEquals("ready\n${text}EOF\n", output.buffer)
            assertEquals(BackgroundInputState.Closed, output.inputState)
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
            assertIs<BackgroundInputResult.Rejected>(f.connection.session.writeBackgroundInput(f.parent, metadata.process, "late"))
            assertEquals(1, f.inventory().processes.size, "Transport operations must not spawn or replay exec")
        }
    }

    @Test
    fun localGatewayCancelStopsTheRealOwnedChildAndRetainsItsOutput() = runBlocking {
        withFixture { f ->
            val (metadata, pid) = f.start("cancel")
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
            val text = "cancel 前🙂\n"
            assertIs<BackgroundInputResult.Accepted>(f.connection.session.writeBackgroundInput(f.parent, metadata.process, text))
            awaitCondition { f.output(metadata.process).buffer == "ready\n$text" }
            assertEquals(BackgroundControlResult.Applied(f.parent, metadata.process),
                f.connection.session.cancelBackgroundProcess(f.parent, metadata.process))
            awaitCondition { !ProcessHandle.of(pid).map { it.isAlive }.orElse(false) }
            awaitCondition { f.output(metadata.process).lifecycle != BackgroundProcessLifecycle.Running }
            val retained = f.output(metadata.process)
            assertEquals("ready\n$text", retained.buffer)
            assertEquals(BackgroundInputState.Closed, retained.inputState)
            assertIs<BackgroundInputResult.Rejected>(f.connection.session.writeBackgroundInput(f.parent, metadata.process, "late"))
            assertEquals(1, f.inventory().processes.size)
            assertEquals(metadata.process, f.inventory().processes.single().process)
        }
    }

    private suspend fun withFixture(body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-background-mutations-chain-").toRealPath()
        val runtime = Runtime()
        try {
            withTimeout(20_000) {
                runtime.install(SessionPlugin())
                runtime.install(AgentPlugin())
                runtime.install(ToolsPlugin())
                runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
                runtime.install(SubprocessPlugin())
                runtime.install(SessionApiPlugin())
                val handle = runtime.context.agents.create(AgentId("owner"), AgentOptions(cwd = cwd.toString()))
                val gateway = ApiGateway()
                gateway.registerSessionApi(runtime.context)
                body(Fixture(runtime, cwd, handle, LocalConnection(gateway)))
            }
        } finally {
            try { withContext(NonCancellable) { runtime.context.dispose() } }
            finally {
                Files.walk(cwd).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val cwd: Path, val handle: AgentHandle, val connection: LocalConnection) {
        val parent = ClientSessionId(handle.agent.session.id.value)
        suspend fun inventory() = assertIs<BackgroundProcessesResult.Available>(connection.session.backgroundProcesses(parent))
        suspend fun output(process: BackgroundProcessId) = assertIs<BackgroundOutputResult.Available>(connection.session.readBackgroundOutput(parent, process))
        suspend fun start(call: String): Pair<BackgroundProcess, Long> {
            val ready = cwd.resolve("$call.pid")
            val subprocess = runtime.context.require(SubprocessKey)
            val result = subprocess.execute(
                ExecCommand(javaCommand(ready), background = true, timeoutMillis = 10_000, stdin = true),
                CallId(call),
                ToolExecutionContext(handle.agent.context, handle.agent.session),
            )
            assertFalse(result.isError)
            val metadata = inventory().processes.single { it.callId == call }
            awaitCondition { Files.exists(ready) && output(metadata.process).buffer == "ready\n" }
            return metadata to Files.readString(ready).toLong()
        }
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
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String = inbox.insert(message, target, wakeup)
    }

    companion object {
        private suspend fun awaitCondition(predicate: suspend () -> Boolean) {
            withTimeout(10_000) { while (!predicate()) delay(5) }
        }

        private fun javaCommand(ready: Path): List<String> {
            val paths = linkedSetOf<String>()
            System.getProperty("java.class.path").split(File.pathSeparator).filter { it.isNotBlank() }
                .forEach { paths += Path.of(it).toAbsolutePath().toString() }
            for (type in listOf(BackgroundMutationChainProcessFixture::class.java, Unit::class.java)) {
                type.protectionDomain.codeSource?.location?.let { paths += Path.of(it.toURI()).toString() }
                var loader: ClassLoader? = type.classLoader
                while (loader != null) {
                    (loader as? URLClassLoader)?.urLs?.filter { it.protocol == "file" }
                        ?.forEach { paths += Path.of(it.toURI()).toString() }
                    loader = loader.parent
                }
            }
            val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            return listOf(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-cp",
                paths.joinToString(File.pathSeparator), BackgroundMutationChainProcessFixture::class.java.name, ready.toString())
        }
    }
}

/** Echo raw input bytes so the transport test detects UTF-8 corruption and missing EOF. */
object BackgroundMutationChainProcessFixture {
    @JvmStatic
    fun main(args: Array<String>) {
        Files.writeString(Path.of(args.single()), ProcessHandle.current().pid().toString())
        System.out.write("ready\n".toByteArray(StandardCharsets.UTF_8))
        System.out.flush()
        val buffer = ByteArray(1024)
        while (true) {
            val count = System.`in`.read(buffer)
            if (count < 0) break
            System.out.write(buffer, 0, count)
            System.out.flush()
        }
        System.out.write("EOF\n".toByteArray(StandardCharsets.UTF_8))
        System.out.flush()
    }
}

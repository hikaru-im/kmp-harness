package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.*
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.*
import im.hikaru.harness.tools.*
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class SubprocessContractTest {
    @Test
    fun realForegroundCommandPublishesUtf8CwdAndExactTerminalAfterEof() = runBlocking {
        withFixture { f ->
            val result = f.execute("utf8", "utf8")
            assertFalse(result.isError)
            val process = f.inventory().single()
            val output = f.output(process.process)
            assertEquals(BackgroundProcessLifecycle.Exited(0), process.lifecycle)
            assertEquals(BackgroundProcessLifecycle.Exited(0), output.lifecycle)
            assertEquals("你好🙂\n" + f.cwd.toRealPath() + "\nlast\n", output.buffer)
            assertEquals(f.owner.session.id, output.ownerSession)
            assertEquals(CallId("utf8"), process.callId)
            assertEquals(f.cwd.toRealPath().toString(), process.cwd)
            assertTrue(process.process.value.startsWith(process.producerIncarnation + ":"))
            assertTrue(output.revision >= 2)
            assertFalse(output.truncated)
            assertEquals(output, f.output(process.process), "Read does not manufacture revisions")
            assertFailsWith<IllegalStateException> { f.execute("utf8", "utf8") }
            assertEquals(1, f.inventory().size)
        }
    }

    @Test
    fun nonzeroExitIsReportedNotErrored() = runBlocking {
        withFixture { f ->
            val result = f.execute("failure", "nonzero")
            assertFalse(result.isError)
            assertEquals("stdout\nstderr\n[exit code: 7]", result.text())
            val output = f.output(f.inventory().single().process)
            assertEquals(BackgroundProcessLifecycle.Exited(7), output.lifecycle)
            assertEquals("stdout\nstderr\n", output.buffer)
        }
    }

    @Test
    fun backgroundReceiptDoesNotCompleteCommandAndReadsNeverSpawn() = runBlocking {
        withFixture { f ->
            val release = f.cwd.resolve("release")
            val ready = f.cwd.resolve("ready")
            val result = f.execute("background", "wait", ready.toString(), release.toString(), background = true)
            assertFalse(result.isError)
            assertTrue(receipt(result).getValue("background").jsonPrimitive.boolean)
            assertEquals("running", receipt(result).getValue("process").jsonObject.getValue("lifecycle").jsonObject.getValue("type").jsonPrimitive.content)
            val process = f.inventory().single()
            awaitCondition { Files.exists(ready) && f.output(process.process).buffer.contains("ready") }
            val running = f.output(process.process)
            assertEquals(BackgroundProcessLifecycle.Running, running.lifecycle)
            repeat(4) { assertEquals(running, f.output(process.process)) }
            assertEquals(1, f.inventory().size)
            Files.writeString(release, "release")
            val terminal = f.awaitTerminal(process.process)
            assertEquals(BackgroundProcessLifecycle.Exited(0), terminal.lifecycle)
            assertEquals("ready\ntail\n", terminal.buffer)
            assertTrue(terminal.revision > running.revision)
        }
    }

    @Test
    fun boundedOutputKeepsDrainingAndAdvancesRevisionEvenWithIdenticalTail() = runBlocking {
        withFixture(SubprocessConfig(maxOutputChars = 64)) { f ->
            val ready = f.cwd.resolve("ready")
            val release = f.cwd.resolve("release")
            f.execute("large", "large", ready.toString(), release.toString(), background = true)
            val process = f.inventory().single()
            awaitCondition { Files.exists(ready) && f.output(process.process).truncated }
            val first = f.output(process.process)
            assertTrue(first.buffer.length <= 64)
            assertFalse(first.buffer.firstOrNull()?.isLowSurrogate() == true)
            Files.writeString(release, "release")
            val second = f.awaitTerminal(process.process)
            assertEquals(BackgroundProcessLifecycle.Exited(0), second.lifecycle)
            assertTrue(second.truncated)
            assertTrue(second.revision > first.revision)
            assertEquals("🙂".repeat(32), second.buffer)
        }
    }

    @Test
    fun wrongContextForeignHostAndShadowedServicesCannotSpawn() = runBlocking {
        withFixture { f ->
            val wrongContext = f.owner.context.child()
            try {
                assertFailsWith<IllegalArgumentException> {
                    f.subprocess.execute(
                        f.command("utf8"), CallId("untrusted"),
                        ToolExecutionContext(wrongContext, f.owner.session),
                    )
                }
            } finally { wrongContext.dispose() }
            withFixture { other ->
                assertEquals(f.owner.session.id, other.owner.session.id)
                assertFailsWith<IllegalArgumentException> {
                    f.subprocess.execute(f.command("utf8"), CallId("foreign"), other.execution)
                }
                val shadowStore = f.owner.context.isolate(SessionKey)
                val shadowRegistry = f.owner.context.isolate(AgentKey)
                try {
                    shadowStore.provide(SessionKey, other.runtime.context.sessions)
                    shadowRegistry.provide(AgentKey, other.runtime.context.agents)
                    assertFailsWith<IllegalArgumentException> {
                        f.subprocess.execute(
                            f.command("utf8"), CallId("shadow-store"),
                            ToolExecutionContext(shadowStore, f.owner.session),
                        )
                    }
                    assertFailsWith<IllegalArgumentException> {
                        f.subprocess.execute(
                            f.command("utf8"), CallId("shadow-registry"),
                            ToolExecutionContext(shadowRegistry, f.owner.session),
                        )
                    }
                    assertFailsWith<IllegalArgumentException> {
                        f.subprocess.execute(
                            f.command("utf8"), CallId("foreign-session"),
                            ToolExecutionContext(f.owner.context, other.owner.session),
                        )
                    }
                } finally { shadowStore.dispose(); shadowRegistry.dispose() }
            }
            assertTrue(f.inventory().isEmpty())
        }
    }

    @Test
    fun realCwdConstraintRejectsTraversalAndOutsideSymlinkAndMissingCwd() = runBlocking {
        withFixture { f ->
            val outside = Files.createTempDirectory("exec-outside-")
            try {
                assertFailsWith<IllegalArgumentException> {
                    f.subprocess.execute(
                        f.command("utf8", cwd = outside.toString()), CallId("outside"), f.execution,
                    )
                }
                Files.createSymbolicLink(f.cwd.resolve("escape"), outside)
                assertFailsWith<IllegalArgumentException> {
                    f.subprocess.execute(f.command("utf8", cwd = "escape"), CallId("symlink"), f.execution)
                }
                val noCwd = f.runtime.context.agents.create(id = AgentId("no-cwd"))
                assertFailsWith<IllegalStateException> {
                    f.subprocess.execute(
                        f.command("utf8"), CallId("no-cwd"),
                        ToolExecutionContext(noCwd.agent.context, noCwd.agent.session),
                    )
                }
                assertTrue(f.inventory().isEmpty())
                val subdir = Files.createDirectory(f.cwd.resolve("allowed"))
                assertFalse(
                    f.subprocess.execute(f.command("utf8", cwd = "allowed"), CallId("inside"), f.execution).isError,
                )
                assertEquals(subdir.toRealPath().toString(), f.inventory().single().cwd)
            } finally { Files.deleteIfExists(outside) }
        }
    }

    @Test
    fun malformedCommandAndFailedStartNeverPublishRunning() = runBlocking {
        withFixture { f ->
            for (command in listOf(
                ExecCommand(emptyList()),
                ExecCommand(listOf("x"), timeoutMillis = 0),
                ExecCommand(listOf("x"), stdin = true),
                ExecCommand(listOf("x\u0000")),
            )) {
                assertFailsWith<IllegalArgumentException> {
                    f.subprocess.execute(command, CallId("invalid"), f.execution)
                }
            }
            assertFailsWith<java.io.IOException> {
                f.subprocess.execute(
                    ExecCommand(listOf(f.cwd.resolve("absent-executable").toString())),
                    CallId("start-failure"), f.execution,
                )
            }
            assertTrue(f.inventory().isEmpty())
        }
    }

    @Test
    fun wrongParentReplacementAndOldIncarnationCannotReadOutput() = runBlocking {
        withFixture { f ->
            f.execute("one", "utf8")
            val process = f.inventory().single().process
            val second = f.runtime.context.agents.create(id = AgentId("second"), options = AgentOptions(cwd = f.cwd.toString()))
            assertIs<BackgroundOutputResult.NotFound>(f.service.read(second.agent.session.id, process))
            assertFailsWith<IllegalStateException> { f.service.list(SessionId("unknown")) }
            f.handle.dispose()
            f.runtime.context.agents.create(id = AgentId("owner"), options = AgentOptions(cwd = f.cwd.toString()))
            assertIs<BackgroundOutputResult.NotFound>(f.service.read(SessionId("owner"), process))
            assertTrue(assertIs<BackgroundProcessesResult.Available>(f.service.list(SessionId("owner"))).processes.isEmpty())
            val reinstalled = LocalSubprocess(f.runtime.context)
            try { assertIs<BackgroundOutputResult.NotFound>(reinstalled.read(SessionId("owner"), process)) }
            finally { reinstalled.dispose() }
        }
    }

    @Test
    fun timeoutIsFailureAndHostDisposeKillsOwnedBackgroundProcess() = runBlocking {
        withFixture { f ->
            val ready = f.cwd.resolve("ready-timeout")
            val release = f.cwd.resolve("never-release")
            val result = f.execute("timeout", "wait", ready.toString(), release.toString(), timeoutMillis = 500)
            assertFalse(result.isError)
            assertTrue("[timed out after 500ms]" in result.text(), result.text())
            assertNotEquals(BackgroundProcessLifecycle.Running, f.output(f.inventory().single().process).lifecycle)

            val backgroundReady = f.cwd.resolve("ready-dispose")
            f.execute("dispose", "wait", backgroundReady.toString(), release.toString(), background = true)
            awaitCondition { Files.exists(backgroundReady) }
            val pid = Files.readString(backgroundReady).toLong()
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
            // Full runtime disposal exercises the plugin's real EffectScope ordering.
            f.runtime.context.dispose()
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
            assertIs<BackgroundProcessesResult.Unavailable>(f.service.list(f.owner.session.id))
            assertFailsWith<IllegalStateException> { f.execute("after-close", "utf8") }
        }
    }

    @Test
    fun immediateHostDisposeAfterBackgroundTerminalConverges() = runBlocking {
        // Observe the same boundary as the real LocalConnection regression: the
        // terminal snapshot is visible before the monitor's finally has returned.
        repeat(6) { iteration ->
            withFixture { f ->
                val ready = f.cwd.resolve("ready-$iteration")
                val release = f.cwd.resolve("release-$iteration")
                f.execute("dispose-after-exit", "wait", ready.toString(), release.toString(), background = true)
                val process = f.inventory().single().process
                awaitCondition { Files.exists(ready) && f.output(process).buffer == "ready\n" }
                val pid = Files.readString(ready).toLong()
                Files.writeString(release, "release")
                val terminal = f.awaitTerminal(process)
                assertEquals(BackgroundProcessLifecycle.Exited(0), terminal.lifecycle)
                assertEquals("ready\ntail\n", terminal.buffer)
                withTimeout(5_000) { f.runtime.context.dispose() }
                assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
                assertIs<BackgroundProcessesResult.Unavailable>(f.service.list(f.owner.session.id))
            }
        }
    }

    @Test
    fun disposeOfBlockedReaderWaitsForRealExitAndRepeatedDisposeConverges() = runBlocking {
        withFixture { f ->
            val ready = f.cwd.resolve("blocked-ready")
            f.execute("blocked-dispose", "wait", ready.toString(),
                f.cwd.resolve("never-release").toString(), background = true)
            val process = f.inventory().single().process
            awaitCondition { Files.exists(ready) && f.output(process).buffer == "ready\n" }
            val pid = Files.readString(ready).toLong()
            assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
            val producer = assertIs<LocalSubprocess>(f.service)
            coroutineScope {
                val first = async { withTimeout(5_000) { producer.dispose() } }
                val second = async { withTimeout(5_000) { producer.dispose() } }
                awaitAll(first, second)
            }
            assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
            withTimeout(5_000) { producer.dispose() }
            assertIs<BackgroundProcessesResult.Unavailable>(f.service.list(f.owner.session.id))
            assertFailsWith<IllegalStateException> { f.execute("after-dispose", "utf8") }
        }
    }

    @Test
    fun foregroundCancellationKillsProcessAndCallCannotRespawn() = runBlocking {
        withFixture { f ->
            val ready = f.cwd.resolve("ready")
            val command = f.command("wait", ready.toString(), f.cwd.resolve("never-release").toString())
            val call = async { f.subprocess.execute(command, CallId("cancel"), f.execution) }
            awaitCondition { Files.exists(ready) }
            val pid = Files.readString(ready).toLong()
            call.cancelAndJoin()
            awaitCondition { !ProcessHandle.of(pid).map { it.isAlive }.orElse(false) }
            assertFailsWith<IllegalStateException> {
                f.subprocess.execute(command, CallId("cancel"), f.execution)
            }
            assertEquals(1, f.inventory().size)
        }
    }

    @Test
    fun processLimitRejectsLiveEntriesAndEvictsOnlyCompletedInventory() = runBlocking {
        withFixture(SubprocessConfig(maxProcesses = 1)) { f ->
            val ready = f.cwd.resolve("ready")
            val release = f.cwd.resolve("release")
            f.execute("held", "wait", ready.toString(), release.toString(), background = true)
            val first = f.inventory().single().process
            assertFailsWith<IllegalStateException> { f.execute("contender", "utf8") }
            Files.writeString(release, "release")
            f.awaitTerminal(first)
            // Terminal is published just before worker cleanup; wait for a successful
            // admission rather than guessing the worker is joined from a UI snapshot.
            awaitCondition {
                try { f.execute("next", "utf8"); true }
                catch (e: IllegalStateException) { if (e.message == "Owned process limit reached") false else throw e }
            }
            assertEquals(1, f.inventory().size)
            assertIs<BackgroundOutputResult.NotFound>(f.service.read(f.owner.session.id, first))
            assertFailsWith<IllegalStateException> { f.execute("held", "utf8") }
        }
    }

    @Test
    fun interactiveUtf8InputEofAndOwnerChecksUseTheRealProcess() = runBlocking {
        withFixture { f ->
            assertFailsWith<IllegalArgumentException> { f.execute("foreground-input", "echo-input", stdin = true) }
            assertTrue(f.inventory().isEmpty())
            f.execute("input", "echo-input", background = true, stdin = true)
            val process = f.inventory().single().process
            assertEquals(BackgroundInputState.Open, f.output(process).inputState)
            val foreign = f.runtime.context.agents.create(id = AgentId("foreign"), options = AgentOptions(cwd = f.cwd.toString()))
            assertIs<BackgroundInputResult.NotFound>(f.service.write(foreign.agent.session.id, process, "private\n"))
            assertIs<BackgroundControlResult.NotFound>(f.service.closeInput(foreign.agent.session.id, process))
            assertIs<BackgroundControlResult.NotFound>(f.service.cancel(foreign.agent.session.id, process))
            assertIs<BackgroundInputResult.Rejected>(f.service.write(f.owner.session.id, process, "bad\u0000"))
            assertIs<BackgroundInputResult.Rejected>(f.service.write(f.owner.session.id, process, "你".repeat(1366)))
            val sent = assertIs<BackgroundInputResult.Accepted>(f.service.write(f.owner.session.id, process, "你好🙂\n"))
            assertEquals("你好🙂\n".toByteArray(Charsets.UTF_8).size, sent.bytes)
            awaitCondition { f.output(process).buffer.contains("echo:你好🙂\n") }
            assertIs<BackgroundControlResult.Applied>(f.service.closeInput(f.owner.session.id, process))
            assertIs<BackgroundInputResult.Rejected>(f.service.write(f.owner.session.id, process, "again\n"))
            val terminal = f.awaitTerminal(process)
            assertEquals(BackgroundProcessLifecycle.Exited(0), terminal.lifecycle)
            assertEquals(BackgroundInputState.Closed, terminal.inputState)
            assertEquals("echo:你好🙂\neof\n", terminal.buffer)
            assertIs<BackgroundControlResult.Rejected>(f.service.cancel(f.owner.session.id, process))
        }
    }

    @Test
    fun defaultCommandsHaveClosedStdinAndNeverExposeInputCapability() = runBlocking {
        withFixture { f ->
            f.execute("default-input", "echo-input")
            val process = f.inventory().single().process
            val output = f.output(process)
            assertEquals("eof\n", output.buffer)
            assertEquals(BackgroundInputState.Unsupported, output.inputState)
            assertIs<BackgroundInputResult.Rejected>(f.service.write(f.owner.session.id, process, "line\n"))
            assertIs<BackgroundControlResult.Rejected>(f.service.closeInput(f.owner.session.id, process))
        }
    }

    @Test
    fun blockedPipeTimesOutWithoutBlockingInventoryAndCanStillBeCancelled() = runBlocking {
        withFixture(SubprocessConfig(inputTimeoutMillis = 100)) { f ->
            val ready = f.cwd.resolve("nonreader-ready")
            f.execute("nonreader", "wait", ready.toString(), f.cwd.resolve("never").toString(), background = true, stdin = true)
            val process = f.inventory().single().process
            awaitCondition { Files.exists(ready) && f.output(process).buffer.contains("ready") }
            var unknown = false
            // Platform pipe capacity varies. Keep filling until the nonreading child stalls.
            for (attempt in 0 until 256) {
                val result = withTimeout(2_000) { f.service.write(f.owner.session.id, process, "x".repeat(4096)) }
                if (result is BackgroundInputResult.UnknownOutcome) { unknown = true; break }
                assertIs<BackgroundInputResult.Accepted>(result)
            }
            assertTrue(unknown, "A nonreading process must eventually fill its pipe")
            withTimeout(1_000) {
                assertEquals(BackgroundInputState.Unknown, f.output(process).inputState)
                assertEquals(1, f.inventory().size)
                assertIs<BackgroundInputResult.Rejected>(f.service.write(f.owner.session.id, process, "retry"))
                assertIs<BackgroundControlResult.Rejected>(f.service.closeInput(f.owner.session.id, process))
            }
            assertTrue(ProcessHandle.of(Files.readString(ready).toLong()).orElseThrow().isAlive, "Input timeout must not cancel a process")
            assertIs<BackgroundControlResult.Applied>(withTimeout(2_000) { f.service.cancel(f.owner.session.id, process) })
            assertIs<BackgroundProcessLifecycle.Exited>(f.awaitTerminal(process).lifecycle)
        }
    }

    @Test
    fun backgroundCommandsIgnoreForegroundTimeouts() = runBlocking {
        withFixture(SubprocessConfig(defaultTimeoutMillis = 200)) { f ->
            val ready = f.cwd.resolve("background-no-timeout-ready")
            val release = f.cwd.resolve("background-no-timeout-release")
            f.execute("background-no-timeout", "wait", ready.toString(), release.toString(), background = true)
            val process = f.inventory().single().process
            awaitCondition { Files.exists(ready) }
            delay(700)
            assertEquals(BackgroundProcessLifecycle.Running, f.output(process).lifecycle)
            Files.writeString(release, "release")
            assertEquals(BackgroundProcessLifecycle.Exited(0), f.awaitTerminal(process).lifecycle)
        }
    }

    @Test
    fun childEnvironmentScrubsCredentialsAndDshVariables() {
        val scrubbed = scrubbedEnvironment(
            mapOf(
                "PATH" to "/bin",
                "HOME" to "/home/test",
                "OPENAI_API_KEY" to "secret",
                "db_password" to "secret",
                "github_token" to "secret",
                "SIGNING_SECRET" to "secret",
                "DSH_HOME" to "/dsh",
                "DSH_SESSION_ID" to "session",
            ),
        )
        assertEquals(mapOf("PATH" to "/bin", "HOME" to "/home/test"), scrubbed)
    }

    @Test
    fun shellToolSchemasExposeDshArgumentsWithoutSandboxFields() {
        for (schema in listOf(BashToolSchema, PwshToolSchema)) {
            val parameters = schema.parameters
            val properties = parameters.getValue("properties").jsonObject.keys
            assertEquals(
                setOf("description", "command", "timeoutMs", "workdir", "run_in_background"),
                properties,
            )
            assertEquals(
                setOf("description", "command"),
                parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet(),
            )
            assertFalse(properties.any { it.contains("sandbox") })
        }
    }

    @Test
    fun bashToolExecutesDshArgumentsThroughTheLocalSubprocess() = runBlocking {
        if (isWindows()) return@runBlocking
        withFixture { f ->
            val result = f.tools.execute(
                ToolCallRequest(
                    CallId("bash-ok"), BASH_TOOL_NAME,
                    """{"description":"Print greeting","command":"printf hello"}""",
                    f.owner.session.id.value,
                ),
                f.execution,
            )
            assertFalse(result.isError)
            val process = f.inventory().single()
            assertEquals(CallId("bash-ok"), process.callId)
            assertEquals("hello", f.output(process.process).buffer)
            assertEquals(f.cwd.toRealPath().toString(), process.cwd)
        }
    }

    @Test
    fun bashToolHonorsWorkdirAndBackgroundArguments() = runBlocking {
        if (isWindows()) return@runBlocking
        withFixture { f ->
            val subdir = Files.createDirectory(f.cwd.resolve("work"))
            val result = f.tools.execute(
                ToolCallRequest(
                    CallId("bash-bg"), BASH_TOOL_NAME,
                    """{"description":"Print workdir","command":"pwd","workdir":"work","run_in_background":true}""",
                    f.owner.session.id.value,
                ),
                f.execution,
            )
            assertFalse(result.isError)
            val started = (result.content.single() as TextBlock).text
            assertTrue(started.startsWith("started background job bash-"), started)
            val process = f.inventory().single()
            assertEquals(subdir.toRealPath().toString(), process.cwd)
            val terminal = f.awaitTerminal(process.process)
            assertEquals("${subdir.toRealPath()}\n", terminal.buffer)
        }
    }

    @Test
    fun shellToolsRejectMissingFieldsUnknownKeysAndForeignScopes() = runBlocking {
        withFixture { f ->
            for ((index, arguments) in listOf(
                """{"command":"echo hi"}""",
                """{"description":"Do it"}""",
                """{"description":"Do it","command":"echo hi","unknown":true}""",
                """{"description":"Do it","command":"echo hi","timeoutMs":0}""",
                """{"description":"Do it","command":"echo hi","timeoutMs":1.5}""",
                """{"description":"   ","command":"echo hi"}""",
                """[]""",
                """{not json""",
            ).withIndex()) {
                assertFailsWith<IllegalArgumentException> {
                    f.tools.execute(
                        ToolCallRequest(CallId("bad-$index"), BASH_TOOL_NAME, arguments, f.owner.session.id.value),
                        f.execution,
                    )
                }
            }
            assertFailsWith<IllegalArgumentException> {
                f.tools.execute(
                    ToolCallRequest(
                        CallId("foreign-scope"), BASH_TOOL_NAME,
                        """{"description":"Do it","command":"echo hi"}""", "other",
                    ),
                    f.execution,
                )
            }
            assertTrue(f.inventory().isEmpty())
        }
    }

    @Test
    fun pwshResolutionFollowsDshCandidateOrder() {
        val environment = mapOf(
            "ProgramFiles" to "/program-files",
            "SystemRoot" to "/system-root",
            "PATH" to "/store-bin",
        )
        fun resolve(exists: (Path) -> Boolean) =
            PwshExecutable.resolve({ environment[it] }, "Windows 11", exists)

        assertEquals("pwsh", resolve { false })
        assertEquals("pwsh.exe", Path.of(resolve { it.fileName.toString() == "pwsh.exe" }).fileName.toString())
        assertEquals(
            "powershell.exe",
            Path.of(resolve { it.fileName.toString() == "powershell.exe" }).fileName.toString(),
        )
        assertEquals(
            "/store-bin",
            Path.of(resolve { it.toString().startsWith("/store-bin") }).parent.toString(),
        )
        assertEquals("pwsh", PwshExecutable.resolve({ environment[it] }, "Mac OS X") { true })
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

    private suspend fun withFixture(config: SubprocessConfig = SubprocessConfig(), body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-exec-test-").toRealPath()
        val runtime = Runtime()
        try {
            withTimeout(20_000) {
                runtime.install(SessionPlugin())
                runtime.install(AgentPlugin())
                runtime.install(ToolsPlugin())
                runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
                runtime.install(SubprocessPlugin(config))
                runtime.install(JobsPlugin())
                runtime.install(ToolBashPlugin())
                runtime.install(ToolPwshPlugin())
                val handle = runtime.context.agents.create(id = AgentId("owner"), options = AgentOptions(cwd = cwd.toString()))
                val fixture = Fixture(runtime, cwd, handle)
                assertTrue(fixture.inventory().isEmpty())
                assertEquals(setOf("bash", "pwsh"), fixture.tools.schemas().map { it.name }.toSet())
                assertEquals(SUBPROCESS_PLUGIN_NAME, SubprocessPluginDefinition.name)
                body(fixture)
            }
        } finally {
            try { withContext(NonCancellable) { runtime.context.dispose() } }
            finally {
                // Only this test's freshly created tree; Files.walk does not follow symlinks.
                Files.walk(cwd).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val cwd: Path, val handle: AgentHandle) {
        val owner get() = handle.agent
        val execution get() = ToolExecutionContext(owner.context, owner.session)
        val tools = runtime.context.require(ToolsKey)
        val service = runtime.context.require(BackgroundProcessesKey)
        val subprocess = runtime.context.require(SubprocessKey)
        suspend fun inventory() = assertIs<BackgroundProcessesResult.Available>(service.list(owner.session.id)).processes
        suspend fun output(process: BackgroundProcessId) = assertIs<BackgroundOutputResult.Available>(service.read(owner.session.id, process))
        suspend fun awaitTerminal(process: BackgroundProcessId): BackgroundOutputResult.Available {
            awaitCondition { output(process).lifecycle != BackgroundProcessLifecycle.Running }
            return output(process)
        }
        fun command(mode: String, vararg args: String, background: Boolean = false,
            timeoutMillis: Long? = null, cwd: String? = null, stdin: Boolean = false) =
            ExecCommand(javaCommand(mode, *args), cwd, background, timeoutMillis, stdin)
        suspend fun execute(call: String, mode: String, vararg args: String,
            background: Boolean = false, timeoutMillis: Long? = null, stdin: Boolean = false) =
            subprocess.execute(command(mode, *args, background = background, timeoutMillis = timeoutMillis, stdin = stdin),
                CallId(call), execution)
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
        override suspend fun send(message: Message, target: AgentSendTarget, wakeup: AgentWakeup): String =
            inbox.insert(message, target, wakeup)
    }

    companion object {
        private suspend fun awaitCondition(predicate: suspend () -> Boolean) {
            withTimeout(10_000) { while (!predicate()) delay(5) }
        }
        private fun receipt(result: ToolExecutionResult) =
            Json.parseToJsonElement((result.content.single() as TextBlock).text).jsonObject

        private fun ToolExecutionResult.text(): String = (content.single() as TextBlock).text

        internal fun javaCommand(mode: String, vararg args: String): List<String> {
            // Test workers may use a URL classloader rather than the process classpath.
            val paths = linkedSetOf<String>()
            System.getProperty("java.class.path").split(File.pathSeparator).filter { it.isNotBlank() }
                .forEach { paths += Path.of(it).toAbsolutePath().toString() }
            for (type in listOf(SubprocessFixtureMain::class.java, Unit::class.java)) {
                type.protectionDomain.codeSource?.location?.let { paths += Path.of(it.toURI()).toString() }
                var loader: ClassLoader? = type.classLoader
                while (loader != null) {
                    (loader as? URLClassLoader)?.getURLs()?.filter { it.protocol == "file" }
                        ?.forEach { paths += Path.of(it.toURI()).toString() }
                    loader = loader.parent
                }
            }
            val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            return listOf(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", paths.joinToString(File.pathSeparator), SubprocessFixtureMain::class.java.name, mode) + args
        }
    }
}

/** Only a test command, launched as a real JVM OS process without any shell. */
object SubprocessFixtureMain {
    @JvmStatic
    fun main(args: Array<String>) {
        fun write(text: String) { System.out.write(text.toByteArray(Charsets.UTF_8)); System.out.flush() }
        fun awaitRelease() { while (!Files.exists(Path.of(args[2]))) Thread.sleep(5) }
        fun ready() {
            val target = Path.of(args[1])
            val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
            Files.writeString(temporary, ProcessHandle.current().pid().toString())
            Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }
        when (args[0]) {
            "echo-input" -> {
                val reader = System.`in`.bufferedReader(Charsets.UTF_8)
                while (true) {
                    val line = reader.readLine() ?: break
                    write("echo:" + line + "\n")
                }
                write("eof\n")
            }
            "utf8" -> {
                for (byte in "你好🙂\n".toByteArray(Charsets.UTF_8)) { System.out.write(byte.toInt() and 255); System.out.flush() }
                write(Path.of(".").toRealPath().toString() + "\nlast\n")
            }
            "nonzero" -> {
                write("stdout\n")
                System.err.write("stderr\n".toByteArray(Charsets.UTF_8)); System.err.flush()
                kotlin.system.exitProcess(7)
            }
            "wait" -> { write("ready\n"); ready(); awaitRelease(); write("tail\n") }
            "large" -> { write("🙂".repeat(100_000)); ready(); awaitRelease(); write("🙂".repeat(100_000)) }
            else -> error("Unknown test command")
        }
    }
}

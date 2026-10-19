package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentFactory
import im.hikaru.harness.agent.AgentFactoryRequest
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentSendTarget
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.AgentWakeup
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsKey
import im.hikaru.harness.tools.ToolsPlugin
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalContractTest {
    @Test
    fun shellTerminalKeepsStateAcrossSendReadListSignalAndClose() = runBlocking {
        withFixture { f ->
            val opened = f.terminals.open(f.execution, "shell", "main")
            assertTrue(opened.cwd.isNotBlank())
            assertEquals("shell", opened.type)
            assertEquals("main", opened.name)

            val sent = f.terminals.send(f.execution, opened.sessionId, "printf 'terminal-hello\\n'", true, 10_000)
            assertTrue(sent.viewport.contains("terminal-hello"), sent.viewport)
            assertTrue(sent.lifecycle is im.hikaru.harness.session.api.TerminalLifecycle.Running)

            val page = f.terminals.read(f.execution, opened.sessionId, offset = 0, count = 500)
            assertTrue(page.text.contains("terminal-hello"), page.text)
            assertTrue(page.totalLines >= page.lineEnd)

            val sessions = f.terminals.list(f.execution)
            assertEquals(listOf(opened.sessionId), sessions.map { it.sessionId })
            assertEquals("main", sessions.single().name)

            assertTrue(f.terminals.signal(f.execution, opened.sessionId, TerminalSignal.SIGINT).delivered)

            val closed = f.terminals.close(f.execution, opened.sessionId)
            assertFalse(closed.alreadyClosing)
            assertTrue(f.terminals.list(f.execution).isEmpty())
            assertFails { f.terminals.read(f.execution, opened.sessionId) }
        }
    }

    @Test
    fun sessionDisposalClosesOwnedTerminals() = runBlocking {
        withFixture { f ->
            val opened = f.terminals.open(f.execution, "shell", "dispose")
            f.terminals.send(f.execution, opened.sessionId, "sleep 30", true, 1_000)
            val host = f.runtime.context.require(im.hikaru.harness.session.api.SessionTerminalHostKey) as JvmSessionTerminalHost
            assertEquals(1, host.bindingCount())

            withTimeout(5_000) { f.handle.dispose() }
            withTimeout(10_000) {
                while (host.bindingCount() != 0) kotlinx.coroutines.delay(10)
            }
            assertEquals(0, host.bindingCount())
        }
    }

    @Test
    fun toolTerminalBackgroundSendIsCollectableAsAJob() = runBlocking {
        withFixture { f ->
            val opened = f.terminals.open(f.execution, "shell", "bg")
            val started = f.tool(
                "terminal-send",
                TERMINAL_SEND_TOOL_NAME,
                """{"sessionId":"${opened.sessionId.value}","text":"printf 'bg-terminal\\n'","run_in_background":true}""",
            )
            val jobId = started.startedJob()
            assertTrue(jobId.startsWith("pty-send-"), jobId)

            val output = f.tool(
                "job-output",
                JOB_OUTPUT_TOOL_NAME,
                """{"job_id":"$jobId","wait":true,"timeout_ms":10000}""",
            )
            assertTrue(output.contains("bg-terminal"), output)
            assertTrue(output.contains("[status: completed"), output)

            val listed = f.tool("terminal-list", TERMINAL_LIST_TOOL_NAME, "{}")
            assertTrue(listed.contains(opened.sessionId.value), listed)
            assertTrue(listed.contains("(bg)"), listed)
            assertTrue(listed.contains("running"), listed)

            val read = f.tool(
                "terminal-read",
                TERMINAL_READ_TOOL_NAME,
                """{"sessionId":"${opened.sessionId.value}"}""",
            )
            assertTrue(read.contains("bg-terminal"), read)

            val closed = f.tool(
                "terminal-close",
                TERMINAL_CLOSE_TOOL_NAME,
                """{"sessionId":"${opened.sessionId.value}"}""",
            )
            assertTrue(closed.contains("closed terminal session"), closed)
            assertTrue(f.tool("terminal-list-2", TERMINAL_LIST_TOOL_NAME, "{}").contains("(no terminal sessions)"))
        }
    }

    private suspend fun withFixture(body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-terminal-test-").toRealPath()
        val runtime = Runtime()
        try {
            withTimeout(30_000) {
                runtime.install(SessionPlugin())
                runtime.install(AgentPlugin())
                runtime.install(ToolsPlugin())
                runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
                runtime.install(SubprocessPlugin())
                runtime.install(JobsPlugin())
                runtime.install(TerminalPlugin(TerminalConfig(startupPollMillis = 500)))
                runtime.install(ToolTerminalPlugin())
                runtime.install(ToolJobsPlugin())
                val handle = runtime.context.agents.create(id = AgentId("owner"), options = AgentOptions(cwd = cwd.toString()))
                body(Fixture(runtime, handle))
            }
        } finally {
            runCatching { withContext(NonCancellable) { runtime.context.dispose() } }
            Files.walk(cwd).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val handle: im.hikaru.harness.agent.AgentHandle) {
        val execution get() = ToolExecutionContext(handle.agent.context, handle.agent.session)
        val terminals = runtime.context.require(TerminalsKey)
        val tools = runtime.context.require(ToolsKey)

        suspend fun tool(callId: String, name: String, arguments: String): String =
            tools.execute(
                ToolCallRequest(CallId(callId), name, arguments, handle.agent.session.id.value),
                execution,
            ).text()
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

    private companion object {
        fun ToolExecutionResult.text(): String = (content.single() as TextBlock).text

        fun String.startedJob(): String {
            val prefix = "started background job "
            assertTrue(startsWith(prefix), this)
            return removePrefix(prefix).trim()
        }
    }
}

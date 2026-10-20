package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentFactory
import im.hikaru.harness.agent.AgentFactoryRequest
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentSendTarget
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.AgentWakeup
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.tools.ToolCallRequest
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult
import im.hikaru.harness.tools.ToolsKey
import im.hikaru.harness.tools.ToolsPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JobsContractTest {
    @Test
    fun backgroundShellJobReportsDeltaStatusAndStopsOnKill() = runBlocking {
        if (isWindows()) return@runBlocking
        withFixture { f ->
            val started = f.bash(
                "jobs-bg",
                "printf 'first\\n'; sleep 0.3; printf 'second\\n'; sleep 30",
                background = true,
            )
            val jobId = started.startedJob()
            assertEquals("bash", jobId.substringBefore('-'))

            val first = f.jobOutput("output-1", jobId, wait = true, timeoutMillis = 5_000)
            assertTrue("first" in first && "second" in first, first)
            assertTrue("[status: running]" in first, first)

            assertTrue(f.jobList("list").contains(jobId))
            assertEquals("(no new output)\n[status: running]", f.jobOutput("output-2", jobId).replace("\r", ""))

            val killed = f.tools.execute(
                ToolCallRequest(CallId("kill"), JOB_KILL_TOOL_NAME, """{"job_id":"$jobId"}""", f.session.id.value),
                f.execution,
            ).text()
            assertTrue("requested cancellation" in killed, killed)

            val settled = f.jobOutput("output-3", jobId, wait = true, timeoutMillis = 10_000)
            assertTrue("[status: killed" in settled, settled)
            val listed = f.jobList("list-2")
            assertTrue("$jobId [bash] killed" in listed, listed)
        }
    }

    @Test
    fun settledJobIsReadableOnceAndCanBeRemoved() = runBlocking {
        if (isWindows()) return@runBlocking
        withFixture { f ->
            val jobId = f.bash("jobs-done", "printf 'finished\\n'", background = true).startedJob()

            val settled = f.jobOutput("done-wait", jobId, wait = true, timeoutMillis = 10_000)
            assertTrue("finished" in settled, settled)
            assertTrue("[status: completed" in settled, settled)

            val again = f.jobOutput("done-again", jobId)
            assertTrue(again.startsWith("(no new output)"), again)
            assertTrue("[status: completed" in again, again)

            f.jobs.remove(JobId(jobId), f.execution)
            assertTrue(f.jobs.list(f.execution).none { it.id.value == jobId })
        }
    }

    @Test
    fun settlementEventsReportWhetherAWaiterCollectedTheOutcome() = runBlocking {
        if (isWindows()) return@runBlocking
        withFixture { f ->
            val events = mutableListOf<JobSettlementEvent>()
            val subscription = f.jobs.subscribe(JobListener { events += it })
            try {
                val unawaited = f.bash("events-1", "printf 'one\\n'", background = true).startedJob()
                awaitCondition { events.any { it.job.id.value == unawaited } }
                val first = events.first { it.job.id.value == unawaited }
                assertEquals(JobSettlementCause.NATURAL, first.cause)
                assertEquals(false, first.awaited)

                val awaited = f.bash("events-2", "printf 'two\\n'; sleep 30", background = true).startedJob()
                f.jobs.waitJob(JobId(awaited), 5_000, f.execution)
                f.jobs.kill(JobId(awaited), f.execution, "test")
                f.jobs.waitJob(JobId(awaited), 10_000, f.execution)
                awaitCondition { events.any { it.job.id.value == awaited } }
                val second = events.first { it.job.id.value == awaited }
                assertEquals(JobSettlementCause.KILL, second.cause)
                assertEquals(true, second.awaited)
            } finally {
                subscription.dispose()
            }
        }
    }

    @Test
    fun sessionDisposalCancelsItsLiveJobs() = runBlocking {
        if (isWindows()) return@runBlocking
        withFixture { f ->
            val events = mutableListOf<JobSettlementEvent>()
            val subscription = f.jobs.subscribe(JobListener { events += it })
            try {
                val pidFile = Path.of(checkNotNull(f.session.header.cwd)).resolve("session-dispose.pid")
                val jobId = f.bash(
                    "session-dispose",
                    "printf '%s' \$\$ > session-dispose.pid; sleep 30",
                    background = true,
                ).startedJob()
                assertEquals(JobStatus.Running, f.jobs.get(JobId(jobId), f.execution).status)
                awaitCondition { Files.exists(pidFile) }
                val pid = Files.readString(pidFile).trim().toLong()
                assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)

                withTimeout(5_000) { f.handle.dispose() }
                awaitCondition { events.any { it.job.id.value == jobId } }
                val settlement = events.first { it.job.id.value == jobId }
                assertEquals(JobStatus.Killed, settlement.job.status)
                assertEquals(JobSettlementCause.TEARDOWN, settlement.cause)
                assertTrue(settlement.job.detail.orEmpty().contains("session disposed"), settlement.job.detail)
                awaitCondition { !ProcessHandle.of(pid).map { it.isAlive }.orElse(false) }
            } finally {
                subscription.dispose()
            }
        }
    }

    @Test
    fun disposalCancelsLiveProducersAndSettlesWaiters() = runBlocking {
        val cwd = Files.createTempDirectory("harness-jobs-dispose-").toRealPath()
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            runtime.install(JobsPlugin())
            runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
            val handle = runtime.context.agents.create(id = AgentId("owner"), options = AgentOptions(cwd = cwd.toString()))
            val execution = ToolExecutionContext(handle.agent.context, handle.agent.session)
            val jobs = runtime.context.require(JobsKey)
            val cancelled = CompletableDeferred<String?>()
            val started = CompletableDeferred<Unit>()
            jobs.start(
                JobSpec(
                    kind = "fake",
                    label = "blocked producer",
                    owner = handle.agent.session.id,
                    producer = JobProducer {
                        started.complete(Unit)
                        object : JobHooks {
                            override suspend fun cancel(reason: String?) {
                                cancelled.complete(reason)
                            }

                            override suspend fun await(): JobOutcome {
                                cancelled.await()
                                return JobOutcome(JobStatus.Killed, "cancelled")
                            }
                        }
                    },
                ),
                execution,
            )
            started.await()

            withTimeout(5_000) {
                withContext(NonCancellable) { runtime.context.dispose() }
            }
            assertEquals("teardown", cancelled.await())
            assertFailsWith<IllegalStateException> { jobs.list(execution) }
            Unit
        } finally {
            runCatching { withContext(NonCancellable) { runtime.context.dispose() } }
            Files.walk(cwd).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private suspend fun withFixture(body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-jobs-test-").toRealPath()
        val runtime = Runtime()
        try {
            withTimeout(20_000) {
                runtime.install(SessionPlugin())
                runtime.install(AgentPlugin())
                runtime.install(ToolsPlugin())
                runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
                runtime.install(SubprocessPlugin())
                runtime.install(JobsPlugin())
                runtime.install(ToolBashPlugin())
                runtime.install(ToolPwshPlugin())
                runtime.install(ToolJobsPlugin())
                val handle = runtime.context.agents.create(id = AgentId("owner"), options = AgentOptions(cwd = cwd.toString()))
                assertTrue(runtime.context.require(JobsKey).list(ToolExecutionContext(handle.agent.context, handle.agent.session)).isEmpty())
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
        val session get() = handle.agent.session
        val execution get() = ToolExecutionContext(handle.agent.context, session)
        val tools = runtime.context.require(ToolsKey)
        val jobs = runtime.context.require(JobsKey)

        suspend fun bash(callId: String, command: String, background: Boolean): ToolExecutionResult =
            tools.execute(
                ToolCallRequest(
                    CallId(callId),
                    BASH_TOOL_NAME,
                    """{"description":"Run test command","command":${jsonString(command)},"run_in_background":$background}""",
                    session.id.value,
                ),
                execution,
            )

        suspend fun jobOutput(callId: String, jobId: String, wait: Boolean = false, timeoutMillis: Long? = null): String {
            val waitField = if (timeoutMillis == null) "" else ""","timeout_ms":$timeoutMillis"""
            return tools.execute(
                ToolCallRequest(
                    CallId(callId),
                    JOB_OUTPUT_TOOL_NAME,
                    """{"job_id":"$jobId","wait":$wait$waitField}""",
                    session.id.value,
                ),
                execution,
            ).text()
        }

        suspend fun jobList(callId: String): String =
            tools.execute(
                ToolCallRequest(CallId(callId), JOB_LIST_TOOL_NAME, "{}", session.id.value),
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
        fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

        fun jsonString(value: String): String = kotlinx.serialization.json.JsonPrimitive(value).toString()

        fun ToolExecutionResult.text(): String = (content.single() as TextBlock).text

        fun ToolExecutionResult.startedJob(): String {
            val text = text()
            val prefix = "started background job "
            assertTrue(text.startsWith(prefix), text)
            return text.removePrefix(prefix).trim()
        }

        suspend fun awaitCondition(predicate: suspend () -> Boolean) {
            withTimeout(10_000) { while (!predicate()) delay(5) }
        }
    }
}

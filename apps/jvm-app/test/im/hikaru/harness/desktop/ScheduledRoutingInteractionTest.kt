package im.hikaru.harness.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.scheduling.DesktopScheduleCalendar
import im.hikaru.harness.desktop.scheduling.DesktopScheduledStore
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ScheduledRoutingInteractionTest {
    @Test
    fun editingBoundTaskPersistsAcrossStoreInstancesWithoutExecutingSession() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val path = Files.createTempDirectory("scheduled-routing-").resolve("tasks.json")
            val store = DesktopScheduledStore(path)
            val connection = ScheduledRoutingConnection("first")
            seed(store, "first", "task-first")
            val calendar = DesktopScheduleCalendar()
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.ScheduledTasks,
                connectionTransport = ConnectionTransport.Local, scheduledStore = store, scheduleCalendar = calendar) }
            onNodeWithTag("scheduled-task:task-first").performClick()
            onNodeWithTag("scheduled-run-now").assertIsNotEnabled()
            onNodeWithTag("scheduled-edit").performScrollTo().performClick()
            onNodeWithTag("scheduled-prompt").performTextReplacement("Updated persistent prompt")
            onNodeWithTag("scheduled-save").performScrollTo().performClick()
            // Saving writes through DesktopScheduledStore on Dispatchers.IO, which the Compose test
            // clock does not track, so the detail only returns once the commit lands.
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasTestTag("scheduled-detail-prompt") and hasText("Updated persistent prompt"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("scheduled-detail-prompt").assertTextEquals("Updated persistent prompt")
            runOnIdle {
                val persisted = runBlocking { DesktopScheduledStore(path).read(ScheduledOwner(HostId("first"), null)) }
                val task = assertIs<ScheduledReadResult.Ready>(persisted).snapshot.tasks.single()
                assertEquals("Updated persistent prompt", task.prompt)
                assertEquals(2L, task.revision)
                assertEquals(ScheduledTaskStatus.Paused, task.status)
                assertEquals(0, connection.executionCalls.get())
            }
        }

    @Test
    fun hostChangesAndDisconnectHidePreviousOwnerTasks() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val store = DesktopScheduledStore(Files.createTempDirectory("scheduled-owners-").resolve("tasks.json"))
            seed(store, "first", "task-first")
            seed(store, "second", "task-second")
            var connection: Connection? by mutableStateOf(ScheduledRoutingConnection("first"))
            val calendar = DesktopScheduleCalendar()
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.ScheduledTasks,
                connectionTransport = ConnectionTransport.Local, scheduledStore = store, scheduleCalendar = calendar) }
            onNodeWithTag("scheduled-task:task-first").assertExists()
            runOnIdle { connection = ScheduledRoutingConnection("second") }
            onNodeWithTag("scheduled-task:task-second").assertExists()
            onNodeWithTag("scheduled-task:task-first").assertDoesNotExist()
            runOnIdle { connection = null }
            onNodeWithTag("scheduled-task:task-second").assertDoesNotExist()
            onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
        }

    private fun seed(store: DesktopScheduledStore, host: String, id: String) = runBlocking {
        val owner = ScheduledOwner(HostId(host), null)
        assertIs<ScheduledReadResult.Missing>(store.read(owner))
        val task = ScheduledTask(id, 1, "Task " + host, "Initial persistent prompt",
            ScheduledTarget.Heartbeat(SessionId("session-" + host)), ScheduledRule.Daily(9, 0, "UTC"), null)
        assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, ScheduledSnapshot(owner, 1, listOf(task))) { true })
    }
}

private class ScheduledRoutingConnection(id: String) : Connection {
    val executionCalls = AtomicInteger()
    private val description = HostDescription(ProtocolVersion.Current, HostId(id), "Host " + id)
    private val summary = SessionSummary(SessionId("session-" + id), 1L)
    override val host = object : HostApi {
        override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "", description)
    }
    override val session = object : SessionApi {
        override suspend fun list(): List<SessionSummary> = listOf(summary)
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
            executionCalls.incrementAndGet()
            error("Saving a paused task must not create a session")
        }
        override suspend fun history(id: SessionId): List<Message> = emptyList()
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
            executionCalls.incrementAndGet()
            error("Saving a paused task must not send a prompt")
        }
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) {
            executionCalls.incrementAndGet()
            error("Saving a paused task must not cancel a session")
        }
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.scheduling.DesktopScheduleCalendar
import im.hikaru.harness.desktop.scheduling.DesktopScheduledStore
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ScheduledPageRoutingInteractionTest {
    @Test fun harnessRouteEditsBoundTaskAndPersistsPausedConfigurationWithoutExecution() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val path = Files.createTempDirectory("scheduled-page-route-").resolve("tasks.json")
            val store = DesktopScheduledStore(path)
            val connection = ScheduledPageTestConnection("route-edit")
            seed(store, "route-edit", "task-route")
            val reader = DesktopScheduledStore(path)
            setContent {
                HarnessApp(connection, initialDestination = HarnessDestination.ScheduledTasks,
                    connectionTransport = ConnectionTransport.Local, scheduledStore = reader,
                    scheduleCalendar = DesktopScheduleCalendar())
            }
            onNodeWithTag("scheduled-task:task-route").scheduledMouseClick()
            onNodeWithTag("scheduled-edit").scheduledMouseClick()
            onNodeWithTag("scheduled-prompt").performScrollTo().performTextReplacement("Route persisted prompt")
            onNodeWithTag("scheduled-save").scheduledMouseClick()
            // Saving writes through DesktopScheduledStore on Dispatchers.IO, which the Compose test
            // clock does not track, so the detail only returns once the commit lands.
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasTestTag("scheduled-detail-prompt") and hasText("Route persisted prompt"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("scheduled-detail-prompt").assertTextEquals("Route persisted prompt")
            runOnIdle {
                val result = runBlocking { DesktopScheduledStore(path).read(ScheduledOwner(im.hikaru.contracts.harness.identity.HostId("route-edit"), null)) }
                val snapshot = assertIs<ScheduledReadResult.Ready>(result).snapshot
                assertEquals("Route persisted prompt", snapshot.tasks.single().prompt)
                assertEquals(2L, snapshot.tasks.single().revision)
                assertEquals(ScheduledTaskStatus.Paused, snapshot.tasks.single().status)
                assertEquals(0, connection.executionCalls.get())
            }
        }

    @Test fun harnessRouteOpensOnlyConfirmedSessionFromDurableHistory() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val path = Files.createTempDirectory("scheduled-page-route-history-").resolve("tasks.json")
            val store = DesktopScheduledStore(path)
            val first = ScheduledPageTestConnection("route-history-first")
            seedWithRun(store, "route-history-first", "task-history", SessionId("confirmed-route"))
            val reader = DesktopScheduledStore(path)
            setContent {
                HarnessApp(first, initialDestination = HarnessDestination.ScheduledTasks,
                    connectionTransport = ConnectionTransport.Local, scheduledStore = reader,
                    scheduleCalendar = DesktopScheduleCalendar())
            }
            onNodeWithTag("scheduled-task:task-history").scheduledMouseClick()
            onNodeWithTag("scheduled-page-open-run:confirmed-route").performScrollTo().assertIsDisplayed()
            onNodeWithTag("scheduled-page-open-run:confirmed-route").scheduledMouseClick()
            runOnIdle {
                assertEquals(listOf(SessionId("confirmed-route")), first.historyCalls)
                assertEquals(0, first.executionCalls.get())
            }
        }

    @Test fun harnessHostBindingHidesPreviousTasksHistoryAndOpenActionsWhileReading() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val path = Files.createTempDirectory("scheduled-page-route-binding-").resolve("tasks.json")
            val durable = DesktopScheduledStore(path)
            val first = ScheduledPageTestConnection("binding-first")
            val second = ScheduledPageTestConnection("binding-second")
            seedWithRun(durable, "binding-first", "task-history", SessionId("confirmed-route"))
            seed(durable, "binding-second", "task-other")
            val reader = DesktopScheduledStore(path)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val store = object : ScheduledStore by reader {
                override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
                    if (owner.hostId.value == "binding-second") gate.await()
                    return reader.read(owner)
                }
            }
            var connection: Connection? by mutableStateOf(first)
            val calendar = DesktopScheduleCalendar()
            setContent {
                HarnessApp(connection, initialDestination = HarnessDestination.ScheduledTasks,
                    connectionTransport = ConnectionTransport.Local, scheduledStore = store, scheduleCalendar = calendar)
            }
            onNodeWithTag("scheduled-task:task-history").scheduledMouseClick()
            onNodeWithTag("scheduled-page-open-run:confirmed-route").assertExists()
            runOnIdle { connection = second }
            onNodeWithTag("scheduled-loading").assertExists()
            onNodeWithTag("scheduled-task:task-history").assertDoesNotExist()
            onNodeWithTag("scheduled-page-run:confirmed-route").assertDoesNotExist()
            onNodeWithTag("scheduled-page-open-run:confirmed-route").assertDoesNotExist()
            runOnIdle { gate.complete(Unit) }
            onNodeWithTag("scheduled-task:task-other").assertExists()
            onNodeWithTag("scheduled-task:task-history").assertDoesNotExist()
            runOnIdle { connection = null }
            onNodeWithTag("scheduled-task:task-other").assertDoesNotExist()
            onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
            runOnIdle { assertEquals(0, first.executionCalls.get()); assertEquals(0, second.executionCalls.get()) }
        }

    private fun seed(store: DesktopScheduledStore, host: String, id: String) = runBlocking {
        val owner = ScheduledOwner(im.hikaru.contracts.harness.identity.HostId(host), null)
        val old = readSeedSnapshot(store, owner)
        val task = ScheduledTask(id, 1, "Route task", "Initial route prompt",
            ScheduledTarget.Heartbeat(SessionId("existing-session")), ScheduledRule.Daily(9, 0, "UTC"), null)
        commitSeedSnapshot(store, old, old.copy(revision = old.revision + 1, tasks = old.tasks + task), "seed paused task")
    }

    private fun seedWithRun(store: DesktopScheduledStore, host: String, id: String, session: SessionId) = runBlocking {
        val owner = ScheduledOwner(im.hikaru.contracts.harness.identity.HostId(host), null)
        val old = readSeedSnapshot(store, owner)
        val task = ScheduledTask(id, 1, "History task", "History prompt",
            ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Daily(9, 0, "UTC"),
            ScheduledModelRef("route-fixture-provider", "route-fixture-model"))
        var snapshot = commitSeedSnapshot(store, old, old.copy(revision = old.revision + 1, tasks = old.tasks + task),
            "seed paused history task")
        var run = ScheduledRun("confirmed-route", task, "request-route", null, 1, session, "input-route")
        // Persist legal ledger transitions without invoking any execution or session API.
        suspend fun persistRun(stage: String) {
            snapshot = commitSeedSnapshot(store, snapshot, snapshot.copy(revision = snapshot.revision + 1,
                runs = snapshot.runs.filterNot { it.id == run.id } + run), stage)
        }
        persistRun("claim history run")
        run = run.copy(phase = ScheduledRunPhase.CreatingSession)
        persistRun("record session creation intent")
        run = run.copy(phase = ScheduledRunPhase.SessionCreated, sessionRef = session)
        persistRun("record confirmed session")
        run = run.copy(phase = ScheduledRunPhase.Dispatching, deliveryIntent = true)
        persistRun("record delivery intent")
        run = run.copy(phase = ScheduledRunPhase.Unknown, detail = "Unknown route evidence")
        persistRun("record unknown outcome")
    }

    private suspend fun readSeedSnapshot(store: DesktopScheduledStore, owner: ScheduledOwner): ScheduledSnapshot =
        when (val result = store.read(owner)) {
            ScheduledReadResult.Missing -> ScheduledSnapshot(owner)
            is ScheduledReadResult.Ready -> result.snapshot.also { assertEquals(owner, it.owner, "seed read owner mismatch") }
            else -> fail("seed read failed for $owner: $result")
        }

    private suspend fun commitSeedSnapshot(store: DesktopScheduledStore, old: ScheduledSnapshot, next: ScheduledSnapshot,
        stage: String): ScheduledSnapshot {
        val label = "$stage: owner=${old.owner}, revision=${old.revision}->${next.revision}"
        assertEquals(old.owner, next.owner, label)
        assertNull(ScheduledValidator.snapshotError(next), "$label: invalid fixture snapshot")
        assertTrue(ScheduledLedgerRules.validTransition(old, next), "$label: illegal fixture ledger transition; runs=${next.runs}")
        return when (val result = store.compareAndSet(old.owner, old.revision, next) { true }) {
            is ScheduledWriteResult.Committed -> result.snapshot.also { assertEquals(next, it, "$label: unexpected committed snapshot") }
            is ScheduledWriteResult.Failed -> fail("$label: DesktopScheduledStore rejected fixture: ${result.message}")
            ScheduledWriteResult.StaleScope -> fail("$label: fixture scope became stale")
        }
    }
}

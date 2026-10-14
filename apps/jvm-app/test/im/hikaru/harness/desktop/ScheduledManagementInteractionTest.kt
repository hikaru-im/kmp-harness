package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Independent page tests; no HarnessApp fixture or executor lifecycle ownership. */
@OptIn(ExperimentalTestApi::class)
class ScheduledManagementInteractionTest {
    @Test fun historyOnlyOpensConfirmedSessionAndDisconnectRemovesRows() =
        runSkikoComposeUiTest(size = Size(900f, 1000f)) {
            val fixture = fixture()
            var opened: SessionId? = null
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementPage(fixture.repository, onOpenRun = { opened = it })
            } }
            runOnIdle { fixture.repository.selectTask("task") }
            onNodeWithTag("scheduled-run-now").assertIsNotEnabled()
            onNodeWithTag("scheduled-pause").assertIsNotEnabled()
            onNodeWithTag("scheduled-page-open-run:intent").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("scheduled-page-open-run:confirmed").performScrollTo().performClick()
            runOnIdle { assertEquals(SessionId("real-session"), opened) }
            runOnIdle { fixture.repository.bind(fixture.scope, fixture.context, connected = false) }
            onNodeWithTag("scheduled-page-run:confirmed").assertDoesNotExist()
            onNodeWithTag("scheduled-task:task").assertDoesNotExist()
            onNodeWithTag("scheduled-editor").assertDoesNotExist()
            onNodeWithTag("scheduled-disconnected").assertExists()
        }

    @Test fun narrowDarkHistoryWithoutNavigationConsumerDisablesOpen() =
        runSkikoComposeUiTest(size = Size(460f, 900f)) {
            val fixture = fixture()
            setContent { HarnessTheme(HarnessThemeMode.Dark) { ScheduledManagementPage(fixture.repository) } }
            runOnIdle { fixture.repository.selectTask("task") }
            onNodeWithTag("scheduled-page-open-run:confirmed").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("scheduled-page-open-run:intent").performScrollTo().assertIsNotEnabled()
            onNodeWithTag("scheduled-page-history").assertExists()
            runOnIdle { assertEquals(ScheduledRunPhase.Unknown, fixture.repository.state.value.effective!!.runs.first().phase) }
        }

    @Test fun navigatorRowShowsTheRealCadenceAndDurableNextRun() =
        runSkikoComposeUiTest(size = Size(900f, 600f)) {
            val scope = ScheduledScope(HostId("nav-test"), null, 1)
            val task = ScheduledTask("t1", 1, "每日摘要", "总结进展。",
                ScheduledTarget.Cron(ScheduledDestination.Projectless),
                ScheduledRule.Daily(9, 0, "UTC"), null, ScheduledTaskStatus.Active)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(task), cursors = listOf(ScheduledCursor("t1", 1, 1_000L)))
            val state = ScheduledManagementState(scope = scope, connected = true,
                phase = ScheduledLoadPhase.Ready, effective = snapshot)
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = true
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
                override val displayZoneId: String = "UTC"
                override fun nextRunLabel(nextEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String = "今天 09:00"
            }
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementNavigator(state, query = "", onQueryChange = {}, onNew = {},
                    onSelect = {}, onRetry = {}, calendar = calendar, nowEpochMillis = 1L)
            } }
            onNodeWithTag("scheduled-task:t1").assertExists()
            onNodeWithText("每天 · 下次运行：今天 09:00").assertExists()
        }

    @Test fun navigatorStatusFilterHidesTasksAndNamesTheEmptyResult() =
        runSkikoComposeUiTest(size = Size(900f, 600f)) {
            val scope = ScheduledScope(HostId("filter-test"), null, 1)
            val active = ScheduledTask("active", 1, "启用任务", "p",
                ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Daily(9, 0, "UTC"),
                null, ScheduledTaskStatus.Active)
            val paused = ScheduledTask("paused", 1, "暂停任务", "p",
                ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Daily(9, 0, "UTC"),
                null, ScheduledTaskStatus.Paused)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(active, paused))
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = true
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
            }
            var filter by mutableStateOf<ScheduledTaskStatus?>(null)
            var query by mutableStateOf("")
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementNavigator(
                    state = ScheduledManagementState(scope = scope, connected = true,
                        phase = ScheduledLoadPhase.Ready, effective = snapshot),
                    query = query, onQueryChange = { query = it }, onNew = {}, onSelect = {}, onRetry = {},
                    calendar = calendar, nowEpochMillis = 1L,
                    statusFilter = filter, onStatusFilterChange = { filter = it },
                )
            } }
            onNodeWithTag("scheduled-task:active").assertExists()
            onNodeWithTag("scheduled-task:paused").assertExists()
            // The options stay hidden until the filter control is opened.
            onNodeWithTag("scheduled-status-filter").assertDoesNotExist()
            onNodeWithTag("scheduled-toggle-filter").performClick()
            onNodeWithTag("scheduled-status-filter").assertExists()
            onNodeWithTag("scheduled-filter-paused").performClick()
            onNodeWithTag("scheduled-task:paused").assertExists()
            onNodeWithTag("scheduled-task:active").assertDoesNotExist()
            // Search and filter combine; a query that matches nothing in the active-only view is empty.
            onNodeWithTag("scheduled-filter-active").performClick()
            runOnIdle { query = "暂停" }
            onNodeWithText("没有符合筛选条件的任务").assertExists()
            runOnIdle { filter = null; query = "" }
            onNodeWithTag("scheduled-task:active").assertExists()
            onNodeWithTag("scheduled-task:paused").assertExists()
        }

    @Test fun navigatorSortsByDefaultStatusNextRunOrName() =
        runSkikoComposeUiTest(size = Size(900f, 800f)) {
            val scope = ScheduledScope(HostId("sort-test"), null, 1)
            fun task(id: String, name: String, status: ScheduledTaskStatus) = ScheduledTask(id, 1, name, "p",
                ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Daily(9, 0, "UTC"), null, status)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(
                task("alpha", "Alpha", ScheduledTaskStatus.Paused),
                task("beta", "Beta", ScheduledTaskStatus.Active),
                task("gamma", "Gamma", ScheduledTaskStatus.Active),
            ), cursors = listOf(ScheduledCursor("beta", 1, 5_000L), ScheduledCursor("gamma", 1, 1_000L)))
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = true
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
            }
            var sort by mutableStateOf(ScheduledTaskSort.Automatic)
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementNavigator(
                    state = ScheduledManagementState(scope = scope, connected = true,
                        phase = ScheduledLoadPhase.Ready, effective = snapshot),
                    query = "", onQueryChange = {}, onNew = {}, onSelect = {}, onRetry = {},
                    calendar = calendar, nowEpochMillis = 1L, sort = sort, onSortChange = { sort = it },
                )
            } }
            fun y(id: String) = onNodeWithTag("scheduled-task:$id").fetchSemanticsNode().positionInRoot.y
            // Automatic: the original lists active tasks before the paused one, stable inside each group.
            assertTrue(y("beta") < y("gamma") && y("gamma") < y("alpha"), "automatic order")
            onNodeWithTag("scheduled-toggle-filter").performClick()
            onNodeWithTag("scheduled-sort").assertExists()
            onNodeWithTag("scheduled-sort-next").performClick()
            // Next run: gamma (1s) before beta (5s); the cursor-less alpha is last.
            assertTrue(y("gamma") < y("beta") && y("beta") < y("alpha"), "next-run order")
            onNodeWithTag("scheduled-sort-title").performClick()
            assertTrue(y("alpha") < y("beta") && y("beta") < y("gamma"), "name order")
        }

    @Test fun navigatorSortsByMostRecentWriteAndListsUnknownTimesLast() =
        runSkikoComposeUiTest(size = Size(900f, 800f)) {
            val scope = ScheduledScope(HostId("updated-sort"), null, 1)
            fun task(id: String, updatedAt: Long?) = ScheduledTask(id, 1, id, "p",
                ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Daily(9, 0, "UTC"),
                null, ScheduledTaskStatus.Paused, updatedAt)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(
                task("old", 1_000L), task("unknown", null), task("new", 3_000L),
            ))
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = true
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
            }
            var sort by mutableStateOf(ScheduledTaskSort.Automatic)
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementNavigator(
                    state = ScheduledManagementState(scope = scope, connected = true,
                        phase = ScheduledLoadPhase.Ready, effective = snapshot),
                    query = "", onQueryChange = {}, onNew = {}, onSelect = {}, onRetry = {},
                    calendar = calendar, nowEpochMillis = 1L, sort = sort, onSortChange = { sort = it },
                )
            } }
            fun y(id: String) = onNodeWithTag("scheduled-task:$id").fetchSemanticsNode().positionInRoot.y
            onNodeWithTag("scheduled-toggle-filter").performClick()
            onNodeWithTag("scheduled-sort-updated").performClick()
            // `updated_at_desc`: newest write first; the task with no recorded time sorts last.
            assertTrue(y("new") < y("old") && y("old") < y("unknown"), "recently-updated order")
        }

    @Test fun navigatorOmitsTheFilterControlWithoutAHandler() =
        runSkikoComposeUiTest(size = Size(900f, 600f)) {
            val scope = ScheduledScope(HostId("no-filter"), null, 1)
            val task = ScheduledTask("t1", 1, "每日摘要", "p",
                ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Daily(9, 0, "UTC"), null)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(task))
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = true
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
            }
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementNavigator(
                    state = ScheduledManagementState(scope = scope, connected = true,
                        phase = ScheduledLoadPhase.Ready, effective = snapshot),
                    query = "", onQueryChange = {}, onNew = {}, onSelect = {}, onRetry = {},
                    calendar = calendar, nowEpochMillis = 1L,
                )
            } }
            onNodeWithTag("scheduled-task:t1").assertExists()
            onNodeWithTag("scheduled-toggle-filter").assertDoesNotExist()
        }

    @Test fun detailShowsTheRealLastRunInstantFromTerminalEvidence() =
        runSkikoComposeUiTest(size = Size(900f, 1000f)) {
            val scope = ScheduledScope(HostId("last-run"), null, 1)
            val target = ScheduledTarget.Cron(ScheduledDestination.Projectless)
            val model = ScheduledModelRef("provider", "model")
            val context = ScheduledValidationContext(listOf(target), listOf(model))
            val task = ScheduledTask("t1", 1, "每日摘要", "总结进展。", target, ScheduledRule.Daily(9, 0, "UTC"), model)
            val run = ScheduledRun("run-1", task, "request-1", null, claimedAt = 5_000L,
                candidateSessionId = SessionId("session-1"), inputMessageId = "input-1",
                phase = ScheduledRunPhase.Succeeded, sessionRef = SessionId("session-1"),
                terminal = ScheduledTerminalEvidence(turn = 1, userMessageSeq = 1, turnEndSeq = 2,
                    endedAt = 9_000L, outcome = ScheduledRunPhase.Succeeded, reason = "completed"),
                deliveryIntent = true)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(task), runs = listOf(run))
            assertNull(ScheduledValidator.snapshotError(snapshot))
            val store = object : ScheduledStore {
                override suspend fun read(owner: ScheduledOwner): ScheduledReadResult =
                    if (owner == snapshot.owner) ScheduledReadResult.Ready(snapshot) else ScheduledReadResult.Missing
                override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
                    isCurrentScope: () -> Boolean): ScheduledWriteResult = error("Read-only fixture must not write")
            }
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
                override val displayZoneId: String = "UTC"
                override fun relativeRunLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String =
                    "22 分钟前"
            }
            val repository = ScheduledRepository(store, ScheduleEngine(calendar))
            repository.bind(scope, context)
            runBlocking { repository.load() }
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementPage(repository, calendar = calendar, nowEpochMillis = 10_000L)
            } }
            runOnIdle { repository.selectTask("t1") }
            onNodeWithTag("scheduled-detail").assertExists()
            onNodeWithText("上次运行于 22 分钟前").assertExists()
        }

    @Test fun detailShowsTheDurableLastUpdatedTime() =
        runSkikoComposeUiTest(size = Size(900f, 1000f)) {
            val scope = ScheduledScope(HostId("updated-detail"), null, 1)
            val target = ScheduledTarget.Cron(ScheduledDestination.Projectless)
            val model = ScheduledModelRef("provider", "model")
            val context = ScheduledValidationContext(listOf(target), listOf(model))
            val task = ScheduledTask("t1", 1, "每日摘要", "总结进展。", target, ScheduledRule.Daily(9, 0, "UTC"), model,
                updatedAt = 5_000L)
            val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(task))
            assertNull(ScheduledValidator.snapshotError(snapshot))
            val store = object : ScheduledStore {
                override suspend fun read(owner: ScheduledOwner): ScheduledReadResult =
                    if (owner == snapshot.owner) ScheduledReadResult.Ready(snapshot) else ScheduledReadResult.Missing
                override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
                    isCurrentScope: () -> Boolean): ScheduledWriteResult = error("Read-only fixture must not write")
            }
            val calendar = object : ScheduleCalendar {
                override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
                override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
                    ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
                override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
                override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
                override val displayZoneId: String = "UTC"
                override fun relativeRunLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String =
                    "22 分钟前"
            }
            val repository = ScheduledRepository(store, ScheduleEngine(calendar))
            repository.bind(scope, context)
            runBlocking { repository.load() }
            setContent { HarnessTheme(HarnessThemeMode.Light) {
                ScheduledManagementPage(repository, calendar = calendar, nowEpochMillis = 10_000L)
            } }
            runOnIdle { repository.selectTask("t1") }
            onNodeWithTag("scheduled-detail-updated").assertExists()
            onNodeWithText("上次更新 · 22 分钟前").assertExists()
        }

    private fun fixture(): Fixture {
        val scope = ScheduledScope(HostId("page-test"), null, 1)
        val target = ScheduledTarget.Cron(ScheduledDestination.Projectless)
        val model = ScheduledModelRef("provider", "model")
        val context = ScheduledValidationContext(listOf(target), listOf(model))
        val task = ScheduledTask("task", 1, "Persisted task", "Prompt", target, ScheduledRule.Hourly(1, 0), model)
        val session = SessionId("real-session")
        val runs = listOf(
            ScheduledRun("confirmed", task, "request-confirmed", null, 1, session, "input-confirmed",
                phase = ScheduledRunPhase.Unknown, sessionRef = session, deliveryIntent = true, detail = "Missing durable TurnEnd"),
            ScheduledRun("intent", task, "request-intent", null, 2, SessionId("intent-session"), "input-intent",
                phase = ScheduledRunPhase.Rejected),
        )
        val snapshot = ScheduledSnapshot(scope.owner, 1, listOf(task), runs = runs)
        assertNull(ScheduledValidator.snapshotError(snapshot))
        val store = object : ScheduledStore {
            override suspend fun read(owner: ScheduledOwner): ScheduledReadResult =
                if (owner == snapshot.owner) ScheduledReadResult.Ready(snapshot) else ScheduledReadResult.Missing
            override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
                isCurrentScope: () -> Boolean): ScheduledWriteResult = error("Read-only navigation fixture must not write")
        }
        val calendar = object : ScheduleCalendar {
            override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
            override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = error("Hourly fixture")
            override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = error("Hourly fixture")
            override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = error("Hourly fixture")
        }
        val repository = ScheduledRepository(store, ScheduleEngine(calendar))
        repository.bind(scope, context)
        runBlocking { repository.load() }
        return Fixture(repository, scope, context)
    }

    private data class Fixture(val repository: ScheduledRepository, val scope: ScheduledScope, val context: ScheduledValidationContext)
}

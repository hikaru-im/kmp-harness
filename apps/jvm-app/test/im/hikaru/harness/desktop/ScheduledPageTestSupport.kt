package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.scheduling.DesktopScheduleCalendar
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Fixtures are isolated to desktop tests; production consumes only its bound Host catalog. */
internal class ScheduledPageTestSupport(
    taskCount: Int = 2,
    longFields: Boolean = false,
    includeRuns: Boolean = true,
    loading: Boolean = false,
    readError: Boolean = false,
) : AutoCloseable {
    val scope = ScheduledScope(HostId("scheduled-page-test"), null, 1)
    val target = ScheduledTarget.Cron(if (longFields) ScheduledDestination.Cwd("/workspace/" + "long-directory/".repeat(30))
        else ScheduledDestination.Projectless)
    val model = ScheduledModelRef(if (longFields) "provider-" + "long-provider-".repeat(20) else "test-provider",
        if (longFields) "model-" + "long-model-".repeat(24) else "test-model")
    val context = ScheduledValidationContext(listOf(target, ScheduledTarget.Heartbeat(SessionId("existing-session"))),
        listOf(model, ScheduledModelRef("second-provider", "second-model")))
    val connection = ScheduledPageTestConnection("scheduled-page-test")
    val tasks = (1..taskCount).map { index -> ScheduledTask("task-$index", 1, "Task $index", "Review the workspace.",
        target, ScheduledRule.Hourly(1, 0), model) }
    val confirmedSession = SessionId("confirmed-session")
    val runs = if (includeRuns && tasks.isNotEmpty()) listOf(
        ScheduledRun("confirmed", tasks.first(), "request-confirmed", null, 1, confirmedSession, "input-confirmed",
            phase = ScheduledRunPhase.Unknown, sessionRef = confirmedSession, deliveryIntent = true,
            detail = "Unknown：缺少持久化 TurnEnd；不能重复投递。"),
        ScheduledRun("intent", tasks.first(), "request-intent", null, 2, SessionId("candidate-only"), "input-intent",
            phase = ScheduledRunPhase.Rejected),
    ) else emptyList()
    val store = ScheduledPageTestStore(ScheduledSnapshot(scope.owner, 1, tasks, runs = runs), loading, readError)
    val worker = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = ScheduledRepository(store, ScheduleEngine(DesktopScheduleCalendar()))

    init {
        assertNull(ScheduledValidator.snapshotError(store.snapshot))
        repository.bind(scope, context)
        if (loading) worker.launch(start = CoroutineStart.UNDISPATCHED) { repository.load() }
        else runBlocking { repository.load() }
    }

    fun selectFirst() = repository.selectTask(tasks.first().id)
    fun beginEdit() = repository.beginEdit(tasks.first().id)
    fun startSaving() {
        beginEdit()
        store.writeGate = CompletableDeferred()
        worker.launch(start = CoroutineStart.UNDISPATCHED) { repository.save() }
        assertEquals(ScheduledMutation.Save, repository.state.value.mutation)
    }
    override fun close() { worker.cancel(); repository.clear() }
}

internal class ScheduledPageTestStore(
    var snapshot: ScheduledSnapshot,
    var loading: Boolean = false,
    var readError: Boolean = false,
) : ScheduledStore {
    val writes = AtomicInteger()
    var failWrite = false
    var warning: String? = null
    var writeGate: CompletableDeferred<Unit>? = null
    override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
        if (loading) awaitCancellation()
        if (readError) return ScheduledReadResult.IoFailure("Fixture read failed; configuration is unavailable.")
        return if (owner == snapshot.owner) ScheduledReadResult.Ready(snapshot) else ScheduledReadResult.Missing
    }
    override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
        isCurrentScope: () -> Boolean): ScheduledWriteResult {
        writes.incrementAndGet()
        writeGate?.await()
        if (!isCurrentScope()) return ScheduledWriteResult.StaleScope
        if (failWrite) return ScheduledWriteResult.Failed("Fixture save failed; draft and effective state retained.")
        if (owner != this.snapshot.owner || expectedRevision != this.snapshot.revision)
            return ScheduledWriteResult.Failed("Fixture revision mismatch")
        assertNull(ScheduledValidator.snapshotError(snapshot))
        this.snapshot = snapshot
        return ScheduledWriteResult.Committed(snapshot, warning)
    }
}

@Composable
internal fun ScheduledPageTestSurface(repository: ScheduledRepository, theme: HarnessThemeMode = HarnessThemeMode.Light,
    onOpenRun: ((SessionId) -> Unit)? = null) {
    CompositionLocalProvider(LocalDensity provides Density(1f)) {
        HarnessTheme(theme) {
            var query by remember(repository) { mutableStateOf("") }
            val state by repository.state.collectAsState()
            val scope = rememberCoroutineScope()
            BoxWithConstraints(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)
                .semantics { testTag = "scheduled-capture-root" }) {
                val navigatorWidth = if (maxWidth < 800.dp) 184.dp else 248.dp
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(navigatorWidth).fillMaxHeight()) {
                        ScheduledManagementNavigator(state, query, { query = it },
                            onNew = { repository.beginCreate() },
                            onSelect = { repository.selectTask(it) },
                            onRetry = { scope.launch { repository.load() } })
                    }
                    ScheduledManagementPage(repository, Modifier.weight(1f), onOpenRun = onOpenRun)
                }
            }
        }
    }
}

internal class ScheduledPageTestConnection(id: String) : Connection {
    val executionCalls = AtomicInteger()
    val historyCalls = mutableListOf<SessionId>()
    private val description = HostDescription(ProtocolVersion.Current, HostId(id), "Host $id")
    private val summary = SessionSummary(SessionId("existing-session"), 1L)
    override val host = object : HostApi {
        override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "", description)
    }
    override val session = object : SessionApi {
        override suspend fun list(): List<SessionSummary> = listOf(summary)
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
            executionCalls.incrementAndGet(); error("Configuration UI must not create a session")
        }
        override suspend fun history(id: SessionId): List<Message> { historyCalls += id; return emptyList() }
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
            executionCalls.incrementAndGet(); error("Configuration UI must not prompt")
        }
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) {
            executionCalls.incrementAndGet(); error("Deleting a configuration must not cancel a session")
        }
    }
}

/** Mouse dispatch exercises hit testing rather than invoking the semantic click action. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.scheduledMouseClick(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    if (node.boundsInRoot.width < node.size.width - 1f || node.boundsInRoot.height < node.size.height - 1f ||
        node.boundsInRoot.width <= 0f || node.boundsInRoot.height <= 0f) performScrollTo()
    return assertIsDisplayed().performMouseInput { click() }
}

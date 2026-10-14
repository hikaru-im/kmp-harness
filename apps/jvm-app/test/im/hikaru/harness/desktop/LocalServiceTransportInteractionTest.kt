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
import im.hikaru.harness.client.account.*
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.git.*
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.scheduling.DesktopScheduleCalendar
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.*

/** An authenticated account cannot change the explicitly selected transport or authorize local IO. */
@OptIn(ExperimentalTestApi::class)
class LocalServiceTransportInteractionTest {
    @Test
    fun explicitLocalUsesGitWithAccountButRelayWithSameHostCannotReadIt() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val account = transportTestAccount()
            val backend = TransportRecordingGit()
            var transport by mutableStateOf(ConnectionTransport.Local)
            val connection = TransportServiceConnection()
            try {
                setContent { HarnessApp(connection, account = account, initialDestination = HarnessDestination.Settings,
                    connectionTransport = transport, gitBackend = backend, gitInitialCwd = "/test-repository") }
                onNodeWithTag("settings-row-git").performClick()
                onNodeWithTag("git-repository-root").assertTextEquals("/test-repository")
                runOnIdle {
                    assertTrue(backend.reads.get() > 0)
                    assertTrue(backend.bindings.all { it.access == GitAccess.LocalReadOnly && it.identity == null })
                }
                val before = backend.reads.get()
                runOnIdle { transport = ConnectionTransport.Relay }
                onNodeWithTag("git-unavailable").assertExists()
                onNodeWithTag("git-repository-root").assertDoesNotExist()
                runOnIdle { assertEquals(before, backend.reads.get()) }
            } finally { runBlocking { account.shutdown() } }
        }

    @Test
    fun explicitLocalUsesSchedulingWithAccountButRelayClearsTasksWithoutReadingLocalStore() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val account = transportTestAccount()
            val store = TransportRecordingStore()
            val connection = TransportServiceConnection()
            val calendar = DesktopScheduleCalendar()
            var transport by mutableStateOf(ConnectionTransport.Local)
            try {
                setContent { HarnessApp(connection, account = account, initialDestination = HarnessDestination.ScheduledTasks,
                    connectionTransport = transport, scheduledStore = store, scheduleCalendar = calendar) }
                onNodeWithTag("scheduled-task:local-only").assertExists()
                runOnIdle { assertTrue(store.reads.get() > 0); assertTrue(store.owners.all { it.memberIdentity == null }) }
                val before = store.reads.get()
                runOnIdle { transport = ConnectionTransport.Relay }
                onNodeWithTag("scheduled-task:local-only").assertDoesNotExist()
                onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
                runOnIdle { assertEquals(before, store.reads.get()) }
            } finally { runBlocking { account.shutdown() } }
        }

    @Test
    fun appOwnedExecutorSurvivesInitialDescribeButStopsWhenVerifiedLocalBindingIsLost() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val account = transportTestAccount()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val calendar = DesktopScheduleCalendar()
            val store = TransportRecordingStore()
            val described = CompletableDeferred<Unit>()
            val base = TransportServiceConnection()
            val sessionReads = AtomicInteger()
            val connection = object : Connection {
                override val host = object : HostApi {
                    override suspend fun describe(): ApiResult<HostDescription> {
                        described.await()
                        return base.host.describe()
                    }
                }
                override val session = object : SessionApi by base.session {
                    override suspend fun list(): List<SessionSummary> {
                        sessionReads.incrementAndGet()
                        return base.session.list()
                    }
                }
            }
            val lease = object : ScheduledLeaderLease {
                var held = false
                override suspend fun acquire(scope: ScheduledScope): Boolean { held = true; return true }
                override fun isHeld() = held
                override suspend fun release() { held = false }
            }
            val access = object : ScheduledSessionAccess {
                override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? = null
                override suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId = error("Unexpected create")
                override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt = error("Unexpected prompt")
                override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun) = ScheduledEvidenceResult.Pending
            }
            val executor = ScheduledExecutor(store, ScheduleEngine(calendar), access, lease, scope,
                config = ScheduledExecutorConfig(verifiedCronExecution = true, tickIntervalMillis = 600_000))
            var transport by mutableStateOf(ConnectionTransport.Local)
            try {
                assertEquals(ScheduledStartResult.Started, runBlocking { executor.start(ScheduledScope(HostId("same-host"), null, 1)) })
                setContent { HarnessApp(connection, account = account, connectionTransport = transport,
                    scheduledStore = store, scheduleCalendar = calendar, scheduledExecutor = executor,
                    scheduledExecutorConnection = connection) }
                runOnIdle { assertTrue(executor.state.value.accepting); assertEquals(0, sessionReads.get()) }
                runOnIdle { described.complete(Unit) }
                waitUntil(timeoutMillis = 5_000) { sessionReads.get() > 0 }
                runOnIdle { assertTrue(executor.state.value.accepting); transport = ConnectionTransport.Relay }
                waitUntil(timeoutMillis = 5_000) { executor.state.value.scope == null }
                runOnIdle { assertFalse(executor.state.value.accepting); assertTrue(lease.isHeld()) }
                assertEquals(ScheduledShutdownResult.Drained, runBlocking { executor.shutdown() })
                assertFalse(lease.isHeld())
            } finally {
                runBlocking { executor.shutdown(); account.shutdown() }
                scope.cancel()
            }
        }
    @Test
    fun recoveryButtonReplacesFailedExecutorAndTransportLossFencesItsOwner() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = RecoveryStore(initialReadFailures = 1)
            val calendar = DesktopScheduleCalendar()
            val connection = TransportServiceConnection()
            val factories = AtomicInteger()
            val owner = recoveryOwner(scope, store, calendar, factories)
            val binding = ScheduledScope(HostId("same-host"), null, 1)
            var transport by mutableStateOf(ConnectionTransport.Local)
            try {
                assertIs<ScheduledStartResult.Unavailable>(runBlocking { owner.start(binding) })
                val original = owner.state.value.executor
                setContent { HarnessApp(connection, initialDestination = HarnessDestination.ScheduledTasks,
                    connectionTransport = transport, scheduledStore = store, scheduleCalendar = calendar,
                    scheduledExecutorOwner = owner, scheduledExecutorConnection = connection) }
                onNodeWithTag("scheduled-execution-recover").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 5_000) { owner.state.value.executor.state.value.accepting }
                runOnIdle { assertEquals(2, factories.get()); assertNotSame(original, owner.state.value.executor) }
                onNodeWithTag("scheduled-execution-recover").assertDoesNotExist()
                runOnIdle { transport = ConnectionTransport.Relay }
                waitUntil(timeoutMillis = 5_000) { owner.state.value.invalidated }
                assertIs<ScheduledStartResult.Unavailable>(runBlocking { owner.recover(binding) })
                runOnIdle { assertEquals(2, factories.get()); assertFalse(owner.state.value.executor.state.value.accepting) }
            } finally { runBlocking { owner.shutdown() }; scope.cancel() }
        }

    @Test
    fun recoveryShowsUnsafeLedgerFailureAndRetainsOriginalExecutor() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val store = RecoveryStore(invalid = true)
            val calendar = DesktopScheduleCalendar()
            val connection = TransportServiceConnection()
            val factories = AtomicInteger()
            val owner = recoveryOwner(scope, store, calendar, factories)
            try {
                assertIs<ScheduledStartResult.Unavailable>(runBlocking { owner.start(ScheduledScope(HostId("same-host"), null, 1)) })
                val original = owner.state.value.executor
                setContent { HarnessApp(connection, initialDestination = HarnessDestination.ScheduledTasks,
                    connectionTransport = ConnectionTransport.Local, scheduledStore = store, scheduleCalendar = calendar,
                    scheduledExecutorOwner = owner, scheduledExecutorConnection = connection) }
                onNodeWithTag("scheduled-execution-recover").assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 5_000) { !owner.state.value.busy && owner.state.value.executor === original &&
                    original.state.value.unavailableReason != null && owner.state.value.error != null }
                onNodeWithTag("scheduled-execution-recovery-error").assertExists()
                runOnIdle { assertEquals(1, factories.get()); assertSame(original, owner.state.value.executor) }
            } finally {
                store.invalid = false
                runBlocking { owner.shutdown() }
                scope.cancel()
            }
        }
}

private fun recoveryOwner(scope: CoroutineScope, store: RecoveryStore, calendar: DesktopScheduleCalendar,
    factories: AtomicInteger): ScheduledExecutorOwner = ScheduledExecutorOwner {
    factories.incrementAndGet()
    ScheduledExecutor(store, ScheduleEngine(calendar), object : ScheduledSessionAccess {
        override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? = null
        override suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId = error("Unexpected create during recovery")
        override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt = error("Unexpected prompt during recovery")
        override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun) = ScheduledEvidenceResult.Pending
    }, object : ScheduledLeaderLease {
        private var held = false
        override suspend fun acquire(scope: ScheduledScope): Boolean { held = true; return true }
        override fun isHeld() = held
        override suspend fun release() { held = false }
    }, scope, config = ScheduledExecutorConfig(verifiedCronExecution = true, tickIntervalMillis = 600_000))
}

private class RecoveryStore(initialReadFailures: Int = 0, @Volatile var invalid: Boolean = false) : ScheduledStore {
    private val failures = AtomicInteger(initialReadFailures)
    override suspend fun read(owner: ScheduledOwner): ScheduledReadResult = when {
        invalid -> ScheduledReadResult.Invalid("Damaged ledger")
        failures.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0 -> ScheduledReadResult.IoFailure("Temporary read failure")
        else -> ScheduledReadResult.Ready(ScheduledSnapshot(owner))
    }
    override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
        isCurrentScope: () -> Boolean): ScheduledWriteResult = error("Unexpected write during empty-ledger recovery")
}

private fun transportTestAccount(): MemberAccount {
    val session = AppSession(MemberIdentity(BackendTenant("https://example.invalid", 1), 1), "test-token", null, null)
    return MemberAccount(object : MemberTransport {
        override suspend fun login(target: BackendTenant, mobile: String, password: String) = error("Unexpected account IO")
        override suspend fun refresh(session: AppSession) = error("Unexpected account IO")
        override suspend fun logout(session: AppSession) = error("Unexpected account IO")
        override suspend fun profile(session: AppSession) = error("Unexpected account IO")
        override suspend fun updateProfile(session: AppSession, request: im.hikaru.contracts.app.member.MemberProfileUpdateRequest) = error("Unexpected account IO")
        override suspend fun tenant(target: BackendTenant, website: String) = error("Unexpected account IO")
        override fun close() = Unit
    }, object : AppSessionStore {
        override val durable = false
        override fun read() = session
        override fun write(session: AppSession) = error("Unexpected account write")
        override fun clear() = Unit
    })
}

private class TransportServiceConnection : Connection {
    override val host = object : HostApi {
        override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "",
            HostDescription(ProtocolVersion.Current, HostId("same-host"), "Service test host"))
    }
    override val session = object : SessionApi {
        override suspend fun list() = listOf(SessionSummary(SessionId("parent"), 1, "/test-repository"))
        override suspend fun history(id: SessionId) = emptyList<Message>()
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected session mutation")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected session mutation")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected session mutation")
    }
}

private class TransportRecordingGit : GitBackend {
    val reads = AtomicInteger()
    val bindings = mutableListOf<GitBinding>()
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository {
        reads.incrementAndGet()
        bindings += binding
        return GitRepository(binding, cwd, "$cwd/.git", "$cwd/.git")
    }
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        reads.incrementAndGet(); return GitStatusSnapshot(emptyList(), "main", "test-head")
    }
    override suspend fun branches(repository: GitRepository): List<GitBranch> { reads.incrementAndGet(); return emptyList() }
    override suspend fun worktrees(repository: GitRepository): List<GitWorktree> { reads.incrementAndGet(); return emptyList() }
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot {
        reads.incrementAndGet(); return GitDiffSnapshot(request, "", "test-head")
    }
}

private class TransportRecordingStore : ScheduledStore {
    val reads = AtomicInteger()
    val owners = mutableListOf<ScheduledOwner>()
    override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
        reads.incrementAndGet()
        owners += owner
        return ScheduledReadResult.Ready(ScheduledSnapshot(owner, 1, listOf(ScheduledTask(
            "local-only", 1, "Local task", "Prompt", ScheduledTarget.Heartbeat(SessionId("parent")),
            ScheduledRule.Daily(9, 0, "UTC"), null))))
    }
    override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
        isCurrentScope: () -> Boolean): ScheduledWriteResult = error("Unexpected scheduling write")
}

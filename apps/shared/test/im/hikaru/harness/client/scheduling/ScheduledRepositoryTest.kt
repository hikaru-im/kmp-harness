package im.hikaru.harness.client.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.SessionPromptReceipt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduledRepositoryTest {
    private val scope = ScheduledScope(HostId("local-test"), null, 1)
    private val target = ScheduledTarget.Cron(ScheduledDestination.Projectless)
    private val model = ScheduledModelRef("test-provider", "test-model")
    private val context = ScheduledValidationContext(listOf(target), listOf(model))
    private val engine = ScheduleEngine(HourlyTestCalendar)

    @Test fun defaultStoreCannotBecomeEditableOrReportMemorySuccess() = runTest {
        val repository = ScheduledRepository(engine = engine)
        repository.bind(scope, context)
        repository.load()
        assertEquals(ScheduledLoadPhase.Error, repository.state.value.phase)
        assertNotNull(repository.state.value.loadError)
        repository.beginCreate(target)
        assertNull(repository.state.value.draft)
        assertFalse(repository.save())
        assertNull(repository.state.value.effective)
    }

    @Test fun pausedCreateEditDeleteAndRetryReadTheActualStore() = runTest {
        val store = TestStore()
        val repository = ready(store)
        draft(repository)
        assertTrue(repository.save())
        val task = repository.state.value.tasks.single()
        assertEquals(ScheduledTaskStatus.Paused, task.status)
        assertFalse(repository.state.value.canActivate)
        assertFalse(repository.state.value.canRunNow)
        assertFalse(repository.setPaused(task.id, false))
        assertFalse(repository.runNow(task.id))
        repository.beginEdit(task.id)
        repository.updateDraft { it.copy(name = "edited") }
        assertTrue(repository.save())
        assertEquals(2L, repository.state.value.tasks.single().revision)
        val reloaded = ready(store)
        assertEquals("edited", reloaded.state.value.tasks.single().name)
        assertTrue(reloaded.delete(task.id))
        reloaded.retry()
        assertTrue(reloaded.state.value.tasks.isEmpty())
        assertEquals(listOf(task.id), reloaded.state.value.effective!!.deletedTaskIds)
        assertTrue(store.reads >= 3)
    }

    @Test fun saveStampsTheDurableWriteTimeFromTheInjectedClock() = runTest {
        val store = TestStore()
        var now = 1_000L
        val repository = ScheduledRepository(store, engine, clock = { now }).also {
            it.bind(scope, context)
            it.load()
        }
        draft(repository)
        assertTrue(repository.save())
        assertEquals(1_000L, repository.state.value.tasks.single().updatedAt)
        now = 2_000L
        repository.beginEdit(repository.state.value.tasks.single().id)
        repository.updateDraft { it.copy(name = "second edit") }
        assertTrue(repository.save())
        assertEquals(2_000L, repository.state.value.tasks.single().updatedAt)
    }

    @Test fun savingCapturesCandidateRejectsDuplicateAndRebasesNewerDraft() = runTest {
        val store = TestStore()
        val repository = ready(store)
        draft(repository)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeWrite = { entered.complete(Unit); release.await() }
        val saving = async { repository.save() }
        entered.await()
        repository.updateDraft { it.copy(name = "newer edit") }
        assertFalse(repository.save())
        assertFalse(repository.delete(repository.state.value.draft!!.taskId))
        repository.cancelDraft()
        assertNotNull(repository.state.value.draft)
        assertTrue(repository.state.value.tasks.isEmpty())
        release.complete(Unit)
        assertTrue(saving.await())
        assertEquals("first", repository.state.value.tasks.single().name)
        assertEquals("newer edit", repository.state.value.draft!!.name)
        assertEquals(1L, repository.state.value.draft!!.expectedTaskRevision)
        store.beforeWrite = null
        assertTrue(repository.save())
        assertNull(repository.state.value.draft)
        assertEquals("newer edit", repository.state.value.tasks.single().name)
        assertEquals(2, store.writes)
    }

    @Test fun failureKeepsEffectiveAndLatestDraftAndRetryDoesNotHideBadFile() = runTest {
        val store = TestStore()
        val repository = ready(store)
        draft(repository)
        assertTrue(repository.save())
        val old = repository.state.value.effective
        repository.beginEdit(old!!.tasks.single().id)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeWrite = { entered.complete(Unit); release.await() }
        store.failure = "disk full"
        val saving = async { repository.save() }
        entered.await()
        repository.updateDraft { it.copy(prompt = "latest prompt") }
        release.complete(Unit)
        assertFalse(saving.await())
        assertEquals(old, repository.state.value.effective)
        assertEquals("latest prompt", repository.state.value.draft!!.prompt)
        assertEquals("disk full", repository.state.value.mutationError)
        store.readResult = ScheduledReadResult.UnsupportedVersion(99)
        repository.retry()
        assertEquals(ScheduledLoadPhase.Error, repository.state.value.phase)
        assertEquals(old, repository.state.value.effective)
        assertEquals("latest prompt", repository.state.value.draft!!.prompt)
        assertFalse(repository.save())
    }

    @Test fun clearAndOwnerGenerationChangesFenceLateLoadsAndSaves() = runTest {
        val store = TestStore()
        val repository = ready(store)
        draft(repository)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeWrite = { entered.complete(Unit); release.await() }
        val saving = async { repository.save() }
        entered.await()
        repository.unbind()
        assertEquals(ScheduledManagementState(), repository.state.value)
        val identity = MemberIdentity(BackendTenant("https://example.test", 1), 7)
        val next = ScheduledScope(HostId("other-test"), identity, 2)
        repository.bind(next, context)
        release.complete(Unit)
        assertFalse(saving.await())
        assertTrue(store.documents.isEmpty())
        repository.load()
        assertEquals(next.owner, repository.state.value.effective!!.owner)
        val loadEntered = CompletableDeferred<Unit>()
        val loadRelease = CompletableDeferred<Unit>()
        store.beforeRead = { loadEntered.complete(Unit); loadRelease.await() }
        val loading = async { repository.retry() }
        loadEntered.await()
        repository.clear()
        repository.bind(next.copy(generation = 3), context)
        loadRelease.complete(Unit)
        loading.await()
        assertEquals(ScheduledLoadPhase.NotLoaded, repository.state.value.phase)
        assertNull(repository.state.value.effective)
    }

    @Test fun sameScopeDisconnectDuringSaveRequiresReloadAndPreservesDraft() = runTest {
        val store = TestStore()
        val repository = ready(store)
        draft(repository)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeWrite = { entered.complete(Unit); release.await() }
        val saving = async { repository.save() }
        entered.await()
        repository.bind(scope, context, connected = false)
        repository.bind(scope, context, connected = true)
        assertEquals(ScheduledLoadPhase.NotLoaded, repository.state.value.phase)
        assertFalse(repository.state.value.canMutate)
        release.complete(Unit)
        assertFalse(saving.await())
        assertNotNull(repository.state.value.draft)
        repository.load()
        assertTrue(repository.state.value.tasks.isEmpty())
        assertEquals("first", repository.state.value.draft!!.name)
    }

    @Test fun catalogValidationRejectsFabricatedTargetsModelsAndClearedNumericFields() = runTest {
        val store = TestStore()
        val repository = ready(store)
        draft(repository)
        repository.updateDraft { it.copy(target = ScheduledTarget.Heartbeat(SessionId("invented")), model = null) }
        assertFalse(repository.save())
        assertTrue("target" in repository.draftErrors())
        repository.updateDraft { it.copy(target = target, model = ScheduledModelRef("foreign", "model")) }
        assertTrue("model" in repository.draftErrors())
        repository.updateDraft { it.copy(model = model, schedule = it.schedule.copy(intervalHours = "")) }
        assertTrue("schedule" in repository.draftErrors())
        assertEquals(0, store.writes)
        val heartbeat = ScheduledTarget.Heartbeat(SessionId("existing"))
        repository.bind(scope, context.copy(targets = listOf(heartbeat)))
        repository.load()
        repository.updateDraft { it.copy(target = heartbeat, model = null, schedule = it.schedule.copy(intervalHours = "1")) }
        assertTrue(repository.save())
        assertNull(repository.state.value.tasks.single().model)
    }

    @Test fun existingActiveTaskCanOnlyBePersistentlyPausedWithoutExecutor() = runTest {
        val store = TestStore()
        val active = ScheduledTask("existing", 1, "active", "prompt", target,
            ScheduledRule.Hourly(1, 0), model, ScheduledTaskStatus.Active)
        store.documents[scope.owner] = ScheduledSnapshot(scope.owner, 1, listOf(active),
            cursors = listOf(ScheduledCursor(active.id, active.revision, 3_600_000)))
        val repository = ready(store)
        assertFalse(repository.runNow(active.id))
        assertFalse(repository.setPaused(active.id, false))
        assertTrue(repository.setPaused(active.id, true))
        repository.retry()
        assertEquals(ScheduledTaskStatus.Paused, repository.state.value.tasks.single().status)
        assertEquals(2L, repository.state.value.tasks.single().revision)
        assertTrue(repository.state.value.effective!!.cursors.isEmpty())
    }

    @Test fun loadedWithMatchingReadyExecutorAdvertisesRealCapability() = runTest {
        val store = TestStore()
        val executor = execution(store, backgroundScope)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context)
        repository.load()
        assertNull(repository.state.value.executionUnavailableReason)
        draft(repository)
        assertTrue(repository.save())
        assertTrue(repository.state.value.canActivate)
        assertTrue(repository.state.value.canRunNow)
        assertTrue(repository.setPaused(repository.state.value.tasks.single().id, false))
        val active = repository.state.value.tasks.single()
        assertEquals(ScheduledTaskStatus.Active, active.status)
        val cursor = repository.state.value.effective!!.cursors.single()
        assertEquals(active.id, cursor.taskId)
        assertEquals(active.revision, cursor.taskRevision)
        assertTrue(repository.setPaused(active.id, true))
        assertEquals(ScheduledTaskStatus.Paused, repository.state.value.tasks.single().status)
        assertTrue(repository.state.value.effective!!.cursors.isEmpty())
        assertNull(ScheduledValidator.snapshotError(store.documents.getValue(scope.owner)))
    }

    @Test fun observingStartupAndInvalidationUpdatesCapabilityAndDurableHistory() = runTest {
        val store = TestStore()
        val access = TestAccess()
        val executor = execution(store, backgroundScope, access)
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context)
        repository.load()
        backgroundScope.launch { repository.observeExecution() }
        runCurrent()
        assertNotNull(repository.state.value.executionUnavailableReason)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        runCurrent()
        assertNull(repository.state.value.executionUnavailableReason)
        draft(repository)
        assertTrue(repository.save())
        val task = repository.state.value.tasks.single()
        assertTrue(repository.runNow(task.id, "stable-request"))
        runCurrent()
        val run = repository.state.value.effective!!.runs.single()
        assertEquals(ScheduledRunPhase.Accepted, run.phase)
        assertEquals(run.sessionRef, repository.runSessionRef(run.id))
        assertEquals(1, access.creates)
        assertEquals(1, access.prompts)
        assertFalse(repository.state.value.canRunNow)
        // Acknowledged retry consumes the same durable claim without another create/prompt.
        assertTrue(repository.runNow(task.id, "stable-request"))
        runCurrent()
        assertEquals(1, access.creates)
        assertEquals(1, access.prompts)
        executor.invalidate()
        runCurrent()
        assertNotNull(repository.state.value.executionUnavailableReason)
        assertFalse(repository.state.value.canActivate)
        assertFalse(repository.runNow(task.id))
        assertEquals(run.sessionRef, repository.runSessionRef(run.id))
        repository.bind(scope, context, connected = false)
        assertNull(repository.state.value.effective)
        assertNull(repository.runSessionRef(run.id))
    }

    @Test fun hostIdentityAndGenerationMustAllMatchExecutionScope() = runTest {
        val store = TestStore()
        val access = TestAccess()
        val executor = execution(store, backgroundScope, access)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val task = ScheduledTask("scoped", 1, "Task", "prompt", target, ScheduledRule.Hourly(1, 0), model)
        val identity = MemberIdentity(BackendTenant("https://tenant.test", 3), 7)
        val scopes = listOf(scope.copy(hostId = HostId("other")), scope.copy(memberIdentity = identity), scope.copy(generation = 2))
        val repository = ScheduledRepository(store, engine, executor)
        for (other in scopes) {
            store.documents[other.owner] = ScheduledSnapshot(other.owner, 1, listOf(task))
            repository.bind(other, context)
            repository.load()
            repository.selectTask(task.id)
            assertNotNull(repository.state.value.executionUnavailableReason)
            assertFalse(repository.state.value.canActivate)
            assertFalse(repository.setPaused(task.id, false))
            assertFalse(repository.runNow(task.id))
        }
        assertEquals(0, access.creates)
        assertEquals(0, access.prompts)
    }

    @Test fun heartbeatActivatesAndClaimsItsBoundSessionWithoutEverCreatingOne() = runTest {
        val store = TestStore()
        val heartbeat = ScheduledTarget.Heartbeat(SessionId("bound-session"))
        val task = ScheduledTask("heartbeat", 1, "Follow up", "keep going", heartbeat, ScheduledRule.Hourly(1, 0), null)
        store.documents[scope.owner] = ScheduledSnapshot(scope.owner, 1, listOf(task))
        val access = TestAccess()
        val executor = execution(store, backgroundScope, access)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context.copy(targets = listOf(heartbeat)))
        repository.load()
        repository.selectTask(task.id)
        // A live-executor heartbeat is a real capability: it no longer reports the old blocked reason.
        assertNull(repository.state.value.executionUnavailableReason)
        assertTrue(repository.state.value.canActivate)
        assertTrue(repository.setPaused(task.id, false))
        assertTrue(repository.runNow(task.id, "heartbeat-request"))
        runCurrent()
        val run = store.documents.getValue(scope.owner).runs.single()
        // The durable intent and its reference are the exact existing session, never a created one.
        assertEquals(heartbeat.sessionId, run.candidateSessionId)
        assertEquals(heartbeat.sessionId, run.sessionRef)
        assertEquals(ScheduledRunPhase.Accepted, run.phase)
        assertNotNull(run.receiptId)
        assertEquals(0, access.creates)
        assertEquals(1, access.prompts)
    }

    @Test fun heartbeatIsRefusedWhenItsBoundSessionIsNoLongerAvailable() = runTest {
        val store = TestStore()
        val heartbeat = ScheduledTarget.Heartbeat(SessionId("gone-session"))
        val task = ScheduledTask("heartbeat", 1, "Follow up", "keep going", heartbeat, ScheduledRule.Hourly(1, 0), null)
        store.documents[scope.owner] = ScheduledSnapshot(scope.owner, 1, listOf(task))
        val access = TestAccess()
        val executor = execution(store, backgroundScope, access)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context.copy(targets = listOf(heartbeat)))
        repository.load()
        repository.selectTask(task.id)
        // The target disappears after start: activation and run-now must both re-verify and refuse.
        access.unavailable = "目标会话当前不是本机活动会话；会话跟进不能创建或恢复它。"
        assertFalse(repository.setPaused(task.id, false))
        assertEquals(access.unavailable, repository.state.value.mutationError)
        assertFalse(repository.runNow(task.id))
        assertEquals(0, access.creates)
        assertEquals(0, access.prompts)
        assertTrue(store.documents.getValue(scope.owner).runs.isEmpty())
    }

    @Test fun lateActivationAndExecutorPublicationCannotRestorePreviousOwner() = runTest {
        val store = TestStore()
        val executor = execution(store, backgroundScope)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context)
        repository.load()
        draft(repository)
        assertTrue(repository.save())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        store.beforeWrite = { entered.complete(Unit); release.await() }
        backgroundScope.launch { repository.observeExecution() }
        val activation = async { repository.setPaused(repository.state.value.tasks.single().id, false) }
        entered.await()
        repository.clear()
        repository.bind(scope.copy(generation = 2), context)
        release.complete(Unit)
        assertFalse(activation.await())
        runCurrent()
        assertEquals(2L, repository.state.value.scope!!.generation)
        assertNull(repository.state.value.effective)
        assertNotNull(repository.state.value.executionUnavailableReason)
    }

    @Test fun persistedHistoryUsesConfirmedReferenceAndSurvivesTaskDeletion() = runTest {
        val store = TestStore()
        val task = ScheduledTask("history", 1, "Task", "prompt", target, ScheduledRule.Hourly(1, 0), model)
        val confirmed = SessionId("confirmed")
        val unknown = ScheduledRun("unknown", task, "request-a", null, 1, confirmed, "input-a",
            phase = ScheduledRunPhase.Unknown, sessionRef = confirmed, deliveryIntent = true, detail = "No durable TurnEnd")
        val intent = ScheduledRun("intent-only", task, "request-b", null, 2, SessionId("candidate"), "input-b",
            phase = ScheduledRunPhase.Rejected)
        store.documents[scope.owner] = ScheduledSnapshot(scope.owner, 1, listOf(task), runs = listOf(unknown, intent))
        val repository = ready(store)
        assertEquals(confirmed, repository.runSessionRef(unknown.id))
        assertNull(repository.runSessionRef(intent.id))
        assertTrue(repository.delete(task.id))
        assertEquals(listOf(unknown, intent), repository.state.value.effective!!.runs)
        assertEquals(confirmed, repository.runSessionRef(unknown.id))
        repository.clear()
        assertNull(repository.runSessionRef(unknown.id))
    }

    @Test fun invalidActiveSnapshotWithoutCursorNeverBecomesReady() = runTest {
        val store = TestStore()
        val task = ScheduledTask("invalid-active", 1, "Task", "prompt", target, ScheduledRule.Hourly(1, 0), model, ScheduledTaskStatus.Active)
        store.documents[scope.owner] = ScheduledSnapshot(scope.owner, 1, listOf(task))
        val repository = ScheduledRepository(store, engine)
        repository.bind(scope, context)
        repository.load()
        assertEquals(ScheduledLoadPhase.Error, repository.state.value.phase)
        assertNull(repository.state.value.effective)
        assertNotNull(repository.state.value.executionUnavailableReason)
    }

    @Test fun ambiguousClaimCancellationRetriesThePersistedRequestWithoutRedelivery() = runTest {
        val store = TestStore()
        val access = TestAccess()
        val executor = execution(store, backgroundScope, access)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context)
        repository.load()
        draft(repository)
        assertTrue(repository.save())
        val taskId = repository.state.value.tasks.single().id
        store.afterWrite = { throw CancellationException("response lost after durable claim") }
        assertFailsWith<CancellationException> { repository.runNow(taskId) }
        val claimed = store.documents.getValue(scope.owner).runs.single()
        assertEquals(ScheduledRunPhase.Claimed, claimed.phase)
        store.afterWrite = null
        repository.load()
        assertTrue(repository.runNow(taskId))
        runCurrent()
        assertEquals(claimed.requestId, repository.state.value.effective!!.runs.single().requestId)
        assertEquals(0, access.creates)
        assertEquals(0, access.prompts)
    }

    @Test fun contextChangeClearsCapabilityUntilValidatedReloadAndFencesOldModel() = runTest {
        val store = TestStore()
        val executor = execution(store, backgroundScope)
        assertEquals(ScheduledStartResult.Started, executor.start(scope))
        val repository = ScheduledRepository(store, engine, executor)
        repository.bind(scope, context)
        repository.load()
        draft(repository)
        assertTrue(repository.save())
        val task = repository.state.value.tasks.single()
        repository.bind(scope, context.copy(models = emptyList()))
        assertEquals(ScheduledLoadPhase.NotLoaded, repository.state.value.phase)
        assertFalse(repository.state.value.canActivate)
        assertNotNull(repository.state.value.executionUnavailableReason)
        repository.load()
        assertFalse(repository.setPaused(task.id, false))
        assertFalse(repository.runNow(task.id))
    }

    private fun execution(store: TestStore, coroutineScope: CoroutineScope, access: TestAccess = TestAccess()): ScheduledExecutor =
        ScheduledExecutor(store, engine, access, object : ScheduledLeaderLease {
            private var held = false
            override suspend fun acquire(scope: ScheduledScope): Boolean { held = true; return true }
            override fun isHeld(): Boolean = held
            override suspend fun release() { held = false }
        }, coroutineScope, ScheduledClock { 0L }, ScheduledExecutorConfig(verifiedCronExecution = true))

    private class TestAccess : ScheduledSessionAccess {
        var creates = 0
        var prompts = 0
        var unavailable: String? = null
        override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? = unavailable
        override suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId { creates++; return run.candidateSessionId }
        override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt {
            prompts++; return SessionPromptReceipt(requireNotNull(run.sessionRef), "receipt-" + run.id)
        }
        override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult = ScheduledEvidenceResult.Pending
    }

    private suspend fun ready(store: TestStore): ScheduledRepository = ScheduledRepository(store, engine).also {
        it.bind(scope, context)
        it.load()
        assertEquals(ScheduledLoadPhase.Ready, it.state.value.phase)
    }
    private fun draft(repository: ScheduledRepository) {
        repository.beginCreate(target)
        repository.updateDraft { it.copy(name = "first", prompt = "real test prompt", model = model,
            schedule = ScheduledRuleDraft(ScheduledFrequency.Hourly)) }
    }

    /** Only tests use memory; production defaults to an unavailable store. */
    private class TestStore : ScheduledStore {
        val documents = mutableMapOf<ScheduledOwner, ScheduledSnapshot>()
        var reads = 0
        var writes = 0
        var failure: String? = null
        var readResult: ScheduledReadResult? = null
        var beforeRead: (suspend () -> Unit)? = null
        var beforeWrite: (suspend () -> Unit)? = null
        var afterWrite: (suspend () -> Unit)? = null
        override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
            reads++
            val captured = readResult ?: documents[owner]?.let { ScheduledReadResult.Ready(it) } ?: ScheduledReadResult.Missing
            beforeRead?.invoke()
            return captured
        }
        override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
            isCurrentScope: () -> Boolean): ScheduledWriteResult {
            beforeWrite?.invoke()
            if (!isCurrentScope()) return ScheduledWriteResult.StaleScope
            failure?.let { return ScheduledWriteResult.Failed(it) }
            if ((documents[owner]?.revision ?: 0) != expectedRevision) return ScheduledWriteResult.Failed("conflict")
            documents[owner] = snapshot
            writes++
            afterWrite?.invoke()
            return ScheduledWriteResult.Committed(snapshot)
        }
    }
}

internal object HourlyTestCalendar : ScheduleCalendar {
    override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
    override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = error("Hourly tests do not use dates")
    override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = error("Hourly tests do not use dates")
    override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = error("Hourly tests do not use dates")
}

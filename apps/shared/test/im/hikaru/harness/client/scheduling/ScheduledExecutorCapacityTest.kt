package im.hikaru.harness.client.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.SessionPromptReceipt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Capacity tests use a validating, JSON-roundtripping CAS store and controlled access.
 * They prove executor admission/recovery, not OS locking or process-crash durability. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledExecutorCapacityTest {
    @Test fun defaultBudgetAdmitsTwoConcurrentTasksDeduplicatesAtCapacityAndReleasesOnTerminal() = runTest {
        val f = Fixture(this)
        f.access.holdCreates = true
        try {
            f.start()
            val first = async { f.executor.runNow(taskA.id, "first") }
            val second = async { f.executor.runNow(taskB.id, "second") }
            val claims = listOf(first.await(), second.await()).map { assertIs<ScheduledClaimResult.Claimed>(it) }
            runCurrent()
            assertEquals(2, f.store.snapshot.runs.count { it.blocksTask })
            assertEquals(setOf(taskA.id, taskB.id), f.access.creates.map { it.task.id }.toSet())
            assertTrue(f.access.prompts.isEmpty())
            val writes = f.store.writeAttempts
            val full = assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskC.id, "third"))
            assertTrue(full.reason.contains("并发额度已满"))
            assertTrue(full.reason.contains("上限 2"))
            assertEquals(writes, f.store.writeAttempts)
            val duplicate = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, "first"))
            assertTrue(duplicate.duplicate)
            assertEquals(claims.first().run.id, duplicate.run.id)
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskC.id, "first"))
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(2, f.access.creates.size)

            f.access.createGate.complete(Unit)
            runCurrent()
            assertEquals(0, f.store.snapshot.runs.count { it.blocksTask })
            assertTrue(f.store.snapshot.runs.all { it.phase == ScheduledRunPhase.Succeeded && it.terminal == terminal })
            assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskC.id, "third"))
            runCurrent()
            assertEquals(3, f.store.snapshot.runs.size)
            assertEquals(3, f.access.creates.size)
            assertEquals(3, f.access.prompts.size)
            assertEquals(3, f.access.prompts.map { it.inputMessageId }.distinct().size)
            assertTrue(f.access.prompts.groupBy { it.task.id }.values.all { it.size == 1 })
        } finally { f.close() }
    }

    @Test fun suspendedClaimCasSerializesTwoTasksCompetingForTheLastSlot() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.access.holdCreates = true
        try {
            f.start()
            f.store.beforeWrite = {
                f.store.beforeWrite = null
                entered.complete(Unit)
                release.await()
            }
            val first = async { f.executor.runNow(taskA.id, "first") }
            entered.await()
            val contender = async { f.executor.runNow(taskB.id, "contender") }
            runCurrent()
            assertFalse(contender.isCompleted)
            assertTrue(f.store.snapshot.runs.isEmpty())
            release.complete(Unit)
            assertIs<ScheduledClaimResult.Claimed>(first.await())
            val refused = assertIs<ScheduledClaimResult.Refused>(contender.await())
            assertTrue(refused.reason.contains("并发额度已满"))
            runCurrent()
            assertEquals(taskA.id, f.store.snapshot.runs.single().task.id)
            assertEquals(listOf(taskA.id), f.access.creates.map { it.task.id })
            assertTrue(f.access.prompts.isEmpty())
            assertTrue(f.store.commits.all { it.runs.count { run -> run.blocksTask } <= 1 })
        } finally { release.complete(Unit); f.close() }
    }

    @Test fun ambiguousCreateUnknownReservesAcrossTasksAndAnIndependentReloadedLifecycle() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        try {
            f.start()
            f.access.failCreate = true
            val claim = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, "ambiguous-create"))
            runCurrent()
            val unknown = f.store.snapshot.runs.single()
            assertEquals(ScheduledRunPhase.Unknown, unknown.phase)
            assertNull(unknown.sessionRef)
            assertFalse(unknown.deliveryIntent)
            assertTrue(unknown.blocksTask)
            val writes = f.store.writeAttempts
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "other-task"))
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(1, f.access.creates.size)
            assertTrue(f.access.prompts.isEmpty())
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())

            val restarted = Fixture(this, f.store.reload(), maxConcurrentRuns = 1)
            try {
                restarted.start(binding.copy(generation = 2))
                assertEquals(ScheduledRunPhase.Unknown, restarted.store.snapshot.runs.single().phase)
                val beforeRetry = restarted.store.writeAttempts
                val retry = assertIs<ScheduledClaimResult.Claimed>(restarted.executor.runNow(taskA.id, "ambiguous-create"))
                assertTrue(retry.duplicate)
                assertEquals(claim.run.id, retry.run.id)
                assertIs<ScheduledClaimResult.Refused>(restarted.executor.runNow(taskC.id, "restart-other-task"))
                assertEquals(beforeRetry, restarted.store.writeAttempts)
                assertTrue(restarted.access.creates.isEmpty())
                assertTrue(restarted.access.prompts.isEmpty())
            } finally { restarted.close() }
        } finally { f.close() }
    }

    @Test fun concurrentRetriesOfOneRequestReserveOnlyOneSlot() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.access.holdCreates = true
        try {
            f.start()
            val writes = f.store.writeAttempts
            f.store.beforeWrite = {
                f.store.beforeWrite = null
                entered.complete(Unit)
                release.await()
            }
            val first = async { f.executor.runNow(taskA.id, "same-request") }
            entered.await()
            val retry = async { f.executor.runNow(taskA.id, "same-request") }
            runCurrent()
            assertFalse(retry.isCompleted)
            release.complete(Unit)
            val claimed = assertIs<ScheduledClaimResult.Claimed>(first.await())
            val duplicate = assertIs<ScheduledClaimResult.Claimed>(retry.await())
            assertFalse(claimed.duplicate)
            assertTrue(duplicate.duplicate)
            assertEquals(claimed.run.id, duplicate.run.id)
            runCurrent()
            assertEquals(1, f.store.snapshot.runs.size)
            // One claim CAS and one CreatingSession CAS; retry itself never writes.
            assertEquals(writes + 2, f.store.writeAttempts)
            assertEquals(1, f.access.creates.size)
            assertTrue(f.access.prompts.isEmpty())
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "other-request"))
            assertEquals(writes + 2, f.store.writeAttempts)
        } finally { release.complete(Unit); f.close() }
    }

    @Test fun deletedTaskUnknownHistoryReservesTheBudgetAndStillDeduplicates() = runTest {
        val history = uncertainRun(taskA)
        val persisted = initial().copy(tasks = listOf(taskB, taskC), deletedTaskIds = listOf(taskA.id), runs = listOf(history))
        val f = Fixture(this, StrictStore(persisted).reload(), maxConcurrentRuns = 1)
        try {
            f.start(binding.copy(generation = 3))
            val writes = f.store.writeAttempts
            val duplicate = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, history.requestId))
            assertTrue(duplicate.duplicate)
            assertEquals(history.id, duplicate.run.id)
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "unrelated"))
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(listOf(taskA.id), f.store.snapshot.deletedTaskIds)
            assertTrue(f.access.creates.isEmpty())
            assertTrue(f.access.prompts.isEmpty())
        } finally { f.close() }
    }

    @Test fun deliveredUnknownReservesUntilExactTerminalReconciliationCommits() = runTest {
        val prior = uncertainRun(taskA).copy(sessionRef = SessionId("prior-a"), deliveryIntent = true, receiptId = "prior-receipt")
        val f = Fixture(this, StrictStore(initial().copy(runs = listOf(prior))), maxConcurrentRuns = 1)
        f.access.result = ScheduledEvidenceResult.Pending
        try {
            f.start()
            val writes = f.store.writeAttempts
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "blocked"))
            assertEquals(writes, f.store.writeAttempts)
            assertTrue(f.access.creates.isEmpty())
            f.access.result = ScheduledEvidenceResult.Terminal(terminal)
            // First wakeup registers a recheck; the next deterministic clock step makes it due.
            f.executor.tick()
            f.now += 10
            f.executor.tick()
            assertEquals(ScheduledRunPhase.Succeeded, f.store.snapshot.runs.single().phase)
            assertEquals(terminal, f.store.snapshot.runs.single().terminal)
            assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskB.id, "blocked"))
            runCurrent()
            assertEquals(listOf(taskB.id), f.access.creates.map { it.task.id })
            assertEquals(listOf(taskB.id), f.access.prompts.map { it.task.id })
        } finally { f.close() }
    }

    @Test fun terminalEvidenceWithoutCommittedCasDoesNotReleaseBudget() = runTest {
        val prior = uncertainRun(taskA).copy(sessionRef = SessionId("prior-a"), deliveryIntent = true, receiptId = "prior-receipt")
        val f = Fixture(this, StrictStore(initial().copy(runs = listOf(prior))), maxConcurrentRuns = 1)
        f.access.result = ScheduledEvidenceResult.Pending
        try {
            f.start()
            f.executor.tick()
            f.now += 10
            f.access.result = ScheduledEvidenceResult.Terminal(terminal)
            f.store.beforeWrite = {
                f.store.beforeWrite = null
                f.store.editTask(taskC.id)
            }
            f.executor.tick()
            val durable = f.store.reload().snapshot
            assertEquals(ScheduledRunPhase.Unknown, durable.runs.single().phase)
            assertNull(durable.runs.single().terminal)
            assertTrue(durable.runs.single().blocksTask)
            assertEquals("competing edit", durable.tasks.single { it.id == taskC.id }.name)
            assertFalse(f.executor.state.value.accepting)
            val writes = f.store.writeAttempts
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "uncommitted-terminal"))
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(durable, f.store.snapshot)
            assertTrue(f.access.creates.isEmpty())
            assertTrue(f.access.prompts.isEmpty())
        } finally { f.close() }
    }

    @Test fun everyExactTerminalOutcomeReleasesBudgetAndKeepsRequestDeduplication() = runTest {
        for (outcome in listOf(ScheduledRunPhase.Succeeded, ScheduledRunPhase.Failed, ScheduledRunPhase.Blocked,
            ScheduledRunPhase.Incomplete, ScheduledRunPhase.Interrupted, ScheduledRunPhase.Aborted)) {
            val f = Fixture(this, maxConcurrentRuns = 1)
            f.access.holdPrompts = true
            val evidence = terminal.copy(outcome = outcome, reason = "controlled $outcome")
            f.access.result = ScheduledEvidenceResult.Terminal(evidence)
            try {
                f.start()
                val first = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, "terminal-$outcome"))
                runCurrent()
                assertEquals(1, f.store.snapshot.runs.count { it.blocksTask })
                assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "after-$outcome"))
                f.access.promptGate.complete(Unit)
                runCurrent()
                val completed = f.store.reload().snapshot.runs.single()
                assertEquals(outcome, completed.phase)
                assertEquals(evidence, completed.terminal)
                assertFalse(completed.blocksTask)
                val writes = f.store.writeAttempts
                val retry = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, "terminal-$outcome"))
                assertTrue(retry.duplicate)
                assertEquals(first.run.id, retry.run.id)
                assertEquals(writes, f.store.writeAttempts)
                assertEquals(1, f.access.creates.size)
                assertEquals(1, f.access.prompts.size)
                assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskB.id, "after-$outcome"))
                runCurrent()
                assertEquals(2, f.store.snapshot.runs.size)
                assertEquals(2, f.access.creates.size)
                assertEquals(2, f.access.prompts.size)
            } finally { f.close() }
        }
    }

    @Test fun capacitySkipAdvancesStrictlyFuturePersistsOnReloadAndNeverBackfillsAfterRelease() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        f.access.holdPrompts = true
        try {
            f.start()
            assertIs<ScheduledActivationResult.Saved>(f.executor.activate(taskB.id, taskB.revision, true))
            assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, "occupy"))
            runCurrent()
            assertEquals(ScheduledRunPhase.Dispatching, f.store.snapshot.runs.single().phase)
            assertEquals(1, f.access.prompts.size)
            f.now = hour
            f.executor.tick()
            val skipped = f.store.snapshot.cursors.single()
            assertEquals(hour, skipped.skippedThrough)
            assertEquals(2 * hour, skipped.nextNominalAt)
            assertTrue(skipped.nextNominalAt > f.now)
            assertEquals(ScheduledSkipReason.SkipCapacity, skipped.skipReason)
            assertEquals(skipped, f.store.reload().snapshot.cursors.single())
            assertEquals(1, f.store.snapshot.runs.size)
            val writes = f.store.writeAttempts
            repeat(3) { f.executor.tick() }
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(1, f.access.creates.size)
            assertEquals(1, f.access.prompts.size)

            f.access.promptGate.complete(Unit)
            runCurrent()
            assertEquals(ScheduledRunPhase.Succeeded, f.store.snapshot.runs.single().phase)
            f.executor.tick()
            assertEquals(1, f.store.snapshot.runs.size)
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
            val restarted = Fixture(this, f.store.reload(), maxConcurrentRuns = 1, initialNow = hour + 1)
            try {
                restarted.start(binding.copy(generation = 2))
                assertEquals(skipped, restarted.store.snapshot.cursors.single())
                repeat(2) { restarted.executor.tick() }
                assertTrue(restarted.access.creates.isEmpty())
                assertTrue(restarted.access.prompts.isEmpty())
                restarted.now = 2 * hour
                restarted.executor.tick()
                runCurrent()
                val due = restarted.store.snapshot.runs.single { it.task.id == taskB.id }
                assertEquals(2 * hour, due.nominalAt)
                assertEquals(ScheduledRunPhase.Succeeded, due.phase)
                assertEquals(terminal, due.terminal)
                assertEquals(1, restarted.access.creates.size)
                assertEquals(1, restarted.access.prompts.size)
                assertTrue(restarted.store.snapshot.runs.none { it.nominalAt == hour })
            } finally { restarted.close() }
        } finally { f.close() }
    }

    @Test fun oneTickSharesBudgetAcrossAllDueTasksBeforeDeliveryJobsRun() = runTest {
        val f = Fixture(this)
        f.access.holdCreates = true
        try {
            f.start()
            for (task in listOf(taskA, taskB, taskC)) {
                assertIs<ScheduledActivationResult.Saved>(f.executor.activate(task.id, task.revision, true))
            }
            f.now = hour
            val manual = async { f.executor.runNow(taskA.id, "manual-with-due") }
            val tick = async { f.executor.tick() }
            assertIs<ScheduledClaimResult.Claimed>(manual.await())
            tick.await()
            runCurrent()
            assertEquals(2, f.store.snapshot.runs.count { it.blocksTask })
            assertEquals(setOf(taskA.id, taskB.id), f.store.snapshot.runs.map { it.task.id }.toSet())
            val cursors = f.store.snapshot.cursors.associateBy { it.taskId }
            assertEquals(ScheduledSkipReason.SkipBusy, cursors.getValue(taskA.id).skipReason)
            assertEquals(ScheduledSkipReason.SkipCapacity, cursors.getValue(taskC.id).skipReason)
            assertTrue(cursors.values.all { it.nextNominalAt == 2 * hour })
            val writes = f.store.writeAttempts
            f.executor.tick()
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(2, f.access.creates.size)
            assertTrue(f.access.prompts.isEmpty())
        } finally { f.close() }
    }

    @Test fun busyAndLateSkipsAreNotMislabelledAsCapacityAndRestartRecordsItsOwnReason() = runTest {
        val active = listOf(taskA, taskB).map { it.copy(status = ScheduledTaskStatus.Active) }
        val snapshot = initial().copy(tasks = active, runs = listOf(uncertainRun(taskA)),
            cursors = active.map { ScheduledCursor(it.id, it.revision, hour) })
        val f = Fixture(this, StrictStore(snapshot), maxConcurrentRuns = 1)
        try {
            f.start()
            f.now = hour + 60_001
            f.executor.tick()
            val cursors = f.store.snapshot.cursors.associateBy { it.taskId }
            assertEquals(ScheduledSkipReason.SkipBusy, cursors.getValue(taskA.id).skipReason)
            assertEquals(ScheduledSkipReason.SkipLate, cursors.getValue(taskB.id).skipReason)
            assertTrue(cursors.values.all { it.skippedThrough == hour && it.nextNominalAt == 2 * hour })
            assertTrue(f.access.creates.isEmpty())
            assertTrue(f.access.prompts.isEmpty())
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
            val restarted = Fixture(this, f.store.reload(), maxConcurrentRuns = 1, initialNow = 2 * hour + 1)
            try {
                restarted.start(binding.copy(generation = 2))
                assertTrue(restarted.store.snapshot.cursors.all {
                    it.skipReason == ScheduledSkipReason.SkipRestart && it.skippedThrough == 2 * hour && it.nextNominalAt == 3 * hour
                })
                restarted.executor.tick()
                assertTrue(restarted.access.creates.isEmpty())
                assertTrue(restarted.access.prompts.isEmpty())
            } finally { restarted.close() }
        } finally { f.close() }
    }

    @Test fun claimUsesCanonicalReadRatherThanPublishedSnapshotOrOwnedJobs() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        try {
            f.start()
            assertTrue(f.executor.state.value.snapshot!!.runs.isEmpty())
            // Model a previous lifecycle's durable claim becoming visible on the next read.
            f.store.appendCompetingClaim(taskA, "external-reservation")
            assertTrue(f.executor.state.value.snapshot!!.runs.isEmpty())
            val writes = f.store.writeAttempts
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(taskB.id, "canonical-budget"))
            assertEquals(writes, f.store.writeAttempts)
            assertEquals(1, f.executor.state.value.snapshot!!.runs.count { it.blocksTask })
            assertTrue(f.access.creates.isEmpty())
            assertTrue(f.access.prompts.isEmpty())
        } finally { f.close() }
    }

    @Test fun conflictingCasCannotDispatchAfterAnotherWriterConsumesTheLastSlot() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            f.start()
            f.store.beforeWrite = {
                f.store.beforeWrite = null
                entered.complete(Unit)
                release.await()
            }
            val claim = async { f.executor.runNow(taskA.id, "losing-claim") }
            entered.await()
            f.store.appendCompetingClaim(taskB, "winning-claim")
            val competing = f.store.snapshot
            release.complete(Unit)
            assertIs<ScheduledClaimResult.Refused>(claim.await())
            runCurrent()
            assertEquals(competing, f.store.snapshot)
            assertEquals(taskB.id, f.store.snapshot.runs.single().task.id)
            assertFalse(f.executor.state.value.accepting)
            assertTrue(f.access.creates.isEmpty())
            assertTrue(f.access.prompts.isEmpty())
        } finally { release.complete(Unit); f.close() }
    }

    @Test fun failedCapacitySkipCasStopsAdmissionAndCannotDispatchOrOverwriteCompetingLedger() = runTest {
        val f = Fixture(this, maxConcurrentRuns = 1)
        f.access.holdCreates = true
        try {
            f.start()
            assertIs<ScheduledActivationResult.Saved>(f.executor.activate(taskB.id, taskB.revision, true))
            assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskA.id, "full"))
            runCurrent()
            f.now = hour
            f.store.beforeWrite = {
                f.store.beforeWrite = null
                f.store.editTask(taskC.id)
            }
            f.executor.tick()
            assertFalse(f.executor.state.value.accepting)
            assertEquals(hour, f.store.snapshot.cursors.single().nextNominalAt)
            assertNull(f.store.snapshot.cursors.single().skipReason)
            assertEquals("competing edit", f.store.snapshot.tasks.single { it.id == taskC.id }.name)
            assertEquals(1, f.store.snapshot.runs.size)
            assertEquals(listOf(taskA.id), f.access.creates.map { it.task.id })
            assertTrue(f.access.prompts.isEmpty())
        } finally { f.close() }
    }

    @Test fun withdrawnAuditDoesNotConsumeCapacityAfterReload() = runTest {
        val withdrawn = uncertainRun(taskA).copy(phase = ScheduledRunPhase.Withdrawn,
            sessionRef = SessionId("prior-a"), deliveryIntent = true, detail = "prompt was never invoked")
        val f = Fixture(this, StrictStore(initial().copy(runs = listOf(withdrawn))).reload(), maxConcurrentRuns = 1)
        try {
            f.start()
            assertFalse(f.store.snapshot.runs.single().blocksTask)
            assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(taskB.id, "after-withdrawal"))
            runCurrent()
            assertEquals(withdrawn, f.store.snapshot.runs.first())
            assertEquals(ScheduledRunPhase.Succeeded, f.store.snapshot.runs.last().phase)
            assertEquals(listOf(taskB.id), f.access.prompts.map { it.task.id })
        } finally { f.close() }
    }

    @Test fun positiveBudgetAndLegacyCursorDefaultsAreExplicit() {
        assertEquals(2, ScheduledExecutorConfig().maxConcurrentRuns)
        assertFalse(ScheduledExecutorConfig().verifiedCronExecution)
        assertEquals(1, ScheduledExecutorConfig(maxConcurrentRuns = 1).maxConcurrentRuns)
        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException> { ScheduledExecutorConfig(maxConcurrentRuns = invalid) }
        }
        val legacy = Json.decodeFromString(ScheduledCursor.serializer(),
            """{"taskId":"a","taskRevision":1,"nextNominalAt":3600000,"skippedThrough":0}""")
        assertNull(legacy.skipReason)
        assertEquals(0L, legacy.skippedThrough)
    }

    private class Fixture(
        test: TestScope, val store: StrictStore = StrictStore(initial()), maxConcurrentRuns: Int = 2,
        initialNow: Long = 1_000,
    ) {
        val access = ControlledAccess()
        private val lease = TestLease()
        var now = initialNow
        val executor = ScheduledExecutor(store, ScheduleEngine(HourlyCalendar), access, lease,
            test.backgroundScope, ScheduledClock { now }, ScheduledExecutorConfig(
                verifiedCronExecution = true, tickIntervalMillis = 60_000, evidencePollMillis = 5,
                observationTimeoutMillis = 20, reconciliationInitialDelayMillis = 10,
                reconciliationMaxDelayMillis = 40, maxConcurrentRuns = maxConcurrentRuns))

        suspend fun start(scope: ScheduledScope = binding) {
            assertEquals(ScheduledStartResult.Started, executor.start(scope))
        }

        suspend fun close() {
            store.beforeWrite = null
            access.createGate.complete(Unit)
            access.promptGate.complete(Unit)
            access.result = ScheduledEvidenceResult.Terminal(terminal)
            assertEquals(ScheduledShutdownResult.Drained, executor.shutdown())
            assertFalse(lease.isHeld())
        }
    }

    /** Each read decodes independent canonical data; each accepted write validates real ledger rules. */
    private class StrictStore(initial: ScheduledSnapshot) : ScheduledStore {
        private var encoded = Json.encodeToString(ScheduledSnapshot.serializer(), initial)
        val snapshot: ScheduledSnapshot get() = Json.decodeFromString(ScheduledSnapshot.serializer(), encoded)
        val commits = mutableListOf<ScheduledSnapshot>()
        var writeAttempts = 0
        var beforeWrite: (suspend () -> Unit)? = null

        init { assertNull(ScheduledValidator.snapshotError(initial)) }

        fun reload(): StrictStore = StrictStore(snapshot)

        override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
            assertEquals(snapshot.owner, owner)
            return ScheduledReadResult.Ready(snapshot)
        }

        override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long,
            snapshot: ScheduledSnapshot, isCurrentScope: () -> Boolean): ScheduledWriteResult {
            writeAttempts++
            beforeWrite?.invoke()
            if (!isCurrentScope()) return ScheduledWriteResult.StaleScope
            val old = this.snapshot
            if (owner != old.owner || expectedRevision != old.revision) return ScheduledWriteResult.Failed("CAS conflict")
            persist(old, snapshot)
            return ScheduledWriteResult.Committed(this.snapshot)
        }

        fun appendCompetingClaim(task: ScheduledTask, requestId: String) {
            val old = snapshot
            val run = ScheduledRun("external-$requestId", task, requestId, null, 1_000,
                SessionId("external-session-$requestId"), "external-input-$requestId")
            persist(old, old.copy(revision = old.revision + 1, runs = old.runs + run))
        }

        fun editTask(taskId: String) {
            val old = snapshot
            persist(old, old.copy(revision = old.revision + 1, tasks = old.tasks.map {
                if (it.id == taskId) it.copy(revision = it.revision + 1, name = "competing edit") else it
            }))
        }

        private fun persist(old: ScheduledSnapshot, next: ScheduledSnapshot) {
            assertEquals(old.revision + 1, next.revision)
            assertNull(ScheduledValidator.snapshotError(next))
            assertTrue(ScheduledLedgerRules.validTransition(old, next))
            encoded = Json.encodeToString(ScheduledSnapshot.serializer(), next)
            commits += snapshot
        }
    }

    private class ControlledAccess : ScheduledSessionAccess {
        val creates = mutableListOf<ScheduledRun>()
        val prompts = mutableListOf<ScheduledRun>()
        val createGate = CompletableDeferred<Unit>()
        val promptGate = CompletableDeferred<Unit>()
        var holdCreates = false
        var holdPrompts = false
        var failCreate = false
        var result: ScheduledEvidenceResult = ScheduledEvidenceResult.Terminal(terminal)

        override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? = null
        override suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId {
            creates += run
            if (holdCreates) createGate.await()
            if (failCreate) error("uncertain create outcome")
            return run.candidateSessionId
        }
        override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt {
            prompts += run
            if (holdPrompts) promptGate.await()
            return SessionPromptReceipt(requireNotNull(run.sessionRef), "receipt-${run.id}")
        }
        override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult = result
    }

    private class TestLease : ScheduledLeaderLease {
        private var held = false
        override suspend fun acquire(scope: ScheduledScope): Boolean {
            if (held) return false
            held = true
            return true
        }
        override fun isHeld(): Boolean = held
        override suspend fun release() { held = false }
    }

    private object HourlyCalendar : ScheduleCalendar {
        override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
        override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = error("hourly does not use wall time")
        override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = error("hourly does not use wall time")
        override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = error("hourly does not use wall time")
    }

    private companion object {
        const val hour = 3_600_000L
        val binding = ScheduledScope(HostId("capacity-test-local"), null, 1)
        val taskA = task("a")
        val taskB = task("b")
        val taskC = task("c")
        val terminal = ScheduledTerminalEvidence(7, 10, 18, 2_000, ScheduledRunPhase.Succeeded, "completed")

        fun task(id: String) = ScheduledTask(id, 1, "Task $id", "prompt $id",
            ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Hourly(1, 0),
            ScheduledModelRef("controlled-provider", "controlled-model"))
        fun initial() = ScheduledSnapshot(binding.owner, 5, listOf(taskA, taskB, taskC))
        fun uncertainRun(task: ScheduledTask) = ScheduledRun("prior-${task.id}", task, "prior-request-${task.id}",
            null, 1_000, SessionId("prior-${task.id}"), "prior-input-${task.id}",
            ScheduledRunPhase.Unknown, detail = "create outcome is uncertain")
    }
}

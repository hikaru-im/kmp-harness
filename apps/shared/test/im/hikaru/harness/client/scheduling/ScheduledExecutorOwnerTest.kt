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
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledExecutorOwnerTest {
    @Test fun readFailureRecoversOnlyAfterReleaseAndKeepsTheCompleteBinding() = runTest {
        val f = Fixture(this)
        try {
            val original = f.owner.state.value.executor
            assertNull(f.owner.state.value.binding)
            assertEquals(1, f.factoryCalls)
            f.store.failure = ScheduledReadResult.IoFailure("startup read failed")
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            assertSame(original, f.owner.state.value.executor)
            assertTrue(f.leases.single().isHeld())
            assertNotNull(f.owner.state.value.error)
            f.store.failure = null
            assertEquals(ScheduledStartResult.Started, f.owner.recover(binding))
            assertNotSame(original, f.owner.state.value.executor)
            assertEquals(binding, f.owner.state.value.binding)
            assertTrue(f.owner.state.value.executor.state.value.accepting)
            assertFalse(f.owner.state.value.busy)
            assertNull(f.owner.state.value.error)
            assertEquals(listOf("factory:1", "acquire:1", "release:1", "factory:2", "acquire:2"), f.events)
            assertEquals(ScheduledStartResult.Started, f.owner.start(binding))
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertEquals(2, f.factoryCalls)
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun wrongScopeAndRepeatedInitialStartCannotReplaceOrRebind() = runTest {
        val f = Fixture(this)
        try {
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            f.failInitialRead()
            val original = f.owner.state.value.executor
            for (other in listOf(binding.copy(generation = 2), binding.copy(hostId = HostId("other-host")))) {
                assertIs<ScheduledStartResult.Unavailable>(f.owner.start(other))
                assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(other))
            }
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            assertSame(original, f.owner.state.value.executor)
            assertEquals(binding, f.owner.state.value.binding)
            assertEquals(1, f.factoryCalls)
            assertEquals(0, f.leases.single().releases)
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun unreadableLedgerRetainsTheFailedInstanceAndLeaseUntilExplicitRetry() = runTest {
        for (failure in listOf(ScheduledReadResult.Invalid("bad ledger"), ScheduledReadResult.UnsupportedVersion(999))) {
            val f = Fixture(this)
            try {
                f.store.failure = failure
                assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
                val original = f.owner.state.value.executor
                assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding, 5))
                assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding, 5))
                assertSame(original, f.owner.state.value.executor)
                assertEquals(1, f.factoryCalls)
                assertTrue(f.leases.single().isHeld())
                assertEquals(0, f.leases.single().releases)
                assertNotNull(f.owner.state.value.error)
                assertFalse(f.owner.state.value.busy)
                f.noDelivery()
                f.store.failure = null
                assertEquals(ScheduledStartResult.Started, f.owner.recover(binding))
            } finally { f.close() }
        }
    }

    @Test fun unresolvedUnknownNeverReplacesOrReplaysAndRepeatedShutdownCanRecheckEvidence() = runTest {
        val f = Fixture(this)
        try {
            val prior = ScheduledRun("prior-run", task, "prior-request", null, 1_000,
                SessionId("prior-session"), "prior-input", ScheduledRunPhase.Unknown,
                SessionId("prior-session"), "prior-receipt", detail = "old diagnostic", deliveryIntent = true)
            f.store.snapshot = f.store.snapshot.copy(runs = listOf(prior))
            f.store.failWrites = true
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            f.store.failWrites = false
            val original = f.owner.state.value.executor
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertSame(original, f.owner.state.value.executor)
            assertEquals(1, f.factoryCalls)
            assertEquals(prior, f.store.snapshot.runs.single())
            assertTrue(f.leases.single().isHeld())
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.owner.shutdown(5))
            assertTrue(f.owner.state.value.stopped)
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            f.access.evidence = ScheduledEvidenceResult.Terminal(terminal)
            assertEquals(ScheduledShutdownResult.Drained, f.owner.shutdown())
            assertEquals(terminal, f.store.snapshot.runs.single().terminal)
            assertFalse(f.leases.single().isHeld())
            assertEquals(1, f.factoryCalls)
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun invalidateWhileRecoveryReleaseIsSuspendedPermanentlyFencesTheOwner() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val original = f.owner.state.value.executor
            val gate = f.gate()
            f.leases.single().beforeRelease = { gate.waitForRelease() }
            val recovery = async { f.owner.recover(binding) }
            gate.awaitEntry()
            assertTrue(f.owner.state.value.busy)
            f.owner.invalidate()
            assertTrue(f.owner.state.value.invalidated)
            assertFalse(original.state.value.accepting)
            assertEquals(1, f.factoryCalls)
            gate.release.complete(Unit)
            assertIs<ScheduledStartResult.Unavailable>(recovery.await())
            assertSame(original, f.owner.state.value.executor)
            assertEquals(binding, f.owner.state.value.binding)
            assertEquals(1, f.factoryCalls)
            assertFalse(f.owner.state.value.busy)
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertEquals(ScheduledShutdownResult.Drained, f.owner.shutdown())
            assertTrue(f.owner.state.value.invalidated)
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun shutdownSetsStopBeforeWaitingForRecoveryAndCanBeExplicitlyRetried() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val original = f.owner.state.value.executor
            val gate = f.gate()
            f.leases.single().beforeRelease = { gate.waitForRelease() }
            val recovery = async { f.owner.recover(binding) }
            gate.awaitEntry()
            val shutdown = async { f.owner.shutdown(5) }
            runCurrent()
            assertTrue(f.owner.state.value.stopped)
            assertTrue(f.owner.state.value.busy)
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(shutdown.await())
            assertTrue(f.leases.single().isHeld())
            gate.release.complete(Unit)
            assertIs<ScheduledStartResult.Unavailable>(recovery.await())
            assertSame(original, f.owner.state.value.executor)
            assertEquals(1, f.factoryCalls)
            assertEquals(ScheduledShutdownResult.Drained, f.owner.shutdown())
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun timeoutObservesReleaseWithoutCancellingItOrAutomaticallyConstructingAReplacement() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val original = f.owner.state.value.executor
            val gate = f.gate()
            var releaseReturned = false
            f.leases.single().beforeRelease = { gate.waitForRelease(); releaseReturned = true }
            val recovery = async { f.owner.recover(binding, 5) }
            gate.awaitEntry()
            assertIs<ScheduledStartResult.Unavailable>(recovery.await())
            assertSame(original, f.owner.state.value.executor)
            assertTrue(f.leases.single().isHeld())
            assertEquals(1, f.factoryCalls)
            gate.release.complete(Unit)
            runCurrent()
            assertTrue(releaseReturned)
            assertFalse(f.leases.single().isHeld())
            assertEquals(1, f.factoryCalls)
            assertSame(original, f.owner.state.value.executor)
            assertEquals(ScheduledStartResult.Started, f.owner.recover(binding))
            assertEquals(2, f.factoryCalls)
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun releaseExceptionKeepsTheInstanceAndFactoryExceptionKeepsTheDrainedPredecessor() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val original = f.owner.state.value.executor
            f.leases.single().beforeRelease = { error("release failed") }
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertSame(original, f.owner.state.value.executor)
            assertTrue(f.leases.single().isHeld())
            assertEquals(1, f.factoryCalls)
            f.leases.single().beforeRelease = null
            f.beforeFactory = { error("factory failed") }
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertSame(original, f.owner.state.value.executor)
            assertFalse(f.leases.single().isHeld())
            assertTrue(f.owner.state.value.error!!.contains("factory failed"))
            f.beforeFactory = null
            assertEquals(ScheduledStartResult.Started, f.owner.recover(binding))
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun concurrentRecoverDoesNotQueueASecondReplacement() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val gate = f.gate()
            f.leases.single().beforeRelease = { gate.waitForRelease() }
            val first = async { f.owner.recover(binding) }
            gate.awaitEntry()
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            assertEquals(1, f.factoryCalls)
            gate.release.complete(Unit)
            assertEquals(ScheduledStartResult.Started, first.await())
            assertEquals(2, f.factoryCalls)
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun factoryInvalidationRetiresConstructedReplacementWithoutStartingOrPublishingIt() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val original = f.owner.state.value.executor
            f.afterFactory = { f.owner.invalidate() }
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertSame(original, f.owner.state.value.executor)
            assertTrue(f.owner.state.value.invalidated)
            assertEquals(2, f.factoryCalls)
            assertEquals(0, f.leases.last().acquires)
            assertIs<ScheduledStartResult.Unavailable>(f.executors.last().start(binding))
            assertEquals(ScheduledShutdownResult.Drained, f.owner.shutdown())
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun suspendedReplacementValidationIsFencedAndRetainedForShutdownWithoutLateAdmission() = runTest {
        for (invalidate in listOf(true, false)) {
            val f = Fixture(this)
            try {
                f.failInitialRead()
                val original = f.owner.state.value.executor
                val gate = f.gate()
                var validationReturned = false
                f.access.beforeValidation = { gate.waitForRelease(); validationReturned = true }
                val recovery = async { f.owner.recover(binding) }
                gate.awaitEntry()
                assertEquals(2, f.factoryCalls)
                assertEquals(0, f.leases.last().acquires)
                if (invalidate) f.owner.invalidate()
                else {
                    val shutdown = async { f.owner.shutdown(5) }
                    runCurrent()
                    assertTrue(f.owner.state.value.stopped)
                    assertIs<ScheduledShutdownResult.UnsafeToRelease>(shutdown.await())
                }
                assertSame(original, f.owner.state.value.executor)
                gate.release.complete(Unit)
                assertIs<ScheduledStartResult.Unavailable>(recovery.await())
                assertTrue(validationReturned)
                assertSame(original, f.owner.state.value.executor)
                assertEquals(binding, f.owner.state.value.binding)
                assertEquals(0, f.leases.last().acquires)
                assertFalse(f.executors.last().state.value.accepting)
                assertIs<ScheduledStartResult.Unavailable>(f.executors.last().start(binding))
                assertEquals(ScheduledShutdownResult.Drained, f.owner.shutdown())
                f.noDelivery()
            } finally { f.close() }
        }
    }

    @Test fun cancelledReplacementAwaitRetainsUnsafeInstanceUntilItsOwnedStartReturns() = runTest {
        val f = Fixture(this)
        try {
            f.failInitialRead()
            val original = f.owner.state.value.executor
            val gate = f.gate()
            var validationReturned = false
            f.access.beforeValidation = { gate.waitForRelease(); validationReturned = true }
            val recovery = async { f.owner.recover(binding, 5) }
            gate.awaitEntry()
            recovery.cancel()
            recovery.join()
            assertSame(original, f.owner.state.value.executor)
            assertFalse(validationReturned)
            assertNotNull(f.owner.state.value.error)
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.owner.shutdown(5))
            assertEquals(2, f.factoryCalls)
            gate.release.complete(Unit)
            runCurrent()
            assertTrue(validationReturned)
            assertFalse(f.executors.last().state.value.accepting)
            assertEquals(0, f.leases.last().acquires)
            assertEquals(ScheduledShutdownResult.Drained, f.owner.shutdown())
            f.noDelivery()
        } finally { f.close() }
    }

    @Test fun defaultVerificationGateRemainsUnavailableAcrossExplicitRecovery() = runTest {
        val f = Fixture(this, verified = false)
        try {
            assertIs<ScheduledStartResult.Unavailable>(f.owner.start(binding))
            assertIs<ScheduledStartResult.Unavailable>(f.owner.recover(binding))
            assertEquals(2, f.factoryCalls)
            assertEquals(binding, f.owner.state.value.binding)
            assertFalse(f.owner.state.value.executor.state.value.accepting)
            assertNotNull(f.owner.state.value.error)
            assertEquals(0, f.store.reads)
            assertEquals(0, f.access.validations)
            assertTrue(f.leases.all { it.acquires == 0 && !it.isHeld() })
            f.noDelivery()
        } finally { f.close() }
    }

    private class Fixture(private val test: TestScope, private val verified: Boolean = true) {
        val events = mutableListOf<String>()
        val store = ControlledStore()
        val access = ControlledAccess()
        val leases = mutableListOf<TestLease>()
        val executors = mutableListOf<ScheduledExecutor>()
        private val gates = mutableListOf<Gate>()
        private val hub = LeaseHub()
        var factoryCalls = 0
        var beforeFactory: (() -> Unit)? = null
        var afterFactory: (() -> Unit)? = null
        val owner = ScheduledExecutorOwner {
            factoryCalls++
            beforeFactory?.invoke()
            events += "factory:" + factoryCalls
            val lease = TestLease(hub, factoryCalls, events)
            leases += lease
            val executor = ScheduledExecutor(store, ScheduleEngine(HourlyCalendar), access, lease,
                test.backgroundScope, ScheduledClock { 1_000 },
                if (verified) ScheduledExecutorConfig(verifiedCronExecution = true, tickIntervalMillis = 60_000)
                else ScheduledExecutorConfig())
            executors += executor
            afterFactory?.invoke()
            executor
        }

        fun gate(): Gate = Gate().also { gates += it }
        suspend fun failInitialRead() {
            store.failure = ScheduledReadResult.IoFailure("startup read failed")
            assertIs<ScheduledStartResult.Unavailable>(owner.start(binding))
            store.failure = null
            assertTrue(leases.single().isHeld())
            assertFalse(owner.state.value.executor.state.value.accepting)
        }
        fun noDelivery() {
            assertEquals(0, access.creates)
            assertEquals(0, access.prompts)
        }
        suspend fun close() {
            gates.forEach { it.release.complete(Unit) }
            leases.forEach { it.beforeRelease = null }
            access.beforeValidation = null
            access.evidence = ScheduledEvidenceResult.Terminal(terminal)
            store.failure = null
            store.failWrites = false
            assertEquals(ScheduledShutdownResult.Drained, owner.shutdown())
            assertTrue(leases.all { !it.isHeld() })
            noDelivery()
        }
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        suspend fun waitForRelease() { entered.complete(Unit); release.await() }
        suspend fun awaitEntry() { withTimeout(1_000) { entered.await() } }
    }

    private class ControlledStore : ScheduledStore {
        var snapshot = ScheduledSnapshot(binding.owner, revision = 5, tasks = listOf(task))
        var failure: ScheduledReadResult? = null
        var failWrites = false
        var reads = 0
        override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
            assertEquals(binding.owner, owner)
            reads++
            return failure ?: ScheduledReadResult.Ready(snapshot)
        }
        override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long,
            snapshot: ScheduledSnapshot, isCurrentScope: () -> Boolean): ScheduledWriteResult {
            if (!isCurrentScope()) return ScheduledWriteResult.StaleScope
            if (failWrites || expectedRevision != this.snapshot.revision) return ScheduledWriteResult.Failed("test CAS failed")
            assertEquals(owner, snapshot.owner)
            assertNull(ScheduledValidator.snapshotError(snapshot))
            assertTrue(ScheduledLedgerRules.validTransition(this.snapshot, snapshot))
            this.snapshot = snapshot
            return ScheduledWriteResult.Committed(snapshot)
        }
    }

    private class ControlledAccess : ScheduledSessionAccess {
        var validations = 0
        var creates = 0
        var prompts = 0
        var beforeValidation: (suspend () -> Unit)? = null
        var evidence: ScheduledEvidenceResult = ScheduledEvidenceResult.Pending
        override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? {
            assertEquals(binding, scope)
            validations++
            beforeValidation?.invoke()
            return null
        }
        override suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId {
            creates++
            error("Owner recovery must never create a session")
        }
        override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt {
            prompts++
            error("Owner recovery must never prompt")
        }
        override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult {
            assertEquals(binding, scope)
            return evidence
        }
    }

    private class LeaseHub { var held: TestLease? = null }
    private class TestLease(private val hub: LeaseHub, private val id: Int,
        private val events: MutableList<String>) : ScheduledLeaderLease {
        var acquires = 0
        var releases = 0
        var beforeRelease: (suspend () -> Unit)? = null
        override suspend fun acquire(scope: ScheduledScope): Boolean {
            assertEquals(binding, scope)
            acquires++
            events += "acquire:" + id
            if (hub.held != null) return false
            hub.held = this
            return true
        }
        override fun isHeld(): Boolean = hub.held === this
        override suspend fun release() {
            assertTrue(isHeld())
            beforeRelease?.invoke()
            events += "release:" + id
            releases++
            hub.held = null
        }
    }

    private object HourlyCalendar : ScheduleCalendar {
        override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
        override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = error("Hourly does not use a wall calendar")
        override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = error("Hourly does not use a wall calendar")
        override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = error("Hourly does not use a wall calendar")
    }

    private companion object {
        val binding = ScheduledScope(HostId("test-host"), null, 1)
        val task = ScheduledTask("test-task", 1, "Test Cron", "test prompt",
            ScheduledTarget.Cron(ScheduledDestination.Cwd("/test/workspace")), ScheduledRule.Hourly(1, 0),
            ScheduledModelRef("test-provider", "test-model"))
        val terminal = ScheduledTerminalEvidence(7, 10, 18, 2_000, ScheduledRunPhase.Succeeded, "completed")
    }
}

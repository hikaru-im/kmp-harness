package im.hikaru.harness.client.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Scripted services are test-only; these tests exercise the production connection adapter.
 * The strict store models durable CAS; filesystem fsync and live Host evidence remain JVM gates. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledExecutorTest {
    @Test fun cronDeliversTypedInputOnceAndPersistsExactTerminalBeforeRelease() = runTest {
        val f = Fixture(this)
        assertEquals(ScheduledStartResult.Started, f.executor.start(binding))
        f.session.beforeCreate = { id, options, agent ->
            val run = f.store.snapshot.runs.single()
            assertEquals(ScheduledRunPhase.CreatingSession, run.phase)
            assertEquals(run.candidateSessionId, id)
            assertEquals("/test/workspace", options.cwd)
            assertEquals("test-provider", agent.provider)
            assertEquals("test-model", agent.model)
            assertEquals(options.cwd, agent.cwd)
        }
        f.session.beforePrompt = { id, message ->
            val run = f.store.snapshot.runs.single()
            assertEquals(ScheduledRunPhase.Dispatching, run.phase)
            assertTrue(run.deliveryIntent)
            assertEquals(run.sessionRef, id)
            assertEquals(run.inputMessageId, message.id)
            assertEquals(MessageRole.USER, message.role)
            assertEquals(UserMessageSource, message.source)
            assertEquals(listOf(TextContent(task.prompt)), message.content)
            assertEquals(1, f.evidence.createdFlushes)
        }
        assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, "manual-1"))
        runCurrent()
        val saved = f.store.snapshot.runs.single()
        assertEquals(ScheduledRunPhase.Succeeded, saved.phase)
        assertEquals(exactTerminal, saved.terminal)
        assertEquals("inbox-receipt", saved.receiptId)
        assertNotEquals(saved.inputMessageId, saved.receiptId)
        assertEquals(1, f.session.creates)
        assertEquals(1, f.session.prompts)
        assertEquals(0, f.session.idleCalls)
        assertEquals(listOf(ScheduledRunPhase.Claimed, ScheduledRunPhase.CreatingSession,
            ScheduledRunPhase.SessionCreated, ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted,
            ScheduledRunPhase.Succeeded), f.store.commits.map { it.runs.single().phase })
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertFalse(f.lease.isHeld())
    }

    @Test fun sameRequestDeduplicatesBothWhileDeliveryIsSuspendedAndAfterTerminal() = runTest {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.session.beforePrompt = { _, _ -> entered.complete(Unit); release.await() }
        f.executor.start(binding)
        val first = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, "stable-request"))
        entered.await()
        val duplicate = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, "stable-request"))
        assertTrue(duplicate.duplicate)
        assertEquals(first.run.id, duplicate.run.id)
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "another-request"))
        assertEquals(1, f.store.snapshot.runs.size)
        release.complete(Unit)
        runCurrent()
        assertTrue(assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, "stable-request")).duplicate)
        assertEquals(1, f.session.prompts)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun casConflictKeepsCompetingRevisionAndNeverCreatesOrPrompts() = runTest {
        val f = Fixture(this)
        f.executor.start(binding)
        f.store.beforeWrite = {
            f.store.beforeWrite = null
            f.store.snapshot = f.store.snapshot.copy(revision = 2, tasks = listOf(task.copy(revision = 2, name = "competing edit")))
        }
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "conflict"))
        runCurrent()
        assertEquals("competing edit", f.store.snapshot.tasks.single().name)
        assertTrue(f.store.snapshot.runs.isEmpty())
        assertEquals(0, f.session.creates)
        assertEquals(0, f.session.prompts)
        assertFalse(f.executor.state.value.accepting)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun restartUnknownRetainsEvidenceAndNeverRedeliversEvenWithNewRequest() = runTest {
        val prior = priorRun(ScheduledRunPhase.Unknown).copy(detail = "old observer diagnostic")
        val f = Fixture(this, initial(prior))
        f.evidence.result = ScheduledEvidenceResult.Pending
        assertEquals(ScheduledStartResult.Started, f.executor.start(binding.copy(generation = 2)))
        val recovered = f.store.snapshot.runs.single()
        assertEquals(ScheduledRunPhase.Unknown, recovered.phase)
        assertNotEquals(prior.detail, recovered.detail)
        assertEquals(prior.copy(detail = recovered.detail), recovered)
        assertTrue(assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, prior.requestId)).duplicate)
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "new-request"))
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown())
        assertTrue(f.lease.isHeld())
        f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertEquals(0, f.session.creates)
        assertEquals(0, f.session.prompts)
    }

    @Test fun restartReconcilesExactTerminalWithoutReceiptOrResend() = runTest {
        val prior = priorRun(ScheduledRunPhase.Unknown).copy(receiptId = null)
        val f = Fixture(this, initial(prior))
        assertEquals(ScheduledStartResult.Started, f.executor.start(binding))
        val repaired = f.store.snapshot.runs.single()
        assertEquals(ScheduledRunPhase.Succeeded, repaired.phase)
        assertEquals(exactTerminal, repaired.terminal)
        assertNull(repaired.receiptId)
        assertTrue(assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, prior.requestId)).duplicate)
        assertEquals(0, f.session.prompts)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun leaseContenderCannotReadClaimOrReleaseLeadersLease() = runTest {
        val hub = LeaseHub()
        val first = Fixture(this, hub = hub)
        val contender = Fixture(this, hub = hub)
        assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
        assertIs<ScheduledStartResult.Unavailable>(contender.executor.start(binding))
        assertEquals(0, contender.store.reads)
        assertIs<ScheduledClaimResult.Refused>(contender.executor.runNow(task.id, "contender"))
        assertEquals(ScheduledShutdownResult.Drained, contender.executor.shutdown())
        assertTrue(first.lease.isHeld())
        assertEquals(0, contender.lease.releases)
        assertEquals(ScheduledShutdownResult.Drained, first.executor.shutdown())
        assertEquals(1, first.lease.releases)
    }

    @Test fun suspendedPromptMakesShutdownUnsafeAndLateExactTerminalPermitsRelease() = runTest {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.session.beforePrompt = { _, _ -> entered.complete(Unit); release.await() }
        f.executor.start(binding)
        f.executor.runNow(task.id, "suspended-delivery")
        entered.await()
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        assertTrue(f.lease.isHeld())
        assertEquals(0, f.lease.releases)
        assertEquals(ScheduledRunPhase.Dispatching, f.store.snapshot.runs.single().phase)
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "after-stop"))
        release.complete(Unit)
        runCurrent()
        assertEquals(ScheduledRunPhase.Succeeded, f.store.snapshot.runs.single().phase)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        assertEquals(1, f.lease.releases)
    }

    @Test fun repeatedConcurrentShutdownReconcilesUnknownAfterInvalidationWithoutPublishingOwner() = runTest {
        val f = Fixture(this)
        f.evidence.result = ScheduledEvidenceResult.Unavailable("test evidence offline")
        f.executor.start(binding)
        f.executor.runNow(task.id, "unknown-delivery")
        runCurrent()
        assertEquals(ScheduledRunPhase.Unknown, f.store.snapshot.runs.single().phase)
        f.executor.invalidate()
        assertNull(f.executor.state.value.scope)
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.evidence.beforeInspect = { entered.complete(Unit); release.await() }
        f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
        val inspectCount = f.evidence.inspections
        val first = async { f.executor.shutdown(5) }
        entered.await()
        val second = async { f.executor.shutdown(5) }
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(first.await())
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(second.await())
        assertEquals(inspectCount + 1, f.evidence.inspections)
        assertTrue(f.lease.isHeld())
        release.complete(Unit)
        runCurrent()
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertNull(f.executor.state.value.scope)
        assertNull(f.executor.state.value.snapshot)
        assertEquals(1, f.session.prompts)
        assertEquals(1, f.lease.releases)
    }

    @Test fun failedTerminalCommitRetainsLeaseUntilDurableReconciliationSucceeds() = runTest {
        val f = Fixture(this)
        f.store.reject = { it.runs.any { run -> run.terminal != null } }
        f.executor.start(binding)
        f.executor.runNow(task.id, "terminal-write-failure")
        runCurrent()
        assertNull(f.store.snapshot.runs.single().terminal)
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        assertTrue(f.lease.isHeld())
        f.store.reject = { false }
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertEquals(1, f.session.prompts)
    }

    @Test fun shutdownDuringClaimValidationFencesLateClaimWithoutCancellingValidation() = runTest {
        val f = Fixture(this)
        f.executor.start(binding)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.evidence.beforeValidation = { current -> if (current != null) { entered.complete(Unit); release.await() } }
        val claiming = async { f.executor.runNow(task.id, "validation-race") }
        entered.await()
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        assertTrue(f.lease.isHeld())
        release.complete(Unit)
        assertIs<ScheduledClaimResult.Refused>(claiming.await())
        assertTrue(f.store.snapshot.runs.isEmpty())
        assertEquals(0, f.session.creates)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
    }

    @Test fun invalidateDuringActivationValidationFencesLateActivationAndKeepsPausedTask() = runTest {
        val f = Fixture(this)
        f.executor.start(binding)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.evidence.beforeValidation = { current -> if (current != null) { entered.complete(Unit); release.await() } }
        val activating = async { f.executor.activate(task.id, task.revision, true) }
        entered.await()
        f.executor.invalidate()
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        release.complete(Unit)
        assertIs<ScheduledActivationResult.Refused>(activating.await())
        assertEquals(task, f.store.snapshot.tasks.single())
        assertTrue(f.store.snapshot.cursors.isEmpty())
        assertNull(f.executor.state.value.scope)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
    }

    @Test fun stoppedClaimAndActivationAreFencedInsideSuspendedStoreCas() = runTest {
        for (activation in listOf(false, true)) {
            val f = Fixture(this)
            f.executor.start(binding)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.store.beforeWrite = { entered.complete(Unit); release.await() }
            val changing = async {
                if (activation) assertIs<ScheduledActivationResult.Refused>(f.executor.activate(task.id, 1, true))
                else assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "cas-stop"))
            }
            entered.await()
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
            release.complete(Unit)
            changing.await()
            assertEquals(1L, f.store.snapshot.revision)
            assertTrue(f.store.snapshot.runs.isEmpty())
            assertEquals(ScheduledTaskStatus.Paused, f.store.snapshot.tasks.single().status)
            assertEquals(0, f.session.creates)
            f.store.beforeWrite = null
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        }
    }

    @Test fun shutdownDuringSuspendedCreateNeverPromptsAfterCreateReturns() = runTest {
        val f = Fixture(this)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.session.beforeCreate = { _, _, _ -> entered.complete(Unit); release.await() }
        f.executor.start(binding)
        f.executor.runNow(task.id, "create-stop")
        entered.await()
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        release.complete(Unit)
        runCurrent()
        val run = f.store.snapshot.runs.single()
        assertEquals(ScheduledRunPhase.Rejected, run.phase)
        assertNotNull(run.sessionRef)
        assertFalse(run.deliveryIntent)
        assertEquals(0, f.session.prompts)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
    }

    @Test fun activationAndDueTickShareDurableOccurrenceAndSkipMissedWakeups() = runTest {
        val f = Fixture(this)
        f.executor.start(binding)
        assertIs<ScheduledActivationResult.Saved>(f.executor.activate(task.id, 1, true))
        val nominal = f.store.snapshot.cursors.single().nextNominalAt
        f.now = nominal
        f.executor.tick()
        runCurrent()
        f.executor.tick()
        runCurrent()
        assertEquals(1, f.session.prompts)
        assertEquals(nominal, f.store.snapshot.runs.single().nominalAt)
        val missed = f.store.snapshot.cursors.single().nextNominalAt
        f.now = missed + 61_000
        f.executor.tick()
        runCurrent()
        assertEquals(1, f.session.prompts)
        assertEquals(missed, f.store.snapshot.cursors.single().skippedThrough)
        assertTrue(f.store.snapshot.cursors.single().nextNominalAt > f.now)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun capabilityDefaultStaysUnavailableWithoutAcquiringLeaseOrReadingStore() = runTest {
        val f = Fixture(this, verified = false)
        assertIs<ScheduledStartResult.Unavailable>(f.executor.start(binding))
        assertEquals(0, f.store.reads)
        assertFalse(f.lease.isHeld())
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "disabled"))
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun stopAfterDurableDispatchIntentWithdrawsWithoutPromptAndSurvivesReload() = runTest {
        for (invalidate in listOf(false, true)) {
            val f = Fixture(this)
            val committed = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.store.afterWrite = { snapshot ->
                if (snapshot.runs.singleOrNull()?.phase == ScheduledRunPhase.Dispatching) {
                    committed.complete(Unit)
                    release.await()
                }
            }
            f.executor.start(binding)
            f.executor.runNow(task.id, "withdraw-before-call")
            committed.await()
            val intent = f.store.snapshot.runs.single()
            assertTrue(intent.deliveryIntent)
            if (invalidate) f.executor.invalidate()
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
            assertTrue(f.lease.isHeld())
            release.complete(Unit)
            runCurrent()
            val withdrawn = f.store.snapshot.runs.single()
            assertEquals(ScheduledRunPhase.Withdrawn, withdrawn.phase)
            assertEquals(intent.copy(phase = withdrawn.phase, detail = withdrawn.detail), withdrawn)
            assertFalse(withdrawn.blocksTask)
            assertFalse(withdrawn.mayHaveDelivered)
            assertNull(withdrawn.terminal)
            assertEquals(0, f.session.prompts)
            assertEquals(0, f.evidence.inspections)
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
            // A new executor reads the persisted terminal audit, never replays its request.
            val reopened = Fixture(this, f.store.snapshot)
            assertEquals(ScheduledStartResult.Started, reopened.executor.start(binding.copy(generation = 2)))
            assertEquals(withdrawn, reopened.store.snapshot.runs.single())
            assertTrue(assertIs<ScheduledClaimResult.Claimed>(reopened.executor.runNow(task.id, withdrawn.requestId)).duplicate)
            assertEquals(0, reopened.session.creates)
            assertEquals(0, reopened.session.prompts)
            assertEquals(ScheduledShutdownResult.Drained, reopened.executor.shutdown())
        }
    }

    @Test fun failedWithdrawalCasRetainsProofAndLeaseUntilRepeatedShutdownSavesAudit() = runTest {
        val f = Fixture(this)
        f.store.afterWrite = { snapshot ->
            if (snapshot.runs.singleOrNull()?.phase == ScheduledRunPhase.Dispatching) f.executor.invalidate()
        }
        f.store.reject = { it.runs.any { run -> run.phase == ScheduledRunPhase.Withdrawn } }
        f.executor.start(binding)
        f.executor.runNow(task.id, "withdraw-cas-failure")
        runCurrent()
        assertEquals(ScheduledRunPhase.Dispatching, f.store.snapshot.runs.single().phase)
        assertEquals(0, f.session.prompts)
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        assertTrue(f.lease.isHeld())
        f.store.reject = { false }
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        assertEquals(ScheduledRunPhase.Withdrawn, f.store.snapshot.runs.single().phase)
        assertTrue(f.store.snapshot.runs.single().deliveryIntent)
        assertEquals(0, f.evidence.inspections)
    }

    @Test fun crashDispatchIntentAndInvokedPromptFailureRemainUnknownAndNeverWithdrawOrResend() = runTest {
        for (restart in listOf(false, true)) {
            val prior = priorRun(ScheduledRunPhase.Dispatching).copy(receiptId = null)
            val f = Fixture(this, if (restart) initial(prior) else initial())
            f.evidence.result = ScheduledEvidenceResult.Pending
            if (!restart) f.session.beforePrompt = { _, _ -> error("ambiguous transport failure") }
            f.executor.start(binding)
            if (!restart) {
                f.executor.runNow(task.id, "ambiguous-prompt")
                runCurrent()
            }
            val unknown = f.store.snapshot.runs.single()
            assertEquals(ScheduledRunPhase.Unknown, unknown.phase)
            assertTrue(unknown.mayHaveDelivered)
            assertTrue(assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(task.id, unknown.requestId)).duplicate)
            assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "do-not-resend"))
            assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
            assertTrue(f.lease.isHeld())
            assertEquals(if (restart) 0 else 1, f.session.prompts)
            assertTrue(f.store.commits.none { it.runs.any { run -> run.phase == ScheduledRunPhase.Withdrawn } })
            f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        }
    }

    @Test fun timeoutOrTemporaryUnavailableReconcilesLateTerminalWithoutRedelivery() = runTest {
        for (timeout in listOf(false, true)) {
            val f = Fixture(this)
            f.evidence.result = if (timeout) ScheduledEvidenceResult.Pending else ScheduledEvidenceResult.Unavailable("temporary flush failure")
            f.executor.start(binding)
            f.executor.runNow(task.id, "late-terminal")
            runCurrent()
            if (timeout) { advanceTimeBy(21); runCurrent() }
            assertEquals(ScheduledRunPhase.Unknown, f.store.snapshot.runs.single().phase)
            val count = f.evidence.inspections
            f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
            f.now = 1_009
            f.executor.tick()
            assertEquals(count, f.evidence.inspections, "Initial backoff must be respected")
            f.now = 1_010
            f.executor.tick()
            assertEquals(count + 1, f.evidence.inspections)
            assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
            assertEquals(1, f.session.creates)
            assertEquals(1, f.session.prompts)
            assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
        }
    }

    @Test fun reconciliationRetriesEvidenceExceptionAndFailedTerminalCasWithAdmissionDisabled() = runTest {
        val f = Fixture(this)
        f.evidence.result = ScheduledEvidenceResult.Unavailable("temporary evidence failure")
        f.executor.start(binding)
        f.executor.runNow(task.id, "retry-evidence-cas")
        runCurrent()
        f.evidence.beforeInspect = { error("temporary read failure") }
        f.now = 1_010
        f.executor.tick()
        assertEquals(ScheduledRunPhase.Unknown, f.store.snapshot.runs.single().phase)
        f.evidence.beforeInspect = null
        f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
        f.store.reject = { it.runs.any { run -> run.terminal != null } }
        f.now = 1_030
        f.executor.tick()
        assertFalse(f.executor.state.value.accepting)
        assertNull(f.store.snapshot.runs.single().terminal)
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "no-new-admission"))
        f.store.reject = { false }
        f.now = 1_070
        f.executor.tick()
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertFalse(f.executor.state.value.accepting, "A successful repair must not silently reopen admission")
        assertEquals(1, f.session.creates)
        assertEquals(1, f.session.prompts)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun failedObserverTerminalAndUnknownCasStillReconcilePriorAcceptedRun() = runTest {
        val f = Fixture(this)
        f.store.reject = { it.runs.any { run -> run.terminal != null || run.phase == ScheduledRunPhase.Unknown } }
        f.executor.start(binding)
        f.executor.runNow(task.id, "observer-cas-failure")
        runCurrent()
        assertEquals(ScheduledRunPhase.Accepted, f.store.snapshot.runs.single().phase)
        assertFalse(f.executor.state.value.accepting)
        f.store.reject = { false }
        f.now = 1_010
        f.executor.tick()
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertEquals(1, f.session.prompts)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    @Test fun unknownRecheckHasBoundedAttemptsBackoffAndOnlyShutdownCanReadAfterExhaustion() = runTest {
        val f = Fixture(this, initial(priorRun(ScheduledRunPhase.Unknown)))
        f.evidence.result = ScheduledEvidenceResult.Pending
        f.executor.start(binding)
        f.executor.tick() // Register this recovered Unknown's first backoff.
        val initialReads = f.evidence.inspections
        for (now in listOf(1_010L, 1_030L, 1_070L)) {
            f.now = now - 1
            val before = f.evidence.inspections
            f.executor.tick()
            assertEquals(before, f.evidence.inspections)
            f.now = now
            f.executor.tick()
            assertEquals(before + 1, f.evidence.inspections)
        }
        f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
        repeat(4) { f.now += 1_000; f.executor.tick() }
        assertEquals(initialReads + 3, f.evidence.inspections)
        assertEquals(ScheduledRunPhase.Unknown, f.store.snapshot.runs.single().phase)
        assertIs<ScheduledClaimResult.Refused>(f.executor.runNow(task.id, "exhaustion-is-not-completion"))
        assertEquals(0, f.session.creates)
        assertEquals(0, f.session.prompts)
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
    }

    @Test fun suspendedReconciliationHasFiniteShutdownAndLateReturnCanDrainWithoutAnotherJob() = runTest {
        val f = Fixture(this, initial(priorRun(ScheduledRunPhase.Unknown)))
        f.evidence.result = ScheduledEvidenceResult.Pending
        f.executor.start(binding)
        f.executor.tick()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.evidence.result = ScheduledEvidenceResult.Terminal(exactTerminal)
        f.evidence.beforeInspect = { entered.complete(Unit); release.await() }
        f.now = 1_010
        val checking = async { f.executor.tick() }
        entered.await()
        assertIs<ScheduledShutdownResult.UnsafeToRelease>(f.executor.shutdown(5))
        assertTrue(f.lease.isHeld())
        release.complete(Unit)
        checking.await()
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown(5))
        assertEquals(exactTerminal, f.store.snapshot.runs.single().terminal)
        assertEquals(0, f.session.creates)
        assertEquals(0, f.session.prompts)
        val count = f.evidence.inspections
        f.executor.tick()
        assertEquals(count, f.evidence.inspections)
    }

    @Test fun directAdapterRejectsUnsupportedOrInvalidTargetAndMismatchedSessionBeforeEffects() = runTest {
        val f = Fixture(this)
        val access = LocalScheduledSessionAccess(f.session, f.evidence)
        val base = priorRun(ScheduledRunPhase.Dispatching).copy(receiptId = null)
        val invalidTasks = listOf(task.copy(target = ScheduledTarget.Cron(ScheduledDestination.Cwd("relative/path"))),
            task.copy(model = null), task.copy(prompt = " "))
        for (invalid in invalidTasks) {
            assertFailsWith<IllegalStateException> { access.create(binding, base.copy(task = invalid)) }
            assertFailsWith<IllegalStateException> { access.prompt(binding, base.copy(task = invalid)) }
        }
        // A heartbeat never creates a session, and its prompt must match the task's own target session.
        val heartbeatTask = task.copy(target = ScheduledTarget.Heartbeat(SessionId("existing")), model = null)
        assertFailsWith<IllegalStateException> { access.create(binding, base.copy(task = heartbeatTask)) }
        assertFailsWith<IllegalStateException> { access.prompt(binding, base.copy(task = heartbeatTask)) }
        assertFailsWith<IllegalStateException> { access.prompt(binding, base.copy(sessionRef = SessionId("another-session"))) }
        assertEquals(0, f.session.creates)
        assertEquals(0, f.session.prompts)
        assertEquals(0, f.evidence.createdFlushes)
    }

    @Test fun heartbeatDeliversIntoItsBoundSessionWithoutCreatingOne() = runTest {
        val heartbeat = ScheduledTarget.Heartbeat(SessionId("live-session"))
        val heartbeatTask = task.copy(target = heartbeat, model = null)
        val f = Fixture(this, initial().copy(tasks = listOf(heartbeatTask)))
        f.session.beforePrompt = { id, message ->
            val run = f.store.snapshot.runs.single()
            assertEquals(ScheduledRunPhase.Dispatching, run.phase)
            assertTrue(run.deliveryIntent)
            assertEquals(heartbeat.sessionId, id)
            assertEquals(run.inputMessageId, message.id)
            assertEquals(heartbeatTask.prompt, (message.content.single() as TextContent).text)
        }
        assertEquals(ScheduledStartResult.Started, f.executor.start(binding))
        val claimed = assertIs<ScheduledClaimResult.Claimed>(f.executor.runNow(heartbeatTask.id, "heartbeat-1"))
        // The durable intent already names the existing session, so there is no candidate to create.
        assertEquals(heartbeat.sessionId, claimed.run.candidateSessionId)
        runCurrent()
        val saved = f.store.snapshot.runs.single()
        assertEquals(ScheduledRunPhase.Succeeded, saved.phase)
        assertEquals(heartbeat.sessionId, saved.sessionRef)
        assertEquals("inbox-receipt", saved.receiptId)
        assertEquals(0, f.session.creates)
        assertEquals(0, f.session.idleCalls)
        assertEquals(0, f.evidence.createdFlushes)
        assertEquals(1, f.session.prompts)
        // No CreatingSession step: a session follow-up is never created, only addressed.
        assertEquals(listOf(ScheduledRunPhase.Claimed, ScheduledRunPhase.SessionCreated,
            ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted, ScheduledRunPhase.Succeeded),
            f.store.commits.map { it.runs.single().phase })
        assertEquals(ScheduledShutdownResult.Drained, f.executor.shutdown())
    }

    private class Fixture(
        test: TestScope, snapshot: ScheduledSnapshot = initial(), hub: LeaseHub = LeaseHub(), verified: Boolean = true,
    ) {
        val store = StrictStore(snapshot)
        val session = ScriptedSession()
        val evidence = ScriptedEvidence()
        val lease = TestLease(hub)
        var now = 1_000L
        val executor = ScheduledExecutor(store, ScheduleEngine(HourlyCalendar), LocalScheduledSessionAccess(session, evidence),
            lease, test.backgroundScope, ScheduledClock { now }, ScheduledExecutorConfig(
                verifiedCronExecution = verified, tickIntervalMillis = 60_000, evidencePollMillis = 5, observationTimeoutMillis = 20,
                reconciliationAttempts = 3, reconciliationInitialDelayMillis = 10, reconciliationMaxDelayMillis = 40))
    }

    private class StrictStore(var snapshot: ScheduledSnapshot) : ScheduledStore {
        var reads = 0
        val commits = mutableListOf<ScheduledSnapshot>()
        var beforeWrite: (suspend () -> Unit)? = null
        var afterWrite: (suspend (ScheduledSnapshot) -> Unit)? = null
        var reject: (ScheduledSnapshot) -> Boolean = { false }
        override suspend fun read(owner: ScheduledOwner): ScheduledReadResult {
            reads++
            assertEquals(snapshot.owner, owner)
            return ScheduledReadResult.Ready(snapshot)
        }
        override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
            isCurrentScope: () -> Boolean): ScheduledWriteResult {
            beforeWrite?.invoke()
            if (!isCurrentScope()) return ScheduledWriteResult.StaleScope
            if (reject(snapshot)) return ScheduledWriteResult.Failed("test disk failure")
            if (owner != this.snapshot.owner || expectedRevision != this.snapshot.revision) return ScheduledWriteResult.Failed("test CAS conflict")
            assertEquals(expectedRevision + 1, snapshot.revision)
            assertNull(ScheduledValidator.snapshotError(snapshot))
            assertTrue(ScheduledLedgerRules.validTransition(this.snapshot, snapshot))
            this.snapshot = snapshot
            commits += snapshot
            afterWrite?.invoke(snapshot)
            return ScheduledWriteResult.Committed(snapshot)
        }
    }

    private class ScriptedSession : SessionApi {
        var creates = 0
        var prompts = 0
        var idleCalls = 0
        var beforeCreate: (suspend (SessionId, CreateSessionOptions, AgentOptions) -> Unit)? = null
        var beforePrompt: (suspend (SessionId, Message) -> Unit)? = null
        override suspend fun list(): List<SessionSummary> = error("executor must not enumerate sessions")
        override suspend fun history(id: SessionId): List<Message> = error("evidence uses the injected durable adapter")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean): Unit = error("shutdown must not cancel a session")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
            creates++
            val actual = requireNotNull(id)
            beforeCreate?.invoke(actual, options, agentOptions)
            return SessionSummary(actual, 1_000, cwd = options.cwd)
        }
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt {
            prompts++
            beforePrompt?.invoke(id, message)
            return object : SessionPrompt {
                override val receipt = SessionPromptReceipt(id, "inbox-receipt")
                override suspend fun awaitIdle() { idleCalls++; error("idle is not terminal evidence") }
            }
        }
    }

    private class ScriptedEvidence : ScheduledSessionEvidence {
        var result: ScheduledEvidenceResult = ScheduledEvidenceResult.Terminal(exactTerminal)
        var inspections = 0
        var createdFlushes = 0
        var beforeValidation: (suspend (ScheduledTask?) -> Unit)? = null
        var beforeInspect: (suspend () -> Unit)? = null
        override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? {
            beforeValidation?.invoke(task)
            return null
        }
        override suspend fun flushCreated(scope: ScheduledScope, sessionId: SessionId) { createdFlushes++ }
        override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult {
            inspections++
            beforeInspect?.invoke()
            return result
        }
    }

    private class LeaseHub { var owner: TestLease? = null }
    private class TestLease(private val hub: LeaseHub) : ScheduledLeaderLease {
        var releases = 0
        override suspend fun acquire(scope: ScheduledScope): Boolean {
            if (hub.owner != null) return false
            hub.owner = this
            return true
        }
        override fun isHeld(): Boolean = hub.owner === this
        override suspend fun release() {
            assertTrue(isHeld())
            releases++
            hub.owner = null
        }
    }

    private object HourlyCalendar : ScheduleCalendar {
        override fun isValidZone(zoneId: String): Boolean = zoneId == "UTC"
        override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate = error("hourly needs no wall calendar")
        override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = error("hourly needs no wall calendar")
        override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = error("hourly needs no wall calendar")
    }

    private companion object {
        val binding = ScheduledScope(HostId("executor-test-local"), null, 1)
        val task = ScheduledTask("test-task", 1, "Test Cron", "test prompt",
            ScheduledTarget.Cron(ScheduledDestination.Cwd("/test/workspace")), ScheduledRule.Hourly(1, 0),
            ScheduledModelRef("test-provider", "test-model"))
        val exactTerminal = ScheduledTerminalEvidence(7, 10, 18, 2_000, ScheduledRunPhase.Succeeded, "completed")
        fun priorRun(phase: ScheduledRunPhase) = ScheduledRun("prior-run", task, "prior-request", null, 1_000,
            SessionId("prior-session"), "prior-input", phase, SessionId("prior-session"), "prior-receipt", deliveryIntent = true)
        fun initial(run: ScheduledRun? = null) = ScheduledSnapshot(binding.owner, if (run == null) 1 else 5,
            listOf(task), runs = listOfNotNull(run))
    }
}

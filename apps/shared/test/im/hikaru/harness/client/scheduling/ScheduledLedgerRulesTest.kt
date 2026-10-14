package im.hikaru.harness.client.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import kotlinx.serialization.json.Json
import kotlin.test.*

class ScheduledLedgerRulesTest {
    @Test fun unknownRecoveryMayRefreshOnlyDiagnosticWithoutLosingDeliveryEvidence() {
        for (intent in listOf(false, true)) {
            val oldRun = unknown.copy(deliveryIntent = intent, sessionRef = if (intent) unknown.sessionRef else null,
                receiptId = if (intent) unknown.receiptId else null, detail = "old diagnostic")
            val old = snapshot(oldRun)
            val next = old.copy(revision = old.revision + 1, runs = listOf(oldRun.copy(detail = "restart: no exact TurnEnd")))
            assertNull(ScheduledLedgerRules.error(next))
            assertTrue(ScheduledLedgerRules.validTransition(old, next))
            assertTrue(ScheduledLedgerRules.validTransition(next, next.copy(revision = next.revision + 1)))
        }
    }

    @Test fun unknownCannotChangeIdentityPayloadIntentReceiptOrSessionDuringRecovery() {
        val old = snapshot(unknown)
        val invalid = listOf(
            unknown.copy(id = "replacement"),
            unknown.copy(requestId = "another-request"),
            unknown.copy(inputMessageId = "another-input"),
            unknown.copy(candidateSessionId = SessionId("another-session")),
            unknown.copy(task = task.copy(prompt = "replacement prompt")),
            unknown.copy(claimedAt = 99),
            unknown.copy(nominalAt = 42),
            unknown.copy(deliveryIntent = false),
            unknown.copy(sessionRef = null),
            unknown.copy(receiptId = null),
            unknown.copy(receiptId = "new-receipt"),
        )
        for (changed in invalid) assertFalse(ScheduledLedgerRules.validTransition(old, next(old, changed)), changed.toString())
        val noIntent = unknown.copy(deliveryIntent = false, sessionRef = null, receiptId = null)
        val noIntentSnapshot = snapshot(noIntent)
        assertFalse(ScheduledLedgerRules.validTransition(noIntentSnapshot, next(noIntentSnapshot, noIntent.copy(deliveryIntent = true))))
        assertFalse(ScheduledLedgerRules.validTransition(noIntentSnapshot, next(noIntentSnapshot, noIntent.copy(sessionRef = unknown.candidateSessionId))))
    }

    @Test fun unknownNeverReopensCreationOrDispatchAndRequiresExactTerminalEvidence() {
        val old = snapshot(unknown)
        for (phase in listOf(ScheduledRunPhase.Claimed, ScheduledRunPhase.CreatingSession, ScheduledRunPhase.SessionCreated,
            ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted, ScheduledRunPhase.Rejected)) {
            assertFalse(ScheduledLedgerRules.validTransition(old, next(old, unknown.copy(phase = phase))))
        }
        assertFalse(ScheduledLedgerRules.validTransition(old, next(old, unknown.copy(phase = ScheduledRunPhase.Succeeded))))
        for (outcome in listOf(ScheduledRunPhase.Succeeded, ScheduledRunPhase.Failed, ScheduledRunPhase.Blocked,
            ScheduledRunPhase.Incomplete, ScheduledRunPhase.Interrupted, ScheduledRunPhase.Aborted)) {
            val terminal = unknown.copy(phase = outcome, terminal = evidence.copy(outcome = outcome), detail = null)
            assertNull(ScheduledLedgerRules.error(next(old, terminal)))
            assertTrue(ScheduledLedgerRules.validTransition(old, next(old, terminal)))
        }
    }

    @Test fun terminalEvidenceCannotBeFabricatedForANeverDispatchedUnknown() {
        val prior = unknown.copy(deliveryIntent = false, sessionRef = null, receiptId = null)
        val old = snapshot(prior)
        val forged = prior.copy(phase = ScheduledRunPhase.Succeeded, sessionRef = prior.candidateSessionId,
            deliveryIntent = true, terminal = evidence)
        assertFalse(ScheduledLedgerRules.validTransition(old, next(old, forged)))
        val dispatchedWithoutConfirmedSession = unknown.copy(sessionRef = null, receiptId = null)
        val oldDispatched = snapshot(dispatchedWithoutConfirmedSession)
        val proven = dispatchedWithoutConfirmedSession.copy(phase = evidence.outcome,
            sessionRef = dispatchedWithoutConfirmedSession.candidateSessionId, terminal = evidence)
        assertTrue(ScheduledLedgerRules.validTransition(oldDispatched, next(oldDispatched, proven)))
    }

    @Test fun terminalRequiresExactOrderedTurnEndAndRemainsImmutable() {
        val prior = snapshot(unknown)
        val terminal = unknown.copy(phase = ScheduledRunPhase.Succeeded, terminal = evidence, detail = null)
        val old = next(prior, terminal)
        val badEvidence = listOf(
            evidence.copy(turn = 0),
            evidence.copy(userMessageSeq = -1),
            evidence.copy(turnEndSeq = evidence.userMessageSeq),
            evidence.copy(turnEndSeq = evidence.userMessageSeq - 1),
            evidence.copy(endedAt = -1),
            evidence.copy(reason = " "),
            evidence.copy(outcome = ScheduledRunPhase.Failed),
        )
        for (bad in badEvidence) assertNotNull(ScheduledLedgerRules.error(next(prior, terminal.copy(terminal = bad))))
        assertFalse(ScheduledLedgerRules.validTransition(old, next(old, terminal.copy(detail = "rewrite terminal"))))
        assertFalse(ScheduledLedgerRules.validTransition(old, next(old, terminal.copy(terminal = evidence.copy(turn = 8)))))
        assertFalse(ScheduledLedgerRules.validTransition(old, next(old, unknown)))
        assertTrue(ScheduledLedgerRules.validTransition(old, old.copy(revision = old.revision + 1)))
    }

    @Test fun requestAndNominalOccurrenceDeduplicationSurviveTerminalCompletion() {
        val terminal = unknown.copy(phase = ScheduledRunPhase.Succeeded, terminal = evidence, nominalAt = 3_600_000)
        val second = terminal.copy(id = "another-run", candidateSessionId = SessionId("another-session"),
            sessionRef = SessionId("another-session"), inputMessageId = "another-input")
        val old = snapshot(terminal)
        assertNotNull(ScheduledLedgerRules.error(old.copy(runs = listOf(terminal, second.copy(nominalAt = null)))))
        assertNotNull(ScheduledLedgerRules.error(old.copy(runs = listOf(terminal, second.copy(requestId = "another-request")))))
        assertNull(ScheduledLedgerRules.error(old.copy(runs = listOf(terminal, second.copy(requestId = "another-request", nominalAt = null)))))
    }

    @Test fun oneTaskCannotHaveOverlappingBlockingRunsOrRemoveItsUnresolvedLedgerEntry() {
        val old = snapshot(unknown)
        val claimed = unknown.copy(id = "another-run", requestId = "another-request", phase = ScheduledRunPhase.Claimed,
            candidateSessionId = SessionId("another-session"), inputMessageId = "another-input",
            sessionRef = null, receiptId = null, deliveryIntent = false)
        assertNotNull(ScheduledLedgerRules.error(old.copy(runs = listOf(unknown, claimed))))
        assertFalse(ScheduledLedgerRules.validTransition(old, old.copy(revision = old.revision + 1, runs = emptyList())))
        assertFalse(ScheduledLedgerRules.validTransition(old, old.copy(revision = old.revision + 1, runs = listOf(unknown, claimed))))
    }

    @Test fun withdrawalRetainsIntentAuditWithoutInventingCompletionAndRoundTrips() {
        val dispatching = unknown.copy(phase = ScheduledRunPhase.Dispatching, receiptId = null)
        val old = snapshot(dispatching)
        val withdrawn = dispatching.copy(phase = ScheduledRunPhase.Withdrawn, detail = "Known uncalled prompt")
        val saved = next(old, withdrawn)
        assertNull(ScheduledLedgerRules.error(saved))
        assertTrue(ScheduledLedgerRules.validTransition(old, saved))
        assertTrue(withdrawn.deliveryIntent)
        assertFalse(withdrawn.blocksTask)
        assertFalse(withdrawn.mayHaveDelivered)
        assertNull(withdrawn.terminal)
        assertEquals(saved, Json.decodeFromString(ScheduledSnapshot.serializer(),
            Json.encodeToString(ScheduledSnapshot.serializer(), saved)))
        assertTrue(ScheduledLedgerRules.validTransition(saved, saved.copy(revision = saved.revision + 1)))
        assertFalse(ScheduledLedgerRules.validTransition(saved, next(saved, withdrawn.copy(detail = "rewrite proof"))))
        assertFalse(ScheduledLedgerRules.validTransition(saved, next(saved, dispatching)))
        assertFalse(ScheduledLedgerRules.validTransition(saved, next(saved, withdrawn.copy(
            phase = ScheduledRunPhase.Succeeded, terminal = evidence))))
    }

    @Test fun withdrawalRequiresConfirmedSessionIntentAndExplicitUncalledAudit() {
        val withdrawn = unknown.copy(phase = ScheduledRunPhase.Withdrawn, receiptId = null, detail = "Known uncalled prompt")
        val invalid = listOf(withdrawn.copy(deliveryIntent = false), withdrawn.copy(sessionRef = null),
            withdrawn.copy(receiptId = "receipt"), withdrawn.copy(terminal = evidence),
            withdrawn.copy(detail = null), withdrawn.copy(detail = " "))
        for (run in invalid) assertNotNull(ScheduledLedgerRules.error(snapshot(run)), run.toString())
        for (phase in listOf(ScheduledRunPhase.Unknown, ScheduledRunPhase.Accepted, ScheduledRunPhase.SessionCreated)) {
            val previous = when (phase) {
                ScheduledRunPhase.SessionCreated -> unknown.copy(phase = phase, receiptId = null, deliveryIntent = false)
                else -> unknown.copy(phase = phase)
            }
            val old = snapshot(previous)
            assertFalse(ScheduledLedgerRules.validTransition(old, next(old, withdrawn)), phase.toString())
        }
    }

    private val task = ScheduledTask("task", 1, "Test Cron", "test prompt",
        ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Hourly(1, 0), ScheduledModelRef("provider", "model"))
    private val unknown = ScheduledRun("run", task, "request", null, 100, SessionId("session"), "input",
        ScheduledRunPhase.Unknown, SessionId("session"), "inbox-receipt", detail = "pending", deliveryIntent = true)
    private val evidence = ScheduledTerminalEvidence(7, 10, 18, 200, ScheduledRunPhase.Succeeded, "completed")
    private fun snapshot(run: ScheduledRun) = ScheduledSnapshot(ScheduledOwner(HostId("ledger-test"), null), 5, listOf(task), runs = listOf(run))
    private fun next(old: ScheduledSnapshot, run: ScheduledRun) = old.copy(revision = old.revision + 1, runs = listOf(run))
}

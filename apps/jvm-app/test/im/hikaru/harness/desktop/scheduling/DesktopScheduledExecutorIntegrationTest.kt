package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.agent.*
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.connection.SessionPromptReceipt
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.session.persistence.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Real executor, JSON ledger, OS lease, local gateway, agent loop and Room.
 * Scheduling time and the LLM stream are controlled. Access/store gates only pause the real
 * adapters; they never synthesize commits, sessions, receipts or evidence. CAS conflicts use
 * external file edits against the production store's observed bytes.
 * Restart coverage is a graceful same-process lifecycle, not a crash or cross-process lease test. */
class DesktopScheduledExecutorIntegrationTest {
    private val host = HostId("scheduled-executor-integration")
    private val binding = ScheduledScope(host, null, 1)
    private val initialTime = Instant.parse("2026-01-05T08:59:00Z").toEpochMilli()
    private val dueTime = Instant.parse("2026-01-05T09:00:00Z").toEpochMilli()

    @Test fun runNowPersistsDeliveryAndExactTerminalThenRestartDeduplicatesWithoutResending() = runBlocking {
        temporary { home ->
            val clock = MutableClock(initialTime)
            val task = task(home)
            seed(home, task)
            val first = fixture(home, clock)
            try {
                assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
                assertTrue(first.leader.isHeld())
                val lockKey = lockKey(home)
                val claim = assertIs<ScheduledClaimResult.Claimed>(first.executor.runNow(task.id, "manual-once"))
                assertFalse(claim.duplicate)
                assertEquals(ScheduledRunPhase.Claimed, claim.run.phase)
                assertNull(claim.run.sessionRef)
                assertNull(claim.run.nominalAt)

                val createIntent = withTimeout(10_000) { first.access.beforeCreate.await() }
                assertEquals(claim.run.id, createIntent.id)
                assertEquals(claim.run.inputMessageId, createIntent.inputMessageId)
                val duplicate = assertIs<ScheduledClaimResult.Claimed>(first.executor.runNow(task.id, "manual-once"))
                assertTrue(duplicate.duplicate)
                assertEquals(claim.run.id, duplicate.run.id)
                assertIs<ScheduledClaimResult.Refused>(first.executor.runNow(task.id, "overlapping-request"))

                val terminal = completeDelivery(home, first, createIntent)
                assertTrue(assertIs<ScheduledClaimResult.Claimed>(first.executor.runNow(task.id, "manual-once")).duplicate)
                first.executor.tick()
                assertSingleDelivery(first)
                first.close()
                assertFalse(first.leader.isHeld())
                assertStableLock(home, lockKey)
                assertEquals(terminal, ledger(home).runs.single())
                assertPersistedExactTerminal(home, terminal)

                // New Runtime, Room connection, store, OS lease and executor read the same home.
                val second = fixture(home, clock, gated = false)
                try {
                    assertTrue(second.runtime.context.agents.list().isEmpty(), "Reading evidence must not restore an agent")
                    assertEquals(ScheduledStartResult.Started, second.executor.start(binding.copy(generation = 2)))
                    val recovered = assertIs<ScheduledClaimResult.Claimed>(second.executor.runNow(task.id, "manual-once"))
                    assertTrue(recovered.duplicate)
                    assertEquals(terminal, recovered.run)
                    repeat(3) { second.executor.tick() }
                    second.close()
                    assertNoDelivery(second)
                    assertEquals(terminal, ledger(home).runs.single())
                    assertStableLock(home, lockKey)
                } finally { second.close() }
            } finally { first.close() }
        }
    }

    @Test fun activationAndDueTickDeliverOneOccurrenceAndGracefulRestartDoesNotBackfillIt() = runBlocking {
        temporary { home ->
            val clock = MutableClock(initialTime)
            val task = task(home)
            seed(home, task)
            val first = fixture(home, clock)
            try {
                assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
                val lockKey = lockKey(home)
                val activated = assertIs<ScheduledActivationResult.Saved>(first.executor.activate(task.id, task.revision, true)).snapshot
                assertEquals(ScheduledTaskStatus.Active, activated.tasks.single().status)
                assertEquals(task.revision + 1, activated.tasks.single().revision)
                assertEquals(dueTime, activated.cursors.single().nextNominalAt)
                assertEquals(activated, ledger(home))
                first.executor.tick()
                assertTrue(ledger(home).runs.isEmpty())
                assertNoDelivery(first)

                clock.set(dueTime)
                first.executor.tick()
                val createIntent = withTimeout(10_000) { first.access.beforeCreate.await() }
                assertEquals(dueTime, createIntent.nominalAt)
                assertEquals("due:${task.id}:$dueTime", createIntent.requestId)
                assertEquals(activated.tasks.single(), createIntent.task)
                repeat(3) { first.executor.tick() }
                assertEquals(1, ledger(home).runs.size)
                assertTrue(ledger(home).cursors.single().nextNominalAt > clock.nowEpochMillis())

                val terminal = completeDelivery(home, first, createIntent)
                repeat(3) { first.executor.tick() }
                assertEquals(listOf(terminal), ledger(home).runs)
                assertSingleDelivery(first)
                first.close()
                assertStableLock(home, lockKey)
                assertPersistedExactTerminal(home, terminal)

                // Keep time at this occurrence: restart must schedule a future cursor, not replay it.
                val second = fixture(home, clock, gated = false)
                try {
                    assertEquals(ScheduledStartResult.Started, second.executor.start(binding.copy(generation = 2)))
                    repeat(3) { second.executor.tick() }
                    assertTrue(ledger(home).cursors.single().nextNominalAt > dueTime)
                    assertTrue(second.runtime.context.agents.list().isEmpty())
                    second.close()
                    assertNoDelivery(second)
                    assertEquals(listOf(terminal), ledger(home).runs)
                    assertStableLock(home, lockKey)
                } finally { second.close() }
            } finally { first.close() }
        }
    }

    @Test fun defaultVerificationGateRefusesRealChainWithoutClaimOrSessionSideEffects() = runBlocking {
        temporary { home ->
            val task = task(home)
            seed(home, task)
            val bytes = Files.readAllBytes(ledgerPath(home))
            val fixture = fixture(home, MutableClock(initialTime), gated = false, config = ScheduledExecutorConfig())
            try {
                assertIs<ScheduledStartResult.Unavailable>(fixture.executor.start(binding))
                assertFalse(fixture.leader.isHeld())
                assertFalse(Files.exists(leaderPath(home)))
                assertIs<ScheduledClaimResult.Refused>(fixture.executor.runNow(task.id, "unverified"))
                fixture.executor.tick()
                assertContentEquals(bytes, Files.readAllBytes(ledgerPath(home)))
                assertTrue(fixture.runtime.context.agents.list().isEmpty())
                fixture.close()
                assertNoDelivery(fixture)
            } finally { fixture.close() }
        }
    }

    @Test fun stopAfterRealDispatchCommitBeforePromptWithdrawsAndFreshReaderNeverRedelivers() = runBlocking {
        temporary { home ->
            val clock = MutableClock(initialTime)
            val task = task(home)
            seed(home, task)
            val gate = CommitGateStore(DesktopScheduledStore(ledgerPath(home)), ScheduledRunPhase.Dispatching, afterCommit = true)
            val first = fixture(home, clock, store = gate)
            try {
                first.access.allowCreate.complete(Unit)
                assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
                val key = lockKey(home)
                val claim = assertIs<ScheduledClaimResult.Claimed>(first.executor.runNow(task.id, "stop-before-prompt"))
                // This gate is outside prompt: the production store already committed and fsynced,
                // but its real receipt has not returned to ScheduledExecutor.stage/deliver yet.
                val dispatchSnapshot = withTimeout(10_000) { gate.paused.await() }
                val committed = assertIs<ScheduledWriteResult.Committed>(withTimeout(10_000) { gate.result.await() })
                assertNull(committed.durabilityWarning)
                assertEquals(committed.snapshot, dispatchSnapshot)
                assertEquals(dispatchSnapshot, ledger(home))
                val dispatch = dispatchSnapshot.runs.single()
                assertEquals(claim.run.id, dispatch.id)
                assertEquals(ScheduledRunPhase.Dispatching, dispatch.phase)
                assertTrue(dispatch.deliveryIntent)
                assertNotNull(dispatch.sessionRef)
                assertEquals(1, first.access.creates.get())
                assertEquals(0, first.access.prompts.get())
                assertFalse(first.access.beforePrompt.isCompleted, "Executor must not have entered prompt")
                assertFalse(first.adapter.started.isCompleted)

                assertIs<ScheduledShutdownResult.UnsafeToRelease>(withTimeout(2_000) { first.executor.shutdown(50) })
                assertFalse(first.executor.state.value.accepting)
                assertTrue(first.leader.isHeld(), "An outstanding CAS method still owns the lease")
                assertEquals(dispatchSnapshot, ledger(home))
                gate.resume.complete(Unit)
                assertEquals(ScheduledShutdownResult.Drained, withTimeout(12_000) { first.executor.shutdown(10_000) })
                val withdrawn = ledger(home).runs.single()
                assertEquals(ScheduledRunPhase.Withdrawn, withdrawn.phase)
                assertEquals(dispatch.copy(phase = withdrawn.phase, detail = withdrawn.detail), withdrawn)
                assertTrue(withdrawn.deliveryIntent)
                assertFalse(withdrawn.blocksTask)
                assertFalse(withdrawn.mayHaveDelivered)
                assertNull(withdrawn.receiptId)
                assertNull(withdrawn.terminal)
                assertFalse(first.access.beforePrompt.isCompleted)
                assertEquals(0, first.access.prompts.get())
                assertEquals(0, first.adapter.streams.get())
                val withdrawnBytes = Files.readAllBytes(ledgerPath(home))
                assertEquals(ScheduledShutdownResult.Drained, first.executor.shutdown(1_000))
                assertContentEquals(withdrawnBytes, Files.readAllBytes(ledgerPath(home)))
                assertFalse(first.leader.isHeld())
                assertStableLock(home, key)
                first.close()
                assertPersistedUndeliveredSession(home, withdrawn)

                val second = fixture(home, clock, gated = false)
                try {
                    assertEquals(ScheduledStartResult.Started, second.executor.start(binding.copy(generation = 2)))
                    val duplicate = assertIs<ScheduledClaimResult.Claimed>(second.executor.runNow(task.id, withdrawn.requestId))
                    assertTrue(duplicate.duplicate)
                    assertEquals(withdrawn, duplicate.run)
                    repeat(3) { second.executor.tick() }
                    assertEquals(ScheduledShutdownResult.Drained, second.executor.shutdown(1_000))
                    assertEquals(ScheduledShutdownResult.Drained, second.executor.shutdown(1_000))
                    assertNoDelivery(second)
                    assertEquals(withdrawn, ledger(home).runs.single())
                    assertTrue(second.runtime.context.agents.list().isEmpty())
                    assertStableLock(home, key)
                } finally { second.close() }
            } finally { gate.resume.complete(Unit); first.close() }
        }
    }

    @Test fun acceptedHangingProviderKeepsOsLeaseUntilLatePersistedTurnEndAndRepeatedShutdown() = runBlocking {
        temporary { home ->
            val clock = MutableClock(initialTime)
            val task = task(home)
            seed(home, task)
            val first = fixture(home, clock, config = ScheduledExecutorConfig(verifiedCronExecution = true,
                tickIntervalMillis = 3_600_000, evidencePollMillis = 10, observationTimeoutMillis = 5_000))
            val contender = DesktopScheduledLeaderLease(leaderPath(home))
            try {
                first.access.allowCreate.complete(Unit)
                first.access.allowPrompt.complete(Unit)
                assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
                val key = lockKey(home)
                assertIs<ScheduledClaimResult.Claimed>(first.executor.runNow(task.id, "accepted-late-terminal"))
                withTimeout(10_000) { first.adapter.started.await() }
                val accepted = awaitRun(home) { it.phase == ScheduledRunPhase.Accepted }
                assertNotNull(accepted.receiptId)
                assertNotEquals(accepted.inputMessageId, accepted.receiptId)
                assertTrue(accepted.deliveryIntent)
                assertSingleDelivery(first)
                assertEquals(ScheduledEvidenceResult.Pending, first.access.inspect(binding, accepted))
                assertIs<ScheduledShutdownResult.UnsafeToRelease>(withTimeout(2_000) { first.executor.shutdown(50) })
                assertTrue(first.leader.isHeld())
                assertFalse(contender.acquire(binding.copy(generation = 2)))
                contender.release()
                assertTrue(first.leader.isHeld(), "A losing contender must not release the holder")

                // Allow the bounded observer to finish Unknown while the real provider stays hung.
                // A completed Unsafe attempt ensures a repeated close must re-inspect late evidence.
                val unknown = awaitRun(home) { it.phase == ScheduledRunPhase.Unknown }
                assertTrue(unknown.mayHaveDelivered)
                assertNull(unknown.terminal)
                assertFalse(first.adapter.finish.isCompleted)
                assertIs<ScheduledShutdownResult.UnsafeToRelease>(withTimeout(3_000) { first.executor.shutdown(2_000) })
                assertFalse(contender.acquire(binding.copy(generation = 2)))
                contender.release()
                assertNotNull(first.runtime.context.sessions.get(SessionId(assertNotNull(unknown.sessionRef).value)))
                assertEquals(1, first.runtime.context.agents.list().size, "Unsafe close must retain the live Runtime")
                assertStableLock(home, key)

                first.adapter.finish.complete(Unit)
                val exact = awaitExactTerminal(first, unknown) // Real input -> TurnEnd, including persistence.flush.
                assertEquals(ScheduledRunPhase.Succeeded, exact.outcome)
                assertEquals(unknown, ledger(home).runs.single(), "Evidence alone must not synthesize a ledger commit")
                assertTrue(first.leader.isHeld())
                assertFalse(contender.acquire(binding.copy(generation = 2)))
                contender.release()
                assertEquals(ScheduledShutdownResult.Drained, withTimeout(12_000) { first.executor.shutdown(10_000) })
                val terminal = ledger(home).runs.single()
                assertEquals(unknown.copy(phase = exact.outcome, terminal = exact, detail = null), terminal)
                assertSingleDelivery(first)
                assertFalse(first.leader.isHeld())
                assertTrue(contender.acquire(binding.copy(generation = 2)), "Takeover is allowed only after Drained")
                contender.release()
                assertStableLock(home, key)
                first.close()
                assertPersistedExactTerminal(home, terminal)

                val second = fixture(home, clock, gated = false)
                try {
                    assertEquals(ScheduledStartResult.Started, second.executor.start(binding.copy(generation = 2)))
                    val duplicate = assertIs<ScheduledClaimResult.Claimed>(second.executor.runNow(task.id, terminal.requestId))
                    assertTrue(duplicate.duplicate)
                    assertEquals(terminal, duplicate.run)
                    assertNoDelivery(second)
                    assertEquals(ScheduledShutdownResult.Drained, second.executor.shutdown(1_000))
                } finally { second.close() }
            } finally {
                withContext(NonCancellable) {
                    try { first.close() } finally { contender.release() }
                }
            }
        }
    }

    @Test fun externalByteEditBeforeRealClaimCasRefusesWithoutCreateOrOverwritingCompetitor() = runBlocking {
        temporary { home ->
            val task = task(home)
            seed(home, task)
            val gate = CommitGateStore(DesktopScheduledStore(ledgerPath(home)), ScheduledRunPhase.Claimed, afterCommit = false)
            val first = fixture(home, MutableClock(initialTime), store = gate)
            try {
                assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
                val pendingClaim = async { first.executor.runNow(task.id, "claim-byte-conflict") }
                val proposed = withTimeout(10_000) { gate.paused.await() }.runs.single()
                val before = ledger(home)
                assertTrue(before.runs.isEmpty(), "The proposed claim has not committed")
                val competingBytes = appendExternalWhitespace(home)
                assertEquals(before, ledger(home), "Only disk bytes changed; revision and snapshot stayed equal")
                gate.resume.complete(Unit)
                val failed = assertIs<ScheduledWriteResult.Failed>(withTimeout(10_000) { gate.result.await() })
                assertTrue(failed.message.contains("外部修改"), failed.message)
                assertIs<ScheduledClaimResult.Refused>(withTimeout(10_000) { pendingClaim.await() })
                assertFalse(first.executor.state.value.accepting)
                assertNotNull(first.executor.state.value.error)
                assertIs<ScheduledClaimResult.Refused>(first.executor.runNow(task.id, "no-side-effect-after-conflict"))
                first.executor.tick()
                assertNoDelivery(first)
                assertNull(first.runtime.context.sessions.get(SessionId(proposed.candidateSessionId.value)))
                assertTrue(first.runtime.context.agents.list().isEmpty())
                assertContentEquals(competingBytes, Files.readAllBytes(ledgerPath(home)))
                assertEquals(ScheduledShutdownResult.Drained, first.executor.shutdown(10_000))
                assertFalse(first.leader.isHeld())
                assertEquals(before, ledger(home))
                assertContentEquals(competingBytes, Files.readAllBytes(ledgerPath(home)))
                first.close()
            } finally { gate.resume.complete(Unit); first.close() }
        }
    }

    @Test fun externalByteEditBeforeRealDispatchCasStopsBeforePromptAndPreservesSessionCreatedBytes() = runBlocking {
        temporary { home ->
            val task = task(home)
            seed(home, task)
            val gate = CommitGateStore(DesktopScheduledStore(ledgerPath(home)), ScheduledRunPhase.Dispatching, afterCommit = false)
            val first = fixture(home, MutableClock(initialTime), store = gate)
            try {
                first.access.allowCreate.complete(Unit)
                assertEquals(ScheduledStartResult.Started, first.executor.start(binding))
                assertIs<ScheduledClaimResult.Claimed>(first.executor.runNow(task.id, "dispatch-byte-conflict"))
                val proposed = withTimeout(10_000) { gate.paused.await() }.runs.single()
                val before = ledger(home)
                val created = before.runs.single()
                assertEquals(ScheduledRunPhase.SessionCreated, created.phase)
                assertFalse(created.deliveryIntent)
                assertEquals(ScheduledRunPhase.Dispatching, proposed.phase)
                assertTrue(proposed.deliveryIntent)
                assertEquals(created.sessionRef, proposed.sessionRef)
                assertEquals(1, first.access.creates.get())
                assertNotNull(first.runtime.context.sessions.get(SessionId(assertNotNull(created.sessionRef).value)))
                assertFalse(first.access.beforePrompt.isCompleted)
                val competingBytes = appendExternalWhitespace(home)
                assertEquals(before, ledger(home))
                gate.resume.complete(Unit)
                val failed = assertIs<ScheduledWriteResult.Failed>(withTimeout(10_000) { gate.result.await() })
                assertTrue(failed.message.contains("外部修改"), failed.message)
                withTimeout(10_000) {
                    while (first.executor.state.value.error == null) delay(10)
                }
                assertFalse(first.executor.state.value.accepting, "The CAS failure itself must stop admission")
                assertIs<ScheduledClaimResult.Refused>(first.executor.runNow(task.id, "no-prompt-after-dispatch-conflict"))
                first.executor.tick()
                assertFalse(first.access.beforePrompt.isCompleted)
                assertEquals(before, ledger(home))
                assertContentEquals(competingBytes, Files.readAllBytes(ledgerPath(home)))
                // Shutdown waits for deliver/finally too: no delayed prompt or audit CAS may
                // rewrite the external bytes after the original production CAS failed.
                assertEquals(ScheduledShutdownResult.Drained, first.executor.shutdown(10_000))
                assertFalse(first.leader.isHeld())
                assertFalse(first.access.beforePrompt.isCompleted)
                assertEquals(0, first.access.prompts.get())
                assertEquals(0, first.adapter.streams.get())
                assertEquals(before, ledger(home))
                assertContentEquals(competingBytes, Files.readAllBytes(ledgerPath(home)))
                first.close()
                assertPersistedUndeliveredSession(home, created)
                assertEquals(before, ledger(home))
                assertContentEquals(competingBytes, Files.readAllBytes(ledgerPath(home)))
            } finally { gate.resume.complete(Unit); first.close() }
        }
    }

    private suspend fun completeDelivery(home: Path, fixture: Fixture, createIntent: ScheduledRun): ScheduledRun {
        assertEquals(ScheduledRunPhase.CreatingSession, createIntent.phase)
        assertNull(createIntent.sessionRef)
        assertFalse(createIntent.deliveryIntent)
        assertEquals(0, fixture.access.creates.get())
        assertNull(fixture.runtime.context.sessions.get(SessionId(createIntent.candidateSessionId.value)))
        fixture.access.allowCreate.complete(Unit)

        val dispatch = withTimeout(10_000) { fixture.access.beforePrompt.await() }
        assertEquals(createIntent.id, dispatch.id)
        assertEquals(ScheduledRunPhase.Dispatching, dispatch.phase)
        assertTrue(dispatch.deliveryIntent)
        assertEquals(createIntent.candidateSessionId, dispatch.sessionRef)
        assertEquals(createIntent.inputMessageId, dispatch.inputMessageId)
        assertEquals(1, fixture.access.creates.get())
        assertEquals(0, fixture.access.prompts.get())
        assertFalse(fixture.adapter.started.isCompleted)
        val sessionId = SessionId(assertNotNull(dispatch.sessionRef).value)
        val agent = assertNotNull(fixture.runtime.context.agents.get(AgentId(sessionId.value)))
        assertEquals(home.toString(), agent.options.cwd)
        assertEquals("scripted", agent.options.provider)
        assertEquals("test", agent.options.model)
        assertTrue(fixture.runtime.context.persistence.inbox(sessionId).nextTurn.isEmpty())
        fixture.access.allowPrompt.complete(Unit)

        val options = withTimeout(10_000) { fixture.adapter.started.await() }
        assertEquals("scripted", options.provider)
        assertEquals("test", options.model)
        val accepted = awaitRun(home) { it.phase == ScheduledRunPhase.Accepted }
        assertNotNull(accepted.receiptId)
        assertNotEquals(accepted.inputMessageId, accepted.receiptId)
        assertEquals(ScheduledEvidenceResult.Pending, fixture.access.inspect(binding, accepted))
        assertSingleDelivery(fixture)
        repeat(3) { fixture.executor.tick() }
        assertEquals(1, ledger(home).runs.size)

        fixture.adapter.finish.complete(Unit)
        // Wait on exact UserMessage.id -> TurnEnd evidence, never on idle or assistant output.
        val exact = awaitExactTerminal(fixture, accepted)
        assertEquals(ScheduledRunPhase.Succeeded, exact.outcome)
        val terminal = awaitRun(home) { it.phase == ScheduledRunPhase.Succeeded }
        assertEquals(exact, terminal.terminal)
        assertEquals(dispatch.sessionRef, terminal.sessionRef)
        assertSingleDelivery(fixture)
        return terminal
    }

    private suspend fun awaitExactTerminal(fixture: Fixture, run: ScheduledRun): ScheduledTerminalEvidence = withTimeout(10_000) {
        var result = fixture.access.inspect(binding, run)
        while (result == ScheduledEvidenceResult.Pending) {
            delay(10)
            result = fixture.access.inspect(binding, run)
        }
        assertIs<ScheduledEvidenceResult.Terminal>(result).evidence
    }

    private suspend fun assertPersistedUndeliveredSession(home: Path, run: ScheduledRun) {
        // The live fixture has already closed Room before a fresh persistence reader is opened.
        val reader = Runtime()
        try {
            reader.install(SessionPlugin())
            reader.install(AgentPlugin())
            reader.install(sessionPersistencePluginForTest(home.resolve("sessions.db").toString()))
            val sessionId = SessionId(assertNotNull(run.sessionRef).value)
            val persisted = reader.context.persistence.load(sessionId)
            assertTrue(persisted.events().none { it.type == SessionEventNames.USER_MESSAGE || it.type == SessionEventNames.TURN_END })
            assertTrue(reader.context.persistence.inbox(sessionId).nextTurn.isEmpty())
            assertTrue(reader.context.persistence.inbox(sessionId).nextStep.isEmpty())
            assertEquals(ScheduledEvidenceResult.Pending, DesktopScheduledSessionEvidence(reader.context, host).inspect(binding, run))
            assertTrue(reader.context.agents.list().isEmpty(), "Header-only evidence must not restore an Agent")
        } finally { withContext(NonCancellable) { reader.context.dispose() } }
    }

    /** Semantically identical external bytes make this a byte-CAS conflict, not a revision conflict. */
    private fun appendExternalWhitespace(home: Path): ByteArray {
        val before = Files.readAllBytes(ledgerPath(home))
        val competing = before + "\n ".toByteArray(Charsets.UTF_8)
        Files.write(ledgerPath(home), competing)
        FileChannel.open(ledgerPath(home), StandardOpenOption.WRITE).use { it.force(true) }
        assertFalse(before.contentEquals(competing))
        assertContentEquals(competing, Files.readAllBytes(ledgerPath(home)))
        return competing
    }

    private suspend fun assertPersistedExactTerminal(home: Path, run: ScheduledRun) {
        // One Runtime owns a Room location at a time. The original fixture is closed
        // before this fresh reader opens it, and the restart fixture is created afterwards.
        val reader = Runtime()
        try {
            reader.install(SessionPlugin())
            reader.install(AgentPlugin())
            reader.install(sessionPersistencePluginForTest(home.resolve("sessions.db").toString()))
            // Load from Room before inspecting: a live SessionStore cannot load the same ID twice,
            // and inspection alone could otherwise flush an in-memory log before this assertion.
            val persisted = reader.context.persistence.load(SessionId(assertNotNull(run.sessionRef).value))
            val events = persisted.events()
            val exact = assertNotNull(DesktopScheduledSessionEvidence.terminalEvidence(events, run.inputMessageId))
            assertEquals(run.terminal, exact)
            val users = events.filter { it.type == SessionEventNames.USER_MESSAGE }
                .map { Json.decodeFromJsonElement(UserMessageEvent.serializer(), it.data) }
            assertEquals(1, users.size, "A real session must contain exactly one delivered user prompt")
            assertEquals(run.inputMessageId, users.single().message.id.value)
            val userEnvelope = events.single { it.seq == exact.userMessageSeq }
            assertEquals(SessionEventNames.USER_MESSAGE, userEnvelope.type)
            val user = Json.decodeFromJsonElement(UserMessageEvent.serializer(), userEnvelope.data)
            assertEquals(run.inputMessageId, user.message.id.value)
            val endEnvelope = events.single { it.seq == exact.turnEndSeq }
            assertEquals(SessionEventNames.TURN_END, endEnvelope.type)
            val end = Json.decodeFromJsonElement(TurnEndEvent.serializer(), endEnvelope.data)
            assertEquals(user.turn, end.turn)
            assertEquals(exact.turn, end.turn)
            assertEquals(CompletedTurnEndReason, end.reason)
            val evidence = DesktopScheduledSessionEvidence(reader.context, host)
            assertEquals(exact, assertIs<ScheduledEvidenceResult.Terminal>(evidence.inspect(binding, run)).evidence)
            assertEquals(ScheduledEvidenceResult.Pending, evidence.inspect(binding,
                run.copy(inputMessageId = assertNotNull(run.receiptId))))
            assertTrue(reader.context.agents.list().isEmpty(), "Reading evidence must not restore an agent")
        } finally { withContext(NonCancellable) { reader.context.dispose() } }
    }

    private fun assertSingleDelivery(fixture: Fixture) {
        assertEquals(1, fixture.access.creates.get())
        assertEquals(1, fixture.access.prompts.get())
        assertEquals(1, fixture.adapter.streams.get())
    }

    private fun assertNoDelivery(fixture: Fixture) {
        assertEquals(0, fixture.access.creates.get())
        assertEquals(0, fixture.access.prompts.get())
        assertEquals(0, fixture.adapter.streams.get())
        assertFalse(fixture.access.beforeCreate.isCompleted)
        assertFalse(fixture.access.beforePrompt.isCompleted)
    }

    private fun task(home: Path) = ScheduledTask("task", 1, "real scheduled execution", "controlled local prompt",
        ScheduledTarget.Cron(ScheduledDestination.Cwd(home.toString())), ScheduledRule.Daily(9, 0, "UTC"),
        ScheduledModelRef("scripted", "test"))

    private suspend fun seed(home: Path, task: ScheduledTask) {
        val store = DesktopScheduledStore(ledgerPath(home))
        assertEquals(ScheduledReadResult.Missing, store.read(binding.owner))
        val saved = assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(binding.owner, 0,
            ScheduledSnapshot(binding.owner, 1, listOf(task))) { true })
        assertNull(saved.durabilityWarning)
    }

    private suspend fun ledger(home: Path): ScheduledSnapshot =
        assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(ledgerPath(home)).read(binding.owner)).snapshot

    private suspend fun awaitRun(home: Path, predicate: (ScheduledRun) -> Boolean): ScheduledRun = withTimeout(10_000) {
        var current = ledger(home).runs.single()
        while (!predicate(current)) { delay(10); current = ledger(home).runs.single() }
        current
    }

    private fun ledgerPath(home: Path): Path = home.resolve("scheduled-tasks.json")
    private fun leaderPath(home: Path): Path = home.resolve("scheduled-executor.lock")
    private fun lockKey(home: Path): Any? = Files.readAttributes(leaderPath(home), BasicFileAttributes::class.java).fileKey()
    private fun assertStableLock(home: Path, key: Any?) {
        assertTrue(Files.exists(leaderPath(home)), "The stable lock file must survive shutdown and restart")
        if (key != null) assertEquals(key, lockKey(home))
    }

    private suspend fun fixture(home: Path, clock: MutableClock, gated: Boolean = true,
        config: ScheduledExecutorConfig = ScheduledExecutorConfig(verifiedCronExecution = true,
            tickIntervalMillis = 3_600_000, evidencePollMillis = 10, observationTimeoutMillis = 30_000),
        store: ScheduledStore = DesktopScheduledStore(ledgerPath(home)),
    ): Fixture {
        val runtime = Runtime()
        val ownerJob = SupervisorJob()
        val adapter = ControlledAdapter()
        val leader = DesktopScheduledLeaderLease(leaderPath(home))
        try {
            runtime.install(SessionPlugin())
            runtime.install(LlmPlugin())
            runtime.install(AgentPlugin())
            runtime.context.llm.registerAdapter(listOf("scripted"), adapter)
            runtime.install(AgentLoopPlugin())
            runtime.install(sessionPersistencePluginForTest(home.resolve("sessions.db").toString()))
            runtime.install(SessionApiPlugin())
            val gateway = ApiGateway()
            gateway.registerSessionApi(runtime.context)
            val access = ObservedAccess(LocalScheduledSessionAccess(LocalConnection(gateway).session,
                DesktopScheduledSessionEvidence(runtime.context, host)), home, gated)
            val executor = ScheduledExecutor(store, ScheduleEngine(DesktopScheduleCalendar()),
                access, leader, CoroutineScope(ownerJob + Dispatchers.Default), clock, config)
            if (!gated) adapter.finish.complete(Unit)
            return Fixture(runtime, adapter, access, leader, executor, ownerJob)
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                adapter.finish.complete(Unit)
                try { ownerJob.cancelAndJoin() } finally { runtime.context.dispose() }
            }
            throw error
        }
    }

    private class Fixture(val runtime: Runtime, val adapter: ControlledAdapter, val access: ObservedAccess,
        val leader: DesktopScheduledLeaderLease, val executor: ScheduledExecutor, private val ownerJob: Job) {
        private var closed = false
        suspend fun close() {
            if (closed) return
            closed = true
            withContext(NonCancellable) {
                access.allowCreate.complete(Unit)
                access.allowPrompt.complete(Unit)
                adapter.finish.complete(Unit)
                try {
                    assertEquals(ScheduledShutdownResult.Drained, executor.shutdown(10_000))
                    assertFalse(leader.isHeld())
                } finally {
                    // Even a failed assertion must close test-only resources. Forced teardown is
                    // not evidence that production may release an unresolved delivery's lease.
                    try { ownerJob.cancelAndJoin() } finally {
                        try { runtime.context.dispose() } finally { leader.release() }
                    }
                }
            }
        }
    }

    private inner class ObservedAccess(private val delegate: ScheduledSessionAccess, private val home: Path,
        private val gated: Boolean) : ScheduledSessionAccess by delegate {
        val creates = AtomicInteger()
        val prompts = AtomicInteger()
        val beforeCreate = CompletableDeferred<ScheduledRun>()
        val beforePrompt = CompletableDeferred<ScheduledRun>()
        val allowCreate = CompletableDeferred<Unit>()
        val allowPrompt = CompletableDeferred<Unit>()
        override suspend fun create(scope: ScheduledScope, run: ScheduledRun): ClientSessionId {
            beforeCreate.complete(ledger(home).runs.single { it.id == run.id })
            if (gated) allowCreate.await()
            creates.incrementAndGet()
            return delegate.create(scope, run)
        }
        override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt {
            beforePrompt.complete(ledger(home).runs.single { it.id == run.id })
            if (gated) allowPrompt.await()
            prompts.incrementAndGet()
            return delegate.prompt(scope, run)
        }
    }

    private class MutableClock(initial: Long) : ScheduledClock {
        private val now = AtomicLong(initial)
        override fun nowEpochMillis(): Long = now.get()
        fun set(value: Long) { now.set(value) }
    }

    /** Pause before a real CAS or after its successful durable result; return the delegate result unchanged. */
    private class CommitGateStore(private val delegate: ScheduledStore, private val phase: ScheduledRunPhase,
        private val afterCommit: Boolean) : ScheduledStore by delegate {
        val paused = CompletableDeferred<ScheduledSnapshot>()
        val resume = CompletableDeferred<Unit>()
        val result = CompletableDeferred<ScheduledWriteResult>()
        private val used = AtomicBoolean(false)
        override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
            isCurrentScope: () -> Boolean): ScheduledWriteResult {
            val pause = snapshot.runs.singleOrNull()?.phase == phase && used.compareAndSet(false, true)
            if (pause && !afterCommit) {
                paused.complete(snapshot)
                resume.await()
            }
            val actual = delegate.compareAndSet(owner, expectedRevision, snapshot, isCurrentScope)
            if (pause) {
                result.complete(actual)
                if (afterCommit && actual is ScheduledWriteResult.Committed) {
                    paused.complete(actual.snapshot)
                    resume.await()
                }
            }
            return actual
        }
    }

    private class ControlledAdapter : LlmAdapter {
        val started = CompletableDeferred<GenerateOptions>()
        val finish = CompletableDeferred<Unit>()
        val streams = AtomicInteger()
        override suspend fun resolveModel(provider: String, model: String): LlmResolvedModelInfo {
            require(provider == "scripted" && model == "test")
            return LlmResolvedModelInfo(provider, model, "Controlled local model")
        }
        override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
            streams.incrementAndGet()
            emit(BlockStartChunk(0, "text"))
            emit(TextDeltaChunk(0, "controlled local answer"))
            started.complete(options)
            finish.await()
            emit(FinishChunk(StopFinishReason))
        }
    }

    private suspend fun temporary(block: suspend (Path) -> Unit) {
        val home = Files.createTempDirectory("harness-scheduled-executor-home-").toAbsolutePath().normalize()
        try { block(home) } finally { home.toFile().deleteRecursively() }
    }
}

package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.session.persistence.SessionDatabaseAccess
import java.io.BufferedWriter
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real subprocess crashes. Temporary homes/transcripts are retained for inspection.
 * Forced fixture cleanup is not a production safe-shutdown guarantee. */
class DesktopScheduledExecutorCrashProcessTest {
    @Test
    fun creatingSessionCommittedBeforeCreateCrashRecoversUnknownWithoutCreating() = runBlocking {
        crash(CrashWindow.CREATING_COMMITTED_BEFORE_CREATE)
    }

    @Test
    fun dispatchingCommittedBeforePromptCrashRecoversUnknownWithoutResending() = runBlocking {
        crash(CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT)
    }

    @Test
    fun promptReturnedBeforeAcceptedCasCrashRecoversUnknownWithoutResending() = runBlocking {
        crash(CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS)
    }

    @Test
    fun acceptedOpenTurnCrashCannotUseRecoveryGeneratedInterruptedAsOldTerminal() = runBlocking {
        crash(CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN)
    }

    @Test
    fun completedTurnEndBeforeTerminalCasCrashRepairsOnlyExactLedgerResult() = runBlocking {
        crash(CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS)
    }

    private suspend fun crash(window: CrashWindow) {
        val fixture = CrashProcesses(window)
        var failure: Throwable? = null
        try {
            exercise(fixture, window)
        } catch (caught: Throwable) {
            failure = caught
            try { Files.writeString(fixture.home.resolve("parent-failure.txt"), caught.stackTraceToString()) }
            catch (diagnostic: Throwable) { caught.addSuppressed(diagnostic) }
            throw caught
        } finally {
            val errors = fixture.close()
            if (errors.isNotEmpty()) {
                val primary = failure ?: AssertionError("Fixture cleanup failed; home=${fixture.home}")
                errors.forEach(primary::addSuppressed)
                if (failure == null) throw primary
            }
        }
    }

    private suspend fun exercise(fixture: CrashProcesses, window: CrashWindow) {
        val holder = fixture.start(CrashRole.HOLDER)
        val gateDeadline = deadline()
        holder.await("READY", until = gateDeadline)
        val claimed = holder.await("CLAIMED", until = gateDeadline)
        val gateReply = holder.await("GATE", until = gateDeadline)
        val gate = assertNotNull(gateReply.gate)
        val disk = readLedger(fixture.home)
        fixture.saveReply("gate.json", gateReply)
        fixture.saveSnapshot("disk-before-crash.json", disk)
        assertEquals(gate.disk, disk, "Parent reads actual committed ledger")
        assertEquals(disk, gateReply.snapshot)
        assertEquals(gateReply, holder.persistedReply(), "Gate counters also persisted on disk")
        assertEquals(window, gate.window)
        assertEquals(true, gateReply.leaderHeld)
        assertEquals(1, gateReply.lifecycle)
        assertEquals(gate.candidateRevision, gate.candidate.revision)
        assertEquals(gate.oldRevision + 1, gate.candidateRevision)
        assertTrue(gate.oldRevision > 0)
        val original = disk.runs.single()
        assertIdentity(assertNotNull(claimed.run), original, allowProgress = true)
        assertEquals(DesktopScheduledExecutorCrashProcessHelper.ORIGINAL_REQUEST_ID, original.requestId)
        assertEquals(DesktopScheduledExecutorCrashProcessHelper.TASK_ID, original.task.id)
        assertEquals(DesktopScheduledExecutorCrashProcessHelper.binding.owner, disk.owner)
        assertTrue(disk.tasks.contains(original.task))
        assertEquals(2, disk.tasks.size)
        assertEquals(ScheduledDestination.Cwd(fixture.home.toString()),
            (original.task.target as ScheduledTarget.Cron).destination)
        assertTrue(original.id.isNotBlank() && original.inputMessageId.isNotBlank())
        assertTrue(original.candidateSessionId.value.isNotBlank())
        assertGate(window, gate, original, gateReply)
        fixture.assertStableLock()

        val beforeBytes = Files.readAllBytes(DesktopScheduledExecutorCrashProcessHelper.ledgerPath(fixture.home))
        val contender = fixture.start(CrashRole.CONTENDER)
        contender.await("READY")
        assertNotEquals(holder.process.pid(), contender.process.pid())
        val refusedId = contender.send(CrashCommandKind.TRY_START)
        val refused = contender.await("REFUSED", refusedId)
        assertEquals(1, refused.lifecycle)
        assertEquals("Unavailable", refused.startResult)
        assertTrue(assertNotNull(refused.reason).isNotBlank())
        assertEquals(false, refused.leaderHeld)
        assertEquals(0, refused.agentCount)
        assertZeroDelivery(refused)
        assertEquals(disk, refused.snapshot)
        val beforeSha = DesktopScheduledExecutorCrashProcessHelper.sha256(beforeBytes)
        assertEquals(beforeSha, refused.ledgerShaBefore)
        assertEquals(beforeSha, refused.ledgerShaAfter)
        assertTrue(beforeBytes.contentEquals(
            Files.readAllBytes(DesktopScheduledExecutorCrashProcessHelper.ledgerPath(fixture.home))))
        assertEquals(refused, contender.persistedReply())
        val heldId = holder.send(CrashCommandKind.HELD)
        val held = holder.await("HELD", heldId)
        assertEquals(true, held.leaderHeld, "Losing contender did not release holder lease")
        assertEquals(disk, held.snapshot)
        assertEquals(gateReply.counters, held.counters)
        assertTrue(holder.process.isAlive)
        fixture.assertStableLock()

        fixture.killHolder(holder)
        assertFalse(holder.process.isAlive)
        assertNotEquals(0, holder.process.exitValue(), "Forced death at the real gate")
        fixture.assertStableLock()
        val rawBefore = readRaw(fixture.home)
        fixture.saveRaw("raw-before-recovery.json", rawBefore)
        assertBeforeRaw(window, rawBefore, original, gate)
        assertEquals(disk, readLedger(fixture.home))

        val recoveryId = contender.send(CrashCommandKind.RECOVER)
        val recoveryDeadline = deadline()
        val childRaw = contender.await("RAW_BEFORE_RECOVERY", recoveryId, recoveryDeadline)
        assertEquals(2, childRaw.lifecycle)
        assertEquals(rawBefore, childRaw.rawRoom, "Independent raw DAO reads before load")
        assertEquals(disk, childRaw.snapshot)
        val recovered = contender.await("RECOVERED", recoveryId, recoveryDeadline)
        fixture.saveReply("recovered.json", recovered)
        assertEquals(2, recovered.lifecycle, "Fresh lifecycle after failed-start disposal")
        assertEquals("Started", recovered.startResult)
        assertEquals(true, recovered.leaderHeld)
        assertEquals(0, recovered.agentCount)
        assertZeroDelivery(recovered)
        assertEquals(recovered, contender.persistedReply(), "Recovery counters persisted on disk")
        fixture.assertStableLock()

        // Capture raw-after evidence BEFORE asserting Unknown. Production load/repairOpenTurn
        // may synthesize Interrupted; leave the genuine failure and its diagnostics intact.
        val checksId = contender.send(CrashCommandKind.CHECKS)
        val checksDeadline = deadline()
        val ticks = (1..4).map { index ->
            contender.await("TICK", checksId, checksDeadline).also { assertEquals(index, it.tick) }
        }
        val checked = contender.await("CHECKED", checksId, checksDeadline)
        fixture.saveReply("checked.json", checked)
        assertEquals(checked, contender.persistedReply(), "Repeated checks persist zero delivery counters")
        val closeId = contender.send(CrashCommandKind.CLOSE_RUNTIME)
        val closed = contender.await("RUNTIME_CLOSED", closeId)
        fixture.saveReply("runtime-closed.json", closed)
        assertEquals(closed, contender.persistedReply(), "Final counters survive Runtime disposal")
        assertEquals(false, closed.leaderHeld)
        assertTrue(assertNotNull(closed.cleanup).contains("not production shutdown evidence"))
        val rawAfter = readRaw(fixture.home)
        fixture.saveRaw("raw-after-recovery.json", rawAfter)
        assertEquals(rawAfter, closed.rawRoom)
        val finalDisk = readLedger(fixture.home)
        fixture.saveSnapshot("disk-after-recovery.json", finalDisk)
        assertEquals(finalDisk, closed.snapshot)
        val exitId = contender.send(CrashCommandKind.EXIT)
        contender.await("CLOSED", exitId)
        contender.awaitExit()
        assertEquals(0, contender.process.exitValue())
        fixture.assertStableLock()

        val expected = if (window == CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS)
            ScheduledRunPhase.Succeeded else ScheduledRunPhase.Unknown
        val phaseMessage = "Expected $expected, never a recovery-generated terminal; window=$window; " +
            "rawBefore=$rawBefore; rawAfter=$rawAfter; recovered=${recovered.run}; home=${fixture.home}"
        val snapshots = listOf(assertNotNull(recovered.snapshot)) +
            ticks.map { assertNotNull(it.snapshot) } + listOf(assertNotNull(checked.snapshot), finalDisk)
        snapshots.forEach { snapshot ->
            assertEquals(disk.owner, snapshot.owner)
            assertEquals(disk.tasks, snapshot.tasks)
            assertTrue(snapshot.revision >= disk.revision)
            val actual = snapshot.runs.single()
            assertIdentity(original, actual)
            assertEquals(expected, actual.phase, phaseMessage)
            if (expected == ScheduledRunPhase.Unknown) {
                assertTrue(actual.blocksTask)
                assertNull(actual.terminal)
            } else assertEquals(gate.candidate.runs.single().terminal, actual.terminal)
        }
        assertEquals(true, checked.duplicate)
        assertIdentity(original, assertNotNull(checked.duplicateRun))
        assertEquals(expected, checked.duplicateRun?.phase, phaseMessage)
        if (expected == ScheduledRunPhase.Unknown) {
            assertEquals(true, checked.overlapRefused)
            assertEquals(true, checked.capacityRefused)
        }
        (ticks + checked + closed).forEach { reply ->
            assertEquals(2, reply.lifecycle)
            assertZeroDelivery(reply)
            if (reply.event != "RUNTIME_CLOSED") {
                assertEquals(0, reply.agentCount)
                assertEquals(true, reply.leaderHeld)
            }
        }
        assertEquals(checked.counters, closed.counters)
        if (original.deliveryIntent) assertTrue(checked.counters.inspections > 0)
        else assertEquals(0, checked.counters.inspections)
        assertAfterRaw(window, rawBefore, rawAfter, original, gate)
    }

    private fun assertGate(window: CrashWindow, gate: CrashGate, run: ScheduledRun, reply: CrashReply) {
        val candidate = gate.candidate.runs.single()
        val committed = window in setOf(CrashWindow.CREATING_COMMITTED_BEFORE_CREATE,
            CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT, CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN)
        assertEquals(committed, gate.committedAcknowledgement)
        assertEquals(if (committed) gate.candidateRevision else gate.oldRevision, gate.disk.revision)
        assertEquals(gate.counters, reply.counters)
        assertIdentity(run, candidate, allowProgress = true)
        val counters = reply.counters
        val created = if (window == CrashWindow.CREATING_COMMITTED_BEFORE_CREATE) 0 else 1
        val prompted = if (window in setOf(CrashWindow.CREATING_COMMITTED_BEFORE_CREATE,
                CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT)) 0 else 1
        assertEquals(created, counters.creates)
        assertEquals(created, counters.createReturns)
        assertEquals(prompted, counters.prompts)
        assertEquals(prompted, counters.promptReturns)
        assertEquals(prompted, counters.providerStreams)
        assertEquals(if (window == CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS) 1 else 0, counters.providerFinishes)
        // DefaultSessionApi.create creates the Agent before prompt or provider execution.
        assertEquals(created, reply.agentCount)
        assertNull(run.terminal)
        when (window) {
            CrashWindow.CREATING_COMMITTED_BEFORE_CREATE -> {
                assertEquals(ScheduledRunPhase.CreatingSession, run.phase)
                assertEquals(ScheduledRunPhase.CreatingSession, candidate.phase)
                assertFalse(run.deliveryIntent)
                assertNull(run.sessionRef)
                assertNull(run.receiptId)
                assertFalse(gate.flushAcknowledged)
            }
            CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT -> {
                assertEquals(ScheduledRunPhase.Dispatching, run.phase)
                assertEquals(ScheduledRunPhase.Dispatching, candidate.phase)
                assertTrue(run.deliveryIntent)
                assertEquals(run.candidateSessionId, run.sessionRef)
                assertNull(run.receiptId)
            }
            CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS -> {
                assertEquals(ScheduledRunPhase.Dispatching, run.phase)
                assertEquals(ScheduledRunPhase.Accepted, candidate.phase)
                assertTrue(run.deliveryIntent)
                assertNull(run.receiptId)
                assertEquals(assertNotNull(gate.returnedReceiptId), candidate.receiptId)
                assertFalse(gate.flushAcknowledged, "No extra pre-Accepted flush")
                assertOpenInput(gate.liveEvents, run)
            }
            CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN -> {
                assertEquals(ScheduledRunPhase.Accepted, run.phase)
                assertEquals(ScheduledRunPhase.Accepted, candidate.phase)
                assertEquals(assertNotNull(gate.returnedReceiptId), run.receiptId)
                assertTrue(gate.flushAcknowledged)
                assertOpenInput(gate.liveEvents, run)
            }
            CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS -> {
                assertEquals(ScheduledRunPhase.Accepted, run.phase)
                assertEquals(ScheduledRunPhase.Succeeded, candidate.phase)
                assertEquals(assertNotNull(gate.returnedReceiptId), run.receiptId)
                assertTrue(gate.flushAcknowledged)
                assertCompletedInput(gate.liveEvents, run, assertNotNull(candidate.terminal))
            }
        }
        if (prompted == 1) {
            assertEquals(run.candidateSessionId, run.sessionRef)
            assertTrue(run.deliveryIntent)
            assertNotEquals(run.inputMessageId, assertNotNull(gate.returnedReceiptId))
        }
    }

    private fun assertBeforeRaw(window: CrashWindow, raw: CrashRawRoom, run: ScheduledRun, gate: CrashGate) {
        if (window == CrashWindow.CREATING_COMMITTED_BEFORE_CREATE) {
            assertTrue(raw.sessions.isEmpty(), "No production create was called")
            return
        }
        assertTrue(raw.databaseExists)
        val session = raw.sessions.single()
        assertEquals(run.candidateSessionId.value, session.id)
        assertEquals(((run.task.target as ScheduledTarget.Cron).destination as ScheduledDestination.Cwd).path, session.cwd)
        when (window) {
            CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT -> {
                assertTrue(session.events.none { it.type == SessionEventNames.USER_MESSAGE })
                assertNull(DesktopScheduledSessionEvidence.terminalEvidence(session.events, run.inputMessageId))
            }
            CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS -> {
                // Live input is genuine; this boundary intentionally does not add a flush.
                assertNull(DesktopScheduledSessionEvidence.terminalEvidence(session.events, run.inputMessageId))
            }
            CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN -> assertOpenInput(session.events, run)
            CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS ->
                assertCompletedInput(session.events, run, assertNotNull(gate.candidate.runs.single().terminal))
            else -> error("Unexpected raw window $window")
        }
    }

    private fun assertAfterRaw(window: CrashWindow, before: CrashRawRoom, after: CrashRawRoom,
        run: ScheduledRun, gate: CrashGate) {
        assertEquals(before.databaseExists, after.databaseExists)
        assertEquals(before.sessions.map { it.id }, after.sessions.map { it.id })
        before.sessions.forEach { old ->
            val current = after.sessions.single { it.id == old.id }
            assertEquals(old.cwd, current.cwd)
            assertEquals(old.events, current.events.take(old.events.size), "Recovery preserves old raw events")
        }
        when (window) {
            CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN -> assertOpenInput(after.sessions.single().events, run)
            CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS -> {
                assertEquals(before, after, "Completed recovery repairs only ledger")
                assertCompletedInput(after.sessions.single().events, run, assertNotNull(gate.candidate.runs.single().terminal))
            }
            else -> after.sessions.forEach {
                assertNull(DesktopScheduledSessionEvidence.terminalEvidence(it.events, run.inputMessageId))
            }
        }
    }

    private fun input(events: List<SessionEventEnvelope>, run: ScheduledRun): Pair<SessionEventEnvelope, UserMessageEvent> {
        val users = events.filter { it.type == SessionEventNames.USER_MESSAGE }.map {
            it to protocolJson.decodeFromJsonElement(UserMessageEvent.serializer(), it.data)
        }.filter { it.second.message.id.value == run.inputMessageId }
        val user = users.single()
        assertTrue(user.second.turn > 0)
        val starts = events.filter { it.type == SessionEventNames.TURN_START }.map {
            protocolJson.decodeFromJsonElement(TurnStartEvent.serializer(), it.data)
        }.filter { it.turn == user.second.turn }
        assertEquals(1, starts.size, "Exact input has matching real TurnStart")
        assertEquals(user.second.turn, starts.single().turn)
        return user
    }

    private fun assertOpenInput(events: List<SessionEventEnvelope>, run: ScheduledRun) {
        val user = input(events, run)
        val ends = events.filter { it.type == SessionEventNames.TURN_END }.map {
            protocolJson.decodeFromJsonElement(TurnEndEvent.serializer(), it.data)
        }.filter { it.turn == user.second.turn }
        assertTrue(ends.isEmpty(), "Old open turn must not gain synthetic recovery terminal")
        assertNull(DesktopScheduledSessionEvidence.terminalEvidence(events, run.inputMessageId))
    }

    private fun assertCompletedInput(events: List<SessionEventEnvelope>, run: ScheduledRun,
        expected: ScheduledTerminalEvidence) {
        val user = input(events, run)
        val ends = events.filter { it.type == SessionEventNames.TURN_END }.map {
            it to protocolJson.decodeFromJsonElement(TurnEndEvent.serializer(), it.data)
        }.filter { it.second.turn == user.second.turn }
        val end = ends.single()
        assertEquals(CompletedTurnEndReason, end.second.reason)
        assertTrue(end.first.seq > user.first.seq)
        assertTrue(end.first.time >= user.first.time)
        assertEquals(ScheduledRunPhase.Succeeded, expected.outcome)
        assertEquals("completed", expected.reason)
        assertEquals(user.second.turn, expected.turn)
        assertEquals(user.first.seq, expected.userMessageSeq)
        assertEquals(end.first.seq, expected.turnEndSeq)
        assertEquals(end.first.time, expected.endedAt)
        assertEquals(expected, DesktopScheduledSessionEvidence.terminalEvidence(events, run.inputMessageId))
        assertNull(DesktopScheduledSessionEvidence.terminalEvidence(events, "wrong-${run.inputMessageId}"))
        val receipt = assertNotNull(run.receiptId)
        assertNotEquals(run.inputMessageId, receipt)
        assertNull(DesktopScheduledSessionEvidence.terminalEvidence(events, receipt), "Receipt is not input ID")
    }

    private fun assertIdentity(old: ScheduledRun, current: ScheduledRun, allowProgress: Boolean = false) {
        assertEquals(old.id, current.id)
        assertEquals(old.task, current.task)
        assertEquals(old.requestId, current.requestId)
        assertEquals(old.nominalAt, current.nominalAt)
        assertEquals(old.claimedAt, current.claimedAt)
        assertEquals(old.inputMessageId, current.inputMessageId)
        assertEquals(old.candidateSessionId, current.candidateSessionId)
        if (!allowProgress || old.sessionRef != null) assertEquals(old.sessionRef, current.sessionRef)
        if (!allowProgress || old.receiptId != null) assertEquals(old.receiptId, current.receiptId)
        if (!allowProgress) assertEquals(old.deliveryIntent, current.deliveryIntent)
    }

    private fun assertZeroDelivery(reply: CrashReply) {
        assertEquals(0, reply.counters.creates)
        assertEquals(0, reply.counters.createReturns)
        assertEquals(0, reply.counters.prompts)
        assertEquals(0, reply.counters.promptReturns)
        assertEquals(0, reply.counters.providerStreams)
        assertEquals(0, reply.counters.providerFinishes)
    }

    private suspend fun readLedger(home: Path): ScheduledSnapshot = withTimeout(stageMillis) {
        DesktopScheduledExecutorCrashProcessHelper.ledger(home)
    }
    private suspend fun readRaw(home: Path): CrashRawRoom = withTimeout(stageMillis) {
        DesktopScheduledExecutorCrashProcessHelper.rawRoom(home)
    }

    private class CrashProcesses(val window: CrashWindow) {
        val home: Path = Files.createTempDirectory("harness-scheduled-crash-${window.name.lowercase()}-")
            .toAbsolutePath().normalize()
        private val children = mutableListOf<Process>()
        private val clients = mutableListOf<Child>()
        private val lock = DesktopScheduledExecutorCrashProcessHelper.leaderPath(home)
        private val originalLockKey: Any?
        init {
            Files.createFile(lock)
            originalLockKey = Files.readAttributes(lock, BasicFileAttributes::class.java).fileKey()
        }
        fun start(role: CrashRole): Child {
            val executable = Path.of(System.getProperty("java.home"), "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
            check(Files.isRegularFile(executable)) { "Child JVM unavailable: $executable" }
            val process = ProcessBuilder(executable.toString(), "-cp", crashClasspath(),
                DesktopScheduledExecutorCrashProcessHelper::class.java.name, home.toString(), role.name, window.name)
                .redirectError(home.resolve("${role.name.lowercase()}-stderr.log").toFile()).start()
            children.add(process) // Immediate registration before any constructor or READY failure.
            assertNotEquals(ProcessHandle.current().pid(), process.pid())
            return Child(process, home, role, window).also {
                clients.add(it)
                it.startReader()
            }
        }
        fun killHolder(holder: Child) {
            check(children.contains(holder.process) && holder.role == CrashRole.HOLDER)
            check(holder.process.isAlive) { "Holder exited before crash; home=$home" }
            holder.process.destroyForcibly()
            check(holder.process.waitFor(cleanupMillis, TimeUnit.MILLISECONDS)) { "Killed holder did not exit; home=$home" }
        }
        fun assertStableLock() {
            assertTrue(Files.isRegularFile(lock), "Stable leader.lock must never be deleted")
            if (originalLockKey != null) assertEquals(originalLockKey,
                Files.readAttributes(lock, BasicFileAttributes::class.java).fileKey())
        }
        fun saveReply(name: String, reply: CrashReply) {
            Files.writeString(home.resolve(name), protocolJson.encodeToString(CrashReply.serializer(), reply))
        }
        fun saveSnapshot(name: String, snapshot: ScheduledSnapshot) {
            Files.writeString(home.resolve(name), protocolJson.encodeToString(ScheduledSnapshot.serializer(), snapshot))
        }
        fun saveRaw(name: String, raw: CrashRawRoom) {
            Files.writeString(home.resolve(name), protocolJson.encodeToString(CrashRawRoom.serializer(), raw))
        }
        fun close(): List<Throwable> {
            val errors = mutableListOf<Throwable>()
            // Signal all registered children, await all, then close IO. Never delete home.
            children.forEach { process ->
                try { if (process.isAlive) process.destroyForcibly() } catch (failure: Throwable) { errors.add(failure) }
            }
            children.forEach { process ->
                try { check(process.waitFor(cleanupMillis, TimeUnit.MILLISECONDS)) { "Fixture child ${process.pid()} alive; home=$home" } }
                catch (failure: Throwable) { errors.add(failure) }
            }
            if (children.none { it.isAlive }) clients.forEach { child ->
                try { errors.addAll(child.closeIo()) } catch (failure: Throwable) { errors.add(failure) }
            }
            else errors.add(AssertionError("Live child: preserve home and pipes: $home"))
            clients.forEach { child ->
                try { child.saveTranscript() } catch (failure: Throwable) { errors.add(failure) }
            }
            return errors
        }
    }

    private sealed interface Output {
        data class Reply(val reply: CrashReply) : Output
        data class Failure(val error: Throwable) : Output
        data object Eof : Output
    }

    private class Child(val process: Process, val home: Path, val role: CrashRole, val window: CrashWindow) {
        private val queue = LinkedBlockingQueue<Output>()
        private val transcript = StringBuffer()
        private val io = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "scheduled-crash-${role.name.lowercase()}-io").apply { isDaemon = true }
        }
        private val reader = process.inputStream.bufferedReader()
        private val writer: BufferedWriter = process.outputStream.bufferedWriter()
        private var sequence = 0L
        private var commandSequence = 0
        private var lastCounters = CrashCounters()
        fun startReader() {
            io.submit {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        transcript.append(line).append(10.toChar())
                        if (line.startsWith(DesktopScheduledExecutorCrashProcessHelper.PREFIX)) {
                            val reply = protocolJson.decodeFromString(CrashReply.serializer(),
                                line.removePrefix(DesktopScheduledExecutorCrashProcessHelper.PREFIX))
                            queue.put(Output.Reply(reply))
                        }
                    }
                    queue.put(Output.Eof)
                } catch (failure: Throwable) { queue.offer(Output.Failure(failure)) }
            }
        }
        fun send(kind: CrashCommandKind): String {
            check(process.isAlive) { "Child exited before $kind; home=$home" }
            val id = "${role.name.lowercase()}-${++commandSequence}-$kind"
            val command = CrashCommand(id, kind)
            val future = io.submit {
                writer.write(protocolJson.encodeToString(CrashCommand.serializer(), command))
                writer.newLine()
                writer.flush()
            }
            try { future.get(stageMillis, TimeUnit.MILLISECONDS) }
            catch (failure: Throwable) { future.cancel(true); throw AssertionError("Protocol write $kind failed; home=$home", failure) }
            return id
        }
        fun await(event: String, commandId: String? = null, until: Long = deadline()): CrashReply {
            while (true) {
                val remaining = until - System.nanoTime()
                check(remaining > 0) { "Timed out awaiting $event/$commandId; home=$home; transcript=$transcript" }
                when (val output = queue.poll(remaining, TimeUnit.NANOSECONDS)) {
                    null -> error("Timed out awaiting $event/$commandId; home=$home; transcript=$transcript")
                    Output.Eof -> error("Child stdout ended awaiting $event; home=$home; transcript=$transcript")
                    is Output.Failure -> throw AssertionError("Child protocol read failed; home=$home", output.error)
                    is Output.Reply -> {
                        val reply = output.reply
                        assertEquals(process.pid(), reply.pid)
                        assertEquals(home.toString(), reply.home)
                        assertEquals(role, reply.role)
                        assertEquals(window, reply.window)
                        assertEquals(sequence + 1, reply.sequence)
                        sequence = reply.sequence
                        assertMonotonicCounters(lastCounters, reply.counters)
                        lastCounters = reply.counters
                        if (reply.event == "FAILURE") error("Child failure; home=$home: ${reply.error}")
                        if (reply.event == "EFFECT") {
                            assertTrue(assertNotNull(reply.effect).isNotBlank())
                            continue
                        }
                        assertEquals(event, reply.event, "Unexpected protocol event; home=$home")
                        assertEquals(commandId, reply.commandId)
                        return reply
                    }
                }
            }
        }
        fun persistedReply(): CrashReply = protocolJson.decodeFromString(CrashReply.serializer(),
            Files.readString(home.resolve("${role.name.lowercase()}-protocol.json")))
        fun awaitExit() {
            val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cleanupMillis)
            check(process.waitFor(cleanupMillis, TimeUnit.MILLISECONDS)) { "Child failed to exit; home=$home" }
            val remaining = until - System.nanoTime()
            check(remaining > 0) { "No time left for child stdout EOF; home=$home" }
            when (val output = queue.poll(remaining, TimeUnit.NANOSECONDS)) {
                Output.Eof -> Unit
                is Output.Failure -> throw AssertionError("Late child protocol read failure; home=$home", output.error)
                is Output.Reply -> error("Unexpected protocol after CLOSED: ${output.reply}; home=$home")
                null -> error("Timed out awaiting child stdout EOF; home=$home")
            }
        }
        fun saveTranscript() {
            Files.writeString(home.resolve("${role.name.lowercase()}-stdout.log"), transcript.toString())
        }
        fun closeIo(): List<Throwable> {
            check(!process.isAlive)
            val errors = mutableListOf<Throwable>()
            io.shutdownNow()
            listOf<() -> Unit>({ writer.close() }, { reader.close() }, { process.errorStream.close() }).forEach { close ->
                try { close() } catch (failure: Throwable) { errors.add(failure) }
            }
            try {
                check(io.awaitTermination(cleanupMillis, TimeUnit.MILLISECONDS)) { "IO threads did not terminate; home=$home" }
            } catch (failure: Throwable) { errors.add(failure) }
            try { saveTranscript() }
            catch (failure: Throwable) { errors.add(failure) }
            return errors
        }
    }

    companion object {
        private const val stageMillis = DesktopScheduledExecutorCrashProcessHelper.STAGE_TIMEOUT_MILLIS
        private const val cleanupMillis = DesktopScheduledExecutorCrashProcessHelper.CLEANUP_TIMEOUT_MILLIS
        private val protocolJson: Json get() = DesktopScheduledExecutorCrashProcessHelper.json
        private fun deadline(): Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(stageMillis)
        private fun assertMonotonicCounters(old: CrashCounters, new: CrashCounters) {
            val before = listOf(old.creates, old.createReturns, old.prompts, old.promptReturns,
                old.providerStreams, old.providerFinishes, old.inspections)
            val after = listOf(new.creates, new.createReturns, new.prompts, new.promptReturns,
                new.providerStreams, new.providerFinishes, new.inspections)
            before.zip(after).forEach { (a, b) -> assertTrue(b >= a, "Cumulative side-effect count decreased") }
        }
        /** Source entries first, then Amper URLClassLoader ancestors and launcher entries. */
        private fun crashClasspath(): String {
            val entries = linkedSetOf<Path>()
            fun addUrl(url: URL) {
                when (url.protocol) {
                    "file" -> entries.add(Path.of(url.toURI()).toAbsolutePath().normalize())
                    "jar" -> addUrl((url.openConnection() as JarURLConnection).jarFileURL)
                    else -> error("Cannot use $url as child JVM classpath")
                }
            }
            val required = listOf(DesktopScheduledExecutorCrashProcessHelper::class.java,
                DesktopScheduledStore::class.java, DesktopScheduledLeaderLease::class.java,
                ScheduledScope::class.java, HostId::class.java, Runtime::class.java, ApiGateway::class.java,
                SessionPlugin::class.java, LlmPlugin::class.java, AgentPlugin::class.java,
                AgentLoopPlugin::class.java, SessionApiPlugin::class.java, SessionDatabaseAccess::class.java,
                LocalConnection::class.java, Json::class.java, kotlinx.serialization.Serializable::class.java,
                Dispatchers::class.java, Unit::class.java)
            required.forEach { type ->
                val location = type.protectionDomain?.codeSource?.location
                if (location != null) addUrl(location) else {
                    val name = type.name.replace('.', '/') + ".class"
                    val resource = requireNotNull(type.getResource("/$name")) { "No classpath source for $name" }
                    if (resource.protocol == "file") {
                        var path = Path.of(resource.toURI())
                        repeat(name.split('/').size) { path = path.parent }
                        entries.add(path.toAbsolutePath().normalize())
                    } else addUrl(resource)
                }
            }
            val visited = mutableSetOf<ClassLoader>()
            (required.map { it.classLoader } + Thread.currentThread().contextClassLoader).forEach { loader ->
                var current = loader
                while (current != null && visited.add(current)) {
                    if (current is URLClassLoader) current.getURLs().forEach(::addUrl)
                    current = current.parent
                }
            }
            System.getProperty("java.class.path").orEmpty().split(File.pathSeparatorChar)
                .filter { it.isNotBlank() }.forEach { entries.add(Path.of(it).toAbsolutePath().normalize()) }
            check(entries.isNotEmpty()) { "No child JVM classpath" }
            return entries.joinToString(File.pathSeparator)
        }
    }
}

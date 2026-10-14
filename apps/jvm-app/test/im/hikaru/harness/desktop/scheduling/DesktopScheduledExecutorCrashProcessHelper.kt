package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.agents
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
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.system.exitProcess

/** Test-owned subprocess entry point. Every gate decorates a real production CAS; none
 * supplies a synthetic run, prompt receipt or terminal event. The parent kills the holder
 * at GATE and owns process deadlines/cleanup. Runtime cleanup here is fixture cleanup,
 * never a claim that an unresolved production executor can safely shut down. */
object DesktopScheduledExecutorCrashProcessHelper {
    const val PREFIX = "HARNESS_SCHEDULED_CRASH "
    const val STAGE_TIMEOUT_MILLIS = 30_000L
    const val CLEANUP_TIMEOUT_MILLIS = 10_000L
    const val TASK_ID = "crash-task"
    const val CAPACITY_TASK_ID = "capacity-task"
    const val ORIGINAL_REQUEST_ID = "crash-original-request"
    val host = HostId("scheduled-executor-crash-process")
    val binding = ScheduledScope(host, null, 1)
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val initialTime = Instant.parse("2026-01-05T08:59:00Z").toEpochMilli()

    fun ledgerPath(home: Path): Path = home.resolve("scheduled-tasks.json")
    fun leaderPath(home: Path): Path = home.resolve("leader.lock")
    fun databasePath(home: Path): Path = home.resolve("sessions.db")

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "Arguments: absolute fixture home, HOLDER|CONTENDER, crash window" }
        val home = Path.of(args[0])
        require(home.isAbsolute && home.normalize() == home && Files.isDirectory(home))
        val role = CrashRole.valueOf(args[1])
        val window = CrashWindow.valueOf(args[2])
        val counters = SideEffects()
        val protocol = Protocol(home, role, window, counters)
        try {
            runBlocking {
                protocol.send(CrashReply(event = "READY"))
                when (role) {
                    CrashRole.HOLDER -> holder(home, window, protocol, counters)
                    CrashRole.CONTENDER -> contender(home, protocol, counters)
                }
            }
        } catch (failure: Throwable) {
            failure.printStackTrace(System.err)
            protocol.send(CrashReply(event = "FAILURE", error = failure.stackTraceToString()))
            exitProcess(1)
        }
    }

    private suspend fun holder(home: Path, window: CrashWindow, protocol: Protocol, counters: SideEffects) {
        seed(home)
        val fixture = Fixture.create(home, window, 1, protocol, counters)
        try {
            check(fixture.executor.start(binding) == ScheduledStartResult.Started)
            check(fixture.leader.isHeld())
            val claim = fixture.executor.runNow(TASK_ID, ORIGINAL_REQUEST_ID)
            check(claim is ScheduledClaimResult.Claimed && !claim.duplicate) { "Actual runNow refused: $claim" }
            protocol.send(CrashReply(event = "CLAIMED", lifecycle = 1, run = claim.run,
                snapshot = ledger(home), leaderHeld = fixture.leader.isHeld()))
            // Only the terminal window releases the real provider. Other holders die while
            // its Deferred is unresolved; cancellation never stands in for normal completion.
            if (window == CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS) {
                withTimeout(STAGE_TIMEOUT_MILLIS) { fixture.adapter.started.await() }
                fixture.adapter.finish.complete(Unit)
            }
            val gate = withTimeout(STAGE_TIMEOUT_MILLIS) { fixture.gates.reached.await() }
            protocol.send(CrashReply(event = "GATE", lifecycle = 1, gate = gate,
                snapshot = ledger(home), run = gate.disk.runs.single(),
                agentCount = fixture.runtime.context.agents.list().size, leaderHeld = fixture.leader.isHeld()))
            while (true) {
                val command = readCommand()
                when (command.command) {
                    CrashCommandKind.HELD -> protocol.send(CrashReply(event = "HELD", commandId = command.id,
                        lifecycle = 1, snapshot = ledger(home), leaderHeld = fixture.leader.isHeld(),
                        agentCount = fixture.runtime.context.agents.list().size))
                    CrashCommandKind.EXIT -> {
                        fixture.disposeForFixture()
                        protocol.send(CrashReply(event = "CLOSED", commandId = command.id, lifecycle = 1,
                            cleanup = "forced-fixture-cleanup; not production shutdown evidence"))
                        return
                    }
                    else -> error("Holder cannot execute ${command.command}")
                }
            }
        } finally {
            fixture.disposeForFixture()
        }
    }

    private suspend fun contender(home: Path, protocol: Protocol, counters: SideEffects) {
        var rejectedLifecycleClosed = false
        var recoveryStarted = false
        var fixture: Fixture? = null
        try {
            while (true) {
                val command = readCommand()
                when (command.command) {
                    CrashCommandKind.TRY_START -> {
                        check(!rejectedLifecycleClosed && fixture == null)
                        val before = ledgerSha(home)
                        val rejected = Fixture.create(home, null, 1, protocol, counters)
                        val reply = try {
                            val result = rejected.executor.start(binding)
                            check(result is ScheduledStartResult.Unavailable) { "Holder lock did not refuse actual start: $result" }
                            check(!rejected.leader.isHeld())
                            check(counters.snapshot().hasNoDelivery())
                            check(rejected.runtime.context.agents.list().isEmpty())
                            // Exercise release of the failed contender's own lease. The parent
                            // subsequently asks HELD to prove the holder's lease survived.
                            rejected.leader.release()
                            CrashReply(event = "REFUSED", commandId = command.id, lifecycle = 1,
                                startResult = "Unavailable", reason = result.reason,
                                snapshot = ledger(home), agentCount = 0, leaderHeld = false,
                                ledgerShaBefore = before, ledgerShaAfter = ledgerSha(home))
                        } finally {
                            rejected.disposeForFixture()
                        }
                        check(before == ledgerSha(home)) { "Rejected lifecycle changed ledger bytes" }
                        rejectedLifecycleClosed = true
                        protocol.send(reply.copy(cleanup = "failed lifecycle disposed before takeover"))
                    }
                    CrashCommandKind.RECOVER -> {
                        check(rejectedLifecycleClosed && !recoveryStarted && fixture == null) {
                            "Takeover requires a fresh second lifecycle after refused start"
                        }
                        recoveryStarted = true
                        // No Runtime is alive in this JVM at this point. The caller first kills
                        // and awaits holder exit. This factory uses raw DAO, never repairing load.
                        val before = rawRoom(home)
                        protocol.send(CrashReply(event = "RAW_BEFORE_RECOVERY", commandId = command.id,
                            lifecycle = 2, rawRoom = before, snapshot = ledger(home)))
                        val recovered = Fixture.create(home, null, 2, protocol, counters)
                        fixture = recovered
                        val result = recovered.executor.start(binding)
                        check(result == ScheduledStartResult.Started) { "Fresh takeover unavailable: $result" }
                        check(recovered.leader.isHeld())
                        check(counters.snapshot().hasNoDelivery())
                        check(recovered.runtime.context.agents.list().isEmpty())
                        val snapshot = ledger(home)
                        protocol.send(CrashReply(event = "RECOVERED", commandId = command.id, lifecycle = 2,
                            startResult = "Started", snapshot = snapshot, run = snapshot.runs.single(),
                            agentCount = 0, leaderHeld = true))
                        // Deliberately report the actual recovered phase. The parent must expect
                        // Unknown for an open durable turn, even if production load repairs it
                        // to Interrupted. This helper never substitutes an evidence provider.
                    }
                    CrashCommandKind.CHECKS -> {
                        val live = requireNotNull(fixture)
                        val before = ledger(home)
                        val original = before.runs.single()
                        val duplicate = live.executor.runNow(original.task.id, original.requestId)
                        check(duplicate is ScheduledClaimResult.Claimed && duplicate.duplicate)
                        check(duplicate.run.id == original.id)
                        var overlap: ScheduledClaimResult? = null
                        var capacity: ScheduledClaimResult? = null
                        if (original.blocksTask) {
                            overlap = live.executor.runNow(original.task.id, "crash-new-overlap-request")
                            capacity = live.executor.runNow(CAPACITY_TASK_ID, "crash-new-capacity-request")
                            check(overlap is ScheduledClaimResult.Refused) { "Unresolved run admitted same-task overlap" }
                            check(capacity is ScheduledClaimResult.Refused) { "Unresolved run freed global capacity" }
                        }
                        repeat(4) { index ->
                            // Advance only the real scheduler clock, past reconciliation backoff.
                            // Both tasks are paused, so ticks cannot add unrelated due work.
                            live.clock.advance(2_000)
                            live.executor.tick()
                            protocol.send(CrashReply(event = "TICK", commandId = command.id, lifecycle = 2,
                                tick = index + 1, snapshot = ledger(home),
                                agentCount = live.runtime.context.agents.list().size,
                                leaderHeld = live.leader.isHeld()))
                        }
                        check(counters.snapshot().hasNoDelivery())
                        check(live.runtime.context.agents.list().isEmpty())
                        val after = ledger(home)
                        check(after.runs.size == 1) { "Recovery reconstructed a new request" }
                        protocol.send(CrashReply(event = "CHECKED", commandId = command.id, lifecycle = 2,
                            snapshot = after, run = after.runs.single(), duplicate = true,
                            duplicateRun = duplicate.run, overlapRefused = overlap is ScheduledClaimResult.Refused,
                            capacityRefused = capacity is ScheduledClaimResult.Refused,
                            agentCount = 0, leaderHeld = live.leader.isHeld()))
                    }
                    CrashCommandKind.CLOSE_RUNTIME -> {
                        val live = requireNotNull(fixture)
                        live.disposeForFixture()
                        fixture = null
                        val raw = rawRoom(home)
                        protocol.send(CrashReply(event = "RUNTIME_CLOSED", commandId = command.id, lifecycle = 2,
                            rawRoom = raw, snapshot = ledger(home), leaderHeld = false,
                            cleanup = "forced-fixture-cleanup; not production shutdown evidence"))
                    }
                    CrashCommandKind.EXIT -> {
                        fixture?.disposeForFixture()
                        fixture = null
                        protocol.send(CrashReply(event = "CLOSED", commandId = command.id,
                            cleanup = "all child Runtime lifecycles disposed"))
                        return
                    }
                    else -> error("Contender cannot execute ${command.command}")
                }
            }
        } finally {
            fixture?.disposeForFixture()
        }
    }

    private suspend fun readCommand(): CrashCommand = withTimeout(STAGE_TIMEOUT_MILLIS) {
        val line = runInterruptible(Dispatchers.IO) { readlnOrNull() }
            ?: error("Protocol stdin closed without EXIT")
        json.decodeFromString(CrashCommand.serializer(), line)
    }

    suspend fun ledger(home: Path): ScheduledSnapshot {
        val actual = DesktopScheduledStore(ledgerPath(home)).read(binding.owner)
        check(actual is ScheduledReadResult.Ready) { "Real ledger is not readable: $actual" }
        return actual.snapshot
    }

    fun ledgerSha(home: Path): String = sha256(Files.readAllBytes(ledgerPath(home)))

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Call only with no fixture Runtime alive in this JVM and no live holder. Reads
     * existing Room rows through its DAO; does not restore/repair a Session or Agent. */
    suspend fun rawRoom(home: Path): CrashRawRoom {
        if (!Files.exists(databasePath(home))) {
            // CreatingSession pauses before the first create. Room may not have opened
            // SQLite yet; report this actual absence rather than creating an empty DB.
            val run = ledger(home).runs.single()
            check(run.sessionRef == null && !run.deliveryIntent) { "A created session lost its Room database" }
            return CrashRawRoom(databaseExists = false, sessions = emptyList())
        }
        check(Files.isRegularFile(databasePath(home))) { "Room database path is not a regular file" }
        val access = jvmSessionPersistenceDatabaseFactory(databasePath(home).toFile()).create()
        try {
            val sessions = access.dao.listSessions().map { row ->
                CrashRawSession(row.sessionId, row.cwd, access.dao.findEvents(row.sessionId).map { event ->
                    SessionEventEnvelope(event.type, event.seq, event.time, json.parseToJsonElement(event.data),
                        event.surfaceOp?.let(SessionSurfaceOperation::valueOf),
                        event.sourceEventSeqs?.let { json.decodeFromString(ListSerializer(Long.serializer()), it) },
                        event.ignorable)
                })
            }
            return CrashRawRoom(databaseExists = true, sessions = sessions)
        } finally {
            access.dispose()
        }
    }

    private suspend fun seed(home: Path) {
        val store = DesktopScheduledStore(ledgerPath(home))
        check(store.read(binding.owner) == ScheduledReadResult.Missing) { "Fixture home contains an existing ledger" }
        fun task(id: String) = ScheduledTask(id, 1, "real crash-window execution", "controlled crash-window prompt",
            ScheduledTarget.Cron(ScheduledDestination.Cwd(home.toString())), ScheduledRule.Daily(9, 0, "UTC"),
            ScheduledModelRef("scripted", "test"))
        val actual = store.compareAndSet(binding.owner, 0,
            ScheduledSnapshot(binding.owner, 1, listOf(task(TASK_ID), task(CAPACITY_TASK_ID)))) { true }
        check(actual is ScheduledWriteResult.Committed && actual.durabilityWarning == null)
    }

    private class SideEffects {
        val creates = AtomicInteger()
        val createReturns = AtomicInteger()
        val prompts = AtomicInteger()
        val promptReturns = AtomicInteger()
        val streams = AtomicInteger()
        val finishes = AtomicInteger()
        val inspections = AtomicInteger()
        fun snapshot() = CrashCounters(creates.get(), createReturns.get(), prompts.get(), promptReturns.get(),
            streams.get(), finishes.get(), inspections.get())
    }

    private class Protocol(val home: Path, val role: CrashRole, val window: CrashWindow, val counters: SideEffects) {
        private val serial = AtomicLong()
        @Synchronized
        fun send(reply: CrashReply) {
            val actual = reply.copy(sequence = serial.incrementAndGet(), pid = ProcessHandle.current().pid(),
                home = home.toString(), role = role, window = window, counters = counters.snapshot())
            val line = json.encodeToString(CrashReply.serializer(), actual)
            // Counters survive loss of the parent's in-memory transcript. Only this fixture's
            // files are written. stdout protocol lines are distinct from Runtime diagnostics.
            FileChannel.open(home.resolve("${role.name.lowercase()}-protocol.json"),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { channel ->
                val bytes = ByteBuffer.wrap(line.toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            println(PREFIX + line)
            System.out.flush()
        }
        fun effect(name: String, lifecycle: Int) = send(CrashReply(event = "EFFECT", effect = name, lifecycle = lifecycle))
    }

    private class Clock : ScheduledClock {
        private val now = AtomicLong(initialTime)
        override fun nowEpochMillis(): Long = now.get()
        fun advance(millis: Long) { now.addAndGet(millis) }
    }

    private class Fixture private constructor(
        val runtime: Runtime, val adapter: Adapter, val leader: DesktopScheduledLeaderLease,
        val executor: ScheduledExecutor, val gates: Gates, val clock: Clock, private val ownerJob: Job,
    ) {
        private val disposed = AtomicBoolean()
        suspend fun disposeForFixture() {
            if (!disposed.compareAndSet(false, true)) return
            withContext(NonCancellable) {
                withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                    executor.invalidate()
                    try { ownerJob.cancelAndJoin() }
                    finally {
                        try { runtime.context.dispose() }
                        finally { leader.release() }
                    }
                }
            }
        }
        companion object {
            suspend fun create(home: Path, window: CrashWindow?, lifecycle: Int,
                protocol: Protocol, counters: SideEffects): Fixture {
                val runtime = Runtime()
                val job = SupervisorJob()
                val leader = DesktopScheduledLeaderLease(leaderPath(home))
                val adapter = Adapter(protocol, counters, lifecycle)
                try {
                    runtime.install(SessionPlugin())
                    runtime.install(LlmPlugin())
                    runtime.install(AgentPlugin())
                    runtime.context.llm.registerAdapter(listOf("scripted"), adapter)
                    runtime.install(AgentLoopPlugin())
                    runtime.install(sessionPersistencePluginForTest(databasePath(home).toString()))
                    runtime.install(SessionApiPlugin())
                    val gateway = ApiGateway()
                    gateway.registerSessionApi(runtime.context)
                    val access = Access(LocalScheduledSessionAccess(LocalConnection(gateway).session,
                        DesktopScheduledSessionEvidence(runtime.context, host)), protocol, counters, lifecycle)
                    val gates = Gates(DesktopScheduledStore(ledgerPath(home)), home, window, runtime, adapter, access, counters)
                    val clock = Clock()
                    val executor = ScheduledExecutor(gates, ScheduleEngine(DesktopScheduleCalendar()), access, leader,
                        CoroutineScope(job + Dispatchers.Default), clock,
                        ScheduledExecutorConfig(verifiedCronExecution = true, tickIntervalMillis = 3_600_000,
                            evidencePollMillis = 10, observationTimeoutMillis = STAGE_TIMEOUT_MILLIS,
                            reconciliationInitialDelayMillis = 10, reconciliationMaxDelayMillis = 100, maxConcurrentRuns = 1))
                    return Fixture(runtime, adapter, leader, executor, gates, clock, job)
                } catch (failure: Throwable) {
                    withContext(NonCancellable) {
                        withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                            try { job.cancelAndJoin() }
                            finally {
                                try { runtime.context.dispose() } finally { leader.release() }
                            }
                        }
                    }
                    throw failure
                }
            }
        }
    }

    private class Access(private val delegate: ScheduledSessionAccess, private val protocol: Protocol,
        private val counters: SideEffects, private val lifecycle: Int) : ScheduledSessionAccess by delegate {
        @Volatile var returnedReceipt: SessionPromptReceipt? = null
        override suspend fun create(scope: ScheduledScope, run: ScheduledRun): ClientSessionId {
            counters.creates.incrementAndGet()
            protocol.effect("create-invoked", lifecycle)
            val actual = delegate.create(scope, run)
            counters.createReturns.incrementAndGet()
            protocol.effect("create-returned", lifecycle)
            return actual
        }
        override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt {
            counters.prompts.incrementAndGet()
            protocol.effect("prompt-invoked", lifecycle)
            val actual = delegate.prompt(scope, run)
            returnedReceipt = actual
            counters.promptReturns.incrementAndGet()
            protocol.effect("prompt-returned", lifecycle)
            return actual
        }
        override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult {
            counters.inspections.incrementAndGet()
            protocol.effect("inspect-invoked", lifecycle)
            return delegate.inspect(scope, run)
        }
    }

    private class Adapter(private val protocol: Protocol, private val counters: SideEffects,
        private val lifecycle: Int) : LlmAdapter {
        val started = CompletableDeferred<GenerateOptions>()
        val finish = CompletableDeferred<Unit>()
        override suspend fun resolveModel(provider: String, model: String): LlmResolvedModelInfo {
            require(provider == "scripted" && model == "test")
            return LlmResolvedModelInfo(provider, model, "Controlled crash-window model")
        }
        override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
            counters.streams.incrementAndGet()
            protocol.effect("provider-stream-started", lifecycle)
            emit(BlockStartChunk(0, "text"))
            emit(TextDeltaChunk(0, "controlled crash-window answer"))
            started.complete(options)
            finish.await()
            emit(FinishChunk(StopFinishReason))
            counters.finishes.incrementAndGet()
            protocol.effect("provider-stream-finished", lifecycle)
        }
    }

    private class Gates(private val delegate: ScheduledStore, private val home: Path, private val window: CrashWindow?,
        private val runtime: Runtime, private val adapter: Adapter, private val access: Access,
        private val counters: SideEffects) : ScheduledStore by delegate {
        val reached = CompletableDeferred<CrashGate>()
        private val resume = CompletableDeferred<Unit>()
        private val used = AtomicBoolean()
        override suspend fun compareAndSet(owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
            isCurrentScope: () -> Boolean): ScheduledWriteResult {
            val run = snapshot.runs.singleOrNull()
            val selected = run != null && when (window) {
                CrashWindow.CREATING_COMMITTED_BEFORE_CREATE -> run.phase == ScheduledRunPhase.CreatingSession
                CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT -> run.phase == ScheduledRunPhase.Dispatching
                CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS, CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN ->
                    run.phase == ScheduledRunPhase.Accepted
                CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS -> run.phase == ScheduledRunPhase.Succeeded
                null -> false
            } && used.compareAndSet(false, true)
            val afterCommit = window == CrashWindow.CREATING_COMMITTED_BEFORE_CREATE ||
                window == CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT || window == CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN
            if (selected && !afterCommit) pause(expectedRevision, snapshot, false)
            val actual = delegate.compareAndSet(owner, expectedRevision, snapshot, isCurrentScope)
            if (selected && afterCommit) {
                check(actual is ScheduledWriteResult.Committed && actual.snapshot == snapshot && actual.durabilityWarning == null)
                pause(expectedRevision, actual.snapshot, true)
            }
            return actual
        }

        private suspend fun pause(expectedRevision: Long, candidate: ScheduledSnapshot, committed: Boolean) {
            val run = candidate.runs.single()
            var liveEvents: List<SessionEventEnvelope> = emptyList()
            var flushAcknowledged = false
            if (window == CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS ||
                window == CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN || window == CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS) {
                withTimeout(STAGE_TIMEOUT_MILLIS) { adapter.started.await() }
                check(counters.promptReturns.get() == 1 && access.returnedReceipt != null)
                check(counters.creates.get() == 1 && counters.prompts.get() == 1 && counters.streams.get() == 1)
                check(access.returnedReceipt?.sessionId == run.sessionRef)
                val session = requireNotNull(runtime.context.sessions.get(SessionId(requireNotNull(run.sessionRef).value)))
                if (window != CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS) {
                    runtime.context.persistence.flush(session)
                    flushAcknowledged = true
                }
                // The pre-Accepted window proves the real live input without adding a
                // persistence side effect. The Accepted window explicitly persists it.
                liveEvents = session.events()
                val user = liveEvents.filter { it.type == SessionEventNames.USER_MESSAGE }.map {
                    json.decodeFromJsonElement(UserMessageEvent.serializer(), it.data)
                }.single { it.message.id.value == run.inputMessageId }
                check(user.turn > 0)
                check(liveEvents.any { it.type == SessionEventNames.TURN_START &&
                    it.data.jsonObject["turn"]?.jsonPrimitive?.longOrNull == user.turn
                }) { "Actual AgentLoop TurnStart for this input is missing" }
                val terminal = DesktopScheduledSessionEvidence.terminalEvidence(liveEvents, run.inputMessageId)
                if (window == CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS) {
                    check(terminal != null && terminal.outcome == ScheduledRunPhase.Succeeded && terminal.reason == "completed")
                    check(run.terminal == terminal) { "Terminal candidate differs from actual flushed evidence" }
                    check(counters.finishes.get() == 1)
                } else {
                    check(terminal == null && counters.finishes.get() == 0) { "Open turn fixture accidentally completed" }
                    check(!adapter.finish.isCompleted) { "Open-turn provider was released" }
                }
            }
            val disk = ledger(home)
            check(disk.owner == binding.owner && candidate.owner == disk.owner)
            check(candidate.revision == expectedRevision + 1)
            check(disk.revision == if (committed) candidate.revision else expectedRevision)
            val persisted = disk.runs.single()
            check(persisted.id == run.id && persisted.requestId == ORIGINAL_REQUEST_ID)
            when (window) {
                CrashWindow.CREATING_COMMITTED_BEFORE_CREATE -> {
                    check(persisted.phase == ScheduledRunPhase.CreatingSession && counters.creates.get() == 0)
                    check(persisted.sessionRef == null && !persisted.deliveryIntent)
                }
                CrashWindow.DISPATCHING_COMMITTED_BEFORE_PROMPT -> {
                    check(persisted.phase == ScheduledRunPhase.Dispatching && persisted.deliveryIntent)
                    check(counters.creates.get() == 1 && counters.createReturns.get() == 1 && counters.prompts.get() == 0)
                    check(runtime.context.sessions.get(SessionId(requireNotNull(persisted.sessionRef).value)) != null)
                }
                CrashWindow.PROMPT_RETURNED_BEFORE_ACCEPTED_CAS -> {
                    check(persisted.phase == ScheduledRunPhase.Dispatching && persisted.receiptId == null)
                    check(candidate.runs.single().receiptId == access.returnedReceipt?.messageId)
                }
                CrashWindow.ACCEPTED_COMMITTED_OPEN_TURN -> {
                    check(persisted.phase == ScheduledRunPhase.Accepted && persisted.receiptId == access.returnedReceipt?.messageId)
                }
                CrashWindow.COMPLETED_BEFORE_TERMINAL_CAS -> {
                    check(persisted.phase == ScheduledRunPhase.Accepted && persisted.terminal == null)
                    check(persisted.receiptId == access.returnedReceipt?.messageId)
                }
                null -> error("Ungated contender cannot pause")
            }
            reached.complete(CrashGate(requireNotNull(window), expectedRevision, candidate.revision, committed,
                disk, candidate, counters.snapshot(), access.returnedReceipt?.messageId, liveEvents, flushAcknowledged))
            // Cancellation from fixture cleanup does not call or alter the underlying CAS.
            // The real holder path is killed by the parent while awaiting this Deferred.
            resume.await()
        }
    }
}

@Serializable
enum class CrashRole { HOLDER, CONTENDER }

@Serializable
enum class CrashWindow {
    CREATING_COMMITTED_BEFORE_CREATE,
    DISPATCHING_COMMITTED_BEFORE_PROMPT,
    PROMPT_RETURNED_BEFORE_ACCEPTED_CAS,
    ACCEPTED_COMMITTED_OPEN_TURN,
    COMPLETED_BEFORE_TERMINAL_CAS,
}

@Serializable
enum class CrashCommandKind { HELD, TRY_START, RECOVER, CHECKS, CLOSE_RUNTIME, EXIT }

@Serializable
data class CrashCommand(val id: String, val command: CrashCommandKind)

@Serializable
data class CrashCounters(val creates: Int = 0, val createReturns: Int = 0, val prompts: Int = 0,
    val promptReturns: Int = 0, val providerStreams: Int = 0, val providerFinishes: Int = 0, val inspections: Int = 0) {
    fun hasNoDelivery(): Boolean = creates == 0 && createReturns == 0 && prompts == 0 && promptReturns == 0 &&
        providerStreams == 0 && providerFinishes == 0
}

@Serializable
data class CrashGate(val window: CrashWindow, val oldRevision: Long, val candidateRevision: Long,
    val committedAcknowledgement: Boolean, val disk: ScheduledSnapshot, val candidate: ScheduledSnapshot,
    val counters: CrashCounters, val returnedReceiptId: String?, val liveEvents: List<SessionEventEnvelope>,
    val flushAcknowledged: Boolean)

@Serializable
data class CrashRawSession(val id: String, val cwd: String?, val events: List<SessionEventEnvelope>)

@Serializable
data class CrashRawRoom(val databaseExists: Boolean, val sessions: List<CrashRawSession>)

/** Every message includes actual PID/home/window/counters and a monotonic child sequence.
 * GATE carries disk AND candidate, so a parent can independently compare the production
 * store and raw Room. RECOVERED reports the actual phase, including a production defect. */
@Serializable
data class CrashReply(
    val event: String, val sequence: Long = 0, val pid: Long = 0, val home: String = "",
    val role: CrashRole? = null, val window: CrashWindow? = null, val lifecycle: Int = 0,
    val commandId: String? = null, val counters: CrashCounters = CrashCounters(),
    val effect: String? = null, val gate: CrashGate? = null, val snapshot: ScheduledSnapshot? = null,
    val run: ScheduledRun? = null, val rawRoom: CrashRawRoom? = null, val startResult: String? = null,
    val reason: String? = null, val leaderHeld: Boolean? = null, val agentCount: Int? = null,
    val duplicate: Boolean? = null, val duplicateRun: ScheduledRun? = null,
    val overlapRefused: Boolean? = null, val capacityRefused: Boolean? = null, val tick: Int? = null,
    val ledgerShaBefore: String? = null, val ledgerShaAfter: String? = null,
    val cleanup: String? = null, val error: String? = null,
)

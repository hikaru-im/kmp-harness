package im.hikaru.harness.client.scheduling

import im.hikaru.harness.client.connection.SessionId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Local Cron executor. Durable intents precede side effects; ambiguous delivery is never replayed.
 * Parent must await shutdown before disposing its connection/runtime or cancelling coroutineScope. */
public class ScheduledExecutor(
    private val store: ScheduledStore, private val engine: ScheduleEngine,
    private val access: ScheduledSessionAccess, private val leader: ScheduledLeaderLease,
    coroutineScope: CoroutineScope, private val clock: ScheduledClock = SystemScheduledClock,
    private val config: ScheduledExecutorConfig = ScheduledExecutorConfig(),
) {
    private val scope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))
    private val operation = Mutex()
    private val token = MutableStateFlow(Any())
    private val jobs = MutableStateFlow<List<Job>>(emptyList())
    private val mutableState = MutableStateFlow(ScheduledExecutorState())
    public val state: StateFlow<ScheduledExecutorState> = mutableState.asStateFlow()
    private var driver: Job? = null
    private val stopping = MutableStateFlow(false)
    private val lifetimeFence = MutableStateFlow<Fence?>(null)
    private val retainedSnapshot = MutableStateFlow<ScheduledSnapshot?>(null)
    private val shutdownRequested = MutableStateFlow(false)
    // Retained across UI invalidation: clearing public owner state must never hide unsafe delivery.
    private val unresolvedDelivery = MutableStateFlow<Set<String>>(emptySet())
    // Only the current deliver invocation can establish this proof. It is deliberately not
    // reconstructed from Dispatching after a crash, when prompt may already have been invoked.
    private val uncalledPrompt = MutableStateFlow<Set<String>>(emptySet())
    // Accessed under operation. Retry delays are data, never sleeping jobs that hold up shutdown.
    private val reconciliation = mutableMapOf<String, Recheck>()
    // Callers time out on await, never cancel an in-flight evidence/store/release method.
    // Concurrent close requests share this attempt; a finished Unknown attempt can be retried.
    private val shutdownAttempt = MutableStateFlow<Deferred<ScheduledShutdownResult>?>(null)
    private var ledgerDurabilityUncertain = false

    public suspend fun start(binding: ScheduledScope): ScheduledStartResult = request {
        operation.withLock {
            if (!config.verifiedCronExecution) return@withLock ScheduledStartResult.Unavailable("本地 Cron 执行尚未通过集成验证。")
            if (shutdownRequested.value) return@withLock ScheduledStartResult.Unavailable("此执行器已停止；新生命周期请构造新实例。")
            if (state.value.scope == binding && state.value.accepting && !stopping.value) return@withLock ScheduledStartResult.Started
            if (leader.isHeld() || jobs.value.count { it.isActive } > 1) return@withLock ScheduledStartResult.Unavailable("上一执行器尚未安全释放；先完成 shutdown。")
            stopping.value = false
            token.value = Any()
            mutableState.update { if (!shutdownRequested.value) ScheduledExecutorState(scope = binding, unavailableReason = "正在核验本机执行服务。") else it }
            val fence = Fence(binding, token.value)
            lifetimeFence.value = fence
            try {
                access.unavailableReason(binding, null)?.let { return@withLock unavailable(it) }
                if (shutdownRequested.value || stopping.value || !same(fence)) return@withLock unavailable("启动已被停止或身份已切换。")
                if (!leader.acquire(binding)) return@withLock unavailable("另一个本机执行器持有领导锁。")
                if (shutdownRequested.value || stopping.value || !same(fence)) return@withLock unavailable("启动已被停止或身份已切换。")
                val old = read(fence) ?: return@withLock unavailable(state.value.error ?: "无法读取任务账本。")
                val now = clock.nowEpochMillis()
                // Recovery never calls create/prompt. Exact durable evidence may repair a prior Unknown.
                val recovered = old.runs.map { run ->
                    if (!run.blocksTask) run else {
                        val evidence = if (run.mayHaveDelivered) access.inspect(binding, run) else ScheduledEvidenceResult.Pending
                        if (evidence is ScheduledEvidenceResult.Terminal) run.copy(phase = evidence.evidence.outcome, terminal = evidence.evidence,
                            sessionRef = run.sessionRef ?: run.candidateSessionId, detail = null)
                        else run.copy(phase = ScheduledRunPhase.Unknown, detail = "重启后未能证明运行结果；禁止自动重新投递。")
                    }
                }
                val cursors = old.tasks.filter { it.status == ScheduledTaskStatus.Active }.map { task ->
                    val future = engine.nextOccurrence(task.schedule, now) ?: return@withLock unavailable("任务没有可计算的未来时间。")
                    val prior = old.cursors.firstOrNull { it.taskId == task.id }
                    val missed = prior?.nextNominalAt?.takeIf { it <= now }
                    ScheduledCursor(task.id, task.revision, future, missed ?: prior?.skippedThrough,
                        if (missed != null) ScheduledSkipReason.SkipRestart else prior?.skipReason)
                }
                val candidate = old.copy(runs = recovered, cursors = cursors)
                unresolvedDelivery.value = recovered.filter { it.mayHaveDelivered && it.blocksTask }.map { it.id }.toSet()
                if (candidate != old && commit(fence, old, candidate) == null) return@withLock unavailable(state.value.error ?: "无法持久化恢复结果。")
                if (!same(fence) || stopping.value || shutdownRequested.value) return@withLock unavailable("启动已被停止或身份已切换。")
                mutableState.update { if (same(fence) && !stopping.value && !shutdownRequested.value) it.copy(accepting = true, unavailableReason = null, error = null) else it }
                if (!accepting(fence)) return@withLock unavailable("启动已被停止或身份已切换。")
                driver = scope.launch {
                    while (isActive && same(fence) && !stopping.value) {
                        delay(config.tickIntervalMillis)
                        tick()
                    }
                }
                ScheduledStartResult.Started
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unavailable("本机执行服务核验或恢复失败；未自动投递。") }
        }
    }

    public suspend fun activate(taskId: String, expectedTaskRevision: Long, active: Boolean): ScheduledActivationResult = request {
        operation.withLock {
            val fence = acceptingFence() ?: return@withLock ScheduledActivationResult.Refused(reason())
            val old = read(fence) ?: return@withLock ScheduledActivationResult.Refused(reason())
            val task = old.tasks.firstOrNull { it.id == taskId } ?: return@withLock ScheduledActivationResult.Refused("任务不存在。")
            if (task.revision != expectedTaskRevision || task.revision == Long.MAX_VALUE) return@withLock ScheduledActivationResult.Refused("任务版本冲突，请重新读取。")
            if (active) {
                access.unavailableReason(fence.binding, task)?.let { return@withLock ScheduledActivationResult.Refused(it) }
                if (old.runs.any { it.task.id == taskId && it.blocksTask }) return@withLock ScheduledActivationResult.Refused("任务有在途或 Unknown 运行，不能启用。")
            }
            val changed = task.copy(revision = task.revision + 1,
                status = if (active) ScheduledTaskStatus.Active else ScheduledTaskStatus.Paused,
                updatedAt = clock.nowEpochMillis())
            val next = if (active) engine.nextOccurrence(task.schedule, clock.nowEpochMillis()) else null
            if (active && next == null) return@withLock ScheduledActivationResult.Refused("无法计算下一次运行时间。")
            val candidate = old.copy(tasks = old.tasks.map { if (it.id == taskId) changed else it },
                cursors = old.cursors.filterNot { it.taskId == taskId } + if (next != null) listOf(ScheduledCursor(taskId, changed.revision, next)) else emptyList())
            val committed = commit(fence, old, candidate, requireAcceptance = true) ?: return@withLock ScheduledActivationResult.Refused(reason())
            ScheduledActivationResult.Saved(committed)
        }
    }

    /** Request ID is a durable dedupe key. Caller must retain it across an ambiguous UI retry. */
    public suspend fun runNow(taskId: String, requestId: String): ScheduledClaimResult = request {
        operation.withLock { claim(taskId, requestId, null) }
    }

    /** Timer is merely a wakeup. nextNominalAt comes from the durable cursor, never next(now) alone. */
    public suspend fun tick(): Unit = request {
        val live = lifetimeFence.value?.takeIf { same(it) && leader.isHeld() && !stopping.value } ?: return@request
        uncalledPrompt.value.firstOrNull()?.let { settleUncalledPrompt(live, it) }
        operation.withLock {
            if (!same(live) || stopping.value || !leader.isHeld()) return@withLock
            val snapshot = read(live) ?: return@withLock
            reconcileUnknown(live, snapshot)
            val fence = acceptingFence() ?: return@withLock
            var old = read(fence) ?: return@withLock
            val now = clock.nowEpochMillis()
            for (cursor in old.cursors.toList()) {
                if (cursor.nextNominalAt > now) continue
                val task = old.tasks.firstOrNull { it.id == cursor.taskId } ?: continue
                // claim rereads the canonical ledger: dedupe, capacity and cursor advancement
                // share the same revision even when another writer changes it during this tick.
                claim(task.id, "due:${task.id}:${cursor.nextNominalAt}", cursor.nextNominalAt)
                if (!accepting(fence)) return@withLock
                old = state.value.snapshot ?: return@withLock
            }
        }
    }

    /** Synchronous identity fence. Existing methods are still tracked and may require UnsafeToRelease. */
    public fun invalidate() {
        shutdownRequested.value = true
        stopping.value = true
        // Keep the old execution fence solely for final evidence/ledger writes. No new claims or
        // UI publication survive invalidation, and this instance can never bind another owner.
        mutableState.value = ScheduledExecutorState(unavailableReason = "连接或身份已失效，执行器停止新准入。")
        driver?.cancel()
    }

    /** Does not cancel a user session. A finite wait only observes owned methods and exact evidence. */
    public suspend fun shutdown(timeoutMillis: Long = 5_000): ScheduledShutdownResult {
        require(timeoutMillis >= 0)
        shutdownRequested.value = true
        stopping.value = true
        mutableState.update { it.copy(accepting = false, unavailableReason = "执行器正在停止。") }
        driver?.cancel()
        val attempt = stoppingAttempt()
        return try {
            withTimeoutOrNull(timeoutMillis.coerceAtLeast(1)) { attempt.await() }
                ?: ScheduledShutdownResult.UnsafeToRelease("仍有 create/prompt/store/evidence/release 方法未收敛；领导锁与本机 runtime 必须保留。")
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
            ScheduledShutdownResult.UnsafeToRelease("执行器生命周期已取消，未确认安全释放。")
        }
    }

    private fun stoppingAttempt(): Deferred<ScheduledShutdownResult> {
        while (true) {
            shutdownAttempt.value?.let { return it }
            val attempt = scope.async(start = CoroutineStart.LAZY) {
                try { drainAndRelease() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ScheduledShutdownResult.UnsafeToRelease("停止时账本、证据或领导锁操作失败；必须保留 runtime。") }
            }
            if (!shutdownAttempt.compareAndSet(null, attempt)) { attempt.cancel(); continue }
            attempt.invokeOnCompletion { shutdownAttempt.compareAndSet(attempt, null) }
            attempt.start()
            return attempt
        }
    }

    private suspend fun drainAndRelease(): ScheduledShutdownResult {
        driver?.join()
        while (jobs.value.isNotEmpty()) jobs.value.toList().joinAll()
        return operation.withLock {
            val fence = lifetimeFence.value
            if (leader.isHeld()) {
                if (fence == null) return@withLock ScheduledShutdownResult.UnsafeToRelease("领导锁仍持有，但执行身份已丢失。")
                var snapshot = read(fence) ?: return@withLock ScheduledShutdownResult.UnsafeToRelease("无法核验持久账本；不得释放领导锁。")
                if (ledgerDurabilityUncertain) {
                    snapshot = commit(fence, snapshot, snapshot)
                        ?: return@withLock ScheduledShutdownResult.UnsafeToRelease("账本持久化尚未确认；不得释放领导锁。")
                }
                for (id in uncalledPrompt.value.toList()) {
                    snapshot = settleUncalledPromptLocked(fence, snapshot, id)
                        ?: return@withLock ScheduledShutdownResult.UnsafeToRelease("确定未调用 prompt 的撤回尚未持久化；不得释放领导锁。")
                }
                // Observation may already have ended Unknown. A later close retries exact evidence,
                // including after UI invalidation, without create/prompt or public owner publication.
                for (run in snapshot.runs.filter { it.mayHaveDelivered && it.blocksTask }) {
                    val evidence = access.inspect(fence.binding, run)
                    if (evidence is ScheduledEvidenceResult.Terminal) {
                        val old = read(fence) ?: return@withLock ScheduledShutdownResult.UnsafeToRelease("无法重读终态账本。")
                        val current = old.runs.firstOrNull { it.id == run.id }
                            ?: return@withLock ScheduledShutdownResult.UnsafeToRelease("在途运行记录已丢失。")
                        if (current.mayHaveDelivered && current.blocksTask) {
                            val terminal = current.copy(phase = evidence.evidence.outcome, terminal = evidence.evidence,
                                sessionRef = current.sessionRef ?: current.candidateSessionId, detail = null)
                            if (commit(fence, old, old.copy(runs = old.runs.map { if (it.id == run.id) terminal else it })) == null)
                                return@withLock ScheduledShutdownResult.UnsafeToRelease("精确终态未确认持久化；不得释放领导锁。")
                        }
                    }
                }
            }
            if (unresolvedDelivery.value.isNotEmpty() || retainedSnapshot.value?.runs.orEmpty().any { it.mayHaveDelivered && it.blocksTask })
                return@withLock ScheduledShutdownResult.UnsafeToRelease("投递可能仍在执行，尚无精确持久 TurnEnd；不得释放领导锁或销毁 runtime。")
            // Stopping fenced all admissions before waiting. Holding operation prevents a queued
            // method from interleaving with release; those methods can only return Refused now.
            if (leader.isHeld()) leader.release()
            if (leader.isHeld()) return@withLock ScheduledShutdownResult.UnsafeToRelease("领导锁尚未确认释放。")
            token.value = Any()
            lifetimeFence.value = null
            retainedSnapshot.value = null
            uncalledPrompt.value = emptySet()
            reconciliation.clear()
            mutableState.value = ScheduledExecutorState(unavailableReason = "执行器已停止。")
            ScheduledShutdownResult.Drained
        }
    }

    private suspend fun claim(taskId: String, requestId: String, nominal: Long?): ScheduledClaimResult {
        val fence = acceptingFence() ?: return ScheduledClaimResult.Refused(reason())
        if (requestId.isBlank() || requestId.length > 500) return ScheduledClaimResult.Refused("请求 ID 无效。")
        val old = read(fence) ?: return ScheduledClaimResult.Refused(reason())
        old.runs.firstOrNull { it.requestId == requestId }?.let {
            return if (it.task.id == taskId && it.nominalAt == nominal) ScheduledClaimResult.Claimed(it, duplicate = true)
            else ScheduledClaimResult.Refused("请求 ID 已用于另一运行。")
        }
        val task = old.tasks.firstOrNull { it.id == taskId } ?: return ScheduledClaimResult.Refused("任务不存在。")
        val cursor = old.cursors.firstOrNull { it.taskId == taskId }
        if (nominal != null && (task.status != ScheduledTaskStatus.Active || cursor?.nextNominalAt != nominal)) return ScheduledClaimResult.Refused("定时游标已改变。")
        val now = clock.nowEpochMillis()
        val skipReason = when {
            old.runs.any { it.task.id == taskId && it.blocksTask } -> ScheduledSkipReason.SkipBusy
            nominal != null && now - nominal > config.maxLatenessMillis -> ScheduledSkipReason.SkipLate
            old.runs.count { it.blocksTask } >= config.maxConcurrentRuns -> ScheduledSkipReason.SkipCapacity
            else -> null
        }
        if (skipReason != null) {
            if (nominal != null) {
                // A skipped occurrence is consumed durably, never queued or backfilled.
                val future = engine.nextOccurrence(task.schedule, now)?.takeIf { it > now }
                    ?: return ScheduledClaimResult.Refused("无法计算下一次运行时间。")
                val skipped = old.copy(cursors = old.cursors.map {
                    if (it.taskId == taskId) it.copy(nextNominalAt = future, skippedThrough = nominal, skipReason = skipReason) else it
                })
                commit(fence, old, skipped, requireAcceptance = true) ?: return ScheduledClaimResult.Refused(reason())
            }
            return ScheduledClaimResult.Refused(when (skipReason) {
                ScheduledSkipReason.SkipBusy -> "任务已有在途或 Unknown 运行；禁止重发。"
                ScheduledSkipReason.SkipLate -> "本次定时运行已迟到，已跳过且不会补跑。"
                ScheduledSkipReason.SkipCapacity -> "调度并发额度已满（上限 ${config.maxConcurrentRuns}）；在途和 Unknown 运行均占用额度。" +
                    (if (nominal != null) "本次定时运行已跳过，不会排队或补跑。" else "请在运行终态确认后重试。")
                ScheduledSkipReason.SkipRestart -> error("Restart skips are recorded only during recovery")
            })
        }
        access.unavailableReason(fence.binding, task)?.let { return ScheduledClaimResult.Refused(it) }
        val id = "run-" + buildString { repeat(32) { append("0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)]) } }
        // A heartbeat's durable intent already names the exact existing session; it never creates one.
        val candidateSessionId = (task.target as? ScheduledTarget.Heartbeat)?.sessionId ?: SessionId("scheduled-$id")
        val run = ScheduledRun(id, task, requestId, nominal, now, candidateSessionId, "scheduled-input-$id")
        val next = if (nominal != null) engine.nextOccurrence(task.schedule, now)?.takeIf { it > now } else null
        if (nominal != null && next == null) return ScheduledClaimResult.Refused("无法计算下一次运行时间。")
        val candidate = old.copy(runs = old.runs + run, cursors = old.cursors.map {
            if (it.taskId == taskId && next != null) it.copy(nextNominalAt = next) else it
        })
        commit(fence, old, candidate, requireAcceptance = true) ?: return ScheduledClaimResult.Refused(reason())
        if (accepting(fence)) launchOwned { deliver(fence, run.id) }
        return ScheduledClaimResult.Claimed(run)
    }

    private suspend fun deliver(fence: Fence, runId: String) {
        var promptInvoked = false
        var failedBeforePrompt = false
        try {
            var run = stage(fence, runId) { old, current ->
                if (!accepting(fence) || old.tasks.none { it.id == current.task.id && it.revision == current.task.revision }) {
                    current.copy(phase = ScheduledRunPhase.Rejected, detail = "投递前任务已修改或执行器已停止。")
                } else if (current.task.target is ScheduledTarget.Heartbeat) {
                    // Heartbeat delivers into the session already named by its intent: no create step.
                    current.copy(phase = ScheduledRunPhase.SessionCreated,
                        sessionRef = (current.task.target as ScheduledTarget.Heartbeat).sessionId)
                } else current.copy(phase = ScheduledRunPhase.CreatingSession)
            } ?: return
            if (run.task.target is ScheduledTarget.Heartbeat) {
                if (run.phase != ScheduledRunPhase.SessionCreated) return
            } else {
                if (run.phase != ScheduledRunPhase.CreatingSession) return
                if (!accepting(fence)) { unknown(fence, runId, "create intent 后执行器停止；未重试创建。"); return }
                val created = access.create(fence.binding, run)
                run = stage(fence, runId) { _, current -> current.copy(phase = ScheduledRunPhase.SessionCreated, sessionRef = created) } ?: return
            }
            run = stage(fence, runId) { old, current ->
                if (!accepting(fence) || old.tasks.none { it.id == current.task.id && it.revision == current.task.revision }) {
                    current.copy(phase = ScheduledRunPhase.Rejected, detail = "会话已创建；prompt 前任务改变或执行器停止，未投递。")
                } else current.copy(phase = ScheduledRunPhase.Dispatching, deliveryIntent = true)
            } ?: return
            if (run.phase != ScheduledRunPhase.Dispatching) return
            unresolvedDelivery.update { it + runId }
            if (!accepting(fence)) return // finally records a trusted withdrawal, preserving intent.
            // No suspending operation between the final fence and marking the call ambiguous.
            promptInvoked = true
            val receipt = access.prompt(fence.binding, run)
            check(receipt.sessionId == run.sessionRef && receipt.messageId.isNotBlank()) { "投递收据与运行不一致。" }
            stage(fence, runId) { _, current -> current.copy(phase = ScheduledRunPhase.Accepted, receiptId = receipt.messageId) } ?: return
            observe(fence, runId)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (promptInvoked) unknown(fence, runId, "prompt/evidence 返回异常；副作用无法排除，禁止自动重发。")
            else failedBeforePrompt = true
        } finally {
            if (!promptInvoked) {
                uncalledPrompt.update { it + runId }
                settleUncalledPrompt(fence, runId, failedBeforePrompt)
            }
        }
    }

    private suspend fun settleUncalledPrompt(fence: Fence, runId: String, failed: Boolean = false) {
        try {
            operation.withLock {
                val old = read(fence) ?: return@withLock
                settleUncalledPromptLocked(fence, old, runId, failed)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail("未调用 prompt 的撤回尚未确认持久化；保留本生命周期证明与领导锁。") }
    }

    private suspend fun settleUncalledPromptLocked(
        fence: Fence, old: ScheduledSnapshot, id: String, failed: Boolean = false,
    ): ScheduledSnapshot? {
        val run = old.runs.firstOrNull { it.id == id } ?: return null
        val changed = when {
            run.phase == ScheduledRunPhase.Dispatching -> run.copy(phase = ScheduledRunPhase.Withdrawn,
                detail = "本生命周期已确认未调用 prompt；投递安全撤回，保留原 intent 审计。")
            // A heartbeat created nothing: a pre-prompt stop leaves no side effect to reconcile.
            run.task.target is ScheduledTarget.Heartbeat && run.blocksTask && !run.deliveryIntent -> run.copy(
                phase = ScheduledRunPhase.Rejected,
                detail = "会话跟进未调用 prompt；无创建副作用，投递已放弃。")
            failed && run.blocksTask && !run.deliveryIntent -> run.copy(phase = ScheduledRunPhase.Unknown,
                detail = "create 返回异常，创建副作用无法排除；未调用 prompt，禁止自动重建。")
            else -> run
        }
        val saved = if (changed == run) old else commit(fence, old, old.copy(runs = old.runs.map { if (it.id == id) changed else it }))
        if (saved != null) uncalledPrompt.update { it - id }
        return saved
    }

    private suspend fun observe(fence: Fence, runId: String) {
        val found = withTimeoutOrNull(config.observationTimeoutMillis) {
            while (same(fence)) {
                val run = operation.withLock { read(fence)?.runs?.firstOrNull { it.id == runId } } ?: return@withTimeoutOrNull false
                when (val result = access.inspect(fence.binding, run)) {
                    is ScheduledEvidenceResult.Terminal -> {
                        val saved = stage(fence, runId) { _, current ->
                            current.copy(phase = result.evidence.outcome, terminal = result.evidence, detail = null)
                        } != null
                        if (saved) unresolvedDelivery.update { it - runId }
                        return@withTimeoutOrNull saved
                    }
                    is ScheduledEvidenceResult.Unavailable -> { unknown(fence, runId, result.reason); return@withTimeoutOrNull false }
                    ScheduledEvidenceResult.Pending -> delay(config.evidencePollMillis)
                }
            }
            false
        }
        if (found != true) unknown(fence, runId, "未取得精确持久 TurnEnd；运行结果 Unknown，禁止自动重新投递。")
    }

    private suspend fun unknown(fence: Fence, runId: String, detail: String) {
        try {
            stage(fence, runId) { _, current -> if (current.blocksTask && current.phase != ScheduledRunPhase.Unknown)
                current.copy(phase = ScheduledRunPhase.Unknown, detail = detail) else current }
        } finally {
            // Even a failed/throwing Unknown CAS must leave the prior Dispatching/Accepted run
            // eligible for read-only reconciliation. It can never reopen delivery.
            operation.withLock {
                if (same(fence)) reconciliation.getOrPut(runId) { firstRecheck() }
            }
        }
    }

    private data class Recheck(val attempts: Int, val nextAt: Long, val delayMillis: Long)
    private fun firstRecheck(): Recheck = Recheck(0,
        after(clock.nowEpochMillis(), config.reconciliationInitialDelayMillis), config.reconciliationInitialDelayMillis)
    private fun after(now: Long, delayMillis: Long): Long = if (now > Long.MAX_VALUE - delayMillis) Long.MAX_VALUE else now + delayMillis

    /** At most one due read per tick and a finite number per run. Never creates or prompts,
     * including when a terminal CAS error has disabled admission. Shutdown owns its final pass. */
    private suspend fun reconcileUnknown(fence: Fence, snapshot: ScheduledSnapshot) {
        reconciliation.keys.retainAll(snapshot.runs.filter { it.blocksTask && it.mayHaveDelivered }.map { it.id }.toSet())
        val now = clock.nowEpochMillis()
        val run = snapshot.runs.firstOrNull {
            if (!it.blocksTask || !it.mayHaveDelivered || it.id in uncalledPrompt.value ||
                (it.phase != ScheduledRunPhase.Unknown && it.id !in reconciliation)) false
            else {
                val retry = reconciliation.getOrPut(it.id) { firstRecheck() }
                retry.attempts < config.reconciliationAttempts && retry.nextAt <= now
            }
        } ?: return
        val retry = reconciliation.getValue(run.id)
        val nextDelay = if (retry.delayMillis >= config.reconciliationMaxDelayMillis / 2)
            config.reconciliationMaxDelayMillis else retry.delayMillis * 2
        reconciliation[run.id] = Recheck(retry.attempts + 1, after(now, nextDelay), nextDelay)
        val result = try { access.inspect(fence.binding, run) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ScheduledEvidenceResult.Unavailable("精确证据读取暂时失败；保持 Unknown。") }
        if (!same(fence)) return
        if (result is ScheduledEvidenceResult.Terminal) {
            val terminal = run.copy(phase = result.evidence.outcome, terminal = result.evidence,
                sessionRef = run.sessionRef ?: run.candidateSessionId, detail = null)
            if (commit(fence, snapshot, snapshot.copy(runs = snapshot.runs.map { if (it.id == run.id) terminal else it })) != null)
                reconciliation.remove(run.id)
        }
    }

    private suspend fun stage(fence: Fence, id: String, change: (ScheduledSnapshot, ScheduledRun) -> ScheduledRun): ScheduledRun? = operation.withLock {
        if (!same(fence)) return@withLock null
        val old = read(fence) ?: return@withLock null
        val run = old.runs.firstOrNull { it.id == id } ?: return@withLock null
        val changed = change(old, run)
        if (changed == run) return@withLock run
        val admission = changed.phase == ScheduledRunPhase.CreatingSession || changed.phase == ScheduledRunPhase.Dispatching
        commit(fence, old, old.copy(runs = old.runs.map { if (it.id == id) changed else it }), requireAcceptance = admission)
            ?.runs?.firstOrNull { it.id == id }
    }

    private suspend fun read(fence: Fence): ScheduledSnapshot? {
        if (!same(fence)) return null
        val result = store.read(fence.binding.owner)
        if (!same(fence)) return null
        val snapshot = when (result) {
            is ScheduledReadResult.Ready -> result.snapshot
            ScheduledReadResult.Missing -> ScheduledSnapshot(fence.binding.owner)
            else -> { fail("任务账本不可读取；坏文件/未知版本保持原样。"); return null }
        }
        if (snapshot.owner != fence.binding.owner || ScheduledValidator.snapshotError(snapshot) != null ||
            snapshot.tasks.any { engine.validationError(it.schedule) != null }) { fail("任务账本校验失败；禁止执行。"); return null }
        if (unresolvedDelivery.value.any { id -> snapshot.runs.none { it.id == id } }) {
            fail("未收敛的投递记录已丢失；不得释放领导锁。"); return null
        }
        retainedSnapshot.value = snapshot
        unresolvedDelivery.value = snapshot.runs.filter { it.mayHaveDelivered && it.blocksTask }.map { it.id }.toSet()
        mutableState.update { if (it.scope == fence.binding) it.copy(snapshot = snapshot) else it }
        return snapshot
    }

    private suspend fun commit(
        fence: Fence, old: ScheduledSnapshot, changed: ScheduledSnapshot, requireAcceptance: Boolean = false,
    ): ScheduledSnapshot? {
        fun permitted(): Boolean = same(fence) && leader.isHeld() && (!requireAcceptance || accepting(fence))
        if (!permitted() || old.revision == Long.MAX_VALUE) return null
        val next = changed.copy(revision = old.revision + 1)
        if (ScheduledValidator.snapshotError(next) != null || !ScheduledLedgerRules.validTransition(old, next)) {
            fail("账本状态转换无效；未继续副作用。"); return null
        }
        val result = store.compareAndSet(fence.binding.owner, old.revision, next) { permitted() }
        if (!same(fence)) return null
        if (result !is ScheduledWriteResult.Committed || result.snapshot != next) { fail("账本写入失败或版本冲突；未继续副作用，请重新读取。"); return null }
        retainedSnapshot.value = result.snapshot
        unresolvedDelivery.value = result.snapshot.runs.filter { it.mayHaveDelivered && it.blocksTask }.map { it.id }.toSet()
        mutableState.update { if (it.scope == fence.binding) it.copy(snapshot = result.snapshot) else it }
        ledgerDurabilityUncertain = result.durabilityWarning != null
        if (result.durabilityWarning != null) { fail(result.durabilityWarning); return null }
        return result.snapshot
    }

    private fun fail(message: String) { mutableState.update { it.copy(accepting = false, error = message, unavailableReason = message) } }
    private fun unavailable(message: String): ScheduledStartResult.Unavailable { fail(message); return ScheduledStartResult.Unavailable(message) }
    private fun reason(): String = state.value.error ?: state.value.unavailableReason ?: "执行器未就绪。"
    private fun acceptingFence(): Fence? = state.value.scope?.let { Fence(it, token.value) }?.takeIf(::accepting)
    private fun accepting(fence: Fence): Boolean = same(fence) && state.value.accepting && !stopping.value && !shutdownRequested.value && leader.isHeld()
    private fun same(fence: Fence): Boolean = lifetimeFence.value == fence && token.value === fence.token
    private data class Fence(val binding: ScheduledScope, val token: Any)

    private suspend fun <T> request(block: suspend () -> T): T {
        val job = scope.async(start = CoroutineStart.LAZY) { block() }
        jobs.update { it + job }
        job.invokeOnCompletion { jobs.update { existing -> existing.filterNot { it === job } } }
        job.start()
        return job.await()
    }
    private fun launchOwned(block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        jobs.update { it + job }
        job.invokeOnCompletion { jobs.update { existing -> existing.filterNot { it === job } } }
        job.start()
    }
}

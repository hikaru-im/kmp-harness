package im.hikaru.harness.client.scheduling

import im.hikaru.harness.client.connection.SessionId
import kotlinx.serialization.Serializable

@Serializable
public enum class ScheduledRunPhase {
    Claimed, CreatingSession, SessionCreated, Dispatching, Accepted,
    Succeeded, Failed, Blocked, Incomplete, Interrupted, Aborted, Rejected, Unknown,
    /** Delivery intent was committed, but this lifecycle proved prompt was never invoked. */
    Withdrawn,
}

@Serializable
public data class ScheduledTerminalEvidence(
    val turn: Long, val userMessageSeq: Long, val turnEndSeq: Long, val endedAt: Long,
    val outcome: ScheduledRunPhase, val reason: String, val failureCode: String? = null,
)

/** Immutable task revision and input ID are retained even after editing/deleting the task.
 * candidateSessionId is an intent, never a navigable session reference. */
@Serializable
public data class ScheduledRun(
    val id: String, val task: ScheduledTask, val requestId: String, val nominalAt: Long?,
    val claimedAt: Long, val candidateSessionId: SessionId, val inputMessageId: String,
    val phase: ScheduledRunPhase = ScheduledRunPhase.Claimed,
    val sessionRef: SessionId? = null, val receiptId: String? = null,
    val terminal: ScheduledTerminalEvidence? = null, val detail: String? = null,
    val deliveryIntent: Boolean = false,
) {
    public val blocksTask: Boolean get() = phase in setOf(ScheduledRunPhase.Claimed, ScheduledRunPhase.CreatingSession,
        ScheduledRunPhase.SessionCreated, ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted, ScheduledRunPhase.Unknown)
    public val mayHaveDelivered: Boolean get() = deliveryIntent && terminal == null && phase != ScheduledRunPhase.Withdrawn
}

@Serializable
public enum class ScheduledSkipReason { SkipCapacity, SkipBusy, SkipLate, SkipRestart }

@Serializable
public data class ScheduledCursor(
    val taskId: String, val taskRevision: Long, val nextNominalAt: Long,
    /** Last skipped due instant; restart/late wakeup never backfills prompts. */
    val skippedThrough: Long? = null,
    /** Reason for skippedThrough; null also supports ledgers written before skip reasons existed. */
    val skipReason: ScheduledSkipReason? = null,
)

public fun interface ScheduledClock { public fun nowEpochMillis(): Long }
public object SystemScheduledClock : ScheduledClock {
    override fun nowEpochMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}

public data class ScheduledExecutorConfig(
    /** Parent turns this on only after its serial targeted verification gate. */
    val verifiedCronExecution: Boolean = false,
    val tickIntervalMillis: Long = 1_000, val evidencePollMillis: Long = 100,
    val maxLatenessMillis: Long = 60_000, val observationTimeoutMillis: Long = 300_000,
    /** Read-only retries per unresolved run in this lifecycle; exhaustion never permits delivery. */
    val reconciliationAttempts: Int = 8,
    val reconciliationInitialDelayMillis: Long = 1_000, val reconciliationMaxDelayMillis: Long = 60_000,
    /** Budget across all durable blocksTask runs, including Unknown and deleted task history. */
    val maxConcurrentRuns: Int = 2,
) {
    init { require(maxConcurrentRuns > 0) { "maxConcurrentRuns must be greater than zero" } }
    init { require(tickIntervalMillis > 0 && evidencePollMillis > 0 && maxLatenessMillis >= 0 && observationTimeoutMillis > 0) }
    init { require(reconciliationAttempts > 0 && reconciliationInitialDelayMillis > 0 &&
        reconciliationMaxDelayMillis >= reconciliationInitialDelayMillis) }
}

public data class ScheduledExecutorState(
    val scope: ScheduledScope? = null, val accepting: Boolean = false,
    val snapshot: ScheduledSnapshot? = null, val error: String? = null,
    val unavailableReason: String? = "本地 Cron 执行尚未通过集成验证。",
)
public sealed interface ScheduledStartResult {
    public data object Started : ScheduledStartResult
    public data class Unavailable(val reason: String) : ScheduledStartResult
}
public sealed interface ScheduledClaimResult {
    public data class Claimed(val run: ScheduledRun, val duplicate: Boolean = false) : ScheduledClaimResult
    public data class Refused(val reason: String) : ScheduledClaimResult
}
public sealed interface ScheduledActivationResult {
    public data class Saved(val snapshot: ScheduledSnapshot) : ScheduledActivationResult
    public data class Refused(val reason: String) : ScheduledActivationResult
}
public sealed interface ScheduledShutdownResult {
    public data object Drained : ScheduledShutdownResult
    /** Includes unresolved delivery and methods that have not returned; lease remains held. */
    public data class UnsafeToRelease(val reason: String) : ScheduledShutdownResult
}

/** A process-duration exclusive lease, distinct from the store write lock. */
public interface ScheduledLeaderLease {
    public suspend fun acquire(scope: ScheduledScope): Boolean
    public fun isHeld(): Boolean
    public suspend fun release()
}

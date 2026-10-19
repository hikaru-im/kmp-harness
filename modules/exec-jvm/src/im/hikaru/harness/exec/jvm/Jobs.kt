package im.hikaru.harness.exec.jvm

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.tools.ToolExecutionContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A registry-issued `<kind>-N` job identity. Access is fenced by the owning Session. */
@Serializable
@JvmInline
public value class JobId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Job id must not be blank" }
        require(Regex("[a-z][a-z0-9-]*-[0-9]+").matches(value)) { "Job id must be <kind>-<ordinal>" }
    }
}

@Serializable
public enum class JobStatus {
    @SerialName("running") Running,
    @SerialName("stopping") Stopping,
    @SerialName("completed") Completed,
    @SerialName("killed") Killed,
    @SerialName("failed") Failed,
    ;

    public val isTerminal: Boolean get() = this == Completed || this == Killed || this == Failed
}

@Serializable
public data class JobChunk(
    /** Absolute character offset of the chunk's first character. */
    val at: Long,
    val text: String,
    val channel: String? = null,
    val gapBefore: Boolean = false,
) {
    init {
        require(at >= 0 && text.isNotEmpty())
    }
}

/** Public projection shared by the model tools and observers. */
@Serializable
public data class JobView(
    val id: JobId,
    val kind: String,
    val label: String,
    val owner: SessionId? = null,
    val outputLimitChars: Int? = null,
    val status: JobStatus,
    val progress: String? = null,
    val detail: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
) {
    init {
        require(kind.isNotBlank() && label.isNotBlank())
        require(startedAt >= 0)
        require(finishedAt == null || finishedAt >= startedAt)
        require((status == JobStatus.Running || status == JobStatus.Stopping) == (finishedAt == null))
    }
}

@Serializable
public data class JobRead(
    val chunks: List<JobChunk>,
    val lossy: Boolean,
    /** A value-producing job's result, returned once by the first read after settlement. */
    val result: String? = null,
    val job: JobView,
)

public enum class JobSettlementCause { NATURAL, KILL, TEARDOWN }

public data class JobSettlementEvent(
    val job: JobView,
    val cause: JobSettlementCause,
    /** A caller waiting through [JobsService.waitJob] collected this settlement. */
    val awaited: Boolean,
)

public fun interface JobListener {
    public suspend fun settled(event: JobSettlementEvent)
}

@Serializable
public data class JobOutcome(
    val status: JobStatus,
    val detail: String? = null,
    /** Returned once through [JobRead.result] when this job is value-producing. */
    val result: String? = null,
) {
    init {
        require(status.isTerminal) { "A job outcome must be terminal" }
        require(status != JobStatus.Running && status != JobStatus.Stopping)
    }
}

/** Producer face of one live job. Writes after settlement are dropped. */
public interface JobHandle {
    public val id: JobId

    /** Scope for producer-owned background work; cancelled when the registry is disposed. */
    public val scope: CoroutineScope

    public fun append(text: String, channel: String? = null)
    public fun updateProgress(line: String)
}

/** Producer controls. */
public interface JobHooks {
    /** Request termination. Must be idempotent and eventually release [await]. */
    public suspend fun cancel(reason: String? = null)

    /** Resolve after the producer has released its resources. */
    public suspend fun await(): JobOutcome
}

public fun interface JobProducer {
    public suspend fun start(job: JobHandle): JobHooks
}

public data class JobSpec(
    val kind: String,
    val label: String,
    val owner: SessionId?,
    val outputLimitChars: Int? = null,
    val producer: JobProducer,
) {
    init {
        require(Regex("[a-z][a-z0-9-]*").matches(kind)) { "Job kind must be a lower-case id" }
        require(label.isNotBlank()) { "Job label must not be blank" }
        require(outputLimitChars == null || outputLimitChars > 0)
    }
}

public enum class JobKillOutcome { REQUESTED, ALREADY_FINISHED }

public data class JobKillResult(
    val outcome: JobKillOutcome,
    val job: JobView,
)

public interface JobsService {
    public suspend fun start(spec: JobSpec, execution: ToolExecutionContext): JobView
    public suspend fun list(execution: ToolExecutionContext): List<JobView>
    public suspend fun get(id: JobId, execution: ToolExecutionContext): JobView
    public suspend fun read(id: JobId, execution: ToolExecutionContext): JobRead
    public suspend fun kill(id: JobId, execution: ToolExecutionContext, reason: String? = null): JobKillResult

    /** Wait for settlement or timeout without cancelling the job. */
    public suspend fun waitJob(id: JobId, timeoutMillis: Long, execution: ToolExecutionContext): JobView

    /** Drop one settled job after its terminal state was collected. */
    public suspend fun remove(id: JobId, execution: ToolExecutionContext)
    public fun subscribe(listener: JobListener): Disposable
}

public object JobsKey : ServiceKey<JobsService>("jobs.local")

public data class JobsConfig(
    val maxOutputChars: Int = 256 * 1024,
    val maxJobs: Int = 256,
    val shutdownTimeoutMillis: Long = 2_000,
    val listenTimeoutMillis: Long = 2_000,
) {
    init {
        require(maxOutputChars >= 2)
        require(maxJobs > 0)
        require(shutdownTimeoutMillis > 0)
        require(listenTimeoutMillis > 0)
    }
}

/**
 * Process-local registry. Producers own execution resources; this service owns
 * identity, owner-fenced access, lifecycle, the bounded output ring, the
 * consuming cursor, and disposal.
 */
public class LocalJobs(
    private val context: Context,
    private val config: JobsConfig = JobsConfig(),
) : JobsService, Disposable {
    private val store = context.require(SessionKey)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val records = LinkedHashMap<JobId, Record>()
    private val ordinals = mutableMapOf<String, Long>()
    private val listeners = CopyOnWriteArrayList<JobListener>()
    private val disposed = AtomicBoolean(false)

    override suspend fun start(spec: JobSpec, execution: ToolExecutionContext): JobView {
        requireOwnedExecution(execution)
        spec.owner?.let { require(it == execution.session.id) { "Job owner does not match the trusted Session" } }
        val (id, record) = gate.withLock {
            check(!disposed.get()) { "Job registry is disposed" }
            makeRoom()
            val ordinal = (ordinals[spec.kind] ?: 0L) + 1
            ordinals[spec.kind] = ordinal
            val id = JobId("${spec.kind}-$ordinal")
            id to Record(id, spec, execution.session.id, scope)
        }
        val hooks = spec.producer.start(record)
        val published = gate.withLock {
            if (disposed.get()) false else {
                record.hooks = hooks
                records[id] = record
                true
            }
        }
        if (!published) {
            runCatching { hooks.cancel("Registry disposed during start") }
            error("Job registry is disposed")
        }
        scope.launch { monitor(record, hooks) }
        return record.view()
    }

    override suspend fun list(execution: ToolExecutionContext): List<JobView> {
        requireOwnedExecution(execution)
        return gate.withLock {
            records.values.filter { it.owner == execution.session.id }.map { it.viewLocked() }
        }
    }

    override suspend fun get(id: JobId, execution: ToolExecutionContext): JobView {
        requireOwnedExecution(execution)
        return gate.withLock { requireVisible(id, execution).viewLocked() }
    }

    override suspend fun read(id: JobId, execution: ToolExecutionContext): JobRead {
        requireOwnedExecution(execution)
        return gate.withLock {
            val record = requireVisible(id, execution)
            val ringRead = record.read(record.cursor)
            record.cursor = ringRead.next
            val result = record.result.also { record.result = null }
            JobRead(ringRead.chunks, ringRead.lossy, result, record.viewLocked())
        }
    }

    override suspend fun kill(id: JobId, execution: ToolExecutionContext, reason: String?): JobKillResult {
        requireOwnedExecution(execution)
        val record = gate.withLock {
            val record = requireVisible(id, execution)
            if (record.status.isTerminal) return JobKillResult(JobKillOutcome.ALREADY_FINISHED, record.viewLocked())
            if (record.status == JobStatus.Stopping) return JobKillResult(JobKillOutcome.REQUESTED, record.viewLocked())
            record.status = JobStatus.Stopping
            record.killRequested = true
            record.killReason = reason?.takeIf(String::isNotBlank)
            record.viewLocked()
            record
        }
        record.hooks.cancel(reason)
        return JobKillResult(JobKillOutcome.REQUESTED, record.view())
    }

    override suspend fun waitJob(id: JobId, timeoutMillis: Long, execution: ToolExecutionContext): JobView {
        requireOwnedExecution(execution)
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val record = gate.withLock {
            val visible = requireVisible(id, execution)
            if (!visible.status.isTerminal) visible.waiters++
            visible
        }
        if (record.status.isTerminal) return record.view()
        try {
            withTimeoutOrNull(timeoutMillis) { record.settled.await() }
        } finally {
            gate.withLock {
                if (record.waiters > 0) record.waiters--
            }
        }
        return record.view()
    }

    override suspend fun remove(id: JobId, execution: ToolExecutionContext) {
        requireOwnedExecution(execution)
        gate.withLock {
            val record = requireVisible(id, execution)
            check(record.status.isTerminal) { "A live job cannot be removed" }
            records.remove(id)
        }
    }

    override fun subscribe(listener: JobListener): Disposable {
        check(!disposed.get()) { "Job registry is disposed" }
        listeners += listener
        return Disposable { listeners -= listener }
    }

    /**
     * Session disposal is a synchronous event. Requesting cancellation here
     * only marks the owner's live jobs; producer shutdown runs on the registry
     * scope so Session teardown never waits on process cleanup.
     */
    internal fun ownerDisposed(owner: SessionId) {
        if (disposed.get()) return
        scope.launch {
            val live = gate.withLock {
                records.values.filter { it.owner == owner && !it.status.isTerminal }.onEach {
                    it.status = JobStatus.Stopping
                    it.killRequested = true
                    it.teardownRequested = true
                    it.killReason = "session disposed"
                }
            }
            live.forEach { record ->
                try {
                    record.hooks.cancel("session disposed")
                } catch (_: Throwable) {
                    // Monitor settlement reports a producer that cannot cancel.
                }
            }
        }
    }

    private suspend fun monitor(record: Record, hooks: JobHooks) {
        val outcome = try {
            hooks.await()
        } catch (_: CancellationException) {
            JobOutcome(JobStatus.Failed, "producer was cancelled during teardown")
        } catch (error: Throwable) {
            JobOutcome(JobStatus.Failed, error.message ?: error::class.simpleName)
        }
        settle(record, outcome)
    }

    private suspend fun settle(record: Record, outcome: JobOutcome) {
        val event = gate.withLock {
            if (record.status.isTerminal) return
            record.status = when {
                record.killRequested -> JobStatus.Killed
                else -> outcome.status
            }
            record.detail = buildList {
                outcome.detail?.takeIf(String::isNotBlank)?.let(::add)
                if (record.killRequested) record.killReason?.let(::add)
            }.joinToString(": ").takeIf(String::isNotEmpty)
            record.result = outcome.result
            record.progress = null
            record.finishedAt = System.currentTimeMillis()
            record.settled.complete(Unit)
            val cause = when {
                record.teardownRequested -> JobSettlementCause.TEARDOWN
                record.killRequested -> JobSettlementCause.KILL
                else -> JobSettlementCause.NATURAL
            }
            JobSettlementEvent(record.viewLocked(), cause, record.waiters > 0)
        }
        // Delivery is contained: a listener failure cannot change settlement.
        for (listener in listeners) {
            try {
                listener.settled(event)
            } catch (_: Throwable) {
                // Observers are not part of the producer contract.
            }
        }
    }

    private fun makeRoom() {
        while (records.size >= config.maxJobs) {
            val expired = records.values.firstOrNull { it.status.isTerminal }
                ?: error("Owned job limit reached")
            records.remove(expired.id)
        }
    }

    private suspend fun requireOwnedExecution(execution: ToolExecutionContext) {
        check(!disposed.get()) { "Job registry is disposed" }
        require(execution.context.root === context.root) { "Execution belongs to another Host" }
        require(execution.context.require(SessionKey) === store) { "Execution uses another store" }
        require(store.get(execution.session.id) === execution.session && execution.session.isLive()) {
            "Session is no longer live"
        }
    }

    private fun requireVisible(id: JobId, execution: ToolExecutionContext): Record {
        val record = records[id] ?: error("Unknown job '$id'")
        require(record.owner == execution.session.id) { "Job '$id' belongs to another Session" }
        return record
    }

    override suspend fun dispose() = withContext(NonCancellable + Dispatchers.IO) {
        if (!disposed.compareAndSet(false, true)) return@withContext
        val live = gate.withLock {
            val owned = records.values.filter { !it.status.isTerminal }
            owned.forEach {
                it.status = JobStatus.Stopping
                it.killRequested = true
                it.teardownRequested = true
                it.killReason = "teardown"
            }
            owned
        }
        // Cancel requests are producer-owned and may be slow; keep each one bounded.
        live.forEach { record ->
            withTimeoutOrNull(config.listenTimeoutMillis) {
                try {
                    record.hooks.cancel("teardown")
                } catch (_: Throwable) {
                    // A broken cancel path is force-settled below.
                }
            }
        }
        withTimeoutOrNull(config.shutdownTimeoutMillis) {
            live.forEach { it.settled.await() }
        }
        gate.withLock {
            records.values.filter { !it.status.isTerminal }.forEach { record ->
                record.status = JobStatus.Failed
                record.detail = record.detail ?: "teardown did not settle"
                record.finishedAt = System.currentTimeMillis()
                record.settled.complete(Unit)
            }
        }
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    private inner class Record(
        override val id: JobId,
        spec: JobSpec,
        val owner: SessionId,
        private val jobScope: CoroutineScope,
    ) : JobHandle {
        override val scope: CoroutineScope get() = jobScope
        val kind = spec.kind
        val label = spec.label
        val outputLimitChars = spec.outputLimitChars
        val startedAt = System.currentTimeMillis()
        lateinit var hooks: JobHooks
        val ring = Ring(spec.outputLimitChars ?: config.maxOutputChars)
        val settled = CompletableDeferred<Unit>()
        @Volatile var status = JobStatus.Running
        @Volatile var progress: String? = null
        @Volatile var detail: String? = null
        @Volatile var result: String? = null
        var cursor = 0L
        var waiters = 0
        var killRequested = false
        var teardownRequested = false
        var killReason: String? = null
        @Volatile var finishedAt: Long? = null

        override fun append(text: String, channel: String?) {
            if (text.isEmpty()) return
            synchronized(ring) { ring.append(text, channel) }
        }

        fun read(cursor: Long): RingRead = synchronized(ring) { ring.read(cursor) }

        override fun updateProgress(line: String) {
            if (line.isBlank()) return
            if (!status.isTerminal) progress = line
        }

        fun view(): JobView = viewLocked()

        fun viewLocked(): JobView = JobView(
            id = id,
            kind = kind,
            label = label,
            owner = owner,
            outputLimitChars = outputLimitChars,
            status = status,
            progress = progress,
            detail = detail,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
    }

    private data class RingRead(val chunks: List<JobChunk>, val lossy: Boolean, val next: Long)

    /** Bounded char ring with absolute offsets. */
    private class Ring(private val limit: Int) {
        private val chunks = ArrayDeque<JobChunk>()
        private var total = 0L

        fun append(text: String, channel: String?) {
            if (text.isEmpty()) return
            chunks.addLast(JobChunk(total, text, channel))
            total += text.length
            trim()
        }

        fun read(cursor: Long): RingRead {
            val firstAt = chunks.firstOrNull()?.at ?: total
            val effective = cursor.coerceAtLeast(firstAt)
            val lossy = cursor < firstAt || cursor > total
            val tail = chunks.mapNotNull { chunk ->
                val end = chunk.at + chunk.text.length
                when {
                    end <= effective -> null
                    chunk.at >= effective -> chunk
                    else -> chunk.copy(text = chunk.text.substring((effective - chunk.at).toInt()))
                }
            }
            return RingRead(tail, lossy, total)
        }

        private fun trim() {
            while (total - (chunks.firstOrNull()?.at ?: total) > limit && chunks.size > 1) {
                chunks.removeFirst()
            }
            val first = chunks.firstOrNull() ?: return
            if (first.text.length > limit) {
                val cut = first.text.length - limit
                chunks[0] = first.copy(at = first.at + cut, text = first.text.substring(cut), gapBefore = true)
            }
        }
    }
}

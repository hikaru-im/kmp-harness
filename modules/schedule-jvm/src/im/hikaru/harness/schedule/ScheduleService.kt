package im.hikaru.harness.schedule

import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.persistence.SessionPersistenceKey
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

public const val SUBAGENT_SESSION_MESSAGE: String =
    "This Session belongs to subagent routing, which never receives reminder delivery."

public data class ScheduleCreateRequest(
    val title: String,
    val prompt: String,
    /** The service accepts exactly one; the tool layer rejects unknown fields first. */
    val selectors: List<ScheduleSelector>,
)

public data class ScheduleUpdateRequest(
    val id: ScheduleId,
    /** The complete record the caller observed; any difference is a conflict. */
    val expected: ScheduleRecord,
    val title: String? = null,
    val prompt: String? = null,
    val change: ScheduleSelector? = null,
)

public sealed interface ScheduleUpdateOutcome {
    public data class Resolved(
        val record: ScheduleRecord,
        val updated: Boolean,
    ) : ScheduleUpdateOutcome

    public data class Miss(
        val id: ScheduleId,
        val code: String,
    ) : ScheduleUpdateOutcome
}

/** Injectable delivery seam; a failed call leaves the task due. */
public fun interface ScheduleDeliverer {
    public suspend fun deliver(sessionId: SessionId, message: im.hikaru.harness.llm.Message)
}

public interface ScheduleService : Disposable {
    public suspend fun create(sessionId: SessionId, request: ScheduleCreateRequest): ScheduleRecord
    public suspend fun list(sessionId: SessionId): List<ScheduleRecord>
    public suspend fun catalog(sessionId: SessionId? = null): List<StoredSchedule>
    public suspend fun update(sessionId: SessionId, request: ScheduleUpdateRequest): ScheduleUpdateOutcome
    public suspend fun delete(sessionId: SessionId, id: ScheduleId): Boolean

    /** Explicit external drive; a non-`now` sample is accepted only by tests. */
    public suspend fun drive(now: Long)

    /** Coalesced wake used by management changes and the durable timer. */
    public fun requestDrive()
}

internal class DefaultScheduleService(
    private val context: Context,
    private val store: ScheduleStore,
    private val clock: ScheduleClock = SystemScheduleClock,
    private val config: ScheduleConfig = ScheduleConfig(),
    private val deliverer: ScheduleDeliverer = SessionScheduleDeliverer(context),
) : ScheduleService {
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val failed = mutableSetOf<ScheduleId>()
    private var loop: Job? = null
    private var stopping = false

    @Volatile
    private var externalRequest = false

    suspend fun initialize() {
        store.all()
    }

    fun start(scope: CoroutineScope) {
        check(loop == null) { "Schedule service already started" }
        loop = scope.launch {
            while (isActive && !stopping) {
                val delay = mutex.withLock {
                    if (externalRequest) {
                        externalRequest = false
                        failed.clear()
                    }
                    driveLocked(clock.nowEpochMillis())
                }
                if (delay == null) {
                    wake.receive()
                } else {
                    withTimeoutOrNull(delay) { wake.receive() }
                }
            }
        }
    }

    override fun requestDrive() {
        externalRequest = true
        wake.trySend(Unit)
    }

    override suspend fun create(
        sessionId: SessionId,
        request: ScheduleCreateRequest,
    ): ScheduleRecord {
        // DSH samples creation time before entering the serial queue.
        val now = clock.nowEpochMillis()
        if (request.selectors.size != 1) {
            throw ScheduleInputException(
                ScheduleErrorCode.InvalidSelector,
                "Exactly one reminder selector is required.",
            )
        }
        if (isSubagentSession(sessionId)) {
            throw ScheduleInputException(ScheduleErrorCode.SubagentSession, SUBAGENT_SESSION_MESSAGE)
        }
        return mutex.withLock {
            check(!stopping) { "Schedule service is stopping" }
            val record = createScheduleRecord(
                id = ScheduleId.random(),
                selector = request.selectors.single(),
                now = now,
                title = request.title,
                prompt = request.prompt,
            )
            store.put(StoredSchedule(sessionId, record, ScheduleStatus.Active))
            requestDrive()
            record
        }
    }

    override suspend fun list(sessionId: SessionId): List<ScheduleRecord> = mutex.withLock {
        check(!stopping) { "Schedule service is stopping" }
        store.all()
            .filter { it.sessionId == sessionId && it.status == ScheduleStatus.Active }
            .map { it.record }
    }

    override suspend fun catalog(sessionId: SessionId?): List<StoredSchedule> = mutex.withLock {
        check(!stopping) { "Schedule service is stopping" }
        store.all().filter { sessionId == null || it.sessionId == sessionId }
    }

    override suspend fun update(
        sessionId: SessionId,
        request: ScheduleUpdateRequest,
    ): ScheduleUpdateOutcome = mutex.withLock {
        check(!stopping) { "Schedule service is stopping" }
        if (isSubagentSession(sessionId)) {
            return@withLock ScheduleUpdateOutcome.Miss(request.id, "subagent_session")
        }
        val task = store.find(request.id)?.takeIf { it.sessionId == sessionId }
            ?: return@withLock ScheduleUpdateOutcome.Miss(request.id, SCHEDULE_NOT_FOUND)
        if (task.status == ScheduleStatus.Inactive) {
            return@withLock ScheduleUpdateOutcome.Miss(request.id, SCHEDULE_ENDED)
        }
        // Update samples its re-anchor time inside the serial queue slot.
        val resolution = resolveScheduleUpdate(
            current = task.record,
            expected = request.expected,
            change = request.change,
            now = clock.nowEpochMillis(),
            title = request.title,
            prompt = request.prompt,
        )
        val record = resolution.record
            ?: return@withLock ScheduleUpdateOutcome.Miss(request.id, resolution.code ?: SCHEDULE_CONFLICT)
        if (resolution.updated) {
            store.put(task.copy(record = record))
            requestDrive()
        }
        ScheduleUpdateOutcome.Resolved(record, resolution.updated)
    }

    override suspend fun delete(sessionId: SessionId, id: ScheduleId): Boolean = mutex.withLock {
        check(!stopping) { "Schedule service is stopping" }
        val task = store.find(id)?.takeIf { it.sessionId == sessionId } ?: return@withLock false
        store.delete(task.record.id)
        true
    }

    override suspend fun drive(now: Long) {
        mutex.withLock {
            check(!stopping) { "Schedule service is stopping" }
            failed.clear()
            driveLocked(now)
        }
    }

    override suspend fun dispose() {
        stopping = true
        wake.trySend(Unit)
        loop?.cancel()
        loop?.join()
        loop = null
        store.dispose()
    }

    private suspend fun driveLocked(scanNow: Long): Long? {
        val tasks = store.all()
        val due = tasks.filter {
            it.status == ScheduleStatus.Active && it.record.scheduledAt <= scanNow
        }
        if (due.isEmpty()) return nextDelay(tasks)

        val handled = mutableSetOf<ScheduleId>()
        val committed = mutableSetOf<ScheduleId>()
        try {
            for (task in due) {
                if (task.record.id in handled) continue
                val group = if (task.record.kind.isRecurring) {
                    due.filter {
                        it.record.id !in handled &&
                            it.sessionId == task.sessionId &&
                            it.record.kind.isRecurring
                    }
                } else {
                    listOf(task)
                }
                group.forEach { handled += it.record.id }
                deliverGroup(group, committed)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Failed reminders stay active and due but are excluded from this timer,
            // preventing a hot retry loop until an explicit drive or restart.
            val failedAt = clock.nowEpochMillis()
            due.filter { it.record.id !in committed && it.record.scheduledAt <= failedAt }
                .forEach { failed += it.record.id }
        }
        return nextDelay(store.all())
    }

    private suspend fun deliverGroup(
        group: List<StoredSchedule>,
        committed: MutableSet<ScheduleId>,
    ) {
        val decision = clock.nowEpochMillis()
        val admitted = group.filter { it.record.scheduledAt <= decision }
        if (admitted.isEmpty()) return
        val sessionId = admitted.first().sessionId
        check(!isSubagentSession(sessionId)) { SUBAGENT_SESSION_MESSAGE }

        val recurring = admitted.filter { it.record.kind.isRecurring }
        val occurrences = recurring.associate { task ->
            task.record.id to resolveRecurringOccurrence(task.record, decision)
        }
        val text = if (recurring.isEmpty()) {
            val task = admitted.single()
            renderReminderFraming(task.record, task.record.scheduledAt)
        } else {
            renderRecurringReminderBatchFraming(
                recurring.map { it.record to occurrences.getValue(it.record.id).occurrenceAt },
            )
        }
        val message = createUserMessage(
            content = listOf(TextBlock(text)),
            source = PluginMessageSource(SCHEDULE_PLUGIN_NAME),
        )
        deliverer.deliver(sessionId, message)
        val deliveredAt = clock.nowEpochMillis()
        val messageId = message.id.value

        for (task in admitted) {
            if (task.record.kind.isRecurring) {
                val occurrence = occurrences.getValue(task.record.id)
                val next = occurrence.nextScheduledAt
                store.put(
                    task.copy(
                        record = task.record.withScheduledAt(next ?: occurrence.occurrenceAt),
                        status = if (next == null) ScheduleStatus.Inactive else ScheduleStatus.Active,
                        lastDelivery = ScheduleDeliveryReceipt(occurrence.occurrenceAt, deliveredAt, messageId),
                    ),
                )
            } else {
                store.put(
                    task.copy(
                        status = ScheduleStatus.Inactive,
                        lastDelivery = ScheduleDeliveryReceipt(task.record.scheduledAt, deliveredAt, messageId),
                    ),
                )
            }
            committed += task.record.id
        }
    }

    private fun nextDelay(tasks: List<StoredSchedule>): Long? {
        val next = tasks.asSequence()
            .filter { it.status == ScheduleStatus.Active && it.record.id !in failed }
            .map { it.record.scheduledAt }
            .minOrNull() ?: return null
        return (next - clock.nowEpochMillis()).coerceIn(0L, config.maxTimerDelayMillis)
    }

    private suspend fun isSubagentSession(sessionId: SessionId): Boolean {
        val live = context.agents.get(AgentId(sessionId.value))?.session
            ?: context.get(SessionKey)?.get(sessionId)
        if (live != null) return (live.header.delegationDepth ?: 0L) > 0L
        val header = context.get(SessionPersistenceKey)?.listHeaders()
            ?.firstOrNull { it.id == sessionId }
        return (header?.delegationDepth ?: 0L) > 0L
    }
}

/** Delivers into the bound Session through the same facade as a user prompt. */
internal class SessionScheduleDeliverer(
    private val context: Context,
) : ScheduleDeliverer {
    override suspend fun deliver(sessionId: SessionId, message: im.hikaru.harness.llm.Message) {
        context.require(SessionApiKey).prompt(sessionId, message)
        // The inbox splice is synchronous; require the durable acknowledgment
        // before the schedule row advances, matching DSH's flush gate.
        context.sessions.get(sessionId)?.let { session ->
            context.get(SessionPersistenceKey)?.flush(session)
        }
    }
}

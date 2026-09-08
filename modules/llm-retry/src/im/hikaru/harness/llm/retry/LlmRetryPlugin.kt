package im.hikaru.harness.llm.retry

import im.hikaru.harness.agent.AgentEvents
import im.hikaru.harness.agent.AgentAttemptResume
import im.hikaru.harness.agent.AgentAttemptResumer
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.agent.AgentRequestErrorDecision
import im.hikaru.harness.agent.AgentRequestErrorEvent
import im.hikaru.harness.llm.AlwaysRetryPolicy
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.NormalRetryPolicy
import im.hikaru.harness.llm.RetryPolicy
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.session.SessionEventEnvelope
import im.hikaru.harness.session.SessionEventKey
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.CompletedTurnEndReason
import im.hikaru.harness.session.MaxTokensTurnEndReason
import im.hikaru.harness.session.RequestHeaderEvent
import im.hikaru.harness.session.SessionEventNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min
import kotlin.random.Random

const val LLM_RETRY_PLUGIN_NAME: String = "llm-retry"

@Serializable
data class LlmRetryEvent(
    val agent: SessionId,
    val turn: Long,
    val step: Long,
    val attempt: Int,
    val failure: LlmFailure,
    val policy: RetryPolicy,
    val notBeforeEpochMilliseconds: Long? = null,
)

@Serializable
data class LlmRetryStartedEvent(
    val agent: SessionId,
    val turn: Long,
    val step: Long,
    val attempt: Int,
)

@Serializable
enum class LlmRetryTerminalStatus {
    SUCCEEDED,
    UNHANDLED,
    EXHAUSTED,
    CANCELLED,
    DISPOSED,
}

@Serializable
data class LlmRetryTerminalEvent(
    val agent: SessionId,
    val turn: Long,
    val step: Long,
    val attempt: Int,
    val status: LlmRetryTerminalStatus,
)

object LlmRetrySessionEvents {
    val Retry = SessionEventKey("llm/retry", LlmRetryEvent.serializer())
    val RetryStarted = SessionEventKey("llm/retry-started", LlmRetryStartedEvent.serializer())
    val Terminal = SessionEventKey("llm/retry-terminal", LlmRetryTerminalEvent.serializer())
}

private data class RetryKey(val session: SessionId, val turn: Long, val step: Long)

private data class RetryState(
    var lastRetryAttempt: Int = 0,
    var lastStartedAttempt: Int = 0,
    var terminal: Boolean = false,
    var lastRetry: LlmRetryEvent? = null,
)

fun interface LlmRetryClock {
    fun nowEpochMilliseconds(): Long
}

object SystemLlmRetryClock : LlmRetryClock {
    override fun nowEpochMilliseconds(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}

data class LlmRetryRecovery(
    val agent: SessionId,
    val turn: Long,
    val step: Long,
    val attempt: Int,
    val notBeforeEpochMilliseconds: Long?,
    val needsStarted: Boolean,
)

/** Finds a retry continuation that has not yet committed its next request. */
fun findPendingLlmRetryRecovery(events: List<SessionEventEnvelope>): LlmRetryRecovery? {
    data class Fold(
        var retry: LlmRetryEvent? = null,
        var startedAttempt: Int? = null,
        var startedSeq: Long = -1L,
        var requestAfterStarted: Boolean = false,
        var terminal: Boolean = false,
    )

    val folds = linkedMapOf<RetryKey, Fold>()
    val codec = Json { ignoreUnknownKeys = false; classDiscriminator = "type" }
    for (event in events) {
        when (event.type) {
            LlmRetrySessionEvents.Retry.name -> {
                val value = codec.decodeFromJsonElement(LlmRetryEvent.serializer(), event.data)
                folds[RetryKey(value.agent, value.turn, value.step)] = Fold(retry = value)
            }
            LlmRetrySessionEvents.RetryStarted.name -> {
                val value = codec.decodeFromJsonElement(LlmRetryStartedEvent.serializer(), event.data)
                val fold = folds.getOrPut(RetryKey(value.agent, value.turn, value.step)) { Fold() }
                fold.startedAttempt = value.attempt
                fold.startedSeq = event.seq
                fold.requestAfterStarted = false
            }
            LlmRetrySessionEvents.Terminal.name -> {
                val value = codec.decodeFromJsonElement(LlmRetryTerminalEvent.serializer(), event.data)
                folds.getOrPut(RetryKey(value.agent, value.turn, value.step)) { Fold() }.terminal = true
            }
            SessionEventNames.REQUEST_HEADER -> {
                val value = codec.decodeFromJsonElement(RequestHeaderEvent.serializer(), event.data)
                folds.values.forEach { fold ->
                    val retry = fold.retry
                    if (retry != null && fold.startedSeq >= 0L && event.seq > fold.startedSeq &&
                        retry.turn == value.turn && retry.step == value.step
                    ) {
                        fold.requestAfterStarted = true
                    }
                }
            }
        }
    }
    return folds.entries.asSequence().mapNotNull { (key, fold) ->
        val retry = fold.retry ?: return@mapNotNull null
        if (fold.terminal || fold.requestAfterStarted) return@mapNotNull null
        when (val started = fold.startedAttempt) {
            null -> LlmRetryRecovery(key.session, key.turn, key.step, retry.attempt + 1, retry.notBeforeEpochMilliseconds, true)
            retry.attempt + 1 -> LlmRetryRecovery(key.session, key.turn, key.step, started, retry.notBeforeEpochMilliseconds, false)
            else -> null
        }
    }.firstOrNull()
}

/** Durable retry executor for request-error waterfall events. */
class LlmRetryPlugin(
    private val clock: LlmRetryClock = SystemLlmRetryClock,
) : SimplePlugin {
    override val inject = setOf(
        im.hikaru.harness.runtime.plugin.InjectSpec.required(AgentKey),
        im.hikaru.harness.runtime.plugin.InjectSpec.required(SessionKey),
    )

    override suspend fun apply(context: Context, scope: EffectScope) {
        val state = RetryStateStore(context, clock)
        state.restoreExisting()
        scope.add(
            context.on(AgentEvents.TurnStopping) { event ->
                if (event.reason === CompletedTurnEndReason || event.reason === MaxTokensTurnEndReason) {
                    state.completeTurn(event.agent.session.id, event.turn)
                }
            }
        )
        scope.add(
            context.on(AgentEvents.RequestError) { event, next ->
                state.decide(event, next)
            }
        )
        scope.add(
            context.on(AgentEvents.Restore) { event ->
                state.resume(event.agent)
            }
        )
        scope.add(Disposable { state.dispose() })
    }
}

private class RetryStateStore(
    private val context: Context,
    private val clock: LlmRetryClock,
) {
    private val mutex = Mutex()
    private val restoreMutex = Mutex()
    private val states = linkedMapOf<RetryKey, RetryState>()
    private val restoredSessions = mutableSetOf<SessionId>()
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
        classDiscriminator = "type"
    }
    private var disposed = false

    suspend fun restoreExisting() {
        for (session in context.sessions.list()) {
            ensureRestored(session)
        }
    }

    /**
     * Session load can happen after this plugin has started. Restore lazily at
     * the first request-error so the decision cannot race an asynchronous
     * observer and duplicate a durable retry event.
     */
    private suspend fun ensureRestored(session: im.hikaru.harness.session.Session) {
        restoreMutex.withLock {
            val alreadyRestored = mutex.withLock { session.id in restoredSessions }
            if (alreadyRestored) return
            restore(session.events())
            mutex.withLock { restoredSessions += session.id }
        }
    }

    private suspend fun restore(events: List<SessionEventEnvelope>) {
        mutex.withLock {
            for (event in events) {
                when (event.type) {
                    LlmRetrySessionEvents.Retry.name -> {
                        val value = json.decodeFromJsonElement(LlmRetryEvent.serializer(), event.data)
                        val key = RetryKey(value.agent, value.turn, value.step)
                        val current = states.getOrPut(key) { RetryState() }
                        current.lastRetryAttempt = maxOf(current.lastRetryAttempt, value.attempt)
                        current.lastRetry = value
                    }
                    LlmRetrySessionEvents.RetryStarted.name -> {
                        val value = json.decodeFromJsonElement(LlmRetryStartedEvent.serializer(), event.data)
                        val key = RetryKey(value.agent, value.turn, value.step)
                        val current = states.getOrPut(key) { RetryState() }
                        current.lastStartedAttempt = maxOf(current.lastStartedAttempt, value.attempt)
                    }
                    LlmRetrySessionEvents.Terminal.name -> {
                        val value = json.decodeFromJsonElement(LlmRetryTerminalEvent.serializer(), event.data)
                        states[RetryKey(value.agent, value.turn, value.step)] = RetryState(
                            lastRetryAttempt = value.attempt,
                            lastStartedAttempt = value.attempt,
                            terminal = true,
                        )
                    }
                }
            }
        }
    }

    suspend fun resume(agent: im.hikaru.harness.agent.Agent) {
        ensureRestored(agent.session)
        val resumer = agent as? AgentAttemptResumer ?: return
        val recovery = findPendingLlmRetryRecovery(agent.session.events()) ?: return
        resumer.resumeAttempt(
            AgentAttemptResume(
                turn = recovery.turn,
                step = recovery.step,
                attempt = recovery.attempt,
                beforeAttempt = {
                    if (!recovery.needsStarted) return@AgentAttemptResume
                    try {
                        val remaining = (recovery.notBeforeEpochMilliseconds ?: clock.nowEpochMilliseconds()) - clock.nowEpochMilliseconds()
                        if (remaining > 0L) delay(remaining)
                        withContext(NonCancellable) {
                            agent.session.append(
                                LlmRetrySessionEvents.RetryStarted,
                                LlmRetryStartedEvent(
                                    agent = recovery.agent,
                                    turn = recovery.turn,
                                    step = recovery.step,
                                    attempt = recovery.attempt,
                                ),
                            )
                        }
                        mutex.withLock {
                            states.getOrPut(RetryKey(recovery.agent, recovery.turn, recovery.step)) { RetryState() }
                                .lastStartedAttempt = recovery.attempt
                        }
                    } catch (cancelled: CancellationException) {
                        complete(
                            RetryKey(recovery.agent, recovery.turn, recovery.step),
                            LlmRetryTerminalStatus.CANCELLED,
                            recovery.attempt,
                        )
                        throw cancelled
                    }
                },
            ),
        )
    }

    suspend fun completeTurn(session: SessionId, turn: Long) {
        val keys = mutex.withLock {
            states.keys.filter { key ->
                key.session == session && key.turn == turn && !states.getValue(key).terminal
            }
        }
        for (key in keys) {
            complete(key, LlmRetryTerminalStatus.SUCCEEDED)
        }
    }

    suspend fun decide(
        event: AgentRequestErrorEvent,
        next: suspend () -> AgentRequestErrorDecision,
    ): AgentRequestErrorDecision {
        ensureRestored(event.agent.session)
        val key = RetryKey(event.agent.session.id, event.turn, event.step)
        val policy = event.retryPolicy
        val shouldHandle = mutex.withLock {
            if (disposed) {
                false
            } else {
                val current = states.getOrPut(key) { RetryState() }
                // A retry-started event records the attempt that should run
                // next; that attempt may itself fail and must be eligible for
                // a new retry. Only a previously persisted retry decision for
                // the same attempt is a duplicate.
                !current.terminal && event.attempt > current.lastRetryAttempt
            }
        }
        if (!shouldHandle) {
            return next()
        }
        if (!isRetryable(policy, event.failure.code, event.attempt)) {
            val decision = next()
            if (decision is AgentRequestErrorDecision.Unhandled) {
                complete(
                    key = key,
                    status =
                        if (policy is NormalRetryPolicy && event.attempt > policy.maxRetries) {
                            LlmRetryTerminalStatus.EXHAUSTED
                        } else {
                            LlmRetryTerminalStatus.UNHANDLED
                        },
                    attempt = event.attempt,
                )
            }
            return decision
        }

        mutex.withLock {
            states.getOrPut(key) { RetryState() }.lastRetryAttempt = event.attempt
        }
        val delayMs = delayFor(policy, event.attempt, event.failure.providerRetryAfterMs)
        val notBefore = clock.nowEpochMilliseconds() + delayMs
        withContext(NonCancellable) {
            event.agent.session.append(
                LlmRetrySessionEvents.Retry,
                LlmRetryEvent(
                    agent = event.agent.session.id,
                    turn = event.turn,
                    step = event.step,
                    attempt = event.attempt,
                    failure = event.failure,
                    policy = policy,
                    notBeforeEpochMilliseconds = notBefore,
                ),
            )
        }
        try {
            delay(delayMs)
        } catch (cancelled: CancellationException) {
            complete(key, LlmRetryTerminalStatus.CANCELLED, event.attempt)
            throw cancelled
        }
        withContext(NonCancellable) {
            event.agent.session.append(
                LlmRetrySessionEvents.RetryStarted,
                LlmRetryStartedEvent(
                    agent = event.agent.session.id,
                    turn = event.turn,
                    step = event.step,
                    attempt = event.attempt + 1,
                ),
            )
        }
        mutex.withLock {
            states.getOrPut(key) { RetryState() }.lastStartedAttempt = event.attempt + 1
        }
        return AgentRequestErrorDecision.Retry
    }

    private suspend fun complete(
        key: RetryKey,
        status: LlmRetryTerminalStatus,
        attempt: Int? = null,
    ) {
        val selected = mutex.withLock {
            val current = states[key] ?: return@withLock null
            if (current.terminal) return@withLock null
            current.terminal = true
            Triple(key, current, attempt ?: maxOf(current.lastStartedAttempt, current.lastRetryAttempt))
        } ?: return
        val session = context.sessions.get(selected.first.session) ?: return
        withContext(NonCancellable) {
            session.append(
                LlmRetrySessionEvents.Terminal,
                LlmRetryTerminalEvent(
                    agent = selected.first.session,
                    turn = selected.first.turn,
                    step = selected.first.step,
                    attempt = selected.third,
                    status = status,
                ),
            )
        }
    }

    suspend fun dispose() {
        val pending = mutex.withLock {
            disposed = true
            states.filterValues { !it.terminal }.keys.toList()
        }
        for (key in pending) {
            complete(key, LlmRetryTerminalStatus.DISPOSED)
        }
    }

    private fun isRetryable(policy: RetryPolicy, code: String, attempt: Int): Boolean =
        when (policy) {
            is NormalRetryPolicy -> attempt <= policy.maxRetries && code in policy.retryableCodes
            is AlwaysRetryPolicy -> true
        }

    private fun delayFor(policy: RetryPolicy, attempt: Int, providerDelay: Long?): Long {
        val exponential =
            min(policy.maxDelayMs, policy.initialDelayMs * (1L shl min(attempt - 1, 30)))
        val provider = providerDelay ?: 0L
        val jitter = (exponential * policy.jitterRatio * Random.nextDouble()).toLong()
        return min(policy.maxDelayMs, maxOf(1L, maxOf(exponential, provider) + jitter))
    }
}

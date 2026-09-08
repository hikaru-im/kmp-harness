package im.hikaru.harness.session

import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Session internal constructor(
    val header: SessionHeader,
    private val eventContext: Context,
    private val clock: SessionClock,
    private val observerFailureHandler: SessionObserverFailureHandler,
    internal val ownerStore: SessionStore,
) {
    private val mutex = Mutex()
    private val log = mutableListOf<SessionEventEnvelope>()
    private var lifecycle = SessionLifecycle.PREPARED

    val id: SessionId
        get() = header.id

    suspend fun <T : Any> append(
        key: SessionEventKey<T>,
        value: T,
        sourceEventSeqs: List<Long>? = null,
    ): SessionEventEnvelope {
        val data = encodeSessionEvent(key, value)
        val sources = sourceEventSeqs?.takeIf(List<Long>::isNotEmpty)?.toList()

        return mutex.withLock {
            checkAppendable()
            validateKey(key)

            val seq = log.size.toLong()
            validateSources(sources, seq)

            val time = clock.nowEpochMilliseconds()
            if (time < 0L) {
                invalidEvent("Session event time must not be negative")
            }

            val envelope =
                SessionEventEnvelope(
                    type = key.name,
                    seq = seq,
                    time = time,
                    data = data.detachedJson(),
                    surfaceOp = key.surfaceOperation,
                    sourceEventSeqs = sources,
                    ignorable = true.takeIf { key.ignorable },
                )

            log += envelope

            if (lifecycle.hasEntered) {
                eventContext.emitContained(
                    key = SessionEvents.Appended,
                    event =
                        SessionEventNotice(
                            session = this,
                            event = envelope.detachedCopy(),
                        ),
                    onFailure = ::reportObserverFailure,
                )
            }

            envelope.detachedCopy()
        }
    }

    suspend fun events(): List<SessionEventEnvelope> =
        mutex.withLock {
            log.map(SessionEventEnvelope::detachedCopy)
        }

    /** Restore a prepared Session from a validated durable log. */
    suspend fun restore(events: List<SessionEventEnvelope>) {
        mutex.withLock {
            if (lifecycle != SessionLifecycle.PREPARED) {
                throw SessionException(
                    message = "Session '${id.value}' can only restore before enter",
                    code = SessionErrorCode.REENTRANT_LIFECYCLE,
                )
            }
            events.forEachIndexed { index, event ->
                if (event.seq != index.toLong() || event.seq < 0L || event.time < 0L) {
                    throw SessionException(
                        message = "Session event sequence is not contiguous",
                        code = SessionErrorCode.INVALID_EVENT,
                    )
                }
                if (event.sourceEventSeqs?.any { source -> source < 0L || source >= event.seq } == true) {
                    throw SessionException(
                        message = "Session event provenance is invalid",
                        code = SessionErrorCode.INVALID_EVENT,
                    )
                }
                val expectedSurface =
                    event.type == SessionEventNames.USER_MESSAGE ||
                        event.type == SessionEventNames.ASSISTANT_MESSAGE
                if ((event.surfaceOp != null) != expectedSurface || event.surfaceOp == SessionSurfaceOperation.REPLACE) {
                    throw SessionException(
                        message = "Session event surface metadata is invalid",
                        code = if (event.surfaceOp == SessionSurfaceOperation.REPLACE) {
                            SessionErrorCode.UNSUPPORTED_SURFACE_OPERATION
                        } else {
                            SessionErrorCode.INVALID_EVENT
                        },
                    )
                }
                try {
                    when (event.type) {
                        SessionEventNames.TURN_START -> decode(TurnStartEvent.serializer(), event.data)
                        SessionEventNames.TURN_END -> decode(TurnEndEvent.serializer(), event.data)
                        SessionEventNames.STEP_START -> decode(StepStartEvent.serializer(), event.data)
                        SessionEventNames.STEP_END -> decode(StepEndEvent.serializer(), event.data)
                        SessionEventNames.USER_MESSAGE -> decode(UserMessageEvent.serializer(), event.data)
                        SessionEventNames.ASSISTANT_CHUNK -> decode(AssistantChunkEvent.serializer(), event.data)
                        SessionEventNames.ASSISTANT_MESSAGE -> decode(AssistantMessageEvent.serializer(), event.data)
                        SessionEventNames.REQUEST_HEADER -> decode(RequestHeaderEvent.serializer(), event.data)
                        SessionEventNames.REQUEST_CONTEXT -> decode(RequestContextEvent.serializer(), event.data)
                    }
                } catch (error: SessionException) {
                    throw error
                } catch (error: Throwable) {
                    throw SessionException(
                        message = "Session event '${event.type}' has invalid data",
                        code = SessionErrorCode.INVALID_EVENT,
                        cause = error,
                    )
                }
                log += event.detachedCopy()
            }
        }
    }

    suspend fun deriveMessages(): List<Message> =
        mutex.withLock {
            deriveMessages(log)
        }

    suspend fun requestHeader(): EpochHeader? =
        mutex.withLock {
            foldRequestHeader(log)
        }

    suspend fun requestContext(): SessionRequestContext? =
        mutex.withLock {
            foldRequestContext(log)
        }

    suspend fun isLive(): Boolean =
        mutex.withLock { lifecycle.hasEntered }

    internal suspend fun enter() {
        mutex.withLock {
            if (lifecycle != SessionLifecycle.PREPARED) {
                throw SessionException(
                    message = "Session '${id.value}' cannot enter from $lifecycle",
                    code = SessionErrorCode.REENTRANT_LIFECYCLE,
                )
            }
            lifecycle = SessionLifecycle.ENTERED
        }
    }

    internal suspend fun beginAnnounce() {
        mutex.withLock {
            if (lifecycle != SessionLifecycle.ENTERED) {
                throw SessionException(
                    message = "Session '${id.value}' cannot announce from $lifecycle",
                    code = SessionErrorCode.REENTRANT_LIFECYCLE,
                )
            }
            lifecycle = SessionLifecycle.ANNOUNCING
        }
    }

    internal suspend fun completeAnnounce() {
        mutex.withLock {
            if (lifecycle != SessionLifecycle.ANNOUNCING) {
                throw SessionException(
                    message = "Session '${id.value}' cannot complete announce from $lifecycle",
                    code = SessionErrorCode.REENTRANT_LIFECYCLE,
                )
            }
            lifecycle = SessionLifecycle.LIVE
        }
    }

    internal suspend fun close(): Boolean =
        mutex.withLock {
            if (lifecycle == SessionLifecycle.CLOSED) {
                false
            } else {
                lifecycle = SessionLifecycle.CLOSED
                true
            }
        }

    private fun checkAppendable() {
        if (lifecycle == SessionLifecycle.CLOSED) {
            throw SessionException(
                message = "Session '${id.value}' is closed",
                code = SessionErrorCode.CLOSED_LIFECYCLE,
            )
        }
    }

    private fun validateKey(key: SessionEventKey<*>) {
        val expectedSurface =
            key.name == SessionEventNames.USER_MESSAGE ||
                key.name == SessionEventNames.ASSISTANT_MESSAGE

        if (key.surfaceOperation == SessionSurfaceOperation.REPLACE) {
            throw SessionException(
                message = "Session S1 does not support surface replacement",
                code = SessionErrorCode.UNSUPPORTED_SURFACE_OPERATION,
            )
        }

        if ((key.surfaceOperation != null) != expectedSurface) {
            invalidEvent("Session event '${key.name}' has invalid surface metadata")
        }

        if (key.name in coreEventNames && key.ignorable) {
            invalidEvent("Core Session event '${key.name}' cannot be ignorable")
        }
    }

    private fun validateSources(
        sources: List<Long>?,
        candidateSeq: Long,
    ) {
        if (sources == null) {
            return
        }
        if (sources.distinct().size != sources.size) {
            invalidEvent("Session event provenance must not contain duplicates")
        }
        if (sources.any { source -> source < 0L || source >= candidateSeq }) {
            invalidEvent("Session event provenance must reference earlier committed events")
        }
    }

    private fun reportObserverFailure(error: Throwable) {
        try {
            observerFailureHandler.onFailure(error)
        } catch (_: Throwable) {
            // Failure reporting is observational and cannot change a committed append.
        }
    }

    private companion object {
        val coreEventNames =
            setOf(
                SessionEventNames.TURN_START,
                SessionEventNames.TURN_END,
                SessionEventNames.STEP_START,
                SessionEventNames.STEP_END,
                SessionEventNames.USER_MESSAGE,
                SessionEventNames.ASSISTANT_CHUNK,
                SessionEventNames.ASSISTANT_MESSAGE,
                SessionEventNames.REQUEST_HEADER,
                SessionEventNames.REQUEST_CONTEXT,
            )
    }
}

private enum class SessionLifecycle {
    PREPARED,
    ENTERED,
    ANNOUNCING,
    LIVE,
    CLOSED,

    ;

    val hasEntered: Boolean
        get() = this == ENTERED || this == ANNOUNCING || this == LIVE
}

package im.hikaru.harness.session

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionHandle internal constructor(
    val session: Session,
    private val store: SessionStore,
) : Disposable {
    override suspend fun dispose() {
        store.detach(session)
    }
}

class SessionStore internal constructor(
    private val context: Context,
    private val idFactory: SessionIdFactory = RandomSessionIdFactory,
    private val clock: SessionClock = SystemSessionClock,
    private val observerFailureHandler: SessionObserverFailureHandler = IgnoreSessionObserverFailures,
) : Disposable {
    private val mutex = Mutex()
    private val live = linkedMapOf<SessionId, Session>()
    private var disposed = false

    suspend fun prepare(
        id: SessionId? = null,
        options: CreateSessionOptions = CreateSessionOptions(),
    ): Session =
        mutex.withLock {
            checkActive()
            val sessionId = id ?: idFactory.create()
            val createdAt = clock.nowEpochMilliseconds()
            val header = options.toHeader(sessionId, createdAt)
            Session(
                header = header,
                eventContext = context,
                clock = clock,
                observerFailureHandler = observerFailureHandler,
                ownerStore = this,
            )
        }

    suspend fun enter(session: Session): SessionHandle =
        mutex.withLock {
            checkActive()
            requireOwned(session)
            if (live.containsKey(session.id)) {
                throw SessionException(
                    message = "Session '${session.id.value}' is already live",
                    code = SessionErrorCode.DUPLICATE_ID,
                )
            }

            session.enter()
            live[session.id] = session
            SessionHandle(session, this)
        }

    suspend fun announce(session: Session) {
        var failure: Throwable? = null

        mutex.withLock {
            withContext(NonCancellable) {
                requireLive(session)
                session.beginAnnounce()
                try {
                    context.emit(
                        SessionEvents.Created,
                        SessionNotice(session),
                    )
                    session.completeAnnounce()
                } catch (error: Throwable) {
                    live.remove(session.id)
                    session.close()
                    failure = error
                }
            }
        }

        failure?.let { error ->
            emitDisposed(session)
            throw error
        }
    }

    suspend fun create(
        id: SessionId? = null,
        options: CreateSessionOptions = CreateSessionOptions(),
    ): SessionHandle {
        val session = prepare(id, options)
        val handle = enter(session)
        try {
            announce(session)
            currentCoroutineContext().ensureActive()
        } catch (error: Throwable) {
            handle.rollbackAndRethrow(error)
        }
        return handle
    }

    /** Load a previously validated durable session without publishing a partial instance. */
    suspend fun load(
        header: SessionHeader,
        events: List<SessionEventEnvelope>,
    ): SessionHandle {
        val session =
            mutex.withLock {
                checkActive()
                Session(
                    header = header,
                    eventContext = context,
                    clock = clock,
                    observerFailureHandler = observerFailureHandler,
                    ownerStore = this,
                )
            }
        try {
            session.restore(events)
            val handle = enter(session)
            try {
                announce(session)
            } catch (error: Throwable) {
                handle.rollbackAndRethrow(error)
            }
            return handle
        } catch (error: Throwable) {
            session.close()
            throw error
        }
    }

    suspend fun get(id: SessionId): Session? =
        mutex.withLock {
            checkActive()
            live[id]
        }

    suspend fun list(): List<Session> =
        mutex.withLock {
            checkActive()
            live.values.toList()
        }

    /**
     * Returns a lifecycle handle for an already-live Session.
     *
     * This is used by an owner Context when it adopts a loaded Session. The
     * returned handle is identity-bound to the exact Session instance, so a
     * later Session with the same id cannot be detached by this handle.
     */
    suspend fun attach(session: Session): SessionHandle =
        mutex.withLock {
            checkActive()
            requireOwned(session)
            check(live[session.id] === session) {
                "Session '${session.id.value}' is not live"
            }
            SessionHandle(session, this)
        }

    suspend fun flush() {
        val snapshot =
            mutex.withLock {
                checkActive()
                live.values.toList()
            }

        var failure: Throwable? = null
        for (session in snapshot) {
            try {
                context.parallel(
                    SessionEvents.Flush,
                    SessionNotice(session),
                )
            } catch (error: Throwable) {
                failure = combineFailures(failure, error)
            }
        }
        failure?.let { throw it }
    }

    internal suspend fun detach(session: Session) {
        val removed =
            mutex.withLock {
                if (live[session.id] !== session) {
                    false
                } else {
                    session.close()
                    live.remove(session.id)
                    true
                }
            }

        if (removed) {
            emitDisposed(session)
        }
    }

    override suspend fun dispose() {
        val snapshot =
            mutex.withLock {
                if (disposed) {
                    return
                }
                disposed = true
                live.values.toList().asReversed()
            }

        for (session in snapshot) {
            detach(session)
        }
    }

    private fun requireOwned(session: Session) {
        if (session.ownerStore !== this) {
            throw SessionException(
                message = "Session belongs to another store",
                code = SessionErrorCode.NOT_LIVE,
            )
        }
    }

    private fun requireLive(session: Session) {
        requireOwned(session)
        if (live[session.id] !== session) {
            throw SessionException(
                message = "Session '${session.id.value}' is not live",
                code = SessionErrorCode.NOT_LIVE,
            )
        }
    }

    private fun checkActive() {
        if (disposed) {
            throw SessionException(
                message = "Session store is disposed",
                code = SessionErrorCode.STORE_DISPOSED,
            )
        }
    }

    private fun emitDisposed(session: Session) {
        context.emitContained(
            key = SessionEvents.Disposed,
            event = SessionNotice(session),
            onFailure = { error ->
                try {
                    observerFailureHandler.onFailure(error)
                } catch (_: Throwable) {
                    // Cleanup cannot be vetoed by an observer or its reporter.
                }
            },
        )
    }
}

internal suspend fun SessionHandle.rollbackAndRethrow(error: Throwable): Nothing {
    try {
        withContext(NonCancellable) {
            dispose()
        }
    } catch (cleanupError: Throwable) {
        if (cleanupError !== error) {
            error.addSuppressed(cleanupError)
        }
    }
    throw error
}

private fun combineFailures(
    current: Throwable?,
    next: Throwable,
): Throwable =
    if (current == null) {
        next
    } else {
        if (next !== current) {
            current.addSuppressed(next)
        }
        current
    }

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
    val admission = SessionAdmission(this)
    private val mutex = Mutex()
    private val live = linkedMapOf<SessionId, Session>()
    private var disposed = false

    suspend fun prepare(
        id: SessionId? = null,
        options: CreateSessionOptions = CreateSessionOptions(),
    ): Session = admission.acquireCatalog().useAdmission { permit -> prepareAdmitted(id, options, permit) }

    private suspend fun prepareAdmitted(
        id: SessionId?,
        options: CreateSessionOptions,
        permit: SessionAdmissionPermit,
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
            ).also { permit.requireSession(it, lifecycle = true) }
        }

    suspend fun enter(session: Session): SessionHandle =
        admission.acquireCatalog(session).useAdmission { permit -> enterAdmitted(session, permit) }

    private suspend fun enterAdmitted(session: Session, permit: SessionAdmissionPermit): SessionHandle =
        mutex.withLock {
            permit.requireSession(session, lifecycle = true)
            checkActive()
            requireOwned(session)
            if (live.containsKey(session.id)) {
                throw SessionException(
                    message = "Session '${session.id.value}' is already live",
                    code = SessionErrorCode.DUPLICATE_ID,
                )
            }

            session.enter(permit)
            live[session.id] = session
            SessionHandle(session, this)
        }

    suspend fun announce(session: Session) {
        admission.acquireCatalog(session).useAdmission { permit -> announceAdmitted(session, permit) }
    }

    private suspend fun announceAdmitted(session: Session, permit: SessionAdmissionPermit) {
        permit.requireSession(session, lifecycle = true)
        var failure: Throwable? = null

        mutex.withLock {
            withContext(NonCancellable) {
                checkActive()
                requireLive(session)
                session.beginAnnounce(permit)
                try {
                    context.emit(
                        SessionEvents.Created,
                        SessionNotice(session),
                    )
                    session.completeAnnounce(permit)
                } catch (error: Throwable) {
                    live.remove(session.id)
                    session.close(permit)
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
    ): SessionHandle =
        admission.acquireCatalog().useAdmission { permit ->
            val session = prepareAdmitted(id, options, permit)
            try {
                val handle = enterAdmitted(session, permit)
                announceAdmitted(session, permit)
                currentCoroutineContext().ensureActive()
                handle
            } catch (error: Throwable) {
                rollbackAdmitted(session, permit, error)
            }
        }

    /** Load a previously validated durable session without publishing a partial instance. */
    suspend fun load(
        header: SessionHeader,
        events: List<SessionEventEnvelope>,
    ): SessionHandle = admission.acquireCatalog().useAdmission { permit ->
        requireNotArchived(header)
        val session =
            mutex.withLock {
                checkActive()
                Session(
                    header = header,
                    eventContext = context,
                    clock = clock,
                    observerFailureHandler = observerFailureHandler,
                    ownerStore = this,
                ).also { permit.requireSession(it, lifecycle = true) }
            }
        try {
            session.restore(events, permit)
            val handle = enterAdmitted(session, permit)
            announceAdmitted(session, permit)
            currentCoroutineContext().ensureActive()
            handle
        } catch (error: Throwable) {
            rollbackAdmitted(session, permit, error)
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
        admission.acquireCatalog(session).useAdmission { permit ->
            requireNotArchived(session.header)
            mutex.withLock {
                permit.requireSession(session, lifecycle = true)
                checkActive()
                requireOwned(session)
                check(live[session.id] === session) {
                    "Session '${session.id.value}' is not live"
                }
                SessionHandle(session, this)
            }
        }

    suspend fun flush() {
        admission.acquireCatalog().useAdmission { flushAdmitted() }
    }

    private suspend fun flushAdmitted() {
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
        val permit = admission.acquireDetach(session) ?: return
        permit.useAdmission { detachAdmitted(session, it) }
    }

    private suspend fun detachAdmitted(session: Session, permit: SessionAdmissionPermit) {
        permit.requireSession(session, lifecycle = true)
        val removed =
            mutex.withLock {
                if (live[session.id] !== session) {
                    false
                } else {
                    session.close(permit)
                    live.remove(session.id)
                    true
                }
            }

        if (removed) {
            emitDisposed(session)
        }
    }

    override suspend fun dispose() {
        withContext(NonCancellable) {
            admission.stopOwner().useAdmission { permit ->
                val snapshot =
                    mutex.withLock {
                        if (disposed) {
                            return@useAdmission
                        }
                        disposed = true
                        live.values.toList().asReversed()
                    }

                var failure: Throwable? = null
                for (session in snapshot) {
                    try {
                        detachAdmitted(session, permit)
                    } catch (error: Throwable) {
                        failure = combineFailures(failure, error)
                    }
                }
                failure?.let { throw it }
            }
        }
    }

    private suspend fun rollbackAdmitted(
        session: Session,
        permit: SessionAdmissionPermit,
        error: Throwable,
    ): Nothing {
        try {
            withContext(NonCancellable) {
                detachAdmitted(session, permit)
                // Also close prepared instances when enter failed, without touching
                // a different live instance that happens to have the same id.
                session.close(permit)
            }
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) error.addSuppressed(cleanupError)
        }
        throw error
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

    private suspend fun requireNotArchived(header: SessionHeader) {
        if (context.get(SessionArchiveKey)?.isArchived(header) == true) {
            throw SessionException(
                message = "Session '${header.id.value}' is archived",
                code = SessionErrorCode.ARCHIVED,
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

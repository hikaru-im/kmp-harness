package im.hikaru.harness.session

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SessionAdmissionRejection {
    CLOSING,
    OWNER_STOPPED,
    FOREIGN_SESSION,
    CLOSED_SESSION,
}

class SessionAdmissionException(
    val reason: SessionAdmissionRejection,
) : IllegalStateException("Session admission rejected: $reason")

enum class SessionReservationBusyReason {
    RESERVATION_ACTIVE,
    TARGET_ACTIVITY,
    CATALOG_ACTIVITY,
}

sealed interface SessionReservationResult {
    data class Reserved(val reservation: SessionReservation) : SessionReservationResult

    data class Busy(val reason: SessionReservationBusyReason) : SessionReservationResult

    data class Rejected(val reason: SessionAdmissionRejection) : SessionReservationResult
}

/** An in-memory fence only; it does not prove that an Agent, Room or driver is quiet. */
class SessionReservation internal constructor(
    private val admission: SessionAdmission,
    val session: Session,
) : Disposable {
    suspend fun isActive(): Boolean = admission.isActive(this)

    override suspend fun dispose() {
        admission.release(this)
    }
}

/** Explicit activity ownership. Child coroutines must acquire their own permit. */
class SessionAdmissionPermit internal constructor(
    internal val admission: SessionAdmission,
    private val target: Session?,
    internal val catalog: Boolean,
    internal val ownerStop: Boolean = false,
) : Disposable {
    // Only the coordinator changes this, under its mutex. Internal helpers use a
    // permit synchronously within its owning try/finally, never in a child task.
    internal var released: Boolean = false
        private set

    internal fun requireSession(session: Session, lifecycle: Boolean = false) {
        check(!released && session.ownerStore.admission === admission) { "Invalid Session permit" }
        check(target == null || target === session) { "Permit belongs to another Session instance" }
        check(!lifecycle || catalog || ownerStop) { "Lifecycle requires a catalog or owner stop permit" }
    }

    internal fun markReleased() {
        released = true
    }

    internal val identity: SessionAdmissionIdentity?
        get() = target?.admissionIdentity

    override suspend fun dispose() {
        admission.release(this)
    }
}

/**
 * Exclusive archive ownership for one exact live Session.
 *
 * Holding it closes new operation permits for that Session without cancelling, clearing or
 * stopping anything by itself. It is not an absence or deletion proof, and it never authorizes
 * another process.
 */
class SessionArchiveToken internal constructor(
    internal val admission: SessionAdmission,
    internal val session: Session,
) : Disposable {
    internal var released: Boolean = false

    override suspend fun dispose() {
        admission.releaseArchive(this)
    }
}

sealed interface SessionArchiveAcquire {
    data class Granted(val token: SessionArchiveToken) : SessionArchiveAcquire

    data class Busy(val reason: SessionReservationBusyReason) : SessionArchiveAcquire

    data class Rejected(val reason: SessionAdmissionRejection) : SessionArchiveAcquire
}

/** Never derived from SessionId, and never reused by a replacement instance. */
internal class SessionAdmissionIdentity(val admission: SessionAdmission) {
    var closed: Boolean = false
}

/** One coordinator per exact Store incarnation. Its mutex never surrounds owner work. */
class SessionAdmission internal constructor(private val store: SessionStore) {
    private val mutex = Mutex()
    private var stopped = false
    private var catalogActivity = 0
    private val activity = mutableMapOf<SessionAdmissionIdentity, Int>()
    private val permits = mutableSetOf<SessionAdmissionPermit>()
    private var reservation: SessionReservation? = null
    private var archiving: SessionAdmissionIdentity? = null

    suspend fun reserve(session: Session): SessionReservationResult =
        mutex.withLock {
            when {
                stopped -> SessionReservationResult.Rejected(SessionAdmissionRejection.OWNER_STOPPED)
                !owns(session) -> SessionReservationResult.Rejected(SessionAdmissionRejection.FOREIGN_SESSION)
                session.admissionIdentity.closed -> SessionReservationResult.Rejected(SessionAdmissionRejection.CLOSED_SESSION)
                reservation != null -> SessionReservationResult.Busy(SessionReservationBusyReason.RESERVATION_ACTIVE)
                activity.getOrElse(session.admissionIdentity) { 0 } != 0 ->
                    SessionReservationResult.Busy(SessionReservationBusyReason.TARGET_ACTIVITY)
                catalogActivity != 0 -> SessionReservationResult.Busy(SessionReservationBusyReason.CATALOG_ACTIVITY)
                else -> {
                    val acquired = SessionReservation(this, session)
                    reservation = acquired
                    SessionReservationResult.Reserved(acquired)
                }
            }
        }

    suspend fun acquireOperation(session: Session): SessionAdmissionPermit =
        acquire(session, catalog = false)!!

    suspend fun acquireCatalog(session: Session? = null): SessionAdmissionPermit =
        acquire(session, catalog = true)!!

    internal suspend fun acquireDetach(session: Session): SessionAdmissionPermit? =
        acquire(session, catalog = true, detach = true)

    private suspend fun acquire(
        session: Session?,
        catalog: Boolean,
        detach: Boolean = false,
    ): SessionAdmissionPermit? =
        mutex.withLock {
            if (session != null && !owns(session)) {
                throw SessionException("Session belongs to another store", SessionErrorCode.NOT_LIVE)
            }
            // Closed handles are idempotent, including while a replacement is reserved.
            // Once stopped, the owner stop path owns all remaining live cleanup.
            if (detach && (stopped || session?.admissionIdentity?.closed == true)) return@withLock null
            if (stopped) {
                throw SessionException("Session store is disposed", SessionErrorCode.STORE_DISPOSED)
            }
            if (session?.admissionIdentity?.closed == true) {
                throw SessionException("Session '${session.id.value}' is closed", SessionErrorCode.CLOSED_LIFECYCLE)
            }
            if (reservation != null && (catalog || reservation?.session === session)) {
                throw SessionAdmissionException(SessionAdmissionRejection.CLOSING)
            }
            // Exclusive archive ownership closes new operation permits for the exact Session, so no
            // new durable event can start while the caller commits the durable archive record.
            // Catalog and detach permits stay available so the owner can still stop the Agent.
            if (!catalog && archiving != null && archiving === session?.admissionIdentity) {
                throw SessionAdmissionException(SessionAdmissionRejection.CLOSING)
            }
            val permit = SessionAdmissionPermit(this, session, catalog)
            permits += permit
            if (catalog) catalogActivity++
            permit.identity?.let { identity -> activity[identity] = activity.getOrElse(identity) { 0 } + 1 }
            permit
        }

    internal suspend fun stopOwner(): SessionAdmissionPermit =
        mutex.withLock {
            stopped = true
            reservation = null
            archiving = null
            SessionAdmissionPermit(this, target = null, catalog = false, ownerStop = true).also { permits += it }
        }

    suspend fun acquireArchive(session: Session): SessionArchiveAcquire =
        mutex.withLock {
            when {
                stopped -> SessionArchiveAcquire.Rejected(SessionAdmissionRejection.OWNER_STOPPED)
                !owns(session) -> SessionArchiveAcquire.Rejected(SessionAdmissionRejection.FOREIGN_SESSION)
                session.admissionIdentity.closed -> SessionArchiveAcquire.Rejected(SessionAdmissionRejection.CLOSED_SESSION)
                reservation != null || archiving != null ->
                    SessionArchiveAcquire.Busy(SessionReservationBusyReason.RESERVATION_ACTIVE)
                activity.getOrElse(session.admissionIdentity) { 0 } != 0 ->
                    SessionArchiveAcquire.Busy(SessionReservationBusyReason.TARGET_ACTIVITY)
                catalogActivity != 0 -> SessionArchiveAcquire.Busy(SessionReservationBusyReason.CATALOG_ACTIVITY)
                else -> {
                    archiving = session.admissionIdentity
                    SessionArchiveAcquire.Granted(SessionArchiveToken(this, session))
                }
            }
        }

    internal suspend fun releaseArchive(token: SessionArchiveToken) {
        withContext(NonCancellable) {
            mutex.withLock {
                if (token.admission !== this@SessionAdmission || token.released) return@withLock
                token.released = true
                if (archiving === token.session.admissionIdentity) archiving = null
            }
        }
    }

    internal suspend fun retire(session: Session, permit: SessionAdmissionPermit) {
        withContext(NonCancellable) {
            mutex.withLock {
                check(permit in permits) { "Expired Session permit" }
                permit.requireSession(session, lifecycle = true)
                session.admissionIdentity.closed = true
            }
        }
    }

    internal suspend fun release(permit: SessionAdmissionPermit) {
        withContext(NonCancellable) {
            mutex.withLock {
                if (permit.admission !== this@SessionAdmission || !permits.remove(permit)) return@withLock
                permit.markReleased()
                if (permit.catalog) catalogActivity--
                permit.identity?.let { identity ->
                    val remaining = activity.getValue(identity) - 1
                    if (remaining == 0) activity.remove(identity) else activity[identity] = remaining
                }
            }
        }
    }

    internal suspend fun isActive(token: SessionReservation): Boolean =
        mutex.withLock { !stopped && reservation === token }

    internal suspend fun release(token: SessionReservation) {
        withContext(NonCancellable) {
            mutex.withLock {
                if (reservation === token) reservation = null
            }
        }
    }

    private fun owns(session: Session): Boolean =
        session.ownerStore === store && session.admissionIdentity.admission === this
}

internal suspend fun <T> SessionAdmissionPermit.useAdmission(block: suspend (SessionAdmissionPermit) -> T): T =
    try {
        block(this)
    } finally {
        dispose()
    }

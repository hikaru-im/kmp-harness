package im.hikaru.harness.agent

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionArchiveAcquire
import im.hikaru.harness.session.SessionArchiveToken
import im.hikaru.harness.session.SessionHandle
import im.hikaru.harness.session.SessionReservationBusyReason
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class AgentEntry(
    val identity: Any,
    val agent: Agent,
    val ownerContext: Context,
)

/** Agent live registry 与可替换 factory 的唯一协调入口。 */
class AgentRegistry internal constructor(
    private val context: Context,
) : Disposable {
    private val mutex = Mutex()
    private val entries = linkedMapOf<AgentId, AgentEntry>()
    private val creating = mutableMapOf<AgentId, Any>()
    private var factory: AgentFactory? = null
    private var factoryIdentity: Any? = null
    private var disposed = false

    suspend fun registerFactory(
        factory: AgentFactory,
        replace: Boolean = false,
    ): AgentFactoryHandle {
        val identity = Any()
        mutex.withLock {
            checkActive()
            check(replace || this.factory == null) { "Agent factory is already registered" }
            this.factory = factory
            factoryIdentity = identity
        }
        return FactoryHandle(this, identity)
    }

    suspend fun replaceFactory(factory: AgentFactory): AgentFactoryHandle =
        registerFactory(factory, replace = true)

    suspend fun create(
        id: AgentId? = null,
        options: AgentOptions = AgentOptions(),
        sessionOptions: CreateSessionOptions = CreateSessionOptions(),
    ): AgentHandle {
        val store = context.sessions
        val permit = store.admission.acquireCatalog()
        val operationIdentity = Any()
        var creatingId: AgentId? = null
        var publishedId: AgentId? = null
        var publishedIdentity: Any? = null
        var ownerContext: Context? = null
        var uninstalledHandle: SessionHandle? = null
        try {
            val (factorySnapshot, factoryToken) =
                mutex.withLock {
                    checkActive()
                    val selected = factory ?: throw AgentException("No AgentFactory is registered", AgentErrorCode.NO_FACTORY)
                    if (id != null) {
                        if (id in entries || id in creating) {
                            throw AgentException("Agent '${id.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                        }
                        creating[id] = operationIdentity
                        creatingId = id
                    }
                    selected to factoryIdentity
                }
            val createdContext = context.child()
            ownerContext = createdContext
            val createdSession =
                store.prepare(
                    id = id?.let { im.hikaru.harness.session.SessionId(it.value) },
                    options = sessionOptions.copy(
                        cwd = sessionOptions.cwd ?: options.cwd,
                        agentPreset = sessionOptions.agentPreset ?: options.preset,
                    ),
                )
            val sessionHandle = store.enter(createdSession)
            uninstalledHandle = sessionHandle
            createdContext.effect(sessionHandle)
            uninstalledHandle = null
            store.announce(createdSession)
            currentCoroutineContext().ensureActive()
            val agentId = AgentId(createdSession.id.value)
            if (id != null && agentId != id) {
                throw AgentException("Agent factory returned a mismatched id", AgentErrorCode.INVALID_FACTORY)
            }
            if (id == null) {
                mutex.withLock {
                    checkActive()
                    if (agentId in entries || agentId in creating) {
                        throw AgentException("Agent '${agentId.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                    }
                    creating[agentId] = operationIdentity
                    creatingId = agentId
                }
            }
            val inbox = Inbox(agentId, createdSession, eventContext = context)
            val createdAgent =
                factorySnapshot.create(
                    AgentFactoryRequest(
                        id = agentId,
                        options = options,
                        session = createdSession,
                        inbox = inbox,
                        context = createdContext,
                    )
                )
            if (createdAgent.id != agentId || createdAgent.session !== createdSession) {
                throw AgentException("AgentFactory returned an invalid Agent", AgentErrorCode.INVALID_FACTORY)
            }
            inbox.bindAgent(createdAgent)
            val identity = Any()
            mutex.withLock {
                checkActive()
                if (factoryIdentity !== factoryToken) {
                    throw AgentException("Agent factory was disposed during creation", AgentErrorCode.FACTORY_DISPOSED)
                }
                if (agentId in entries) {
                    throw AgentException("Agent '${agentId.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                }
                check(creating[agentId] === operationIdentity) { "Agent creation no longer owns its registration" }
                entries[agentId] = AgentEntry(identity, createdAgent, createdContext)
                publishedId = agentId
                publishedIdentity = identity
                creating.remove(agentId)
            }
            context.emitContained(AgentEvents.Created, AgentNotice(createdAgent))
            context.emitContained(AgentEvents.SessionStart, AgentNotice(createdAgent))
            context.emitContained(AgentEvents.Status, AgentStatusNotice(createdAgent, createdAgent.status))
            currentCoroutineContext().ensureActive()
            return Handle(this, agentId, identity, createdAgent)
        } catch (error: Throwable) {
            rollback(creatingId, operationIdentity, publishedId, publishedIdentity, ownerContext, uninstalledHandle, error)
        } finally {
            permit.dispose()
        }
    }

    /** Attach a factory-created Agent to a previously loaded Session. */
    suspend fun restore(
        session: im.hikaru.harness.session.Session,
        options: AgentOptions = AgentOptions(
            cwd = session.header.cwd,
            preset = session.header.agentPreset,
        ),
    ): AgentHandle {
        val store = context.sessions
        val permit = store.admission.acquireCatalog(session)
        val id = AgentId(session.id.value)
        val operationIdentity = Any()
        var creatingId: AgentId? = null
        var ownerContext: Context? = null
        var uninstalledHandle: SessionHandle? = null
        var registeredIdentity: Any? = null
        try {
            val (factorySnapshot, factoryToken) =
                mutex.withLock {
                    checkActive()
                    val selected = factory ?: throw AgentException("No AgentFactory is registered", AgentErrorCode.NO_FACTORY)
                    if (id in entries || id in creating) {
                        throw AgentException("Agent '${id.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                    }
                    creating[id] = operationIdentity
                    creatingId = id
                    selected to factoryIdentity
                }
            val createdContext = context.child()
            ownerContext = createdContext
            val inbox =
                Inbox(
                    id,
                    session,
                    eventContext = context,
                    initial = Inbox.replay(session.events(), id),
                )
            currentCoroutineContext().ensureActive()
            // The loaded Session is already live in SessionStore. Transfer its
            // lifecycle ownership to the Agent child Context so disposing the
            // restored Agent cannot leave a live Session behind.
            // No suspension separates receiving the handle from installing its
            // effect. Keep it locally until installation succeeds as well.
            val sessionHandle = store.attach(session)
            uninstalledHandle = sessionHandle
            createdContext.effect(sessionHandle)
            uninstalledHandle = null
            val created =
                factorySnapshot.create(
                    AgentFactoryRequest(id, options, session, inbox, createdContext)
                )
            if (created.id != id || created.session !== session) {
                throw AgentException("AgentFactory returned an invalid Agent", AgentErrorCode.INVALID_FACTORY)
            }
            inbox.bindAgent(created)
            val identity = Any()
            mutex.withLock {
                checkActive()
                if (factoryIdentity !== factoryToken) {
                    throw AgentException("Agent factory was disposed during restore", AgentErrorCode.FACTORY_DISPOSED)
                }
                if (id in entries) {
                    throw AgentException("Agent '${id.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                }
                check(creating[id] === operationIdentity) { "Agent restore no longer owns its registration" }
                entries[id] = AgentEntry(identity, created, createdContext)
                registeredIdentity = identity
                creating.remove(id)
            }
            context.sequential(AgentEvents.Restore, AgentRestoreEvent(created))
            context.emitContained(AgentEvents.Created, AgentNotice(created))
            context.emitContained(AgentEvents.SessionStart, AgentNotice(created))
            context.emitContained(AgentEvents.Status, AgentStatusNotice(created, created.status))
            currentCoroutineContext().ensureActive()
            return Handle(this, id, identity, created)
        } catch (error: Throwable) {
            rollback(creatingId, operationIdentity, id, registeredIdentity, ownerContext, uninstalledHandle, error)
        } finally {
            permit.dispose()
        }
    }

    /**
     * Attaches a declared quiescent-restorable factory to a Session that was already loaded as a
     * quiescent read surface. It repairs, flushes and drains nothing, and it rejects a factory that
     * has not declared [QuiescentRestorableFactory], so an Agent able to start autonomous work is
     * never attached to an archived chat by accident.
     */
    suspend fun restoreQuiescent(
        session: im.hikaru.harness.session.Session,
        options: AgentOptions = AgentOptions(cwd = session.header.cwd, preset = session.header.agentPreset),
    ): AgentQuiescentRestore {
        val declared = mutex.withLock {
            checkActive()
            factory is QuiescentRestorableFactory
        }
        if (!declared) return AgentQuiescentRestore.Rejected("Agent factory 未声明支持静止恢复；不会挂接。")
        return try {
            AgentQuiescentRestore.Granted(restore(session, options))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: AgentException) {
            AgentQuiescentRestore.Rejected(error.message ?: "静止恢复失败。")
        }
    }

    private suspend fun rollback(
        creatingId: AgentId?,
        operationIdentity: Any,
        publishedId: AgentId?,
        publishedIdentity: Any?,
        ownerContext: Context?,
        uninstalledHandle: SessionHandle?,
        error: Throwable,
    ): Nothing {
        withContext(NonCancellable) {
            try {
                mutex.withLock {
                    if (creatingId != null && creating[creatingId] === operationIdentity) creating.remove(creatingId)
                    if (publishedId != null && publishedIdentity != null && entries[publishedId]?.identity === publishedIdentity) {
                        entries.remove(publishedId)
                    }
                }
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            try {
                uninstalledHandle?.dispose()
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            try {
                ownerContext?.dispose()
            } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
        }
        throw error
    }

    /**
     * Exclusive archive preparation for one exact live Agent.
     *
     * Returns Busy instead of queueing when the Agent's Session has in-flight activity, an active
     * reservation or catalog activity. When granted it closes new operation permits for that
     * Session, so no new durable event can start while the caller commits the durable archive
     * record. Nothing is cancelled, cleared or stopped until [AgentArchivePreparation.commit] runs.
     */
    suspend fun beginArchive(id: AgentId): AgentArchiveBegin {
        val store = context.sessions
        val entry = mutex.withLock {
            checkActive()
            entries[id]
        } ?: return AgentArchiveBegin.NotRegistered
        return when (val acquired = store.admission.acquireArchive(entry.agent.session)) {
            is SessionArchiveAcquire.Granted -> AgentArchiveBegin.Granted(
                AgentArchivePreparation(acquired.token) { quiesceArchivedAgent(id, entry.identity, entry.agent) }
            )
            is SessionArchiveAcquire.Busy -> AgentArchiveBegin.Busy(acquired.reason)
            is SessionArchiveAcquire.Rejected -> AgentArchiveBegin.Rejected(acquired.reason.name)
        }
    }

    suspend fun get(id: AgentId): Agent? =
        mutex.withLock {
            checkActive()
            entries[id]?.agent
        }

    suspend fun list(): List<Agent> =
        mutex.withLock {
            checkActive()
            entries.values.map { it.agent }
        }

    override suspend fun dispose() {
        val pending =
            mutex.withLock {
                if (disposed) return
                disposed = true
                val values = entries.values.toList()
                entries.clear()
                creating.clear()
                factory = null
                factoryIdentity = null
                values
            }
        var failure: Throwable? = null
        for (entry in pending.asReversed()) {
            try {
                entry.agent.cancel(AgentException("Agent registry disposed", AgentErrorCode.REGISTRY_DISPOSED))
            } catch (error: Throwable) {
                failure = failure ?: error
            }
            try {
                entry.ownerContext.dispose()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
            emitDisposed(entry.agent)
        }
        failure?.let { throw it }
    }

    private suspend fun unregisterFactory(identity: Any) {
        mutex.withLock {
            if (factoryIdentity === identity) {
                factory = null
                factoryIdentity = null
            }
        }
    }

    private suspend fun detach(
        id: AgentId,
        identity: Any,
        agent: Agent,
    ) {
        // An old handle must not resolve a closing Context or enter admission.
        if (!mutex.withLock { entries[id]?.identity === identity }) return
        val store = context.sessions
        val permit = store.admission.acquireCatalog()
        try {
            val removed =
                mutex.withLock {
                    if (entries[id]?.identity !== identity) return@withLock null
                    entries.remove(id)
                } ?: return
            // Once removed, cancellation must not strand the Context or skip
            // notices. No Registry lock surrounds any of this owner work.
            val failure = withContext(NonCancellable) {
                var failure: Throwable? = null
                try {
                    removed.agent.cancel(AgentException("Agent handle disposed", AgentErrorCode.AGENT_DISPOSED))
                } catch (error: Throwable) {
                    failure = error
                }
                try {
                    removed.ownerContext.dispose()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
                }
                try {
                    emitDisposed(agent)
                } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
                }
                failure
            }
            failure?.let { throw it }
        } finally {
            permit.dispose()
        }
    }

    /**
     * Best-effort quiesce for a committed archive of one exact Agent.
     *
     * Disposing the owner Context stops the Agent loop and detaches its Session; the registry
     * record is dropped and the disposal notice is emitted. Unlike [detach] this never calls
     * [Agent.cancel], because the archive gate already closed the Session's operation permits and
     * a cancel would only fail on that closed gate. The Session detach permit stays catalog-level,
     * which the gate deliberately leaves open so the owner can still stop the Agent.
     */
    private suspend fun quiesceArchivedAgent(
        id: AgentId,
        identity: Any,
        agent: Agent,
    ) {
        if (!mutex.withLock { entries[id]?.identity === identity }) return
        val store = context.sessions
        val permit = store.admission.acquireCatalog()
        try {
            val removed =
                mutex.withLock {
                    if (entries[id]?.identity !== identity) return@withLock null
                    entries.remove(id)
                } ?: return
            val failure = withContext(NonCancellable) {
                var failure: Throwable? = null
                try {
                    removed.ownerContext.dispose()
                } catch (error: Throwable) {
                    failure = error
                }
                try {
                    emitDisposed(agent)
                } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
                }
                failure
            }
            failure?.let { throw it }
        } finally {
            permit.dispose()
        }
    }

    /** Parent Context marks itself disposing before plugin effects stop. */
    private fun emitDisposed(agent: Agent) {
        try {
            context.emitContained(AgentEvents.Disposed, AgentNotice(agent))
        } catch (_: IllegalStateException) {
            // Disposal notifications are best effort once the parent Context is closing.
        }
    }

    private fun checkActive() {
        check(!disposed) { "AgentRegistry is disposed" }
    }

    private class FactoryHandle(
        private val registry: AgentRegistry,
        private val identity: Any,
    ) : AgentFactoryHandle {
        private var disposed = false

        override suspend fun dispose() {
            if (disposed) return
            disposed = true
            registry.unregisterFactory(identity)
        }
    }

    private class Handle(
        private val registry: AgentRegistry,
        private val id: AgentId,
        private val identity: Any,
        override val agent: Agent,
    ) : AgentHandle {
        private var disposed = false

        override suspend fun dispose() {
            if (disposed) return
            registry.detach(id, identity, agent)
            disposed = true
        }
    }
}

/** Result of beginning an exclusive Agent archive. It is not a deletion or absence proof. */
sealed interface AgentArchiveBegin {
    data class Granted(val preparation: AgentArchivePreparation) : AgentArchiveBegin

    data object NotRegistered : AgentArchiveBegin

    data class Busy(val reason: SessionReservationBusyReason) : AgentArchiveBegin

    data class Rejected(val reason: String) : AgentArchiveBegin
}

/** Result of attaching a declared quiescent-restorable factory to an already loaded Session. */
sealed interface AgentQuiescentRestore {
    data class Granted(val handle: AgentHandle) : AgentQuiescentRestore

    data class Rejected(val reason: String) : AgentQuiescentRestore
}

/**
 * Exclusive archive preparation for one exact live Agent.
 *
 * Disposing without [commit] only releases the ownership; it never cancels or stops the Agent.
 * [commit] is the best-effort quiesce step and is reported separately from the durable commit.
 */
class AgentArchivePreparation internal constructor(
    private val token: SessionArchiveToken,
    private val commitAction: suspend () -> Unit,
) : Disposable {
    /** Stops the Agent, detaches its Session and drops its registry record; warns on failure. */
    suspend fun commit(): String? =
        try {
            commitAction()
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            "已提交归档，但关联 Agent/会话未能完全停止；请刷新核验。"
        }

    override suspend fun dispose() {
        token.dispose()
    }
}

enum class AgentErrorCode {
    MODEL_NOT_CONFIGURED,
    NO_FACTORY,
    DUPLICATE_AGENT,
    INVALID_FACTORY,
    FACTORY_DISPOSED,
    REGISTRY_DISPOSED,
    AGENT_DISPOSED,
}

class AgentException(
    message: String,
    val code: AgentErrorCode,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

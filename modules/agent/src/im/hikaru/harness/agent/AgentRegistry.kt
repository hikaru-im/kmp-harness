package im.hikaru.harness.agent

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.createSession
import im.hikaru.harness.session.sessions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val creating = mutableSetOf<AgentId>()
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
        val (factorySnapshot, factoryToken, reservedId) =
            mutex.withLock {
                checkActive()
                val selected =
                    factory
                        ?: throw AgentException("No AgentFactory is registered", AgentErrorCode.NO_FACTORY)
                val token = factoryIdentity
                if (id != null) {
                    if (id in entries || id in creating) {
                        throw AgentException("Agent '${id.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                    }
                    creating += id
                }
                Triple(selected, token, id)
            }

        var ownerContext: Context? = null
        var session: im.hikaru.harness.session.Session? = null
        var agent: Agent? = null
        try {
            ownerContext = context.child()
            session =
                ownerContext.createSession(
                    id = reservedId?.let { im.hikaru.harness.session.SessionId(it.value) },
                    options = sessionOptions.copy(
                        cwd = sessionOptions.cwd ?: options.cwd,
                        agentPreset = sessionOptions.agentPreset ?: options.preset,
                    ),
                )
            val createdContext = checkNotNull(ownerContext)
            val createdSession = checkNotNull(session)
            val agentId = AgentId(createdSession.id.value)
            if (reservedId != null && agentId != reservedId) {
                throw AgentException("Agent factory returned a mismatched id", AgentErrorCode.INVALID_FACTORY)
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
            agent = createdAgent
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
                entries[agentId] = AgentEntry(identity, createdAgent, createdContext)
                if (reservedId != null) creating.remove(reservedId)
            }
            context.emitContained(AgentEvents.Created, AgentNotice(createdAgent))
            context.emitContained(AgentEvents.SessionStart, AgentNotice(createdAgent))
            context.emitContained(AgentEvents.Status, AgentStatusNotice(createdAgent, createdAgent.status))
            return Handle(this, agentId, identity, createdAgent)
        } catch (error: Throwable) {
            if (reservedId != null) {
                mutex.withLock { creating.remove(reservedId) }
            }
            try {
                ownerContext?.dispose()
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
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
        val (factorySnapshot, factoryToken) =
            mutex.withLock {
                checkActive()
                val selected = factory ?: throw AgentException("No AgentFactory is registered", AgentErrorCode.NO_FACTORY)
                val id = AgentId(session.id.value)
                if (id in entries || id in creating) {
                    throw AgentException("Agent '${id.value}' already exists", AgentErrorCode.DUPLICATE_AGENT)
                }
                creating += id
                selected to factoryIdentity
            }
        val ownerContext = context.child()
        val id = AgentId(session.id.value)
        val inbox =
            Inbox(
                id,
                session,
                eventContext = context,
                initial = Inbox.replay(session.events(), id),
            )
        var registeredIdentity: Any? = null
        try {
            // The loaded Session is already live in SessionStore. Transfer its
            // lifecycle ownership to the Agent child Context so disposing the
            // restored Agent cannot leave a live Session behind.
            ownerContext.effect(context.sessions.attach(session))
            val created =
                factorySnapshot.create(
                    AgentFactoryRequest(id, options, session, inbox, ownerContext)
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
                entries[id] = AgentEntry(identity, created, ownerContext)
                registeredIdentity = identity
                creating.remove(id)
            }
            context.sequential(AgentEvents.Restore, AgentRestoreEvent(created))
            context.emitContained(AgentEvents.Created, AgentNotice(created))
            context.emitContained(AgentEvents.SessionStart, AgentNotice(created))
            context.emitContained(AgentEvents.Status, AgentStatusNotice(created, created.status))
            return Handle(this, id, identity, created)
        } catch (error: Throwable) {
            mutex.withLock {
                creating.remove(id)
                val identity = registeredIdentity
                if (identity != null && entries[id]?.identity === identity) {
                    entries.remove(id)
                }
            }
            ownerContext.dispose()
            throw error
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
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
            context.emitContained(AgentEvents.Disposed, AgentNotice(entry.agent))
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
        val removed =
            mutex.withLock {
                val entry = entries[id]
                if (entry == null || entry.identity !== identity) return@withLock null
                entries.remove(id)
            }
                ?: return
        var failure: Throwable? = null
        try {
            removed.agent.cancel(AgentException("Agent handle disposed", AgentErrorCode.AGENT_DISPOSED))
        } catch (error: Throwable) {
            failure = error
        }
        try {
            removed.ownerContext.dispose()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure?.addSuppressed(error)
        }
        context.emitContained(AgentEvents.Disposed, AgentNotice(agent))
        failure?.let { throw it }
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
            disposed = true
            registry.detach(id, identity, agent)
        }
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

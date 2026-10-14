package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.AppSession
import im.hikaru.harness.client.account.MemberIdentity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Observable lifecycle of the authenticated remote client connection. */
public enum class RemoteConnectionStatus {
    IDLE,
    DISCOVERING,
    CONNECTING,
    ONLINE,
    RECONNECTING,
    DISCONNECTED,
}

public data class RemoteDiscoveryState(val refreshing: Boolean = false, val error: String? = null)

/**
 * Owns the remote client connection for one authenticated account scope.
 *
 * This is the only place that reconnects: it re-discovers the Host, re-handshakes, rebuilds the
 * subscriptions of the current identity scope and recovers history with bounded backoff. It never
 * re-sends an execution command such as session.create or session.prompt, because their result is
 * uncertain after a drop; a timeout surfaces as unknown and the caller refreshes history instead.
 */
public class RemoteConnectionOwner(
    private val client: HttpClient,
    private val closeClient: () -> Unit,
    private val scope: CoroutineScope,
    private val policy: ReconnectPolicy = ReconnectPolicy(),
) : AutoCloseable {
    private val mutex = Mutex()
    private val mutableConnection = MutableStateFlow<RemoteConnection?>(null)
    private val mutableHosts = MutableStateFlow<List<HostDescription>>(emptyList())
    private val mutableStatus = MutableStateFlow(RemoteConnectionStatus.IDLE)
    private val mutableDiscovery = MutableStateFlow(RemoteDiscoveryState())
    private val mutableIdentity = MutableStateFlow<MemberIdentity?>(null)
    private val mutableSelectedHost = MutableStateFlow<HostDescription?>(null)
    private val discoveryMutex = Mutex()
    private val refreshMutex = Mutex()
    private val subscriptions = linkedSetOf<StreamId>()
    private var session: AppSession? = null
    private var clientId: String? = null
    private var desiredHostId: String? = null
    private var supervisor: Job? = null
    private var closed = false
    private var generation = 0L

    public val connection: StateFlow<RemoteConnection?> = mutableConnection.asStateFlow()
    public val hosts: StateFlow<List<HostDescription>> = mutableHosts.asStateFlow()
    public val status: StateFlow<RemoteConnectionStatus> = mutableStatus.asStateFlow()
    public val discovery: StateFlow<RemoteDiscoveryState> = mutableDiscovery.asStateFlow()
    public val identity: StateFlow<MemberIdentity?> = mutableIdentity.asStateFlow()
    /** Selected target survives transport reconnects; stop/logout clears its ownership. */
    public val selectedHost: StateFlow<HostDescription?> = mutableSelectedHost.asStateFlow()

    /**
     * Events of the currently connected Host, already narrowed by the Host and the Relay to the streams
     * this client subscribed to. Empty while no connection is online.
     */
    public val sessionEvents: Flow<RelayEvent> =
        flow {
            mutableConnection.collectLatest { connection ->
                connection?.events?.collect { emit(it) }
            }
        }

    /** History snapshots pulled after a detected sequence gap, so the shell can reconcile its view. */
    public val historyRecoveries: Flow<HistoryRecovery> =
        flow {
            mutableConnection.collectLatest { connection ->
                connection?.historyRecoveries?.collect { emit(it) }
            }
        }

    /** Streams rebuilt after a reconnect. Returned as a snapshot so callers cannot mutate the scope. */
    public suspend fun subscribedStreams(): Set<StreamId> = mutex.withLock { subscriptions.toSet() }

    public suspend fun discover(session: AppSession): List<HostDescription> {
        val version = mutex.withLock {
            if (closed || this.session != session) throw CancellationException("Account scope changed")
            generation
        }
        return discoveryMutex.withLock {
            mutex.withLock {
                if (closed || generation != version || this.session != session) throw CancellationException("Account scope changed")
                mutableDiscovery.value = RemoteDiscoveryState(refreshing = true)
            }
            try {
                val response = client.get {
                    url(session.identity.target.baseUrl.trimEnd('/') + "/app-api/harness/hosts")
                    header(HttpHeaders.Authorization, "Bearer " + session.accessToken)
                    header("tenant-id", session.identity.target.tenantId)
                }.body<ApiResult<List<HostDescription>>>()
                currentCoroutineContext().ensureActive()
                if (!response.isSuccess) throw RemoteConnectionException("Host discovery failed", response.code)
                val result = response.data.orEmpty()
                mutex.withLock {
                    if (closed || generation != version || this.session != session) throw CancellationException("Account scope changed")
                    mutableHosts.value = result
                    mutableDiscovery.value = RemoteDiscoveryState()
                }
                result
            } catch (cancelled: CancellationException) {
                mutex.withLock { if (generation == version && !closed) mutableDiscovery.value = RemoteDiscoveryState() }
                throw cancelled
            } catch (failure: Exception) {
                mutex.withLock {
                    if (generation == version && !closed) mutableDiscovery.value = RemoteDiscoveryState(error = "无法发现远程 Host，请重试。")
                }
                throw failure
            }
        }
    }

    /** Coalesces repeated page actions and never changes the active transport state. */
    public suspend fun refresh(expectedSession: AppSession? = null) {
        if (!refreshMutex.tryLock()) return
        try {
            val identity = mutex.withLock {
                if (closed || mutableDiscovery.value.refreshing || (expectedSession != null && session != expectedSession)) return
                session ?: return
            }
            discover(identity)
        } finally {
            refreshMutex.unlock()
        }
    }

    /**
     * Starts the supervised connection loop for [session]. Call [stop] before switching backend, tenant
     * or account so the old identity scope cannot observe the new one.
     */
    public suspend fun start(session: AppSession, clientId: String) {
        stop()
        mutex.withLock {
            check(!closed) { "Remote connection owner is closed" }
            this.session = session
            mutableIdentity.value = session.identity
            this.clientId = clientId
            val version = generation
            supervisor = scope.launch { supervise(version) }
        }
    }

    /** Stops the loop, closes the connection and forgets the old identity scope. */
    public suspend fun stop() {
        val previous = mutex.withLock {
            generation += 1
            val previous = supervisor
            supervisor = null
            mutableConnection.value?.close()
            mutableConnection.value = null
            mutableHosts.value = emptyList()
            subscriptions.clear()
            session = null
            mutableIdentity.value = null
            clientId = null
            desiredHostId = null
            mutableSelectedHost.value = null
            mutableDiscovery.value = RemoteDiscoveryState()
            if (!closed) mutableStatus.value = RemoteConnectionStatus.IDLE
            previous
        }
        previous?.cancelAndJoin()
    }

    /**
     * Pins the supervised loop to [host]; the current connection is dropped so the loop reconnects.
     *
     * Switching Host also drops this client's subscriptions, so the previous Host's registration is not
     * carried over to the new one: [connectTo] rebuilds only what this identity scope still owns, and
     * the shell re-registers its current selection once the new connection is online.
     */
    public suspend fun select(host: HostDescription, expectedSession: AppSession? = null) {
        val (previous, version) = mutex.withLock {
            if (closed || (expectedSession != null && session != expectedSession)) return
            if (host.protocolVersion.major != ProtocolVersion.Current.major) return
            if (session != null && mutableHosts.value.none { it == host }) return
            if (mutableConnection.value != null && desiredHostId == host.hostId.value) return
            desiredHostId = host.hostId.value
            mutableSelectedHost.value = host
            subscriptions.clear()
            generation += 1
            val previous = supervisor
            supervisor = null
            mutableConnection.value?.close()
            mutableConnection.value = null
            mutableDiscovery.value = RemoteDiscoveryState()
            if (session != null) mutableStatus.value = RemoteConnectionStatus.CONNECTING
            previous to generation
        }
        previous?.cancelAndJoin()
        mutex.withLock {
            if (!closed && version == generation && session != null) supervisor = scope.launch { supervise(version) }
        }
    }

    public suspend fun subscribe(streamId: StreamId) {
        val connection = mutex.withLock {
            subscriptions.add(streamId)
            mutableConnection.value
        }
        connection?.let { current ->
            try {
                current.subscribe(streamId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The intent is already recorded, so the next connection rebuilds it.
            }
        }
    }

    public suspend fun unsubscribe(streamId: StreamId) {
        val connection = mutex.withLock {
            subscriptions.remove(streamId)
            mutableConnection.value
        }
        connection?.let { current ->
            try {
                current.unsubscribe(streamId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Already dropped locally; the connection is closing or gone.
            }
        }
    }

    private suspend fun supervise(version: Long) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val identity = mutex.withLock { if (generation != version || closed) return; session to clientId }
            val activeSession = identity.first ?: return
            val activeClientId = identity.second ?: return
            try {
                mutex.withLock {
                    if (generation != version || closed) return
                    mutableStatus.value = RemoteConnectionStatus.DISCOVERING
                }
                val discovered = discover(activeSession)
                val preferred = mutex.withLock { desiredHostId }
                val host = if (preferred != null) {
                    discovered.firstOrNull { it.hostId.value == preferred }
                        ?: throw RemoteConnectionException("Selected Host is unavailable")
                } else {
                    discovered.firstOrNull { it.protocolVersion.major == ProtocolVersion.Current.major }
                        ?: throw RemoteConnectionException("No compatible Host for this identity")
                }
                if (host.protocolVersion.major != ProtocolVersion.Current.major)
                    throw RemoteConnectionException("Host protocol is incompatible")
                connectTo(activeSession, host, activeClientId, version)
                attempt = 0
                val active = mutex.withLock { mutableConnection.value } ?: continue
                active.awaitClosed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Fall through to a bounded retry; the failure stays observable through status.
            }
            if (!currentCoroutineContext().isActive) return
            mutex.withLock {
                if (closed || generation != version) return
                mutableConnection.value?.close()
                mutableConnection.value = null
            }
            val delayMillis =
                policy.nextDelay(attempt) ?: run {
                    mutex.withLock {
                        if (closed || generation != version) return
                        mutableConnection.value?.close()
                        mutableConnection.value = null
                        mutableStatus.value = RemoteConnectionStatus.DISCONNECTED
                        if (mutableDiscovery.value.error == null) mutableDiscovery.value = RemoteDiscoveryState(error = "无法连接远程 Host，请确认 Host 在线后重试。")
                    }
                    return
                }
            attempt += 1
            mutex.withLock {
                if (closed || generation != version) return
                mutableStatus.value = RemoteConnectionStatus.RECONNECTING
            }
            delay(delayMillis)
        }
    }

    private suspend fun connectTo(session: AppSession, host: HostDescription, clientId: String, version: Long) {
        mutex.withLock {
            if (closed || generation != version || this.session != session) return
            desiredHostId = host.hostId.value
            mutableSelectedHost.value = host
            mutableStatus.value = RemoteConnectionStatus.CONNECTING
        }
        val created = RemoteConnection.connect(
            client = client,
            baseUrl = session.identity.target.baseUrl,
            accessToken = session.accessToken,
            tenantId = session.identity.target.tenantId,
            host = host,
            clientId = clientId,
            scope = scope,
        )
        val streams = mutex.withLock {
            if (closed || generation != version || this.session != session) {
                created.close()
                return
            }
            mutableConnection.value?.close()
            mutableConnection.value = created
            mutableStatus.value = RemoteConnectionStatus.ONLINE
            subscriptions.toSet()
        }
        // Rebuild only subscription ownership: no execution command is replayed on a new connection.
        streams.forEach { stream ->
            try {
                created.subscribe(stream)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // A later reconnect retries the subscription.
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        generation += 1
        val previous = supervisor
        supervisor = null
        previous?.cancel()
        mutableConnection.value?.close()
        mutableConnection.value = null
        mutableHosts.value = emptyList()
        mutableStatus.value = RemoteConnectionStatus.IDLE
        mutableDiscovery.value = RemoteDiscoveryState()
        mutableIdentity.value = null
        mutableSelectedHost.value = null
        subscriptions.clear()
        closeClient()
        scope.coroutineContext[Job]?.cancel()
    }
}

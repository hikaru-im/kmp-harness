package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.AppSession
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
    private val subscriptions = linkedSetOf<StreamId>()
    private var session: AppSession? = null
    private var clientId: String? = null
    private var desiredHostId: String? = null
    private var supervisor: Job? = null
    private var closed = false

    public val connection: StateFlow<RemoteConnection?> = mutableConnection.asStateFlow()
    public val hosts: StateFlow<List<HostDescription>> = mutableHosts.asStateFlow()
    public val status: StateFlow<RemoteConnectionStatus> = mutableStatus.asStateFlow()

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
        val response = client.get {
            url(session.identity.target.baseUrl.trimEnd('/') + "/app-api/harness/hosts")
            header(HttpHeaders.Authorization, "Bearer " + session.accessToken)
            header("tenant-id", session.identity.target.tenantId)
        }.body<ApiResult<List<HostDescription>>>()
        if (!response.isSuccess) throw RemoteConnectionException(response.msg ?: "Host discovery failed", response.code)
        val result = response.data.orEmpty()
        mutex.withLock { if (!closed) mutableHosts.value = result }
        return result
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
            this.clientId = clientId
            supervisor = scope.launch { supervise() }
        }
    }

    /** Stops the loop, closes the connection and forgets the old identity scope. */
    public suspend fun stop() {
        val previous = mutex.withLock { supervisor.also { supervisor = null } }
        previous?.cancelAndJoin()
        mutex.withLock {
            mutableConnection.value?.close()
            mutableConnection.value = null
            mutableHosts.value = emptyList()
            subscriptions.clear()
            session = null
            clientId = null
            desiredHostId = null
            if (!closed) mutableStatus.value = RemoteConnectionStatus.IDLE
        }
    }

    /** Pins the supervised loop to [host]; the current connection is dropped so the loop reconnects. */
    public suspend fun select(host: HostDescription) {
        mutex.withLock { desiredHostId = host.hostId.value }
        mutex.withLock { mutableConnection.value?.close() }
    }

    public suspend fun subscribe(streamId: StreamId) {
        val connection = mutex.withLock {
            subscriptions.add(streamId)
            mutableConnection.value
        }
        connection?.subscribe(streamId)
    }

    public suspend fun unsubscribe(streamId: StreamId) {
        val connection = mutex.withLock {
            subscriptions.remove(streamId)
            mutableConnection.value
        }
        connection?.unsubscribe(streamId)
    }

    private suspend fun supervise() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val identity = mutex.withLock { session to clientId }
            val activeSession = identity.first ?: return
            val activeClientId = identity.second ?: return
            try {
                mutex.withLock { mutableStatus.value = RemoteConnectionStatus.DISCOVERING }
                val discovered = discover(activeSession)
                val preferred = mutex.withLock { desiredHostId }
                val host =
                    (preferred?.let { id -> discovered.firstOrNull { it.hostId.value == id } })
                        ?: discovered.firstOrNull()
                        ?: throw RemoteConnectionException("No online Host for this identity")
                connectTo(activeSession, host, activeClientId)
                attempt = 0
                val active = mutex.withLock { mutableConnection.value } ?: continue
                active.awaitClosed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Fall through to a bounded retry; the failure stays observable through status.
            }
            if (!currentCoroutineContext().isActive) return
            val delayMillis =
                policy.nextDelay(attempt) ?: run {
                    mutex.withLock {
                        mutableConnection.value?.close()
                        mutableConnection.value = null
                        mutableStatus.value = RemoteConnectionStatus.DISCONNECTED
                    }
                    return
                }
            attempt += 1
            mutex.withLock { mutableStatus.value = RemoteConnectionStatus.RECONNECTING }
            delay(delayMillis)
        }
    }

    private suspend fun connectTo(session: AppSession, host: HostDescription, clientId: String) {
        mutex.withLock { mutableStatus.value = RemoteConnectionStatus.CONNECTING }
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
            if (closed) {
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
        val previous = supervisor
        supervisor = null
        previous?.cancel()
        mutableConnection.value?.close()
        mutableConnection.value = null
        mutableHosts.value = emptyList()
        mutableStatus.value = RemoteConnectionStatus.IDLE
        subscriptions.clear()
        closeClient()
        scope.coroutineContext[Job]?.cancel()
    }
}
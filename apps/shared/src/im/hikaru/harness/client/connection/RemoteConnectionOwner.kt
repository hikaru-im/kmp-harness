package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.client.account.AppSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the remote client connection for one authenticated account scope. */
public class RemoteConnectionOwner(
    private val client: HttpClient,
    private val closeClient: () -> Unit,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val mutex = Mutex()
    private val mutableConnection = MutableStateFlow<RemoteConnection?>(null)
    private val mutableHosts = MutableStateFlow<List<HostDescription>>(emptyList())
    private var closed = false

    public val connection: StateFlow<RemoteConnection?> = mutableConnection.asStateFlow()
    public val hosts: StateFlow<List<HostDescription>> = mutableHosts.asStateFlow()

    public suspend fun discover(session: AppSession): List<HostDescription> {
        val response = client.get {
            url(session.identity.target.baseUrl.trimEnd('/') + "/app-api/harness/hosts")
            header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
            header("tenant-id", session.identity.target.tenantId)
        }.body<ApiResult<List<HostDescription>>>()
        if (!response.isSuccess) throw RemoteConnectionException(response.msg ?: "Host discovery failed", response.code)
        val result = response.data.orEmpty()
        mutex.withLock { if (!closed) mutableHosts.value = result }
        return result
    }

    public suspend fun connect(session: AppSession, host: HostDescription, clientId: String) {
        val created = RemoteConnection.connect(
            client = client,
            baseUrl = session.identity.target.baseUrl,
            accessToken = session.accessToken,
            tenantId = session.identity.target.tenantId,
            host = host,
            clientId = clientId,
            scope = scope,
        )
        mutex.withLock {
            if (closed) {
                created.close()
                return
            }
            mutableConnection.value?.close()
            mutableConnection.value = created
        }
    }

    public suspend fun disconnect() = mutex.withLock {
        mutableConnection.value?.close()
        mutableConnection.value = null
    }

    override fun close() {
        mutableConnection.value?.close()
        mutableConnection.value = null
        mutableHosts.value = emptyList()
        closeClient()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        closed = true
    }
}

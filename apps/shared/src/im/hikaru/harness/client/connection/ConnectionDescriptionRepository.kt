package im.hikaru.harness.client.connection

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

public data class ConnectionDescriptionState(
    val host: HostDescription? = null,
    val phase: ConnectionsPhase = ConnectionsPhase.Connecting,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** Owned by one Connection instance. Replacing the instance also replaces its observed state. */
public class ConnectionDescriptionRepository(private val connection: Connection) {
    private val operation = Mutex()
    private val mutableState = MutableStateFlow(ConnectionDescriptionState())
    public val state: StateFlow<ConnectionDescriptionState> = mutableState.asStateFlow()

    public suspend fun refresh() {
        if (!operation.tryLock()) return
        val previous = mutableState.value
        mutableState.value = previous.copy(refreshing = true, error = null)
        try {
            val response = connection.host.describe()
            currentCoroutineContext().ensureActive()
            val host = response.data
            if (!response.isSuccess || host == null) {
                mutableState.value = ConnectionDescriptionState(phase = ConnectionsPhase.Disconnected, error = "无法读取 Host 信息，请重试。")
            } else {
                val compatible = host.protocolVersion.major == ProtocolVersion.Current.major
                mutableState.value = ConnectionDescriptionState(host = host,
                    phase = if (compatible) ConnectionsPhase.Online else ConnectionsPhase.Incompatible,
                    error = if (compatible) null else "Host 协议与当前客户端不兼容。")
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = previous
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = ConnectionDescriptionState(phase = ConnectionsPhase.Disconnected, error = "无法读取 Host 信息，请重试。")
        } finally {
            operation.unlock()
        }
    }
}

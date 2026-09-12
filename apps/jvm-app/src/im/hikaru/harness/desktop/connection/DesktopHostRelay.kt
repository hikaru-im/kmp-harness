package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.client.account.AppSession
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.connection.ReconnectPolicy
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Observable state of the Desktop Host's outbound RuoYi Relay connection. */
public enum class DesktopRelayStatus {
    IDLE,
    CONNECTING,
    ONLINE,
    RECONNECTING,
    DISCONNECTED,
}

/**
 * Desktop 主动连接已配置 RuoYi 后端的 Host 侧入口。
 *
 * Desktop 仍然通过 LocalConnection 拥有 Agent 执行权；本类型只把已登录身份的 Host 注册到 Relay，
 * 转发本机 Session 事件，并在失败后按有限退避重新注册。身份、后端或租户变化时先关闭旧连接，再按
 * 新作用域连接，旧作用域的注册不会被复用。
 */
public class DesktopHostRelay(
    private val client: HttpClient,
    private val closeClient: () -> Unit,
    private val gateway: ApiGateway,
    private val description: HostDescription,
    private val events: Flow<RelayEvent>,
    private val scope: CoroutineScope,
    private val policy: ReconnectPolicy = ReconnectPolicy(),
) : AutoCloseable {
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow(DesktopRelayStatus.IDLE)
    private var connection: HostRelayConnection? = null
    private var loop: Job? = null
    private var closed = false

    public val status: StateFlow<DesktopRelayStatus> = mutableStatus.asStateFlow()

    /** Follows the authenticated account scope and keeps the Host registered while it is logged in. */
    public fun start(account: MemberAccount) {
        if (closed) return
        loop =
            scope.launch {
                account.state.collectLatest { state ->
                    val session = state.session
                    if (session == null || state.target == null) {
                        disconnect(DesktopRelayStatus.IDLE)
                    } else {
                        supervise(session)
                    }
                }
            }
    }

    private suspend fun supervise(session: AppSession) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                mutex.withLock { mutableStatus.value = DesktopRelayStatus.CONNECTING }
                val created =
                    HostRelayConnection.connect(
                        client = client,
                        baseUrl = session.identity.target.baseUrl,
                        token = session.accessToken,
                        tenantId = session.identity.target.tenantId,
                        host = description,
                        gateway = gateway,
                        events = events,
                        scope = scope,
                    )
                mutex.withLock {
                    connection?.close()
                    connection = created
                    mutableStatus.value = DesktopRelayStatus.ONLINE
                }
                attempt = 0
                created.awaitClosed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Fall through to the bounded retry below; status stays observable.
            }
            if (!currentCoroutineContext().isActive) return
            val delayMillis =
                policy.nextDelay(attempt) ?: run {
                    mutex.withLock {
                        connection?.close()
                        connection = null
                        mutableStatus.value = DesktopRelayStatus.DISCONNECTED
                    }
                    return
                }
            attempt += 1
            mutex.withLock { mutableStatus.value = DesktopRelayStatus.RECONNECTING }
            delay(delayMillis)
        }
    }

    private suspend fun disconnect(status: DesktopRelayStatus) {
        mutex.withLock {
            connection?.close()
            connection = null
            if (!closed) mutableStatus.value = status
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        val running = loop
        loop = null
        running?.cancel()
        connection?.close()
        connection = null
        mutableStatus.value = DesktopRelayStatus.IDLE
        closeClient()
        scope.coroutineContext[Job]?.cancel()
    }
}
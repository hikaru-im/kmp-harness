package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/** 单实例后端使用的内存 Host 注册表。 */
@Component
class DefaultHostConnectionRegistry : HostConnectionRegistry {
    private val lock = Any()
    private val generationSequence = AtomicLong()
    private val connectionsByHost = HashMap<HostKey, HostConnection>()
    private val connectionsBySession = HashMap<String, HostConnection>()

    override fun register(
        principal: HarnessPrincipal,
        sessionId: String,
        description: HostDescription,
    ): HostRegistration = synchronized(lock) {
        require(sessionId.isNotBlank()) { "WebSocket session id must not be blank" }
        val key = HostKey(principal, description.hostId)
        val currentForSession = connectionsBySession[sessionId]

        if (currentForSession != null && currentForSession.toKey() == key) {
            val refreshed = currentForSession.copy(description = description)
            connectionsByHost[key] = refreshed
            connectionsBySession[sessionId] = refreshed
            return@synchronized HostRegistration(refreshed, emptyList())
        }

        val displaced = LinkedHashSet<HostConnection>()
        currentForSession?.let { connection ->
            remove(connection)
            displaced += connection
        }
        connectionsByHost[key]?.let { connection ->
            remove(connection)
            displaced += connection
        }

        val connection =
            HostConnection(
                principal = principal,
                sessionId = sessionId,
                generation = generationSequence.incrementAndGet(),
                description = description,
            )
        connectionsByHost[key] = connection
        connectionsBySession[sessionId] = connection
        HostRegistration(connection, displaced.toList())
    }

    override fun find(principal: HarnessPrincipal, hostId: HostId): HostConnection? =
        synchronized(lock) {
            connectionsByHost[HostKey(principal, hostId)]
        }

    override fun findBySession(sessionId: String): HostConnection? =
        synchronized(lock) {
            connectionsBySession[sessionId]
        }

    override fun unregister(sessionId: String): HostConnection? =
        synchronized(lock) {
            val connection = connectionsBySession[sessionId] ?: return@synchronized null
            remove(connection)
            connection
        }

    private fun remove(connection: HostConnection) {
        val key = connection.toKey()
        if (connectionsByHost[key] == connection) {
            connectionsByHost.remove(key)
        }
        if (connectionsBySession[connection.sessionId] == connection) {
            connectionsBySession.remove(connection.sessionId)
        }
    }

    private fun HostConnection.toKey(): HostKey = HostKey(principal, description.hostId)

    private data class HostKey(
        val principal: HarnessPrincipal,
        val hostId: HostId,
    )
}

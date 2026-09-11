package im.hikaru.ruoyi.module.harness.relay

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.harness.relay.RequestId
import org.springframework.stereotype.Component

/** 单实例后端使用的内存 pending request 注册表。 */
@Component
class DefaultPendingRelayRegistry : PendingRelayRegistry {
    private val lock = Any()
    private val pending = HashMap<RequestKey, PendingRelayRequest>()

    override fun register(request: PendingRelayRequest): Boolean = synchronized(lock) {
        val key = request.toKey()
        if (pending.containsKey(key)) {
            return@synchronized false
        }
        pending[key] = request
        true
    }

    override fun complete(
        connection: HostConnection,
        response: RelayResponse,
    ): PendingRelayRequest? = synchronized(lock) {
        val key = RequestKey(connection.principal, response.hostId, response.requestId)
        val request = pending[key] ?: return@synchronized null
        if (request.hostSessionId != connection.sessionId ||
            request.hostGeneration != connection.generation
        ) {
            return@synchronized null
        }
        pending.remove(key)
    }

    override fun remove(request: PendingRelayRequest): PendingRelayRequest? = synchronized(lock) {
        val key = request.toKey()
        if (pending[key] == request) pending.remove(key) else null
    }

    override fun removeByHost(connection: HostConnection): List<PendingRelayRequest> =
        removeMatching { request ->
            request.hostSessionId == connection.sessionId &&
                request.hostGeneration == connection.generation
        }

    override fun removeByClientSession(sessionId: String): List<PendingRelayRequest> =
        removeMatching { request -> request.clientSessionId == sessionId }

    private fun removeMatching(predicate: (PendingRelayRequest) -> Boolean): List<PendingRelayRequest> =
        synchronized(lock) {
            val removed = pending.values.filter(predicate)
            removed.forEach { request -> pending.remove(request.toKey()) }
            removed
        }

    private fun PendingRelayRequest.toKey(): RequestKey =
        RequestKey(principal, request.hostId, request.requestId)

    private data class RequestKey(
        val principal: HarnessPrincipal,
        val hostId: HostId,
        val requestId: RequestId,
    )
}

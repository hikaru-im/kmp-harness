package im.hikaru.harness.client.connection

import im.hikaru.contracts.harness.relay.RelayResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** Uses RemoteConnection's authenticated, request/Host-fenced invocation; owns no socket or cache. */
internal class RemoteSessionDescendants(
    private val json: Json,
    private val invoke: suspend (String, JsonElement?) -> RelayResponse,
) {
    suspend fun descendants(parent: SessionId): SessionDescendantsResult {
        val response = invoke("session.descendants", json.encodeToJsonElement(SessionId.serializer(), parent))
        if (response.isMissingReadEndpoint("session.descendants")) return SessionDescendantsResult.Unavailable(parent)
        val result = decode<SessionDescendantsResult>(response)
        require(result.parent == parent) { "Descendants response scope mismatch" }
        return result
    }

    suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult {
        val payload = json.encodeToJsonElement(WireChildHistoryRequest.serializer(), WireChildHistoryRequest(parent, child))
        val response = invoke("session.child.history", payload)
        if (response.isMissingReadEndpoint("session.child.history")) return ChildHistoryResult.Unavailable(parent, child)
        val result = decode<ChildHistoryResult>(response)
        require(result.parent == parent && result.child == child) { "Child history response scope mismatch" }
        return result
    }

    private inline fun <reified T> decode(response: RelayResponse): T {
        if (!response.result.isSuccess) throw RemoteConnectionException(response.result.msg ?: "Remote request rejected", response.result.code)
        val payload = response.result.data ?: throw RemoteConnectionException("Remote response has no data", response.result.code)
        return json.decodeFromJsonElement(serializer(), payload)
    }
}

@Serializable
internal data class WireChildHistoryRequest(val parent: SessionId, val child: SessionId)

/** Only exact absent-endpoint responses denote missing capability; lineage/auth failures propagate. */
internal fun RelayResponse.isMissingReadEndpoint(method: String): Boolean =
    method in setOf("session.descendants", "session.child.history") && result.code == 404 &&
        (result.msg == "Harness endpoint unavailable" || result.msg == "Harness API endpoint '$method' 未注册")

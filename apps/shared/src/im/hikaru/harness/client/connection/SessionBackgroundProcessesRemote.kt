package im.hikaru.harness.client.connection

import im.hikaru.contracts.harness.relay.RelayResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** Reuses RemoteConnection's authenticated, request/Host-fenced invocation without owning a cache. */
internal class RemoteSessionBackgroundProcesses(
    private val json: Json,
    private val invoke: suspend (String, JsonElement?) -> RelayResponse,
) {
    suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult {
        val method = "session.background-processes.list"
        val response = invoke(method, json.encodeToJsonElement(SessionId.serializer(), parent))
        if (response.isMissingBackgroundEndpoint(method)) {
            return BackgroundProcessesResult.Unavailable(parent, "Remote background process endpoint is unavailable")
        }
        val result = decode<BackgroundProcessesResult>(response)
        require(result.parent == parent) { "Background process response scope mismatch" }
        return result
    }

    suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
        val method = "session.background-processes.read"
        val request = WireBackgroundOutputRequest(parent, process)
        val response = invoke(method, json.encodeToJsonElement(WireBackgroundOutputRequest.serializer(), request))
        if (response.isMissingBackgroundEndpoint(method)) {
            return BackgroundOutputResult.Unavailable(parent, process, "Remote background output endpoint is unavailable")
        }
        val result = decode<BackgroundOutputResult>(response)
        require(result.parent == parent && result.process == process) { "Background output response scope mismatch" }
        return result
    }

    suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
        val request = WireBackgroundInputRequest(parent, process, input)
        return mutate(
            "session.background-processes.write",
            json.encodeToJsonElement(WireBackgroundInputRequest.serializer(), request),
            BackgroundInputResult.Unavailable(parent, process, "Remote background input endpoint is unavailable"),
            BackgroundInputResult.UnknownOutcome(parent, process, "Remote background input outcome is unknown"),
        ) { result: BackgroundInputResult -> result.parent == parent && result.process == process }
    }

    suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        control("session.background-processes.close-input", parent, process)

    suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        control("session.background-processes.cancel", parent, process)

    private suspend fun control(method: String, parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
        val request = WireBackgroundControlRequest(parent, process)
        return mutate(
            method,
            json.encodeToJsonElement(WireBackgroundControlRequest.serializer(), request),
            BackgroundControlResult.Unavailable(parent, process, "Remote background control endpoint is unavailable"),
            BackgroundControlResult.UnknownOutcome(parent, process, "Remote background control outcome is unknown"),
        ) { result: BackgroundControlResult -> result.parent == parent && result.process == process }
    }

    /** Exactly one invocation. A lost, malformed or mismatched acknowledgement cannot prove non-execution. */
    private suspend inline fun <reified T> mutate(
        method: String,
        payload: JsonElement,
        unavailable: T,
        unknown: T,
        matches: (T) -> Boolean,
    ): T {
        currentCoroutineContext().ensureActive()
        return try {
            val response = invoke(method, payload)
            if (response.isMissingBackgroundEndpoint(method)) unavailable else {
                val result = decode<T>(response)
                require(matches(result)) { "Background mutation response scope mismatch" }
                result
            }
        } catch (_: Exception) {
            // Preserve actual caller cancellation; an invocation timeout in a live caller is UnknownOutcome.
            currentCoroutineContext().ensureActive()
            unknown
        }
    }

    private inline fun <reified T> decode(response: RelayResponse): T {
        if (!response.result.isSuccess) throw RemoteConnectionException(response.result.msg ?: "Remote request rejected", response.result.code)
        val payload = response.result.data ?: throw RemoteConnectionException("Remote response has no data", response.result.code)
        return json.decodeFromJsonElement(serializer(), payload)
    }
}

@Serializable
internal data class WireBackgroundOutputRequest(val parent: SessionId, val process: BackgroundProcessId)

@Serializable
internal data class WireBackgroundInputRequest(val parent: SessionId, val process: BackgroundProcessId, val input: String)

@Serializable
internal data class WireBackgroundControlRequest(val parent: SessionId, val process: BackgroundProcessId)

private fun RelayResponse.isMissingBackgroundEndpoint(method: String): Boolean =
    result.code == 404 &&
        (result.msg == "Harness endpoint unavailable" || result.msg == "Harness API endpoint '$method' 未注册")

package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.ChildHistoryRequest
import im.hikaru.harness.api.gateway.session.SessionChildHistoryEndpoint
import im.hikaru.harness.api.gateway.session.SessionDescendantsEndpoint
import im.hikaru.harness.session.SessionId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** HostRelayConnection calls this only after its Host-id and forbidden-method checks. */
internal suspend fun invokeSessionDescendantsRead(
    gateway: ApiGateway,
    method: String,
    payload: JsonElement?,
    json: Json,
): ApiResult<JsonElement> {
    return try {
        when (method) {
            SessionDescendantsEndpoint.method -> {
                val parent = json.decodeFromJsonElement(SessionId.serializer(), requireNotNull(payload))
                encodeSessionRead(gateway.invoke(SessionDescendantsEndpoint, parent), json)
            }
            SessionChildHistoryEndpoint.method -> {
                val request = json.decodeFromJsonElement(ChildHistoryRequest.serializer(), requireNotNull(payload))
                encodeSessionRead(gateway.invoke(SessionChildHistoryEndpoint, request), json)
            }
            else -> ApiResult(404, "Harness endpoint unavailable")
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IllegalArgumentException) {
        ApiResult(422, "Session read request rejected")
    }
}

private inline fun <reified T : Any> encodeSessionRead(result: ApiResult<T>, json: Json): ApiResult<JsonElement> =
    ApiResult(result.code, result.msg, result.data?.let { json.encodeToJsonElement(serializer(), it) })

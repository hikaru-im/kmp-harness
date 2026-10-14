package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.api.gateway.session.BackgroundInputRequest
import im.hikaru.harness.api.gateway.session.BackgroundControlRequest
import im.hikaru.harness.api.gateway.session.SessionBackgroundInputEndpoint
import im.hikaru.harness.api.gateway.session.SessionBackgroundCloseInputEndpoint
import im.hikaru.harness.api.gateway.session.SessionBackgroundCancelEndpoint
import im.hikaru.harness.api.gateway.session.BackgroundOutputRequest
import im.hikaru.harness.api.gateway.session.SessionBackgroundOutputEndpoint
import im.hikaru.harness.api.gateway.session.SessionBackgroundProcessesEndpoint
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.BackgroundInputResult
import im.hikaru.harness.session.api.BackgroundControlResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** Called after HostRelayConnection checks the authenticated Host and forbidden methods. */
internal suspend fun invokeSessionBackgroundProcessesRead(
    gateway: ApiGateway,
    method: String,
    payload: JsonElement?,
    json: Json,
): ApiResult<JsonElement> = try {
    when (method) {
        SessionBackgroundProcessesEndpoint.method -> {
            val parent = json.decodeFromJsonElement(SessionId.serializer(), requireNotNull(payload))
            encodeBackgroundRead(gateway.invoke(SessionBackgroundProcessesEndpoint, parent), json)
        }
        SessionBackgroundOutputEndpoint.method -> {
            val request = json.decodeFromJsonElement(BackgroundOutputRequest.serializer(), requireNotNull(payload))
            encodeBackgroundRead(gateway.invoke(SessionBackgroundOutputEndpoint, request), json)
        }
        else -> ApiResult(404, "Harness endpoint unavailable")
    }
} catch (error: CancellationException) {
    throw error
} catch (_: IllegalArgumentException) {
    ApiResult(422, "Session background read request rejected")
}

private inline fun <reified T : Any> encodeBackgroundRead(result: ApiResult<T>, json: Json): ApiResult<JsonElement> =
    ApiResult(result.code, result.msg, result.data?.let { json.encodeToJsonElement(serializer(), it) })

/** Host/request fencing is supplied by HostRelayConnection. Decode before dispatching any mutation. */
internal suspend fun invokeSessionBackgroundProcessesMutation(
    gateway: ApiGateway,
    method: String,
    payload: JsonElement?,
    json: Json,
): ApiResult<JsonElement> = try {
    when (method) {
        SessionBackgroundInputEndpoint.method -> {
            val request = json.decodeFromJsonElement(BackgroundInputRequest.serializer(), requireNotNull(payload))
            relayBackgroundMutation(gateway, SessionBackgroundInputEndpoint, request, json,
                BackgroundInputResult.UnknownOutcome(request.parent, request.process, "Background input outcome is unknown")) { result ->
                result.parent == request.parent && result.process == request.process
            }
        }
        SessionBackgroundCloseInputEndpoint.method, SessionBackgroundCancelEndpoint.method -> {
            val request = json.decodeFromJsonElement(BackgroundControlRequest.serializer(), requireNotNull(payload))
            val endpoint = if (method == SessionBackgroundCloseInputEndpoint.method) SessionBackgroundCloseInputEndpoint else SessionBackgroundCancelEndpoint
            relayBackgroundMutation(gateway, endpoint, request, json,
                BackgroundControlResult.UnknownOutcome(request.parent, request.process, "Background control outcome is unknown")) { result ->
                result.parent == request.parent && result.process == request.process
            }
        }
        else -> ApiResult(404, "Harness endpoint unavailable")
    }
} catch (error: CancellationException) {
    throw error
} catch (_: IllegalArgumentException) {
    ApiResult(422, "Session background mutation request rejected")
}

private suspend inline fun <Request : Any, reified Response : Any> relayBackgroundMutation(
    gateway: ApiGateway,
    endpoint: ApiEndpoint<Request, Response>,
    request: Request,
    json: Json,
    unknown: Response,
    matches: (Response) -> Boolean,
): ApiResult<JsonElement> {
    currentCoroutineContext().ensureActive()
    return try {
        val result = gateway.invoke(endpoint, request)
        if (result.code == 404 && result.msg == "Harness API endpoint '${endpoint.method}' 未注册") {
            ApiResult(result.code, result.msg)
        } else {
            val data = result.data
            val verified = if (result.isSuccess && data != null && matches(data)) data else unknown
            ApiResult(ApiResult.SUCCESS_CODE, "", json.encodeToJsonElement(serializer<Response>(), verified))
        }
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        ApiResult(ApiResult.SUCCESS_CODE, "", json.encodeToJsonElement(serializer<Response>(), unknown))
    }
}

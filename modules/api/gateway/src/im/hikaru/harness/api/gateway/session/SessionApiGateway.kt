package im.hikaru.harness.api.gateway.session

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.agent.AgentException
import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.SessionApi
import im.hikaru.harness.session.api.SessionPrompt
import im.hikaru.harness.session.api.SessionPromptReceipt
import im.hikaru.harness.session.api.SessionSummary
import im.hikaru.harness.session.api.sessionApi
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.llm.Message
import kotlinx.coroutines.CancellationException

/** Typed in-process endpoints backing both LocalConnection and a future relay adapter. */
public object SessionListEndpoint : ApiEndpoint<Unit, List<SessionSummary>>("session.list")

@kotlinx.serialization.Serializable
public data class SessionCreateRequest(
    val id: SessionId? = null,
    val options: CreateSessionOptions = CreateSessionOptions(),
    val agentOptions: AgentOptions = AgentOptions(),
)

public object SessionCreateEndpoint : ApiEndpoint<SessionCreateRequest, SessionSummary>("session.create")

public object SessionHistoryEndpoint : ApiEndpoint<SessionId, List<Message>>("session.history")

@kotlinx.serialization.Serializable
public data class SessionPromptRequest(
    val id: SessionId,
    val message: Message,
)

public object SessionPromptEndpoint : ApiEndpoint<SessionPromptRequest, SessionPrompt>("session.prompt")

@kotlinx.serialization.Serializable
public data class SessionCancelRequest(
    val id: SessionId,
    val keepInbox: Boolean = false,
)

public object SessionCancelEndpoint : ApiEndpoint<SessionCancelRequest, Unit>("session.cancel")

/** Register all session operations for the lifetime of the root Host Context. */
public suspend fun ApiGateway.registerSessionApi(owner: Context): Disposable {
    val registrations = listOf(
        register(owner, SessionListEndpoint) { request ->
            require(request === Unit)
            withSessionApi(owner) { it.list() }
        },
        register(owner, SessionCreateEndpoint) { request ->
            withSessionApi(owner) { it.create(request.id, request.options, request.agentOptions) }
        },
        register(owner, SessionHistoryEndpoint) { id ->
            withSessionApi(owner) { it.history(id) }
        },
        register(owner, SessionPromptEndpoint) { request ->
            withSessionApi(owner) { it.prompt(request.id, request.message) }
        },
        register(owner, SessionCancelEndpoint) { request ->
            withSessionApi(owner) { it.cancel(request.id, request.keepInbox); Unit }
        },
    )
    return Disposable {
        var failure: Throwable? = null
        registrations.asReversed().forEach { registration ->
            try {
                registration.dispose()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

private suspend inline fun <T : Any> withSessionApi(
    owner: Context,
    block: (SessionApi) -> T,
): ApiResult<T> = try {
    val api = owner.get(im.hikaru.harness.session.api.SessionApiKey)
        ?: return ApiResult(GatewayResultCodes.ConfigurationError, "Harness Session API Service 未注册")
    ApiResult(ApiResult.SUCCESS_CODE, "", block(api))
} catch (error: CancellationException) {
    throw error
} catch (error: AgentException) {
    // Keep model selection failures stable across local and relay transports.
    ApiResult(422, "Session operation rejected: ${error.code.name}")
} catch (_: IllegalArgumentException) {
    ApiResult(422, "Session operation rejected")
} catch (_: IllegalStateException) {
    ApiResult(404, "Session operation unavailable")
}

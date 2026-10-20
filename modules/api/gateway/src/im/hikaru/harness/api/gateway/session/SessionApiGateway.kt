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
import im.hikaru.harness.session.api.SessionDescendantsResult
import im.hikaru.harness.session.api.ChildHistoryResult
import im.hikaru.harness.session.api.BackgroundProcessesResult
import im.hikaru.harness.session.api.BackgroundOutputResult
import im.hikaru.harness.session.api.BackgroundInputResult
import im.hikaru.harness.session.api.BackgroundControlResult
import im.hikaru.harness.session.api.sessionApi
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.llm.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Typed in-process endpoints backing both LocalConnection and a future relay adapter. */
public object SessionListEndpoint : ApiEndpoint<Unit, List<SessionSummary>>("session.list")
public object ConversationUpdateEndpoint : ApiEndpoint<im.hikaru.harness.session.api.ConversationUpdate, SessionSummary>("session.conversation.update")
@kotlinx.serialization.Serializable
public data class ConversationArchiveRequest(val id: SessionId, val archived: Boolean)
public object ConversationArchiveEndpoint : ApiEndpoint<ConversationArchiveRequest, Unit>("session.conversation.archive")
@kotlinx.serialization.Serializable
public data class ConversationDeleteRequest(val id: SessionId, val expectedRevision: Long)
public object ConversationDeleteEndpoint : ApiEndpoint<ConversationDeleteRequest, Unit>("session.conversation.delete")

@kotlinx.serialization.Serializable
public data class SessionCreateRequest(
    val id: SessionId? = null,
    val options: CreateSessionOptions = CreateSessionOptions(),
    val agentOptions: AgentOptions = AgentOptions(),
)

public object SessionCreateEndpoint : ApiEndpoint<SessionCreateRequest, SessionSummary>("session.create")

public object SessionHistoryEndpoint : ApiEndpoint<SessionId, List<Message>>("session.history")

public object SessionDescendantsEndpoint : ApiEndpoint<SessionId, SessionDescendantsResult>("session.descendants")

@kotlinx.serialization.Serializable
public data class ChildHistoryRequest(val parent: SessionId, val child: SessionId)

public object SessionChildHistoryEndpoint : ApiEndpoint<ChildHistoryRequest, ChildHistoryResult>("session.child.history")

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

@kotlinx.serialization.Serializable
public data class SessionForkRequest(
    val parent: SessionId,
    val beforeMessageId: String,
)

public object SessionForkEndpoint : ApiEndpoint<SessionForkRequest, SessionSummary>("session.fork")

/** Register all session operations for the lifetime of the root Host Context. */
public suspend fun ApiGateway.registerSessionApi(owner: Context): Disposable {
    val registrations = listOf(
        register(owner, SessionListEndpoint) { request ->
            require(request === Unit)
            withSessionApi(owner) { it.list() }
        },
        register(owner, ConversationUpdateEndpoint) { update -> withSessionApi(owner) { it.updateConversation(update) } },
        register(owner, ConversationArchiveEndpoint) { request -> withSessionApi(owner) { it.archiveConversation(request.id, request.archived) } },
        register(owner, ConversationDeleteEndpoint) { request -> withSessionApi(owner) { it.deleteConversation(request.id, request.expectedRevision) } },
        register(owner, SessionCreateEndpoint) { request ->
            withSessionApi(owner) { it.create(request.id, request.options, request.agentOptions) }
        },
        register(owner, SessionHistoryEndpoint) { id ->
            withSessionApi(owner) { it.history(id) }
        },
        register(owner, SessionBackgroundProcessesEndpoint) { parent ->
            withSessionApi<BackgroundProcessesResult>(owner, BackgroundProcessesResult.Unavailable(parent, "Session API is unavailable")) {
                it.backgroundProcesses(parent)
            }
        },
        register(owner, SessionBackgroundOutputEndpoint) { request ->
            withSessionApi<BackgroundOutputResult>(owner, BackgroundOutputResult.Unavailable(request.parent, request.process, "Session API is unavailable")) {
                it.readBackgroundOutput(request.parent, request.process)
            }
        },
        register(owner, SessionBackgroundInputEndpoint) { request ->
            withBackgroundMutation<BackgroundInputResult>(owner,
                BackgroundInputResult.Unavailable(request.parent, request.process, "Session API is unavailable"),
                BackgroundInputResult.UnknownOutcome(request.parent, request.process, "Background input outcome is unknown")) {
                it.writeBackgroundInput(request.parent, request.process, request.input).also { result ->
                    require(result.parent == request.parent && result.process == request.process)
                }
            }
        },
        register(owner, SessionBackgroundCloseInputEndpoint) { request ->
            withBackgroundMutation<BackgroundControlResult>(owner,
                BackgroundControlResult.Unavailable(request.parent, request.process, "Session API is unavailable"),
                BackgroundControlResult.UnknownOutcome(request.parent, request.process, "Background input close outcome is unknown")) {
                it.closeBackgroundInput(request.parent, request.process).also { result ->
                    require(result.parent == request.parent && result.process == request.process)
                }
            }
        },
        register(owner, SessionBackgroundCancelEndpoint) { request ->
            withBackgroundMutation<BackgroundControlResult>(owner,
                BackgroundControlResult.Unavailable(request.parent, request.process, "Session API is unavailable"),
                BackgroundControlResult.UnknownOutcome(request.parent, request.process, "Background process cancel outcome is unknown")) {
                it.cancelBackgroundProcess(request.parent, request.process).also { result ->
                    require(result.parent == request.parent && result.process == request.process)
                }
            }
        },
        register(owner, SessionDescendantsEndpoint) { parent ->
            withSessionApi<SessionDescendantsResult>(owner, SessionDescendantsResult.Unavailable(parent)) { it.descendants(parent) }
        },
        register(owner, SessionChildHistoryEndpoint) { request ->
            withSessionApi<ChildHistoryResult>(owner, ChildHistoryResult.Unavailable(request.parent, request.child)) {
                it.readChildHistory(request.parent, request.child)
            }
        },
        register(owner, SessionPromptEndpoint) { request ->
            withSessionApi(owner) { it.prompt(request.id, request.message) }
        },
        register(owner, SessionCancelEndpoint) { request ->
            withSessionApi(owner) { it.cancel(request.id, request.keepInbox); Unit }
        },
        register(owner, SessionForkEndpoint) { request ->
            withSessionApi(owner) { it.fork(request.parent, request.beforeMessageId) }
        },
        registerSessionTerminals(owner),
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

/** Once dispatched, an exception cannot establish that a process mutation did not happen. */
private suspend inline fun <T : Any> withBackgroundMutation(
    owner: Context,
    unavailable: T,
    unknown: T,
    block: (SessionApi) -> T,
): ApiResult<T> {
    currentCoroutineContext().ensureActive()
    val result = try {
        val api = owner.get(im.hikaru.harness.session.api.SessionApiKey)
        if (api == null) unavailable else block(api)
    } catch (_: Exception) {
        // Caller cancellation remains cancellation. A service timeout/cancellation in a live caller is uncertain.
        currentCoroutineContext().ensureActive()
        unknown
    }
    return ApiResult(ApiResult.SUCCESS_CODE, "", result)
}

private suspend inline fun <T : Any> withSessionApi(
    owner: Context,
    unavailable: T? = null,
    block: (SessionApi) -> T,
): ApiResult<T> {
    return try {
        val api = owner.get(im.hikaru.harness.session.api.SessionApiKey)
            ?: return if (unavailable != null) {
                ApiResult(ApiResult.SUCCESS_CODE, "", unavailable)
            } else {
                ApiResult(GatewayResultCodes.ConfigurationError, "Harness Session API Service 未注册")
            }
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
}

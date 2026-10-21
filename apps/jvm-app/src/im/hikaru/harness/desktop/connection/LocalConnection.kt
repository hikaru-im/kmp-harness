package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.SessionCancelEndpoint
import im.hikaru.harness.api.gateway.session.SessionCancelRequest
import im.hikaru.harness.api.gateway.session.SessionForkEndpoint
import im.hikaru.harness.api.gateway.session.SessionForkRequest
import im.hikaru.harness.api.gateway.session.SessionCreateEndpoint
import im.hikaru.harness.api.gateway.session.SessionCreateRequest
import im.hikaru.harness.api.gateway.session.SessionHistoryEndpoint
import im.hikaru.harness.api.gateway.session.SessionListEndpoint
import im.hikaru.harness.api.gateway.session.SessionPromptEndpoint
import im.hikaru.harness.api.gateway.session.SessionPromptRequest
import im.hikaru.harness.api.gateway.session.SessionDescendantsEndpoint
import im.hikaru.harness.api.gateway.session.SessionChildHistoryEndpoint
import im.hikaru.harness.api.gateway.session.ChildHistoryRequest
import im.hikaru.harness.api.gateway.session.SessionBackgroundProcessesEndpoint
import im.hikaru.harness.api.gateway.session.SessionBackgroundOutputEndpoint
import im.hikaru.harness.api.gateway.session.BackgroundOutputRequest
import im.hikaru.harness.api.gateway.session.BackgroundInputRequest
import im.hikaru.harness.api.gateway.session.BackgroundControlRequest
import im.hikaru.harness.api.gateway.session.SessionBackgroundInputEndpoint
import im.hikaru.harness.api.gateway.session.SessionBackgroundCloseInputEndpoint
import im.hikaru.harness.api.gateway.session.SessionBackgroundCancelEndpoint
import im.hikaru.harness.client.connection.BackgroundProcessId
import im.hikaru.harness.client.connection.BackgroundProcessesResult
import im.hikaru.harness.client.connection.BackgroundOutputResult
import im.hikaru.harness.client.connection.BackgroundInputResult
import im.hikaru.harness.client.connection.BackgroundControlResult
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.client.connection.SessionDescendantsResult
import im.hikaru.harness.client.connection.ChildHistoryResult
import im.hikaru.harness.client.connection.TerminalCreateRequest
import im.hikaru.harness.client.connection.TerminalCreateResult
import im.hikaru.harness.client.connection.TerminalDiscoveryResult
import im.hikaru.harness.client.connection.TerminalKey
import im.hikaru.harness.client.connection.TerminalInput
import im.hikaru.harness.client.connection.TerminalListResult
import im.hikaru.harness.client.connection.TerminalMutationResult
import im.hikaru.harness.client.connection.TerminalOwner
import im.hikaru.harness.client.connection.TerminalReadResult
import im.hikaru.harness.client.connection.TerminalSize
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.MessageId
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.MessageSource as CoreMessageSource
import im.hikaru.harness.llm.ModelMessageSource
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.ToolMessageSource
import im.hikaru.harness.llm.UserMessageSource
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.HostApi
import im.hikaru.harness.client.connection.AgentOptions as ClientAgentOptions
import im.hikaru.harness.client.connection.CreateSessionOptions as ClientCreateSessionOptions
import im.hikaru.harness.client.connection.Message as ClientMessage
import im.hikaru.harness.client.connection.MessageContent
import im.hikaru.harness.client.connection.MessageRole as ClientMessageRole
import im.hikaru.harness.client.connection.MessageSource
import im.hikaru.harness.client.connection.ModelMessageSource as ClientModelMessageSource
import im.hikaru.harness.client.connection.PluginMessageSource as ClientPluginMessageSource
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.connection.SessionPrompt as ClientSessionPrompt
import im.hikaru.harness.client.connection.SessionPromptReceipt as ClientSessionPromptReceipt
import im.hikaru.harness.client.connection.SessionSummary as ClientSessionSummary
import im.hikaru.harness.client.connection.SessionApi
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.ToolResultContent
import im.hikaru.harness.client.connection.ToolMessageSource as ClientToolMessageSource
import im.hikaru.harness.client.connection.UserMessageSource as ClientUserMessageSource
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.SessionPrompt
import im.hikaru.harness.session.api.SessionSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Desktop 客户端到同进程 Harness Host 的本地连接。
 *
 * 本实现直接调用类型化 Gateway，不创建 RPC 信封，也不执行 JSON 编解码。
 */
public class LocalConnection(
    private val gateway: ApiGateway,
) : Connection {
    private val localTerminals = LocalSessionTerminals(gateway)

    override val host: HostApi =
        object : HostApi {
            override suspend fun describe(): ApiResult<HostDescription> =
                gateway.invoke(
                    endpoint = HostDescribeEndpoint,
                    request = Unit,
                )
        }

    override val session: SessionApi =
        object : SessionApi {
            override suspend fun terminalCapability(session: ClientSessionId): TerminalDiscoveryResult =
                localTerminals.terminalCapability(session)

            override suspend fun createTerminal(request: TerminalCreateRequest): TerminalCreateResult =
                localTerminals.createTerminal(request)

            override suspend fun terminals(owner: TerminalOwner): TerminalListResult =
                localTerminals.terminals(owner)

            override suspend fun readTerminal(key: TerminalKey): TerminalReadResult =
                localTerminals.readTerminal(key)

            override suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult =
                localTerminals.writeTerminal(key, input)

            override suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult =
                localTerminals.inputTerminal(key, input)

            override suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult =
                localTerminals.resizeTerminal(key, size)

            override suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult =
                localTerminals.closeTerminal(key)

            override suspend fun backgroundProcesses(parent: ClientSessionId): BackgroundProcessesResult =
                optionalRead(SessionBackgroundProcessesEndpoint, SessionId(parent.value))?.toClient()
                    ?.also { require(it.parent == parent) { "Background process parent mismatch" } }
                    ?: BackgroundProcessesResult.Unavailable(parent, "Background process service is unavailable")

            override suspend fun readBackgroundOutput(parent: ClientSessionId, process: BackgroundProcessId): BackgroundOutputResult =
                optionalRead(SessionBackgroundOutputEndpoint, BackgroundOutputRequest(
                    SessionId(parent.value), im.hikaru.harness.session.api.BackgroundProcessId(process.value)))
                    ?.toClient()?.also { require(it.parent == parent && it.process == process) { "Background output identity mismatch" } }
                    ?: BackgroundOutputResult.Unavailable(parent, process, "Background output service is unavailable")

            override suspend fun writeBackgroundInput(parent: ClientSessionId, process: BackgroundProcessId, input: String): BackgroundInputResult =
                backgroundMutation(SessionBackgroundInputEndpoint, BackgroundInputRequest(
                    SessionId(parent.value), im.hikaru.harness.session.api.BackgroundProcessId(process.value), input),
                    BackgroundInputResult.Unavailable(parent, process, "Background input endpoint is unavailable"),
                    BackgroundInputResult.UnknownOutcome(parent, process, "Background input outcome is unknown")) { response ->
                    response.toClient().also { require(it.parent == parent && it.process == process) }
                }

            override suspend fun closeBackgroundInput(parent: ClientSessionId, process: BackgroundProcessId): BackgroundControlResult =
                backgroundMutation(SessionBackgroundCloseInputEndpoint, BackgroundControlRequest(
                    SessionId(parent.value), im.hikaru.harness.session.api.BackgroundProcessId(process.value)),
                    BackgroundControlResult.Unavailable(parent, process, "Background input close endpoint is unavailable"),
                    BackgroundControlResult.UnknownOutcome(parent, process, "Background input close outcome is unknown")) { response ->
                    response.toClient().also { require(it.parent == parent && it.process == process) }
                }

            override suspend fun cancelBackgroundProcess(parent: ClientSessionId, process: BackgroundProcessId): BackgroundControlResult =
                backgroundMutation(SessionBackgroundCancelEndpoint, BackgroundControlRequest(
                    SessionId(parent.value), im.hikaru.harness.session.api.BackgroundProcessId(process.value)),
                    BackgroundControlResult.Unavailable(parent, process, "Background process cancel endpoint is unavailable"),
                    BackgroundControlResult.UnknownOutcome(parent, process, "Background process cancel outcome is unknown")) { response ->
                    response.toClient().also { require(it.parent == parent && it.process == process) }
                }

            override suspend fun descendants(parent: ClientSessionId): SessionDescendantsResult =
                optionalRead(SessionDescendantsEndpoint, SessionId(parent.value))?.toClient()
                    ?: SessionDescendantsResult.Unavailable(parent)

            override suspend fun readChildHistory(parent: ClientSessionId, child: ClientSessionId): ChildHistoryResult =
                optionalRead(SessionChildHistoryEndpoint, ChildHistoryRequest(SessionId(parent.value), SessionId(child.value)))
                    ?.toClient() ?: ChildHistoryResult.Unavailable(parent, child)

            override suspend fun list(): List<ClientSessionSummary> =
                invoke(SessionListEndpoint, Unit).map(::toClientSummary)

            override suspend fun deleteConversation(id: ClientSessionId, expectedRevision: Long) {
                invoke(im.hikaru.harness.api.gateway.session.ConversationDeleteEndpoint,
                    im.hikaru.harness.api.gateway.session.ConversationDeleteRequest(SessionId(id.value), expectedRevision))
            }

            override suspend fun updateConversation(update: im.hikaru.harness.client.connection.ConversationUpdate): ClientSessionSummary =
                invoke(im.hikaru.harness.api.gateway.session.ConversationUpdateEndpoint,
                    im.hikaru.harness.session.api.ConversationUpdate(im.hikaru.harness.session.SessionId(update.id.value),
                        update.expectedRevision, update.title, update.pinned, update.lastReadSeq)).let(::toClientSummary)

            override suspend fun archiveConversation(id: ClientSessionId, archived: Boolean) {
                invoke(im.hikaru.harness.api.gateway.session.ConversationArchiveEndpoint,
                    im.hikaru.harness.api.gateway.session.ConversationArchiveRequest(SessionId(id.value), archived))
            }

            override suspend fun create(
                id: ClientSessionId?,
                options: ClientCreateSessionOptions,
                agentOptions: ClientAgentOptions,
            ): ClientSessionSummary =
                invoke(
                    SessionCreateEndpoint,
                    SessionCreateRequest(
                        id = id?.let { SessionId(it.value) },
                        options = CreateSessionOptions(
                            cwd = options.cwd,
                            parentSession = options.parentSession?.let { SessionId(it.value) },
                            seedLength = options.seedLength,
                            agentPreset = options.agentPreset,
                        ),
                        agentOptions = AgentOptions(
                            preset = agentOptions.preset,
                            cwd = agentOptions.cwd,
                            provider = agentOptions.provider,
                            model = agentOptions.model,
                            reasoningEffort = agentOptions.reasoningEffort?.let { ReasoningEffortId(it.value) },
                            maxTokens = agentOptions.maxTokens,
                            reasoning = agentOptions.reasoning?.toCoreReasoning(),
                        ),
                    ),
                ).let(::toClientSummary)

            override suspend fun modelConfiguration(id: ClientSessionId): ClientAgentOptions? =
                optionalRead(im.hikaru.harness.api.gateway.session.SessionModelConfigurationEndpoint, SessionId(id.value))?.options?.let {
                    ClientAgentOptions(provider = it.provider, model = it.model, maxTokens = it.maxTokens,
                        reasoningEffort = it.reasoningEffort?.let { effort -> im.hikaru.harness.client.connection.ReasoningEffortId(effort.value) },
                        reasoning = it.reasoning?.toClientReasoning())
                }

            override suspend fun history(id: ClientSessionId): List<ClientMessage> =
                invoke(SessionHistoryEndpoint, SessionId(id.value)).map(::toClientMessage)

            override suspend fun prompt(id: ClientSessionId, message: ClientMessage): ClientSessionPrompt =
                LocalSessionPrompt(
                    invoke(
                        SessionPromptEndpoint,
                        SessionPromptRequest(SessionId(id.value), message.toCoreMessage()),
                    )
                )

            override suspend fun prompt(id: ClientSessionId, message: ClientMessage, agentOptions: ClientAgentOptions): ClientSessionPrompt =
                LocalSessionPrompt(invoke(SessionPromptEndpoint, SessionPromptRequest(SessionId(id.value), message.toCoreMessage(),
                    AgentOptions(provider = agentOptions.provider, model = agentOptions.model,
                        reasoningEffort = agentOptions.reasoningEffort?.let { ReasoningEffortId(it.value) },
                        maxTokens = agentOptions.maxTokens, reasoning = agentOptions.reasoning?.toCoreReasoning()))))

            override suspend fun cancel(id: ClientSessionId, keepInbox: Boolean) {
                invoke(SessionCancelEndpoint, SessionCancelRequest(SessionId(id.value), keepInbox))
            }

            override suspend fun fork(parent: ClientSessionId, beforeMessageId: String): ClientSessionSummary =
                invoke(SessionForkEndpoint, SessionForkRequest(SessionId(parent.value), beforeMessageId))
                    .let(::toClientSummary)
        }

    private suspend fun <Request : Any, Response : Any, Result> backgroundMutation(
        endpoint: ApiEndpoint<Request, Response>,
        request: Request,
        unavailable: Result,
        unknown: Result,
        map: (Response) -> Result,
    ): Result {
        currentCoroutineContext().ensureActive()
        return try {
            val result = gateway.invoke(endpoint, request)
            if (result.code == GatewayResultCodes.NotFound &&
                result.msg == "Harness API endpoint '${endpoint.method}' 未注册") unavailable
            else if (!result.isSuccess || result.data == null) unknown
            else map(requireNotNull(result.data))
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            unknown
        }
    }

    private suspend fun <Request : Any, Response : Any> optionalRead(
        endpoint: ApiEndpoint<Request, Response>,
        request: Request,
    ): Response? {
        val result = gateway.invoke(endpoint, request)
        if (result.code == GatewayResultCodes.NotFound &&
            result.msg == "Harness API endpoint '${endpoint.method}' 未注册") return null
        if (!result.isSuccess) throw LocalConnectionException(result.code, result.msg)
        return result.data ?: throw LocalConnectionException(result.code, "Harness API returned no data")
    }

    private suspend fun <Request : Any, Response : Any> invoke(
        endpoint: ApiEndpoint<Request, Response>,
        request: Request,
    ): Response {
        val result = gateway.invoke(endpoint, request)
        if (!result.isSuccess) {
            throw LocalConnectionException(result.code, result.msg)
        }
        return result.data ?: throw LocalConnectionException(result.code, "Harness API returned no data")
    }
}

private fun toClientSummary(summary: SessionSummary): ClientSessionSummary =
    ClientSessionSummary(
        id = ClientSessionId(summary.id.value),
        createdAt = summary.createdAt,
        cwd = summary.cwd,
        agentPreset = summary.agentPreset,
        title = summary.title, updatedAt = summary.updatedAt, pinned = summary.pinned, unread = summary.unread,
        archived = summary.archived, revision = summary.revision, lastEventSeq = summary.lastEventSeq,
        managementAvailable = summary.managementAvailable, archiveAvailable = summary.archiveAvailable,
        live = summary.live, running = summary.running,
        customTitle = summary.customTitle, lastReadSeq = summary.lastReadSeq,
    )

internal fun toClientMessage(message: Message): ClientMessage =
    ClientMessage(
        id = message.id.value,
        role = when (message.role) {
            MessageRole.USER -> ClientMessageRole.USER
            MessageRole.ASSISTANT -> ClientMessageRole.ASSISTANT
        },
        content = message.content.map(::toClientContent),
        source = message.source.toClientSource(),
        timestampMillis = message.timestampMillis,
    )

internal fun ClientMessage.toCoreMessage(): Message =
    Message(
        id = MessageId(id),
        role = when (role) {
            ClientMessageRole.USER -> MessageRole.USER
            ClientMessageRole.ASSISTANT -> MessageRole.ASSISTANT
        },
        content = content.map(::toCoreContent),
        source = source.toCoreSource(),
    )

private fun CoreMessageSource.toClientSource(): MessageSource = when (this) {
    UserMessageSource -> ClientUserMessageSource
    is PluginMessageSource -> ClientPluginMessageSource(plugin)
    is ModelMessageSource -> ClientModelMessageSource(provider, model, replayState)
    is ToolMessageSource -> ClientToolMessageSource(callId.value)
}

private fun MessageSource.toCoreSource(): CoreMessageSource = when (this) {
    ClientUserMessageSource -> UserMessageSource
    is ClientPluginMessageSource -> PluginMessageSource(plugin)
    is ClientModelMessageSource -> ModelMessageSource(provider, model, replayState)
    is ClientToolMessageSource -> ToolMessageSource(im.hikaru.harness.llm.CallId(callId))
}

private fun toClientContent(content: im.hikaru.harness.llm.ContentBlock): MessageContent =
    when (content) {
        is im.hikaru.harness.llm.TextBlock -> TextContent(content.text)
        is im.hikaru.harness.llm.ReasoningBlock -> ReasoningContent(content.text)
        is im.hikaru.harness.llm.ToolCallBlock ->
            ToolCallContent(content.id.value, content.name, content.arguments)
        is im.hikaru.harness.llm.ToolResultBlock ->
            ToolResultContent(
                toolCallId = content.toolCallId.value,
                content = content.content.map(::toClientContent),
                isError = content.isError,
            )
    }

private fun toCoreContent(content: MessageContent): im.hikaru.harness.llm.ContentBlock =
    when (content) {
        is TextContent -> im.hikaru.harness.llm.TextBlock(content.text)
        is ReasoningContent -> im.hikaru.harness.llm.ReasoningBlock(content.text)
        is ToolCallContent ->
            im.hikaru.harness.llm.ToolCallBlock(
                im.hikaru.harness.llm.CallId(content.id),
                content.name,
                content.arguments,
            )
        is ToolResultContent ->
            im.hikaru.harness.llm.ToolResultBlock(
                toolCallId = im.hikaru.harness.llm.CallId(content.toolCallId),
                content = content.content.map(::toCoreContent),
                isError = content.isError,
            )
    }

private class LocalSessionPrompt(
    private val delegate: SessionPrompt,
) : ClientSessionPrompt {
    override val receipt: ClientSessionPromptReceipt =
        ClientSessionPromptReceipt(
            sessionId = ClientSessionId(delegate.receipt.sessionId.value),
            messageId = delegate.receipt.messageId,
        )

    override suspend fun awaitIdle() = delegate.awaitIdle()

    override suspend fun failure(): im.hikaru.harness.client.connection.SessionPromptFailure? =
        delegate.failure()?.let { im.hikaru.harness.client.connection.SessionPromptFailure(it.code, it.status) }
}

/** Stable local transport failure; it intentionally excludes endpoint payloads and credentials. */
public class LocalConnectionException(
    public val code: Int?,
    message: String?,
) : IllegalStateException(message ?: "Harness local connection failed")

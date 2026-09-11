package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.api.gateway.ApiEndpoint
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.SessionCancelEndpoint
import im.hikaru.harness.api.gateway.session.SessionCancelRequest
import im.hikaru.harness.api.gateway.session.SessionCreateEndpoint
import im.hikaru.harness.api.gateway.session.SessionCreateRequest
import im.hikaru.harness.api.gateway.session.SessionHistoryEndpoint
import im.hikaru.harness.api.gateway.session.SessionListEndpoint
import im.hikaru.harness.api.gateway.session.SessionPromptEndpoint
import im.hikaru.harness.api.gateway.session.SessionPromptRequest
import im.hikaru.harness.llm.Message
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.HostApi
import im.hikaru.harness.client.connection.AgentOptions as ClientAgentOptions
import im.hikaru.harness.client.connection.CreateSessionOptions as ClientCreateSessionOptions
import im.hikaru.harness.client.connection.Message as ClientMessage
import im.hikaru.harness.client.connection.MessageContent
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.connection.SessionPrompt as ClientSessionPrompt
import im.hikaru.harness.client.connection.SessionPromptReceipt as ClientSessionPromptReceipt
import im.hikaru.harness.client.connection.SessionSummary as ClientSessionSummary
import im.hikaru.harness.client.connection.SessionApi
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.ToolResultContent
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.SessionPrompt
import im.hikaru.harness.session.api.SessionSummary

/**
 * Desktop 客户端到同进程 Harness Host 的本地连接。
 *
 * 本实现直接调用类型化 Gateway，不创建 RPC 信封，也不执行 JSON 编解码。
 */
public class LocalConnection(
    private val gateway: ApiGateway,
) : Connection {

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
            override suspend fun list(): List<ClientSessionSummary> =
                invoke(SessionListEndpoint, Unit).map(::toClientSummary)

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
                        ),
                    ),
                ).let(::toClientSummary)

            override suspend fun history(id: ClientSessionId): List<ClientMessage> =
                invoke(SessionHistoryEndpoint, SessionId(id.value)).map(::toClientMessage)

            override suspend fun prompt(id: ClientSessionId, message: ClientMessage): ClientSessionPrompt =
                LocalSessionPrompt(
                    invoke(
                        SessionPromptEndpoint,
                        SessionPromptRequest(SessionId(id.value), message.toCoreMessage()),
                    )
                )

            override suspend fun cancel(id: ClientSessionId, keepInbox: Boolean) {
                invoke(SessionCancelEndpoint, SessionCancelRequest(SessionId(id.value), keepInbox))
            }
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
    )

internal fun toClientMessage(message: Message): ClientMessage =
    ClientMessage(
        content = message.content.map(::toClientContent),
    )

internal fun ClientMessage.toCoreMessage(): Message =
    im.hikaru.harness.llm.createUserMessage(
        content = content.map(::toCoreContent),
    )

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
}

/** Stable local transport failure; it intentionally excludes endpoint payloads and credentials. */
public class LocalConnectionException(
    public val code: Int?,
    message: String?,
) : IllegalStateException(message ?: "Harness local connection failed")

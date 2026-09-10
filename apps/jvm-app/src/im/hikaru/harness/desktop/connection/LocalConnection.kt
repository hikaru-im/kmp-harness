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
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.SessionApi
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
            override suspend fun list(): List<SessionSummary> =
                invoke(SessionListEndpoint, Unit)

            override suspend fun create(
                id: SessionId?,
                options: CreateSessionOptions,
                agentOptions: AgentOptions,
            ): SessionSummary =
                invoke(SessionCreateEndpoint, SessionCreateRequest(id, options, agentOptions))

            override suspend fun history(id: SessionId): List<Message> =
                invoke(SessionHistoryEndpoint, id)

            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt =
                invoke(SessionPromptEndpoint, SessionPromptRequest(id, message))

            override suspend fun cancel(id: SessionId, keepInbox: Boolean) {
                invoke(SessionCancelEndpoint, SessionCancelRequest(id, keepInbox))
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

/** Stable local transport failure; it intentionally excludes endpoint payloads and credentials. */
public class LocalConnectionException(
    public val code: Int?,
    message: String?,
) : IllegalStateException(message ?: "Harness local connection failed")

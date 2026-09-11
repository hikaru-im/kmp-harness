package im.hikaru.harness.client.connection

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 客户端访问 Harness Host 的类型化连接。
 *
 * Desktop 使用进程内 LocalConnection，Mobile 后续使用 RemoteConnection；上层客户端状态不需要
 * 判断 Agent 实际位于本地还是远程。
 */
public interface Connection {
    public val host: HostApi
    public val session: SessionApi
}

@JvmInline
@Serializable
public value class SessionId(public val value: String) {
    init { require(value.isNotBlank()) { "SessionId must not be blank" } }
}

@Serializable
public data class CreateSessionOptions(
    val cwd: String? = null,
    val parentSession: SessionId? = null,
    val seedLength: Long? = null,
    val agentPreset: String? = null,
)

@Serializable
public data class AgentOptions(
    val preset: String? = null,
    val cwd: String? = null,
    val provider: String? = null,
    val model: String? = null,
)

@Serializable
public sealed interface MessageContent

@Serializable
public data class TextContent(val text: String) : MessageContent

@Serializable
public data class Message(val content: List<MessageContent>)

public fun userMessage(text: String): Message = Message(listOf(TextContent(text)))

@Serializable
public data class SessionSummary(
    val id: SessionId,
    val createdAt: Long,
    val cwd: String? = null,
    val agentPreset: String? = null,
)

@Serializable
public data class SessionPromptReceipt(
    val sessionId: SessionId,
    val messageId: String,
)

public interface SessionPrompt {
    public val receipt: SessionPromptReceipt
    public suspend fun awaitIdle()
}

public interface SessionApi {
    public suspend fun list(): List<SessionSummary>
    public suspend fun create(
        id: SessionId? = null,
        options: CreateSessionOptions = CreateSessionOptions(),
        agentOptions: AgentOptions = AgentOptions(),
    ): SessionSummary
    public suspend fun history(id: SessionId): List<Message>
    public suspend fun prompt(id: SessionId, message: Message): SessionPrompt
    public suspend fun cancel(id: SessionId, keepInbox: Boolean = false)
}

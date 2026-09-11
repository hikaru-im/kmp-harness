package im.hikaru.harness.client.connection

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
@SerialName("text")
public data class TextContent(val text: String) : MessageContent

@Serializable
@SerialName("reasoning")
public data class ReasoningContent(val text: String) : MessageContent

@Serializable
@SerialName("tool-call")
public data class ToolCallContent(
    val id: String,
    val name: String,
    val arguments: String,
) : MessageContent {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

@Serializable
@SerialName("tool-result")
public data class ToolResultContent(
    val toolCallId: String,
    val content: List<MessageContent>,
    val isError: Boolean = false,
) : MessageContent {
    init { require(toolCallId.isNotBlank()) }
}

@Serializable
public enum class MessageRole { USER, ASSISTANT }

@Serializable
public sealed interface MessageSource

@Serializable
@SerialName("user")
public data object UserMessageSource : MessageSource

@Serializable
@SerialName("plugin")
public data class PluginMessageSource(val plugin: String) : MessageSource

@Serializable
@SerialName("model")
public data class ModelMessageSource(
    val provider: String,
    val model: String,
    val replayState: JsonElement? = null,
) : MessageSource

@Serializable
@SerialName("tool")
public data class ToolMessageSource(val callId: String) : MessageSource

@Serializable
public data class Message(
    val id: String,
    val role: MessageRole,
    val content: List<MessageContent>,
    val source: MessageSource,
)

@OptIn(ExperimentalUuidApi::class)
public fun userMessage(text: String): Message =
    Message(
        id = Uuid.random().toString(),
        role = MessageRole.USER,
        content = listOf(TextContent(text)),
        source = UserMessageSource,
    )

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

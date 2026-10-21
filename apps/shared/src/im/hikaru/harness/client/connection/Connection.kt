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
    val reasoningEffort: ReasoningEffortId? = null,
    val maxTokens: Long? = null,
    val reasoning: ReasoningOptions? = null,
) {
    init {
        require(preset == null || preset.isNotBlank()) { "Agent preset must not be blank" }
        require(cwd == null || cwd.isNotBlank()) { "Agent cwd must not be blank" }
        require(provider == null || provider.isNotBlank()) { "Agent provider must not be blank" }
        require(model == null || model.isNotBlank()) { "Agent model must not be blank" }
        require(maxTokens == null || maxTokens > 0L) { "Agent max tokens must be positive" }
    }
}

@JvmInline
@Serializable
public value class ReasoningEffortId(public val value: String) {
    init { require(value.isNotBlank()) { "Reasoning effort must not be blank" } }
}

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
public enum class MessageRole {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,
}

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
    val timestampMillis: Long? = null,
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
    val title: String? = null,
    val updatedAt: Long = createdAt,
    val pinned: Boolean = false,
    val unread: Boolean = false,
    val archived: Boolean = false,
    val revision: Long = 0,
    val lastEventSeq: Long = -1,
    val managementAvailable: Boolean = false,
    val archiveAvailable: Boolean = false,
    val live: Boolean = true,
    /** Authoritative execution state reported by the Host; Stop is offered only while this is true. */
    val running: Boolean = false,
    val customTitle: String? = null,
    val lastReadSeq: Long = -1,
)

@Serializable
public data class ConversationUpdate(val id: SessionId, val expectedRevision: Long, val title: String?,
    val pinned: Boolean, val lastReadSeq: Long)

@Serializable
public data class SessionPromptReceipt(
    val sessionId: SessionId,
    val messageId: String,
)

public interface SessionPrompt {
    public val receipt: SessionPromptReceipt
    public suspend fun awaitIdle()
    public suspend fun failure(): SessionPromptFailure? = null
}

public data class SessionPromptFailure(val code: String, val status: Int? = null)

public interface SessionApi {
    public suspend fun deleteConversation(id: SessionId, expectedRevision: Long): Unit = error("Conversation deletion is unavailable")
    public suspend fun updateConversation(update: ConversationUpdate): SessionSummary = error("Conversation management is unavailable")
    public suspend fun archiveConversation(id: SessionId, archived: Boolean): Unit = error("Archive management is unavailable")
    public suspend fun terminalCapability(session: SessionId): TerminalDiscoveryResult =
        TerminalDiscoveryResult.Unavailable(session, "Terminal Host service is not exposed by this connection")

    /** Create is never retried; an unknown outcome may have no acknowledged terminal key. */
    public suspend fun createTerminal(request: TerminalCreateRequest): TerminalCreateResult =
        TerminalCreateResult.Unavailable(request.owner, "Terminal Host service is not exposed by this connection")

    public suspend fun terminals(owner: TerminalOwner): TerminalListResult =
        TerminalListResult.Unavailable(owner, "Terminal Host service is not exposed by this connection")

    public suspend fun readTerminal(key: TerminalKey): TerminalReadResult =
        TerminalReadResult.Unavailable(key, "Terminal Host service is not exposed by this connection")

    public suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is not exposed by this connection")

    public suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is not exposed by this connection")

    public suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is not exposed by this connection")

    public suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host service is not exposed by this connection")

    public suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult =
        BackgroundProcessesResult.Unavailable(parent, "Background process service is not exposed by this connection")

    public suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult =
        BackgroundOutputResult.Unavailable(parent, process, "Background output service is not exposed by this connection")

    /** Mutations are never retried; UnknownOutcome means the process may already have received the operation. */
    public suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult =
        BackgroundInputResult.Unavailable(parent, process, "Background input service is not exposed by this connection")

    public suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        BackgroundControlResult.Unavailable(parent, process, "Background input service is not exposed by this connection")

    public suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        BackgroundControlResult.Unavailable(parent, process, "Background process control service is not exposed by this connection")

    /** Optional read-only capability. Successful empty and unavailable remain distinct. */
    public suspend fun descendants(parent: SessionId): SessionDescendantsResult =
        SessionDescendantsResult.Unavailable(parent)

    public suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult =
        ChildHistoryResult.Unavailable(parent, child)

    public suspend fun list(): List<SessionSummary>
    public suspend fun create(
        id: SessionId? = null,
        options: CreateSessionOptions = CreateSessionOptions(),
        agentOptions: AgentOptions = AgentOptions(),
    ): SessionSummary
    public suspend fun modelConfiguration(id: SessionId): AgentOptions? = null
    public suspend fun history(id: SessionId): List<Message>
    public suspend fun prompt(id: SessionId, message: Message): SessionPrompt
    public suspend fun prompt(id: SessionId, message: Message, agentOptions: AgentOptions): SessionPrompt =
        error("此连接暂不支持更换模型")
    public suspend fun cancel(id: SessionId, keepInbox: Boolean = false)

    /** Creates a child conversation holding this one's history before [beforeMessageId]. */
    public suspend fun fork(parent: SessionId, beforeMessageId: String): SessionSummary =
        error("Session forking is unavailable")
}

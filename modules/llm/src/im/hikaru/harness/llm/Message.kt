package im.hikaru.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 模型消息角色。系统提示词由 GenerateOptions.system 单独承载。 */
@Serializable
enum class MessageRole {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,
}

/** 消息生产者信息。Session 会原样持久化它以支持重放和诊断。 */
@Serializable
sealed interface MessageSource

/** 直接来自用户的输入。 */
@Serializable
@SerialName("user")
data object UserMessageSource : MessageSource

/** Harness Plugin 合成并注入的模型上下文。 */
@Serializable
@SerialName("plugin")
data class PluginMessageSource(
    val plugin: String,
) : MessageSource {
    init {
        require(plugin.isNotBlank()) {
            "Plugin message source must not be blank"
        }
    }
}

/** 模型生成消息的提供方、模型及可选私有重放状态。 */
@Serializable
@SerialName("model")
data class ModelMessageSource(
    val provider: String,
    val model: String,
    val replayState: JsonElement? = null,
) : MessageSource {
    init {
        require(provider.isNotBlank()) {
            "Model message provider must not be blank"
        }
        require(model.isNotBlank()) {
            "Model message model must not be blank"
        }
    }
}

/** 工具结果消息的来源，并携带与调用块一致的 CallId。 */
@Serializable
@SerialName("tool")
data class ToolMessageSource(
    val callId: CallId,
) : MessageSource

/**
 * 投递、Session 日志和模型请求共同使用的一份消息表示。
 *
 * 调用方应通过 createUserMessage、createAssistantMessage 或
 * createToolResultMessage 创建新消息，从创建时就获得稳定身份。
 */
@Serializable
data class Message(
    val id: MessageId,
    val role: MessageRole,
    val content: List<ContentBlock>,
    val source: MessageSource,
) {
    init {
        when (source) {
            is ModelMessageSource ->
                require(role == MessageRole.ASSISTANT) {
                    "Model message source requires assistant role"
                }

            is ToolMessageSource -> {
                require(role == MessageRole.USER) {
                    "Tool message source requires user role"
                }
                val result = content.singleOrNull() as? ToolResultBlock
                require(result?.toolCallId == source.callId) {
                    "Tool message must contain one matching tool-result block"
                }
            }

            UserMessageSource ->
                require(role == MessageRole.USER) {
                    "User message source requires user role"
                }

            is PluginMessageSource -> Unit
        }
    }
}

/** 创建一条具有新身份的 user 消息，并复制内容列表。 */
fun createUserMessage(
    content: List<ContentBlock>,
    source: MessageSource = UserMessageSource,
): Message {
    require(source !is ModelMessageSource && source !is ToolMessageSource) {
        "User message requires a user or plugin source"
    }

    return Message(
        id = newMessageId(),
        role = MessageRole.USER,
        content = content.toList(),
        source = source,
    )
}

/** 创建一条具有新身份和模型来源的 assistant 消息。 */
fun createAssistantMessage(
    content: List<ContentBlock>,
    provider: String,
    model: String,
    replayState: JsonElement? = null,
): Message =
    Message(
        id = newMessageId(),
        role = MessageRole.ASSISTANT,
        content = content.toList(),
        source = ModelMessageSource(
            provider = provider,
            model = model,
            replayState = replayState,
        ),
    )

/** 创建一条与 CallId 严格关联的工具结果消息。 */
fun createToolResultMessage(
    callId: CallId,
    content: List<ContentBlock>,
    isError: Boolean,
): Message =
    Message(
        id = newMessageId(),
        role = MessageRole.USER,
        content = listOf(
            ToolResultBlock(
                toolCallId = callId,
                content = content.toList(),
                isError = isError,
            )
        ),
        source = ToolMessageSource(callId),
    )

/** 导入已有身份的消息，并复制所有列表节点。 */
fun copyMessage(message: Message): Message =
    message.copy(
        content = message.content.map(::copyContentBlock),
    )

/** 替换消息正文时清除旧的 Provider 私有 replay state，避免正文与元数据失配。 */
fun Message.replaceContent(content: List<ContentBlock>): Message =
    copy(
        content = content.map(::copyContentBlock),
        source = source.withoutReplayState(),
    )

/** 显式删除模型消息上的 Provider 私有 replay state；普通消息保持不变。 */
fun Message.withoutReplayState(): Message =
    when (val messageSource = source) {
        is ModelMessageSource -> copy(source = messageSource.copy(replayState = null))
        else -> this
    }

private fun MessageSource.withoutReplayState(): MessageSource =
    when (this) {
        is ModelMessageSource -> copy(replayState = null)
        else -> this
    }

private fun copyContentBlock(block: ContentBlock): ContentBlock =
    when (block) {
        is TextBlock -> block.copy()
        is ReasoningBlock -> block.copy()
        is ToolCallBlock -> block.copy()
        is ToolResultBlock ->
            block.copy(
                content = block.content.map(::copyContentBlock),
            )
    }

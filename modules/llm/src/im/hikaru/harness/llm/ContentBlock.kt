package im.hikaru.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 模型可见内容块。只有所有生产方和消费方都支持的类型才应进入这里。 */
@Serializable
sealed interface ContentBlock

/** 面向用户展示的普通文本。 */
@Serializable
@SerialName("text")
data class TextBlock(
    val text: String,
) : ContentBlock

/** 与普通文本分离的模型推理内容。 */
@Serializable
@SerialName("reasoning")
data class ReasoningBlock(
    val text: String,
) : ContentBlock

/** 模型请求执行一个工具。arguments 保留模型产生的原始 JSON 字符串。 */
@Serializable
@SerialName("tool-call")
data class ToolCallBlock(
    val id: CallId,
    val name: String,
    val arguments: String,
) : ContentBlock

/** 一次工具调用返回给模型的结果。 */
@Serializable
@SerialName("tool-result")
data class ToolResultBlock(
    val toolCallId: CallId,
    val content: List<ContentBlock>,
    val isError: Boolean = false,
) : ContentBlock

/** 返回内容块的稳定类型名，供流式 block-start 使用。 */
val ContentBlock.type: String
    get() =
        when (this) {
            is TextBlock -> "text"
            is ReasoningBlock -> "reasoning"
            is ToolCallBlock -> "tool-call"
            is ToolResultBlock -> "tool-result"
        }

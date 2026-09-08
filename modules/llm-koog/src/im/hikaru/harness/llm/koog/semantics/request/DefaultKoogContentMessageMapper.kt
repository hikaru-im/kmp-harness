package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolResultBlock
import im.hikaru.harness.llm.type

/** Provider-neutral mapping for text, tool-call, and tool-result history. */
internal class DefaultKoogContentMessageMapper(
    private val textMapper: KoogMessageMapper = TextOnlyKoogMessageMapper(),
) : KoogMessageMapper {
    override fun map(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage {
        val hasToolCall = message.content.any { block -> block is ToolCallBlock }
        val hasToolResult = message.content.any { block -> block is ToolResultBlock }
        return when {
            hasToolResult -> mapToolResult(message, context)
            hasToolCall -> mapAssistantToolCalls(message)
            else -> textMapper.map(message, context)
        }
    }

    private fun mapAssistantToolCalls(message: HarnessMessage): KoogMessage.Assistant {
        if (message.role != MessageRole.ASSISTANT) {
            invalidToolHistory("Tool calls must belong to assistant messages")
        }
        val callIds = mutableSetOf<String>()
        val parts: List<MessagePart.ResponsePart> =
            message.content.map { block ->
                when (block) {
                    is TextBlock -> MessagePart.Text(block.text)
                    is ToolCallBlock -> {
                        if (block.name.isBlank()) {
                            invalidToolHistory(
                                "Tool call '${block.id.value}' has a blank name"
                            )
                        }
                        if (!callIds.add(block.id.value)) {
                            invalidToolHistory(
                                "Tool call '${block.id.value}' appears more than once"
                            )
                        }
                        MessagePart.Tool.Call(
                            id = block.id.value,
                            tool = block.name,
                            args = block.arguments,
                        )
                    }
                    else -> unsupportedToolMessageContent(block.type)
                }
            }
        return KoogMessage.Assistant(
            parts = parts,
            metaInfo = ResponseMetaInfo.Empty,
            id = message.id.value,
        )
    }

    private fun mapToolResult(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage.User {
        if (message.role != MessageRole.USER) {
            invalidToolHistory("Tool results must belong to user messages")
        }
        val result =
            message.content.singleOrNull() as? ToolResultBlock
                ?: invalidToolHistory(
                    "Tool result messages must contain exactly one tool-result block"
                )
        val toolName = context.requireToolName(result.toolCallId)
        val resultParts: List<MessagePart.ContentPart> =
            result.content.map { block ->
                when (block) {
                    is TextBlock -> MessagePart.Text(block.text)
                    else -> unsupportedToolMessageContent(block.type)
                }
            }
        return KoogMessage.User(
            parts =
                listOf(
                    MessagePart.Tool.Result(
                        id = result.toolCallId.value,
                        tool = toolName,
                        parts = resultParts,
                        isError = result.isError,
                    )
                ),
            metaInfo = RequestMetaInfo.Empty,
            id = message.id.value,
        )
    }
}

private fun invalidToolHistory(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.INVALID_TOOL_HISTORY,
    )

private fun unsupportedToolMessageContent(type: String): Nothing =
    throw LlmException(
        message = "Koog tool message mapping does not support $type",
        code = KoogLlmErrorCode.UNSUPPORTED_CONTENT,
    )

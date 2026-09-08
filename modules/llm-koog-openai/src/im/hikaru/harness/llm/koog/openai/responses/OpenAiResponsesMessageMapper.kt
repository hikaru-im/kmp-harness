package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolResultBlock
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogMessageMapper
import im.hikaru.harness.llm.koog.KoogMessageMappingContext

/** Maps durable Responses history before the replay restorer overlays private metadata. */
public class OpenAiResponsesMessageMapper : KoogMessageMapper {
    override fun map(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage =
        when {
            message.content.any { it is ToolResultBlock } -> mapToolResult(message, context)
            message.content.any { it is ToolCallBlock } -> mapAssistant(message)
            else -> mapSimple(message)
        }

    private fun mapSimple(message: HarnessMessage): KoogMessage =
        when (message.role) {
            MessageRole.USER ->
                KoogMessage.User(
                    parts =
                        message.content.map { block ->
                            when (block) {
                                is TextBlock -> MessagePart.Text(block.text)
                                else ->
                                    invalidResponsesReplay(
                                        "OpenAI Responses user content contains an unsupported block"
                                    )
                            }
                        },
                    metaInfo = RequestMetaInfo.Empty,
                    id = message.id.value,
                )

            MessageRole.ASSISTANT ->
                KoogMessage.Assistant(
                    parts =
                        message.content.map { block ->
                            when (block) {
                                is TextBlock -> MessagePart.Text(block.text)
                                is ReasoningBlock -> MessagePart.Reasoning(block.text)
                                else ->
                                    invalidResponsesReplay(
                                        "OpenAI Responses assistant content contains an unsupported block"
                                    )
                            }
                        },
                    metaInfo = ResponseMetaInfo.Empty,
                    id = message.id.value,
                )
        }

    private fun mapAssistant(message: HarnessMessage): KoogMessage.Assistant {
        if (message.role != MessageRole.ASSISTANT) {
            invalidResponsesReplay(
                "OpenAI Responses tool calls must belong to assistant messages"
            )
        }
        return KoogMessage.Assistant(
            parts =
                message.content.map { block ->
                    when (block) {
                        is TextBlock -> MessagePart.Text(block.text)
                        is ReasoningBlock -> MessagePart.Reasoning(block.text)
                        is ToolCallBlock ->
                            MessagePart.Tool.Call(
                                id = block.id.value,
                                tool = block.name,
                                args = block.arguments,
                            )
                        else ->
                            invalidResponsesReplay(
                                "OpenAI Responses assistant content contains an unsupported block"
                            )
                    }
                },
            metaInfo = ResponseMetaInfo.Empty,
            id = message.id.value,
        )
    }

    private fun mapToolResult(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage.User {
        if (message.role != MessageRole.USER) {
            invalidResponsesReplay(
                "OpenAI Responses tool results must belong to user messages"
            )
        }
        val result =
            message.content.singleOrNull() as? ToolResultBlock
                ?: invalidResponsesReplay(
                    "OpenAI Responses tool result must contain exactly one result block"
                )
        if (result.content.any { block -> block !is TextBlock }) {
            unsupportedContent(
                "OpenAI Responses tool result contains a non-text block"
            )
        }
        return KoogMessage.User(
            part =
                MessagePart.Tool.Result(
                    id = result.toolCallId.value,
                    tool = context.requireToolName(result.toolCallId),
                    output =
                        result.content
                            .filterIsInstance<TextBlock>()
                            .joinToString("") { it.text },
                    isError = result.isError,
                ),
            metaInfo = RequestMetaInfo.Empty,
            id = message.id.value,
        )
    }

    private fun unsupportedContent(message: String): Nothing =
        throw LlmException(
            message = message,
            code = KoogLlmErrorCode.UNSUPPORTED_CONTENT,
        )
}

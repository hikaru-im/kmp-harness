package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.type

/** Provider-neutral mapping for messages that contain only text blocks. */
internal class TextOnlyKoogMessageMapper : KoogMessageMapper {
    override fun map(
        message: HarnessMessage,
        @Suppress("UNUSED_PARAMETER")
        context: KoogMessageMappingContext,
    ): KoogMessage {
        val parts =
            message.content.map { block ->
                if (block !is TextBlock) {
                    throw LlmException(
                        message = "Koog text mapping does not support ${block.type}",
                        code = KoogLlmErrorCode.UNSUPPORTED_CONTENT,
                    )
                }
                MessagePart.Text(block.text)
            }

        return when (message.role) {
            MessageRole.USER ->
                KoogMessage.User(
                    parts = parts,
                    metaInfo = RequestMetaInfo.Empty,
                    id = message.id.value,
                )

            MessageRole.ASSISTANT ->
                KoogMessage.Assistant(
                    parts = parts,
                    metaInfo = ResponseMetaInfo.Empty,
                    id = message.id.value,
                )
        }
    }
}

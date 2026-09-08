package im.hikaru.harness.llm.koog.openai.chat

import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.koog.KoogFinishReasonMapper
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogStreamContext

/** OpenAI Chat Completions 的已验证 finish reason 子集。 */
public class OpenAiChatFinishReasonMapper : KoogFinishReasonMapper {
    override fun map(
        reason: String?,
        context: KoogStreamContext,
    ): FinishReason =
        when (reason) {
            "stop" -> StopFinishReason
            "length" -> MaxTokensFinishReason
            "tool_calls" -> ToolCallsFinishReason
            else ->
                throw LlmException(
                    message = "Unsupported OpenAI Chat Completions finish reason '$reason'",
                    code = KoogLlmErrorCode.UNSUPPORTED_FINISH_REASON,
                )
        }
}

package im.hikaru.harness.llm.koog.openai.responses

import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.koog.KoogFinishReasonMapper
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogStreamContext

/** OpenAI Responses API 的完成状态映射。Koog client 对 response.completed 使用 null reason。 */
public class OpenAiResponsesFinishReasonMapper : KoogFinishReasonMapper {
    override fun map(
        reason: String?,
        context: KoogStreamContext,
    ): FinishReason =
        when (reason) {
            null, "completed", "stop" -> StopFinishReason
            "tool_calls" -> ToolCallsFinishReason
            "length", "max_output_tokens" -> MaxTokensFinishReason
            else ->
                throw LlmException(
                    message = "Unsupported OpenAI Responses finish reason '$reason'",
                    code = KoogLlmErrorCode.UNSUPPORTED_FINISH_REASON,
                )
        }
}

package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.StopFinishReason

/** Koog finish reason 字符串到 Harness FinishReason 的扩展点。 */
fun interface KoogFinishReasonMapper {
    fun map(
        reason: String?,
        context: KoogStreamContext,
    ): FinishReason
}

/** 文本流阶段只承认已经验证过的 stop 终止原因。 */
class StopOnlyKoogFinishReasonMapper : KoogFinishReasonMapper {
    override fun map(
        reason: String?,
        context: KoogStreamContext,
    ): FinishReason =
        when (reason) {
            "stop" -> StopFinishReason
            else ->
                throw LlmException(
                    message = "Unsupported Koog finish reason '$reason'",
                    code = KoogLlmErrorCode.UNSUPPORTED_FINISH_REASON,
                )
        }
}

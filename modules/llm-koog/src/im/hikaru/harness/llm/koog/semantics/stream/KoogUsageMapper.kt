package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.ResponseMetaInfo
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.TokenUsage

/** Koog response metadata 到 Harness token usage 的扩展点。 */
fun interface KoogUsageMapper {
    fun map(
        metaInfo: ResponseMetaInfo,
        context: KoogStreamContext,
    ): TokenUsage?
}

/**
 * Koog 标准 input/output 计数的 provider-neutral 无损子集。
 *
 * total 只用于一致性校验，不能推导缺失字段；metadata 中的 Provider 私有计数不会被猜测。
 */
class DefaultKoogUsageMapper : KoogUsageMapper {
    override fun map(
        metaInfo: ResponseMetaInfo,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): TokenUsage? {
        val total = metaInfo.totalTokensCount
        val input = metaInfo.inputTokensCount
        val output = metaInfo.outputTokensCount
        if (total == null && input == null && output == null) {
            return null
        }

        if (input == null || output == null) {
            invalidUsage("Koog usage requires both input and output token counts")
        }
        if (input < 0 || output < 0 || (total != null && total < 0)) {
            invalidUsage("Koog token counts must not be negative")
        }

        val inputLong = input.toLong()
        val outputLong = output.toLong()
        if (total != null && total.toLong() != inputLong + outputLong) {
            invalidUsage("Koog total token count does not equal input plus output")
        }

        return TokenUsage(
            inputTokens = inputLong,
            outputTokens = outputLong,
        )
    }
}

private fun invalidUsage(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.INVALID_USAGE,
    )

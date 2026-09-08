package im.hikaru.harness.llm.koog

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException

/**
 * 将 Harness 调用级参数转换成 Koog 参数。
 *
 * 具体 Provider 可以依据已经解析好的 [model] 返回自己的 LLMParams 子类；不得静默丢弃
 * Provider 不支持的请求字段。
 */
fun interface KoogOptionMapper {
    fun map(
        options: GenerateOptions,
        model: LLModel,
    ): LLMParams

    fun map(
        options: GenerateOptions,
        model: LLModel,
        context: KoogStreamContext,
    ): LLMParams = map(options, model)
}

/** 当前只支持 Koog 通用的采样字段。 */
class BasicKoogOptionMapper : KoogOptionMapper {
    override fun map(
        options: GenerateOptions,
        @Suppress("UNUSED_PARAMETER")
        model: LLModel,
    ): LLMParams {
        if (options.stop != null) {
            unsupportedOption("Koog generic streaming does not support stop sequences")
        }
        if (options.reasoningEffort != null) {
            unsupportedOption("Koog generic streaming does not support reasoning effort")
        }
        options.temperature?.let { temperature ->
            if (temperature !in 0.0..2.0) {
                unsupportedOption(
                    "Koog temperature must be between 0.0 and 2.0: $temperature"
                )
            }
        }

        return LLMParams(
            temperature = options.temperature,
            maxTokens = options.maxTokens?.toKoogMaxTokens(),
        )
    }
}

private fun Long.toKoogMaxTokens(): Int {
    if (this > Int.MAX_VALUE) {
        unsupportedOption("Koog maxTokens cannot exceed ${Int.MAX_VALUE}: $this")
    }
    return toInt()
}

private fun unsupportedOption(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.UNSUPPORTED_OPTION,
    )

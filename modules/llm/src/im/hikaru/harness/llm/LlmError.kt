package im.hikaru.harness.llm

/** LLM 模块使用的稳定错误码。调用方必须按 code 分支，不能解析 message。 */
object LlmErrorCode {
    const val NO_ADAPTER = "NO_ADAPTER"
    const val DUPLICATE_ADAPTER = "DUPLICATE_ADAPTER"
    const val INVALID_ADAPTER = "INVALID_ADAPTER"
    const val DUPLICATE_DIRECTORY = "DUPLICATE_DIRECTORY"
    const val INVALID_DIRECTORY = "INVALID_DIRECTORY"
    const val REGISTRATION_DISPOSED = "REGISTRATION_DISPOSED"
    const val LLM_DISPOSED = "LLM_DISPOSED"
    const val INVALID_MODEL_INFO = "INVALID_MODEL_INFO"
    const val INVALID_MODEL_CONTEXT = "INVALID_MODEL_CONTEXT"
    const val INVALID_MODEL_MAX_TOKENS = "INVALID_MODEL_MAX_TOKENS"
    const val INVALID_MODEL_REASONING = "INVALID_MODEL_REASONING"
    const val UNSUPPORTED_REASONING_EFFORT = "UNSUPPORTED_REASONING_EFFORT"
    const val INVALID_PREPARED_CALL = "INVALID_PREPARED_CALL"
    const val ABORTED = "ABORTED"
    const val UNKNOWN = "UNKNOWN"

    const val CONTEXT_WINDOW_EXCEEDED = "CONTEXT_WINDOW_EXCEEDED"
    const val QUOTA_EXCEEDED = "QUOTA"
    const val EMPTY_RESPONSE = "EMPTY_RESPONSE"
    const val INVALID_CREDENTIAL = "INVALID_CREDENTIAL"
}

/** 带稳定机器错误码和可序列化 failure 的 LLM 异常。 */
class LlmException(
    message: String,
    val code: String,
    val status: Int? = null,
    val providerRetryAfterMs: Long? = null,
    val requestId: ProviderRequestId? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    init {
        require(message.isNotBlank()) {
            "LlmException message must not be blank"
        }
        require(code.isNotBlank()) {
            "LlmException code must not be blank"
        }
        require(status == null || status in 100..599) {
            "LlmException status must be between 100 and 599"
        }
        require(providerRetryAfterMs == null || providerRetryAfterMs > 0L) {
            "LlmException provider retry delay must be positive"
        }
    }

    val failure: LlmFailure =
        LlmFailure(
            message = message,
            code = code,
            status = status,
            providerRetryAfterMs = providerRetryAfterMs,
            requestId = requestId,
        )
}

/** 将适配器抛出的异常转换成可进入 finish chunk 的提供方无关事实。 */
fun normalizeLlmFailure(error: Throwable): LlmFailure =
    if (error is LlmException) {
        error.failure
    } else {
        LlmFailure(
            message = error.message?.takeIf(String::isNotBlank)
                ?: "LLM adapter failed",
            code = LlmErrorCode.UNKNOWN,
        )
    }

/** 识别常见 OpenAI-compatible 上下文窗口溢出描述。 */
fun isContextWindowExceededError(detail: String): Boolean {
    val text = detail.lowercase()
    return "context_length_exceeded" in text ||
        "context-window-overflow" in text ||
        Regex("maximum\\s+(allowed\\s+|supported\\s+)?context\\s+(length|window)")
            .containsMatchIn(text) ||
        Regex("(input|prompt|request|messages?).{0,40}(exceeds?|overflow|too (long|large)).{0,40}(model.{0,8})?context")
            .containsMatchIn(text) ||
        Regex("(input|prompt|request).{0,20}too (long|large) for (this|the) model")
            .containsMatchIn(text)
}

/** 区分余额或配额耗尽与暂时性的请求速率限制。 */
fun isQuotaExceededError(detail: String): Boolean {
    val text = detail.lowercase().replace('_', ' ').replace('-', ' ')
    return Regex("insufficient\\s+(quota|balance|credits?)").containsMatchIn(text) ||
        Regex("(quota|usage limit)\\s+(exceeded|exhausted|reached)").containsMatchIn(text) ||
        Regex("exceed(ed|s)?\\s+(your\\s+|the\\s+)?(current\\s+)?quota").containsMatchIn(text) ||
        Regex("(balance|credits?)\\s+(exhausted|depleted)").containsMatchIn(text) ||
        Regex("out of (credits?|budget)").containsMatchIn(text)
}

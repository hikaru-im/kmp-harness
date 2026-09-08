package im.hikaru.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_MAX_RETRIES = 2
private const val DEFAULT_INITIAL_DELAY_MS = 500L
private const val DEFAULT_MAX_DELAY_MS = 10_000L
private const val DEFAULT_JITTER_RATIO = 0.1

/**
 * 提供方注册时捕获的重试策略。
 *
 * 本模块只拥有数据约定；真正执行退避和重试的是后续 llm-retry Plugin。
 */
@Serializable
sealed interface RetryPolicy {
    val initialDelayMs: Long
    val maxDelayMs: Long
    val jitterRatio: Double
}

/** 对指定暂时性错误执行有界重试。 */
@Serializable
@SerialName("normal")
data class NormalRetryPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryableCodes: List<String> = defaultRetryableCodes,
    override val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    override val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    override val jitterRatio: Double = DEFAULT_JITTER_RATIO,
) : RetryPolicy {
    init {
        validateRetryBackoff(initialDelayMs, maxDelayMs, jitterRatio)
        require(maxRetries >= 0) {
            "Max retries must not be negative"
        }
        require(retryableCodes.isNotEmpty()) {
            "Retryable codes must not be empty"
        }
        require(retryableCodes.all(String::isNotBlank)) {
            "Retryable codes must not contain blank values"
        }
        require(retryableCodes.distinct().size == retryableCodes.size) {
            "Retryable codes must not contain duplicates"
        }
    }
}

/** 对每个模型请求失败持续重试，直到成功、取消或 Plugin dispose。 */
@Serializable
@SerialName("always")
data class AlwaysRetryPolicy(
    override val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    override val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    override val jitterRatio: Double = DEFAULT_JITTER_RATIO,
) : RetryPolicy {
    init {
        validateRetryBackoff(initialDelayMs, maxDelayMs, jitterRatio)
    }
}

/** DSH normal mode 的默认可重试错误集合。 */
val defaultRetryableCodes: List<String> =
    listOf(
        LlmErrorCode.EMPTY_RESPONSE,
        "RATE_LIMIT",
        "SERVER",
        "TIMEOUT",
        "TRANSPORT",
    )

/** 默认提供方策略：首次请求失败后最多再尝试两次。 */
fun defaultRetryPolicy(): RetryPolicy =
    NormalRetryPolicy()

private fun validateRetryBackoff(
    initialDelayMs: Long,
    maxDelayMs: Long,
    jitterRatio: Double,
) {
    require(initialDelayMs > 0L) {
        "Initial retry delay must be positive"
    }
    require(maxDelayMs >= initialDelayMs) {
        "Maximum retry delay must not be smaller than initial delay"
    }
    require(jitterRatio.isFinite() && jitterRatio in 0.0..1.0) {
        "Retry jitter ratio must be between 0 and 1"
    }
}

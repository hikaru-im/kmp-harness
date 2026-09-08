package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.normalizeLlmFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** 不携带消息正文或凭据的 Provider 错误分类上下文。 */
data class KoogFailureContext(
    val provider: String,
    val model: String,
    val api: String = provider,
) {
    init {
        require(provider.isNotBlank()) {
            "Koog failure provider must not be blank"
        }
        require(model.isNotBlank()) {
            "Koog failure model must not be blank"
        }
        require(api.isNotBlank()) {
            "Koog failure api must not be blank"
        }
    }
}

/**
 * 识别具体 Provider/HTTP client 异常。
 *
 * 返回 null 表示不认识，由通用层使用 UNKNOWN fallback；实现不能处理协程取消或覆盖已有
 * LlmException。
 */
fun interface KoogProviderFailureClassifier {
    fun classify(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure?
}

/** 默认不猜测任何 Provider 异常语义。 */
object UnclassifiedKoogProviderFailures : KoogProviderFailureClassifier {
    override fun classify(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure? = null
}

/** Provider/HTTP 异常到 Harness LlmFailure 的内部编排边界。 */
internal fun interface KoogFailureMapper {
    fun map(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure
}

/** 保留取消和已有稳定错误，再尝试 Provider 分类，最后回退到通用 UNKNOWN。 */
internal class DefaultKoogFailureMapper(
    private val providerClassifier: KoogProviderFailureClassifier =
        UnclassifiedKoogProviderFailures,
) : KoogFailureMapper {
    override fun map(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure {
        if (error is CancellationException) {
            throw error
        }
        if (error is im.hikaru.harness.llm.LlmException) {
            return error.failure
        }
        providerClassifier.classify(error, context)?.let { failure ->
            return failure
        }
        return normalizeLlmFailure(error)
    }
}

/** 只映射 Flow 上游失败；下游 collector 异常由 Flow catch 的透明性保证原样传播。 */
internal fun <T> Flow<T>.mapKoogFailures(
    failureMapper: KoogFailureMapper,
    context: KoogFailureContext,
): Flow<T> =
    catch { error ->
        val failure = failureMapper.map(error, context)
        if (error is LlmException && error.failure == failure) {
            throw error
        }
        throw LlmException(
            message = failure.message,
            code = failure.code,
            status = failure.status,
            providerRetryAfterMs = failure.providerRetryAfterMs,
            requestId = failure.requestId,
            cause = error,
        )
    }

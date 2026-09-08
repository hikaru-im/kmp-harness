package im.hikaru.harness.llm.koog.openai.failure

import ai.koog.http.client.KoogHttpClientException
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.ProviderRequestId
import im.hikaru.harness.llm.isContextWindowExceededError
import im.hikaru.harness.llm.isQuotaExceededError
import im.hikaru.harness.llm.koog.KoogFailureContext
import im.hikaru.harness.llm.koog.KoogProviderFailureClassifier
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenAI HTTP/client 错误分类。
 *
 * Koog Chat client 会把 HTTP 异常包在 `LLMClientException` 中，所以这里沿 cause 链查找
 * `KoogHttpClientException` 的结构化 status/body；不会从本地化异常 message 猜测类别。
 */
public class OpenAiFailureClassifier : KoogProviderFailureClassifier {
    private val json = Json { ignoreUnknownKeys = true }

    override fun classify(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure? {
        if (
            context.api != OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID &&
                context.api != OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID
        ) {
            return null
        }

        val http = error.findCause<KoogHttpClientException>()
        val details = http?.let(::parseDetails)
        val searchable = buildString {
            append(details?.code.orEmpty())
            append(' ')
            append(details?.type.orEmpty())
            append(' ')
            append(details?.message.orEmpty())
            append(' ')
            append(http?.errorBody.orEmpty())
        }
        val status = http?.statusCode

        if (isContextWindowExceededError(searchable)) {
            return failure(
                code = LlmErrorCode.CONTEXT_WINDOW_EXCEEDED,
                status = status,
                message = details?.message ?: "OpenAI context window exceeded",
            )
        }
        if (isQuotaExceededError(searchable)) {
            return failure(
                code = LlmErrorCode.QUOTA_EXCEEDED,
                status = status,
                message = details?.message ?: "OpenAI quota exceeded",
            )
        }

        val normalizedCode = details?.code?.lowercase()
        val normalizedMessage = details?.message?.lowercase().orEmpty()
        if (
            normalizedCode in setOf("invalid_api_key", "authentication_error", "unauthorized") ||
                normalizedMessage.contains("incorrect api key")
        ) {
            return failure(
                code = LlmErrorCode.INVALID_CREDENTIAL,
                status = status,
                message = details?.message ?: "OpenAI credential rejected",
            )
        }

        when {
            status == 429 ->
                return failure(
                    code = "RATE_LIMIT",
                    status = status,
                    message = details?.message ?: "OpenAI rate limit reached",
                )

            status == 408 || status == 504 ->
                return failure(
                    code = "TIMEOUT",
                    status = status,
                    message = details?.message ?: "OpenAI request timed out",
                )

            status in 500..599 ->
                return failure(
                    code = "SERVER",
                    status = status,
                    message = details?.message ?: "OpenAI server error",
                )
        }

        val causes = error.causeChain().drop(1)
        if (causes.any { it is SocketTimeoutException }) {
            return failure("TIMEOUT", null, "OpenAI request timed out")
        }
        if (causes.any { it is IOException }) {
            return failure("TRANSPORT", null, "OpenAI transport request failed")
        }

        return null
    }

    private fun failure(
        code: String,
        status: Int?,
        message: String,
    ): LlmFailure =
        LlmFailure(
            message = message.trim().ifBlank { "OpenAI request failed" },
            code = code,
            status = status,
            // Koog 1.1.1 drops response headers; do not invent Retry-After/request id.
            requestId = null,
        )

    private fun parseDetails(error: KoogHttpClientException): ErrorDetails? {
        val body = error.errorBody ?: return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val payload = root["error"]?.jsonObject ?: root
            ErrorDetails(
                code = payload["code"]?.jsonPrimitive?.content,
                type = payload["type"]?.jsonPrimitive?.content,
                message = payload["message"]?.jsonPrimitive?.content,
            )
        }.getOrNull()
    }

    private data class ErrorDetails(
        val code: String?,
        val type: String?,
        val message: String?,
    )
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? =
    causeChain().filterIsInstance<T>().firstOrNull()

private fun Throwable.causeChain(): List<Throwable> {
    val result = mutableListOf<Throwable>()
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        result += current
        current = current.cause
    }
    return result
}

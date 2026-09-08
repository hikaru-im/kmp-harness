package im.hikaru.harness.llm.koog.openai.responses

import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.koog.KoogLlmErrorCode

/** Versioned provider-native state for OpenAI Responses history replay. */
public data class OpenAiResponsesReplayState(
    val kind: String,
    val version: Int,
    val provider: String,
    val model: String,
    val stopReason: String,
    val blocks: List<OpenAiResponsesReplayBlock>,
) {
    init {
        require(kind.isNotBlank()) { "OpenAI Responses replay kind must not be blank" }
        require(version > 0) { "OpenAI Responses replay version must be positive" }
        require(provider.isNotBlank()) { "OpenAI Responses replay provider must not be blank" }
        require(model.isNotBlank()) { "OpenAI Responses replay model must not be blank" }
        require(stopReason.isNotBlank()) { "OpenAI Responses replay stop reason must not be blank" }
        require(stopReason in OPENAI_RESPONSES_REPLAY_STOP_REASONS) {
            "OpenAI Responses replay stop reason is not successful: $stopReason"
        }
    }
}

/** Provider-private metadata for one durable Harness content block. */
public data class OpenAiResponsesReplayBlock(
    val index: Int,
    val type: String,
    val reasoningId: String? = null,
    val reasoningSummary: List<String> = emptyList(),
    val reasoningEncrypted: String? = null,
) {
    init {
        require(index >= 0) { "OpenAI Responses replay block index must not be negative" }
        require(type.isNotBlank()) { "OpenAI Responses replay block type must not be blank" }
        require(reasoningSummary.none(String::isBlank)) {
            "OpenAI Responses replay reasoning summary must not contain blanks"
        }
        require(reasoningId == null || reasoningId.isNotBlank()) {
            "OpenAI Responses replay reasoning id must not be blank"
        }
        require(reasoningEncrypted == null || reasoningEncrypted.isNotBlank()) {
            "OpenAI Responses replay encrypted reasoning must not be blank"
        }
    }
}

internal const val OPENAI_RESPONSES_REPLAY_KIND = "openai-responses"
internal const val OPENAI_RESPONSES_FOREIGN_REPLAY_KIND = "foreign"
internal const val OPENAI_RESPONSES_REPLAY_VERSION = 1
internal const val OPENAI_RESPONSES_ROUTE_ID =
    OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID
internal val OPENAI_RESPONSES_REPLAY_STOP_REASONS =
    setOf("stop", "tool-calls", "max-tokens")

internal fun invalidResponsesReplay(
    message: String,
    cause: Throwable? = null,
): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.INVALID_REPLAY_STATE,
        cause = cause,
    )

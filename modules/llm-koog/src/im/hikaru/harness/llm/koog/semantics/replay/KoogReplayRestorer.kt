package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.FinishReason
import kotlinx.serialization.json.JsonElement

/** 不含正文或凭据的 replay source/target 身份。 */
data class KoogReplayContext(
    val sourceProvider: String,
    val sourceModel: String,
    val targetProvider: String,
    val targetModel: String,
    val targetKoogProvider: String,
    val targetApi: String = targetProvider,
) {
    init {
        require(sourceProvider.isNotBlank()) {
            "Koog replay source provider must not be blank"
        }
        require(sourceModel.isNotBlank()) {
            "Koog replay source model must not be blank"
        }
        require(targetProvider.isNotBlank()) {
            "Koog replay target provider must not be blank"
        }
        require(targetModel.isNotBlank()) {
            "Koog replay target model must not be blank"
        }
        require(targetKoogProvider.isNotBlank()) {
            "Koog replay target client provider must not be blank"
        }
        require(targetApi.isNotBlank()) {
            "Koog replay target api must not be blank"
        }
    }
}

/**
 * 用版本化 Provider 私有 state 修复已经按 durable Harness 内容生成的 Koog assistant message。
 *
 * 实现必须验证 state 与 message source/content 一致；不能用 state 替换 durable 正文。
 */
fun interface KoogReplayRestorer {
    fun restore(
        message: HarnessMessage,
        base: KoogMessage.Assistant,
        context: KoogReplayContext,
    ): KoogMessage.Assistant
}

/**
 * Creates provider-private replay state after a stream has reached a successful End frame.
 *
 * A writer must never replace durable Harness content with provider state. Returning null means
 * this provider has no native replay state for the response.
 */
fun interface KoogReplayWriter {
    fun write(
        state: KoogStreamState,
        frame: ai.koog.prompt.streaming.StreamFrame.End,
        context: KoogStreamContext,
        finishReason: FinishReason,
    ): JsonElement?
}

/** Default writer for providers without a native replay codec. */
object NoopKoogReplayWriter : KoogReplayWriter {
    override fun write(
        state: KoogStreamState,
        frame: ai.koog.prompt.streaming.StreamFrame.End,
        context: KoogStreamContext,
        finishReason: FinishReason,
    ): JsonElement? = null
}

/** replay codec 未实现时显式失败，避免静默丢弃 Provider-native metadata。 */
object RejectingKoogReplayRestorer : KoogReplayRestorer {
    override fun restore(
        message: HarnessMessage,
        base: KoogMessage.Assistant,
        context: KoogReplayContext,
    ): KoogMessage.Assistant =
        throw LlmException(
            message =
                "Koog replay restoration is not implemented for " +
                    "${context.sourceProvider}/${context.sourceModel}",
            code = KoogLlmErrorCode.REPLAY_MAPPING_NOT_IMPLEMENTED,
        )
}

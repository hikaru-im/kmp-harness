package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message as HarnessMessage

/** Ensures a custom content mapper cannot silently discard Provider replay state. */
internal class ReplayAwareKoogMessageMapper(
    private val delegate: KoogMessageMapper,
    private val replayRestorer: KoogReplayRestorer,
) : KoogMessageMapper {
    override fun map(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage {
        val mapped = delegate.map(message, context)
        val replayContext = context.replayContext(message) ?: return mapped
        val assistant =
            mapped as? KoogMessage.Assistant
                ?: throw LlmException(
                    message = "Koog replay state belongs to a non-assistant message",
                    code = KoogLlmErrorCode.INVALID_REPLAY_STATE,
                )
        return replayRestorer.restore(
            message = message,
            base = assistant,
            context = replayContext,
        )
    }
}

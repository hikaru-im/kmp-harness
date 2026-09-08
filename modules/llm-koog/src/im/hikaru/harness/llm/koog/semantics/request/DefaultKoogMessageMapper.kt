package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import im.hikaru.harness.llm.Message as HarnessMessage

/** Combines the default content mapper with mandatory replay protection. */
internal class DefaultKoogMessageMapper(
    textMapper: KoogMessageMapper = TextOnlyKoogMessageMapper(),
    replayRestorer: KoogReplayRestorer = RejectingKoogReplayRestorer,
) : KoogMessageMapper {
    private val delegate =
        ReplayAwareKoogMessageMapper(
            delegate = DefaultKoogContentMessageMapper(textMapper),
            replayRestorer = replayRestorer,
        )

    override fun map(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage =
        delegate.map(message, context)
}

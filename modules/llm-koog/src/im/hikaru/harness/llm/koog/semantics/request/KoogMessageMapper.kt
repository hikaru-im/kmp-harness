package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import im.hikaru.harness.llm.Message as HarnessMessage

/** Maps one Harness message into one Koog message. */
fun interface KoogMessageMapper {
    fun map(
        message: HarnessMessage,
        context: KoogMessageMappingContext,
    ): KoogMessage
}

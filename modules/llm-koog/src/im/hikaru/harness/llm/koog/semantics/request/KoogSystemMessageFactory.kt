package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo

/** Creates Koog system messages without inventing timestamps or Provider metadata. */
internal object KoogSystemMessageFactory {
    fun create(text: String): KoogMessage.System =
        KoogMessage.System(
            parts = listOf(MessagePart.Text(text)),
            metaInfo = RequestMetaInfo.Empty,
        )
}

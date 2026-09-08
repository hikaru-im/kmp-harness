package im.hikaru.contracts.harness.relay

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 一条 Harness 下行事件的唯一标识。 */
@JvmInline
@Serializable
public value class EventId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Event id must not be blank"
        }
    }

    override fun toString(): String = value
}

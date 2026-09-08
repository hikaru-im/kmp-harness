package im.hikaru.contracts.harness.relay

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 一条有序 Harness 事件流的标识。 */
@JvmInline
@Serializable
public value class StreamId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Stream id must not be blank"
        }
    }

    override fun toString(): String = value
}

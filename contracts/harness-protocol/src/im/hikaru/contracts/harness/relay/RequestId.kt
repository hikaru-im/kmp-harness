package im.hikaru.contracts.harness.relay

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 一次 Harness Relay 请求的唯一标识。 */
@JvmInline
@Serializable
public value class RequestId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Request id must not be blank"
        }
    }

    override fun toString(): String = value
}

package im.hikaru.contracts.harness.protocol

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 稳定且可扩展的 Harness 能力标识。 */
@JvmInline
@Serializable
public value class CapabilityId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Capability id must not be blank"
        }
    }

    override fun toString(): String = value
}

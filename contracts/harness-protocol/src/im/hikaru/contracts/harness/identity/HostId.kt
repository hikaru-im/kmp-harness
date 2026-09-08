package im.hikaru.contracts.harness.identity

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** RuoYi Relay 中注册的 Harness Host 稳定标识。 */
@JvmInline
@Serializable
public value class HostId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Host id must not be blank"
        }
    }

    override fun toString(): String = value
}

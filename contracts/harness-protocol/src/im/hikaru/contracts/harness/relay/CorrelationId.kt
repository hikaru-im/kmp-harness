package im.hikaru.contracts.harness.relay

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 把多个 Relay 请求关联到同一个 Agent 业务操作。 */
@JvmInline
@Serializable
public value class CorrelationId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Correlation id must not be blank"
        }
    }

    override fun toString(): String = value
}

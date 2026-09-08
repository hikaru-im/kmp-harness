package im.hikaru.harness.agent

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Agent 与其 Session 共享稳定身份，但保留独立的业务类型。 */
@JvmInline
@Serializable
value class AgentId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "AgentId must not be blank" }
    }
}

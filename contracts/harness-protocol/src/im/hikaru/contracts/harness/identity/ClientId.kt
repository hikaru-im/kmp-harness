package im.hikaru.contracts.harness.identity

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Harness 客户端实例的稳定标识，不代表 RuoYi 用户身份。 */
@JvmInline
@Serializable
public value class ClientId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Client id must not be blank"
        }
    }

    override fun toString(): String = value
}

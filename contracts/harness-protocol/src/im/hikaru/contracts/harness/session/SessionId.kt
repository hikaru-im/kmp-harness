package im.hikaru.contracts.harness.session

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 一个 Host 内部的 Harness Session 标识；跨 Host 引用时必须同时携带 HostId。 */
@JvmInline
@Serializable
public value class SessionId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "Session id must not be blank"
        }
    }

    override fun toString(): String = value
}

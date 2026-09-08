package im.hikaru.contracts.harness.protocol

import kotlinx.serialization.Serializable

/** 与传输方式无关的 Harness 协议版本。 */
@Serializable
public data class ProtocolVersion(
    val major: Int,
    val minor: Int,
) {
    init {
        require(major >= 0) {
            "Protocol major version must not be negative"
        }
        require(minor >= 0) {
            "Protocol minor version must not be negative"
        }
    }

    public companion object {
        public val Current: ProtocolVersion =
            ProtocolVersion(
                major = 1,
                minor = 0,
            )
    }
}

package im.hikaru.harness.session

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class SessionSurfaceOperation {
    @SerialName("append")
    APPEND,

    @SerialName("replace")
    REPLACE,
}

open class SessionEventKey<T : Any>(
    val name: String,
    val serializer: KSerializer<T>,
    val ignorable: Boolean = false,
) {
    internal open val surfaceOperation: SessionSurfaceOperation? = null

    init {
        require(name.isNotBlank()) {
            "Session event name must not be blank"
        }
        require('/' in name && name.none(Char::isWhitespace)) {
            "Session event name must be a slash-separated stable name"
        }
    }
}

open class SurfaceSessionEventKey<T : Any>(
    name: String,
    serializer: KSerializer<T>,
    val operation: SessionSurfaceOperation = SessionSurfaceOperation.APPEND,
) : SessionEventKey<T>(
    name = name,
    serializer = serializer,
    ignorable = false,
) {
    final override val surfaceOperation: SessionSurfaceOperation = operation
}

@Serializable
data class SessionEventEnvelope(
    val type: String,
    val seq: Long,
    val time: Long,
    val data: JsonElement,
    val surfaceOp: SessionSurfaceOperation? = null,
    val sourceEventSeqs: List<Long>? = null,
    val ignorable: Boolean? = null,
)

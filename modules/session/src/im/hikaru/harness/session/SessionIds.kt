package im.hikaru.harness.session

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
@Serializable
value class SessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "SessionId must not be blank"
        }
    }
}

fun interface SessionIdFactory {
    fun create(): SessionId
}

object RandomSessionIdFactory : SessionIdFactory {
    @OptIn(ExperimentalUuidApi::class)
    override fun create(): SessionId =
        SessionId(Uuid.random().toString())
}

fun interface SessionClock {
    fun nowEpochMilliseconds(): Long
}

object SystemSessionClock : SessionClock {
    override fun nowEpochMilliseconds(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()
}

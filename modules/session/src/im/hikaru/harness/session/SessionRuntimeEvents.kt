package im.hikaru.harness.session

import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.event.ParallelEventKey

data class SessionNotice(
    val session: Session,
)

data class SessionEventNotice(
    val session: Session,
    val event: SessionEventEnvelope,
)

object SessionEvents {
    object Created : EventKey<SessionNotice>("session/created")
    object Appended : EventKey<SessionEventNotice>("session/event")
    object Disposed : EventKey<SessionNotice>("session/disposed")
    object Flush : ParallelEventKey<SessionNotice>("session/flush")
}

fun interface SessionObserverFailureHandler {
    fun onFailure(error: Throwable)
}

internal object IgnoreSessionObserverFailures : SessionObserverFailureHandler {
    override fun onFailure(error: Throwable) = Unit
}

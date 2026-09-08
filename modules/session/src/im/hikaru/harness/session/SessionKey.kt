package im.hikaru.harness.session

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.service.ServiceKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object SessionKey : ServiceKey<SessionStore>("session")

val Context.sessions: SessionStore
    get() = require(SessionKey)

suspend fun Context.createSession(
    id: SessionId? = null,
    options: CreateSessionOptions = CreateSessionOptions(),
): Session {
    val store = sessions
    val session = store.prepare(id, options)
    val handle = store.enter(session)

    try {
        effect(handle)
        store.announce(session)
        currentCoroutineContext().ensureActive()
    } catch (error: Throwable) {
        handle.rollbackAndRethrow(error)
    }

    return session
}

package im.hikaru.harness.client.app

import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.harness.client.connection.HistoryRecovery
import im.hikaru.harness.client.connection.SessionId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** True when this forwarded event belongs to the Session the shell is currently showing. */
public fun RelayEvent.belongsTo(session: SessionId): Boolean = streamId.value == session.value

private fun HistoryRecovery.belongsTo(session: SessionId): Boolean = streamId.value == session.value

/**
 * Owns the client-side live stream of exactly one Session.
 *
 * Selecting a Session is what makes this client subscribe, so the Host only ever publishes the stream
 * a client actually asked for. Every forwarded event and every gap recovery refreshes the screen from
 * the authoritative history instead of trusting the event body, and the subscription is always released
 * when the selection ends — including when the surrounding coroutine is cancelled by switching Host,
 * cancelling the Session, or logging out.
 *
 * This type stays Compose-free so its lifecycle rules are testable without a running Host.
 */
public class SessionStreamDriver(
    private val events: Flow<RelayEvent>,
    private val recoveries: Flow<HistoryRecovery>,
    private val subscribe: suspend (SessionId) -> Unit,
    private val unsubscribe: suspend (SessionId) -> Unit,
    private val refresh: suspend (SessionId) -> Unit,
    private val onEvent: (RelayEvent) -> Unit = {},
) {
    /** Runs until cancelled, then releases the subscription even while cancellation is in flight. */
    public suspend fun run(session: SessionId) {
        subscribe(session)
        try {
            coroutineScope {
                launch {
                    events
                        .filter { it.belongsTo(session) }
                        .conflate()
                        .collect { event ->
                            onEvent(event)
                            refresh(session)
                        }
                }
                launch {
                    recoveries
                        .filter { it.belongsTo(session) }
                        .conflate()
                        .collect { refresh(session) }
                }
            }
        } finally {
            withContext(NonCancellable) { unsubscribe(session) }
        }
    }
}
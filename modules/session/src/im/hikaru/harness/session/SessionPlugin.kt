package im.hikaru.harness.session

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin

class SessionPlugin(
    private val idFactory: SessionIdFactory = RandomSessionIdFactory,
    private val clock: SessionClock = SystemSessionClock,
    private val observerFailureHandler: SessionObserverFailureHandler = IgnoreSessionObserverFailures,
) : SimplePlugin {
    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        // This child remains active while the owning Fiber stops its effects,
        // so lifecycle notifications can still be dispatched during uninstall.
        val eventContext = context.child()
        scope.add { eventContext.dispose() }
        val store =
            SessionStore(
                context = eventContext,
                idFactory = idFactory,
                clock = clock,
                observerFailureHandler = observerFailureHandler,
            )

        scope.add(
            context.provide(
                key = SessionKey,
                service = store,
            )
        )
        scope.add(store)
    }
}

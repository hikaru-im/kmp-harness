package im.hikaru.harness.timer

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TimerPlugin(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: TimerClock = MonotonicTimerClock,
    private val failureHandler: TimerFailureHandler =
        TimerFailureHandler { error -> throw error },
) : SimplePlugin {

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val service =
            TimerService(
                serviceScope = CoroutineScope(
                    SupervisorJob() + dispatcher
                ),
                failureHandler = failureHandler,
                clock = clock,
            )

        scope.add(
            context.provide(
                key = TimerKey,
                service = service,
            )
        )

        scope.add(service)
    }
}

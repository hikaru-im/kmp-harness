package im.hikaru.harness.logger

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin

/** Publishes [LoggerService] without changing kotlin-logging global configuration. */
object LoggerPlugin : SimplePlugin {

    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        scope.add(
            context.provide(
                key = LoggerKey,
                service = LoggerService(),
            )
        )
    }
}

package im.hikaru.harness.llm

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin

/** 安装 provider-neutral LlmRuntime 的 Runtime Plugin。 */
class LlmPlugin : SimplePlugin {
    override suspend fun apply(
        context: Context,
        scope: EffectScope,
    ) {
        val service = LlmRuntime(context)

        scope.add(
            context.provide(
                key = LlmKey,
                service = service,
            )
        )
        scope.add(service)
    }
}

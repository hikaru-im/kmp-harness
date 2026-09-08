package im.hikaru.harness.api.gateway.host

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin

/** 把代码定义的 [HostDescription] 发布为 Runtime Host API Service。 */
public object HostApiPlugin : Plugin<HostDescription> {

    override suspend fun apply(
        context: Context,
        config: HostDescription,
        scope: EffectScope,
    ) {
        scope.add(
            context.provide(
                key = HostApiKey,
                service = HostApiService { config },
            )
        )
    }
}

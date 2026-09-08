package im.hikaru.harness.include

import im.hikaru.harness.loader.LoaderFactoryKey
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.InjectSpec
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.service.ServiceKey

public object IncludeKey : ServiceKey<IncludeService>("include")

public data class IncludeConfig(
    val source: ConfigSource,
    val transforms: List<EntryTransform> = emptyList(),
)

/** Installs one isolated, child-loader-backed configuration subtree. */
public object IncludePlugin : Plugin<IncludeConfig> {
    override val inject =
        setOf(
            InjectSpec.required(LoaderFactoryKey),
        )

    override suspend fun apply(
        context: Context,
        config: IncludeConfig,
        scope: EffectScope,
    ) {
        val includeContext = context.isolate(IncludeKey)
        scope.add(
            Disposable {
                includeContext.dispose()
            }
        )

        val loader =
            context.require(LoaderFactoryKey).create(includeContext)
        val service =
            IncludeService(
                loader = loader,
                source = config.source,
                transforms = config.transforms,
            )
        scope.add(service)

        scope.add(
            includeContext.provide(
                key = IncludeKey,
                service = service,
            )
        )

        service.refresh()
    }
}

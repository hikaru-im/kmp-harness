package im.hikaru.harness.loader

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.service.ServiceKey

/**
 * Creates loaders that share one plugin registry but own independent snapshots.
 *
 * A loader reconciles the complete snapshot assigned to it. Components such as
 * include must therefore create a dedicated loader anchored at a child context
 * instead of reconciling through an application's root loader.
 */
public class LoaderFactory(
    private val runtime: Runtime,
    private val registry: Registry,
) {
    public fun create(context: Context = runtime.context): Loader =
        Loader(
            runtime = runtime,
            registry = registry,
            context = context,
        )
}

public object LoaderFactoryKey : ServiceKey<LoaderFactory>("loader.factory")

public suspend fun Runtime.provideLoaderFactory(registry: Registry): Disposable =
    provide(LoaderFactoryKey, LoaderFactory(this, registry))

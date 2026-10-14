package im.hikaru.harness.ui.slot

import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.Loader
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.Registry
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.runtime.service.ServiceKey

public val UiContributionKey: ServiceKey<UiContributionSink> =
    ServiceKey("ui-contribution-sink")

public class ClientHarnessHost private constructor(
    public val snapshot: UiSnapshot,
    private val runtime: Runtime,
    private val loader: Loader,
    private val catalogRegistration: Disposable,
    private val sinkRegistration: Disposable,
) {
    public suspend fun close() {
        var failure: Throwable? = null
        try {
            loader.dispose()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            sinkRegistration.dispose()
        } catch (error: Throwable) {
            failure = combine(failure, error)
        }
        try {
            runtime.context.dispose()
        } catch (error: Throwable) {
            failure = combine(failure, error)
        }
        try {
            catalogRegistration.dispose()
        } catch (error: Throwable) {
            failure = combine(failure, error)
        }
        failure?.let { throw it }
    }

    public companion object {
        public suspend fun start(
            catalog: PluginCatalog,
            entries: List<Entry>,
            schema: UiSchema,
        ): ClientHarnessHost {
            val runtime = Runtime()
            val registry = Registry()
            val loader = Loader(runtime, registry)
            val builder = UiContributionBuilder(schema)
            var catalogRegistration: Disposable? = null
            var sinkRegistration: Disposable? = null
            try {
                catalogRegistration = catalog.registerInto(registry)
                sinkRegistration = runtime.provide(UiContributionKey, builder)
                loader.reconcile(entries)
                entries.filterNot(Entry::disabled).forEach { entry ->
                    check(loader.fiber(entry.id)?.state == FiberState.Active) {
                        "Client UI entry '${entry.id}' did not become active"
                    }
                }
                return ClientHarnessHost(
                    snapshot = builder.freeze(),
                    runtime = runtime,
                    loader = loader,
                    catalogRegistration = checkNotNull(catalogRegistration),
                    sinkRegistration = checkNotNull(sinkRegistration),
                )
            } catch (error: Throwable) {
                try {
                    loader.dispose()
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                try {
                    sinkRegistration?.dispose()
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                try {
                    runtime.context.dispose()
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                try {
                    catalogRegistration?.dispose()
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) error.addSuppressed(cleanupError)
                }
                throw error
            }
        }
    }
}

private fun combine(
    current: Throwable?,
    next: Throwable,
): Throwable {
    if (current == null) return next
    if (current !== next) current.addSuppressed(next)
    return current
}

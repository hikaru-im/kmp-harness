package im.hikaru.harness.boot

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostApiPlugin
import im.hikaru.harness.api.gateway.host.registerHostApi
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.Loader
import im.hikaru.harness.loader.Registry
import im.hikaru.harness.loader.provideLoaderFactory
import im.hikaru.harness.loader.typedConfig
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 一个已经完成启动的本地 Harness Host。
 *
 * Host 持有 Runtime、Registry、Loader 和 API Gateway 的共同生命周期。
 */
public class HarnessHost private constructor(
    public val profile: DesktopProfile,
    public val runtime: Runtime,
    public val registry: Registry,
    public val loader: Loader,
    public val gateway: ApiGateway,
) {

    private val closeMutex = Mutex()
    private var closed = false

    /** 当前 Host 是否已经完成关闭。 */
    public val isClosed: Boolean
        get() = closed

    /**
     * 关闭整个 Host。
     *
     * Loader 先停止自己管理的 Fiber，随后根 Context 释放其余 Service、Endpoint 和 effects。
     * 重复关闭是安全的。
     */
    public suspend fun close() {
        withContext(NonCancellable) {
            closeMutex.withLock {
                if (closed) {
                    return@withLock
                }
                closed = true

                disposeHost(
                    loader = loader,
                    context = runtime.context,
                )?.let { error ->
                    throw error
                }
            }
        }
    }

    public companion object {

        private const val HostApiPluginName = "harness.host-api"
        private const val HostApiEntryId = "harness.host-api"

        /**
         * 按 Profile 事务性启动一个 Harness Host。
         *
         * 任意注册或 Loader 协调失败都会反向释放已经创建的资源。
         */
        public suspend fun start(profile: DesktopProfile): HarnessHost {
            val runtime = Runtime()
            val registry = Registry()
            val loader = Loader(runtime, registry)
            val gateway = ApiGateway()

            try {
                gateway.registerHostApi(runtime.context)
                gateway.registerSessionApi(runtime.context)
                runtime.provideLoaderFactory(registry)

                runtime.context.effect(
                    registry.register(
                        name = HostApiPluginName,
                        plugin = HostApiPlugin,
                        config = typedConfig<HostDescription>(),
                    )
                )

                runtime.context.effect(profile.catalog.registerInto(registry))

                loader.reconcile(hostEntries(profile.hostDescription, profile.entries))

                return HarnessHost(
                    profile = profile,
                    runtime = runtime,
                    registry = registry,
                    loader = loader,
                    gateway = gateway,
                )
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    disposeHost(
                        loader = loader,
                        context = runtime.context,
                    )?.let { cleanupError ->
                        if (cleanupError !== error) {
                            error.addSuppressed(cleanupError)
                        }
                    }
                }
                throw error
            }
        }

        private fun hostEntries(
            description: HostDescription,
            entries: List<Entry>,
        ): List<Entry> =
            listOf(
                Entry(
                    id = HostApiEntryId,
                    name = HostApiPluginName,
                    config = description,
                )
            ) + entries
    }

    /** Applies a new complete profile snapshot while preserving the Host API row. */
    public suspend fun reconcileProfile(entries: List<Entry>) {
        check(!closed) { "Harness Host is closed" }
        loader.reconcile(hostEntries(profile.hostDescription, entries))
    }
}

/** 释放 Host 的全部根资源，并保留每个清理失败。 */
private suspend fun disposeHost(
    loader: Loader,
    context: Context,
): Throwable? {
    var failure: Throwable? = null

    try {
        loader.dispose()
    } catch (error: Throwable) {
        failure = error
    }

    try {
        context.dispose()
    } catch (error: Throwable) {
        if (failure == null) {
            failure = error
        } else if (error !== failure) {
            failure.addSuppressed(error)
        }
    }

    return failure
}

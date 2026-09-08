package im.hikaru.harness.loader

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.plugin.InstallResult
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.SimplePlugin

/**
 * Loader 可按名称解析的 Plugin 定义集合。
 *
 * Cordis 的 JS Loader 可以动态 import 模块；KMP 没有统一的动态模块系统，
 * 因此 Registry 是跨平台的显式解析边界。
 */
class Registry {

    private val registrations =
        linkedMapOf<String, Registration<*>>()

    val size: Int
        get() = registrations.size

    val names: Set<String>
        get() = registrations.keys.toSet()

    operator fun contains(name: String): Boolean =
        name in registrations

    fun <C : Any> register(
        name: String,
        plugin: Plugin<C>,
        config: ConfigAdapter<C>,
    ): Disposable {
        check(name.isNotBlank()) {
            "Plugin registration name must not be blank"
        }

        check(name !in registrations) {
            "Plugin '$name' is already registered"
        }

        val registration =
            Registration(
                name = name,
                plugin = plugin,
                adapter = config,
            )

        registrations[name] =
            registration

        return Disposable {
            if (registrations[name] === registration) {
                registrations.remove(name)
            }
        }
    }

    fun register(
        name: String,
        plugin: SimplePlugin,
    ): Disposable =
        register(
            name = name,
            plugin = plugin,
            config = ConfigAdapter.unit(),
        )

    internal fun prepare(
        name: String,
        rawConfig: Any?,
    ): PreparedPlugin {
        val registration =
            registrations[name]
                ?: error(
                    "Plugin '$name' is not registered"
                )

        return registration.prepare(rawConfig)
    }
}

internal interface PreparedPlugin {

    val registration: Any

    suspend fun install(
        runtime: Runtime,
        parent: Context,
    ): InstallResult<*>
}

private class Registration<C : Any>(
    val name: String,
    val plugin: Plugin<C>,
    val adapter: ConfigAdapter<C>,
) {

    fun prepare(rawConfig: Any?): PreparedPlugin {
        val config =
            adapter.decode(rawConfig)

        return PreparedRegistration(
            registration = this,
            plugin = plugin,
            config = config,
        )
    }
}

private class PreparedRegistration<C : Any>(
    override val registration: Registration<C>,
    private val plugin: Plugin<C>,
    private val config: C,
) : PreparedPlugin {

    override suspend fun install(
        runtime: Runtime,
        parent: Context,
    ): InstallResult<C> =
        runtime.installCatching(
            plugin = plugin,
            config = config,
            parent = parent,
        )
}

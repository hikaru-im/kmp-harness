package im.hikaru.harness.runtime.plugin

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.service.ServiceKey

@DslMarker
annotation class PluginDsl

@PluginDsl
class PluginBuilder<C : Any> internal constructor() {

    private val dependencies =
        linkedMapOf<ServiceKey<*>, InjectSpec>()

    private var action:
        (suspend (Context, C, EffectScope) -> Unit)? = null

    fun inject(
        key: ServiceKey<*>,
        required: Boolean = true,
    ) {
        check(key !in dependencies) {
            "Service '${key.name}' is already injected"
        }

        dependencies[key] =
            if (required) {
                InjectSpec.required(key)
            } else {
                InjectSpec.optional(key)
            }
    }

    fun optional(
        key: ServiceKey<*>,
    ) {
        inject(
            key = key,
            required = false,
        )
    }

    fun apply(
        block: suspend (
            context: Context,
            config: C,
            scope: EffectScope,
        ) -> Unit,
    ) {
        check(action == null) {
            "Plugin apply block is already defined"
        }

        action =
            block
    }

    internal fun build(): Plugin<C> {
        val apply =
            checkNotNull(action) {
                "Plugin apply block is required"
            }

        val inject =
            dependencies.values.toSet()

        return object : Plugin<C> {

            override val inject: Set<InjectSpec> =
                inject

            override suspend fun apply(
                context: Context,
                config: C,
                scope: EffectScope,
            ) {
                apply(context, config, scope)
            }
        }
    }
}

@PluginDsl
class SimplePluginBuilder internal constructor() {

    private val dependencies =
        linkedMapOf<ServiceKey<*>, InjectSpec>()

    private var action:
        (suspend (Context, EffectScope) -> Unit)? = null

    fun inject(
        key: ServiceKey<*>,
        required: Boolean = true,
    ) {
        check(key !in dependencies) {
            "Service '${key.name}' is already injected"
        }

        dependencies[key] =
            if (required) {
                InjectSpec.required(key)
            } else {
                InjectSpec.optional(key)
            }
    }

    fun optional(
        key: ServiceKey<*>,
    ) {
        inject(
            key = key,
            required = false,
        )
    }

    fun apply(
        block: suspend (
            context: Context,
            scope: EffectScope,
        ) -> Unit,
    ) {
        check(action == null) {
            "Plugin apply block is already defined"
        }

        action =
            block
    }

    internal fun build(): SimplePlugin {
        val apply =
            checkNotNull(action) {
                "Plugin apply block is required"
            }

        val inject =
            dependencies.values.toSet()

        return object : SimplePlugin {

            override val inject: Set<InjectSpec> =
                inject

            override suspend fun apply(
                context: Context,
                scope: EffectScope,
            ) {
                apply(context, scope)
            }
        }
    }
}

fun <C : Any> plugin(
    block: PluginBuilder<C>.() -> Unit,
): Plugin<C> =
    PluginBuilder<C>()
        .apply(block)
        .build()

fun simplePlugin(
    block: SimplePluginBuilder.() -> Unit,
): SimplePlugin =
    SimplePluginBuilder()
        .apply(block)
        .build()

/** Cordis 风格的安装别名；Runtime 仍然是显式协调器。 */
suspend fun <C : Any> Runtime.plugin(
    plugin: Plugin<C>,
    config: C,
    parent: Context = context,
): Fiber<C> =
    install(
        plugin = plugin,
        config = config,
        parent = parent,
    )

suspend fun Runtime.plugin(
    plugin: SimplePlugin,
    parent: Context = context,
): Fiber<Unit> =
    install(
        plugin = plugin,
        parent = parent,
    )

package im.hikaru.harness.loader

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.plugin.Plugin
import im.hikaru.harness.runtime.plugin.SimplePlugin

/** One compiled plugin definition addressable by a stable configuration name. */
public interface PluginDefinition {
    public val name: String

    public fun register(registry: Registry): Disposable
}

/** Creates a definition for a plugin that does not accept configuration. */
public fun pluginDefinition(
    name: String,
    plugin: SimplePlugin,
): PluginDefinition =
    pluginDefinition(
        name = name,
        plugin = plugin,
        config = ConfigAdapter.unit(),
    )

/** Creates a definition whose adapter owns raw configuration validation. */
public fun <C : Any> pluginDefinition(
    name: String,
    plugin: Plugin<C>,
    config: ConfigAdapter<C>,
): PluginDefinition =
    DefaultPluginDefinition(
        name = name,
        plugin = plugin,
        config = config,
    )

/**
 * The KMP module-resolution boundary used by profile configuration.
 *
 * Unlike Node, KMP cannot import arbitrary packages on every target at runtime.
 * A catalog therefore exposes only plugin definitions compiled into the host.
 */
public class PluginCatalog(
    definitions: List<PluginDefinition>,
) {
    public val definitions: List<PluginDefinition> = definitions.toList()

    public val names: Set<String> = this.definitions.mapTo(linkedSetOf()) { it.name }

    init {
        this.definitions.forEach { definition ->
            require(definition.name.isNotBlank()) {
                "Plugin definition name must not be blank"
            }
        }
        require(names.size == this.definitions.size) {
            val duplicate =
                this.definitions
                    .groupingBy(PluginDefinition::name)
                    .eachCount()
                    .entries
                    .first { (_, count) -> count > 1 }
                    .key
            "Plugin catalog contains duplicate definition '$duplicate'"
        }
    }

    /** Registers the complete catalog and rolls back a partially registered batch. */
    public suspend fun registerInto(registry: Registry): Disposable {
        val registrations = mutableListOf<Disposable>()
        try {
            definitions.forEach { definition ->
                registrations += definition.register(registry)
            }
        } catch (error: Throwable) {
            registrations.asReversed().forEach { registration ->
                try {
                    registration.dispose()
                } catch (cleanupError: Throwable) {
                    if (cleanupError !== error) {
                        error.addSuppressed(cleanupError)
                    }
                }
            }
            throw error
        }

        return Disposable {
            var failure: Throwable? = null
            registrations.asReversed().forEach { registration ->
                try {
                    registration.dispose()
                } catch (error: Throwable) {
                    if (failure == null) {
                        failure = error
                    } else if (failure !== error) {
                        failure.addSuppressed(error)
                    }
                }
            }
            failure?.let { throw it }
        }
    }

    public companion object {
        public val Empty: PluginCatalog = PluginCatalog(emptyList())
    }
}

private class DefaultPluginDefinition<C : Any>(
    override val name: String,
    private val plugin: Plugin<C>,
    private val config: ConfigAdapter<C>,
) : PluginDefinition {
    override fun register(registry: Registry): Disposable =
        registry.register(
            name = name,
            plugin = plugin,
            config = config,
        )
}

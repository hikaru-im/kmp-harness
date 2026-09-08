package im.hikaru.harness.settings.file

import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin
import java.nio.file.Path
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

public const val SETTINGS_FILE_PLUGIN_NAME: String = "settings-file"

public val SettingsFilePluginDefinition: PluginDefinition =
    settingsFilePluginDefinition()

/** Creates the compiled definition with a launcher-owned default home. */
public fun settingsFilePluginDefinition(defaultHarnessHome: HarnessHome? = null): PluginDefinition =
    pluginDefinition(
        name = SETTINGS_FILE_PLUGIN_NAME,
        plugin = ConfiguredSettingsFilePlugin,
        config =
            jsonObjectConfig(allowMissing = true) { value ->
                decodeSettingsFileConfig(value, defaultHarnessHome)
            },
    )

private object ConfiguredSettingsFilePlugin : Plugin<SettingsFileConfig> {
    override suspend fun apply(
        context: Context,
        config: SettingsFileConfig,
        scope: EffectScope,
    ) {
        SettingsFilePlugin(config).apply(context, scope)
    }
}

private fun decodeSettingsFileConfig(
    value: JsonObject,
    defaultHarnessHome: HarnessHome?,
): SettingsFileConfig {
    if (defaultHarnessHome == null) {
        value.requireOnly("path", "watch", "debounceMillis")
    } else {
        value.requireOnly("watch", "debounceMillis")
    }
    return SettingsFileConfig(
        path =
            value.path("path")
                ?: defaultHarnessHome?.settings
                ?: error("settings-file requires an explicit path or a Host-provided Harness home"),
        watch = value.boolean("watch") ?: true,
        debounceMillis = value.long("debounceMillis") ?: 100,
    )
}

private fun JsonObject.requireOnly(vararg names: String) {
    val unsupported = keys - names.toSet()
    require(unsupported.isEmpty()) {
        "settings-file config contains unknown fields: ${unsupported.sorted().joinToString()}"
    }
}

private fun JsonObject.path(name: String): Path? =
    string(name)?.let(Path::of)

private fun JsonObject.string(name: String): String? {
    val value = get(name) ?: return null
    return (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: error("settings-file config field $name must be a non-blank string")
}

private fun JsonObject.boolean(name: String): Boolean? {
    val value = get(name) ?: return null
    return (value as? JsonPrimitive)?.booleanOrNull
        ?: error("settings-file config field $name must be a boolean")
}

private fun JsonObject.long(name: String): Long? {
    val value = get(name) ?: return null
    return (value as? JsonPrimitive)?.longOrNull
        ?: error("settings-file config field $name must be an integer")
}

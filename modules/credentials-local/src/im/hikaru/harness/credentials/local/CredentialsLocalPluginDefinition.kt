package im.hikaru.harness.credentials.local

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

public const val CREDENTIALS_LOCAL_PLUGIN_NAME: String = "credentials-local"

public val CredentialsLocalPluginDefinition: PluginDefinition =
    credentialsLocalPluginDefinition()

/** Creates the compiled definition with a launcher-owned default home. */
public fun credentialsLocalPluginDefinition(defaultHarnessHome: HarnessHome? = null): PluginDefinition =
    pluginDefinition(
        name = CREDENTIALS_LOCAL_PLUGIN_NAME,
        plugin = ConfiguredCredentialsLocalPlugin,
        config =
            jsonObjectConfig(allowMissing = true) { value ->
                decodeCredentialsLocalConfig(value, defaultHarnessHome)
            },
    )

private object ConfiguredCredentialsLocalPlugin : Plugin<LocalCredentialsConfig> {
    override suspend fun apply(
        context: Context,
        config: LocalCredentialsConfig,
        scope: EffectScope,
    ) {
        CredentialsLocalPlugin(config).apply(context, scope)
    }
}

private fun decodeCredentialsLocalConfig(
    value: JsonObject,
    defaultHarnessHome: HarnessHome?,
): LocalCredentialsConfig {
    if (defaultHarnessHome == null) {
        value.requireOnly(
            "path",
            "projectDir",
            "userEnvPath",
            "watch",
            "debounceMillis",
        )
    } else {
        value.requireOnly(
            "projectDir",
            "userEnvPath",
            "watch",
            "debounceMillis",
        )
    }
    return LocalCredentialsConfig(
        path =
            value.path("path")
                ?: defaultHarnessHome?.credentials
                ?: error("credentials-local requires an explicit path or a Host-provided Harness home"),
        projectDir = value.path("projectDir") ?: Path.of("").toAbsolutePath(),
        userEnvPath = value.path("userEnvPath") ?: defaultHarnessHome?.userEnvironment,
        watch = value.boolean("watch") ?: true,
        debounceMillis = value.long("debounceMillis") ?: 100,
    )
}

private fun JsonObject.requireOnly(vararg names: String) {
    val unsupported = keys - names.toSet()
    require(unsupported.isEmpty()) {
        "credentials-local config contains unknown fields: ${unsupported.sorted().joinToString()}"
    }
}

private fun JsonObject.path(name: String): Path? =
    string(name)?.let(Path::of)

private fun JsonObject.string(name: String): String? {
    val value = get(name) ?: return null
    return (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: error("credentials-local config field $name must be a non-blank string")
}

private fun JsonObject.boolean(name: String): Boolean? {
    val value = get(name) ?: return null
    return (value as? JsonPrimitive)?.booleanOrNull
        ?: error("credentials-local config field $name must be a boolean")
}

private fun JsonObject.long(name: String): Long? {
    val value = get(name) ?: return null
    return (value as? JsonPrimitive)?.longOrNull
        ?: error("credentials-local config field $name must be an integer")
}

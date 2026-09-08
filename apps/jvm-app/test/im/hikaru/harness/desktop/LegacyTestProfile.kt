package im.hikaru.harness.desktop

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.boot.DesktopProfile
import im.hikaru.harness.credentials.local.CredentialsLocalPlugin
import im.hikaru.harness.credentials.local.LocalCredentialsConfig
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.logger.LoggerPlugin
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.settings.file.SettingsFileConfig
import im.hikaru.harness.settings.file.SettingsFilePlugin

/** Test-only compatibility fixture for transport tests that inject fake Provider clients. */
data class DesktopLlmProviderPlugin(
    val id: String,
    val plugin: SimplePlugin,
)

sealed interface ConfigurationSource {
    data object Static : ConfigurationSource
    data object Files : ConfigurationSource
}

data class DesktopFileConfiguration(
    val settings: SettingsFileConfig,
    val credentials: LocalCredentialsConfig,
)

fun createDesktopProfile(
    providerPlugins: List<DesktopLlmProviderPlugin> = emptyList(),
    configurationSource: ConfigurationSource = ConfigurationSource.Static,
    fileConfiguration: DesktopFileConfiguration? = null,
): DesktopProfile {
    val definitions = mutableListOf(pluginDefinition("logger", LoggerPlugin))
    val entries = mutableListOf(Entry("logger", "logger"))
    if (configurationSource == ConfigurationSource.Files) {
        val files =
            requireNotNull(fileConfiguration) {
                "File configuration is required when configurationSource is Files"
            }
        definitions += pluginDefinition("settings-file", SettingsFilePlugin(files.settings))
        definitions +=
            pluginDefinition("credentials-local", CredentialsLocalPlugin(files.credentials))
        entries += Entry("settings-file", "settings-file")
        entries += Entry("credentials-local", "credentials-local")
    }
    if (providerPlugins.isNotEmpty()) {
        definitions += pluginDefinition("llm", LlmPlugin())
        entries += Entry("llm", "llm")
        providerPlugins.forEach { provider ->
            definitions += pluginDefinition(provider.id, provider.plugin)
            entries += Entry(provider.id, provider.id)
        }
    }
    return DesktopProfile(
        hostDescription =
            HostDescription(
                protocolVersion = ProtocolVersion(1, 0),
                hostId = HostId("desktop-test"),
                displayName = "KMP Harness Desktop Test",
            ),
        catalog = PluginCatalog(definitions),
        entries = entries,
    )
}

package im.hikaru.harness.bundle.desktop

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.credentials.local.CREDENTIALS_LOCAL_PLUGIN_NAME
import im.hikaru.harness.credentials.local.CredentialsLocalPluginDefinition
import im.hikaru.harness.credentials.local.credentialsLocalPluginDefinition
import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.llm.LLM_PLUGIN_NAME
import im.hikaru.harness.llm.LlmPluginDefinition
import im.hikaru.harness.llm.koog.openai.OPENAI_KOOG_PLUGIN_NAME
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPluginDefinition
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.logger.LOGGER_PLUGIN_NAME
import im.hikaru.harness.logger.LoggerPluginDefinition
import im.hikaru.harness.profile.EntryPatch
import im.hikaru.harness.profile.ProfileBundle
import im.hikaru.harness.profile.ProfileBundleCatalog
import im.hikaru.harness.profile.ProfilePatchReload
import im.hikaru.harness.profile.file.FileProfileLoader
import im.hikaru.harness.profile.file.ProfileLoadRequest
import im.hikaru.harness.profile.file.ProfileReloadFailure
import im.hikaru.harness.profile.file.ProfiledHarnessHost
import im.hikaru.harness.settings.file.SETTINGS_FILE_PLUGIN_NAME
import im.hikaru.harness.settings.file.SettingsFilePluginDefinition
import im.hikaru.harness.settings.file.settingsFilePluginDefinition
import java.nio.file.Path

public const val DESKTOP_BUNDLE_NAME: String = "@hikaru-ai/harness-desktop"

/** Every plugin implementation compiled into the Desktop host. */
public val DesktopPluginCatalog: PluginCatalog =
    desktopPluginCatalog()

private fun desktopPluginCatalog(defaultHarnessHome: HarnessHome? = null): PluginCatalog =
    PluginCatalog(
        listOf(
            LoggerPluginDefinition,
            if (defaultHarnessHome == null) {
                SettingsFilePluginDefinition
            } else {
                settingsFilePluginDefinition(defaultHarnessHome)
            },
            if (defaultHarnessHome == null) {
                CredentialsLocalPluginDefinition
            } else {
                credentialsLocalPluginDefinition(defaultHarnessHome)
            },
            LlmPluginDefinition,
            OpenAiKoogPluginDefinition,
        )
    )

/** DSH-style bundle patch applied over an empty profile root. */
public val DesktopProfileBundle: ProfileBundle =
    ProfileBundle(
        name = DESKTOP_BUNDLE_NAME,
        patches =
            listOf(
                EntryPatch(
                    insert =
                        listOf(
                            Entry(LOGGER_PLUGIN_NAME, LOGGER_PLUGIN_NAME),
                            Entry(SETTINGS_FILE_PLUGIN_NAME, SETTINGS_FILE_PLUGIN_NAME),
                            Entry(CREDENTIALS_LOCAL_PLUGIN_NAME, CREDENTIALS_LOCAL_PLUGIN_NAME),
                            Entry(LLM_PLUGIN_NAME, LLM_PLUGIN_NAME),
                            Entry(OPENAI_KOOG_PLUGIN_NAME, OPENAI_KOOG_PLUGIN_NAME),
                        )
                )
            ),
    )

/** Starts the DSH-compatible Desktop profile from the platform Harness home. */
public suspend fun startDesktopProfile(
    harnessHome: Path? = null,
    overlays: List<Path> = emptyList(),
    pollMillis: Long = 250,
    onReloadFailure: (ProfileReloadFailure) -> Unit = {},
): ProfiledHarnessHost {
    val resolvedHome = resolveDesktopHarnessHome(harnessHome)
    val loader =
        FileProfileLoader(
            home = resolvedHome,
            bundles = ProfileBundleCatalog(listOf(DesktopProfileBundle)),
            defaultManifestName = "harness-desktop",
            defaultBundles = listOf(DESKTOP_BUNDLE_NAME),
            defaultPatchReload = ProfilePatchReload.Live,
        )
    return ProfiledHarnessHost.start(
        profileLoader = loader,
        request = ProfileLoadRequest(overlays = overlays),
        catalog = desktopPluginCatalog(resolvedHome),
        hostDescription = desktopHostDescription(),
        pollMillis = pollMillis,
        onReloadFailure = onReloadFailure,
    )
}

public fun desktopHostDescription(): HostDescription =
    HostDescription(
        protocolVersion = ProtocolVersion(major = 1, minor = 0),
        hostId = HostId("desktop-local"),
        displayName = "KMP Harness Desktop",
    )

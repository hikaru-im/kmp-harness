package im.hikaru.harness.bundle.desktop

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.agent.AgentPluginDefinition
import im.hikaru.harness.agent.instructions.AGENT_INSTRUCTIONS_PLUGIN_NAME
import im.hikaru.harness.agent.instructions.AgentInstructionsPlugin
import im.hikaru.harness.agent.loop.AgentLoopPluginDefinition
import im.hikaru.harness.credentials.local.CREDENTIALS_LOCAL_PLUGIN_NAME
import im.hikaru.harness.credentials.local.credentialsLocalPluginDefinition
import im.hikaru.harness.exec.jvm.JOBS_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.JobsPluginDefinition
import im.hikaru.harness.exec.jvm.SUBPROCESS_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.SubprocessPluginDefinition
import im.hikaru.harness.exec.jvm.TERMINAL_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.TOOL_BASH_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.TOOL_JOBS_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.TOOL_PWSH_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.TOOL_TERMINAL_PLUGIN_NAME
import im.hikaru.harness.exec.jvm.TerminalPluginDefinition
import im.hikaru.harness.exec.jvm.ToolBashPluginDefinition
import im.hikaru.harness.exec.jvm.ToolJobsPluginDefinition
import im.hikaru.harness.exec.jvm.ToolPwshPluginDefinition
import im.hikaru.harness.exec.jvm.ToolTerminalPluginDefinition
import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.llm.LLM_PLUGIN_NAME
import im.hikaru.harness.llm.LlmPluginDefinition
import im.hikaru.harness.llm.koog.openai.OPENAI_KOOG_PLUGIN_NAME
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPluginDefinition
import im.hikaru.harness.llm.retry.LlmRetryPluginDefinition
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.pluginDefinition
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
import im.hikaru.harness.schedule.SCHEDULE_PLUGIN_NAME
import im.hikaru.harness.schedule.TOOL_SCHEDULE_PLUGIN_NAME
import im.hikaru.harness.schedule.ToolSchedulePluginDefinition
import im.hikaru.harness.schedule.schedulePluginDefinition
import im.hikaru.harness.session.SessionPluginDefinition
import im.hikaru.harness.session.api.SessionApiPluginDefinition
import im.hikaru.harness.session.persistence.jvmSessionPersistenceDatabaseFactory
import im.hikaru.harness.session.persistence.sessionPersistencePluginDefinition
import im.hikaru.harness.settings.file.SETTINGS_FILE_PLUGIN_NAME
import im.hikaru.harness.settings.file.settingsFilePluginDefinition
import im.hikaru.harness.subagent.tools.LIST_AGENTS_PLUGIN_NAME
import im.hikaru.harness.subagent.tools.ListAgentsPluginDefinition
import im.hikaru.harness.subagent.tools.TOOL_SUBAGENT_CONTROL_PLUGIN_NAME
import im.hikaru.harness.subagent.tools.TOOL_SUBAGENT_PLUGIN_NAME
import im.hikaru.harness.subagent.tools.ToolSubagentControlPluginDefinition
import im.hikaru.harness.subagent.tools.ToolSubagentPluginDefinition
import im.hikaru.harness.system.prompt.SYSTEM_PROMPT_PLUGIN_NAME
import im.hikaru.harness.system.prompt.SystemPromptPluginDefinition
import im.hikaru.harness.tools.ToolsPluginDefinition
import java.nio.file.Path

public const val DESKTOP_BUNDLE_NAME: String = "@hikaru-ai/harness-desktop"

/** Every plugin implementation compiled into the Desktop host. */
public val DesktopPluginCatalog: PluginCatalog =
    desktopPluginCatalog(resolveDesktopHarnessHome())

private fun desktopPluginCatalog(defaultHarnessHome: HarnessHome): PluginCatalog =
    PluginCatalog(
        listOf(
            LoggerPluginDefinition,
            settingsFilePluginDefinition(defaultHarnessHome),
            credentialsLocalPluginDefinition(defaultHarnessHome),
            LlmPluginDefinition,
            OpenAiKoogPluginDefinition,
            SessionPluginDefinition,
            AgentPluginDefinition,
            SystemPromptPluginDefinition,
            ToolsPluginDefinition,
            desktopAgentInstructionsPluginDefinition(defaultHarnessHome),
            SubprocessPluginDefinition,
            JobsPluginDefinition,
            ToolBashPluginDefinition,
            ToolPwshPluginDefinition,
            ToolJobsPluginDefinition,
            TerminalPluginDefinition,
            ToolTerminalPluginDefinition,
            ToolSubagentPluginDefinition,
            ToolSubagentControlPluginDefinition,
            ListAgentsPluginDefinition,
            AgentLoopPluginDefinition,
            SessionApiPluginDefinition,
            sessionPersistencePluginDefinition(
                jvmSessionPersistenceDatabaseFactory(
                    defaultHarnessHome.resolve("harness-sessions.db").toFile()
                ), autoSave = true
            ),
            schedulePluginDefinition(
                defaultHarnessHome.resolve("harness-schedule.db").toFile()
            ),
            ToolSchedulePluginDefinition,
            LlmRetryPluginDefinition,
        )
    )

/** Publishes locally installed plugin skills through the `agent-instructions` prompt section. */
private fun desktopAgentInstructionsPluginDefinition(defaultHarnessHome: HarnessHome): PluginDefinition =
    pluginDefinition(
        name = AGENT_INSTRUCTIONS_PLUGIN_NAME,
        plugin =
            AgentInstructionsPlugin(
                DesktopUserPlugins(
                    defaultHarnessHome.directory.resolve("client-plugins.json"),
                ),
            ),
    )

/** DSH-style bundle patch applied over an empty profile root. */
public val DesktopProfileBundle: ProfileBundle = desktopProfileBundle(isWindowsPlatform())

/** The shell tool the default profile mounts: DSH's platform gate between `tool-bash` and `tool-pwsh`. */
internal fun desktopShellToolPluginName(isWindows: Boolean): String =
    if (isWindows) TOOL_PWSH_PLUGIN_NAME else TOOL_BASH_PLUGIN_NAME

internal fun isWindowsPlatform(): Boolean =
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

internal fun desktopProfileBundle(isWindows: Boolean): ProfileBundle =
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
                            Entry("session", "session"),
                            Entry("agent", "agent"),
                            Entry(SYSTEM_PROMPT_PLUGIN_NAME, SYSTEM_PROMPT_PLUGIN_NAME),
                            Entry("tools", "tools"),
                            Entry(AGENT_INSTRUCTIONS_PLUGIN_NAME, AGENT_INSTRUCTIONS_PLUGIN_NAME),
                            Entry(SUBPROCESS_PLUGIN_NAME, SUBPROCESS_PLUGIN_NAME),
                            Entry(JOBS_PLUGIN_NAME, JOBS_PLUGIN_NAME),
                            Entry(desktopShellToolPluginName(isWindows), desktopShellToolPluginName(isWindows)),
                            Entry(TOOL_JOBS_PLUGIN_NAME, TOOL_JOBS_PLUGIN_NAME),
                            Entry(TERMINAL_PLUGIN_NAME, TERMINAL_PLUGIN_NAME),
                            Entry(TOOL_TERMINAL_PLUGIN_NAME, TOOL_TERMINAL_PLUGIN_NAME),
                            Entry(TOOL_SUBAGENT_PLUGIN_NAME, TOOL_SUBAGENT_PLUGIN_NAME),
                            Entry(TOOL_SUBAGENT_CONTROL_PLUGIN_NAME, TOOL_SUBAGENT_CONTROL_PLUGIN_NAME),
                            Entry(LIST_AGENTS_PLUGIN_NAME, LIST_AGENTS_PLUGIN_NAME),
                            Entry("agent-loop", "agent-loop"),
                            Entry("session-api", "session-api"),
                            Entry("session-persistence", "session-persistence"),
                            Entry(SCHEDULE_PLUGIN_NAME, SCHEDULE_PLUGIN_NAME),
                            Entry(TOOL_SCHEDULE_PLUGIN_NAME, TOOL_SCHEDULE_PLUGIN_NAME),
                            Entry("llm-retry", "llm-retry"),
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
        // A chat without a project keeps the directory the Host was launched from, like DSH's
        // `process.cwd()` fallback.
        defaultCwd = System.getProperty("user.dir")?.takeIf { it.isNotBlank() },
    )
}

public fun desktopHostDescription(): HostDescription =
    HostDescription(
        protocolVersion = ProtocolVersion(major = 1, minor = 0),
        hostId = HostId("desktop-local"),
        displayName = "KMP Harness",
    )

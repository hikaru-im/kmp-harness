package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.agent.agents
import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.credentials.credentialRef
import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.plugin.FiberState
import im.hikaru.harness.session.createSession
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.persistence.SessionPersistenceKey
import im.hikaru.harness.settings.SettingsKey
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class DesktopBundleTest {
    @Test
    fun desktopHomeResolutionShouldPreferExplicitThenEnvironmentThenPlatformDefault() {
        val userHome = Path.of("desktop-user-home")
        val environmentHome = Path.of("desktop-environment-home")
        val explicitHome = Path.of("desktop-explicit-home")
        val environment: (String) -> String? = { name ->
            environmentHome.toString().takeIf { name == "HARNESS_HOME" }
        }

        assertEquals(
            explicitHome.toAbsolutePath(),
            resolveDesktopHarnessHome(explicitHome, environment, userHome).directory,
        )
        assertEquals(
            environmentHome.toAbsolutePath(),
            resolveDesktopHarnessHome(null, environment, userHome).directory,
        )
        assertEquals(
            userHome.resolve(".harness").toAbsolutePath(),
            resolveDesktopHarnessHome(null, { null }, userHome).directory,
        )
    }

    @Test
    fun bundleAndCatalogShouldExposeTheSameExplicitPluginRows() {
        val entries = DesktopProfileBundle.patches.single().insert.orEmpty()

        assertEquals(entries.map { it.name }.toSet(), DesktopPluginCatalog.names)
        assertEquals(
            listOf("logger", "settings-file", "credentials-local", "llm", "llm-koog-openai", "session", "agent", "tools", "agent-loop", "session-api", "session-persistence", "llm-retry"),
            entries.map { it.id },
        )
    }

    @Test
    fun startupProfileShouldHonorPatchDisables() = runTest {
        val home = Files.createTempDirectory("desktop-bundle")
        val harnessHome = HarnessHome.at(home)
        Files.writeString(
            harnessHome.profilePatch,
            "- id: llm-koog-openai\n  disabled: true\n",
        )

        val session = startDesktopProfile(harnessHome = home, pollMillis = 25)
        try {
            assertTrue(session.host.loader.entry("llm-koog-openai")?.disabled == true)
            assertFalse(session.host.loader.fiber("llm-koog-openai") != null)
        } finally {
            session.close()
        }
    }

    @Test
    fun oneDesktopHomeShouldOwnAllFiveFilesWithoutAProfileSubdirectory() = runTest {
        val directory = Files.createTempDirectory("desktop-home-layout")
        val home = HarnessHome.at(directory)
        Files.writeString(
            home.profilePatch,
            "- id: llm-koog-openai\n  disabled: true\n",
        )

        val session = startDesktopProfile(harnessHome = directory, pollMillis = 25)
        try {
            assertTrue(Files.exists(home.manifest))
            assertTrue(Files.exists(home.profileRoot))
            assertTrue(Files.exists(home.profilePatch))
            assertTrue(Files.exists(home.settings))
            assertFalse(Files.exists(home.credentials))

            session.host.runtime.context
                .require(CredentialsKey)
                .set(credentialRef("LAYOUT_TEST_KEY"), "layout-test-value")

            val files =
                listOf(
                    home.manifest,
                    home.profileRoot,
                    home.profilePatch,
                    home.settings,
                    home.credentials,
                )
            assertTrue(files.all(Files::exists))
            assertEquals(setOf(home.directory), files.map { it.parent }.toSet())
            assertFalse(Files.exists(home.directory.resolve("profiles")))
        } finally {
            session.close()
        }
    }

    @Test
    fun completeProfileShouldKeepOpenAiDormantUntilSettingsDeclareAProvider() = runTest {
        val home = Files.createTempDirectory("desktop-bundle-full")
        val credentials = home.resolve(".credentials.yaml")
        Files.writeString(credentials, "OPENAI_API_KEY: test-only-key\n")
        runCatching {
            Files.setPosixFilePermissions(
                credentials,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }

        val session = startDesktopProfile(harnessHome = home, pollMillis = 25)
        try {
            val context = session.host.runtime.context
            val pluginNames = DesktopProfileBundle.patches.single().insert.orEmpty().map { it.id }
            assertEquals(12, pluginNames.size)
            assertTrue(
                pluginNames.all { name ->
                    session.host.loader.fiber(name)?.state == FiberState.Active
                }
            )
            assertTrue(context.get(SettingsKey) != null)
            assertTrue(context.get(CredentialsKey) != null)
            assertTrue(context.agents.list().isEmpty())
            assertTrue(context.get(SessionPersistenceKey) != null)
            context.createSession()
            context.sessions.flush()
            assertTrue(Files.exists(home.resolve("harness-sessions.db")))
            assertTrue(context.llm.listProviders().isEmpty())
            assertEquals(
                listOf("openai" to false),
                context.llm.listConfigurableProviders().map { provider ->
                    provider.provider to provider.declared
                },
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun definitionsShouldApplySupportedConfigAndRejectUnsupportedFields() = runTest {
        val validHome = Files.createTempDirectory("desktop-bundle-valid-config")
        Files.writeString(
            HarnessHome.at(validHome).profilePatch,
            """
            - id: tools
              config: { maxConcurrentCalls: 2 }
            - id: agent-loop
              config: { system: configured-system }
            """.trimIndent() + "\n",
        )
        startDesktopProfile(harnessHome = validHome).close()

        val invalidPatches =
            listOf(
                "- id: session\n  config: {}\n",
                "- id: agent\n  config: {}\n",
                "- id: session-api\n  config: {}\n",
                "- id: llm-retry\n  config: {}\n",
                "- id: agent-loop\n  config: { provider: openai }\n",
                "- id: agent-loop\n  config: { model: gpt-4o-mini }\n",
                "- id: agent-loop\n  config: { agents: [] }\n",
                "- id: tools\n  config: { maxConcurrentCalls: 0 }\n",
                "- id: tools\n  config: { maxConcurrentCalls: \"2\" }\n",
                "- id: tools\n  config: { unknown: true }\n",
                "- id: session-persistence\n  config: { path: other.db }\n",
            )
        invalidPatches.forEachIndexed { index, patch ->
            val home = Files.createTempDirectory("desktop-bundle-invalid-definition-$index")
            Files.writeString(HarnessHome.at(home).profilePatch, patch)
            assertFails { startDesktopProfile(harnessHome = home) }
        }
    }

    @Test
    fun disablingAndRestoringSessionShouldRebuildTheDependentChain() = runTest {
        val home = Files.createTempDirectory("desktop-bundle-session-reload")
        val harnessHome = HarnessHome.at(home)
        val session = startDesktopProfile(harnessHome = home, pollMillis = 20)
        try {
            val loader = session.host.loader
            val context = session.host.runtime.context
            val dependentNames =
                listOf("agent", "agent-loop", "session-api", "session-persistence", "llm-retry")
            val initialPersistence = checkNotNull(context.get(SessionPersistenceKey))

            withContext(Dispatchers.IO) { delay(75) }
            Files.writeString(harnessHome.profilePatch, "- id: session\n  disabled: true\n")
            withContext(Dispatchers.IO) {
                withTimeout(3_000) {
                    while (loader.fiber("session") != null || context.get(SessionPersistenceKey) != null) {
                        delay(20)
                    }
                }
            }
            assertTrue(dependentNames.all { name -> loader.fiber(name)?.state == FiberState.Pending })
            assertEquals(FiberState.Active, loader.fiber("tools")?.state)

            Files.writeString(harnessHome.profilePatch, "[]\n")
            withContext(Dispatchers.IO) {
                withTimeout(3_000) {
                    while (
                        loader.fiber("session")?.state != FiberState.Active ||
                        dependentNames.any { name -> loader.fiber(name)?.state != FiberState.Active }
                    ) {
                        delay(20)
                    }
                }
            }
            val restoredPersistence = checkNotNull(context.get(SessionPersistenceKey))
            assertNotSame(initialPersistence, restoredPersistence)
            assertTrue(context.agents.list().isEmpty())
        } finally {
            session.close()
        }
    }

    @Test
    fun pluginPatchShouldNotOverrideTheHostOwnedHarnessHome() = runTest {
        val home = Files.createTempDirectory("desktop-bundle-invalid-config")
        val harnessHome = HarnessHome.at(home)
        Files.writeString(
            harnessHome.profilePatch,
            """
            - id: settings-file
              config:
                harnessHome: /tmp/not-the-host-home
                path: /tmp/not-the-host-settings.yaml
            - id: llm-koog-openai
              disabled: true
            """.trimIndent() + "\n",
        )

        val error =
            assertFailsWith<IllegalArgumentException> {
                startDesktopProfile(harnessHome = home)
            }

        assertTrue(error.message.orEmpty().contains("unknown fields"))
    }
}

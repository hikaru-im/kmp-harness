package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.credentials.credentialRef
import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.llm.llm
import im.hikaru.harness.settings.SettingsKey
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
            listOf("logger", "settings-file", "credentials-local", "llm", "llm-koog-openai"),
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
            assertTrue(context.get(SettingsKey) != null)
            assertTrue(context.get(CredentialsKey) != null)
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

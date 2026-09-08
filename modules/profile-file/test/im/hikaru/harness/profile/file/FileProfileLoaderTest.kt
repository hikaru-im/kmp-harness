package im.hikaru.harness.profile.file

import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.profile.EntryPatch
import im.hikaru.harness.profile.PatchValue
import im.hikaru.harness.profile.ProfileBundle
import im.hikaru.harness.profile.ProfileBundleCatalog
import im.hikaru.harness.profile.ProfilePatchReload
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileProfileLoaderTest {
    @Test
    fun shouldInitializeAndComposeBundleHomeOverlayAndLauncherOrder() = runTest {
        val home = Files.createTempDirectory("harness-profile-home")
        val bundleConfig = JsonObject(mapOf("layer" to JsonPrimitive("bundle")))
        val loader =
            FileProfileLoader(
                home = HarnessHome.at(home),
                bundles =
                    ProfileBundleCatalog(
                        listOf(
                            ProfileBundle(
                                name = "desktop",
                                patches =
                                    listOf(
                                        EntryPatch(
                                            insert = listOf(Entry("service", "plugin", bundleConfig))
                                        )
                                    ),
                            )
                        )
                    ),
                defaultBundles = listOf("desktop"),
                defaultPatchReload = ProfilePatchReload.Live,
            )
        val directory = loader.home.directory
        val first = loader.load(ProfileLoadRequest())
        assertEquals(bundleConfig, first.entries.single().config)
        assertTrue(Files.exists(directory.resolve("package.json")))
        assertEquals("[]\n", Files.readString(loader.home.profileRoot))
        assertFalse(Files.exists(home.resolve("profiles")))

        Files.writeString(
            loader.home.profilePatch,
            "- id: service\n  config: { layer: home }\n",
        )
        val overlay = home.resolve("overlay.yml")
        Files.writeString(overlay, "- id: service\n  config: { layer: overlay }\n")
        val launcher = JsonObject(mapOf("layer" to JsonPrimitive("launcher")))

        val loaded =
            loader.load(
                ProfileLoadRequest(
                    overlays = listOf(overlay),
                    launcherPatches =
                        listOf(
                            EntryPatch(id = "service", config = PatchValue.Present(launcher))
                        ),
                )
            )

        assertEquals(launcher, loaded.entries.single().config)
    }

    @Test
    fun unknownBundleShouldFail() = runTest {
        val home = Files.createTempDirectory("harness-profile-invalid")
        val loader =
            FileProfileLoader(
                home = HarnessHome.at(home),
                bundles = ProfileBundleCatalog(emptyList()),
                defaultBundles = listOf("missing"),
                defaultPatchReload = ProfilePatchReload.Startup,
            )

        assertFailsWith<IllegalStateException> {
            loader.load(ProfileLoadRequest())
        }
    }

}

package im.hikaru.harness.profile.file

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.home.HarnessHome
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.profile.EntryPatch
import im.hikaru.harness.profile.ProfileBundle
import im.hikaru.harness.profile.ProfileBundleCatalog
import im.hikaru.harness.profile.ProfilePatchReload
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.Plugin
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfiledHarnessHostTest {
    @Test
    fun liveWatcherShouldCommitValidPatchAndKeepLastGoodFiberOnInvalidPatch() = runTest {
        val home = Files.createTempDirectory("profiled-host-live")
        val starts = mutableListOf<String>()
        val stops = mutableListOf<String>()
        val fixture = fixture(home, ProfilePatchReload.Live, starts, stops)
        val session =
            ProfiledHarnessHost.start(
                profileLoader = fixture.loader,
                request = ProfileLoadRequest(),
                catalog = fixture.catalog,
                hostDescription = hostDescription(),
                pollMillis = 20,
            )
        try {
            assertTrue(session.isWatching)
            assertEquals(listOf("one"), starts)
            val patchPath = fixture.loader.home.profilePatch
            withContext(Dispatchers.IO) { delay(75) }
            Files.writeString(patchPath, "- id: tracked\n  config: { value: two }\n")

            withContext(Dispatchers.IO) {
                withTimeout(3_000) {
                    while (starts.lastOrNull() != "two") delay(20)
                }
            }
            val lastGoodFiber = session.host.loader.fiber("tracked")
            val lastGoodConfig = session.host.loader.entry("tracked")?.config

            Files.writeString(patchPath, "not: an-array\n")
            withContext(Dispatchers.IO) {
                withTimeout(3_000) {
                    while (session.lastReloadFailure == null) delay(20)
                }
            }

            assertSame(lastGoodFiber, session.host.loader.fiber("tracked"))
            assertEquals(lastGoodConfig, session.host.loader.entry("tracked")?.config)
            assertEquals(listOf("one", "two"), starts)
        } finally {
            session.close()
        }
        assertEquals(listOf("one", "two"), stops)
    }

    @Test
    fun startupModeShouldLoadOnceWithoutWatcher() = runTest {
        val home = Files.createTempDirectory("profiled-host-startup")
        val fixture = fixture(home, ProfilePatchReload.Startup, mutableListOf(), mutableListOf())
        val session =
            ProfiledHarnessHost.start(
                profileLoader = fixture.loader,
                request = ProfileLoadRequest(),
                catalog = fixture.catalog,
                hostDescription = hostDescription(),
            )
        try {
            assertFalse(session.isWatching)
        } finally {
            session.close()
        }
    }

    @Test
    fun unknownEnabledPluginShouldFailBeforeAnyRuntimePluginStarts() = runTest {
        val home = Files.createTempDirectory("profiled-host-unknown")
        var starts = 0
        val tracked = recordingPlugin(onStart = { starts += 1 })
        val catalog =
            PluginCatalog(
                listOf(
                    pluginDefinition(
                        "tracked",
                        tracked,
                        jsonObjectConfig(allowMissing = true) { TrackedConfig("tracked") },
                    )
                )
            )
        val bundle =
            ProfileBundle(
                "bundle",
                listOf(
                    EntryPatch(
                        insert =
                            listOf(
                                Entry("tracked", "tracked", JsonObject(emptyMap())),
                                Entry("missing", "missing"),
                            )
                    )
                ),
            )
        val loader =
            FileProfileLoader(
                home = HarnessHome.at(home),
                bundles = ProfileBundleCatalog(listOf(bundle)),
                defaultBundles = listOf("bundle"),
                defaultPatchReload = ProfilePatchReload.Startup,
            )

        assertFailsWith<IllegalStateException> {
            ProfiledHarnessHost.start(
                profileLoader = loader,
                request = ProfileLoadRequest(),
                catalog = catalog,
                hostDescription = hostDescription(),
            )
        }
        assertEquals(0, starts)
    }

    private fun fixture(
        home: java.nio.file.Path,
        reload: ProfilePatchReload,
        starts: MutableList<String>,
        stops: MutableList<String>,
    ): Fixture {
        val plugin =
            recordingPlugin(
                onStart = { value -> starts += value },
                onStop = { value -> stops += value },
            )
        val catalog =
            PluginCatalog(
                listOf(
                    pluginDefinition(
                        "tracked",
                        plugin,
                        jsonObjectConfig { value ->
                            value.keys.singleOrNull().let { key ->
                                require(key == "value") { "tracked config requires only value" }
                            }
                            TrackedConfig(value.getValue("value").jsonPrimitive.content)
                        },
                    )
                )
            )
        val bundle =
            ProfileBundle(
                "bundle",
                listOf(
                    EntryPatch(
                        insert =
                            listOf(
                                Entry(
                                    "tracked",
                                    "tracked",
                                    JsonObject(mapOf("value" to JsonPrimitive("one"))),
                                )
                            )
                    )
                ),
            )
        val loader =
            FileProfileLoader(
                home = HarnessHome.at(home),
                bundles = ProfileBundleCatalog(listOf(bundle)),
                defaultBundles = listOf("bundle"),
                defaultPatchReload = reload,
            )
        return Fixture(
            loader = loader,
            catalog = catalog,
        )
    }

    private fun recordingPlugin(
        onStart: (String) -> Unit,
        onStop: (String) -> Unit = {},
    ): Plugin<TrackedConfig> =
        object : Plugin<TrackedConfig> {
            override suspend fun apply(
                context: Context,
                config: TrackedConfig,
                scope: EffectScope,
            ) {
                onStart(config.value)
                scope.add(Disposable { onStop(config.value) })
            }
        }

    private data class TrackedConfig(val value: String)

    private data class Fixture(
        val loader: FileProfileLoader,
        val catalog: PluginCatalog,
    )

    private fun hostDescription(): HostDescription =
        HostDescription(
            protocolVersion = ProtocolVersion(1, 0),
            hostId = HostId("profile-test"),
            displayName = "Profile Test",
        )
}

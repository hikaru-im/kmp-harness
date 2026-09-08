package im.hikaru.harness.boot

import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HarnessHostTest {

    @Test
    fun shouldStartDescribeAndCloseIdempotently() = runTest {
        var stopped = 0
        val profile =
            DesktopProfile(
                hostDescription = hostDescription(),
                catalog =
                    PluginCatalog(
                        listOf(
                            pluginDefinition("tracked", trackedPlugin { stopped += 1 })
                        )
                    ),
                entries = listOf(Entry("tracked", "tracked")),
            )

        val host = HarnessHost.start(profile)
        val result = host.gateway.invoke(HostDescribeEndpoint, Unit)

        assertTrue(result.isSuccess)
        assertSame(profile.hostDescription, result.data)
        assertEquals(2, host.runtime.fibers.size)

        host.close()
        host.close()

        assertTrue(host.isClosed)
        assertTrue(host.runtime.context.isDisposed)
        assertTrue(host.runtime.fibers.isEmpty())
        assertEquals(1, stopped)
        assertEquals(
            GatewayResultCodes.NotFound,
            host.gateway.invoke(HostDescribeEndpoint, Unit).code,
        )
    }

    @Test
    fun failedStartupShouldRollbackAlreadyStartedPlugins() = runTest {
        val lifecycle = mutableListOf<String>()
        val profile =
            DesktopProfile(
                hostDescription = hostDescription(),
                catalog =
                    PluginCatalog(
                        listOf(
                            pluginDefinition(
                                "good",
                                trackedPlugin { lifecycle += "good:stop" },
                            ),
                            pluginDefinition(
                                "bad",
                                object : SimplePlugin {
                                    override suspend fun apply(
                                        context: Context,
                                        scope: EffectScope,
                                    ) {
                                        lifecycle += "bad:start"
                                        error("profile startup failed")
                                    }
                                },
                            ),
                        )
                    ),
                entries =
                    listOf(
                        Entry("good", "good"),
                        Entry("bad", "bad"),
                    ),
            )

        val error =
            assertFailsWith<IllegalStateException> {
                HarnessHost.start(profile)
            }

        assertEquals("profile startup failed", error.message)
        assertEquals(
            listOf("bad:start", "good:stop"),
            lifecycle,
        )
    }

    private fun trackedPlugin(onStop: () -> Unit): SimplePlugin =
        object : SimplePlugin {
            override suspend fun apply(
                context: Context,
                scope: EffectScope,
            ) {
                scope.add(
                    Disposable {
                        onStop()
                    }
                )
            }
        }

    private fun hostDescription(): HostDescription =
        HostDescription(
            protocolVersion = ProtocolVersion(major = 1, minor = 0),
            hostId = HostId("desktop-test"),
            displayName = "Desktop Test",
        )
}

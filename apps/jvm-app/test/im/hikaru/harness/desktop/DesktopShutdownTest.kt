package im.hikaru.harness.desktop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopShutdownTest {
    @Test
    fun accountFailureDoesNotSkipHostAndPreservesFailure() = runTest {
        val closed = mutableListOf<String>()
        val accountFailure = IllegalStateException("account close failed")

        val thrown =
            assertFailsWith<IllegalStateException> {
                shutdownDesktopResources(
                    accountShutdown = {
                        closed += "account"
                        throw accountFailure
                    },
                    hostClose = { closed += "host" },
                )
            }

        assertEquals(listOf("account", "host"), closed)
        assertEquals(accountFailure, thrown)
    }

    @Test
    fun hostFailureIsReportedWhenAccountCloses() = runTest {
        val hostFailure = IllegalArgumentException("host close failed")
        val thrown =
            assertFailsWith<IllegalArgumentException> {
                shutdownDesktopResources(
                    accountShutdown = {},
                    hostClose = { throw hostFailure },
                )
            }
        assertEquals(hostFailure, thrown)
    }
}

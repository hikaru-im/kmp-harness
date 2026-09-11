package im.hikaru.harness.desktop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopShutdownTest {
    @Test
    fun accountInitializationFailureClosesAlreadyStartedHost() = runTest {
        val events = mutableListOf<String>()
        val initializationFailure = IllegalStateException("account initialization failed")

        val thrown =
            assertFailsWith<IllegalStateException> {
                openDesktopResources(
                    startHost = { events += "host-started"; "host" },
                    createAccount = { events += "account-started"; throw initializationFailure },
                    closeHost = { events += "$it-closed" },
                )
            }

        assertEquals(initializationFailure, thrown)
        assertEquals(listOf("host-started", "account-started", "host-closed"), events)
    }

    @Test
    fun accountConstructionFailureClosesCreatedTransport() {
        val transport = TestTransport()
        assertFailsWith<IllegalArgumentException> {
            createOwnedAccount(
                createTransport = { transport },
                createAccount = { throw IllegalArgumentException("account failed") },
            )
        }
        assertEquals(1, transport.closeCount)
    }

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

    private class TestTransport : AutoCloseable {
        var closeCount = 0
        override fun close() { closeCount += 1 }
    }
}

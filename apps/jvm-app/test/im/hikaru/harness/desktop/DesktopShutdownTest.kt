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
        val engine = TestTransport()
        val client = TestTransport()
        val transport = TestTransport()
        assertFailsWith<IllegalArgumentException> {
            createOwnedAccount(
                createEngine = { engine },
                createClient = { client },
                createTransport = { transport },
                createAccount = { throw IllegalArgumentException("account failed") },
            )
        }
        assertEquals(1, transport.closeCount)
        assertEquals(1, client.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun engineCreationFailureDoesNotLeakOrCreateLaterResources() {
        val created = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            createOwnedAccount(
                createEngine = { created += "engine"; throw IllegalStateException("engine failed") },
                createClient = { created += "client"; TestTransport() },
                createTransport = { created += "transport"; TestTransport() },
                createAccount = { TestTransport() },
            )
        }
        assertEquals(listOf("engine"), created)
    }

    @Test
    fun clientCreationFailureClosesEngine() {
        val engine = TestTransport()
        assertFailsWith<IllegalArgumentException> {
            createOwnedAccount(
                createEngine = { engine },
                createClient = { throw IllegalArgumentException("client failed") },
                createTransport = { TestTransport() },
                createAccount = { TestTransport() },
            )
        }
        assertEquals(1, engine.closeCount)
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

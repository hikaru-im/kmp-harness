package im.hikaru.harness.desktop

import im.hikaru.contracts.app.member.MemberAuthLoginResponse
import im.hikaru.contracts.app.member.MemberProfileResponse
import im.hikaru.contracts.app.member.MemberProfileUpdateRequest
import im.hikaru.contracts.app.system.AppTenantResponse
import im.hikaru.harness.client.account.AppSession
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberTransport
import im.hikaru.harness.client.account.MemoryAppSessionStore
import im.hikaru.harness.client.account.MemberAccount
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
        val transport = OwnedTestTransport(engine, client)
        assertFailsWith<IllegalArgumentException> {
            createOwnedAccount(
                createEngine = { engine },
                createClient = { client },
                createTransport = { _, _ -> transport },
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
                createTransport = { _, _ -> created += "transport"; TestTransport() },
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
                createTransport = { _, _ -> TestTransport() },
                createAccount = { TestTransport() },
            )
        }
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun transportCreationFailureClosesClientAndEngine() {
        val engine = TestTransport()
        val client = TestTransport()
        assertFailsWith<IllegalArgumentException> {
            createOwnedAccount(
                createEngine = { engine },
                createClient = { client },
                createTransport = { _, _ -> throw IllegalArgumentException("transport failed") },
                createAccount = { TestTransport() },
            )
        }
        assertEquals(1, client.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun successfulShutdownClosesAccountOwnedTransportClientAndEngine() = runTest {
        val engine = TestTransport()
        val client = TestTransport()
        val transport = ClosingMemberTransport(engine, client)
        val account = MemberAccount(transport, MemoryAppSessionStore())

        shutdownDesktopResources(account::shutdown, hostClose = {})

        assertEquals(1, transport.closeCount)
        assertEquals(1, client.closeCount)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun accountCloseFailureIsPreservedAndEachOwnedResourceClosesOnce() = runTest {
        val engine = TestTransport()
        val client = TestTransport()
        val transportFailure = IllegalStateException("transport close failed")
        val transport = ClosingMemberTransport(engine, client, transportFailure)
        val account = MemberAccount(transport, MemoryAppSessionStore())

        val thrown = assertFailsWith<IllegalStateException> {
            shutdownDesktopResources(account::shutdown, hostClose = {})
        }

        assertEquals(transportFailure, thrown)
        assertEquals(1, transport.closeCount)
        assertEquals(1, client.closeCount)
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

    private class OwnedTestTransport(
        private val engine: TestTransport,
        private val client: TestTransport,
        private val failure: Throwable? = null,
    ) : AutoCloseable {
        var closeCount = 0
        override fun close() {
            closeCount += 1
            client.close()
            engine.close()
            failure?.let { throw it }
        }
    }

    private class ClosingMemberTransport(
        private val engine: TestTransport,
        private val client: TestTransport,
        private val failure: Throwable? = null,
    ) : MemberTransport {
        var closeCount = 0

        override suspend fun login(target: BackendTenant, mobile: String, password: String): MemberAuthLoginResponse =
            error("unused")
        override suspend fun refresh(session: AppSession): MemberAuthLoginResponse = error("unused")
        override suspend fun logout(session: AppSession) = error("unused")
        override suspend fun profile(session: AppSession): MemberProfileResponse = error("unused")
        override suspend fun updateProfile(session: AppSession, request: MemberProfileUpdateRequest): Boolean =
            error("unused")
        override suspend fun tenant(target: BackendTenant, website: String): AppTenantResponse? = error("unused")

        override fun close() {
            closeCount += 1
            client.close()
            engine.close()
            failure?.let { throw it }
        }
    }
}

package im.hikaru.harness.client.account

import im.hikaru.contracts.app.member.MemberAuthLoginResponse
import im.hikaru.contracts.app.member.MemberProfileResponse
import im.hikaru.contracts.app.member.MemberProfileUpdateRequest
import im.hikaru.contracts.app.system.AppTenantResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberAccountTest {
    private val target = BackendTenant("https://api.example.test", 7)

    @Test
    fun `parallel expired requests share one refresh and preserve identity`() = runTest {
        val transport = FakeTransport()
        val account = MemberAccount(transport, MemoryAppSessionStore(), StandardTestDispatcher(testScheduler))
        account.selectTarget(target)
        account.login("13800000000", "password")

        val results = listOf(
            async { account.loadProfile() },
            async { account.loadProfile() },
        ).awaitAll()

        assertEquals(1, transport.refreshCalls)
        assertEquals(listOf(11L, 11L), results.map { it.id })
        assertEquals("refreshed", account.state.value.session?.accessToken)
        account.shutdown()
    }

    @Test
    fun `changing target cancels old login and rejects stale publication`() = runTest {
        val transport = FakeTransport().apply { loginGate = CompletableDeferred() }
        val account = MemberAccount(transport, MemoryAppSessionStore(), StandardTestDispatcher(testScheduler))
        account.selectTarget(target)
        val oldLogin = async { account.login("old", "password") }
        delay(1)
        account.selectTarget(target.copy(baseUrl = "https://other.example.test"))
        oldLogin.cancel()
        oldLogin.join()

        assertNull(account.state.value.session)
        assertEquals("https://other.example.test", account.state.value.target?.baseUrl)
        account.shutdown()
    }

    @Test
    fun `logout clears local state even when server is unavailable`() = runTest {
        val transport = FakeTransport().apply { failLogout = true }
        val account = MemberAccount(transport, MemoryAppSessionStore(), StandardTestDispatcher(testScheduler))
        account.selectTarget(target)
        account.login("13800000000", "password")
        account.logout()

        assertNull(account.state.value.session)
        assertTrue(transport.logoutCalls > 0)
        account.shutdown()
    }

    @Test
    fun `rejected refresh clears the recoverable login`() = runTest {
        val transport = FakeTransport().apply { refreshFailure = AccountException.Rejected(400) }
        val account = MemberAccount(transport, MemoryAppSessionStore(), StandardTestDispatcher(testScheduler))
        account.selectTarget(target)
        account.login("13800000000", "password")

        assertFailsWith<AccountException.Rejected> { account.loadProfile() }

        assertNull(account.state.value.session)
        account.shutdown()
    }

    @Test
    fun `network failure during refresh remains recoverable`() = runTest {
        val transport = FakeTransport().apply { refreshFailure = AccountException.Network() }
        val account = MemberAccount(transport, MemoryAppSessionStore(), StandardTestDispatcher(testScheduler))
        account.selectTarget(target)
        account.login("13800000000", "password")

        assertFailsWith<AccountException.Network> { account.loadProfile() }

        assertEquals("initial", account.state.value.session?.accessToken)
        account.shutdown()
    }

    @Test
    fun `invalid target credentials cannot be accepted`() {
        assertFailsWith<IllegalArgumentException> { BackendTenant("https://user:secret@example.test", 7) }
        assertFailsWith<IllegalArgumentException> { BackendTenant("https://api.example.test?token=secret", 7) }
    }

    private class FakeTransport : MemberTransport {
        var loginGate: CompletableDeferred<Unit>? = null
        var refreshCalls = 0
        var logoutCalls = 0
        var failLogout = false
        var refreshFailure: AccountException? = null
        private var profileCalls = 0

        override suspend fun login(target: BackendTenant, mobile: String, password: String): MemberAuthLoginResponse {
            loginGate?.await()
            return MemberAuthLoginResponse(userId = 11, accessToken = "initial", refreshToken = "refresh")
        }

        override suspend fun refresh(session: AppSession): MemberAuthLoginResponse {
            refreshCalls++
            refreshFailure?.let { throw it }
            return MemberAuthLoginResponse(userId = 11, accessToken = "refreshed", refreshToken = "refresh")
        }

        override suspend fun logout(session: AppSession) {
            logoutCalls++
            if (failLogout) error("offline")
        }

        override suspend fun profile(session: AppSession): MemberProfileResponse {
            profileCalls++
            if (session.accessToken == "initial") throw AccountException.Authentication()
            return MemberProfileResponse(id = 11, nickname = "Member")
        }

        override suspend fun updateProfile(session: AppSession, request: MemberProfileUpdateRequest) = true
        override suspend fun tenant(target: BackendTenant, website: String): AppTenantResponse? = null
        override fun close() = Unit
    }
}

package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ConnectionDescriptionRepositoryTest {
    private val description = HostDescription(ProtocolVersion.Current, HostId("real-description"), "Host returned by API")

    @Test fun successFailureRetryAndIncompatibleProtocolComeFromApi() = runTest {
        var response = ApiResult(code = 0, data = description)
        val repository = repository { response }
        repository.refresh()
        assertSame(description, repository.state.value.host)
        assertEquals(ConnectionsPhase.Online, repository.state.value.phase)
        response = ApiResult(code = 403, data = description, msg = "sensitive rejection")
        repository.refresh()
        assertNull(repository.state.value.host)
        assertEquals(ConnectionsPhase.Disconnected, repository.state.value.phase)
        assertFalse(repository.state.value.toString().contains("sensitive rejection"))
        response = ApiResult(code = 0, data = description.copy(protocolVersion = ProtocolVersion(2, 0)))
        repository.refresh()
        assertEquals(ConnectionsPhase.Incompatible, repository.state.value.phase)
        assertNotNull(repository.state.value.error)
        response = ApiResult(code = 0, data = description)
        repository.refresh()
        assertNull(repository.state.value.error)
    }

    @Test fun duplicateRefreshDoesNotIssueAnotherDescribeAndCancellationPropagates() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val repository = repository { calls++; entered.complete(Unit); release.await(); ApiResult(code = 0, data = description) }
        val job = launch { repository.refresh() }
        entered.await()
        assertTrue(repository.state.value.refreshing)
        repository.refresh()
        assertEquals(1, calls)
        job.cancelAndJoin()
        assertFalse(repository.state.value.refreshing)
        assertNull(repository.state.value.error)
        release.complete(Unit)
        repository.refresh()
        assertEquals(2, calls)
        assertEquals(ConnectionsPhase.Online, repository.state.value.phase)
        assertFailsWith<CancellationException> { repository { throw CancellationException("cancel") }.refresh() }
    }

    @Test fun exceptionAndMissingSuccessfulDataAreRetryableAndSanitized() = runTest {
        val failed = repository { error("access-token=synthetic-secret") }
        failed.refresh()
        assertEquals(ConnectionsPhase.Disconnected, failed.state.value.phase)
        assertFalse(failed.state.value.toString().contains("synthetic-secret"))
        val missing = repository { ApiResult(code = 0) }
        missing.refresh()
        assertNotNull(missing.state.value.error)
        assertNull(missing.state.value.host)
    }

    private fun repository(describeCall: suspend () -> ApiResult<HostDescription>) = ConnectionDescriptionRepository(object : Connection {
        override val host = object : HostApi { override suspend fun describe() = describeCall() }
        override val session: SessionApi get() = error("Session API must not be used by connection settings")
    })
}

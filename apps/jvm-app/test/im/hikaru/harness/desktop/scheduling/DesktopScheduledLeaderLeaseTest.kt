package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.scheduling.ScheduledScope
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlin.test.*

class DesktopScheduledLeaderLeaseTest {
    private val scope = ScheduledScope(HostId("local-test"), null, 1)

    @Test fun simultaneousContendersHaveOneLeaderAndLoserReleaseCannotUnlockIt() = runBlocking {
        temporary { path ->
            val leases = List(8) { DesktopScheduledLeaderLease(path) }
            try {
                val gate = CompletableDeferred<Unit>()
                val results = coroutineScope {
                    val attempts = leases.map { lease -> async(Dispatchers.Default) { gate.await(); lease.acquire(scope) } }
                    gate.complete(Unit)
                    attempts.awaitAll()
                }
                assertEquals(1, results.count { it })
                val winner = leases[results.indexOf(true)]
                val losers = leases.filter { it !== winner }
                losers.forEach { it.release(); assertFalse(it.isHeld()) }
                assertTrue(winner.isHeld())
                assertFalse(losers.first().acquire(scope.copy(generation = 2)))
                winner.release()
                assertTrue(losers.first().acquire(scope.copy(generation = 3)))
            } finally { leases.forEach { it.release() } }
        }
    }

    @Test fun sameInstanceCannotReenterButCanReacquireAfterIdempotentRelease() = runBlocking {
        temporary { path ->
            val lease = DesktopScheduledLeaderLease(path)
            try {
                assertTrue(lease.acquire(scope))
                assertFalse(lease.acquire(scope))
                assertTrue(lease.isHeld())
                lease.release()
                lease.release()
                assertFalse(lease.isHeld())
                assertTrue(Files.exists(path), "The stable lock inode must survive release")
                assertTrue(lease.acquire(scope))
            } finally { lease.release() }
        }
    }

    @Test fun normalizedPathsAndDifferentOwnersStillContendForOneProcessLease() = runBlocking {
        temporary { path ->
            val first = DesktopScheduledLeaderLease(path)
            val alias = DesktopScheduledLeaderLease(path.parent.resolve("unused/../" + path.fileName))
            try {
                assertTrue(first.acquire(scope))
                assertFalse(alias.acquire(scope.copy(hostId = HostId("other-test"))))
                first.release()
                assertTrue(alias.acquire(scope))
            } finally { first.release(); alias.release() }
        }
    }

    private suspend fun temporary(block: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("harness-scheduled-lease-")
        try { block(directory.resolve("leader.lock")) }
        finally { directory.toFile().deleteRecursively() }
    }
}

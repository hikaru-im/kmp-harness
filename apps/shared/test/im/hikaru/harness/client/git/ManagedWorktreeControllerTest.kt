package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedWorktreeControllerTest {
    @Test
    fun lateNonCancellableOldReadCannotLeakAcrossBindingOrClose() = runTest {
        val gate = CompletableDeferred<Unit>()
        val old = repository("/old", "old-host")
        val next = repository("/next", "next-host")
        val reads = Reads().apply { answer = { repo ->
            if (repo == old) withContext(NonCancellable) { gate.await() }
            ready(repo)
        } }
        val controller = ManagedWorktreeController(backgroundScope, reads)
        try {
            controller.bind(old)
            runCurrent()
            controller.bind(next)
            runCurrent()
            assertEquals(next, controller.state.value.inventory?.repository)
            gate.complete(Unit)
            runCurrent()
            assertEquals(next, controller.state.value.inventory?.repository)
            assertEquals("next-host", controller.state.value.inventory?.owner?.hostId?.value)
            controller.close()
            assertEquals(ManagedWorktreeState(), controller.state.value)
        } finally { gate.complete(Unit); controller.close() }
    }

    @Test
    fun failedRefreshRetainsStaleObservationWithUnknownProtectionsAndRetryReadsAgain() = runTest {
        val reads = Reads()
        val controller = ManagedWorktreeController(backgroundScope, reads)
        try {
            controller.bind(repository())
            runCurrent()
            val inventory = assertNotNull(controller.state.value.inventory)
            reads.answer = { ManagedWorktreeRead.Failed("actual read failed") }
            controller.refresh()
            runCurrent()
            assertEquals(ManagedWorktreePhase.Error, controller.state.value.phase)
            assertEquals(inventory, controller.state.value.inventory)
            assertTrue(controller.state.value.stale)
            assertEquals(ManagedProtectionAvailability.Unknown, inventory.entries.single().protection.sessions.availability)
            assertEquals(ManagedProtectionAvailability.Unavailable, inventory.entries.single().protection.pins.availability)
            reads.answer = { ready(it) }
            controller.refresh()
            runCurrent()
            assertEquals(ManagedWorktreePhase.Ready, controller.state.value.phase)
            assertFalse(controller.state.value.stale)
            assertEquals(3, reads.calls)
        } finally { controller.close() }
    }

    @Test
    fun registrationInvalidationReadsButRemoteAndUnboundNeverCallService() = runTest {
        val reads = Reads()
        val controller = ManagedWorktreeController(backgroundScope, reads)
        try {
            runCurrent()
            controller.bind(repository())
            runCurrent()
            reads.revision.value++
            runCurrent()
            assertEquals(2, reads.calls)
            val remote = repository().let { it.copy(binding = it.binding.copy(access = GitAccess.RemoteUnavailable)) }
            controller.bind(remote)
            controller.refresh()
            runCurrent()
            assertEquals(ManagedWorktreePhase.Unavailable, controller.state.value.phase)
            assertNull(controller.state.value.inventory)
            assertEquals(2, reads.calls)
            controller.bind(null)
            reads.revision.value++
            runCurrent()
            assertEquals(ManagedWorktreeState(), controller.state.value)
            assertEquals(2, reads.calls)
        } finally { controller.close() }
    }

    @Test
    fun mismatchedOwnerAndRepositoryResultsFailClosedWithoutInventingEmptyInventory() = runTest {
        val reads = Reads()
        val controller = ManagedWorktreeController(backgroundScope, reads)
        try {
            reads.answer = { repo -> ready(repo).let { result ->
                result.copy(inventory = result.inventory.copy(owner = result.inventory.owner.copy(hostId = HostId("foreign"))))
            } }
            controller.bind(repository())
            runCurrent()
            assertEquals(ManagedWorktreePhase.Error, controller.state.value.phase)
            assertNull(controller.state.value.inventory)
            reads.answer = { ready(repository("/foreign")) }
            controller.refresh()
            runCurrent()
            assertEquals(ManagedWorktreePhase.Error, controller.state.value.phase)
            assertNull(controller.state.value.inventory)
        } finally { controller.close() }
    }

    private class Reads : ManagedWorktreeRepository {
        override val revision = MutableStateFlow(0L)
        var calls = 0
        var answer: suspend (GitRepository) -> ManagedWorktreeRead = { ready(it) }
        override suspend fun read(repository: GitRepository): ManagedWorktreeRead { calls++; return answer(repository) }
    }

    @Test
    fun removalIsSingleFlightAndRepublishesOnlyThroughAFreshReadAfterRemoved() = runTest {
        val reads = RemovalReads()
        val controller = ManagedWorktreeController(backgroundScope, reads)
        try {
            controller.bind(repository())
            runCurrent()
            val plan = assertIs<ManagedWorktreeRemovalRead.Available>(
                controller.state.value.inventory!!.entries.single { it.root == reads.root }.removal).plan
            assertTrue(controller.state.value.canRemove(plan.root))
            val gate = CompletableDeferred<Unit>()
            reads.gate = gate
            controller.remove(plan)
            runCurrent()
            assertTrue(controller.state.value.removalBusy)
            assertEquals(1, reads.removals.size)
            // A second dispatch while busy must not reach the service twice.
            controller.remove(plan)
            runCurrent()
            assertEquals(1, reads.removals.size)
            gate.complete(Unit)
            runCurrent()
            assertFalse(controller.state.value.removalBusy)
            assertIs<ManagedWorktreeRemovalReceipt.Removed>(controller.state.value.removalReceipt)
            // Removed never flips a local gone bit; it forces a fresh read instead.
            assertEquals(2, reads.calls)
        } finally { controller.close() }
    }

    @Test
    fun mismatchedRemovalReceiptBecomesUnknownOutcomeWithoutRepublishingInventory() = runTest {
        val reads = RemovalReads()
        reads.answer = { request ->
            ManagedWorktreeRemovalReceipt.Removed(
                ManagedWorktreeRemovalRequest(request.plan.copy(root = "/somewhere-else")), emptyList())
        }
        val controller = ManagedWorktreeController(backgroundScope, reads)
        try {
            controller.bind(repository())
            runCurrent()
            val plan = assertIs<ManagedWorktreeRemovalRead.Available>(
                controller.state.value.inventory!!.entries.single { it.root == reads.root }.removal).plan
            controller.remove(plan)
            runCurrent()
            assertIs<ManagedWorktreeRemovalReceipt.UnknownOutcome>(controller.state.value.removalReceipt)
            // Only a matching Removed refreshes; a mismatched receipt proves nothing.
            assertEquals(1, reads.calls)
        } finally { controller.close() }
    }

    private class RemovalReads : ManagedWorktreeRepository {
        override val revision = MutableStateFlow(0L)
        val root = "/repo"
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        val removals = mutableListOf<ManagedWorktreeRemovalRequest>()
        var answer: suspend (ManagedWorktreeRemovalRequest) -> ManagedWorktreeRemovalReceipt = { request ->
            ManagedWorktreeRemovalReceipt.Removed(request, emptyList())
        }
        override suspend fun read(repository: GitRepository): ManagedWorktreeRead {
            calls++
            val owner = ManagedWorktreeOwner(repository.binding.identity, repository.binding.hostId, "/test-home")
            val plan = ManagedWorktreeRemovalPlan(owner, repository, repository.root, "a".repeat(40), "refs/heads/codex/managed")
            return ManagedWorktreeRead.Ready(ManagedWorktreeInventory(owner, repository, listOf(
                ManagedWorktreeEntry(repository.root, GitWorktree(repository.root, "a".repeat(40), "refs/heads/codex/managed"),
                    ManagedOwnership.Managed, removal = ManagedWorktreeRemovalRead.Available(plan))), 1))
        }
        override suspend fun removeWorktree(request: ManagedWorktreeRemovalRequest): ManagedWorktreeRemovalReceipt {
            removals += request
            gate?.await()
            return answer(request)
        }
    }

    companion object {
        private fun repository(root: String = "/repo", host: String = "local") = GitRepository(
            GitBinding(null, HostId(host), 1, GitAccess.LocalReadOnly), root, root + "/.git", root + "/.git")
        private fun ready(repo: GitRepository): ManagedWorktreeRead.Ready = ManagedWorktreeRead.Ready(
            ManagedWorktreeInventory(ManagedWorktreeOwner(repo.binding.identity, repo.binding.hostId, "/test-home"), repo,
                listOf(ManagedWorktreeEntry(repo.root, null, ManagedOwnership.UnknownOwnership)), 0))
    }
}

package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedWorktreeChatArchiveControllerTest {
    @Test
    fun archiveWithExactObservedKeyDispatchesOnceAndRefreshesAfterRevision() = runTest {
        val service = Archives()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent()
            controller.setArchived(repo.root, key, true)
            controller.setArchived(repo.root, key, true)
            runCurrent()
            assertTrue(controller.state.value.archiveBusy)
            assertEquals(1, service.requests.size)
            assertEquals(ManagedWorktreeChatArchiveRequest(repo, owner, repo.root, key, 0, true), service.requests.single())
            service.gate.complete(Unit); runCurrent()
            assertFalse(controller.state.value.archiveBusy)
            assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(controller.state.value.archiveReceipt)
            val disk = assertIs<ManagedWorktreeChatArchiveRead.Available>(controller.state.value.inventory?.archiveRead).snapshot
            assertEquals(1L, disk.revision); assertEquals(listOf(key), disk.archived)
            assertTrue(service.reads >= 2)
            controller.setArchived(repo.root, key, false); runCurrent()
            assertEquals(emptyList(), assertIs<ManagedWorktreeChatArchiveRead.Available>(controller.state.value.inventory?.archiveRead).snapshot.archived)
            assertEquals(2L, service.disk.revision)
        } finally { service.gate.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun unavailableUnknownStaleAndDuplicateClicksNeverDispatch() = runTest {
        val service = Archives()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent()
            for (archive in listOf<ManagedWorktreeChatArchiveRead?>(null,
                ManagedWorktreeChatArchiveRead.Unavailable(owner, "absent"), ManagedWorktreeChatArchiveRead.Unknown(owner, "bad bytes"),
                ManagedWorktreeChatArchiveRead.Available(service.disk.copy(owner = owner.copy(homeNamespace = "/foreign"))))) {
                service.transform = { it.copy(archiveRead = archive) }; controller.refresh(); runCurrent()
                controller.setArchived(repo.root, key, true); runCurrent()
                assertTrue(service.requests.isEmpty())
            }
            for (status in listOf(ManagedSessionObservationStatus.Unknown, ManagedSessionObservationStatus.Unavailable)) {
                service.transform = { inventory -> inventory.copy(entries = inventory.entries.map {
                    it.copy(sessionObservation = it.sessionObservation!!.copy(status = status))
                }) }; controller.refresh(); runCurrent()
                controller.setArchived(repo.root, key, true); runCurrent(); assertTrue(service.requests.isEmpty())
            }
            service.transform = { inventory -> inventory.copy(entries = inventory.entries.map { entry ->
                entry.copy(sessionObservation = entry.sessionObservation!!.copy(sessions = listOf(session.copy(pinKey = null))))
            }) }; controller.refresh(); runCurrent(); controller.setArchived(repo.root, key, true); runCurrent()
            assertTrue(service.requests.isEmpty())
            service.transform = { it }; controller.refresh(); runCurrent()
            controller.setArchived(repo.root, key, true, pageReady = false)
            controller.setArchived(repo.root, key.copy(headerFingerprint = "b".repeat(64)), true)
            service.readGate = CompletableDeferred(); controller.refresh(); runCurrent()
            assertTrue(controller.state.value.stale)
            controller.setArchived(repo.root, key, true); runCurrent(); assertTrue(service.requests.isEmpty())
            service.readGate!!.complete(Unit); runCurrent()
            controller.setArchived(repo.root, key, true); controller.setArchived(repo.root, key, true); runCurrent()
            assertEquals(1, service.requests.size)
        } finally { service.gate.complete(Unit); service.readGate?.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun lateReceiptCannotLeakAcrossBindingOrClose() = runTest {
        val service = Archives()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent(); controller.setArchived(repo.root, key, true); runCurrent()
            val next = repo.copy(binding = repo.binding.copy(generation = 2), root = "/next")
            controller.bind(next); runCurrent()
            service.gate.complete(Unit); runCurrent()
            assertEquals(next, controller.state.value.repository); assertNull(controller.state.value.archiveReceipt)
            assertFalse(controller.state.value.archiveBusy); assertEquals(listOf(key), service.disk.archived)
            controller.bind(repo); runCurrent()
            service.gate = CompletableDeferred()
            controller.setArchived(repo.root, key, false); runCurrent(); controller.close()
            service.gate.complete(Unit); runCurrent()
            assertEquals(ManagedWorktreeState(), controller.state.value)
            assertEquals(emptyList(), service.disk.archived, "Accepted commit survives page close")
        } finally { service.gate.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun revisionRefreshDuringAcceptedArchiveKeepsReceiptAndRefreshes() = runTest {
        val service = Archives()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent(); controller.setArchived(repo.root, key, true); runCurrent()
            service.revision.value++; runCurrent()
            assertTrue(controller.state.value.archiveBusy); assertEquals(2, service.reads)
            controller.setArchived(repo.root, key, true); runCurrent(); assertEquals(1, service.requests.size)
            service.gate.complete(Unit); runCurrent()
            val receipt = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(controller.state.value.archiveReceipt)
            assertEquals(service.requests.single(), receipt.request); assertEquals(1L, receipt.snapshot.revision)
            assertEquals(listOf(key), assertIs<ManagedWorktreeChatArchiveRead.Available>(controller.state.value.inventory?.archiveRead).snapshot.archived)
            controller.refresh(); runCurrent(); assertEquals(receipt, controller.state.value.archiveReceipt)
        } finally { service.gate.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun mismatchedOwnerRepositoryOrKeyReceiptNeverPublishesSuccess() = runTest {
        val mutations: List<(ManagedWorktreeChatArchiveReceipt.Applied) -> ManagedWorktreeChatArchiveReceipt> = listOf(
            { it.copy(request = it.request.copy(owner = owner.copy(homeNamespace = "/foreign"))) },
            { it.copy(request = it.request.copy(repository = repo.copy(root = "/foreign"))) },
            { it.copy(request = it.request.copy(key = key.copy(headerFingerprint = "b".repeat(64)))) },
            { it.copy(snapshot = it.snapshot.copy(owner = owner.copy(hostId = HostId("foreign")))) },
            { it.copy(snapshot = it.snapshot.copy(revision = 0)) },
            { it.copy(snapshot = it.snapshot.copy(revision = 2)) },
            { it.copy(snapshot = it.snapshot.copy(archived = emptyList())) },
        )
        for (mutation in mutations) {
            val service = Archives().apply { receiptTransform = mutation; emitRevision = false; gate.complete(Unit) }
            val controller = ManagedWorktreeController(backgroundScope, service)
            try {
                controller.bind(repo); runCurrent(); controller.setArchived(repo.root, key, true); runCurrent()
                assertIs<ManagedWorktreeChatArchiveReceipt.Unknown>(controller.state.value.archiveReceipt)
                assertFalse(controller.state.value.archiveBusy); assertEquals(1, service.reads)
                controller.refresh(); runCurrent(); assertEquals(1, service.requests.size, "Refresh must never resubmit")
            } finally { controller.close() }
        }
    }

    private class Archives : ManagedWorktreeRepository {
        override val revision = MutableStateFlow(0L)
        var disk = ManagedWorktreeChatArchiveSnapshot(owner)
        var gate = CompletableDeferred<Unit>()
        var readGate: CompletableDeferred<Unit>? = null
        var reads = 0
        var emitRevision = true
        val requests = mutableListOf<ManagedWorktreeChatArchiveRequest>()
        var transform: (ManagedWorktreeInventory) -> ManagedWorktreeInventory = { it }
        var receiptTransform: (ManagedWorktreeChatArchiveReceipt.Applied) -> ManagedWorktreeChatArchiveReceipt = { it }
        override suspend fun read(repository: GitRepository): ManagedWorktreeRead {
            reads++; readGate?.await()
            val entry = ManagedWorktreeEntry(repository.root, GitWorktree(repository.root, "a".repeat(40), "refs/heads/main"),
                ManagedOwnership.UnknownOwnership, sessionObservation = ManagedSessionObservation(owner, 1, 2,
                    ManagedSessionObservationStatus.Observed, listOf(session)))
            return ManagedWorktreeRead.Ready(transform(ManagedWorktreeInventory(owner, repository, listOf(entry), 0,
                archiveRead = ManagedWorktreeChatArchiveRead.Available(disk))))
        }
        override suspend fun setArchived(request: ManagedWorktreeChatArchiveRequest): ManagedWorktreeChatArchiveReceipt {
            requests += request; gate.await()
            disk = disk.copy(revision = disk.revision + 1, archived = if (request.desiredArchived) listOf(request.key) else emptyList())
            if (emitRevision) revision.value++
            return receiptTransform(ManagedWorktreeChatArchiveReceipt.Applied(request, disk))
        }
    }

    companion object {
        private val repo = GitRepository(GitBinding(null, HostId("local"), 1, GitAccess.LocalReadOnly), "/repo", "/repo/.git", "/repo/.git")
        private val owner = ManagedWorktreeOwner(null, repo.binding.hostId, "/test-home")
        private val key = ChatArchiveKey("chat", "a".repeat(64))
        private val session = ManagedObservedSession(key.id, repo.root, null, setOf(ManagedSessionSource.Live),
            PinKey(key.id, key.headerFingerprint))
    }
}


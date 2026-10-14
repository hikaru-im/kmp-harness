package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedWorktreePinControllerTest {
    @Test
    fun pinWithExactObservedKeyDispatchesOnceAndRefreshesAfterRevision() = runTest {
        val service = Pins()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent()
            controller.setPinned(repo.root, key, true)
            controller.setPinned(repo.root, key, true)
            runCurrent()
            assertTrue(controller.state.value.pinBusy)
            assertEquals(1, service.requests.size)
            assertEquals(ManagedWorktreePinRequest(repo, owner, repo.root, key, 0, true), service.requests.single())
            service.gate.complete(Unit); runCurrent()
            assertFalse(controller.state.value.pinBusy)
            assertIs<ManagedWorktreePinReceipt.Applied>(controller.state.value.pinReceipt)
            val disk = assertIs<ManagedWorktreePinRead.Available>(controller.state.value.inventory?.pinRead).snapshot
            assertEquals(1L, disk.revision); assertEquals(listOf(key), disk.pins)
            assertTrue(service.reads >= 2)
            controller.setPinned(repo.root, key, false); runCurrent()
            assertEquals(emptyList(), assertIs<ManagedWorktreePinRead.Available>(controller.state.value.inventory?.pinRead).snapshot.pins)
            assertEquals(2L, service.disk.revision)
        } finally { service.gate.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun unavailableUnknownStaleAndDuplicateClicksNeverDispatch() = runTest {
        val service = Pins()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent()
            for (pins in listOf<ManagedWorktreePinRead?>(null,
                ManagedWorktreePinRead.Unavailable(owner, "absent"), ManagedWorktreePinRead.Unknown(owner, "bad bytes"),
                ManagedWorktreePinRead.Available(service.disk.copy(owner = owner.copy(homeNamespace = "/foreign"))))) {
                service.transform = { it.copy(pinRead = pins) }; controller.refresh(); runCurrent()
                controller.setPinned(repo.root, key, true); runCurrent()
                assertTrue(service.requests.isEmpty())
            }
            for (status in listOf(ManagedSessionObservationStatus.Unknown, ManagedSessionObservationStatus.Unavailable)) {
                service.transform = { inventory -> inventory.copy(entries = inventory.entries.map {
                    it.copy(sessionObservation = it.sessionObservation!!.copy(status = status))
                }) }; controller.refresh(); runCurrent()
                controller.setPinned(repo.root, key, true); runCurrent(); assertTrue(service.requests.isEmpty())
            }
            service.transform = { inventory -> inventory.copy(entries = inventory.entries.map { entry ->
                entry.copy(sessionObservation = entry.sessionObservation!!.copy(sessions = listOf(session.copy(pinKey = null))))
            }) }; controller.refresh(); runCurrent(); controller.setPinned(repo.root, key, true); runCurrent()
            assertTrue(service.requests.isEmpty())
            service.transform = { it }; controller.refresh(); runCurrent()
            controller.setPinned(repo.root, key, true, pageReady = false)
            controller.setPinned(repo.root, key.copy(headerFingerprint = "b".repeat(64)), true)
            service.readGate = CompletableDeferred(); controller.refresh(); runCurrent()
            assertTrue(controller.state.value.stale)
            controller.setPinned(repo.root, key, true); runCurrent(); assertTrue(service.requests.isEmpty())
            service.readGate!!.complete(Unit); runCurrent()
            controller.setPinned(repo.root, key, true); controller.setPinned(repo.root, key, true); runCurrent()
            assertEquals(1, service.requests.size)
        } finally { service.gate.complete(Unit); service.readGate?.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun lateReceiptCannotLeakAcrossBindingOrClose() = runTest {
        val service = Pins()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent(); controller.setPinned(repo.root, key, true); runCurrent()
            val next = repo.copy(binding = repo.binding.copy(generation = 2), root = "/next")
            controller.bind(next); runCurrent()
            service.gate.complete(Unit); runCurrent()
            assertEquals(next, controller.state.value.repository); assertNull(controller.state.value.pinReceipt)
            assertFalse(controller.state.value.pinBusy); assertEquals(listOf(key), service.disk.pins)
            controller.bind(repo); runCurrent()
            service.gate = CompletableDeferred()
            controller.setPinned(repo.root, key, false); runCurrent(); controller.close()
            service.gate.complete(Unit); runCurrent()
            assertEquals(ManagedWorktreeState(), controller.state.value)
            assertEquals(emptyList(), service.disk.pins, "Accepted commit survives page close")
        } finally { service.gate.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun revisionRefreshDuringAcceptedPinKeepsReceiptAndRefreshes() = runTest {
        val service = Pins()
        val controller = ManagedWorktreeController(backgroundScope, service)
        try {
            controller.bind(repo); runCurrent(); controller.setPinned(repo.root, key, true); runCurrent()
            service.revision.value++; runCurrent()
            assertTrue(controller.state.value.pinBusy); assertEquals(2, service.reads)
            controller.setPinned(repo.root, key, true); runCurrent(); assertEquals(1, service.requests.size)
            service.gate.complete(Unit); runCurrent()
            val receipt = assertIs<ManagedWorktreePinReceipt.Applied>(controller.state.value.pinReceipt)
            assertEquals(service.requests.single(), receipt.request); assertEquals(1L, receipt.snapshot.revision)
            assertEquals(listOf(key), assertIs<ManagedWorktreePinRead.Available>(controller.state.value.inventory?.pinRead).snapshot.pins)
            controller.refresh(); runCurrent(); assertEquals(receipt, controller.state.value.pinReceipt)
        } finally { service.gate.complete(Unit); runCurrent(); controller.close() }
    }

    @Test
    fun mismatchedOwnerRepositoryOrKeyReceiptNeverPublishesSuccess() = runTest {
        val mutations: List<(ManagedWorktreePinReceipt.Applied) -> ManagedWorktreePinReceipt> = listOf(
            { it.copy(request = it.request.copy(owner = owner.copy(homeNamespace = "/foreign"))) },
            { it.copy(request = it.request.copy(repository = repo.copy(root = "/foreign"))) },
            { it.copy(request = it.request.copy(key = key.copy(headerFingerprint = "b".repeat(64)))) },
            { it.copy(snapshot = it.snapshot.copy(owner = owner.copy(hostId = HostId("foreign")))) },
            { it.copy(snapshot = it.snapshot.copy(revision = 0)) },
            { it.copy(snapshot = it.snapshot.copy(revision = 2)) },
            { it.copy(snapshot = it.snapshot.copy(pins = emptyList())) },
        )
        for (mutation in mutations) {
            val service = Pins().apply { receiptTransform = mutation; emitRevision = false; gate.complete(Unit) }
            val controller = ManagedWorktreeController(backgroundScope, service)
            try {
                controller.bind(repo); runCurrent(); controller.setPinned(repo.root, key, true); runCurrent()
                assertIs<ManagedWorktreePinReceipt.Unknown>(controller.state.value.pinReceipt)
                assertFalse(controller.state.value.pinBusy); assertEquals(1, service.reads)
                controller.refresh(); runCurrent(); assertEquals(1, service.requests.size, "Refresh must never resubmit")
            } finally { controller.close() }
        }
    }

    private class Pins : ManagedWorktreeRepository {
        override val revision = MutableStateFlow(0L)
        var disk = ManagedWorktreePinSnapshot(owner)
        var gate = CompletableDeferred<Unit>()
        var readGate: CompletableDeferred<Unit>? = null
        var reads = 0
        var emitRevision = true
        val requests = mutableListOf<ManagedWorktreePinRequest>()
        var transform: (ManagedWorktreeInventory) -> ManagedWorktreeInventory = { it }
        var receiptTransform: (ManagedWorktreePinReceipt.Applied) -> ManagedWorktreePinReceipt = { it }
        override suspend fun read(repository: GitRepository): ManagedWorktreeRead {
            reads++; readGate?.await()
            val entry = ManagedWorktreeEntry(repository.root, GitWorktree(repository.root, "a".repeat(40), "refs/heads/main"),
                ManagedOwnership.UnknownOwnership, sessionObservation = ManagedSessionObservation(owner, 1, 2,
                    ManagedSessionObservationStatus.Observed, listOf(session)))
            return ManagedWorktreeRead.Ready(transform(ManagedWorktreeInventory(owner, repository, listOf(entry), 0,
                ManagedWorktreePinRead.Available(disk))))
        }
        override suspend fun setPinned(request: ManagedWorktreePinRequest): ManagedWorktreePinReceipt {
            requests += request; gate.await()
            disk = disk.copy(revision = disk.revision + 1, pins = if (request.desiredPinned) listOf(request.key) else emptyList())
            if (emitRevision) revision.value++
            return receiptTransform(ManagedWorktreePinReceipt.Applied(request, disk))
        }
    }

    companion object {
        private val repo = GitRepository(GitBinding(null, HostId("local"), 1, GitAccess.LocalReadOnly), "/repo", "/repo/.git", "/repo/.git")
        private val owner = ManagedWorktreeOwner(null, repo.binding.hostId, "/test-home")
        private val key = PinKey("chat", "a".repeat(64))
        private val session = ManagedObservedSession(key.id, repo.root, null, setOf(ManagedSessionSource.Live), key)
    }
}

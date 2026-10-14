package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlin.test.*

class DesktopWorktreePinIntegrationTest {
    @Test
    fun realLiveAndDurablePinRoundTripWithoutSessionOrGitWrites() = withPins { p ->
        val f = p.fixture
        f.registerLinkedWorktree()
        val history = f.session("durable-parent")
        history.session.append(SessionEventKeys.TurnStart, TurnStartEvent(1))
        f.context.persistence.flush(history.session); history.dispose()
        val child = f.session("dual-child", parent = "durable-parent")
        f.context.persistence.flush(child.session)
        f.session("live-child", f.nested.toString(), "dual-child")
        f.session("linked-live", f.linked.toString())
        val before = pinSourceEvidence(f)
        val lifecycle = java.util.concurrent.atomic.AtomicInteger()
        f.context.on(SessionEvents.Created) { lifecycle.incrementAndGet() }
        f.context.on(SessionEvents.Disposed) { lifecycle.incrementAndGet() }
        for ((id, root) in listOf("durable-parent" to f.root, "dual-child" to f.root,
            "live-child" to f.root, "linked-live" to f.linked)) {
            val request = p.request(id, root = root)
            val applied = assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(request))
            assertEquals(request, applied.request); assertNull(applied.durabilityWarning)
            assertTrue(request.key in DesktopWorktreePinStore(p.path).read(f.owner).snapshot.pins)
        }
        val durable = p.inventory().entries.single { it.root == f.root.toString() }.sessionObservation!!.sessions.single { it.id == "durable-parent" }
        assertEquals(setOf(ManagedSessionSource.Durable), durable.sources)
        val unpin = p.request("dual-child", desired = false)
        val applied = assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(unpin))
        assertEquals(5L, applied.snapshot.revision); assertFalse(unpin.key in applied.snapshot.pins)
        val noOp = p.request("dual-child", desired = false); val bytes = Files.readAllBytes(p.path)
        assertEquals(5L, assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(noOp)).snapshot.revision)
        assertContentEquals(bytes, Files.readAllBytes(p.path))
        assertEquals(before, pinSourceEvidence(f)); assertEquals(0, lifecycle.get())
    }

    @Test
    fun freshBootstrapReadsDurablePinWithoutLoadingHistory() = withPins { p ->
        val f = p.fixture
        val history = f.session("durable-unloaded")
        f.context.persistence.flush(history.session); history.dispose()
        // Header-only readers must tolerate an event log that could not be loaded.
        f.database { it.sessions().upsertEvents(listOf(SessionEventRow("durable-unloaded", 0, "user/message", 1,
            "{ broken event", "APPEND", null, null))) }
        val request = p.request("durable-unloaded")
        assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(request))
        val rows = f.database { it.sessions().listSessions() }; val events = f.database { it.sessions().findEvents("durable-unloaded") }
        val registry = Files.readAllBytes(f.registry); val pins = Files.readAllBytes(p.path)
        f.host.profiledHost.close()
        val fresh = DesktopWorktreeSessionObserver.startDesktopHost(f.home)
        try {
            val context = fresh.profiledHost.host.runtime.context
            val binding = GitBinding(null, fresh.profiledHost.host.profile.hostDescription.hostId, 2, GitAccess.LocalReadOnly)
            val owner = ManagedWorktreeOwner(null, binding.hostId, f.home.toString())
            val actual = DesktopGitBackend(binding.hostId); val repo = actual.discover(binding, f.root.toString())
            val repository = DesktopManagedWorktreeRepository(actual, DesktopManagedWorktreeStore(f.registry),
                ownerValidator = { owner }, sessionObserver = fresh.sessionObserver, pinStore = DesktopWorktreePinStore(p.path))
            assertTrue(context.sessions.list().isEmpty())
            val inventory = assertIs<ManagedWorktreeRead.Ready>(repository.read(repo)).inventory
            val observed = inventory.entries.single().sessionObservation!!.sessions.single()
            assertEquals(request.key, observed.pinKey); assertEquals(setOf(ManagedSessionSource.Durable), observed.sources)
            assertTrue(request.key in assertIs<ManagedWorktreePinRead.Available>(inventory.pinRead).snapshot.pins)
            assertTrue(context.sessions.list().isEmpty(), "Fresh bootstrap observation must not load history")
            assertEquals(rows, f.database { it.sessions().listSessions() })
            assertEquals(events, f.database { it.sessions().findEvents("durable-unloaded") })
            assertContentEquals(registry, Files.readAllBytes(f.registry)); assertContentEquals(pins, Files.readAllBytes(p.path))
        } finally { fresh.profiledHost.close() }
    }

    @Test
    fun staleRevisionAndChangedHeaderRejectBeforeWrite() = withPins { p ->
        val f = p.fixture; val live = f.session("reused-id")
        f.context.persistence.flush(live.session)
        val original = p.request("reused-id")
        assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(original))
        val firstBytes = Files.readAllBytes(p.path)
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(original.copy(desiredPinned = false)))
        assertContentEquals(firstBytes, Files.readAllBytes(p.path))
        val captured = p.request("reused-id", desired = false)
        val row = f.database { assertNotNull(it.sessions().findSession("reused-id")) }
        f.database { it.sessions().upsertSession(row.copy(createdAt = row.createdAt + 1)) }
        // A live/durable identity conflict is not an exact observed key.
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(captured))
        assertContentEquals(firstBytes, Files.readAllBytes(p.path))
        live.dispose()
        val changed = p.request("reused-id")
        assertNotEquals(original.key, changed.key)
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(captured))
        assertContentEquals(firstBytes, Files.readAllBytes(p.path))
        assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(changed))
        val snapshot = DesktopWorktreePinStore(p.path).read(f.owner).snapshot
        assertTrue(original.key in snapshot.pins, "Old fingerprint stays pinned and never rebinds")
        assertTrue(changed.key in snapshot.pins); assertEquals(2, snapshot.pins.size)
        val finalRequest = p.request("reused-id", desired = false)
        val finalBytes = Files.readAllBytes(p.path)
        val actual = f.actual
        var treeReads = 0
        val changedDuringGitFence = object : GitBackend by actual {
            override suspend fun worktrees(repository: GitRepository): List<GitWorktree> {
                val trees = actual.worktrees(repository)
                if (++treeReads == 2) f.database { it.sessions().upsertSession(row.copy(createdAt = row.createdAt + 2)) }
                return trees
            }
        }
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository(backend = changedDuringGitFence).setPinned(finalRequest))
        assertContentEquals(finalBytes, Files.readAllBytes(p.path))
    }

    @Test
    fun ownerOrCapturedServiceReplacementCannotWrite() = withPins { p ->
        val f = p.fixture; f.session("private")
        val request = p.request("private"); val before = pinSourceEvidence(f)
        val actual = f.actual
        for (transform in listOf<(List<GitWorktree>) -> List<GitWorktree>>(
            { emptyList() }, { trees -> trees.map { it.copy(bare = true) } },
            { trees -> trees.map { it.copy(prunable = true) } })) {
            val invalidTarget = object : GitBackend by actual {
                override suspend fun worktrees(repository: GitRepository): List<GitWorktree> =
                    transform(actual.worktrees(repository))
            }
            assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository(backend = invalidTarget).setPinned(request))
            assertFalse(Files.exists(p.path))
        }
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(
            request.copy(repository = request.repository.copy(gitDir = request.repository.commonDir + "/objects"))))
        assertFalse(Files.exists(p.path))
        f.currentOwner = null
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(request)); assertFalse(Files.exists(p.path))
        f.currentOwner = f.owner
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(request.copy(owner = f.owner.copy(homeNamespace = f.sibling.toString()))))
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            val binding = isolated.provide(SessionPersistenceKey, f.context.persistence)
            val captured = f.capture(isolated)
            val repository = p.repository(captured)
            binding.dispose()
            isolated.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {})
            assertIs<ManagedWorktreePinReceipt.Rejected>(repository.setPinned(request)); assertFalse(Files.exists(p.path))
            isolated.dispose()
            assertIs<ManagedWorktreePinReceipt.Rejected>(repository.setPinned(request)); assertFalse(Files.exists(p.path))
        } finally { isolated.dispose() }
        val liveContext = f.context.isolate(SessionKey)
        try {
            val liveBinding = liveContext.provide(SessionKey, f.context.sessions)
            val captured = f.capture(liveContext)
            liveBinding.dispose()
            val other = DesktopWorktreeSessionObserver.startDesktopHost(Files.createDirectories(f.directory.resolve("other-pin-home")))
            try {
                liveContext.provide(SessionKey, other.profiledHost.host.runtime.context.sessions)
                assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository(captured).setPinned(request))
                assertFalse(Files.exists(p.path))
            } finally { other.profiledHost.close() }
        } finally { liveContext.dispose() }
        // Revoke ownership from a real catalog read, after initial validation.
        val source = f.context.isolate(SessionPersistenceKey)
        try {
            source.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {
                override suspend fun listHeaders(): List<SessionHeader>? {
                    val headers = f.context.persistence.listHeaders(); f.currentOwner = null; return headers
                }
            })
            assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository(f.capture(source)).setPinned(request))
            assertFalse(Files.exists(p.path))
        } finally { f.currentOwner = f.owner; source.dispose() }
        assertEquals(before, pinSourceEvidence(f))
    }

    @Test
    fun diskConflictAndMalformedStoreRetainBytes() = withPins { p ->
        val f = p.fixture; f.session("chat")
        val request = p.request("chat"); val before = pinSourceEvidence(f)
        val initial = p.store.read(f.owner); p.store.compareAndSet(initial, initial.snapshot.copy(revision = 1))
        val current = request.copy(expectedRevision = 1)
        val conflictStore = object : DesktopWorktreePinStore(p.path) {
            override fun compareAndSet(expected: Observation, update: ManagedWorktreePinSnapshot): Observation {
                Files.write(p.path, Files.readAllBytes(p.path) + "\n ".toByteArray())
                return super.compareAndSet(expected, update)
            }
        }
        val bytes = Files.readAllBytes(p.path) + "\n ".toByteArray()
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository(store = conflictStore).setPinned(current))
        assertContentEquals(bytes, Files.readAllBytes(p.path))
        val invalid = "{ bad pins".toByteArray(); Files.write(p.path, invalid)
        assertIs<ManagedWorktreePinReceipt.Rejected>(p.repository.setPinned(current))
        assertIs<ManagedWorktreePinRead.Unknown>(p.inventory().pinRead)
        assertContentEquals(invalid, Files.readAllBytes(p.path)); assertEquals(before, pinSourceEvidence(f))
    }

    @Test
    fun directorySyncFailureReturnsCommittedWarning() = withPins { p ->
        val f = p.fixture; f.session("chat"); val before = pinSourceEvidence(f)
        val service = p.repository(store = DesktopWorktreePinStore(p.path) { error("fsync directory") })
        val request = p.request("chat")
        val receipt = assertIs<ManagedWorktreePinReceipt.Applied>(service.setPinned(request))
        assertEquals(request, receipt.request); assertNotNull(receipt.durabilityWarning)
        assertEquals(1L, receipt.snapshot.revision)
        assertEquals(receipt.snapshot, DesktopWorktreePinStore(p.path).read(f.owner).snapshot)
        assertEquals(before, pinSourceEvidence(f))
    }

    private fun withPins(block: suspend (WorktreePinFixture) -> Unit) = runBlocking {
        val p = WorktreePinFixture()
        try { p.fixture.open(); block(p) } finally { p.close() }
    }
}

/** Adds only the independent pin service to the unchanged production observation fixture. */
internal class WorktreePinFixture : AutoCloseable {
    val fixture = WorktreeObservationFixture()
    val path: Path = fixture.home.resolve("managed-chat-pins.json")
    val store: DesktopWorktreePinStore get() = DesktopWorktreePinStore(path)
    val repository: DesktopManagedWorktreeRepository by lazy { repository() }
    fun repository(observer: DesktopWorktreeSessionObserver = fixture.host.sessionObserver,
        store: DesktopWorktreePinStore = this.store, backend: GitBackend = fixture.actual,
        archiveStore: DesktopWorktreeChatArchiveStore? = null) =
        DesktopManagedWorktreeRepository(backend, DesktopManagedWorktreeStore(fixture.registry),
            ownerValidator = { candidate -> if (candidate == fixture.binding) fixture.currentOwner else null },
            sessionObserver = observer, pinStore = store, archiveStore = archiveStore)
    suspend fun inventory(): ManagedWorktreeInventory = assertIs<ManagedWorktreeRead.Ready>(repository.read(fixture.gitRepository)).inventory
    suspend fun request(id: String, desired: Boolean = true, root: Path = fixture.root): ManagedWorktreePinRequest {
        val current = inventory(); val snapshot = assertIs<ManagedWorktreePinRead.Available>(current.pinRead).snapshot
        val observed = current.entries.single { it.root == root.toString() }.sessionObservation!!
        assertEquals(ManagedSessionObservationStatus.Observed, observed.status)
        val key = assertNotNull(observed.sessions.single { it.id == id }.pinKey)
        return ManagedWorktreePinRequest(current.repository, current.owner, root.toString(), key, snapshot.revision, desired)
    }
    suspend fun chatArchiveRequest(id: String, desired: Boolean = true, root: Path = fixture.root): ManagedWorktreeChatArchiveRequest {
        val current = inventory(); val snapshot = assertIs<ManagedWorktreeChatArchiveRead.Available>(current.archiveRead).snapshot
        val observed = current.entries.single { it.root == root.toString() }.sessionObservation!!
        assertEquals(ManagedSessionObservationStatus.Observed, observed.status)
        val key = assertNotNull(observed.sessions.single { it.id == id }.pinKey)
        return ManagedWorktreeChatArchiveRequest(current.repository, current.owner, root.toString(),
            ChatArchiveKey(key.id, key.headerFingerprint), snapshot.revision, desired)
    }
    override fun close() = fixture.close()
}

internal suspend fun pinSourceEvidence(f: WorktreeObservationFixture): Map<String, String> {
    val result = linkedMapOf<String, String>()
    fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    result["registry"] = digest(Files.readAllBytes(f.registry))
    val rows = f.database { it.sessions().listSessions().sortedBy { row -> row.sessionId } }
    result["roomRows"] = rows.toString()
    result["roomEvents"] = f.database { db -> rows.associate { it.sessionId to db.sessions().findEvents(it.sessionId) } }.toString()
    result["live"] = f.context.sessions.list().map { Triple(System.identityHashCode(it), it.header, it.events()) }.toString()
    Files.walk(Path.of(f.gitRepository.commonDir)).use { paths ->
        paths.filter { Files.isRegularFile(it) }.sorted().forEach { path -> result["git:$path"] = digest(Files.readAllBytes(path)) }
    }
    for (root in listOf(f.root, f.linked).filter { Files.isDirectory(it) })
        result["tracked:$root"] = digest(Files.readAllBytes(root.resolve("tracked.txt")))
    return result
}

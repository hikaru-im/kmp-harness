package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import im.hikaru.harness.agent.AgentArchiveBegin
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.agents
import im.hikaru.harness.llm.LlmCallConfig
import im.hikaru.harness.llm.ReasoningModeId
import im.hikaru.harness.llm.ReasoningOptions
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlin.test.*

class DesktopWorktreeChatArchiveIntegrationTest {
    @Test
    fun realLiveAndDurableArchiveRoundTripWithoutSessionOrGitWrites() = withArchives { p ->
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
            val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(request))
            assertEquals(request, applied.request); assertNull(applied.durabilityWarning)
            assertTrue(request.key in DesktopWorktreeChatArchiveStore(p.path).read(f.owner).snapshot.archived)
        }
        val durable = p.inventory().entries.single { it.root == f.root.toString() }.sessionObservation!!.sessions.single { it.id == "durable-parent" }
        assertEquals(setOf(ManagedSessionSource.Durable), durable.sources)
        val unarchive = p.request("dual-child", desired = false)
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(unarchive))
        assertEquals(5L, applied.snapshot.revision); assertFalse(unarchive.key in applied.snapshot.archived)
        val noOp = p.request("dual-child", desired = false); val bytes = Files.readAllBytes(p.path)
        assertEquals(5L, assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(noOp)).snapshot.revision)
        assertContentEquals(bytes, Files.readAllBytes(p.path))
        assertEquals(before, pinSourceEvidence(f)); assertEquals(0, lifecycle.get())
    }

    @Test
    fun freshBootstrapReadsDurableArchiveWithoutLoadingHistory() = withArchives { p ->
        val f = p.fixture
        val history = f.session("durable-unloaded")
        f.context.persistence.flush(history.session); history.dispose()
        // Header-only readers must tolerate an event log that could not be loaded.
        f.database { it.sessions().upsertEvents(listOf(SessionEventRow("durable-unloaded", 0, "user/message", 1,
            "{ broken event", "APPEND", null, null))) }
        val request = p.request("durable-unloaded")
        assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(request))
        val rows = f.database { it.sessions().listSessions() }; val events = f.database { it.sessions().findEvents("durable-unloaded") }
        val registry = Files.readAllBytes(f.registry); val archives = Files.readAllBytes(p.path)
        f.host.profiledHost.close()
        val fresh = DesktopWorktreeSessionObserver.startDesktopHost(f.home)
        try {
            val context = fresh.profiledHost.host.runtime.context
            val binding = GitBinding(null, fresh.profiledHost.host.profile.hostDescription.hostId, 2, GitAccess.LocalReadOnly)
            val owner = ManagedWorktreeOwner(null, binding.hostId, f.home.toString())
            val actual = DesktopGitBackend(binding.hostId); val repo = actual.discover(binding, f.root.toString())
            val repository = DesktopManagedWorktreeRepository(actual, DesktopManagedWorktreeStore(f.registry),
                ownerValidator = { owner }, sessionObserver = fresh.sessionObserver, archiveStore = DesktopWorktreeChatArchiveStore(p.path))
            assertTrue(context.sessions.list().isEmpty())
            val inventory = assertIs<ManagedWorktreeRead.Ready>(repository.read(repo)).inventory
            val observed = inventory.entries.single().sessionObservation!!.sessions.single()
            assertEquals(request.key.id, observed.id)
            assertEquals(request.key.headerFingerprint, observed.pinKey?.headerFingerprint)
            assertEquals(setOf(ManagedSessionSource.Durable), observed.sources)
            assertTrue(request.key in assertIs<ManagedWorktreeChatArchiveRead.Available>(inventory.archiveRead).snapshot.archived)
            assertTrue(context.sessions.list().isEmpty(), "Fresh bootstrap observation must not load history")
            assertEquals(rows, f.database { it.sessions().listSessions() })
            assertEquals(events, f.database { it.sessions().findEvents("durable-unloaded") })
            assertContentEquals(registry, Files.readAllBytes(f.registry)); assertContentEquals(archives, Files.readAllBytes(p.path))
        } finally { fresh.profiledHost.close() }
    }

    @Test
    fun staleRevisionAndChangedHeaderRejectBeforeWrite() = withArchives { p ->
        val f = p.fixture; val live = f.session("reused-id")
        f.context.persistence.flush(live.session)
        val original = p.request("reused-id")
        assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(original))
        val firstBytes = Files.readAllBytes(p.path)
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(original.copy(desiredArchived = false)))
        assertContentEquals(firstBytes, Files.readAllBytes(p.path))
        val captured = p.request("reused-id", desired = false)
        val row = f.database { assertNotNull(it.sessions().findSession("reused-id")) }
        f.database { it.sessions().upsertSession(row.copy(createdAt = row.createdAt + 1)) }
        // A live/durable identity conflict is not an exact observed key.
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(captured))
        assertContentEquals(firstBytes, Files.readAllBytes(p.path))
        live.dispose()
        val changed = p.request("reused-id")
        assertNotEquals(original.key, changed.key)
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(captured))
        assertContentEquals(firstBytes, Files.readAllBytes(p.path))
        assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(changed))
        val snapshot = DesktopWorktreeChatArchiveStore(p.path).read(f.owner).snapshot
        assertTrue(original.key in snapshot.archived, "Old fingerprint stays archived and never rebinds")
        assertTrue(changed.key in snapshot.archived); assertEquals(2, snapshot.archived.size)
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
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository(backend = changedDuringGitFence).setArchived(finalRequest))
        assertContentEquals(finalBytes, Files.readAllBytes(p.path))
    }

    @Test
    fun ownerOrCapturedServiceReplacementCannotWrite() = withArchives { p ->
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
            assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository(backend = invalidTarget).setArchived(request))
            assertFalse(Files.exists(p.path))
        }
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(
            request.copy(repository = request.repository.copy(gitDir = request.repository.commonDir + "/objects"))))
        assertFalse(Files.exists(p.path))
        f.currentOwner = null
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(request)); assertFalse(Files.exists(p.path))
        f.currentOwner = f.owner
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(request.copy(owner = f.owner.copy(homeNamespace = f.sibling.toString()))))
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            val binding = isolated.provide(SessionPersistenceKey, f.context.persistence)
            val captured = f.capture(isolated)
            val repository = p.repository(captured)
            binding.dispose()
            isolated.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {})
            assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(repository.setArchived(request)); assertFalse(Files.exists(p.path))
            isolated.dispose()
            assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(repository.setArchived(request)); assertFalse(Files.exists(p.path))
        } finally { isolated.dispose() }
        val liveContext = f.context.isolate(SessionKey)
        try {
            val liveBinding = liveContext.provide(SessionKey, f.context.sessions)
            val captured = f.capture(liveContext)
            liveBinding.dispose()
            val other = DesktopWorktreeSessionObserver.startDesktopHost(Files.createDirectories(f.directory.resolve("other-archive-home")))
            try {
                liveContext.provide(SessionKey, other.profiledHost.host.runtime.context.sessions)
                assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository(captured).setArchived(request))
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
            assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository(f.capture(source)).setArchived(request))
            assertFalse(Files.exists(p.path))
        } finally { f.currentOwner = f.owner; source.dispose() }
        assertEquals(before, pinSourceEvidence(f))
    }

    @Test
    fun diskConflictAndMalformedStoreRetainBytes() = withArchives { p ->
        val f = p.fixture; f.session("chat")
        val request = p.request("chat"); val before = pinSourceEvidence(f)
        val initial = p.store.read(f.owner); p.store.compareAndSet(initial, initial.snapshot.copy(revision = 1))
        val current = request.copy(expectedRevision = 1)
        val conflictStore = object : DesktopWorktreeChatArchiveStore(p.path) {
            override fun compareAndSet(expected: Observation, update: ManagedWorktreeChatArchiveSnapshot): Observation {
                Files.write(p.path, Files.readAllBytes(p.path) + "\n ".toByteArray())
                return super.compareAndSet(expected, update)
            }
        }
        val bytes = Files.readAllBytes(p.path) + "\n ".toByteArray()
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository(store = conflictStore).setArchived(current))
        assertContentEquals(bytes, Files.readAllBytes(p.path))
        val invalid = "{ bad archives".toByteArray(); Files.write(p.path, invalid)
        assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(current))
        assertIs<ManagedWorktreeChatArchiveRead.Unknown>(p.inventory().archiveRead)
        assertContentEquals(invalid, Files.readAllBytes(p.path)); assertEquals(before, pinSourceEvidence(f))
    }

    @Test
    fun directorySyncFailureReturnsCommittedWarning() = withArchives { p ->
        val f = p.fixture; f.session("chat"); val before = pinSourceEvidence(f)
        val service = p.repository(store = DesktopWorktreeChatArchiveStore(p.path) { error("fsync directory") })
        val request = p.request("chat")
        val receipt = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(service.setArchived(request))
        assertEquals(request, receipt.request); assertNotNull(receipt.durabilityWarning)
        assertEquals(1L, receipt.snapshot.revision)
        assertEquals(receipt.snapshot, DesktopWorktreeChatArchiveStore(p.path).read(f.owner).snapshot)
        assertEquals(before, pinSourceEvidence(f))
    }

    @Test
    fun liveChatWithInFlightOperationRejectsArchiveAsBusy() = withArchives { p ->
        val f = p.fixture
        val live = f.session("busy-chat")
        val request = p.request("busy-chat")
        val before = pinSourceEvidence(f)
        val permit = live.session.acquireOperationPermit()
        try {
            val rejected = assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(p.repository.setArchived(request))
            assertTrue(rejected.reason.contains("在途操作"), rejected.reason)
            assertFalse(Files.exists(p.path))
        } finally { permit.dispose() }
        // The gate is always released, so the same idle chat can be archived afterwards.
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(request))
        assertEquals(1L, applied.snapshot.revision)
        assertEquals(before, pinSourceEvidence(f))
    }

    @Test
    fun archiveReservationIsReleasedAfterCommitAndNeverBlocksLaterWork() = withArchives { p ->
        val f = p.fixture
        val live = f.session("gate-chat")
        val request = p.request("gate-chat")
        assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(request))
        // A still-held reservation would reject this as CLOSING; succeeding proves the gate is free.
        live.session.acquireOperationPermit().dispose()
        val unarchive = p.request("gate-chat", desired = false)
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(unarchive))
        assertEquals(2L, applied.snapshot.revision)
    }

    @Test
    fun archivingALiveAgentChatStopsAndDetachesIt() = withArchives { p ->
        val f = p.fixture
        val registry = f.context.agents
        val handle = registry.create(AgentId("agent-chat"), AgentOptions(cwd = f.root.toString(), provider = "test", model = "test"))
        val repository = p.repository(archiveAgents = { id -> registry.beginArchive(AgentId(id)) })
        val request = p.request("agent-chat")
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(repository.setArchived(request))
        assertEquals(request, applied.request)
        assertNull(applied.durabilityWarning)
        assertEquals(1L, applied.snapshot.revision)
        assertTrue(registry.list().none { it.id.value == "agent-chat" })
        assertNull(f.context.sessions.get(SessionId("agent-chat")))
        assertEquals(listOf(request.key), DesktopWorktreeChatArchiveStore(p.path).read(f.owner).snapshot.archived)
        handle.dispose()
    }

    @Test
    fun agentBackedArchiveIsBusyWhileTheAgentHasInFlightActivity() = withArchives { p ->
        val f = p.fixture
        val registry = f.context.agents
        val handle = registry.create(AgentId("busy-agent"), AgentOptions(cwd = f.root.toString(), provider = "test", model = "test"))
        val repository = p.repository(archiveAgents = { id -> registry.beginArchive(AgentId(id)) })
        val request = p.request("busy-agent")
        val permit = handle.agent.session.acquireOperationPermit()
        try {
            val rejected = assertIs<ManagedWorktreeChatArchiveReceipt.Rejected>(repository.setArchived(request))
            assertTrue(rejected.reason.contains("在途操作"), rejected.reason)
            assertFalse(Files.exists(p.path))
        } finally {
            permit.dispose()
        }
        assertTrue(registry.list().any { it.id.value == "busy-agent" })
        handle.dispose()
    }

    @Test
    fun unarchivingRestoresTheChatWithoutRepairOrANewModelRequest() = withArchives { p ->
        val f = p.fixture
        val registry = f.context.agents
        val handle = registry.create(AgentId("restore-chat"),
            AgentOptions(cwd = f.root.toString(), provider = "hist", model = "hist-model"))
        // Give the chat its own durable model origin so restore needs no settings lookup.
        handle.agent.session.append(SessionEventKeys.RequestContext,
            RequestContextEvent(1, 1, SessionRequestContext("hist", "hist-model")))
        val callConfig = LlmCallConfig("hist", "hist-model", maxTokens = 4096,
            reasoning = ReasoningOptions(mode = ReasoningModeId.ENABLED, budgetTokens = 1024))
        handle.agent.session.append(SessionEventKeys.RequestHeader,
            RequestHeaderEvent(1, 1, EpochHeader(callConfig, reason = EpochReason.INITIAL)))
        f.context.persistence.flush(handle.agent.session)
        val before = handle.agent.session.events()
        val service = DesktopWorktreeChatRestoreService(f.context)
        val repository = p.repository(archiveAgents = { id -> registry.beginArchive(AgentId(id)) },
            restoreChat = service::restore)
        val archived = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(repository.setArchived(p.request("restore-chat")))
        assertNull(archived.restore)
        assertFalse(registry.list().any { it.id.value == "restore-chat" })
        assertNull(f.context.sessions.get(SessionId("restore-chat")))

        val unarchive = p.request("restore-chat", desired = false)
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(repository.setArchived(unarchive))
        assertEquals(ManagedWorktreeChatRestore.Restored, applied.restore)
        assertTrue(applied.snapshot.archived.isEmpty())
        val restored = assertNotNull(registry.get(AgentId("restore-chat")))
        assertNotNull(f.context.sessions.get(SessionId("restore-chat")))
        // Exact durable history: nothing repaired, flushed or appended by the restore.
        assertEquals(before, restored.session.events())
        assertEquals("hist", restored.session.requestContext()?.provider)
        assertEquals(callConfig, restored.options.requireCallConfig())
        handle.dispose()
    }

    @Test
    fun unarchivingWithoutARestoreServiceStillCommitsAndReportsNoRestore() = withArchives { p ->
        val f = p.fixture
        val history = f.session("plain-restore")
        f.context.persistence.flush(history.session); history.dispose()
        assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(p.request("plain-restore")))
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(p.repository.setArchived(p.request("plain-restore", desired = false)))
        assertNull(applied.restore)
        assertTrue(applied.snapshot.archived.isEmpty())
        assertTrue(f.context.sessions.list().isEmpty())
    }

    @Test
    fun restoreWithoutAModelOriginIsReportedFailedAndLeavesNoAgentlessSession() = withArchives { p ->
        val f = p.fixture
        val registry = f.context.agents
        val handle = registry.create(AgentId("no-origin"),
            AgentOptions(cwd = f.root.toString(), provider = "hist", model = "hist-model"))
        f.context.persistence.flush(handle.agent.session)
        val service = DesktopWorktreeChatRestoreService(f.context)
        val repository = p.repository(archiveAgents = { id -> registry.beginArchive(AgentId(id)) },
            restoreChat = service::restore)
        assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(repository.setArchived(p.request("no-origin")))
        val applied = assertIs<ManagedWorktreeChatArchiveReceipt.Applied>(repository.setArchived(p.request("no-origin", desired = false)))
        assertIs<ManagedWorktreeChatRestore.Failed>(applied.restore)
        // A rejected attach must not leave a live, agentless Session behind.
        assertNull(f.context.sessions.get(SessionId("no-origin")))
        assertTrue(registry.list().none { it.id.value == "no-origin" })
    }

    private fun withArchives(block: suspend (WorktreeChatArchiveFixture) -> Unit) = runBlocking {
        val p = WorktreeChatArchiveFixture()
        try { p.fixture.open(); block(p) } finally { p.close() }
    }
}

/** Adds only the independent archive service to the unchanged production observation fixture. */
internal class WorktreeChatArchiveFixture : AutoCloseable {
    val fixture = WorktreeObservationFixture()
    val path: Path = fixture.home.resolve("managed-chat-archive.json")
    val store: DesktopWorktreeChatArchiveStore get() = DesktopWorktreeChatArchiveStore(path)
    val repository: DesktopManagedWorktreeRepository by lazy { repository() }
    fun repository(observer: DesktopWorktreeSessionObserver = fixture.host.sessionObserver,
        store: DesktopWorktreeChatArchiveStore = this.store, backend: GitBackend = fixture.actual,
        archiveAgents: (suspend (String) -> AgentArchiveBegin)? = null,
        restoreChat: (suspend (String) -> ManagedWorktreeChatRestore)? = null) =
        DesktopManagedWorktreeRepository(backend, DesktopManagedWorktreeStore(fixture.registry),
            ownerValidator = { candidate -> if (candidate == fixture.binding) fixture.currentOwner else null },
            sessionObserver = observer, archiveStore = store, archiveAgents = archiveAgents, restoreChat = restoreChat)
    suspend fun inventory(): ManagedWorktreeInventory = assertIs<ManagedWorktreeRead.Ready>(repository.read(fixture.gitRepository)).inventory
    suspend fun request(id: String, desired: Boolean = true, root: Path = fixture.root): ManagedWorktreeChatArchiveRequest {
        val current = inventory(); val snapshot = assertIs<ManagedWorktreeChatArchiveRead.Available>(current.archiveRead).snapshot
        val observed = current.entries.single { it.root == root.toString() }.sessionObservation!!
        assertEquals(ManagedSessionObservationStatus.Observed, observed.status)
        val header = assertNotNull(observed.sessions.single { it.id == id }.pinKey)
        return ManagedWorktreeChatArchiveRequest(current.repository, current.owner, root.toString(),
            ChatArchiveKey(header.id, header.headerFingerprint), snapshot.revision, desired)
    }
    override fun close() = fixture.close()
}

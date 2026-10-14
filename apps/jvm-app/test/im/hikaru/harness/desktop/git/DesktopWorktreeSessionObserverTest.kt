package im.hikaru.harness.desktop.git

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.git.*
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*

/** Successful reads use the production Desktop bootstrap, real Room and native temporary Git. */
class DesktopWorktreeSessionObserverTest {
    @Test
    fun emptyRoomAndLiveStoreAreAnObservedEmptyReadWithOwnerAndTime() = withObservationFixture { f ->
        val before = System.currentTimeMillis()
        val observation = f.observe()
        assertEquals(ManagedSessionObservationStatus.Observed, observation.status)
        assertEquals(f.owner, observation.owner)
        assertTrue(observation.startedAtMillis >= before)
        assertTrue(observation.completedAtMillis >= observation.startedAtMillis)
        assertTrue(observation.completedAtMillis <= System.currentTimeMillis())
        assertEquals(emptyList(), observation.sessions)
        assertEquals(emptyList(), observation.issues)
        assertEquals(emptyList(), f.context.sessions.list())
        assertEquals(emptyList(), f.context.persistence.listHeaders())
    }

    @Test
    fun realDurableUnloadedLiveUnflushedAndDualSourcePreserveLineageWithoutWrites() = withObservationFixture { f ->
        val parent = f.session("durable-parent")
        parent.session.append(SessionEventKeys.TurnStart, TurnStartEvent(1L))
        parent.session.append(SessionEventKeys.StepStart, StepStartEvent(1L, 1L))
        f.context.persistence.flush(parent.session)
        parent.dispose()
        val malformed = f.session("malformed-events")
        f.context.persistence.flush(malformed.session)
        malformed.dispose()
        val invalidEvent = SessionEventRow("malformed-events", 0L, "user/message", 1L,
            " { definitely-not-json \n", "APPEND", "not-json-either", null)
        f.database { it.sessions().upsertEvents(listOf(invalidEvent)) }
        val dual = f.session("dual-child", parent = "durable-parent")
        f.context.persistence.flush(dual.session)
        val live = f.session("live-unflushed", cwd = f.nested.toString(), parent = "dual-child")
        val lifecycle = AtomicInteger()
        f.context.on(SessionEvents.Created) { lifecycle.incrementAndGet() }
        f.context.on(SessionEvents.Disposed) { lifecycle.incrementAndGet() }
        val beforeLive = f.context.sessions.list()
        val beforeRows = f.database { it.sessions().listSessions() }
        val beforeEvents = f.database { db -> beforeRows.associate { it.sessionId to db.sessions().findEvents(it.sessionId) } }
        val registry = Files.readAllBytes(f.registry)
        repeat(2) {
            val observation = f.observe()
            assertEquals(ManagedSessionObservationStatus.Observed, observation.status)
            val byId = observation.sessions.associateBy { it.id }
            assertEquals(setOf("durable-parent", "malformed-events", "dual-child", "live-unflushed"), byId.keys)
            assertEquals(setOf(ManagedSessionSource.Durable), byId.getValue("durable-parent").sources)
            assertEquals(setOf(ManagedSessionSource.Durable), byId.getValue("malformed-events").sources)
            assertEquals(setOf(ManagedSessionSource.Live, ManagedSessionSource.Durable), byId.getValue("dual-child").sources)
            assertEquals(setOf(ManagedSessionSource.Live), byId.getValue("live-unflushed").sources)
            assertEquals("durable-parent", byId.getValue("dual-child").parentSessionId)
            assertEquals("dual-child", byId.getValue("live-unflushed").parentSessionId)
        }
        assertEquals(listOf(dual.session, live.session), beforeLive)
        assertEquals(beforeLive, f.context.sessions.list(), "Durable-only sessions must remain unloaded")
        assertEquals(0, lifecycle.get(), "Header observation must not create or dispose sessions")
        assertEquals(beforeRows, f.database { it.sessions().listSessions() })
        assertEquals(beforeEvents, f.database { db -> beforeRows.associate { it.sessionId to db.sessions().findEvents(it.sessionId) } })
        assertEquals(listOf(invalidEvent), beforeEvents.getValue("malformed-events"))
        assertEquals(2, beforeEvents.getValue("durable-parent").size, "Interrupted events remain untouched")
        assertContentEquals(registry, Files.readAllBytes(f.registry))
    }

    @Test
    fun nativePathComponentsSubdirectoriesAndRealSymlinksDoNotMatchSiblingPrefix() = withObservationFixture { f ->
        val alias = f.directory.resolve("tree-alias")
        Files.createSymbolicLink(alias, f.root)
        f.session("direct")
        f.session("nested", f.nested.toString())
        f.session("alias", alias.resolve("nested").toString())
        f.session("sibling", f.sibling.toString())
        val results = f.host.sessionObserver.observe(f.binding, f.owner,
            listOf(f.root.toString(), f.sibling.toString(), alias.toString())) { f.currentOwner }
        assertEquals(setOf("direct", "nested", "alias"), results.getValue(f.root.toString()).sessions.map { it.id }.toSet())
        assertEquals(listOf("sibling"), results.getValue(f.sibling.toString()).sessions.map { it.id })
        assertEquals(results.getValue(f.root.toString()).sessions, results.getValue(alias.toString()).sessions)
        assertTrue(results.values.all { it.status == ManagedSessionObservationStatus.Observed })
        assertEquals(f.nested.toString(), results.getValue(f.root.toString()).sessions.single { it.id == "alias" }.cwd)
    }

    @Test
    fun unassignableCwdsAndInvalidRootRemainUnknownAndKeepValidMatches() = withObservationFixture { f ->
        f.session("valid")
        f.session("no-cwd", cwd = null)
        f.session("missing", f.directory.resolve("missing").toString())
        f.session("not-directory", f.root.resolve("tracked.txt").toString())
        f.session("invalid-native", "/bad\u0000")
        if (java.io.File.separatorChar == '/') {
            f.session("windows", "C:\\foreign\\tree")
            f.session("unc", "\\\\server\\share")
        }
        val results = f.host.sessionObserver.observe(f.binding, f.owner,
            listOf(f.root.toString(), f.sibling.toString(), f.directory.resolve("missing-root").toString())) { f.currentOwner }
        assertTrue(results.values.all { it.status == ManagedSessionObservationStatus.Unknown })
        assertEquals(listOf("valid"), results.getValue(f.root.toString()).sessions.map { it.id })
        assertEquals(emptyList(), results.getValue(f.sibling.toString()).sessions)
        val kinds = results.getValue(f.root.toString()).issues.map { it.kind }.toSet()
        assertTrue(kinds.containsAll(setOf(ManagedSessionIssueKind.MissingCwd, ManagedSessionIssueKind.MissingPath,
            ManagedSessionIssueKind.NotDirectory, ManagedSessionIssueKind.InvalidPath)))
        assertTrue(results.getValue(f.directory.resolve("missing-root").toString()).issues.any { it.kind == ManagedSessionIssueKind.InvalidRoot })
    }

    @Test
    fun realRoomConflictingIdentityMetadataIsUnknownButFormatVersionAloneIsAccepted() = withObservationFixture { f ->
        val live = f.session("same-id").session
        f.context.persistence.flush(live)
        val row = f.database { assertNotNull(it.sessions().findSession(live.id.value)) }
        val conflicts = listOf(row.copy(cwd = f.sibling.toString()), row.copy(parentSession = "other-parent"),
            row.copy(createdAt = row.createdAt + 1), row.copy(seedLength = 1), row.copy(origin = "SUBAGENT"),
            row.copy(delegationDepth = 1), row.copy(agentPreset = "other-agent"))
        for (conflict in conflicts) {
            f.database { it.sessions().upsertSession(conflict) }
            val result = f.observe()
            assertEquals(ManagedSessionObservationStatus.Unknown, result.status)
            assertEquals(emptyList(), result.sessions)
            assertEquals(ManagedSessionIssueKind.HeaderConflict, result.issues.single().kind)
            assertEquals(live.id.value, result.issues.single().sessionId)
        }
        f.database { it.sessions().upsertSession(row.copy(version = row.version + 1)) }
        val result = f.observe()
        assertEquals(ManagedSessionObservationStatus.Observed, result.status)
        assertEquals(setOf(ManagedSessionSource.Live, ManagedSessionSource.Durable), result.sessions.single().sources)
        assertEquals(live.header, f.context.sessions.list().single().header)
    }

    @Test
    fun unsupportedAndAbsentCatalogDoNotTurnLiveEmptyIntoObservedEmpty() = withObservationFixture { f ->
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            val unsupported = object : SessionPersistence by f.context.persistence {
                override suspend fun listHeaders(): List<SessionHeader>? = null
            }
            val binding = isolated.provide(SessionPersistenceKey, unsupported)
            val observer = f.capture(isolated)
            val result = f.observe(observer)
            assertEquals(ManagedSessionObservationStatus.Unavailable, result.status)
            assertEquals(ManagedSessionIssueKind.Unsupported, result.issues.single().kind)
            assertTrue(result.sessions.isEmpty())
            binding.dispose()
            val absent = f.observe(f.capture(isolated))
            assertEquals(ManagedSessionObservationStatus.Unavailable, absent.status)
            assertEquals(ManagedSessionIssueKind.SourceUnavailable, absent.issues.single().kind)
        } finally { isolated.dispose() }
    }

    @Test
    fun sourceFailureDisposalAndExactServiceReplacementRejectPartialData() = withObservationFixture { f ->
        f.session("must-not-leak")
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            var fail = true
            val provider = object : SessionPersistence by f.context.persistence {
                override suspend fun listHeaders(): List<SessionHeader>? {
                    if (fail) error("controlled catalog failure")
                    return f.context.persistence.listHeaders()
                }
            }
            val binding = isolated.provide(SessionPersistenceKey, provider)
            val captured = f.capture(isolated)
            f.observe(captured).assertRejected(ManagedSessionIssueKind.SourceFailed)
            fail = false
            binding.dispose()
            isolated.provide(SessionPersistenceKey, f.context.persistence)
            f.observe(captured).assertRejected(ManagedSessionIssueKind.SourceChanged)
            val current = f.capture(isolated)
            isolated.dispose()
            f.observe(current).assertRejected(ManagedSessionIssueKind.SourceChanged)
        } finally { isolated.dispose() }
        val liveContext = f.context.isolate(SessionKey)
        try {
            val binding = liveContext.provide(SessionKey, f.context.sessions)
            val captured = f.capture(liveContext)
            binding.dispose()
            val other = DesktopWorktreeSessionObserver.startDesktopHost(Files.createDirectories(f.directory.resolve("other-home")))
            try {
                liveContext.provide(SessionKey, other.profiledHost.host.runtime.context.sessions)
                f.observe(captured).assertRejected(ManagedSessionIssueKind.SourceChanged)
            } finally { other.profiledHost.close() }
        } finally { liveContext.dispose() }
        f.context.sessions.dispose()
        f.observe().assertRejected(ManagedSessionIssueKind.SourceFailed)
    }

    @Test
    fun hostHomeMemberRemoteAndChangingOwnerNeverExposeForeignSessions() = withObservationFixture { f ->
        f.session("private-session")
        val member = MemberIdentity(BackendTenant("https://example.invalid", 1), 1)
        val candidates = listOf(
            f.binding.copy(hostId = HostId("foreign")) to f.owner.copy(hostId = HostId("foreign")),
            f.binding to f.owner.copy(homeNamespace = f.sibling.toString()),
            f.binding.copy(identity = member) to f.owner.copy(identity = member),
            f.binding.copy(access = GitAccess.RemoteUnavailable) to f.owner,
        )
        for ((binding, owner) in candidates) {
            f.host.sessionObserver.observe(binding, owner, listOf(f.root.toString())) { owner }
                .getValue(f.root.toString()).assertRejected(ManagedSessionIssueKind.OwnerChanged)
        }
        var validations = 0
        f.host.sessionObserver.observe(f.binding, f.owner, listOf(f.root.toString())) {
            if (++validations == 1) f.owner else null
        }.getValue(f.root.toString()).assertRejected(ManagedSessionIssueKind.OwnerChanged)
        assertEquals(2, validations)
    }

    @Test
    fun cancellationBeforeAndDuringCatalogReadPropagatesAndDoesNotPublishEmptySuccess() = withObservationFixture { f ->
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            isolated.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {
                override suspend fun listHeaders(): List<SessionHeader>? { entered.complete(Unit); gate.await(); return emptyList() }
            })
            val captured = f.capture(isolated)
            coroutineScope {
                val pending = async { f.observe(captured) }
                entered.await()
                pending.cancel()
                assertFailsWith<CancellationException> { pending.await() }
            }
            val cancelledJob = Job().also { it.cancel() }
            assertFailsWith<CancellationException> { withContext(cancelledJob) { f.observe() } }
        } finally { gate.complete(Unit); isolated.dispose() }
    }

    @Test
    fun realRegisteredGitInventoryObservesCatalogOnceAndKeepsLegacyConstructorsAndRegistryBytes() = withObservationFixture { f ->
        f.registerLinkedWorktree()
        val durable = f.session("linked-history", f.linked.toString())
        f.context.persistence.flush(durable.session)
        durable.dispose()
        f.session("main-live")
        val beforeRegistry = Files.readAllBytes(f.registry)
        val beforeRows = f.database { it.sessions().listSessions() }
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            val reads = AtomicInteger()
            isolated.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {
                override suspend fun listHeaders(): List<SessionHeader>? {
                    reads.incrementAndGet()
                    return f.context.persistence.listHeaders()
                }
            })
            val inventory = assertIs<ManagedWorktreeRead.Ready>(f.repository(f.capture(isolated)).read(f.gitRepository)).inventory
            assertEquals(1, reads.get(), "One real Room catalog read supplies all worktree entries")
            assertEquals(2, inventory.entries.size)
            assertEquals(1, inventory.registeredCount)
            val linked = inventory.entries.single { it.root == f.linked.toString() }
            assertEquals(ManagedOwnership.Managed, linked.ownership)
            assertEquals(ManagedRegistrationPhase.Registered, linked.registration)
            assertEquals(listOf("linked-history"), assertNotNull(linked.sessionObservation).sessions.map { it.id })
            assertEquals(listOf("main-live"), inventory.entries.single { it.root == f.root.toString() }.sessionObservation?.sessions?.map { it.id })
            assertTrue(inventory.entries.all { it.sessionObservation?.status == ManagedSessionObservationStatus.Observed })
            val store = DesktopManagedWorktreeStore(f.registry)
            val trailing = DesktopManagedWorktreeRepository(f.actual, store) { f.currentOwner }
            val positional = DesktopManagedWorktreeRepository(f.actual, store, { f.currentOwner })
            for (legacy in listOf(trailing, positional)) {
                val old = assertIs<ManagedWorktreeRead.Ready>(legacy.read(f.gitRepository)).inventory
                assertTrue(old.entries.all { it.sessionObservation == null })
                assertTrue(old.entries.all { it.protection.sessions.availability == ManagedProtectionAvailability.Unknown })
            }
            assertEquals(1, reads.get())
        } finally { isolated.dispose() }
        assertContentEquals(beforeRegistry, Files.readAllBytes(f.registry))
        assertEquals(beforeRows, f.database { it.sessions().listSessions() })
        assertEquals(listOf("main-live"), f.context.sessions.list().map { it.id.value })
    }

    @Test
    fun repositoryRevalidatesOwnerAfterTheRealCatalogRead() = withObservationFixture { f ->
        f.session("old-owner-private")
        val registry = Files.readAllBytes(f.registry)
        val isolated = f.context.isolate(SessionPersistenceKey)
        try {
            isolated.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {
                override suspend fun listHeaders(): List<SessionHeader>? {
                    val real = f.context.persistence.listHeaders()
                    f.currentOwner = null
                    return real
                }
            })
            assertIs<ManagedWorktreeRead.Failed>(f.repository(f.capture(isolated)).read(f.gitRepository))
            assertContentEquals(registry, Files.readAllBytes(f.registry))
            assertEquals(listOf("old-owner-private"), f.context.sessions.list().map { it.id.value })
        } finally { isolated.dispose() }
    }
}

private fun ManagedSessionObservation.assertRejected(kind: ManagedSessionIssueKind) {
    assertNotEquals(ManagedSessionObservationStatus.Observed, status)
    assertTrue(sessions.isEmpty(), "Rejected reads must discard even the already read live headers")
    assertEquals(kind, issues.single().kind)
}

private fun withObservationFixture(block: suspend (WorktreeObservationFixture) -> Unit) = runBlocking {
    val fixture = WorktreeObservationFixture()
    try { fixture.open(); block(fixture) } finally { fixture.close() }
}

/** Shared UI fixture: production home -> DesktopBundle -> Room, plus real Git and registry. */
internal class WorktreeObservationFixture : AutoCloseable {
    val directory: Path = Files.createTempDirectory("worktree-session-observation-").toRealPath()
    val home: Path = Files.createDirectories(directory.resolve("home"))
    val root: Path = Files.createDirectories(directory.resolve("tree"))
    val nested: Path = Files.createDirectories(root.resolve("nested"))
    val sibling: Path = Files.createDirectories(directory.resolve("tree-2"))
    val linked: Path = directory.resolve("linked")
    val registry: Path = home.resolve("managed-worktrees.json")
    lateinit var host: DesktopObservedHost
        private set
    val context: Context get() = host.profiledHost.host.runtime.context
    val binding: GitBinding get() = GitBinding(null, host.profiledHost.host.profile.hostDescription.hostId, 1, GitAccess.LocalReadOnly)
    val owner: ManagedWorktreeOwner get() = ManagedWorktreeOwner(null, binding.hostId, home.toString())
    @Volatile var currentOwner: ManagedWorktreeOwner? = null
    val actual: DesktopGitBackend get() = DesktopGitBackend(binding.hostId)
    lateinit var gitRepository: GitRepository
        private set

    suspend fun open() {
        host = DesktopWorktreeSessionObserver.startDesktopHost(home)
        currentOwner = owner
        git(root, "init", "--initial-branch=main")
        git(root, "config", "core.hooksPath", "/dev/null")
        Files.writeString(root.resolve("tracked.txt"), "committed\n")
        git(root, "add", "--", "tracked.txt")
        git(root, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "-m", "observation fixture")
        gitRepository = actual.discover(binding, root.toString())
        val store = DesktopManagedWorktreeStore(registry)
        val empty = store.read(owner)
        store.compareAndSet(empty, empty.snapshot.copy(revision = 1))
    }

    suspend fun session(id: String, cwd: String? = root.toString(), parent: String? = null): SessionHandle =
        context.sessions.create(SessionId(id), CreateSessionOptions(cwd = cwd, parentSession = parent?.let(::SessionId),
            origin = if (parent == null) null else SessionOrigin.SUBAGENT))

    fun repository(observer: DesktopWorktreeSessionObserver? = host.sessionObserver) =
        DesktopManagedWorktreeRepository(actual, DesktopManagedWorktreeStore(registry),
            ownerValidator = { candidate -> if (candidate == binding) currentOwner else null }, sessionObserver = observer)

    fun capture(source: Context) = DesktopWorktreeSessionObserver.captureForTest(source, binding.hostId, home)
    suspend fun observe(observer: DesktopWorktreeSessionObserver = host.sessionObserver) =
        observer.observe(binding, owner, listOf(root.toString())) { currentOwner }.getValue(root.toString())

    suspend fun registerLinkedWorktree() {
        assertIs<GitCreateWorktreeResult.Created>(DesktopManagedGitBackend(actual, repository()).createWorktree(
            gitRepository, GitCreateWorktreeRequest(binding, linked.toString(), "codex/observation")))
    }

    suspend fun <T> database(block: suspend (SessionPersistenceDatabase) -> T): T {
        val database = Room.databaseBuilder<SessionPersistenceDatabase>(home.resolve("harness-sessions.db").toString())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try { return block(database) } finally { database.close() }
    }

    private suspend fun git(cwd: Path, vararg args: String) {
        val result = DesktopGitCommandRunner().run(cwd.toString(), args.toList())
        assertEquals(0, result.exitCode, args.toList().toString() + ": " + result.stderr)
    }

    override fun close() = runBlocking {
        try { if (::host.isInitialized) host.profiledHost.close() }
        finally { assertTrue(directory.toFile().deleteRecursively(), "Temporary observation fixture cleanup") }
    }
}

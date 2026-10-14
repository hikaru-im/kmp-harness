package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.APPEND
import kotlinx.coroutines.*
import kotlin.test.*

/** Real Git receipts and registry bytes; controlled seams only stop IO or reject acknowledgement. */
class DesktopManagedWorktreeIntegrationTest {
    @Test
    fun realCreateRestartAndDelegatedCheckoutPreserveSourceAndUseCurrentGitHead() = withFixture { f ->
        f.git(f.root, "branch", "feature")
        Files.writeString(f.root.resolve("tracked.txt"), "staged\n")
        f.git(f.root, "add", "--", "tracked.txt")
        Files.writeString(f.root.resolve("tracked.txt"), "working\n")
        Files.writeString(f.root.resolve("untracked.txt"), "keep\n")
        val sourceIndex = Files.readAllBytes(f.root.resolve(".git/index"))
        val sourceStatus = f.git(f.root, "status", "--porcelain=v1", "-z")
        val repository = f.actual.discover(f.binding, f.root.toString())
        val store = DesktopManagedWorktreeStore(f.registry)
        val managed = f.managed(store)
        val backend = DesktopManagedGitBackend(f.actual, managed)
        assertTrue(backend.canCreateWorktree(f.binding))
        val created = assertIs<GitCreateWorktreeResult.Created>(backend.createWorktree(repository, f.request()))
        assertEquals("committed\n", Files.readString(f.target.resolve("tracked.txt")))
        assertEquals(created.receipt.startOid, f.git(f.target, "rev-parse", "HEAD").trim())
        val targetRepo = backend.discover(f.binding, f.target.toString())
        // Same commit first, then advance feature so initial creation evidence cannot supply live HEAD.
        val status = backend.status(targetRepo)
        val feature = backend.branches(targetRepo).single { it.ref == "refs/heads/feature" }
        assertIs<GitBranchCheckoutResult.Switched>(backend.switchBranch(targetRepo,
            GitBranchCheckoutRequest(f.binding, targetRepo.root, status.branch, requireNotNull(status.headOid), feature.ref, feature.oid)))
        Files.writeString(f.target.resolve("tracked.txt"), "advanced feature\n")
        f.git(f.target, "add", "--", "tracked.txt")
        f.commit(f.target, "advance feature")
        val beforeRead = Files.readAllBytes(f.registry)
        val restarted = f.managed(DesktopManagedWorktreeStore(f.registry))
        val inventory = assertIs<ManagedWorktreeRead.Ready>(restarted.read(repository)).inventory
        val owned = inventory.entries.single { it.root == f.target.toString() }
        assertEquals(ManagedOwnership.Managed, owned.ownership)
        assertEquals(ManagedRegistrationPhase.Registered, owned.registration)
        assertEquals("refs/heads/feature", owned.worktree?.branch)
        assertEquals(f.git(f.target, "rev-parse", "HEAD").trim(), owned.worktree?.headOid)
        assertNotEquals(created.receipt.startOid, owned.worktree?.headOid)
        assertEquals(ManagedOwnership.UnknownOwnership, inventory.entries.single { it.root == f.root.toString() }.ownership)
        assertEquals(ManagedProtectionAvailability.Unknown, owned.protection.sessions.availability)
        assertEquals(ManagedProtectionAvailability.Unavailable, owned.protection.pins.availability)
        assertEquals(ManagedProtectionAvailability.Unavailable, owned.protection.archive.availability)
        assertContentEquals(beforeRead, Files.readAllBytes(f.registry), "reconcile must be read only")
        assertContentEquals(sourceIndex, Files.readAllBytes(f.root.resolve(".git/index")))
        assertEquals(sourceStatus, f.git(f.root, "status", "--porcelain=v1", "-z"))
        assertEquals("working\n", Files.readString(f.root.resolve("tracked.txt")))
        assertEquals("keep\n", Files.readString(f.root.resolve("untracked.txt")))
    }

    @Test
    fun preparedDirectorySyncWarningPreservesIntentAndPreventsAnyCreatorInvocation() = withFixture { f ->
        var syncs = 0
        var calls = 0
        val store = DesktopManagedWorktreeStore(f.registry) { syncs++; error("Prepared directory fsync") }
        val delegate = f.creator { repo, request -> calls++; f.actual.createWorktree(repo, request) }
        val managed = f.managed(store, delegate)
        val backend = DesktopManagedGitBackend(delegate, managed)
        val repo = f.actual.discover(f.binding, f.root.toString())
        val failed = assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(repo, f.request()))
        assertFalse(failed.failure.needsRescan)
        assertEquals(0, calls)
        assertEquals(1, syncs, "warning must not rewrite Prepared to Rejected")
        assertFalse(Files.exists(f.target))
        val snapshot = DesktopManagedWorktreeStore(f.registry).read(f.owner).snapshot
        assertEquals(1L, snapshot.revision)
        assertEquals(ManagedRegistrationPhase.Prepared, snapshot.intents.single().phase)
        assertNull(snapshot.intents.single().evidence)
        val bytes = Files.readAllBytes(f.registry)
        val entry = assertIs<ManagedWorktreeRead.Ready>(managed.read(repo)).inventory.entries.single { it.root == f.target.toString() }
        assertEquals(ManagedOwnership.Missing, entry.ownership)
        assertTrue(requireNotNull(entry.registrationWarning).contains("fsync"))
        assertContentEquals(bytes, Files.readAllBytes(f.registry))
    }

    @Test
    fun registeredDirectorySyncWarningKeepsActualCreatedCommittedRegistrationAndFiles() = withFixture { f ->
        var syncs = 0
        val store = DesktopManagedWorktreeStore(f.registry) { directory ->
            if (++syncs == 3) error("Registered directory fsync")
            FileChannel.open(directory, READ).use { it.force(true) }
        }
        val managed = f.managed(store)
        val backend = DesktopManagedGitBackend(f.actual, managed)
        val repo = f.actual.discover(f.binding, f.root.toString())
        val result = assertIs<GitCreateWorktreeResult.Created>(backend.createWorktree(repo, f.request()))
        assertEquals(3, syncs)
        assertEquals(result.receipt.startOid, f.git(f.target, "rev-parse", "HEAD").trim())
        assertEquals("committed\n", Files.readString(f.target.resolve("tracked.txt")))
        val snapshot = DesktopManagedWorktreeStore(f.registry).read(f.owner).snapshot
        assertEquals(3L, snapshot.revision)
        assertEquals(ManagedRegistrationPhase.Registered, snapshot.intents.single().phase)
        assertNotNull(snapshot.intents.single().evidence)
        val entry = assertIs<ManagedWorktreeRead.Ready>(managed.read(repo)).inventory.entries.single { it.root == f.target.toString() }
        assertEquals(ManagedOwnership.Managed, entry.ownership)
        assertTrue(requireNotNull(entry.registrationWarning).contains("fsync"))
    }

    @Test
    fun preDispatchActualExternalByteEditRejectsWithoutOverwritingOrCallingGit() = withFixture { f ->
        var external: ByteArray? = null
        var calls = 0
        val store = object : DesktopManagedWorktreeStore(f.registry) {
            override fun compareAndSet(expected: DesktopManagedWorktreeStore.Observation,
                update: ManagedWorktreeSnapshot): DesktopManagedWorktreeStore.Observation {
                val committed = super.compareAndSet(expected, update)
                if (external == null) {
                    external = Files.readAllBytes(f.registry) + "\n ".toByteArray()
                    Files.write(f.registry, requireNotNull(external))
                }
                return committed
            }
        }
        val delegate = f.creator { repo, request -> calls++; f.actual.createWorktree(repo, request) }
        val backend = DesktopManagedGitBackend(delegate, f.managed(store, delegate))
        val repo = f.actual.discover(f.binding, f.root.toString())
        assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(repo, f.request()))
        assertEquals(0, calls)
        assertFalse(Files.exists(f.target))
        assertContentEquals(requireNotNull(external), Files.readAllBytes(f.registry))
        assertEquals(ManagedRegistrationPhase.Prepared, store.read(f.owner).snapshot.intents.single().phase)
    }

    @Test
    fun registrationCasConflictAfterRealCreatedPreservesReceiptBytesAndUnknownOwnershipAtRestart() = withFixture { f ->
        var external: ByteArray? = null
        var calls = 0
        val delegate = f.creator { repo, request ->
            calls++
            val result = f.actual.createWorktree(repo, request)
            assertIs<GitCreateWorktreeResult.Created>(result)
            external = Files.readAllBytes(f.registry) + "\n ".toByteArray()
            Files.write(f.registry, requireNotNull(external))
            result
        }
        val store = DesktopManagedWorktreeStore(f.registry)
        val managed = f.managed(store, delegate)
        val repo = f.actual.discover(f.binding, f.root.toString())
        val result = assertIs<GitCreateWorktreeResult.Created>(DesktopManagedGitBackend(delegate, managed).createWorktree(repo, f.request()))
        assertEquals(1, calls)
        assertEquals(result.receipt.startOid, f.git(f.target, "rev-parse", "HEAD").trim())
        assertContentEquals(requireNotNull(external), Files.readAllBytes(f.registry))
        val current = assertIs<ManagedWorktreeRead.Ready>(managed.read(repo)).inventory.entries.single { it.root == f.target.toString() }
        assertEquals(ManagedOwnership.UnknownOwnership, current.ownership)
        assertTrue(requireNotNull(current.registrationWarning).contains("登记失败"))
        val fresh = assertIs<ManagedWorktreeRead.Ready>(f.managed(DesktopManagedWorktreeStore(f.registry)).read(repo)).inventory
        assertEquals(ManagedOwnership.UnknownOwnership, fresh.entries.single { it.root == f.target.toString() }.ownership)
        assertContentEquals(requireNotNull(external), Files.readAllBytes(f.registry))
        assertEquals("committed\n", Files.readString(f.target.resolve("tracked.txt")))
    }

    @Test
    fun missingValidatorForeignOwnerAndRemoteScopesFailClosedWithoutIntentOrGitEffects() = withFixture { f ->
        val store = DesktopManagedWorktreeStore(f.registry)
        val repo = f.actual.discover(f.binding, f.root.toString())
        val absent = DesktopManagedGitBackend(f.actual, DesktopManagedWorktreeRepository(f.actual, store))
        assertFalse(absent.canCreateWorktree(f.binding))
        assertIs<GitCreateWorktreeResult.Failed>(absent.createWorktree(repo, f.request()))
        val foreign = DesktopManagedWorktreeRepository(f.actual, store) { f.owner.copy(hostId = HostId("foreign")) }
        assertIs<GitCreateWorktreeResult.Failed>(DesktopManagedGitBackend(f.actual, foreign).createWorktree(repo, f.request()))
        assertTrue(assertIs<ManagedWorktreeRead.Failed>(foreign.read(repo)).unavailable)
        val backend = DesktopManagedGitBackend(f.actual, f.managed(store))
        val remote = f.binding.copy(access = GitAccess.RemoteUnavailable)
        assertFalse(backend.canCreateWorktree(remote))
        assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(repo.copy(binding = remote), f.request().copy(binding = remote)))
        assertFalse(Files.exists(f.registry))
        assertFalse(Files.exists(f.target))
        assertEquals(1, f.actual.worktrees(repo).size)
    }

    @Test
    fun acceptedOwnerSurvivesNavigationAndAnotherOwnerCannotAdoptCreatedWorktree() = withFixture { f ->
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var activeOwner = f.owner
        val delegate = f.creator { repo, request -> entered.complete(Unit); release.await(); f.actual.createWorktree(repo, request) }
        val store = DesktopManagedWorktreeStore(f.registry)
        val managed = DesktopManagedWorktreeRepository(delegate, store) { activeOwner }
        val repo = f.actual.discover(f.binding, f.root.toString())
        coroutineScope {
            val creation = async { DesktopManagedGitBackend(delegate, managed).createWorktree(repo, f.request()) }
            try {
                withTimeout(5_000) { entered.await() }
                activeOwner = f.owner.copy(homeNamespace = Files.createDirectory(f.directory.resolve("other-home")).toString())
                release.complete(Unit)
                assertIs<GitCreateWorktreeResult.Created>(withTimeout(15_000) { creation.await() })
                assertEquals(ManagedRegistrationPhase.Registered, store.read(f.owner).snapshot.intents.single().phase)
                assertTrue(store.read(activeOwner).snapshot.intents.isEmpty())
                val visible = assertIs<ManagedWorktreeRead.Ready>(managed.read(repo)).inventory
                assertEquals(0, visible.registeredCount)
                assertTrue(visible.entries.all { it.ownership == ManagedOwnership.UnknownOwnership })
            } finally { release.complete(Unit); creation.cancelAndJoin() }
        }
    }

    @Test
    fun cancellationAfterActualGitAddKeepsFilesAndUnknownIntentWithoutInventedEvidence() = withFixture { f ->
        val runner = DesktopGitCommandRunner()
        val actual = DesktopGitBackend(f.binding.hostId, GitCommandRunner { cwd, args ->
            val result = runner.run(cwd, args)
            if (args.take(2) == listOf("worktree", "add")) throw CancellationException("after real Git add")
            result
        })
        val store = DesktopManagedWorktreeStore(f.registry)
        val managed = f.managed(store, actual)
        val repo = f.actual.discover(f.binding, f.root.toString())
        assertFailsWith<CancellationException> { DesktopManagedGitBackend(actual, managed).createWorktree(repo, f.request()) }
        assertEquals("committed\n", Files.readString(f.target.resolve("tracked.txt")))
        val intent = store.read(f.owner).snapshot.intents.single()
        assertEquals(ManagedRegistrationPhase.UnknownOutcome, intent.phase)
        assertNull(intent.evidence)
        val entry = assertIs<ManagedWorktreeRead.Ready>(f.managed(DesktopManagedWorktreeStore(f.registry)).read(repo)).inventory.entries
            .single { it.root == f.target.toString() }
        assertEquals(ManagedOwnership.UnknownOwnership, entry.ownership)
    }

    private fun withFixture(block: suspend (ManagedGitFixture) -> Unit) = runBlocking {
        val fixture = ManagedGitFixture()
        try { withTimeout(45_000) { fixture.initialize(); block(fixture) } }
        finally { fixture.directory.toFile().deleteRecursively() }
    }
}

private class ManagedGitFixture {
    val directory: Path = Files.createTempDirectory("harness-managed-git-").toRealPath()
    val root: Path = Files.createDirectory(directory.resolve("source"))
    val target: Path = directory.resolve("linked")
    val registry: Path = directory.resolve("managed.json")
    val binding = GitBinding(null, HostId("managed-integration-local"), 1, GitAccess.LocalReadOnly)
    val owner = ManagedWorktreeOwner(null, binding.hostId, directory.toString())
    val actual = DesktopGitBackend(binding.hostId)
    private val runner = DesktopGitCommandRunner()
    suspend fun initialize() {
        git(root, "init", "--initial-branch=main")
        git(root, "config", "core.hooksPath", "/dev/null")
        Files.writeString(root.resolve("tracked.txt"), "committed\n")
        git(root, "add", "--", "tracked.txt")
        commit(root, "fixture")
    }
    suspend fun git(cwd: Path, vararg args: String): String {
        val result = runner.run(cwd.toString(), args.toList())
        assertEquals(0, result.exitCode, args.toList().toString() + ": " + result.stderr)
        return result.stdout
    }
    suspend fun commit(cwd: Path, message: String) {
        git(cwd, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "-m", message)
    }
    fun request() = GitCreateWorktreeRequest(binding, target.toString(), "codex/managed")
    fun managed(store: DesktopManagedWorktreeStore, backend: GitBackend = actual) =
        DesktopManagedWorktreeRepository(backend, store) { owner }
    fun creator(create: suspend (GitRepository, GitCreateWorktreeRequest) -> GitCreateWorktreeResult): GitBackend =
        object : GitBackend by actual, GitWorktreeCreator {
            override fun canCreateWorktree(binding: GitBinding): Boolean = actual.canCreateWorktree(binding)
            override suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest) = create(repository, request)
        }
}

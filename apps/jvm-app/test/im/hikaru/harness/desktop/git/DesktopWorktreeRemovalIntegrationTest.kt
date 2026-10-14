package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlin.test.*

/**
 * Real Git, registry, sessions, pins and archives: the destructive removal path is only accepted
 * after every protection dimension is re-read and Git itself refuses to lose tracked content.
 */
class DesktopWorktreeRemovalIntegrationTest {
    @Test
    fun cleanManagedLinkedWorktreeIsRemovedByRealGitAndItsIntentIsRetired() = withRemoval { r ->
        r.fixture.registerLinkedWorktree()
        val repository = r.repository()
        val plan = r.plan(repository)
        assertTrue(plan.eligible, "clean managed worktree must be removable: " + plan.blocks)
        assertEquals(r.fixture.linked.toString(), plan.root)
        assertNotNull(plan.branch, "an eligible plan carries a re-verifiable branch fingerprint")
        val receipt = assertIs<ManagedWorktreeRemovalReceipt.Removed>(
            repository.removeWorktree(ManagedWorktreeRemovalRequest(plan)))
        assertEquals(plan.root, receipt.request.plan.root)
        assertFalse(Files.exists(r.fixture.linked), "real Git removes the worktree directory")
        assertTrue(r.fixture.actual.worktrees(r.fixture.gitRepository).none { it.path == plan.root },
            "the path must leave the real Git worktree list")
        assertTrue(r.inventory(repository).entries.none { it.root == plan.root },
            "a retired intent must not resurface as an owned or missing entry")
    }

    @Test
    fun stalePlanCannotDeleteAWorktreeThatGainedUncommittedContent() = withRemoval { r ->
        r.fixture.registerLinkedWorktree()
        val repository = r.repository()
        val plan = r.plan(repository)
        assertTrue(plan.eligible)
        // The page plan is now stale: content appears after the read but before dispatch.
        Files.writeString(r.fixture.linked.resolve("edited.txt"), "uncommitted\n")
        val receipt = assertIs<ManagedWorktreeRemovalReceipt.Blocked>(
            repository.removeWorktree(ManagedWorktreeRemovalRequest(plan)))
        assertTrue(receipt.blocks.any { it.kind == ManagedRemovalBlockKind.UncommittedChanges }, receipt.blocks.toString())
        assertTrue(Files.exists(r.fixture.linked.resolve("edited.txt")), "no force: the file must survive")
        assertTrue(r.fixture.actual.worktrees(r.fixture.gitRepository).any { it.path == plan.root })
    }

    @Test
    fun primaryWorktreeIsNeverARemovalTarget() = withRemoval { r ->
        r.fixture.registerLinkedWorktree()
        val repository = r.repository()
        val plan = r.plan(repository, r.fixture.root)
        assertFalse(plan.eligible)
        assertTrue(plan.blocks.any { it.kind == ManagedRemovalBlockKind.PrimaryWorktree }, plan.blocks.toString())
        assertIs<ManagedWorktreeRemovalReceipt.Blocked>(repository.removeWorktree(ManagedWorktreeRemovalRequest(plan)))
        assertTrue(Files.isDirectory(r.fixture.root))
    }

    @Test
    fun liveSessionRunningInTheWorktreeBlocksRemoval() = withRemoval { r ->
        r.fixture.registerLinkedWorktree()
        r.fixture.session("live-in-linked", cwd = r.fixture.linked.toString())
        val repository = r.repository()
        val plan = r.plan(repository)
        assertFalse(plan.eligible)
        assertTrue(plan.blocks.any { it.kind == ManagedRemovalBlockKind.RunningAgent }, plan.blocks.toString())
        assertIs<ManagedWorktreeRemovalReceipt.Blocked>(repository.removeWorktree(ManagedWorktreeRemovalRequest(plan)))
        assertTrue(Files.isDirectory(r.fixture.linked))
    }

    @Test
    fun ignoredAndNestedContentBlockRemovalWithoutForce() = withRemoval { r ->
        r.fixture.registerLinkedWorktree()
        val exclude = Path.of(r.fixture.gitRepository.commonDir).resolve("info/exclude")
        Files.createDirectories(exclude.parent)
        Files.writeString(exclude, "ignored.bin\n")
        Files.writeString(r.fixture.linked.resolve("ignored.bin"), "ignored\n")
        val nested = Files.createDirectory(r.fixture.linked.resolve("vendor"))
        Files.writeString(nested.resolve(".git"), "gitdir: nowhere\n")
        val plan = r.plan(r.repository())
        assertTrue(plan.blocks.any { it.kind == ManagedRemovalBlockKind.IgnoredContent }, plan.blocks.toString())
        assertTrue(plan.blocks.any { it.kind == ManagedRemovalBlockKind.NestedRepositories }, plan.blocks.toString())
        assertIs<ManagedWorktreeRemovalReceipt.Blocked>(
            r.repository().removeWorktree(ManagedWorktreeRemovalRequest(plan)))
        assertTrue(Files.isDirectory(r.fixture.linked))
    }

    @Test
    fun detachedHeadWorktreeIsRejectedEvenWithMatchingHeadFingerprint() = withRemoval { r ->
        r.fixture.registerLinkedWorktree()
        runGit(r.fixture.linked, "checkout", "--detach")
        val repository = r.fixture.actual
        val tree = repository.worktrees(r.fixture.gitRepository).single { it.path == r.fixture.linked.toString() }
        assertNull(tree.branch, "detached worktrees report no branch fingerprint")
        assertTrue(tree.detached)
        val rejected = assertIs<GitRemoveWorktreeResult.Rejected>(repository.removeWorktree(
            r.fixture.gitRepository,
            GitRemoveWorktreeRequest(r.fixture.binding, r.fixture.linked.toString(), requireNotNull(tree.headOid), null)))
        assertEquals(GitFailureKind.InvalidRequest, rejected.failure.kind)
        assertTrue(Files.isDirectory(r.fixture.linked))
    }

    private fun withRemoval(block: suspend (WorktreeRemovalFixture) -> Unit) = runBlocking {
        val r = WorktreeRemovalFixture()
        try { r.fixture.open(); block(r) } finally { r.close() }
    }
}

/** Real Git + registry + observation, plus the pin and archive services removal must re-verify. */
internal class WorktreeRemovalFixture : AutoCloseable {
    val fixture = WorktreeObservationFixture()
    val pinPath: Path = fixture.home.resolve("managed-chat-pins.json")
    val archivePath: Path = fixture.home.resolve("managed-chat-archive.json")
    fun repository(archive: DesktopWorktreeChatArchiveStore? = DesktopWorktreeChatArchiveStore(archivePath)) =
        DesktopManagedWorktreeRepository(fixture.actual, DesktopManagedWorktreeStore(fixture.registry),
            ownerValidator = { candidate -> if (candidate == fixture.binding) fixture.currentOwner else null },
            sessionObserver = fixture.host.sessionObserver,
            pinStore = DesktopWorktreePinStore(pinPath), archiveStore = archive)
    suspend fun inventory(repository: ManagedWorktreeRepository = repository()): ManagedWorktreeInventory =
        assertIs<ManagedWorktreeRead.Ready>(repository.read(fixture.gitRepository)).inventory
    suspend fun plan(repository: ManagedWorktreeRepository = repository(), root: Path = fixture.linked): ManagedWorktreeRemovalPlan =
        assertIs<ManagedWorktreeRemovalRead.Available>(
            assertNotNull(inventory(repository).entries.singleOrNull { it.root == root.toString() }).removal).plan
    override fun close() = fixture.close()
}

private suspend fun runGit(cwd: Path, vararg args: String) {
    val result = DesktopGitCommandRunner().run(cwd.toString(), args.toList())
    assertEquals(0, result.exitCode, args.toList().toString() + ": " + result.stderr)
}


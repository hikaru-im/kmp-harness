package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/** Real temporary Git repositories; success evidence comes from real Git objects. */
class DesktopGitBranchCreateTest {
    @Test
    fun createsAndChecksOutNewBranchFromHeadLeavingOriginalWorkspaceUntouched() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val original = fixture.originalEvidence()
            val repo = fixture.repository()
            val source = fixture.backend.status(repo)
            val head = requireNotNull(source.headOid)
            val request = GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head, "codex/new", "HEAD", head)
            val receipt = assertIs<GitCreateBranchResult.Created>(
                withTimeout(15_000) { fixture.backend.createBranch(repo, request) }).receipt
            assertEquals(request, receipt.request)
            assertEquals(repo, receipt.repository)
            assertEquals("refs/heads/codex/new", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertEquals(fixture.baseOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
            assertEquals("refs/heads/codex/new", receipt.status.branch)
            assertEquals(fixture.baseOid, receipt.status.headOid)
            assertTrue(receipt.branches.any { it.ref == "refs/heads/codex/new" && it.current && !it.remote })
            assertTrue(receipt.worktrees.any { it.path == repo.root && it.branch == "refs/heads/codex/new" })
            assertEquals("main content\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
            assertEquals(original, fixture.originalEvidence())
        } finally { fixture.close() }
    }

    @Test
    fun createsFromExistingLocalBranchWithoutCheckingThatBranchOutFirst() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val repo = fixture.repository()
            val source = fixture.backend.status(repo)
            val request = GitCreateBranchRequest(fixture.binding, repo.root, source.branch, requireNotNull(source.headOid),
                "codex/from-feature", "refs/heads/feature", fixture.featureOid)
            val receipt = assertIs<GitCreateBranchResult.Created>(
                withTimeout(15_000) { fixture.backend.createBranch(repo, request) }).receipt
            assertEquals("refs/heads/codex/from-feature", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
            assertEquals(fixture.featureOid, receipt.status.headOid)
            assertEquals("feature content\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
        } finally { fixture.close() }
    }

    @Test
    fun existingNameAndStaleStartRejectBeforeRunningSwitch() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val repo = fixture.repository()
            val source = fixture.backend.status(repo)
            val head = requireNotNull(source.headOid)
            val real = DesktopGitCommandRunner()
            var switches = 0
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "switch") switches++
                real.run(cwd, args)
            })
            val collision = GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head, "feature", "HEAD", head)
            val rejected = assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { backend.createBranch(repo, collision) })
            assertEquals(GitFailureKind.InvalidRequest, rejected.failure.kind)
            val stale = GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head,
                "codex/stale", "refs/heads/feature", fixture.baseOid)
            val staleRejected = assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { backend.createBranch(repo, stale) })
            assertEquals(GitFailureKind.StaleSnapshot, staleRejected.failure.kind)
            assertEquals(0, switches, "preflight rejection must not run git switch")
            assertEquals("refs/heads/sandbox", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertFalse(fixture.backend.branches(repo).any { it.ref == "refs/heads/codex/stale" })
        } finally { fixture.close() }
    }

    @Test
    fun dirtyWorktreeAndMissingStartRejectBeforeRunningSwitch() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val repo = fixture.repository()
            val source = fixture.backend.status(repo)
            val head = requireNotNull(source.headOid)
            val real = DesktopGitCommandRunner()
            var switches = 0
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "switch") switches++
                real.run(cwd, args)
            })
            Files.writeString(fixture.workspace.resolve("tracked.txt"), "user edit after read\n")
            val dirty = GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head, "codex/dirty", "HEAD", head)
            val rejected = assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { backend.createBranch(repo, dirty) })
            assertEquals(GitFailureKind.InvalidRequest, rejected.failure.kind)
            assertEquals("user edit after read\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
            Files.writeString(fixture.workspace.resolve("tracked.txt"), "main content\n")
            val missing = GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head,
                "codex/missing", "refs/heads/absent", head)
            val missingRejected = assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { backend.createBranch(repo, missing) })
            assertEquals(GitFailureKind.StaleSnapshot, missingRejected.failure.kind)
            assertEquals(0, switches, "preflight rejection must not run git switch")
        } finally { fixture.close() }
    }

    @Test
    fun invalidNamesForeignScopeAndRemoteBindingAreRejected() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val repo = fixture.repository()
            val source = fixture.backend.status(repo)
            val head = requireNotNull(source.headOid)
            assertTrue(fixture.backend.canCreateBranch(fixture.binding))
            assertFalse(fixture.backend.canCreateBranch(fixture.binding.copy(access = GitAccess.RemoteUnavailable)))
            assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { fixture.backend.createBranch(repo,
                GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head, "bad name", "HEAD", head)) })
            assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { fixture.backend.createBranch(repo,
                GitCreateBranchRequest(fixture.binding, repo.root, source.branch, head, "codex/ok", "refs/heads/x y", head)) })
            assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { fixture.backend.createBranch(repo,
                GitCreateBranchRequest(fixture.binding, "/other/root", source.branch, head, "codex/ok", "HEAD", head)) })
            assertIs<GitCreateBranchResult.Rejected>(withTimeout(15_000) { fixture.backend.createBranch(repo,
                GitCreateBranchRequest(fixture.binding.copy(access = GitAccess.Disconnected), repo.root, source.branch,
                    head, "codex/ok", "HEAD", head)) })
            assertEquals("refs/heads/sandbox", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertFalse(fixture.backend.branches(repo).any { it.ref == "refs/heads/codex/ok" })
        } finally { fixture.close() }
    }
}

package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class DesktopGitBranchCheckoutTest {
    @Test
    fun ignoredTargetCollisionPreservesBytesAndReturnsUnknownAfterGitRejects() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            fixture.git(fixture.root, "switch", "feature")
            Files.writeString(fixture.root.resolve("collision.log"), "tracked target content")
            fixture.git(fixture.root, "add", "--force", "--", "collision.log")
            fixture.git(fixture.root, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
                "-c", "commit.gpgsign=false", "commit", "-m", "tracked collision")
            fixture.git(fixture.root, "switch", "main")
            val excludes = fixture.directory.resolve("ignored-patterns")
            Files.writeString(excludes, "collision.log\n")
            fixture.git(fixture.workspace, "config", "core.excludesFile", excludes.toString())
            fixture.git(fixture.workspace, "config", "submodule.recurse", "true")
            val ignored = fixture.workspace.resolve("collision.log")
            Files.writeString(ignored, "ignored user bytes must survive")
            val repo = fixture.repository()
            val request = fixture.request(repo)
            assertTrue(fixture.backend.status(repo).changes.isEmpty(), "normal status excludes ignored content")
            val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
            val original = fixture.originalEvidence()
            val real = DesktopGitCommandRunner()
            var switches = 0
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "switch") switches++
                real.run(cwd, args)
            })
            assertIs<GitBranchCheckoutResult.UnknownOutcome>(withTimeout(15_000) { backend.switchBranch(repo, request) })
            assertEquals(1, switches, "ignored collision must reach real Git without overwriting ignored bytes")
            assertEquals("ignored user bytes must survive", Files.readString(ignored))
            assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
            assertEquals("refs/heads/sandbox", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertEquals(fixture.baseOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
            assertEquals(original, fixture.originalEvidence())
        } finally { fixture.close() }
    }

    @Test
    fun switchesRealLinkedWorktreeAndLeavesOriginalWorkspaceExactlyUntouched() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val before = fixture.originalEvidence()
            val repo = fixture.repository()
            val request = fixture.request(repo)
            val result = withTimeout(15_000) { fixture.backend.switchBranch(repo, request) }
            val receipt = assertIs<GitBranchCheckoutResult.Switched>(result).receipt
            assertEquals(request, receipt.request)
            assertEquals(repo, receipt.repository)
            assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
            assertEquals("refs/heads/feature", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertEquals("feature content\n", Files.readString(fixture.workspace.resolve("tracked.txt")))
            assertTrue(receipt.status.changes.isEmpty())
            assertEquals(before, fixture.originalEvidence())
        } finally { fixture.close() }
    }

    @Test
    fun dirtyIndexWorkingTreeAndUntrackedAreRejectedWithoutChangingAnyBytes() = runBlocking {
        for (dirty in listOf("index", "working", "untracked", "conflict")) {
            val fixture = BranchCheckoutFixture()
            try {
                val repo = fixture.repository()
                val request = fixture.request(repo)
                when (dirty) {
                    "index" -> {
                        Files.writeString(fixture.workspace.resolve("tracked.txt"), "staged keep\n")
                        fixture.git(fixture.workspace, "add", "--", "tracked.txt")
                    }
                    "working" -> Files.writeString(fixture.workspace.resolve("tracked.txt"), "working keep\n")
                    "untracked" -> Files.writeString(fixture.workspace.resolve("untracked.txt"), "untracked keep\n")
                    else -> {
                        fixture.git(fixture.workspace, "update-index", "--force-remove", "--", "tracked.txt")
                        val blob = fixture.git(fixture.workspace, "rev-parse", "HEAD:tracked.txt").trim()
                        fixture.gitInput(fixture.workspace, "100644 $blob 1\ttracked.txt\n100644 $blob 2\ttracked.txt\n100644 $blob 3\ttracked.txt\n",
                            "update-index", "--index-info")
                    }
                }
                val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
                val status = fixture.git(fixture.workspace, "status", "--porcelain=v1", "-z", "--untracked-files=all")
                val contents = Files.readString(fixture.workspace.resolve("tracked.txt"))
                val original = fixture.originalEvidence()
                val rejected = assertIs<GitBranchCheckoutResult.Rejected>(fixture.backend.switchBranch(repo, request))
                assertEquals(GitFailureKind.InvalidRequest, rejected.failure.kind, dirty)
                assertEquals(status, fixture.git(fixture.workspace, "status", "--porcelain=v1", "-z", "--untracked-files=all"))
                assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
                assertEquals(contents, Files.readString(fixture.workspace.resolve("tracked.txt")))
                if (dirty == "untracked") assertEquals("untracked keep\n", Files.readString(fixture.workspace.resolve("untracked.txt")))
                assertEquals("refs/heads/sandbox", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
                assertEquals(original, fixture.originalEvidence())
            } finally { fixture.close() }
        }
    }

    @Test
    fun changedSourceOrTargetOidRejectsBeforeSwitch() = runBlocking {
        for (source in listOf(true, false)) {
            val fixture = BranchCheckoutFixture()
            try {
                val repo = fixture.repository()
                val request = fixture.request(repo)
                fixture.git(fixture.workspace, "update-ref", if (source) "refs/heads/sandbox" else "refs/heads/feature",
                    if (source) fixture.featureOid else fixture.baseOid)
                val before = fixture.workspaceEvidence(repo)
                val result = assertIs<GitBranchCheckoutResult.Rejected>(fixture.backend.switchBranch(repo, request))
                assertEquals(GitFailureKind.StaleSnapshot, result.failure.kind)
                assertEquals(before, fixture.workspaceEvidence(repo))
            } finally { fixture.close() }
        }
    }

    @Test
    fun branchUsedByAnotherWorktreeIsNotForcedAndFailureRequiresRescan() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val repo = fixture.repository()
            val before = fixture.workspaceEvidence(repo)
            val original = fixture.originalEvidence()
            val request = fixture.request(repo, "refs/heads/main")
            assertIs<GitBranchCheckoutResult.UnknownOutcome>(fixture.backend.switchBranch(repo, request))
            assertEquals(before, fixture.workspaceEvidence(repo))
            assertEquals(original, fixture.originalEvidence())
        } finally { fixture.close() }
    }

    @Test
    fun injectionRemoteForeignHostAndForgedRepositoryScopeNeverDispatch() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            var switches = 0
            val real = DesktopGitCommandRunner()
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "switch") switches++
                real.run(cwd, args)
            })
            val repo = fixture.repository()
            val request = fixture.request(repo)
            val before = fixture.workspaceEvidence(repo)
            fixture.git(fixture.workspace, "update-ref", "refs/remotes/origin/feature", fixture.featureOid)
            for (ref in listOf("--force", "refs/heads/--force", "refs/heads/@{-1}",
                "refs/remotes/origin/feature", "refs/heads/feature\u0000--force", "feature")) {
                assertIs<GitBranchCheckoutResult.Rejected>(backend.switchBranch(repo, request.copy(targetRef = ref)), ref)
            }
            for (binding in listOf(fixture.binding.copy(hostId = HostId("foreign")),
                fixture.binding.copy(access = GitAccess.RemoteUnavailable), fixture.binding.copy(generation = 2))) {
                assertIs<GitBranchCheckoutResult.Rejected>(backend.switchBranch(repo, request.copy(binding = binding)))
            }
            assertIs<GitBranchCheckoutResult.Rejected>(backend.switchBranch(repo.copy(gitDir = fixture.root.resolve(".git").toString()), request))
            assertIs<GitBranchCheckoutResult.Rejected>(backend.switchBranch(repo.copy(commonDir = fixture.directory.toString()), request))
            assertIs<GitBranchCheckoutResult.Rejected>(backend.switchBranch(repo, request.copy(root = fixture.root.toString())))
            assertEquals(0, switches)
            assertEquals(before, fixture.workspaceEvidence(repo))
        } finally { fixture.close() }
    }

    @Test
    fun explicitDetachedHeadCanSwitchButUnbornCannot() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            fixture.git(fixture.workspace, "switch", "--detach", fixture.baseOid)
            val repo = fixture.repository()
            val request = fixture.request(repo)
            assertNull(request.expectedBranch)
            assertIs<GitBranchCheckoutResult.Switched>(fixture.backend.switchBranch(repo, request))
            val unborn = Files.createDirectory(fixture.directory.resolve("unborn"))
            fixture.git(unborn, "init", "--initial-branch=main")
            val unbornRepo = fixture.backend.discover(fixture.binding, unborn.toString())
            assertIs<GitBranchCheckoutResult.Rejected>(fixture.backend.switchBranch(unbornRepo, request.copy(root = unbornRepo.root,
                expectedBranch = "refs/heads/main")))
            assertEquals("refs/heads/main", fixture.git(unborn, "symbolic-ref", "HEAD").trim())
        } finally { fixture.close() }
    }
}

/** Real temporary repositories only. No writes to HOME and no fake Git receipts. */
internal class BranchCheckoutFixture {
    val directory: Path = Files.createTempDirectory("harness-branch-checkout-").toRealPath()
    val root: Path = Files.createDirectory(directory.resolve("original repo"))
    val workspace: Path = directory.resolve("linked workspace")
    val host = HostId("branch-checkout-local")
    val binding = GitBinding(null, host, 1, GitAccess.LocalReadOnly)
    val backend = DesktopGitBackend(host)
    val baseOid: String
    val featureOid: String
    init {
        git(root, "init", "--initial-branch=main")
        git(root, "config", "core.hooksPath", "/dev/null")
        git(root, "config", "core.fsmonitor", "false")
        Files.writeString(root.resolve("tracked.txt"), "main content\n")
        git(root, "add", "--", "tracked.txt")
        commit("main fixture")
        baseOid = git(root, "rev-parse", "HEAD").trim()
        git(root, "switch", "-c", "feature")
        Files.writeString(root.resolve("tracked.txt"), "feature content\n")
        git(root, "add", "--", "tracked.txt")
        commit("feature fixture")
        featureOid = git(root, "rev-parse", "HEAD").trim()
        git(root, "switch", "main")
        git(root, "worktree", "add", "-b", "sandbox", "--", workspace.toString(), baseOid)
    }
    private fun commit(message: String) {
        git(root, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "-m", message)
    }
    suspend fun repository(): GitRepository = backend.discover(binding, workspace.toString())
    suspend fun request(repository: GitRepository, ref: String = "refs/heads/feature"): GitBranchCheckoutRequest {
        val source = backend.status(repository)
        val target = backend.branches(repository).single { it.ref == ref }
        return GitBranchCheckoutRequest(binding, repository.root, source.branch, requireNotNull(source.headOid), ref, target.oid)
    }
    fun originalEvidence(): List<String> = listOf(git(root, "symbolic-ref", "HEAD"), git(root, "rev-parse", "HEAD"),
        git(root, "status", "--porcelain=v1", "-z"), Files.readAllBytes(root.resolve(".git/index")).joinToString(),
        Files.readString(root.resolve("tracked.txt")))
    fun workspaceEvidence(repo: GitRepository): List<String> = listOf(git(workspace, "rev-parse", "HEAD"),
        git(workspace, "status", "--porcelain=v1", "-z", "--untracked-files=all"),
        Files.readAllBytes(Path.of(repo.gitDir).resolve("index")).joinToString(),
        Files.readString(workspace.resolve("tracked.txt")))
    fun git(cwd: Path, vararg arguments: String): String = gitInput(cwd, null, *arguments)
    fun gitInput(cwd: Path, input: String?, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "--no-optional-locks", "-c", "core.fsmonitor=false",
            "-c", "core.hooksPath=/dev/null") + arguments).directory(cwd.toFile()).redirectErrorStream(true).apply {
            listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE",
                "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES").forEach { environment().remove(it) }
            environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
            environment()["GIT_TERMINAL_PROMPT"] = "0"
        }.start()
        try {
            process.outputStream.use { if (input != null) it.write(input.toByteArray()) }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "bounded git fixture command: ${arguments.toList()}")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.exitValue(), "git ${arguments.toList()}: $output")
            return output
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
    fun close() { directory.toFile().deleteRecursively() }
}

package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*

/** Only self-created temporary repositories; success evidence comes from real Git objects. */
class DesktopGitCommitTest {
    @Test
    fun partialIndexOnlyCommitPreservesWorkingUntrackedIgnoredAndOriginalWorkspace() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted staged\n")
            Files.writeString(f.workspace.resolve("tracked.txt"), "unstaged user bytes\n")
            Files.writeString(f.workspace.resolve("untracked.txt"), "untracked keep\n")
            Files.writeString(f.workspace.resolve("ignored.log"), "ignored keep\n")
            val original = f.originalEvidence()
            val repo = f.repository()
            val request = f.request(repo, "--amend\nquotes ' and literal \$(text)\n")
            val receipt = assertIs<GitCommitResult.Committed>(withTimeout(15_000) { f.backend.commit(repo, request) }).receipt
            assertEquals(f.baseOid, receipt.parentOid)
            assertEquals(receipt.commitOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
            assertEquals("accepted staged\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
            assertEquals("unstaged user bytes\n", Files.readString(f.workspace.resolve("tracked.txt")))
            assertEquals("untracked keep\n", Files.readString(f.workspace.resolve("untracked.txt")))
            assertEquals("ignored keep\n", Files.readString(f.workspace.resolve("ignored.log")))
            assertEquals(request.message, f.git(f.workspace, "cat-file", "commit", receipt.commitOid).substringAfter("\n\n"))
            assertEquals(request.snapshot.indexDigest, receipt.treeDigest)
            assertEquals(0, receipt.snapshot.stagedCount)
            assertEquals(1, receipt.snapshot.status.unstagedCount)
            assertEquals(original, f.originalEvidence())
        } finally { f.close() }
    }

    @Test
    fun includeUnstagedStagesTrackedAndUntrackedKeepsIgnoredOutThenCommitsVerified() = runBlocking {
        val f = CommitFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), "worktree tracked\n")
            Files.writeString(f.workspace.resolve("untracked.txt"), "worktree untracked\n")
            Files.writeString(f.workspace.resolve("ignored.log"), "ignored keep\n")
            val repo = f.repository()
            val snapshot = f.backend.readCommitSnapshot(repo)
            assertEquals(0, snapshot.stagedCount)
            assertEquals(0, snapshot.status.stagedCount)
            val request = GitCommitRequest(snapshot, requireNotNull(normalizeGitCommitMessage("include unstaged\n")),
                includeUnstaged = true)
            val receipt = assertIs<GitCommitResult.Committed>(
                withTimeout(15_000) { f.backend.commit(repo, request) }).receipt
            assertTrue(receipt.stagedByRequest)
            assertNotEquals(snapshot.indexDigest, receipt.stagedIndexDigest)
            assertEquals(receipt.treeDigest, receipt.stagedIndexDigest)
            assertEquals(f.baseOid, receipt.parentOid)
            assertEquals("worktree tracked\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
            assertEquals("worktree untracked\n", f.git(f.workspace, "show", "HEAD:untracked.txt"))
            assertFalse(f.git(f.workspace, "ls-tree", "-r", "--name-only", "HEAD").contains("ignored.log"))
            assertEquals("worktree tracked\n", Files.readString(f.workspace.resolve("tracked.txt")))
            assertEquals("ignored keep\n", Files.readString(f.workspace.resolve("ignored.log")))
            assertEquals(request.message, f.git(f.workspace, "cat-file", "commit", receipt.commitOid).substringAfter("\n\n"))
            assertEquals(0, receipt.snapshot.stagedCount)
            assertEquals(0, receipt.snapshot.status.unstagedCount)
            assertEquals(0, receipt.snapshot.status.untrackedCount)
            assertEquals(1, f.commitInvocations)
        } finally { f.close() }
    }

    @Test
    fun includeUnstagedWithNothingToStageIsRejectedBeforeAnyCommand() = runBlocking {
        val f = CommitFixture()
        try {
            val repo = f.repository()
            val snapshot = f.backend.readCommitSnapshot(repo)
            assertTrue(snapshot.status.changes.isEmpty())
            val request = GitCommitRequest(snapshot, requireNotNull(normalizeGitCommitMessage("nothing\n")),
                includeUnstaged = true)
            val result = assertIs<GitCommitResult.Rejected>(
                withTimeout(15_000) { f.backend.commit(repo, request) })
            assertEquals(GitFailureKind.InvalidRequest, result.failure.kind)
            assertEquals(0, f.commitInvocations)
            assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
        } finally { f.close() }
    }

    @Test
    fun includeUnstagedStaleIndexRejectsBeforeStagingAndLeavesIndexBytesAlone() = runBlocking {
        val f = CommitFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), "worktree change\n")
            val repo = f.repository()
            val request = GitCommitRequest(f.backend.readCommitSnapshot(repo),
                requireNotNull(normalizeGitCommitMessage("stale include\n")), includeUnstaged = true)
            // A competing stage changes the index digest after the accepted snapshot was read.
            f.git(f.workspace, "add", "--", "tracked.txt")
            val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
            val result = assertIs<GitCommitResult.Rejected>(
                withTimeout(15_000) { f.backend.commit(repo, request) })
            assertEquals(GitFailureKind.StaleSnapshot, result.failure.kind)
            assertEquals(0, f.commitInvocations)
            assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
            assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
        } finally { f.close() }
    }

    @Test
    fun sameStagedCountButDifferentBlobRejectsBeforeInvocationWithoutChangingBytes() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted\n")
            val repo = f.repository()
            val request = f.request(repo)
            f.stage("competing blob\n")
            val current = f.backend.readCommitSnapshot(repo)
            assertEquals(request.snapshot.stagedCount, current.stagedCount)
            assertNotEquals(request.snapshot.indexDigest, current.indexDigest)
            val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
            val result = assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request))
            assertEquals(GitFailureKind.StaleSnapshot, result.failure.kind)
            assertEquals(0, f.commitInvocations)
            assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
            assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
            assertEquals("competing blob\n", Files.readString(f.workspace.resolve("tracked.txt")))
        } finally { f.close() }
    }

    @Test
    fun inProgressMarkersAndConflictsRejectWithoutDispatchOrDeletingEvidence() = runBlocking {
        for (marker in listOf("MERGE_HEAD", "REBASE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD",
            "rebase-merge", "rebase-apply", "sequencer", "index.lock")) {
            val f = CommitFixture()
            try {
                f.stage("accepted\n")
                val repo = f.repository()
                val request = f.request(repo)
                val path = Path.of(f.git(f.workspace, "rev-parse", "--path-format=absolute", "--git-path", marker).trim())
                if (marker in listOf("rebase-merge", "rebase-apply", "sequencer")) Files.createDirectory(path)
                else Files.writeString(path, f.baseOid + "\n")
                val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
                assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request), marker)
                assertNotNull(f.backend.readCommitSnapshot(repo).blockedReason, marker)
                assertTrue(Files.exists(path), marker)
                assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
                assertEquals(0, f.commitInvocations)
            } finally { f.close() }
        }
        val f = CommitFixture()
        try {
            f.stage("accepted\n")
            val repo = f.repository()
            val request = f.request(repo)
            val blob = f.git(f.workspace, "rev-parse", "HEAD:tracked.txt").trim()
            f.git(f.workspace, "update-index", "--force-remove", "--", "tracked.txt")
            f.gitInput(f.workspace, "100644 $blob 1\ttracked.txt\n100644 $blob 2\ttracked.txt\n100644 $blob 3\ttracked.txt\n",
                "update-index", "--index-info")
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request))
            assertNotNull(f.backend.readCommitSnapshot(repo).blockedReason)
            assertEquals(0, f.commitInvocations)
        } finally { f.close() }
    }

    @Test
    fun hooksAndSigningConfiguredInRepositoryNeverExecute() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted\n")
            val hooks = Files.createDirectory(f.directory.resolve("hooks"))
            val hookMarker = f.directory.resolve("hook-ran")
            for (name in listOf("pre-commit", "prepare-commit-msg", "commit-msg", "post-commit", "reference-transaction")) {
                val script = hooks.resolve(name)
                Files.writeString(script, "#!/bin/sh\nprintf ran >> '$hookMarker'\nexit 99\n")
                assertTrue(script.toFile().setExecutable(true))
            }
            val signing = f.directory.resolve("signer")
            val signingMarker = f.directory.resolve("signer-ran")
            Files.writeString(signing, "#!/bin/sh\nprintf ran >> '$signingMarker'\nexit 99\n")
            assertTrue(signing.toFile().setExecutable(true))
            f.git(f.workspace, "config", "core.hooksPath", hooks.toString())
            f.git(f.workspace, "config", "commit.gpgSign", "true")
            f.git(f.workspace, "config", "gpg.program", signing.toString())
            val repo = f.repository()
            val receipt = assertIs<GitCommitResult.Committed>(f.backend.commit(repo, f.request(repo))).receipt
            assertFalse(Files.exists(hookMarker))
            assertFalse(Files.exists(signingMarker))
            assertFalse(f.git(f.workspace, "cat-file", "commit", receipt.commitOid).substringBefore("\n\n").contains("gpgsig"))
            assertEquals(1, f.commitInvocations)
            assertEquals("true", f.git(f.workspace, "config", "--get", "commit.gpgSign").trim(), "policy never edits config")
        } finally { f.close() }
    }

    @Test
    fun realCommitThenConfirmationFailureIsUnknownAndNeverRolledBack() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted\n")
            val repo = f.repository()
            val request = f.request(repo)
            val backend = DesktopGitBackend(f.host, GitCommandRunner { cwd, args ->
                if (args.firstOrNull() == "cat-file") throw GitReadException(GitFailure(GitFailureKind.Timeout, "confirmation unavailable"))
                f.runner.run(cwd, args)
            })
            assertIs<GitCommitResult.UnknownOutcome>(backend.commit(repo, request))
            val actual = f.git(f.workspace, "rev-parse", "HEAD").trim()
            assertNotEquals(f.baseOid, actual)
            assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD^").trim())
            assertEquals("accepted\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
            assertEquals(1, f.commitInvocations)
            assertEquals(0, f.backend.readCommitSnapshot(repo).stagedCount)
        } finally { f.close() }
    }

    @Test
    fun emptyDetachedUnbornForeignScopeAndMalformedMessageNeverDispatch() = runBlocking {
        val f = CommitFixture()
        try {
            val repo = f.repository()
            val empty = f.request(repo)
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, empty))
            f.stage("accepted\n")
            val request = f.request(repo)
            for (message in listOf("", "   ", "bad\u0000message", "not normalized"))
                assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request.copy(message = message)))
            for (binding in listOf(f.binding.copy(hostId = HostId("foreign")), f.binding.copy(access = GitAccess.RemoteUnavailable))) {
                val other = repo.copy(binding = binding)
                assertIs<GitCommitResult.Rejected>(f.backend.commit(other, request.copy(snapshot = request.snapshot.copy(repository = other))))
            }
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo.copy(commonDir = f.directory.toString()), request))
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo.copy(gitDir = f.root.resolve(".git").toString()), request))
            f.git(f.workspace, "switch", "--detach", f.baseOid)
            assertNotNull(f.backend.readCommitSnapshot(repo).blockedReason)
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request))
            val unborn = Files.createDirectory(f.directory.resolve("unborn"))
            f.git(unborn, "init", "--initial-branch=main")
            val unbornRepo = f.backend.discover(f.binding, unborn.toString())
            val unbornSnapshot = f.backend.readCommitSnapshot(unbornRepo)
            assertNotNull(unbornSnapshot.blockedReason)
            assertIs<GitCommitResult.Rejected>(f.backend.commit(unbornRepo, GitCommitRequest(unbornSnapshot, "message\n")))
            assertEquals(0, f.commitInvocations)
        } finally { f.close() }
    }

    @Test
    fun managedDecoratorForwardsRealCommitWithoutTouchingRegistration() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted\n")
            val file = f.directory.resolve("managed.json")
            val repository = DesktopManagedWorktreeRepository(f.backend, DesktopManagedWorktreeStore(file))
            val decorated = DesktopManagedGitBackend(f.backend, repository)
            val repo = decorated.discover(f.binding, f.workspace.toString())
            assertTrue(decorated.canCommit(f.binding))
            assertFalse(decorated.canCreateWorktree(f.binding), "missing validator does not affect index-only commit")
            val snapshot = decorated.readCommitSnapshot(repo)
            assertIs<GitCommitResult.Committed>(decorated.commit(repo, GitCommitRequest(snapshot, "decorated\n")))
            assertFalse(Files.exists(file))
            assertFalse(Files.exists(file.resolveSibling("managed.json.lock")))
            val readOnly = DesktopManagedGitBackend(object : GitBackend by f.backend {}, repository)
            assertFalse(readOnly.canCommit(f.binding))
            assertIs<GitCommitResult.Rejected>(readOnly.commit(repo, GitCommitRequest(snapshot, "message\n")))
            assertEquals(1, f.commitInvocations)
        } finally { f.close() }
    }
}

/** Real isolated command runner; never reads/writes test process HOME or global/system Git config. */
internal class CommitFixture : AutoCloseable {
    val directory: Path = Files.createTempDirectory("harness-commit-").toRealPath()
    val root: Path = Files.createDirectory(directory.resolve("original"))
    val workspace: Path = directory.resolve("workspace")
    val host = HostId("commit-local")
    val binding = GitBinding(null, host, 1, GitAccess.LocalReadOnly)
    private val invocationCount = java.util.concurrent.atomic.AtomicInteger()
    val commitInvocations: Int get() = invocationCount.get()
    val runner = GitCommandRunner { cwd, args ->
        if ("commit" in args && args.firstOrNull() != "cat-file") invocationCount.incrementAndGet()
        withContext(Dispatchers.IO) { execute(Path.of(cwd), args, null) }
    }
    val backend = DesktopGitBackend(host, runner)
    val baseOid: String
    init {
        git(root, "init", "--initial-branch=main")
        git(root, "config", "user.name", "Harness Test")
        git(root, "config", "user.email", "test@example.invalid")
        git(root, "config", "core.hooksPath", "/dev/null")
        git(root, "config", "commit.gpgSign", "false")
        Files.writeString(root.resolve("tracked.txt"), "base\n")
        Files.writeString(root.resolve(".gitignore"), "ignored.log\n")
        git(root, "add", "--", "tracked.txt", ".gitignore")
        git(root, "commit", "-m", "fixture base")
        baseOid = git(root, "rev-parse", "HEAD").trim()
        git(root, "worktree", "add", "-b", "sandbox", "--", workspace.toString(), baseOid)
    }
    fun stage(text: String) {
        Files.writeString(workspace.resolve("tracked.txt"), text)
        git(workspace, "add", "--", "tracked.txt")
    }
    suspend fun repository() = backend.discover(binding, workspace.toString())
    suspend fun request(repo: GitRepository, message: String = "accepted message\n") =
        GitCommitRequest(backend.readCommitSnapshot(repo), requireNotNull(normalizeGitCommitMessage(message)))
    fun originalEvidence() = listOf(git(root, "rev-parse", "HEAD"), git(root, "symbolic-ref", "HEAD"),
        git(root, "status", "--porcelain=v1", "-z"), Files.readAllBytes(root.resolve(".git/index")).joinToString(),
        Files.readString(root.resolve("tracked.txt")))
    fun git(cwd: Path, vararg args: String): String = gitInput(cwd, null, *args)
    fun gitInput(cwd: Path, input: String?, vararg args: String): String {
        val result = execute(cwd, args.toList(), input)
        assertEquals(0, result.exitCode, "git " + args.toList() + ": " + result.stderr)
        return result.stdout
    }
    private fun execute(cwd: Path, args: List<String>, input: String?): GitCommandResult {
        val process = ProcessBuilder(listOf("git", "--no-optional-locks", "-c", "core.fsmonitor=false") + args)
            .directory(cwd.toFile()).apply {
                listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY",
                    "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_CONFIG_COUNT", "GIT_CONFIG_PARAMETERS",
                    "GIT_AUTHOR_NAME", "GIT_AUTHOR_EMAIL", "GIT_COMMITTER_NAME", "GIT_COMMITTER_EMAIL").forEach { environment().remove(it) }
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_TERMINAL_PROMPT"] = "0"
                environment()["GIT_OPTIONAL_LOCKS"] = "0"
            }.start()
        try {
            process.outputStream.use { if (input != null) it.write(input.toByteArray(Charsets.UTF_8)) }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "bounded real fixture command")
            return GitCommandResult(process.exitValue(), process.inputStream.readAllBytes().decodeToString(throwOnInvalidSequence = true),
                process.errorStream.readAllBytes().decodeToString(throwOnInvalidSequence = true))
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
    override fun close() { directory.toFile().deleteRecursively() }
}

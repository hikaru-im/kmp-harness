package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Actual Git mutations occur only in disposable repositories owned by each test. */
class WorktreeCreationProcessTest {
    @Test
    fun createsFromSelectedRefAndPreservesDirtySourceIndexAndFiles() = runBlocking {
        withFixture { fixture ->
            val selectedHead = fixture.git("rev-parse", "HEAD").trim()
            fixture.git("branch", "selected-base")
            Files.writeString(fixture.root.resolve("tracked.txt"), "second commit\n")
            fixture.git("add", "--", "tracked.txt")
            fixture.commit("second")
            fixture.dirtySource()
            val beforeIndex = Files.readAllBytes(fixture.root.resolve(".git/index"))
            val beforeStatus = fixture.git("status", "--porcelain=v1", "-z")
            val beforeHead = fixture.git("rev-parse", "HEAD").trim()
            val backend = DesktopGitBackend(fixture.binding.hostId)
            val repository = backend.discover(fixture.binding, fixture.root.toString())
            val target = fixture.directory.resolve("工作树 with spaces")
            val result = assertIs<GitCreateWorktreeResult.Created>(backend.createWorktree(repository,
                GitCreateWorktreeRequest(fixture.binding, target.toString(), "codex/selected", "refs/heads/selected-base")))
            assertEquals(target.toString(), result.receipt.worktree.path)
            assertEquals("refs/heads/codex/selected", result.receipt.worktree.branch)
            assertEquals(selectedHead, result.receipt.startOid)
            assertEquals(selectedHead, fixture.gitAt(target, "rev-parse", "HEAD").trim())
            assertEquals("committed\n", Files.readString(target.resolve("tracked.txt")))
            assertFalse(Files.exists(target.resolve("untracked.txt")))
            assertTrue(result.receipt.worktree in backend.worktrees(repository))
            assertContentEquals(beforeIndex, Files.readAllBytes(fixture.root.resolve(".git/index")))
            assertEquals(beforeStatus, fixture.git("status", "--porcelain=v1", "-z"))
            assertEquals(beforeHead, fixture.git("rev-parse", "HEAD").trim())
            assertEquals("working\n", Files.readString(fixture.root.resolve("tracked.txt")))
            assertEquals("keep\n", Files.readString(fixture.root.resolve("untracked.txt")))
        }
    }

    @Test
    fun rejectsExistingAndInvalidDestinationsWithoutCreatingBranchOrOverwritingFiles() = runBlocking {
        withFixture { fixture ->
            val backend = DesktopGitBackend(fixture.binding.hostId)
            val repository = backend.discover(fixture.binding, fixture.root.toString())
            val existing = Files.createDirectory(fixture.directory.resolve("existing"))
            Files.writeString(existing.resolve("keep.txt"), "untouched")
            val requests = listOf(
                GitCreateWorktreeRequest(fixture.binding, existing.toString(), "codex/existing"),
                GitCreateWorktreeRequest(fixture.binding, "relative", "codex/relative"),
                GitCreateWorktreeRequest(fixture.binding, fixture.root.resolve("inside").toString(), "codex/inside"),
                GitCreateWorktreeRequest(fixture.binding, fixture.directory.resolve("missing/child").toString(), "codex/missing"),
                GitCreateWorktreeRequest(fixture.binding, fixture.directory.resolve("option").toString(), "--force"),
                GitCreateWorktreeRequest(fixture.binding, fixture.directory.resolve("bad-ref").toString(), "codex/bad-ref", "--detach"),
            )
            requests.forEach { request ->
                val failure = assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(repository, request))
                assertFalse(failure.failure.needsRescan)
            }
            assertEquals("untouched", Files.readString(existing.resolve("keep.txt")))
            assertEquals(listOf("refs/heads/main"), backend.branches(repository).map { it.ref })
            assertEquals(1, backend.worktrees(repository).size)
            assertFalse(Files.exists(fixture.root.resolve("inside")))
            assertFalse(Files.exists(fixture.directory.resolve("option")))
        }
    }

    @Test
    fun rejectsRemoteAndMismatchedRepositoryScope() = runBlocking {
        withFixture { fixture ->
            val backend = DesktopGitBackend(fixture.binding.hostId)
            val repository = backend.discover(fixture.binding, fixture.root.toString())
            val target = fixture.directory.resolve("forbidden")
            val request = GitCreateWorktreeRequest(fixture.binding, target.toString(), "codex/forbidden")
            val remote = fixture.binding.copy(access = GitAccess.RemoteUnavailable)
            assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(repository, request.copy(binding = remote)))
            assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(
                repository.copy(commonDir = fixture.directory.toString()), request))
            assertFalse(Files.exists(target))
            assertEquals(1, backend.worktrees(repository).size)
        }
    }

    @Test
    fun failedConfirmationReturnsUnknownEvenWhenActualGitCreationSucceeded() = runBlocking {
        withFixture { fixture ->
            val delegate = DesktopGitCommandRunner()
            var added = false
            val backend = DesktopGitBackend(fixture.binding.hostId, GitCommandRunner { cwd, argv ->
                if (added && argv.take(2) == listOf("worktree", "list"))
                    GitCommandResult(1, "", "injected confirmation failure")
                else delegate.run(cwd, argv).also { if (argv.take(2) == listOf("worktree", "add")) added = true }
            })
            val repository = backend.discover(fixture.binding, fixture.root.toString())
            val target = fixture.directory.resolve("created-but-unconfirmed")
            val result = assertIs<GitCreateWorktreeResult.Failed>(backend.createWorktree(repository,
                GitCreateWorktreeRequest(fixture.binding, target.toString(), "codex/unconfirmed")))
            assertTrue(result.failure.needsRescan)
            assertTrue(Files.exists(target.resolve(".git")))
            val actual = DesktopGitBackend(fixture.binding.hostId).worktrees(repository)
            assertEquals("refs/heads/codex/unconfirmed", actual.single { it.path == target.toString() }.branch)
            assertEquals(2, actual.size)
        }
    }

    @Test
    fun cancellationAfterGitAddPreservesActualWorktreeAndDoesNotInventReceipt() = runBlocking {
        withFixture { fixture ->
            val delegate = DesktopGitCommandRunner()
            val backend = DesktopGitBackend(fixture.binding.hostId, GitCommandRunner { cwd, argv ->
                val result = delegate.run(cwd, argv)
                if (argv.take(2) == listOf("worktree", "add")) throw CancellationException("after actual add")
                result
            })
            val repository = backend.discover(fixture.binding, fixture.root.toString())
            val target = fixture.directory.resolve("cancelled-after-add")
            assertFailsWith<CancellationException> {
                backend.createWorktree(repository, GitCreateWorktreeRequest(fixture.binding, target.toString(), "codex/cancelled"))
            }
            val actual = DesktopGitBackend(fixture.binding.hostId).worktrees(repository)
            assertEquals("refs/heads/codex/cancelled", actual.single { it.path == target.toString() }.branch)
        }
    }

    private suspend fun withFixture(block: suspend (ProcessWorktreeFixture) -> Unit) {
        val fixture = ProcessWorktreeFixture()
        try { block(fixture) } finally { fixture.directory.toFile().deleteRecursively() }
    }
}

private class ProcessWorktreeFixture {
    val directory: Path = Files.createTempDirectory("harness-create-worktree-").toRealPath()
    val root: Path = Files.createDirectory(directory.resolve("source"))
    val binding = GitBinding(null, HostId("worktree-process-local"), 1, GitAccess.LocalReadOnly)
    init {
        git("init", "--initial-branch=main")
        Files.writeString(root.resolve("tracked.txt"), "committed\n")
        git("add", "--", "tracked.txt")
        commit("fixture")
    }
    fun commit(message: String) {
        git("-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
            "-c", "commit.gpgsign=false", "commit", "-m", message)
    }
    fun dirtySource() {
        Files.writeString(root.resolve("tracked.txt"), "staged\n")
        git("add", "--", "tracked.txt")
        Files.writeString(root.resolve("tracked.txt"), "working\n")
        Files.writeString(root.resolve("untracked.txt"), "keep\n")
    }
    fun git(vararg arguments: String): String = gitAt(root, *arguments)
    fun gitAt(cwd: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "-c", "core.hooksPath=/dev/null") + arguments)
            .directory(cwd.toFile()).redirectErrorStream(true).apply {
                listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE",
                    "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES").forEach { environment().remove(it) }
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_TERMINAL_PROMPT"] = "0"
            }.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), "git ${arguments.toList()}: $output")
        return output
    }
}

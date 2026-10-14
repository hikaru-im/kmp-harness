package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Exercises the actual process runner and Git formats in an isolated repository. */
class DesktopGitProcessIntegrationTest {
    @Test
    fun realRepositoryReadsIndexWorkingTreeBranchesAndWorktreesWithoutChangingFiles() = runBlocking {
        val directory = Files.createTempDirectory("harness-git-process-")
        try {
            git(directory, "init", "--initial-branch=main")
            val tracked = directory.resolve("file with spaces.txt")
            Files.writeString(tracked, "original\n")
            git(directory, "add", "--", tracked.fileName.toString())
            git(directory, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
                "-c", "commit.gpgsign=false", "commit", "-m", "fixture")
            Files.writeString(tracked, "staged\n")
            git(directory, "add", "--", tracked.fileName.toString())
            Files.writeString(tracked, "working\n")
            Files.writeString(directory.resolve("未跟踪.txt"), "untracked\n")
            val indexBefore = Files.readAllBytes(directory.resolve(".git/index"))
            val headBefore = git(directory, "rev-parse", "HEAD").trim()
            val binding = GitBinding(null, HostId("integration-local"), 1, GitAccess.LocalReadOnly)
            val backend = DesktopGitBackend(binding.hostId)
            val repository = backend.discover(binding, directory.toString())
            val status = backend.status(repository)
            assertEquals(directory.toRealPath().toString(), repository.root)
            assertEquals(headBefore, status.headOid)
            assertEquals("refs/heads/main", status.branch)
            val change = status.changes.single { it.path == tracked.fileName.toString() }
            assertTrue(change.staged && change.unstaged)
            assertEquals(1, status.untrackedCount)
            assertEquals("未跟踪.txt", status.changes.single { it.untracked }.path)
            assertEquals("refs/heads/main", backend.branches(repository).single { it.current }.ref)
            assertEquals(repository.root, backend.worktrees(repository).single().path)
            val working = backend.diff(repository, GitDiffRequest()).patch
            assertTrue("-staged" in working && "+working" in working)
            val index = backend.diff(repository, GitDiffRequest(GitDiffSource.Index)).patch
            assertTrue("-original" in index && "+staged" in index)
            val base = backend.diff(repository, GitDiffRequest(GitDiffSource.BaseBranch, "refs/heads/main")).patch
            assertTrue("-original" in base && "+working" in base)
            // The summary Environment row reads the same HEAD base; the untracked file is excluded, so
            // the real counts are one tracked file with one addition and one deletion.
            val head = backend.diff(repository, GitDiffRequest(GitDiffSource.BaseBranch, "HEAD")).patch
            assertTrue("-original" in head && "+working" in head && "未跟踪" !in head)
            assertEquals(GitDiffStats(fileCount = 1, additions = 1, deletions = 1), gitDiffStats(head))
            assertContentEquals(indexBefore, Files.readAllBytes(directory.resolve(".git/index")))
            assertEquals(headBefore, git(directory, "rev-parse", "HEAD").trim())
            assertEquals("working\n", Files.readString(tracked))
            assertEquals("untracked\n", Files.readString(directory.resolve("未跟踪.txt")))
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun git(directory: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile())
            .redirectErrorStream(true).apply {
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

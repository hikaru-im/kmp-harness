package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/**
 * Real index evidence for GIT-10 stage/unstage. Every repository is created by this test inside a
 * temporary directory, and every assertion is read back through Git itself — never from the receipt.
 */
class DesktopGitStageTest {
    @Test
    fun stageAndUnstageWholeFilesCoverModifiedUntrackedAndDeleted() = runBlocking {
        val f = StageFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), "modified\n")
            Files.writeString(f.workspace.resolve("untracked.txt"), "new file\n")
            Files.delete(f.workspace.resolve("doomed.txt"))
            val repo = f.repository()
            assertEquals(listOf("doomed.txt", "tracked.txt", "untracked.txt"), f.status(repo).changes.map { it.path })

            for (path in listOf("tracked.txt", "untracked.txt", "doomed.txt")) {
                val applied = f.stage(repo, GitStageAction.Stage, GitStageTarget.File(path))
                assertEquals(GitStageAction.Stage, applied.receipt.request.action)
                val entry = applied.receipt.status.changes.single { it.path == path }
                assertTrue(entry.staged, "$path should be staged")
                // The receipt is a reread of the real index, not the patch we sent.
                assertEquals(f.repositoryIndexCode(path), entry.indexStatus, path)
            }
            assertEquals("modified\n", f.git(f.workspace, "show", ":tracked.txt"))
            assertEquals("new file\n", f.git(f.workspace, "show", ":untracked.txt"))
            assertEquals("", f.git(f.workspace, "ls-files", "--", "doomed.txt"))
            // The worktree keeps the user's bytes: staging never rewrites files.
            assertEquals("modified\n", Files.readString(f.workspace.resolve("tracked.txt")))
            assertEquals("new file\n", Files.readString(f.workspace.resolve("untracked.txt")))

            // Unstage in reverse order, each with a fresh, real status read.
            for (path in listOf("doomed.txt", "untracked.txt", "tracked.txt")) {
                val applied = f.stage(repo, GitStageAction.Unstage, GitStageTarget.File(path))
                val entry = applied.receipt.status.changes.single { it.path == path }
                assertTrue(entry.unstaged || entry.untracked, "$path should leave the index: $entry")
            }
            assertEquals("", f.git(f.workspace, "diff", "--cached"))
            assertEquals("modified\n", Files.readString(f.workspace.resolve("tracked.txt")))
            assertEquals("new file\n", Files.readString(f.workspace.resolve("untracked.txt")))
        } finally { f.close() }
    }

    @Test
    fun hunkStageWritesOnlyThatHunkIntoTheIndexAndHunkUnstageRestoresIt() = runBlocking {
        val f = StageFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), HUNK_TEXT)
            val repo = f.repository()
            val file = f.singleDiffFile(repo)
            assertEquals(2, parseGitDiffHunks(file.body).size, "fixture must produce two separable hunks")

            val first = assertNotNull(gitHunkPatch(file, 0))
            val applied = f.stage(repo, GitStageAction.Stage, GitStageTarget.Hunk("tracked.txt", first))
            val staged = f.git(f.workspace, "diff", "--cached", "--", "tracked.txt")
            assertTrue(staged.contains("+TWO"), staged)
            assertFalse(staged.contains("+TEN"), "the staged patch must include exactly one hunk, got: $staged")
            val unstaged = f.git(f.workspace, "diff", "--", "tracked.txt")
            assertTrue(unstaged.contains("+TEN"), unstaged)
            assertFalse(unstaged.contains("+TWO"), unstaged)
            val entry = applied.receipt.status.changes.single()
            assertEquals("tracked.txt", entry.path)
            assertTrue(entry.staged && entry.unstaged, "a partial stage leaves both sides: $entry")

            // Reversing the same hunk takes it back out of the index and touches nothing else.
            val restored = f.stage(repo, GitStageAction.Unstage, GitStageTarget.Hunk("tracked.txt", first))
            assertEquals("", f.git(f.workspace, "diff", "--cached"))
            val worktree = f.git(f.workspace, "diff", "--", "tracked.txt")
            assertTrue(worktree.contains("+TWO") && worktree.contains("+TEN"), worktree)
            assertTrue(restored.receipt.status.changes.single().unstaged)
            assertEquals(HUNK_TEXT, Files.readString(f.workspace.resolve("tracked.txt")))
        } finally { f.close() }
    }

    @Test
    fun unstagingAStagedRenameClearsBothSides() = runBlocking {
        val f = StageFixture()
        try {
            Files.move(f.workspace.resolve("tracked.txt"), f.workspace.resolve("renamed.txt"))
            val repo = f.repository()
            val before = f.status(repo)
            // A worktree rename is two independent entries: the source deletion and the new path.
            assertEquals(listOf("renamed.txt", "tracked.txt"), before.changes.map { it.path }.sorted())
            for (path in listOf("tracked.txt", "renamed.txt")) {
                f.stage(repo, GitStageAction.Stage, GitStageTarget.File(path))
            }
            val renamed = f.status(repo).changes.single()
            assertEquals("renamed.txt", renamed.path)
            assertEquals("tracked.txt", renamed.originalPath)
            assertTrue(renamed.staged)

            // The backend names the source itself from its own reread, so one request clears both.
            val applied = f.stage(repo, GitStageAction.Unstage, GitStageTarget.File("renamed.txt"))
            assertEquals("renamed.txt", assertNotNull(applied.receipt.request.target as GitStageTarget.File).path)
            assertEquals("", f.git(f.workspace, "diff", "--cached"))
            val after = f.status(repo)
            assertTrue(after.changes.none { it.staged }, "no index side may remain: ${after.changes}")
            assertEquals(BASE_TEXT, Files.readString(f.workspace.resolve("renamed.txt")))
        } finally { f.close() }
    }

    @Test
    fun unbornBranchUnstagesIntoAnUntrackedFileWithoutLosingBytes() = runBlocking {
        val f = StageFixture()
        try {
            val unborn = Files.createDirectory(f.directory.resolve("unborn"))
            f.git(unborn, "init", "--initial-branch=main")
            Files.writeString(unborn.resolve("first.txt"), "one\n")
            f.git(unborn, "add", "--", "first.txt")
            Files.writeString(unborn.resolve("first.txt"), "two\n")
            val repo = f.backend.discover(f.binding, unborn.toString())
            val status = f.status(repo)
            assertNull(status.headOid)
            assertEquals("refs/heads/main", status.branch)
            assertTrue(status.changes.single().staged)

            val applied = f.stage(repo, GitStageAction.Unstage, GitStageTarget.File("first.txt"))
            assertTrue(applied.receipt.status.unborn)
            assertTrue(applied.receipt.status.changes.single().untracked)
            assertEquals("", f.git(unborn, "diff", "--cached"))
            assertEquals("two\n", Files.readString(unborn.resolve("first.txt")))

            // Staging it again on the unborn branch is a plain index add.
            val stagedAgain = f.stage(repo, GitStageAction.Stage, GitStageTarget.File("first.txt"))
            assertTrue(stagedAgain.receipt.status.changes.single().staged)
            assertEquals("two\n", f.git(unborn, "show", ":first.txt"))
        } finally { f.close() }
    }

    @Test
    fun aFileNameWithGlobCharactersStagesOnlyThatFile() = runBlocking {
        val f = StageFixture()
        try {
            // Git would treat this argument as a pathspec glob and stage every matching file.
            Files.writeString(f.workspace.resolve("*.txt"), "literal star name\n")
            Files.writeString(f.workspace.resolve("tracked.txt"), "unrelated change\n")
            val repo = f.repository()
            val applied = f.stage(repo, GitStageAction.Stage, GitStageTarget.File("*.txt"))
            assertEquals(listOf("*.txt"),
                applied.receipt.status.changes.filter { it.staged }.map { it.path })
            assertTrue(applied.receipt.status.changes.single { it.path == "tracked.txt" }.unstaged,
                "the unrelated file must stay out of the index")
            assertEquals("", f.git(f.workspace, "diff", "--cached", "--", "tracked.txt"))

            f.stage(repo, GitStageAction.Unstage, GitStageTarget.File("*.txt"))
            assertTrue(f.status(repo).changes.none { it.staged })
            assertEquals("unrelated change\n", Files.readString(f.workspace.resolve("tracked.txt")))
        } finally { f.close() }
    }

    @Test
    fun hunkStageWorksForAPathGitQuotesAsOctal() = runBlocking {
        val f = StageFixture()
        try {
            val name = "中文 文件.txt"
            Files.writeString(f.workspace.resolve(name), BASE_TEXT)
            f.git(f.workspace, "add", "--", name)
            f.git(f.workspace, "commit", "-m", "add unicode file")
            Files.writeString(f.workspace.resolve(name), HUNK_TEXT)
            val repo = f.repository()
            val patch = f.worktreePatch(repo)
            // Git's default `core.quotePath` writes a non-ASCII path as a quoted octal string.
            assertTrue(patch.contains("\"a/"), patch)
            val file = assertNotNull(parseGitDiff(patch).files.singleOrNull())
            assertEquals(name, file.path)
            val first = assertNotNull(gitHunkPatch(file, 0))
            val applied = f.stage(repo, GitStageAction.Stage, GitStageTarget.Hunk(name, first))
            assertEquals(name, applied.receipt.status.changes.single().path)
            assertTrue(f.git(f.workspace, "diff", "--cached", "--", name).contains("+TWO"))
            assertEquals(HUNK_TEXT, Files.readString(f.workspace.resolve(name)))
        } finally { f.close() }
    }

    @Test
    fun invalidPathOrPatchIsRejectedBeforeAnyIndexWrite() = runBlocking {
        val f = StageFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), HUNK_TEXT)
            val repo = f.repository()
            val file = f.singleDiffFile(repo)
            val before = f.indexBytes(repo)
            val mutations = f.mutatingCommands
            // Rewriting the header names another file; the body is otherwise a valid hunk.
            val foreign = gitHunkPatch(file, 0)!!.replace("a/tracked.txt", "a/other.txt")
                .replace("b/tracked.txt", "b/other.txt")
            val cases = listOf(
                "absolute" to GitStageTarget.File(f.workspace.resolve("tracked.txt").toString()),
                "traversal" to GitStageTarget.File("../outside.txt"),
                "option" to GitStageTarget.File("--force"),
                "empty" to GitStageTarget.File(""),
                "wrong-path" to GitStageTarget.Hunk("tracked.txt", foreign),
                // Two hunks at once violate the single-hunk contract.
                "two-hunks" to GitStageTarget.Hunk("tracked.txt", f.worktreePatch(repo)),
                // A body whose line count contradicts its header is malformed, not "close enough".
                "broken-body" to GitStageTarget.Hunk("tracked.txt",
                    "diff --git a/tracked.txt b/tracked.txt\n--- a/tracked.txt\n+++ b/tracked.txt\n" +
                        "@@ -1,5 +1,5 @@\n one\n-two\n+TWO\n"),
            )
            for ((name, target) in cases) {
                val result = f.backend.applyStage(repo, f.stageRequest(repo, GitStageAction.Stage, target))
                val rejected = assertIs<GitStageResult.Rejected>(result, name)
                assertEquals(GitFailureKind.InvalidRequest, rejected.failure.kind, name)
            }
            assertEquals(mutations, f.mutatingCommands, "validation must finish before any mutating Git command")
            assertContentEquals(before, f.indexBytes(repo))
            assertEquals(listOf("tracked.txt"), f.status(repo).changes.map { it.path })
        } finally { f.close() }
    }

    @Test
    fun hunkPatchWhosePreimageMissesTheIndexIsRefusedByTheDryRun() = runBlocking {
        val f = StageFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), HUNK_TEXT)
            val repo = f.repository()
            val file = f.singleDiffFile(repo)
            val first = assertNotNull(gitHunkPatch(file, 0))
            f.stage(repo, GitStageAction.Stage, GitStageTarget.Hunk("tracked.txt", first))
            val written = f.indexBytes(repo)
            // Well-formed patch, but one context line was tampered with, so Git's dry run cannot
            // find the preimage. The index must be left exactly as it was.
            val tampered = assertNotNull(gitHunkPatch(file, 1)).replace(" seven\n", " seven (tampered)\n")
            val result = f.backend.applyStage(repo, f.stageRequest(repo, GitStageAction.Stage,
                GitStageTarget.Hunk("tracked.txt", tampered)))
            val rejected = assertIs<GitStageResult.Rejected>(result)
            assertEquals(GitFailureKind.CommandFailed, rejected.failure.kind)
            assertContentEquals(written, f.indexBytes(repo), "a failed dry run must not change the index")
            val staged = f.git(f.workspace, "diff", "--cached", "--", "tracked.txt")
            assertTrue(staged.contains("+TWO") && !staged.contains("+TEN"), staged)
        } finally { f.close() }
    }

    @Test
    fun staleHeadAndIneligibleEntriesAreRejectedWithoutWriting() = runBlocking {
        val f = StageFixture()
        try {
            val repo = f.repository()
            assertEquals(emptyList(), f.status(repo).changes)
            // Nothing is unstaged, so a stage request for a clean path must be refused.
            val untouched = assertIs<GitStageResult.Rejected>(
                f.backend.applyStage(repo, f.stageRequest(repo, GitStageAction.Stage, GitStageTarget.File("tracked.txt"))))
            assertEquals(GitFailureKind.InvalidRequest, untouched.failure.kind)

            Files.writeString(f.workspace.resolve("tracked.txt"), "modified\n")
            val staged = f.stage(repo, GitStageAction.Stage, GitStageTarget.File("tracked.txt"))
            val stale = f.stageRequest(repo, GitStageAction.Unstage,
                GitStageTarget.File("tracked.txt"), staged.receipt.status).copy(expectedHeadOid = "f".repeat(40))
            val result = assertIs<GitStageResult.Rejected>(f.backend.applyStage(repo, stale))
            assertEquals(GitFailureKind.StaleSnapshot, result.failure.kind)
            assertEquals("modified\n", f.git(f.workspace, "show", ":tracked.txt"),
                "the refused request must not change the index")
        } finally { f.close() }
    }

    @Test
    fun managedDecoratorForwardsStageAndStaysUnavailableWhenTheDelegateCannotStage() = runBlocking {
        val f = StageFixture()
        try {
            Files.writeString(f.workspace.resolve("tracked.txt"), "modified\n")
            val registration = f.directory.resolve("managed.json")
            val repository = DesktopManagedWorktreeRepository(f.backend, DesktopManagedWorktreeStore(registration))
            val decorated = DesktopManagedGitBackend(f.backend, repository)
            val repo = decorated.discover(f.binding, f.workspace.toString())
            assertTrue(decorated.canStage(f.binding))
            val applied = assertIs<GitStageResult.Applied>(withTimeout(15_000) {
                decorated.applyStage(repo, f.stageRequest(repo, GitStageAction.Stage, GitStageTarget.File("tracked.txt")))
            })
            assertTrue(applied.receipt.status.changes.single().staged)
            assertFalse(Files.exists(registration))

            val readOnly = DesktopManagedGitBackend(object : GitBackend by f.backend {}, repository)
            assertFalse(readOnly.canStage(f.binding))
            val rejected = readOnly.applyStage(repo,
                f.stageRequest(repo, GitStageAction.Unstage, GitStageTarget.File("tracked.txt"), applied.receipt.status))
            assertIs<GitStageResult.Rejected>(rejected)
            assertEquals(GitFailureKind.Unavailable, rejected.failure.kind)
        } finally { f.close() }
    }
}

/** Committed content of `tracked.txt`; the hunk tests change line 2 and line 10 of it. */
private const val BASE_TEXT: String = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n"

/**
 * Two well-separated changes over [BASE_TEXT]: line 2 and line 10, so `-U3` keeps them in two
 * separate hunks that can be staged independently.
 */
private const val HUNK_TEXT: String = "one\nTWO\nthree\nfour\nfive\nsix\nseven\neight\nnine\nTEN\n"

/** Self-created temporary repository: a linked worktree over one base commit. */
internal class StageFixture : AutoCloseable {
    val directory: Path = Files.createTempDirectory("harness-stage-").toRealPath()
    val workspace: Path = directory.resolve("workspace")
    val host = HostId("stage-local")
    val binding = GitBinding(null, host, 1, GitAccess.LocalReadOnly)
    private val mutations = AtomicInteger()
    val mutatingCommands: Int get() = mutations.get()
    val runner = GitCommandRunner { cwd, args ->
        val command = args.firstOrNull()
        val writes = command in setOf("add", "restore", "rm", "commit", "worktree") ||
            command == "apply" && "--check" !in args
        if (writes) mutations.incrementAndGet()
        withContext(Dispatchers.IO) { execute(Path.of(cwd), args) }
    }
    val backend = DesktopGitBackend(host, runner)
    val baseOid: String
    init {
        git(directory, "init", "--initial-branch=main")
        git(directory, "config", "user.name", "Harness Test")
        git(directory, "config", "user.email", "test@example.invalid")
        git(directory, "config", "core.hooksPath", "/dev/null")
        Files.writeString(directory.resolve("tracked.txt"), BASE_TEXT)
        Files.writeString(directory.resolve("doomed.txt"), "removed later\n")
        git(directory, "add", "--", "tracked.txt", "doomed.txt")
        git(directory, "commit", "-m", "fixture base")
        baseOid = git(directory, "rev-parse", "HEAD").trim()
        git(directory, "worktree", "add", "-b", "sandbox", "--", workspace.toString(), baseOid)
    }
    suspend fun repository(): GitRepository = backend.discover(binding, workspace.toString())
    suspend fun status(repo: GitRepository): GitStatusSnapshot = backend.status(repo)
    suspend fun worktreePatch(repo: GitRepository): String = backend.diff(repo, GitDiffRequest()).patch
    suspend fun singleDiffFile(repo: GitRepository): GitDiffFile =
        assertNotNull(parseGitDiff(worktreePatch(repo)).files.singleOrNull(), "fixture changes exactly one file")
    suspend fun stageRequest(
        repo: GitRepository,
        action: GitStageAction,
        target: GitStageTarget,
        status: GitStatusSnapshot? = null,
    ): GitStageRequest {
        val current = status ?: backend.status(repo)
        return GitStageRequest(repo.binding, repo.root, current.branch, current.headOid, action, target)
    }
    suspend fun stage(repo: GitRepository, action: GitStageAction, target: GitStageTarget): GitStageResult.Applied {
        val result = withTimeout(15_000) { backend.applyStage(repo, stageRequest(repo, action, target)) }
        return assertIs<GitStageResult.Applied>(result, "expected applied, got $result")
    }
    /** Index-side letter as Git reports it, read from a command the backend never sees. */
    fun repositoryIndexCode(path: String): Char? =
        git(workspace, "status", "--porcelain=v1", "--", path).lineSequence().firstOrNull()?.first()
    fun indexBytes(repo: GitRepository): ByteArray = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
    fun git(cwd: Path, vararg args: String): String {
        val result = execute(cwd, args.toList())
        assertEquals(0, result.exitCode, "git " + args.toList() + ": " + result.stderr)
        return result.stdout
    }
    private fun execute(cwd: Path, args: List<String>): GitCommandResult {
        val process = ProcessBuilder(listOf("git", "--no-optional-locks", "-c", "core.fsmonitor=false") + args)
            .directory(cwd.toFile()).apply {
                listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY",
                    "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_CONFIG_COUNT", "GIT_CONFIG_PARAMETERS",
                    "GIT_AUTHOR_NAME", "GIT_AUTHOR_EMAIL", "GIT_COMMITTER_NAME", "GIT_COMMITTER_EMAIL")
                    .forEach { environment().remove(it) }
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_TERMINAL_PROMPT"] = "0"
                environment()["GIT_OPTIONAL_LOCKS"] = "0"
            }.start()
        try {
            process.outputStream.close()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "bounded real fixture command")
            return GitCommandResult(process.exitValue(),
                process.inputStream.readAllBytes().decodeToString(throwOnInvalidSequence = true),
                process.errorStream.readAllBytes().decodeToString(throwOnInvalidSequence = true))
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
    override fun close() { directory.toFile().deleteRecursively() }
}

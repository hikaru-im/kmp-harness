package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DesktopGitWorktreeCreatorTest {
    @Test
    fun explicitRefIsResolvedToOidAndArgvPreservesUserCharactersBeforePostAddScan() = runTest {
        CreatorFixture().use { f ->
            val target = f.parent.resolve("new space ; literal")
            val request = f.request(target, startRef = "refs/heads/main")
            val receipt = assertIs<GitCreateWorktreeResult.Created>(f.backend.createWorktree(f.repo, request)).receipt
            assertEquals(request, receipt.request)
            assertEquals(target.toRealPath().toString(), receipt.worktree.path)
            assertEquals(CREATOR_OID, receipt.startOid)
            assertEquals("refs/heads/codex/new", receipt.worktree.branch)
            assertTrue(receipt.worktree in receipt.worktrees)
            val added = f.calls.single { it.second.take(2) == listOf("worktree", "add") }
            assertEquals(f.root.toString(), added.first)
            assertEquals(listOf("worktree", "add", "-b", "codex/new", "--", target.toString(), CREATOR_OID), added.second)
            assertTrue(f.calls.any { it.second == listOf("rev-parse", "--verify", "--end-of-options", "refs/heads/main^{commit}") })
            val addIndex = f.calls.indexOf(added)
            assertEquals(listOf("worktree", "list", "--porcelain", "-z"), f.calls[addIndex + 1].second)
            assertTrue(f.calls.all { it.first == f.root.toString() })
        }
    }

    @Test
    fun headIsExplicitlyResolvedAndNeverPassedAsAddStartArgument() = runTest {
        CreatorFixture().use { f ->
            val receipt = assertIs<GitCreateWorktreeResult.Created>(f.backend.createWorktree(f.repo, f.request())).receipt
            assertEquals("HEAD", receipt.request.startRef)
            assertTrue(f.calls.any { it.second == listOf("rev-parse", "--verify", "--end-of-options", "HEAD^{commit}") })
            assertEquals(CREATOR_OID, f.addArgs!!.last())
        }
    }

    @Test
    fun invalidBranchAndOptionLikeOrNonAbsolutePathAreRejectedBeforeRunner() = runTest {
        CreatorFixture().use { f ->
            for (branch in listOf("", "codex/", "--force", "a b", "a..b", "a@{b", "a.lock", "a/.hidden",
                    "refs/heads/other", "a\u0000b", "a\nb", "a~b", "a:b", "a?b", "a*b", "a[b", "a\\b")) {
                val failure = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request().copy(branch = branch))).failure
                assertEquals(GitFailureKind.InvalidRequest, failure.failure.kind, branch)
                assertFalse(failure.needsRescan)
            }
            for (path in listOf("", "relative/path", "--force", f.parent.resolve("-option").toString(),
                    f.parent.resolve("nested/../other").toString(), "/new\u0000bad", "/new\nline")) {
                val failure = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request().copy(path = path))).failure
                assertEquals(GitFailureKind.InvalidRequest, failure.failure.kind, path)
                assertFalse(failure.needsRescan)
            }
            assertTrue(f.calls.isEmpty())
            assertFalse(Files.exists(f.target))
        }
    }

    @Test
    fun existingDirectoriesFilesAndDanglingSymlinksAreNeverPopulated() = runTest {
        CreatorFixture().use { f ->
            val empty = Files.createDirectory(f.parent.resolve("empty"))
            val occupied = Files.createDirectory(f.parent.resolve("occupied"))
            val marker = Files.writeString(occupied.resolve("keep"), "preserve")
            val file = Files.writeString(f.parent.resolve("file"), "preserve")
            val symlink = Files.createSymbolicLink(f.parent.resolve("link"), f.parent.resolve("absent"))
            for (target in listOf(empty, occupied, file, symlink)) {
                val failure = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request(target))).failure
                assertEquals(GitFailureKind.InvalidRequest, failure.failure.kind)
                assertFalse(failure.needsRescan)
            }
            assertEquals("preserve", Files.readString(marker))
            assertEquals("preserve", Files.readString(file))
            assertTrue(Files.isSymbolicLink(symlink))
            Files.list(empty).use { assertEquals(0L, it.count()) }
            assertTrue(f.calls.isEmpty())
        }
    }

    @Test
    fun remoteForeignDisconnectedAndMismatchedGenerationCannotReachRunner() = runTest {
        CreatorFixture().use { f ->
            for (binding in listOf(CREATOR_LOCAL.copy(access = GitAccess.RemoteUnavailable),
                    CREATOR_LOCAL.copy(access = GitAccess.Disconnected), CREATOR_LOCAL.copy(hostId = HostId("foreign")))) {
                assertFalse(f.backend.canCreateWorktree(binding))
                val result = f.backend.createWorktree(f.repo.copy(binding = binding), f.request().copy(binding = binding))
                assertEquals(GitFailureKind.Unavailable, assertIs<GitCreateWorktreeResult.Failed>(result).failure.failure.kind)
            }
            val mismatched = f.backend.createWorktree(f.repo, f.request().copy(binding = CREATOR_LOCAL.copy(generation = 2)))
            assertEquals(GitFailureKind.InvalidRequest, assertIs<GitCreateWorktreeResult.Failed>(mismatched).failure.failure.kind)
            assertTrue(f.calls.isEmpty())
        }
    }

    @Test
    fun repositoryMetadataMismatchAndProtectedDestinationCannotDispatchAdd() = runTest {
        CreatorFixture().use { f ->
            val foreign = Files.createDirectory(f.parent.resolve("foreign-metadata"))
            val result = f.backend.createWorktree(f.repo.copy(commonDir = foreign.toString()), f.request())
            assertEquals(GitFailureKind.InvalidRequest, assertIs<GitCreateWorktreeResult.Failed>(result).failure.failure.kind)
            val protected = f.backend.createWorktree(f.repo, f.request(f.root.resolve(".git/new")))
            assertEquals(GitFailureKind.InvalidRequest, assertIs<GitCreateWorktreeResult.Failed>(protected).failure.failure.kind)
            val insideRepository = f.root.resolve("nested-worktree")
            val nested = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request(insideRepository))).failure
            assertEquals(GitFailureKind.InvalidRequest, nested.failure.kind)
            assertFalse(nested.needsRescan)
            assertFalse(Files.exists(insideRepository))
            assertNull(f.addArgs)
            assertFalse(Files.exists(f.target))
        }
    }

    @Test
    fun invalidStartMissingStartDuplicateBranchAndMalformedOidCannotDispatchAdd() = runTest {
        for (start in listOf("--detach", "main", "refs/heads/missing", "HEAD~1", "a\u0000b")) {
            CreatorFixture().use { f ->
                val result = f.backend.createWorktree(f.repo, f.request(startRef = start))
                assertEquals(GitFailureKind.InvalidRequest, assertIs<GitCreateWorktreeResult.Failed>(result).failure.failure.kind)
                assertNull(f.addArgs)
                assertFalse(Files.exists(f.target))
            }
        }
        CreatorFixture().use { f ->
            val duplicate = f.backend.createWorktree(f.repo, f.request().copy(branch = "main"))
            assertEquals(GitFailureKind.InvalidRequest, assertIs<GitCreateWorktreeResult.Failed>(duplicate).failure.failure.kind)
            assertNull(f.addArgs)
        }
        CreatorFixture().use { f ->
            f.startOid = "not-an-oid"
            val malformed = f.backend.createWorktree(f.repo, f.request())
            assertEquals(GitFailureKind.MalformedOutput, assertIs<GitCreateWorktreeResult.Failed>(malformed).failure.failure.kind)
            assertNull(f.addArgs)
        }
    }

    @Test
    fun concurrentlyCreatedEmptyDirectoryIsRejectedByAtomicReservation() = runTest {
        CreatorFixture().use { f ->
            f.beforeSecondVerification = { Files.createDirectory(f.target) }
            val result = f.backend.createWorktree(f.repo, f.request())
            assertEquals(GitFailureKind.InvalidRequest, assertIs<GitCreateWorktreeResult.Failed>(result).failure.failure.kind)
            assertNull(f.addArgs)
            Files.list(f.target).use { assertEquals(0L, it.count()) }
        }
    }

    @Test
    fun gitBranchRejectionAndStartResolutionTimeoutBeforeAddDoNotRequireRescan() = runTest {
        CreatorFixture().use { f ->
            f.refCheckResult = { GitCommandResult(1, "", "invalid branch") }
            val failure = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request())).failure
            assertEquals(GitFailureKind.InvalidRequest, failure.failure.kind)
            assertFalse(failure.needsRescan)
            assertNull(f.addArgs)
            assertFalse(Files.exists(f.target))
        }
        CreatorFixture().use { f ->
            f.resolveStart = { throw GitReadException(GitFailure(GitFailureKind.Timeout, "start deadline")) }
            val failure = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request())).failure
            assertEquals(GitFailureKind.Timeout, failure.failure.kind)
            assertFalse(failure.needsRescan)
            assertNull(f.addArgs)
            assertFalse(Files.exists(f.target))
        }
    }

    @Test
    fun nonzeroAddTimeoutAndRescanFailureNeverIssueSuccessReceipt() = runTest {
        for (mode in listOf("add-fail", "add-timeout", "scan-fail", "scan-timeout", "scan-missing", "scan-branch", "scan-head")) {
            CreatorFixture().use { f ->
                when (mode) {
                    "add-fail" -> f.addResult = { GitCommandResult(128, "", "fixture rejected") }
                    "add-timeout" -> f.addResult = { throw GitReadException(GitFailure(GitFailureKind.Timeout, "deadline")) }
                    "scan-fail" -> f.afterScan = { GitCommandResult(1, "", "scan denied") }
                    "scan-timeout" -> f.afterScan = { throw GitReadException(GitFailure(GitFailureKind.Timeout, "scan deadline")) }
                    "scan-missing" -> f.afterScan = { f.ok(f.mainEntry()) }
                    "scan-branch" -> f.afterScan = { f.ok(f.newEntry(branch = "refs/heads/other")) }
                    "scan-head" -> f.afterScan = { f.ok(f.newEntry(oid = "b".repeat(40))) }
                }
                val failed = assertIs<GitCreateWorktreeResult.Failed>(f.backend.createWorktree(f.repo, f.request()), mode).failure
                assertTrue(failed.needsRescan, mode)
                assertTrue(failed.failure.message.contains("结果未知"))
                assertNotNull(f.addArgs)
            }
        }
    }

    @Test
    fun cancellationDuringAddPropagatesWithoutInventingReceiptOrRunningPostScan() = runTest {
        CreatorFixture().use { f ->
            f.addResult = { throw CancellationException("caller cancelled") }
            assertFailsWith<CancellationException> { f.backend.createWorktree(f.repo, f.request()) }
            assertNotNull(f.addArgs)
            assertEquals(1, f.calls.count { it.second == listOf("worktree", "list", "--porcelain", "-z") })
        }
    }

    @Test
    fun cancellationDuringConfirmationScanCannotReturnCreatedReceipt() = runTest {
        CreatorFixture().use { f ->
            f.afterScan = { throw CancellationException("confirmation cancelled") }
            assertFailsWith<CancellationException> { f.backend.createWorktree(f.repo, f.request()) }
            assertNotNull(f.addArgs)
            assertEquals(2, f.calls.count { it.second == listOf("worktree", "list", "--porcelain", "-z") })
        }
    }
}

private val CREATOR_LOCAL = GitBinding(null, HostId("creator-local"), 1, GitAccess.LocalReadOnly)
private val CREATOR_OID = "a".repeat(40)

/** Tests the creator's real filesystem validation with a recorded argv boundary, not a shell. */
private class CreatorFixture : AutoCloseable {
    val parent: Path = Files.createTempDirectory("harness-creator-contract-").toRealPath()
    val root: Path = Files.createDirectory(parent.resolve("source"))
    private val metadata = Files.createDirectory(root.resolve(".git"))
    val target: Path = parent.resolve("new worktree")
    val repo = GitRepository(CREATOR_LOCAL, root.toString(), metadata.toString(), metadata.toString())
    val calls = mutableListOf<Pair<String, List<String>>>()
    var addArgs: List<String>? = null
    var startOid = CREATOR_OID
    var refCheckResult: () -> GitCommandResult = { ok("") }
    var resolveStart: () -> GitCommandResult = { ok("$startOid\n") }
    var beforeSecondVerification: (() -> Unit)? = null
    var addResult: () -> GitCommandResult = { ok("") }
    var afterScan: () -> GitCommandResult = { ok(mainEntry() + newEntry()) }
    private var verifications = 0
    val backend = DesktopGitBackend(CREATOR_LOCAL.hostId, GitCommandRunner { cwd, argv ->
        calls.add(cwd to argv.toList())
        when {
            argv == listOf("rev-parse", "--is-inside-work-tree") -> {
                if (++verifications == 2) beforeSecondVerification?.invoke()
                ok("true\n")
            }
            argv.last() == "--show-toplevel" -> ok("$root\n")
            argv.last() == "--git-common-dir" || argv.last() == "--git-dir" -> ok("$metadata\n")
            argv.first() == "check-ref-format" -> refCheckResult()
            argv.first() == "for-each-ref" -> ok("refs/heads/main\u0000$CREATOR_OID\u0000*\u0000\n")
            argv.take(2) == listOf("rev-parse", "--verify") -> resolveStart()
            argv.take(2) == listOf("worktree", "add") -> {
                addArgs = argv.toList()
                assertTrue(Files.isDirectory(Path.of(argv[5])), "new destination is reserved before dispatch")
                addResult()
            }
            argv == listOf("worktree", "list", "--porcelain", "-z") ->
                if (addArgs == null) ok(mainEntry()) else afterScan()
            else -> error("unexpected argv: $argv")
        }
    })
    fun request(path: Path = target, startRef: String = "HEAD") =
        GitCreateWorktreeRequest(CREATOR_LOCAL, path.toString(), "codex/new", startRef)
    fun ok(value: String) = GitCommandResult(0, value, "")
    fun mainEntry() = "worktree $root\u0000HEAD $CREATOR_OID\u0000branch refs/heads/main\u0000\u0000"
    fun newEntry(branch: String = "refs/heads/codex/new", oid: String = CREATOR_OID): String {
        val path = addArgs!![5]
        return "worktree $path\u0000HEAD $oid\u0000branch $branch\u0000\u0000"
    }
    override fun close() {
        Files.walk(parent).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}

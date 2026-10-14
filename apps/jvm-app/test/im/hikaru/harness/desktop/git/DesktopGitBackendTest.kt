package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DesktopGitBackendTest {
    @Test
    fun remoteDisconnectedAndForeignHostsCannotInvokeLocalRunner() = runTest {
        val runner = RecordingRunner()
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        for (binding in listOf(LOCAL.copy(access = GitAccess.RemoteUnavailable),
            LOCAL.copy(access = GitAccess.Disconnected), LOCAL.copy(hostId = HostId("remote")))) {
            val repo = repository(binding)
            assertEquals(GitFailureKind.Unavailable, assertFailsWith<GitReadException> { backend.discover(binding, "/unused") }.failure.kind)
            assertFailsWith<GitReadException> { backend.status(repo) }
            assertFailsWith<GitReadException> { backend.branches(repo) }
            assertFailsWith<GitReadException> { backend.worktrees(repo) }
            assertFailsWith<GitReadException> { backend.diff(repo, GitDiffRequest()) }
        }
        assertTrue(runner.calls.isEmpty(), "remote scope must not read the parent machine")
    }

    @Test
    fun discoverUsesCanonicalDirectoryAndPreservesBinding() = runTest {
        val directory = Files.createTempDirectory("git-discover-fixture")
        try {
            val root = directory.toRealPath().toString()
            val runner = RecordingRunner().apply { response = { _, args -> ok(when (args.last()) {
                "--is-inside-work-tree" -> "true\n"
                "--show-toplevel" -> "$root\n"
                else -> "$root/.git\n"
            }) } }
            val actual = DesktopGitBackend(LOCAL.hostId, runner).discover(LOCAL, directory.toString())
            assertEquals(LOCAL, actual.binding)
            assertEquals(root, actual.root)
            assertEquals("$root/.git", actual.commonDir)
            assertEquals(4, runner.calls.size)
            assertTrue(runner.calls.all { it.first == root })
        } finally { Files.deleteIfExists(directory) }
    }

    @Test
    fun statusBranchesAndWorktreesUseOnlyReadCommands() = runTest {
        val runner = RecordingRunner().apply { response = { _, args -> when (args.first()) {
            "symbolic-ref" -> ok("refs/heads/main\n")
            "rev-parse" -> ok("$OID\n")
            "status" -> ok(" M file with spaces\u0000?? 文件\u0000")
            "for-each-ref" -> ok("refs/heads/main\u0000$OID\u0000*\u0000\n")
            "worktree" -> ok("worktree /repo\u0000HEAD $OID\u0000branch refs/heads/main\u0000\u0000")
            else -> error("unexpected command: $args")
        } } }
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        val status = backend.status(repository())
        assertEquals(2, status.changes.size)
        assertEquals("refs/heads/main", status.branch)
        assertEquals(OID, status.headOid)
        assertTrue(backend.branches(repository()).single().current)
        assertEquals("/repo", backend.worktrees(repository()).single().path)
        assertTrue(runner.calls.any { it.second == listOf("status", "--porcelain=v1", "-z", "--untracked-files=all", "--renames") })
        assertTrue(runner.calls.any { it.second == listOf("worktree", "list", "--porcelain", "-z") })
        assertTrue(runner.calls.all { it.first == "/repo" })
    }

    @Test
    fun baseDiffResolvesRefThenUsesOidAndDisablesExternalDiff() = runTest {
        val runner = RecordingRunner().apply { response = { _, args -> when (args.first()) {
            "symbolic-ref" -> ok("refs/heads/main\n")
            "rev-parse" -> ok("$OID\n")
            "diff" -> ok("diff --git a/a b/a\n")
            else -> error("unexpected command")
        } } }
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        val request = GitDiffRequest(GitDiffSource.BaseBranch, "refs/heads/main")
        assertEquals(request, backend.diff(repository(), request).request)
        assertTrue(runner.calls.any { it.second == listOf("rev-parse", "--verify", "--end-of-options", "refs/heads/main^{commit}") })
        val diff = runner.calls.single { it.second.first() == "diff" }.second
        assertEquals(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short", OID, "--"), diff)
    }

    @Test
    fun invalidBaseIsRejectedBeforeAnyCommandAndIndexDiffIsExplicit() = runTest {
        val runner = RecordingRunner()
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        for (ref in listOf(null, "", "--output=/tmp/unwanted", "main\u0000invalid")) {
            assertEquals(GitFailureKind.InvalidRequest, assertFailsWith<GitReadException> {
                backend.diff(repository(), GitDiffRequest(GitDiffSource.BaseBranch, ref))
            }.failure.kind)
        }
        assertTrue(runner.calls.isEmpty())
        runner.response = { _, args -> when (args.first()) {
            "symbolic-ref" -> GitCommandResult(1, "", "")
            "rev-parse" -> ok("$OID\n")
            else -> ok("")
        } }
        val diff = backend.diff(repository(), GitDiffRequest(GitDiffSource.Index))
        assertEquals("", diff.patch)
        assertEquals(OID, diff.headOid)
        assertTrue(runner.calls.any { it.second.takeLast(2) == listOf("--cached", "--") })
    }

    @Test
    fun hideWhitespaceAddsIgnoreAllSpaceBeforeThePathspecAndStaysOffByDefault() = runTest {
        val runner = RecordingRunner().apply { response = { _, args -> when (args.first()) {
            "symbolic-ref" -> ok("refs/heads/main\n")
            "rev-parse" -> ok("$OID\n")
            "diff" -> ok("diff --git a/a b/a\n")
            else -> error("unexpected command")
        } } }
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        backend.diff(repository(), GitDiffRequest())
        assertEquals(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short", "--"),
            runner.calls.single { it.second.first() == "diff" }.second)
        // Codex sends `hideWhitespace` as a review-diff input; the host computes the patch with git's
        // whitespace-ignoring switch, so the flag lands before the `--` pathspec separator.
        backend.diff(repository(), GitDiffRequest(hideWhitespace = true))
        assertEquals(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short", "--ignore-all-space", "--"),
            runner.calls.last { it.second.first() == "diff" }.second)
    }

    @Test
    fun loadFullFilesWidensContextBeforeThePathspecAndStaysOffByDefault() = runTest {
        val runner = RecordingRunner().apply { response = { _, args -> when (args.first()) {
            "symbolic-ref" -> ok("refs/heads/main\n")
            "rev-parse" -> ok("$OID\n")
            "diff" -> ok("diff --git a/a b/a\n")
            else -> error("unexpected command")
        } } }
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        backend.diff(repository(), GitDiffRequest())
        assertEquals(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short", "--"),
            runner.calls.single { it.second.first() == "diff" }.second)
        // `codex.review.loadFullFiles.enable` shows the whole file for a partial diff: the host widens
        // git's context so the patch comes back with the full file, still before the `--` separator.
        backend.diff(repository(), GitDiffRequest(loadFullFiles = true))
        val args = runner.calls.last { it.second.first() == "diff" }.second
        assertEquals(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short",
            "--unified=100000", "--"), args)
        // Both toggles are independent diff inputs, so they compose in a stable order.
        backend.diff(repository(), GitDiffRequest(hideWhitespace = true, loadFullFiles = true))
        assertEquals(listOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short",
            "--ignore-all-space", "--unified=100000", "--"), runner.calls.last { it.second.first() == "diff" }.second)
    }

    @Test
    fun headChangeAndCommandFailureNeverReturnSuccessfulCleanSnapshot() = runTest {
        var headReads = 0
        val runner = RecordingRunner().apply { response = { _, args -> when (args.first()) {
            "symbolic-ref" -> ok("refs/heads/main\n")
            "rev-parse" -> ok((if (++headReads == 1) OID else "b".repeat(40)) + "\n")
            else -> ok("")
        } } }
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        assertEquals(GitFailureKind.StaleSnapshot, assertFailsWith<GitReadException> { backend.status(repository()) }.failure.kind)
        headReads = 0
        assertEquals(GitFailureKind.StaleSnapshot, assertFailsWith<GitReadException> { backend.diff(repository(), GitDiffRequest()) }.failure.kind)
        runner.response = { _, _ -> GitCommandResult(128, "", "permission denied") }
        assertEquals(GitFailureKind.CommandFailed, assertFailsWith<GitReadException> { backend.worktrees(repository()) }.failure.kind)
    }

    @Test
    fun readPreviewFileReadsOnlySafeInRootFilesAndFallsBackWithAReason() = runTest {
        val root = Files.createTempDirectory("git-preview-fixture")
        val outside = Files.createTempDirectory("git-preview-outside")
        val runner = RecordingRunner()
        val backend = DesktopGitBackend(LOCAL.hostId, runner)
        val repo = GitRepository(LOCAL, root.toRealPath().toString(), "$root/.git", "$root/.git")
        try {
            Files.writeString(root.resolve("readme.md"), "# 标题\n\n正文")
            assertEquals("# 标题\n\n正文", assertIs<GitPreviewFile.Ready>(backend.readPreviewFile(repo, "readme.md")).text)
            Files.createDirectories(root.resolve("docs"))
            Files.writeString(root.resolve("docs/nested.md"), "nested")
            assertEquals("nested", assertIs<GitPreviewFile.Ready>(backend.readPreviewFile(repo, "docs/nested.md")).text)

            assertIs<GitPreviewFile.Unavailable>(backend.readPreviewFile(repo, "missing.md"))
            Files.createDirectory(root.resolve("dir.md"))
            assertIs<GitPreviewFile.Unavailable>(backend.readPreviewFile(repo, "dir.md"))
            Files.write(root.resolve("bad.md"), byteArrayOf(0x23, 0xFF.toByte(), 0xFE.toByte()))
            assertIs<GitPreviewFile.Unavailable>(backend.readPreviewFile(repo, "bad.md"))
            Files.write(root.resolve("big.md"), ByteArray(GitPreviewTextLimit + 1) { 'a'.code.toByte() })
            assertIs<GitPreviewFile.Unavailable>(backend.readPreviewFile(repo, "big.md"))

            // A symlink is not followed, so an in-repository name cannot read outside the root.
            Files.writeString(outside.resolve("secret.md"), "secret")
            runCatching { Files.createSymbolicLink(root.resolve("link.md"), outside.resolve("secret.md")) }
                .onSuccess { assertIs<GitPreviewFile.Unavailable>(backend.readPreviewFile(repo, "link.md")) }

            for (path in listOf("", "   ", "/etc/passwd", "../secret.md", "docs/../../secret.md",
                "C:/x.md", "a\\b.md", "docs/./x.md", "a\u0000b")) {
                assertIs<GitPreviewFile.Unavailable>(backend.readPreviewFile(repo, path), path)
            }
            assertTrue(runner.calls.isEmpty(), "a preview read must not run a git command")
        } finally {
            deleteTree(root)
            deleteTree(outside)
        }
    }

    @Test
    fun readPreviewFileRejectsForeignAndRemoteBindings() = runTest {
        val root = Files.createTempDirectory("git-preview-binding")
        val backend = DesktopGitBackend(LOCAL.hostId, RecordingRunner())
        try {
            Files.writeString(root.resolve("readme.md"), "x")
            for (binding in listOf(LOCAL.copy(access = GitAccess.RemoteUnavailable),
                LOCAL.copy(access = GitAccess.Disconnected), LOCAL.copy(hostId = HostId("remote")))) {
                val repo = GitRepository(binding, root.toRealPath().toString(), "$root/.git", "$root/.git")
                assertEquals(GitFailureKind.Unavailable,
                    assertFailsWith<GitReadException> { backend.readPreviewFile(repo, "readme.md") }.failure.kind)
            }
        } finally { deleteTree(root) }
    }
}

private val LOCAL = GitBinding(null, HostId("local"), 1, GitAccess.LocalReadOnly)
private val OID = "a".repeat(40)
private fun repository(binding: GitBinding = LOCAL) = GitRepository(binding, "/repo", "/repo/.git", "/repo/.git")
private fun ok(stdout: String) = GitCommandResult(0, stdout, "")

private fun deleteTree(root: java.nio.file.Path) {
    if (!Files.exists(root)) return
    val entries = mutableListOf<java.nio.file.Path>()
    Files.walk(root).use { stream -> stream.forEach { entries.add(it) } }
    entries.asReversed().forEach { Files.deleteIfExists(it) }
}

private class RecordingRunner : GitCommandRunner {
    val calls = mutableListOf<Pair<String, List<String>>>()
    var response: suspend (String, List<String>) -> GitCommandResult = { _, _ -> error("unexpected runner call") }
    override suspend fun run(cwd: String, arguments: List<String>): GitCommandResult {
        calls.add(cwd to arguments.toList())
        return response(cwd, arguments)
    }
}

package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitControllerTest {
    @Test
    fun lateDiscoveryCannotReplaceSelectedWorktree() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = FakeGit().apply { discoverResult = { binding, cwd ->
            if (cwd == "/old") withContext(NonCancellable) { gate.await() }
            repository(binding, cwd)
        } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/old")
        runCurrent()
        controller.selectWorktree("/new")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("/new", controller.state.value.selectedCwd)
        assertEquals("/new", ready(controller.state.value.repository).root)
        assertEquals(listOf("/new"), backend.statusRoots)
        controller.close()
    }

    @Test
    fun latestRefreshFailureWinsOverLateSuccessAndOtherReadsRemainIndependent() = runTest {
        val old = CompletableDeferred<GitStatusSnapshot>()
        var calls = 0
        val backend = FakeGit().apply { statusResult = {
            if (++calls == 1) withContext(NonCancellable) { old.await() }
            else throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "status failed"))
        } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        controller.refresh()
        runCurrent()
        assertEquals(GitFailureKind.CommandFailed, assertIs<GitRead.Failed>(controller.state.value.status).failure.kind)
        assertEquals("patch:/repo", ready(controller.state.value.diff).patch)
        assertEquals(1, ready(controller.state.value.worktrees).size)
        old.complete(GitStatusSnapshot(emptyList(), "old", null))
        runCurrent()
        assertIs<GitRead.Failed>(controller.state.value.status)
        controller.close()
    }

    @Test
    fun changingDiffFencesLateDiffWithoutCancellingStatus() = runTest {
        val oldDiff = CompletableDeferred<GitDiffSnapshot>()
        val status = CompletableDeferred<GitStatusSnapshot>()
        val backend = FakeGit().apply {
            statusResult = { status.await() }
            diffResult = { repo, request ->
                if (request.source == GitDiffSource.WorkingTree) withContext(NonCancellable) { oldDiff.await() }
                else GitDiffSnapshot(request, "index patch", null)
            }
        }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        controller.selectDiff(GitDiffRequest(GitDiffSource.Index))
        runCurrent()
        assertEquals("index patch", ready(controller.state.value.diff).patch)
        assertIs<GitRead.Loading>(controller.state.value.status)
        status.complete(GitStatusSnapshot(emptyList(), "current", null))
        oldDiff.complete(GitDiffSnapshot(GitDiffRequest(), "obsolete", null))
        runCurrent()
        assertEquals("current", ready(controller.state.value.status).branch)
        assertEquals("index patch", ready(controller.state.value.diff).patch)
        controller.close()
    }

    @Test
    fun identitySwitchAndLogoutFenceNonCooperativeRead() = runTest {
        val old = CompletableDeferred<GitStatusSnapshot>()
        val first = LOCAL.copy(identity = MemberIdentity(BackendTenant("https://member.example", 1), 1))
        val backend = FakeGit().apply { statusResult = { withContext(NonCancellable) { old.await() } } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(first)
        controller.selectRepository("/private")
        runCurrent()
        controller.bind(first.copy(identity = first.identity!!.copy(userId = 2)))
        assertNull(controller.state.value.selectedCwd)
        assertIs<GitRead.Unselected>(controller.state.value.status)
        controller.bind(null)
        old.complete(GitStatusSnapshot(listOf(GitChange("secret", indexStatus = 'M', worktreeStatus = ' ')), "private", null))
        runCurrent()
        assertEquals(GitState(), controller.state.value)
        controller.close()
    }

    @Test
    fun sameHostDisconnectAndNewGenerationPreserveDirectoryButClearAndReloadData() = runTest {
        val backend = FakeGit()
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status)
        controller.bind(LOCAL.copy(access = GitAccess.Disconnected))
        assertEquals("/repo", controller.state.value.selectedCwd)
        assertFalse(controller.state.value.canRefresh)
        assertIs<GitRead.Unselected>(controller.state.value.status)
        controller.refresh()
        runCurrent()
        assertEquals(1, backend.discoverCalls)
        controller.bind(LOCAL.copy(generation = 2))
        runCurrent()
        assertEquals(2, backend.discoverCalls)
        assertEquals(2L, ready(controller.state.value.repository).binding.generation)
        controller.bind(LOCAL.copy(hostId = HostId("other"), generation = 3))
        assertNull(controller.state.value.selectedCwd)
        assertIs<GitRead.Unselected>(controller.state.value.repository)
        controller.close()
    }

    @Test
    fun remoteScopeNeverCallsBackendEvenWithSelectedDirectory() = runTest {
        val backend = FakeGit()
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL.copy(access = GitAccess.RemoteUnavailable))
        controller.selectRepository("/parent-machine-repo")
        controller.refresh()
        controller.selectDiff(GitDiffRequest(GitDiffSource.Index))
        runCurrent()
        assertEquals(0, backend.discoverCalls)
        assertTrue(backend.statusRoots.isEmpty())
        assertNotNull(controller.state.value.unavailableReason)
        assertIs<GitRead.Unselected>(controller.state.value.diff)
        controller.close()
    }

    @Test
    fun branchFailureDoesNotBecomeEmptyListOrHideSuccessfulStatus() = runTest {
        val backend = FakeGit().apply { branchesResult = { error("private stderr") } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status)
        val failure = assertIs<GitRead.Failed>(controller.state.value.branches)
        assertFalse(failure.failure.message.contains("private stderr"))
        assertIs<GitRead.Ready<GitDiffSnapshot>>(controller.state.value.diff)
        controller.close()
    }

    @Test
    fun immediateUnconfinedReadsRegisterJobsBeforeTheyComplete() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val backend = FakeGit()
        val controller = GitController(scope, backend)
        try {
            controller.bind(LOCAL)
            controller.selectRepository("/repo")
            assertEquals("patch:/repo", ready(controller.state.value.diff).patch)
            controller.refresh()
            assertEquals(2, backend.statusRoots.size)
            controller.close()
            controller.close()
            assertEquals(GitState(), controller.state.value)
        } finally { controller.close(); scope.cancel() }
    }

    @Test
    fun previewReadsAreLazyDeduplicatedAndDroppedByRebind() = runTest {
        val gate = CompletableDeferred<GitPreviewFile>()
        val backend = FakeGit().apply { previewResult = { _, _ -> gate.await() } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        // No repository yet: nothing is requested.
        val unselected = GitController(backgroundScope, backend)
        unselected.refreshPreviewFiles(listOf("early.md"))
        runCurrent()
        assertTrue(backend.previewReads.isEmpty())
        unselected.close()
        // Distinct paths only, each once, and the card sees a Loading placeholder immediately.
        controller.refreshPreviewFiles(listOf("docs/a.md", "docs/a.md", "docs/b.md"))
        runCurrent()
        assertEquals(listOf("docs/a.md", "docs/b.md"), backend.previewReads.map { it.second })
        assertEquals(GitPreviewFile.Loading, controller.state.value.previewFiles["docs/a.md"])
        // A second request while the read is in flight adds no duplicate read.
        controller.refreshPreviewFiles(listOf("docs/a.md"))
        runCurrent()
        assertEquals(2, backend.previewReads.size)
        // Rebinding fences the in-flight read; its late result must not land in the new state.
        controller.bind(null)
        gate.complete(GitPreviewFile.Ready("# hi"))
        runCurrent()
        assertTrue(controller.state.value.previewFiles.isEmpty())
        controller.close()
    }

    @Test
    fun resolvedPreviewsAreCachedPerIncarnationAndOnlyReReadWhenTheDiffChanges() = runTest {
        val backend = FakeGit().apply { previewResult = { _, path ->
            if (path == "ok.md") GitPreviewFile.Ready("# ok") else GitPreviewFile.Unavailable("没有该文件")
        } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        controller.refreshPreviewFiles(listOf("ok.md"))
        runCurrent()
        assertEquals(GitPreviewFile.Ready("# ok"), controller.state.value.previewFiles["ok.md"])
        controller.refreshPreviewFiles(listOf("ok.md", "missing.md"))
        runCurrent()
        assertEquals(2, backend.previewReads.size)
        assertEquals(GitPreviewFile.Unavailable("没有该文件"), controller.state.value.previewFiles["missing.md"])
        // Both a success and a failure are terminal for this incarnation: no repeated reads.
        controller.refreshPreviewFiles(listOf("ok.md", "missing.md"))
        runCurrent()
        assertEquals(2, backend.previewReads.size)
        // A refresh that returns the same patch keeps the cached text and reads nothing more.
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.previewFiles.isNotEmpty())
        controller.refreshPreviewFiles(listOf("ok.md"))
        runCurrent()
        assertEquals(2, backend.previewReads.size)
        // A refresh whose patch changed drops the previews so the card reads the working tree again.
        backend.patch = "diff --git a/ok.md b/ok.md\n--- a/ok.md\n+++ b/ok.md\n@@ -1,1 +1,1 @@\n-a\n+b\n"
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.previewFiles.isEmpty())
        controller.refreshPreviewFiles(listOf("ok.md"))
        runCurrent()
        assertEquals(3, backend.previewReads.size)
        // A rebind starts a fresh incarnation with an empty cache.
        controller.bind(LOCAL.copy(generation = 2))
        runCurrent()
        assertTrue(controller.state.value.previewFiles.isEmpty())
        controller.close()
    }

    @Test
    fun previewReadFailureBecomesUnavailableAndClosedControllerIgnoresRequests() = runTest {
        val backend = FakeGit().apply { previewResult = { _, _ ->
            throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "private stderr"))
        } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        controller.refreshPreviewFiles(listOf("a.md"))
        runCurrent()
        val failure = assertIs<GitPreviewFile.Unavailable>(controller.state.value.previewFiles["a.md"])
        assertFalse(failure.reason.contains("private stderr"))
        controller.close()
        controller.refreshPreviewFiles(listOf("a.md"))
        runCurrent()
        assertEquals(1, backend.previewReads.size)
        assertTrue(controller.state.value.previewFiles.isEmpty())
    }
}

private val LOCAL = GitBinding(null, HostId("local"), 1, GitAccess.LocalReadOnly)
private fun repository(binding: GitBinding, cwd: String) = GitRepository(binding, cwd, "$cwd/.git", "$cwd/.git")
private fun <T> ready(read: GitRead<T>): T = assertIs<GitRead.Ready<T>>(read).value

private class FakeGit : GitBackend {
    var discoverCalls = 0
    val statusRoots = mutableListOf<String>()
    val previewReads = mutableListOf<Pair<GitRepository, String>>()
    var discoverResult: suspend (GitBinding, String) -> GitRepository = { b, cwd -> repository(b, cwd) }
    var statusResult: suspend (GitRepository) -> GitStatusSnapshot = { GitStatusSnapshot(emptyList(), "refs/heads/main", null) }
    var branchesResult: suspend (GitRepository) -> List<GitBranch> = { emptyList() }
    var patch: String? = null
    var diffResult: suspend (GitRepository, GitDiffRequest) -> GitDiffSnapshot =
        { repo, request -> GitDiffSnapshot(request, patch ?: "patch:${repo.root}", null) }
    var previewResult: suspend (GitRepository, String) -> GitPreviewFile = { _, _ -> GitPreviewFile.Unavailable("测试后端没有预览。") }
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository { discoverCalls++; return discoverResult(binding, cwd) }
    override suspend fun status(repository: GitRepository): GitStatusSnapshot { statusRoots.add(repository.root); return statusResult(repository) }
    override suspend fun branches(repository: GitRepository): List<GitBranch> = branchesResult(repository)
    override suspend fun worktrees(repository: GitRepository): List<GitWorktree> = listOf(GitWorktree(repository.root, null, "refs/heads/main"))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot = diffResult(repository, request)
    override suspend fun readPreviewFile(repository: GitRepository, path: String): GitPreviewFile {
        previewReads.add(repository to path)
        return previewResult(repository, path)
    }
}

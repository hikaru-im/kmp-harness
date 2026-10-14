package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitWorktreeCreationControllerTest {
    @Test
    fun successSelectsActualDirectoryAndKeepsReceiptWhenDiscoveryFails() = runTest {
        val backend = CreationGit().apply { failedDiscoverPath = "/new" }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new", "refs/heads/main")
        assertTrue(controller.state.value.creationBusy)
        runCurrent()
        val created = assertIs<GitWorktreeCreation.Created>(controller.state.value.creation)
        assertEquals("/new", created.receipt.worktree.path)
        assertEquals("refs/heads/main", created.receipt.request.startRef)
        assertEquals("/new", controller.state.value.selectedCwd)
        assertIs<GitRead.Failed>(controller.state.value.repository)
        assertFalse(controller.state.value.creationBusy)
        controller.refresh()
        runCurrent()
        assertEquals(created, controller.state.value.creation)
        assertIs<GitRead.Failed>(controller.state.value.repository)
        backend.failedDiscoverPath = null
        controller.refresh()
        runCurrent()
        assertEquals(created, controller.state.value.creation)
        assertEquals("/new", assertIs<GitRead.Ready<GitRepository>>(controller.state.value.repository).value.root)
        assertEquals(1, backend.creations, "discover retries never repeat creation")
        controller.close()
    }

    @Test
    fun successfulCreationAutomaticallyReadsNewRepository() = runTest {
        val backend = CreationGit()
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        assertEquals("/new", assertIs<GitRead.Ready<GitRepository>>(controller.state.value.repository).value.root)
        assertEquals(listOf("/repo", "/new"), backend.statusRoots)
        assertIs<GitWorktreeCreation.Created>(controller.state.value.creation)
        controller.refresh()
        runCurrent()
        assertIs<GitWorktreeCreation.Created>(controller.state.value.creation)
        assertEquals(1, backend.creations)
        controller.close()
    }

    @Test
    fun refreshDoesNotCancelCreationOrAllowDuplicateSubmission() = runTest {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val backend = CreationGit().apply { create = { repo, request ->
            try { gate.await(); created(repo, request) } catch (e: CancellationException) { cancelled = true; throw e }
        } }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        controller.refresh()
        controller.createWorktree("/duplicate", "codex/duplicate")
        runCurrent()
        assertTrue(controller.state.value.creationBusy)
        assertFalse(controller.state.value.canCreateWorktree)
        assertEquals(1, backend.creations)
        assertFalse(cancelled)
        gate.complete(Unit)
        runCurrent()
        assertIs<GitWorktreeCreation.Created>(controller.state.value.creation)
        controller.close()
    }

    @Test
    fun selectionAndBindingFenceWithoutCancellingInflightEffectOrOpeningAnotherSlot() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = CreationGit().apply { create = { repo, request -> gate.await(); created(repo, request) } }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        controller.selectRepository("/other")
        controller.bind(CREATION_LOCAL.copy(generation = 2))
        runCurrent()
        assertTrue(controller.state.value.creationBusy)
        controller.createWorktree("/second", "codex/second")
        assertEquals(1, backend.creations)
        gate.complete(Unit)
        runCurrent()
        assertEquals("/other", controller.state.value.selectedCwd)
        assertEquals(2L, controller.state.value.binding?.generation)
        assertIs<GitWorktreeCreation.Idle>(controller.state.value.creation)
        assertFalse(controller.state.value.creationBusy)
        controller.close()
    }

    @Test
    fun lateUnknownOnAnotherWorktreeAndGenerationNeedsRescanWithoutOldSensitiveDetails() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = CreationGit().apply { create = { _, _ ->
            gate.await()
            GitCreateWorktreeResult.Failed(GitCreateWorktreeFailure(
                GitFailure(GitFailureKind.Timeout, "secret old target /private"), true))
        } }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/private", "codex/private")
        runCurrent()
        controller.bind(CREATION_LOCAL.copy(access = GitAccess.Disconnected))
        controller.bind(CREATION_LOCAL.copy(generation = 2))
        controller.selectWorktree("/another-root")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertTrue(controller.state.value.creationNeedsRescan)
        assertFalse(controller.state.value.canCreateWorktree)
        val failed = assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        assertFalse(failed.failure.failure.message.contains("secret"))
        assertFalse(failed.failure.failure.message.contains("/private"))
        controller.refresh()
        runCurrent()
        assertFalse(controller.state.value.creationNeedsRescan)
        assertTrue(controller.state.value.canCreateWorktree)
        assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        assertIs<GitRead.Ready<List<GitWorktree>>>(controller.state.value.worktrees)
        assertEquals(1, backend.creations, "rescan never resubmits")
        controller.close()
    }

    @Test
    fun scanStartedBeforeUnknownResultCannotClearRescanRequirement() = runTest {
        val mutation = CompletableDeferred<Unit>()
        val scan = CompletableDeferred<Unit>()
        val backend = CreationGit()
        val controller = loaded(backgroundScope, backend)
        backend.create = { _, _ -> mutation.await(); unknownResult() }
        backend.scan = { repo -> scan.await(); listOf(GitWorktree(repo.root, CREATION_OID, "refs/heads/main")) }
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        controller.refresh()
        runCurrent()
        mutation.complete(Unit)
        runCurrent()
        scan.complete(Unit)
        runCurrent()
        assertTrue(controller.state.value.creationNeedsRescan)
        assertFalse(controller.state.value.canCreateWorktree)
        controller.refresh()
        runCurrent()
        assertFalse(controller.state.value.creationNeedsRescan)
        assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        assertEquals(1, backend.creations)
        controller.close()
    }

    @Test
    fun failedRescanKeepsUnknownBlockedAndDoesNotCreate() = runTest {
        val backend = CreationGit()
        val controller = loaded(backgroundScope, backend)
        backend.create = { _, _ -> unknownResult() }
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        backend.scan = { throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "scan failed")) }
        controller.refresh()
        runCurrent()
        controller.createWorktree("/retry", "codex/retry")
        assertTrue(controller.state.value.creationNeedsRescan)
        assertIs<GitRead.Failed>(controller.state.value.worktrees)
        assertEquals(1, backend.creations)
        controller.close()
    }

    @Test
    fun lateUnknownDoesNotBlockAnotherCommonDirButReturningRepositoryRequiresScan() = runTest {
        val mutation = CompletableDeferred<Unit>()
        val rescan = CompletableDeferred<Unit>()
        val backend = CreationGit().apply {
            commonDirFor = { cwd -> if (cwd == "/other") "/other/.git" else "/repo/.git" }
            create = { _, _ -> mutation.await(); unknownResult() }
        }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        controller.selectRepository("/other")
        runCurrent()
        assertTrue(controller.state.value.creationBusy)
        controller.createWorktree("/second", "codex/second")
        assertEquals(1, backend.creations)
        mutation.complete(Unit)
        runCurrent()
        assertEquals("/other", controller.state.value.selectedCwd)
        assertIs<GitWorktreeCreation.Idle>(controller.state.value.creation)
        assertFalse(controller.state.value.creationNeedsRescan)
        assertTrue(controller.state.value.canCreateWorktree)
        backend.scan = { repo -> rescan.await(); listOf(GitWorktree(repo.root, CREATION_OID, "refs/heads/main")) }
        controller.selectRepository("/repo")
        runCurrent()
        assertTrue(controller.state.value.creationNeedsRescan)
        assertFalse(controller.state.value.canCreateWorktree)
        assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        rescan.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.creationNeedsRescan)
        assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        assertIs<GitRead.Ready<List<GitWorktree>>>(controller.state.value.worktrees)
        assertEquals(1, backend.creations, "returning and scanning never repeat creation")
        controller.close()
    }

    @Test
    fun knownFailureKeepsSelectionAndRefreshDoesNotRetryMutation() = runTest {
        val expected = GitCreateWorktreeFailure(GitFailure(GitFailureKind.InvalidRequest, "target exists"))
        val backend = CreationGit().apply { create = { _, _ -> GitCreateWorktreeResult.Failed(expected) } }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        assertEquals("/repo", controller.state.value.selectedCwd)
        assertEquals(expected, assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation).failure)
        assertFalse(controller.state.value.creationBusy)
        assertFalse(controller.state.value.creationNeedsRescan)
        assertTrue(controller.state.value.canCreateWorktree)
        controller.refresh()
        runCurrent()
        assertEquals(expected, assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation).failure)
        assertEquals(1, backend.creations)
        controller.close()
    }

    @Test
    fun logoutAndIdentitySwitchDoNotPublishLateUnknownButReturningOwnerStillRequiresScan() = runTest {
        val gate = CompletableDeferred<Unit>()
        val first = CREATION_LOCAL.copy(identity = MemberIdentity(BackendTenant("https://fixture.invalid", 1), 1))
        val backend = CreationGit().apply { create = { _, _ -> gate.await(); unknownResult() } }
        val controller = GitController(backgroundScope, backend)
        controller.bind(first)
        controller.selectRepository("/repo")
        runCurrent()
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        controller.bind(null)
        gate.complete(Unit)
        runCurrent()
        assertEquals(GitState(), controller.state.value)
        controller.bind(first.copy(identity = first.identity!!.copy(userId = 2)))
        controller.selectRepository("/repo")
        runCurrent()
        assertIs<GitWorktreeCreation.Idle>(controller.state.value.creation)
        backend.scan = { throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "scan unavailable")) }
        controller.bind(first.copy(generation = 3))
        controller.selectRepository("/another-root")
        runCurrent()
        assertTrue(controller.state.value.creationNeedsRescan)
        assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        controller.close()
    }

    @Test
    fun cancellationAndUnexpectedExceptionAreUnknownAndNeverSuccessful() = runTest {
        for (cancel in listOf(true, false)) {
            val backend = CreationGit().apply { create = { _, _ ->
                if (cancel) throw CancellationException("fixture") else error("private stderr")
            } }
            val controller = loaded(backgroundScope, backend)
            controller.createWorktree("/new", "codex/new")
            runCurrent()
            assertFalse(controller.state.value.creationBusy)
            assertTrue(controller.state.value.creationNeedsRescan)
            assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
            assertEquals("/repo", controller.state.value.selectedCwd)
            controller.close()
        }
    }

    @Test
    fun invalidReceiptIsUnknownAndCannotSelectAnUnconfirmedDirectory() = runTest {
        val backend = CreationGit().apply { create = { repo, request ->
            val valid = created(repo, request).receipt
            GitCreateWorktreeResult.Created(valid.copy(worktree = valid.worktree.copy(branch = "refs/heads/other")))
        } }
        val controller = loaded(backgroundScope, backend)
        controller.createWorktree("/new", "codex/new")
        runCurrent()
        assertTrue(controller.state.value.creationNeedsRescan)
        assertEquals("/repo", controller.state.value.selectedCwd)
        assertIs<GitWorktreeCreation.Failed>(controller.state.value.creation)
        controller.close()
    }

    @Test
    fun absentCapabilityAndRemoteBindingNeverInvokeCreation() = runTest {
        val backend = CreationGit()
        val readOnly = object : GitBackend by backend {}
        val controller = GitController(backgroundScope, readOnly)
        controller.bind(CREATION_LOCAL)
        controller.selectRepository("/repo")
        runCurrent()
        assertFalse(controller.state.value.creationAvailable)
        controller.createWorktree("/new", "codex/new")
        assertEquals(0, backend.creations)
        controller.close()
        val remote = GitController(backgroundScope, backend)
        remote.bind(CREATION_LOCAL.copy(access = GitAccess.RemoteUnavailable))
        remote.selectRepository("/repo")
        remote.createWorktree("/new", "codex/new")
        runCurrent()
        assertFalse(remote.state.value.canCreateWorktree)
        assertEquals(0, backend.creations)
        remote.close()
    }

    @Test
    fun immediateUnconfinedCreationReleasesSlotAndKeepsReceipt() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val controller = GitController(scope, CreationGit())
        try {
            controller.bind(CREATION_LOCAL)
            controller.selectRepository("/repo")
            controller.createWorktree("/new", "codex/new")
            assertFalse(controller.state.value.creationBusy)
            assertIs<GitWorktreeCreation.Created>(controller.state.value.creation)
            assertEquals("/new", controller.state.value.selectedCwd)
        } finally { controller.close(); scope.cancel() }
    }

    private suspend fun TestScope.loaded(scope: CoroutineScope, backend: CreationGit): GitController =
        GitController(scope, backend).also {
            it.bind(CREATION_LOCAL)
            it.selectRepository("/repo")
            runCurrent()
            assertTrue(it.state.value.canCreateWorktree)
        }
}

private val CREATION_LOCAL = GitBinding(null, HostId("local"), 1, GitAccess.LocalReadOnly)
private val CREATION_OID = "a".repeat(40)
private fun created(repo: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult.Created {
    val actual = GitWorktree(request.path, CREATION_OID, "refs/heads/${request.branch}")
    return GitCreateWorktreeResult.Created(GitCreateWorktreeReceipt(repo, request, CREATION_OID, actual, listOf(actual)))
}
private fun unknownResult() = GitCreateWorktreeResult.Failed(
    GitCreateWorktreeFailure(GitFailure(GitFailureKind.Timeout, "unknown"), true))

private class CreationGit : GitBackend, GitWorktreeCreator {
    var creations = 0
    var failedDiscoverPath: String? = null
    var commonDirFor: (String) -> String = { "/repo/.git" }
    val statusRoots = mutableListOf<String>()
    var create: suspend (GitRepository, GitCreateWorktreeRequest) -> GitCreateWorktreeResult = ::created
    var scan: suspend (GitRepository) -> List<GitWorktree> = { listOf(GitWorktree(it.root, CREATION_OID, "refs/heads/main")) }
    override fun canCreateWorktree(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult {
        creations++
        return create(repository, request)
    }
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository {
        if (cwd == failedDiscoverPath) throw GitReadException(GitFailure(GitFailureKind.NotRepository, "discover failed"))
        return GitRepository(binding, cwd, commonDirFor(cwd), "$cwd/.git")
    }
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        statusRoots.add(repository.root)
        return GitStatusSnapshot(emptyList(), "refs/heads/main", CREATION_OID)
    }
    override suspend fun branches(repository: GitRepository) = listOf(GitBranch("refs/heads/main", CREATION_OID, true, false))
    override suspend fun worktrees(repository: GitRepository) = scan(repository)
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest) = GitDiffSnapshot(request, "", CREATION_OID)
}

package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Controller seams only; the desktop suite separately proves real Git objects for create-and-checkout. */
@OptIn(ExperimentalCoroutinesApi::class)
class GitBranchCreateControllerTest {
    @Test
    fun draftAndStartOnlySelectAndVerifiedCreateCarriesExpectedSnapshot() = runTest {
        val backend = BranchCreateControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            assertEquals(0, backend.creates)
            controller.setNewBranchName("codex/new")
            controller.setNewBranchStart("HEAD")
            assertEquals(0, backend.creates)
            assertTrue(controller.state.value.canCreateBranch)
            controller.createBranch()
            assertTrue(controller.state.value.branchCreateBusy)
            runCurrent()
            val receipt = assertIs<GitBranchCreation.Created>(controller.state.value.branchCreation).receipt
            assertEquals("codex/new", receipt.request.branch)
            assertEquals("HEAD", receipt.request.startPointRef)
            assertEquals(BC_MAIN_OID, receipt.request.startPointOid)
            assertEquals("refs/heads/main", receipt.request.expectedBranch)
            assertEquals("/repo", receipt.request.root)
            assertEquals(BC_BINDING, receipt.request.binding)
            assertEquals("refs/heads/codex/new",
                assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.branch)
            assertFalse(controller.state.value.branchCreateBusy)
            assertNull(controller.state.value.newBranchStartRef)
            assertEquals(1, backend.creates)
        } finally { controller.close() }
    }

    @Test
    fun createFromExistingLocalBranchPassesThatStartPoint() = runTest {
        val backend = BranchCreateControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            controller.setNewBranchName("codex/from-feature")
            controller.setNewBranchStart("refs/heads/feature")
            assertTrue(controller.state.value.canCreateBranch)
            controller.createBranch()
            runCurrent()
            val receipt = assertIs<GitBranchCreation.Created>(controller.state.value.branchCreation).receipt
            assertEquals("refs/heads/feature", receipt.request.startPointRef)
            assertEquals(BC_FEATURE_OID, receipt.request.startPointOid)
            assertEquals(1, backend.creates)
        } finally { controller.close() }
    }

    @Test
    fun nameCollisionDirtyUnbornRemoteAndUnsupportedNeverAdmitCreate() = runTest {
        val backend = BranchCreateControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            controller.setNewBranchName("feature")
            assertFalse(controller.state.value.canCreateBranch)
            controller.setNewBranchName("-bad")
            assertFalse(controller.state.value.canCreateBranch)
            controller.setNewBranchName("codex/ok")
            assertTrue(controller.state.value.canCreateBranch)
            backend.statusRead = { GitStatusSnapshot(
                listOf(GitChange("keep", indexStatus = '?', worktreeStatus = '?')), backend.currentRef, backend.currentOid) }
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.canCreateBranch)
            backend.statusRead = { GitStatusSnapshot(emptyList(), backend.currentRef, null) }
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.canCreateBranch)
            backend.statusRead = null
            controller.refresh()
            runCurrent()
            controller.bind(BC_BINDING.copy(access = GitAccess.RemoteUnavailable))
            assertFalse(controller.state.value.canCreateBranch)
            controller.createBranch()
            assertEquals(0, backend.creates)
        } finally { controller.close() }
        val readOnly = object : GitBackend by backend {}
        val unsupported = loaded(backgroundScope, readOnly)
        try {
            assertFalse(unsupported.state.value.branchCreateAvailable)
            unsupported.setNewBranchName("codex/ok")
            unsupported.createBranch()
            assertEquals(0, backend.creates)
        } finally { unsupported.close() }
    }

    @Test
    fun createSharesAdmissionWithCheckoutCreationAndCommit() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = BranchCreateControllerBackend().apply { create = { repo, request -> gate.await(); created(repo, request) } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.setNewBranchName("codex/new")
            controller.createBranch()
            runCurrent()
            assertTrue(controller.state.value.branchCreateBusy)
            assertFalse(controller.state.value.canSwitchBranch)
            assertFalse(controller.state.value.canCreateWorktree)
            controller.createBranch()
            controller.switchBranch()
            controller.createWorktree("/new", "new")
            controller.commit("late")
            assertEquals(1, backend.creates)
            gate.complete(Unit)
            runCurrent()
            assertFalse(controller.state.value.branchCreateBusy)
            assertIs<GitBranchCreation.Created>(controller.state.value.branchCreation)
        } finally { controller.close() }
    }

    @Test
    fun mismatchedReceiptAndCancellationAreUnknownNotVerified() = runTest {
        for (cancel in listOf(false, true)) {
            val backend = BranchCreateControllerBackend().apply { create = { repo, request ->
                if (cancel) throw CancellationException("accepted cancellation")
                val receipt = (created(repo, request) as GitCreateBranchResult.Created).receipt
                GitCreateBranchResult.Created(receipt.copy(request = request.copy(branch = "codex/other")))
            } }
            val controller = loaded(backgroundScope, backend)
            try {
                controller.setNewBranchName("codex/new")
                controller.createBranch()
                runCurrent()
                assertIs<GitBranchCreation.UnknownOutcome>(controller.state.value.branchCreation)
                assertTrue(controller.state.value.branchCreateNeedsRescan)
                assertFalse(controller.state.value.canCreateBranch)
                assertFalse(controller.state.value.creationBusy)
                assertFalse(controller.state.value.branchCreateBusy)
            } finally { controller.close() }
        }
    }

    @Test
    fun unknownAcrossNavigationRequiresAllThreeNewReadsAndNeverReplays() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = BranchCreateControllerBackend().apply { create = { _, _ ->
            gate.await(); GitCreateBranchResult.UnknownOutcome(GitFailure(GitFailureKind.Timeout, "private old target"))
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.setNewBranchName("codex/new")
            controller.createBranch()
            runCurrent()
            controller.selectRepository("/other")
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            val statusGate = CompletableDeferred<GitStatusSnapshot>()
            val branchGate = CompletableDeferred<List<GitBranch>>()
            backend.statusRead = { statusGate.await() }
            backend.branchRead = { branchGate.await() }
            controller.bind(BC_BINDING.copy(generation = 3))
            controller.selectRepository("/repo")
            runCurrent()
            assertIs<GitRead.Ready<List<GitWorktree>>>(controller.state.value.worktrees)
            assertTrue(controller.state.value.branchCreateNeedsRescan, "worktree read alone cannot resolve branch creation")
            val unknown = assertIs<GitBranchCreation.UnknownOutcome>(controller.state.value.branchCreation)
            assertFalse(unknown.failure.message.contains("private"))
            statusGate.complete(backend.snapshot())
            runCurrent()
            assertTrue(controller.state.value.branchCreateNeedsRescan)
            branchGate.complete(backend.currentBranches())
            runCurrent()
            assertFalse(controller.state.value.branchCreateNeedsRescan)
            assertIs<GitBranchCreation.Rescanned>(controller.state.value.branchCreation)
            assertNull(controller.state.value.newBranchStartRef)
            assertEquals(1, backend.creates)
        } finally { controller.close() }
    }

    private fun TestScope.loaded(scope: CoroutineScope, backend: GitBackend): GitController =
        GitController(scope, backend).also { it.bind(BC_BINDING); it.selectRepository("/repo"); runCurrent() }
}

private val BC_BINDING = GitBinding(null, HostId("branch-create-controller-local"), 1, GitAccess.LocalReadOnly)
private val BC_MAIN_OID = "a".repeat(40)
private val BC_FEATURE_OID = "b".repeat(40)

private class BranchCreateControllerBackend : GitBackend, GitBranchCreator {
    var creates = 0
    var statusReads = 0
    var currentRef = "refs/heads/main"
    var currentOid = BC_MAIN_OID
    var statusRead: (suspend () -> GitStatusSnapshot)? = null
    var branchRead: (suspend () -> List<GitBranch>)? = null
    var create: suspend (GitRepository, GitCreateBranchRequest) -> GitCreateBranchResult = { repo, request -> created(repo, request) }
    private val branches = mutableListOf(
        GitBranch("refs/heads/main", BC_MAIN_OID, true, false),
        GitBranch("refs/heads/feature", BC_FEATURE_OID, false, false),
    )
    fun snapshot() = GitStatusSnapshot(emptyList(), currentRef, currentOid)
    fun currentBranches(): List<GitBranch> = branches.map { it.copy(current = it.ref == currentRef) }
    fun created(repo: GitRepository, request: GitCreateBranchRequest): GitCreateBranchResult {
        val newRef = "refs/heads/${request.branch}"
        if (branches.none { it.ref == newRef }) branches += GitBranch(newRef, request.startPointOid, false, false)
        currentRef = newRef
        currentOid = request.startPointOid
        return GitCreateBranchResult.Created(GitCreateBranchReceipt(repo, request, snapshot(), currentBranches(),
            listOf(GitWorktree(repo.root, currentOid, currentRef))))
    }
    override fun canCreateBranch(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository {
        val root = if (cwd == "/repo/subdir") "/repo" else cwd
        return GitRepository(binding, root, "$root/.git", "$root/.git")
    }
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        statusReads++
        return statusRead?.invoke() ?: snapshot()
    }
    override suspend fun branches(repository: GitRepository) = branchRead?.invoke() ?: currentBranches()
    override suspend fun worktrees(repository: GitRepository) = listOf(GitWorktree(repository.root, currentOid, currentRef))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest) = GitDiffSnapshot(request, "", currentOid)
    override suspend fun createBranch(repository: GitRepository, request: GitCreateBranchRequest): GitCreateBranchResult {
        creates++
        return create(repository, request)
    }
}

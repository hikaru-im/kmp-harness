package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitBranchCheckoutControllerTest {
    @Test
    fun selectionOnlyReadsAndVerifiedSwitchCarriesExpectedSnapshot() = runTest {
        val backend = CheckoutControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            assertEquals(0, backend.switches)
            controller.selectBranch("feature")
            assertNull(controller.state.value.selectedBranchRef)
            controller.selectBranch("refs/heads/feature")
            assertEquals(0, backend.switches)
            controller.switchBranch()
            assertTrue(controller.state.value.creationBusy)
            runCurrent()
            val receipt = assertIs<GitBranchCheckout.Switched>(controller.state.value.checkout).receipt
            assertEquals("refs/heads/main", receipt.request.expectedBranch)
            assertEquals(CHECKOUT_MAIN_OID, receipt.request.expectedHeadOid)
            assertEquals(CHECKOUT_FEATURE_OID, receipt.request.targetOid)
            assertEquals("/repo", receipt.request.root)
            assertEquals(CHECKOUT_BINDING, receipt.request.binding)
            assertEquals("refs/heads/feature", assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.branch)
            assertFalse(controller.state.value.creationBusy)
            controller.refresh()
            runCurrent()
            assertEquals(1, backend.switches)
        } finally { controller.close() }
    }

    @Test
    fun switchAndCreationShareAdmissionAcrossRefreshAndNavigation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = CheckoutControllerBackend().apply { switch = { repo, request -> gate.await(); switched(repo, request) } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.selectBranch("refs/heads/feature")
            controller.switchBranch()
            runCurrent()
            controller.refresh()
            controller.createWorktree("/new", "new")
            controller.switchBranch()
            controller.selectRepository("/other")
            controller.bind(CHECKOUT_BINDING.copy(generation = 2))
            runCurrent()
            assertTrue(controller.state.value.creationBusy)
            controller.selectBranch("refs/heads/feature")
            controller.switchBranch()
            controller.createWorktree("/new", "new")
            assertEquals(1, backend.switches)
            assertEquals(0, backend.creates)
            gate.complete(Unit)
            runCurrent()
            assertEquals("/other", controller.state.value.selectedCwd)
            assertIs<GitBranchCheckout.Idle>(controller.state.value.checkout)
            assertFalse(controller.state.value.creationBusy)
            val creationGate = CompletableDeferred<Unit>()
            backend.create = { _, _ -> creationGate.await(); error("controlled creation failure") }
            controller.createWorktree("/new", "new")
            runCurrent()
            controller.switchBranch()
            assertEquals(1, backend.switches)
            assertEquals(1, backend.creates)
            creationGate.complete(Unit)
            runCurrent()
        } finally { controller.close() }
    }

    @Test
    fun fencedSuccessRefreshesSameRepositoryAndDiscardsLateOldHeadWithoutLeakingReceipt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val oldRead = CompletableDeferred<GitStatusSnapshot>()
        val backend = CheckoutControllerBackend().apply { switch = { repo, request -> gate.await(); switched(repo, request) } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.selectBranch("refs/heads/feature")
            controller.switchBranch()
            runCurrent()
            controller.selectRepository("/repo/subdir")
            runCurrent()
            assertEquals("refs/heads/main", assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.branch)
            val readsBeforeCompletion = backend.statusReads
            backend.statusRead = { withContext(NonCancellable) { oldRead.await() } }
            controller.refresh()
            runCurrent()
            backend.statusRead = null
            gate.complete(Unit)
            runCurrent()
            assertTrue(backend.statusReads > readsBeforeCompletion + 1, "fenced completion rereads the selected repository")
            assertEquals("/repo/subdir", controller.state.value.selectedCwd)
            assertIs<GitBranchCheckout.Idle>(controller.state.value.checkout)
            assertEquals("refs/heads/feature", assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.branch)
            oldRead.complete(GitStatusSnapshot(emptyList(), "refs/heads/main", CHECKOUT_MAIN_OID))
            runCurrent()
            assertEquals(CHECKOUT_FEATURE_OID, assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.headOid)
            assertFalse(controller.state.value.creationBusy)
            assertNull(controller.state.value.selectedBranchRef)
            assertEquals(1, backend.switches)
        } finally { oldRead.complete(backend.snapshot()); controller.close() }
    }

    @Test
    fun unknownAcrossNavigationRequiresAllThreeNewReadsAndNeverReplays() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = CheckoutControllerBackend().apply { switch = { _, _ ->
            gate.await(); GitBranchCheckoutResult.UnknownOutcome(GitFailure(GitFailureKind.Timeout, "private old target"))
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.selectBranch("refs/heads/feature")
            controller.switchBranch()
            runCurrent()
            controller.selectRepository("/other")
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertFalse(controller.state.value.checkoutNeedsRescan)
            val statusGate = CompletableDeferred<GitStatusSnapshot>()
            val branchGate = CompletableDeferred<List<GitBranch>>()
            backend.statusRead = { statusGate.await() }
            backend.branchRead = { branchGate.await() }
            controller.bind(CHECKOUT_BINDING.copy(generation = 3))
            controller.selectRepository("/repo")
            runCurrent()
            assertIs<GitRead.Ready<List<GitWorktree>>>(controller.state.value.worktrees)
            assertTrue(controller.state.value.checkoutNeedsRescan, "worktree read alone cannot resolve checkout")
            assertFalse(controller.state.value.canCreateWorktree)
            val unknown = assertIs<GitBranchCheckout.UnknownOutcome>(controller.state.value.checkout)
            assertFalse(unknown.failure.message.contains("private"))
            statusGate.complete(backend.snapshot())
            runCurrent()
            assertTrue(controller.state.value.checkoutNeedsRescan)
            branchGate.complete(backend.refs())
            runCurrent()
            assertFalse(controller.state.value.checkoutNeedsRescan)
            assertIs<GitBranchCheckout.Rescanned>(controller.state.value.checkout)
            assertNull(controller.state.value.selectedBranchRef)
            assertTrue(controller.state.value.canCreateWorktree)
            assertEquals(1, backend.switches)
        } finally { controller.close() }
    }

    @Test
    fun scanStartedBeforeUnknownAndFailedStatusCannotClearGate() = runTest {
        val gate = CompletableDeferred<Unit>()
        val oldRead = CompletableDeferred<GitStatusSnapshot>()
        val backend = CheckoutControllerBackend().apply { switch = { _, _ ->
            gate.await(); GitBranchCheckoutResult.UnknownOutcome(GitFailure(GitFailureKind.CommandFailed, "unknown"))
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.selectBranch("refs/heads/feature")
            controller.switchBranch()
            runCurrent()
            backend.statusRead = { withContext(NonCancellable) { oldRead.await() } }
            controller.refresh()
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            oldRead.complete(backend.snapshot())
            runCurrent()
            assertTrue(controller.state.value.checkoutNeedsRescan)
            assertIs<GitRead.Unselected>(controller.state.value.status)
            backend.statusRead = { throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "read failed")) }
            controller.refresh()
            runCurrent()
            assertTrue(controller.state.value.checkoutNeedsRescan)
            assertIs<GitRead.Failed>(controller.state.value.status)
            controller.createWorktree("/new", "new")
            assertEquals(0, backend.creates)
            backend.statusRead = null
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.checkoutNeedsRescan)
        } finally { controller.close() }
    }

    @Test
    fun mismatchedReceiptAndCancellationAreUnknownNotVerified() = runTest {
        for (cancel in listOf(false, true)) {
            val backend = CheckoutControllerBackend().apply { switch = { repo, request ->
                if (cancel) throw CancellationException("accepted cancellation")
                val receipt = (switched(repo, request) as GitBranchCheckoutResult.Switched).receipt
                GitBranchCheckoutResult.Switched(receipt.copy(request = request.copy(targetOid = CHECKOUT_MAIN_OID)))
            } }
            val controller = loaded(backgroundScope, backend)
            try {
                controller.selectBranch("refs/heads/feature")
                controller.switchBranch()
                runCurrent()
                assertIs<GitBranchCheckout.UnknownOutcome>(controller.state.value.checkout)
                assertTrue(controller.state.value.checkoutNeedsRescan)
                assertFalse(controller.state.value.canCreateWorktree)
                assertFalse(controller.state.value.creationBusy)
            } finally { controller.close() }
        }
    }

    @Test
    fun dirtyRemoteCurrentUnbornAndUnsupportedNeverAdmitSwitch() = runTest {
        val backend = CheckoutControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            controller.selectBranch("refs/remotes/origin/feature")
            assertNull(controller.state.value.selectedBranchRef)
            controller.switchBranch()
            controller.selectBranch("refs/heads/main")
            controller.switchBranch()
            controller.selectBranch("refs/heads/feature")
            backend.statusRead = { GitStatusSnapshot(listOf(GitChange("keep", indexStatus = '?', worktreeStatus = '?')), backend.currentRef, backend.currentOid) }
            controller.refresh()
            runCurrent()
            controller.switchBranch()
            backend.statusRead = { GitStatusSnapshot(emptyList(), "refs/heads/main", null) }
            controller.refresh()
            runCurrent()
            controller.switchBranch()
            controller.bind(CHECKOUT_BINDING.copy(access = GitAccess.RemoteUnavailable))
            controller.switchBranch()
            assertEquals(0, backend.switches)
        } finally { controller.close() }
        val readOnly = object : GitBackend by backend {}
        val unsupported = loaded(backgroundScope, readOnly)
        try {
            unsupported.selectBranch("refs/heads/feature")
            assertFalse(unsupported.state.value.checkoutAvailable)
            unsupported.switchBranch()
            assertEquals(0, backend.switches)
        } finally { unsupported.close() }
    }

    private fun TestScope.loaded(scope: CoroutineScope, backend: GitBackend): GitController =
        GitController(scope, backend).also { it.bind(CHECKOUT_BINDING); it.selectRepository("/repo"); runCurrent() }
}

private val CHECKOUT_BINDING = GitBinding(null, HostId("checkout-controller-local"), 1, GitAccess.LocalReadOnly)
private val CHECKOUT_MAIN_OID = "a".repeat(40)
private val CHECKOUT_FEATURE_OID = "b".repeat(40)

private class CheckoutControllerBackend : GitBackend, GitBranchSwitcher, GitWorktreeCreator {
    var switches = 0
    var creates = 0
    var statusReads = 0
    var currentRef = "refs/heads/main"
    var currentOid = CHECKOUT_MAIN_OID
    var statusRead: (suspend () -> GitStatusSnapshot)? = null
    var branchRead: (suspend () -> List<GitBranch>)? = null
    var switch: suspend (GitRepository, GitBranchCheckoutRequest) -> GitBranchCheckoutResult = { repo, request -> switched(repo, request) }
    var create: suspend (GitRepository, GitCreateWorktreeRequest) -> GitCreateWorktreeResult = { _, _ -> error("unused creation") }
    fun snapshot() = GitStatusSnapshot(emptyList(), currentRef, currentOid)
    fun refs() = listOf(GitBranch("refs/heads/main", CHECKOUT_MAIN_OID, currentRef == "refs/heads/main", false),
        GitBranch("refs/heads/feature", CHECKOUT_FEATURE_OID, currentRef == "refs/heads/feature", false),
        GitBranch("refs/remotes/origin/feature", CHECKOUT_FEATURE_OID, false, true))
    fun switched(repo: GitRepository, request: GitBranchCheckoutRequest): GitBranchCheckoutResult {
        currentRef = request.targetRef
        currentOid = request.targetOid
        return GitBranchCheckoutResult.Switched(GitBranchCheckoutReceipt(repo, request, snapshot(), refs(),
            listOf(GitWorktree(repo.root, currentOid, currentRef))))
    }
    override fun canSwitchBranch(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override fun canCreateWorktree(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository {
        val root = if (cwd == "/repo/subdir") "/repo" else cwd
        return GitRepository(binding, root, "$root/.git", "$root/.git")
    }
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        statusReads++
        return statusRead?.invoke() ?: snapshot()
    }
    override suspend fun branches(repository: GitRepository) = branchRead?.invoke() ?: refs()
    override suspend fun worktrees(repository: GitRepository) = listOf(GitWorktree(repository.root, currentOid, currentRef))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest) = GitDiffSnapshot(request, "", currentOid)
    override suspend fun switchBranch(repository: GitRepository, request: GitBranchCheckoutRequest): GitBranchCheckoutResult {
        switches++; return switch(repository, request)
    }
    override suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult {
        creates++; return create(repository, request)
    }
}

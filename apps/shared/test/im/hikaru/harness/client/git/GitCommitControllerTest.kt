package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Controller seams only; the desktop suites separately prove real index/object evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class GitCommitControllerTest {
    @Test
    fun normalizedMessageIsIdempotentAtCharacterAndUtf8ByteBoundaries() {
        val ascii = "x".repeat(32_768)
        val normalized = assertNotNull(normalizeGitCommitMessage(ascii))
        assertEquals(ascii + "\n", normalized)
        assertEquals(normalized, normalizeGitCommitMessage(normalized))
        assertEquals(normalized, normalizeGitCommitMessage(ascii + "\r\n\r\n"))
        assertNull(normalizeGitCommitMessage("x".repeat(32_769)))
        val bytes = "界".repeat(21_845)
        val accepted = assertNotNull(normalizeGitCommitMessage(bytes))
        assertEquals(65_536, accepted.encodeToByteArray().size)
        assertEquals(accepted, normalizeGitCommitMessage(accepted))
        assertNull(normalizeGitCommitMessage(bytes + "界"))
        assertNull(normalizeGitCommitMessage(" ".repeat(32_768)))
        assertNull(normalizeGitCommitMessage("nul\u0000"))
    }

    @Test
    fun cancelledOwnerBeforeBodyStartsReleasesAdmissionWithoutCapabilityInvocationOrUnknown() = runTest {
        val backend = CommitControllerBackend()
        val parent = SupervisorJob()
        val controller = loaded(CoroutineScope(backgroundScope.coroutineContext + parent), backend)
        try {
            parent.cancel()
            controller.commit("accepted before start")
            runCurrent()
            assertEquals(0, backend.commits)
            assertFalse(controller.state.value.creationBusy)
            assertFalse(controller.state.value.commitBusy)
            assertFalse(controller.state.value.commitNeedsRescan)
            assertIs<GitCommit.Rejected>(controller.state.value.commit)
            controller.selectRepository("/sibling")
            assertFalse(controller.state.value.creationBusy, "navigation cannot inherit a stranded admission slot")
        } finally { controller.close(); parent.cancel() }
    }

    @Test
    fun ownerCancelledAfterAcceptanceBeforeScheduledBodyRunsAlsoReleasesAdmission() = runTest {
        val backend = CommitControllerBackend()
        val parent = SupervisorJob()
        val controller = loaded(CoroutineScope(backgroundScope.coroutineContext + parent), backend)
        try {
            controller.commit("accepted but not executed")
            assertTrue(controller.state.value.commitBusy)
            parent.cancel()
            runCurrent()
            assertEquals(0, backend.commits)
            assertFalse(controller.state.value.creationBusy)
            assertFalse(controller.state.value.commitBusy)
            assertFalse(controller.state.value.commitNeedsRescan)
            assertIs<GitCommit.Rejected>(controller.state.value.commit)
        } finally { controller.close(); parent.cancel() }
    }

    @Test
    fun explicitCommitAcceptsExactSnapshotAndNormalizedMessageOnlyOnce() = runTest {
        val backend = CommitControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            val accepted = assertIs<GitRead.Ready<GitCommitSnapshot>>(controller.state.value.commitSnapshot).value
            controller.commit(" \r\nmessage\r\nsecond line\n\n")
            assertTrue(controller.state.value.commitBusy)
            controller.commit("duplicate")
            runCurrent()
            val receipt = assertIs<GitCommit.Verified>(controller.state.value.commit).receipt
            assertEquals(accepted, receipt.request.snapshot)
            assertEquals(" \nmessage\nsecond line\n", receipt.request.message)
            assertEquals(GitCommitPolicy.HooksDisabledSigningDisabled, receipt.request.policy)
            assertEquals(COMMIT_OLD, receipt.parentOid)
            assertEquals(COMMIT_NEW, receipt.snapshot.status.headOid)
            assertEquals(1, backend.commits)
            assertFalse(controller.state.value.canCommit)
            assertFalse(controller.state.value.creationBusy)
            controller.refresh()
            runCurrent()
            assertEquals(1, backend.commits)
        } finally { controller.close() }
    }

    @Test
    fun commitCreateAndSwitchShareAdmissionAndNavigationCannotCancelAcceptedMutation() = runTest {
        val commitGate = CompletableDeferred<Unit>()
        val backend = CommitControllerBackend().apply { action = { repo, request ->
            commitGate.await(); committed(repo, request)
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.commit("accepted")
            runCurrent()
            controller.refresh()
            controller.createWorktree("/new", "new")
            controller.switchBranch()
            controller.selectRepository("/elsewhere")
            controller.bind(COMMIT_BINDING.copy(generation = 2))
            runCurrent()
            controller.commit("redirected")
            controller.createWorktree("/new", "new")
            assertTrue(controller.state.value.creationBusy)
            assertEquals(1, backend.commits)
            assertEquals(0, backend.creates)
            assertEquals(0, backend.switches)
            commitGate.complete(Unit)
            runCurrent()
            assertIs<GitCommit.Idle>(controller.state.value.commit)
            assertEquals("/elsewhere", controller.state.value.selectedCwd)
            assertFalse(controller.state.value.creationBusy)
            val createGate = CompletableDeferred<Unit>()
            backend.createGate = createGate
            controller.createWorktree("/new", "new")
            runCurrent()
            controller.commit("during creation")
            assertEquals(1, backend.commits)
            createGate.complete(Unit)
            runCurrent()
            // Switch shares the same slot even though the index on this separate repository is empty.
            backend.clean = true
            controller.refresh()
            runCurrent()
            val switchGate = CompletableDeferred<Unit>()
            backend.switchGate = switchGate
            controller.selectBranch("refs/heads/feature")
            controller.switchBranch()
            runCurrent()
            controller.commit("during switch")
            controller.createWorktree("/new2", "new2")
            assertEquals(1, backend.switches)
            assertEquals(1, backend.creates)
            assertEquals(1, backend.commits)
            switchGate.complete(Unit)
            runCurrent()
        } finally { commitGate.complete(Unit); controller.close() }
    }

    @Test
    fun mismatchedReceiptAndCancellationAreUnknownNeverVerifiedOrReplayed() = runTest {
        for (mode in listOf("parent", "tree", "message", "repository", "cancel")) {
            val backend = CommitControllerBackend().apply { action = { repo, request ->
                if (mode == "cancel") throw CancellationException("private old scope")
                val receipt = committed(repo, request).receipt
                GitCommitResult.Committed(when (mode) {
                    "parent" -> receipt.copy(parentOid = COMMIT_NEW)
                    "tree" -> receipt.copy(treeDigest = "e".repeat(64))
                    "message" -> receipt.copy(message = "other message\n")
                    else -> receipt.copy(repository = repo.copy(root = "/private"))
                })
            } }
            val controller = loaded(backgroundScope, backend)
            try {
                controller.commit("accepted")
                runCurrent()
                val unknown = assertIs<GitCommit.UnknownOutcome>(controller.state.value.commit)
                assertFalse(unknown.failure.message.contains("private"))
                assertTrue(controller.state.value.commitNeedsRescan, mode)
                assertFalse(controller.state.value.canCommit)
                assertFalse(controller.state.value.canCreateWorktree)
                controller.commit("replay")
                controller.createWorktree("/new", "new")
                assertEquals(1, backend.commits)
                assertEquals(0, backend.creates)
                controller.refresh()
                runCurrent()
                assertIs<GitCommit.Rescanned>(controller.state.value.commit)
                assertFalse(controller.state.value.commitNeedsRescan)
                assertEquals(1, backend.commits)
            } finally { controller.close() }
        }
    }

    @Test
    fun siblingWorktreeAndGenerationCannotResolveOriginalIndexUnknown() = runTest {
        val backend = CommitControllerBackend().apply { action = { _, _ ->
            GitCommitResult.UnknownOutcome(GitFailure(GitFailureKind.Timeout, "private original path"))
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.commit("accepted")
            runCurrent()
            controller.selectRepository("/sibling")
            controller.bind(COMMIT_BINDING.copy(generation = 3))
            runCurrent()
            assertIs<GitRead.Ready<GitCommitSnapshot>>(controller.state.value.commitSnapshot)
            assertTrue(controller.state.value.commitNeedsRescan)
            assertFalse(controller.state.value.canCreateWorktree)
            controller.refresh()
            runCurrent()
            assertTrue(controller.state.value.commitNeedsRescan, "a sibling has a different index")
            val gate = CompletableDeferred<Unit>()
            backend.scanGate = gate
            controller.selectRepository("/repo")
            runCurrent()
            assertIs<GitRead.Ready<List<GitWorktree>>>(controller.state.value.worktrees)
            assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status)
            assertTrue(controller.state.value.commitNeedsRescan, "ordinary reads are insufficient")
            gate.complete(Unit)
            runCurrent()
            assertFalse(controller.state.value.commitNeedsRescan)
            assertIs<GitCommit.Rescanned>(controller.state.value.commit)
            assertTrue(controller.state.value.canCreateWorktree)
            assertEquals(1, backend.commits)
        } finally { controller.close() }
    }

    @Test
    fun scanStartedBeforeUnknownAndFailedStrongScanCannotClearGate() = runTest {
        val mutationGate = CompletableDeferred<Unit>()
        val oldRead = CompletableDeferred<GitCommitSnapshot>()
        val backend = CommitControllerBackend().apply { action = { _, _ ->
            mutationGate.await(); GitCommitResult.UnknownOutcome(GitFailure(GitFailureKind.CommandFailed, "unknown"))
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.commit("accepted")
            runCurrent()
            backend.scan = { repo -> withContext(NonCancellable) { oldRead.await().copy(repository = repo) } }
            controller.refresh()
            runCurrent()
            mutationGate.complete(Unit)
            runCurrent()
            backend.scan = null
            oldRead.complete(backend.snapshot(backend.repo(COMMIT_BINDING, "/repo")))
            runCurrent()
            assertTrue(controller.state.value.commitNeedsRescan)
            assertIs<GitRead.Unselected>(controller.state.value.commitSnapshot)
            backend.scan = { throw GitReadException(GitFailure(GitFailureKind.MalformedOutput, "invalid index")) }
            controller.refresh()
            runCurrent()
            assertIs<GitRead.Failed>(controller.state.value.commitSnapshot)
            assertTrue(controller.state.value.commitNeedsRescan)
            backend.scan = null
            controller.refresh()
            runCurrent()
            assertIs<GitCommit.Rescanned>(controller.state.value.commit)
            assertFalse(controller.state.value.commitNeedsRescan)
            assertEquals(1, backend.commits)
        } finally { mutationGate.complete(Unit); controller.close() }
    }

    @Test
    fun fencedSuccessRefreshesSameRepositoryAndDiscardsOldHeadWithoutReceiptLeak() = runTest {
        val mutationGate = CompletableDeferred<Unit>()
        val oldRead = CompletableDeferred<GitCommitSnapshot>()
        val backend = CommitControllerBackend().apply { action = { repo, request ->
            mutationGate.await(); committed(repo, request)
        } }
        val controller = loaded(backgroundScope, backend)
        try {
            controller.commit("old scope message")
            runCurrent()
            controller.selectRepository("/repo/subdir")
            runCurrent()
            assertEquals(COMMIT_OLD, assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.headOid)
            backend.scan = { repo -> withContext(NonCancellable) { oldRead.await().copy(repository = repo) } }
            controller.refresh()
            runCurrent()
            backend.scan = null
            mutationGate.complete(Unit)
            runCurrent()
            assertEquals(COMMIT_NEW, assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value.headOid)
            assertEquals(COMMIT_NEW, assertIs<GitRead.Ready<GitCommitSnapshot>>(controller.state.value.commitSnapshot).value.status.headOid)
            assertIs<GitCommit.Idle>(controller.state.value.commit)
            oldRead.complete(backend.snapshot(backend.repo(COMMIT_BINDING, "/repo")).copy(
                status = GitStatusSnapshot(emptyList(), "refs/heads/main", COMMIT_OLD)))
            runCurrent()
            assertEquals(COMMIT_NEW, assertIs<GitRead.Ready<GitCommitSnapshot>>(controller.state.value.commitSnapshot).value.status.headOid)
            assertEquals("/repo/subdir", controller.state.value.selectedCwd)
            assertEquals(1, backend.commits)
        } finally { mutationGate.complete(Unit); controller.close() }
    }

    @Test
    fun unavailableEmptyBlockedAndInvalidMessagesNeverDispatch() = runTest {
        val backend = CommitControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            for (message in listOf("", " \n ", "nul\u0000", "x".repeat(32_769))) controller.commit(message)
            runCurrent()
            assertEquals(0, backend.commits)
            backend.clean = true
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.canCommit)
            controller.commit("empty")
            backend.clean = false
            backend.blocked = "operation in progress"
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.canCommit)
            controller.commit("blocked")
            controller.bind(COMMIT_BINDING.copy(access = GitAccess.RemoteUnavailable))
            controller.commit("remote")
            assertFalse(controller.state.value.commitAvailable)
            assertEquals(0, backend.commits)
            val readOnly = GitController(backgroundScope, object : GitBackend by backend {})
            try {
                readOnly.bind(COMMIT_BINDING)
                readOnly.selectRepository("/repo")
                runCurrent()
                assertFalse(readOnly.state.value.commitAvailable)
                assertIs<GitRead.Unselected>(readOnly.state.value.commitSnapshot)
                readOnly.commit("unsupported")
                assertEquals(0, backend.commits)
            } finally { readOnly.close() }
        } finally { controller.close() }
    }

    private suspend fun TestScope.loaded(scope: CoroutineScope, backend: CommitControllerBackend): GitController {
        val controller = GitController(scope, backend)
        controller.bind(COMMIT_BINDING)
        controller.selectRepository("/repo")
        runCurrent()
        assertTrue(controller.state.value.canCommit)
        return controller
    }

    @Test
    fun includeUnstagedAllowsAnEmptyIndexButRequiresAReceiptThatDeclaresTheStageStep() = runTest {
        val backend = CommitControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            // Re-scan as an unstaged-only worktree whose index is empty.
            backend.scan = { repo -> backend.snapshot(repo).copy(stagedCount = 0,
                status = GitStatusSnapshot(listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M')),
                    "refs/heads/main", COMMIT_OLD)) }
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.canCommit)
            assertTrue(assertNotNull(controller.state.value.commitDisabledReason).contains("勾选"))
            var captured: GitCommitRequest? = null
            backend.action = { repo, request -> captured = request; backend.committed(repo, request) }
            controller.setIncludeUnstaged(true)
            assertTrue(controller.state.value.includeUnstaged)
            assertTrue(controller.state.value.canCommit)
            controller.commit("include unstaged")
            runCurrent()
            assertEquals(true, captured?.includeUnstaged)
            // A receipt that does not declare the stage step must not be trusted.
            assertIs<GitCommit.UnknownOutcome>(controller.state.value.commit)
            assertEquals(1, backend.commits)
        } finally { controller.close() }
    }

    @Test
    fun includeUnstagedVerifiedReceiptMustBindThePostStageDigest() = runTest {
        val backend = CommitControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            val stagedDigest = "e".repeat(64)
            backend.action = { repo, request ->
                val base = (backend.committed(repo, request) as GitCommitResult.Committed).receipt
                GitCommitResult.Committed(base.copy(treeDigest = stagedDigest, stagedByRequest = true,
                    stagedIndexDigest = stagedDigest, snapshot = base.snapshot.copy(indexDigest = stagedDigest)))
            }
            controller.setIncludeUnstaged(true)
            controller.commit("verified include")
            runCurrent()
            assertIs<GitCommit.Verified>(controller.state.value.commit)
            assertEquals(1, backend.commits)
        } finally { controller.close() }
    }
}

private val COMMIT_BINDING = GitBinding(null, HostId("commit-controller"), 1, GitAccess.LocalReadOnly)
private val COMMIT_OLD = "a".repeat(40)
private val COMMIT_NEW = "b".repeat(40)
private val COMMIT_DIGEST = "d".repeat(64)

private class CommitControllerBackend : GitBackend, GitCommitter, GitWorktreeCreator, GitBranchSwitcher {
    var commits = 0
    var creates = 0
    var switches = 0
    var clean = false
    var blocked: String? = null
    var scanGate: CompletableDeferred<Unit>? = null
    var scan: (suspend (GitRepository) -> GitCommitSnapshot)? = null
    var createGate: CompletableDeferred<Unit>? = null
    var switchGate: CompletableDeferred<Unit>? = null
    private val heads = mutableMapOf<String, String>()
    var action: suspend (GitRepository, GitCommitRequest) -> GitCommitResult = { repo, request -> committed(repo, request) }
    fun repo(binding: GitBinding, cwd: String): GitRepository {
        val root = when { cwd.startsWith("/repo") -> "/repo"; cwd == "/sibling" -> "/sibling"; else -> cwd }
        return GitRepository(binding, root, if (root in listOf("/repo", "/sibling")) "/common" else "/other-common", "$root/git")
    }
    fun snapshot(repo: GitRepository): GitCommitSnapshot {
        val changes = if (clean || heads[repo.root] == COMMIT_NEW) emptyList() else listOf(GitChange("tracked", indexStatus = 'M', worktreeStatus = 'M'))
        return GitCommitSnapshot(repo, GitStatusSnapshot(changes, "refs/heads/main", heads[repo.root] ?: COMMIT_OLD),
            COMMIT_DIGEST, changes.size, blocked)
    }
    fun refs(repo: GitRepository) = listOf(GitBranch("refs/heads/main", snapshot(repo).status.headOid!!, true, false),
        GitBranch("refs/heads/feature", "c".repeat(40), false, false))
    fun trees(repo: GitRepository) = listOf(GitWorktree(repo.root, snapshot(repo).status.headOid, "refs/heads/main"))
    fun committed(repo: GitRepository, request: GitCommitRequest): GitCommitResult.Committed {
        heads[repo.root] = COMMIT_NEW
        return GitCommitResult.Committed(GitCommitReceipt(repo, request, COMMIT_NEW, COMMIT_OLD, "c".repeat(40),
            request.snapshot.indexDigest, request.message, snapshot(repo), refs(repo), trees(repo)))
    }
    override suspend fun discover(binding: GitBinding, cwd: String) = repo(binding, cwd)
    override suspend fun status(repository: GitRepository) = snapshot(repository).status
    override suspend fun branches(repository: GitRepository) = refs(repository)
    override suspend fun worktrees(repository: GitRepository) = trees(repository)
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest) = GitDiffSnapshot(request, "", snapshot(repository).status.headOid)
    override fun canCommit(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun readCommitSnapshot(repository: GitRepository): GitCommitSnapshot {
        scanGate?.await()
        return scan?.invoke(repository) ?: snapshot(repository)
    }
    override suspend fun commit(repository: GitRepository, request: GitCommitRequest): GitCommitResult {
        commits++
        return action(repository, request)
    }
    override fun canCreateWorktree(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult {
        creates++; createGate?.await()
        return GitCreateWorktreeResult.Failed(GitCreateWorktreeFailure(GitFailure(GitFailureKind.InvalidRequest, "controlled preflight rejection")))
    }
    override fun canSwitchBranch(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun switchBranch(repository: GitRepository, request: GitBranchCheckoutRequest): GitBranchCheckoutResult {
        switches++; switchGate?.await()
        return GitBranchCheckoutResult.Rejected(GitFailure(GitFailureKind.InvalidRequest, "controlled preflight rejection"))
    }
}

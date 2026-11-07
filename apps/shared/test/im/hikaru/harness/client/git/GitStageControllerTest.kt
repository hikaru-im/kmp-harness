package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Controller seams for index-only staging; the desktop suite separately proves real index evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class GitStageControllerTest {
    @Test
    fun filterSplitsTheChangeListByIndexAndWorktreeState() {
        val untracked = GitChange("new", indexStatus = '?', worktreeStatus = '?')
        val worktreeOnly = GitChange("edited", indexStatus = ' ', worktreeStatus = 'M')
        val both = GitChange("both", indexStatus = 'M', worktreeStatus = 'M')
        val stagedOnly = GitChange("added", indexStatus = 'A', worktreeStatus = ' ')
        val conflicted = GitChange("conflict", indexStatus = 'U', worktreeStatus = 'U')
        val changes = listOf(untracked, worktreeOnly, both, stagedOnly, conflicted)

        assertEquals(listOf(untracked, worktreeOnly, both, conflicted), GitChangeFilter.Unstaged.filter(changes))
        assertEquals(listOf(both, stagedOnly, conflicted), GitChangeFilter.Staged.filter(changes))
        assertEquals(4, GitChangeFilter.Unstaged.count(changes))
        assertEquals(3, GitChangeFilter.Staged.count(changes))
        assertEquals("未暂存", GitChangeFilter.Unstaged.label)
        assertEquals("已暂存", GitChangeFilter.Staged.label)
        // Every entry carries a real letter on both sides, so nothing silently disappears from review.
        assertTrue(changes.all { change -> GitChangeFilter.entries.any { it.matches(change) } })
    }

    @Test
    fun actionEligibilityFollowsTheFilterAndNeverTouchesConflicts() {
        val untracked = GitChange("new", indexStatus = '?', worktreeStatus = '?')
        val stagedOnly = GitChange("added", indexStatus = 'A', worktreeStatus = ' ')
        val conflicted = GitChange("conflict", indexStatus = 'U', worktreeStatus = 'U')
        assertTrue(GitStageAction.Stage.accepts(untracked))
        assertTrue(GitStageAction.Stage.accepts(GitChange("edited", indexStatus = ' ', worktreeStatus = 'M')))
        assertFalse(GitStageAction.Stage.accepts(stagedOnly))
        assertFalse(GitStageAction.Unstage.accepts(untracked))
        assertTrue(GitStageAction.Unstage.accepts(stagedOnly))
        assertFalse(GitStageAction.Stage.accepts(conflicted))
        assertFalse(GitStageAction.Unstage.accepts(conflicted))
    }

    @Test
    fun stagePathRejectsAnythingGitCouldReadAsAnotherTarget() {
        assertTrue(gitStagePathIsSafe("src/app.kt"))
        assertTrue(gitStagePathIsSafe("中文 文件.txt"))
        assertFalse(gitStagePathIsSafe("-n"))
        assertFalse(gitStagePathIsSafe("/etc/passwd"))
        assertFalse(gitStagePathIsSafe("../outside.txt"))
        assertFalse(gitStagePathIsSafe("a/../b"))
        assertFalse(gitStagePathIsSafe("a//b"))
        assertFalse(gitStagePathIsSafe("C:/repo/file"))
        assertFalse(gitStagePathIsSafe("a\\b"))
        assertFalse(gitStagePathIsSafe("a\u0000b"))
        assertFalse(gitStagePathIsSafe(" "))
    }

    @Test
    fun stageFileSendsTheReviewedChangeAndAdoptsTheRealPostCommandStatus() = runTest {
        val backend = StageControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            controller.stageFile("tracked")
            assertTrue(controller.state.value.stageBusy)
            runCurrent()
            val request = assertNotNull(backend.requests.singleOrNull())
            assertEquals(GitStageAction.Stage, request.action)
            assertEquals(GitStageTarget.File("tracked"), request.target)
            assertEquals(STAGE_HEAD, request.expectedHeadOid)
            assertEquals("refs/heads/main", request.expectedBranch)
            val applied = assertIs<GitStage.Applied>(controller.state.value.stage)
            assertEquals(backend.statusOf(backend.repositoryOf("/repo")), applied.receipt.status)
            assertEquals(applied.receipt.status, assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value)
            assertFalse(controller.state.value.stageBusy)
            assertFalse(controller.state.value.stageNeedsRescan)
            assertIs<GitRead.Ready<GitDiffSnapshot>>(controller.state.value.diff)
        } finally { controller.close() }
    }

    @Test
    fun ineligibleOrUnsafeTargetsNeverInvokeTheCapability() = runTest {
        val backend = StageControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(
                GitChange("staged", indexStatus = 'M', worktreeStatus = ' '),
                GitChange("conflict", indexStatus = 'U', worktreeStatus = 'U'))
            controller.refresh()
            runCurrent()
            controller.stageFile("staged")       // already staged
            controller.stageFile("conflict")     // unmerged index
            controller.stageFile("missing")      // not in the change list
            controller.stageFile("-n")           // option-like path
            controller.unstageFile("staged")     // allowed
            runCurrent()
            assertEquals(listOf(GitStageAction.Unstage), backend.requests.map { it.action })
            assertEquals(1, backend.requests.size)
        } finally { controller.close() }
    }

    @Test
    fun unstageFileOnAnUnbornBranchCarriesNoHeadAndIsAccepted() = runTest {
        val backend = StageControllerBackend().apply { head = null }
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("new", indexStatus = 'A', worktreeStatus = ' '))
            controller.refresh()
            runCurrent()
            controller.unstageFile("new")
            runCurrent()
            val request = assertNotNull(backend.requests.singleOrNull())
            assertNull(request.expectedHeadOid)
            assertEquals("refs/heads/main", request.expectedBranch)
            assertIs<GitStage.Applied>(controller.state.value.stage)
        } finally { controller.close() }
    }

    @Test
    fun stageHunkSendsThePatchItWasGivenAndUnstageHunkReversesIt() = runTest {
        val backend = StageControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            val patch = "diff --git a/tracked b/tracked\n--- a/tracked\n+++ b/tracked\n@@ -1 +1 @@\n-a\n+b\n"
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            controller.stageHunk("tracked", patch)
            runCurrent()
            controller.unstageHunk("tracked", patch)
            runCurrent()
            assertEquals(listOf(GitStageAction.Stage, GitStageAction.Unstage), backend.requests.map { it.action })
            assertEquals(listOf<GitStageTarget>(GitStageTarget.Hunk("tracked", patch), GitStageTarget.Hunk("tracked", patch)),
                backend.requests.map { it.target })
            assertEquals(patch, (backend.requests.first().target as GitStageTarget.Hunk).patch)
        } finally { controller.close() }
    }

    @Test
    fun rejectedStageRefreshesInsteadOfClaimingSuccess() = runTest {
        val backend = StageControllerBackend().apply {
            action = { _, _ -> GitStageResult.Rejected(GitFailure(GitFailureKind.StaleSnapshot, "暂存区已变化")) }
        }
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            controller.stageFile("tracked")
            runCurrent()
            val rejected = assertIs<GitStage.Rejected>(controller.state.value.stage)
            assertEquals(GitFailureKind.StaleSnapshot, rejected.failure.kind)
            assertFalse(controller.state.value.stageNeedsRescan)
            assertEquals(1, backend.requests.size)
            val status = assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status).value
            assertEquals(listOf("tracked"), status.changes.map { it.path })
        } finally { controller.close() }
    }

    @Test
    fun unknownOutcomeBlocksEveryMutationUntilARealStatusRereadResolvesIt() = runTest {
        val backend = StageControllerBackend().apply {
            action = { _, _ -> GitStageResult.UnknownOutcome(GitFailure(GitFailureKind.Timeout, "私有路径")) }
        }
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            controller.stageFile("tracked")
            runCurrent()
            val unknown = assertIs<GitStage.UnknownOutcome>(controller.state.value.stage)
            assertFalse(unknown.failure.message.contains("私有路径"))
            assertTrue(controller.state.value.stageNeedsRescan)
            assertFalse(controller.state.value.canStage)
            assertFalse(controller.state.value.canCommit)
            assertFalse(controller.state.value.canCreateWorktree)
            controller.stageFile("tracked")
            controller.commit("replay")
            runCurrent()
            assertEquals(1, backend.requests.size)
            assertEquals(0, backend.commits)
            controller.refresh()
            runCurrent()
            assertFalse(controller.state.value.stageNeedsRescan)
            assertIs<GitStage.Rescanned>(controller.state.value.stage)
            assertTrue(controller.state.value.canStage)
        } finally { controller.close() }
    }

    @Test
    fun receiptThatMovesHeadIsUnknownNeverApplied() = runTest {
        val backend = StageControllerBackend().apply {
            action = { repo, request -> GitStageResult.Applied(GitStageReceipt(repo, request,
                GitStatusSnapshot(emptyList(), "refs/heads/main", "f".repeat(40)))) }
        }
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            controller.stageFile("tracked")
            runCurrent()
            assertIs<GitStage.UnknownOutcome>(controller.state.value.stage)
            assertTrue(controller.state.value.stageNeedsRescan)
        } finally { controller.close() }
    }

    @Test
    fun navigationDuringAnAcceptedStageCannotRedirectOrCancelIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = StageControllerBackend().apply {
            action = { repo, request -> gate.await(); applied(repo, request) }
        }
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            controller.stageFile("tracked")
            runCurrent()
            controller.stageFile("tracked")
            controller.selectRepository("/sibling")
            controller.bind(STAGE_BINDING.copy(generation = 2))
            runCurrent()
            assertEquals(1, backend.requests.size)
            gate.complete(Unit)
            runCurrent()
            assertFalse(controller.state.value.creationBusy)
            assertFalse(controller.state.value.stageBusy)
            assertEquals("/sibling", controller.state.value.selectedCwd)
        } finally { gate.complete(Unit); controller.close() }
    }

    @Test
    fun filterSelectionIsPureAndOnlyChangesWhichChangesAreListed() = runTest {
        val backend = StageControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("edited", indexStatus = ' ', worktreeStatus = 'M'),
                GitChange("added", indexStatus = 'A', worktreeStatus = ' '))
            controller.refresh()
            runCurrent()
            assertEquals(GitChangeFilter.Unstaged, controller.state.value.changeFilter)
            assertEquals(listOf("edited"), controller.changesFor().map { it.path })
            val reads = backend.statusReads
            controller.selectChangeFilter(GitChangeFilter.Staged)
            assertEquals(GitChangeFilter.Staged, controller.state.value.changeFilter)
            assertEquals(listOf("added"), controller.changesFor().map { it.path })
            assertEquals(0, backend.requests.size)
            assertEquals(reads, backend.statusReads, "selecting a filter never re-reads the repository")
        } finally { controller.close() }
    }

    @Test
    fun hunkPatchKeepsOnlyTheHeadersGitNeedsAndOneHunk() {
        val patch = "diff --git a/src/app.kt b/src/app.kt\n" +
            "index 1111111..2222222 100644\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,3 +1,3 @@ fun main() {\n" +
            " one\n" +
            "-two\n" +
            "+TWO\n" +
            " three\n" +
            "@@ -10,2 +10,3 @@\n" +
            " ten\n" +
            "+eleven\n" +
            " twelve\n"
        val file = assertNotNull(parseGitDiff(patch).files.singleOrNull())
        val first = assertNotNull(gitHunkPatch(file, 0))
        assertEquals("diff --git a/src/app.kt b/src/app.kt\n" +
            "index 1111111..2222222 100644\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,3 +1,3 @@ fun main() {\n" +
            " one\n" +
            "-two\n" +
            "+TWO\n" +
            " three\n", first)
        assertEquals(1, first.lineSequence().count { it.startsWith("@@") })
        assertTrue(gitHunkPatch(file, 1)!!.endsWith(" twelve\n"))
        assertNull(gitHunkPatch(file, 2))
        assertNull(gitHunkPatch(file, -1))
    }

    @Test
    fun appliedStageLeavesBranchAndWorktreeReadsReadyForThePage() = runTest {
        val backend = StageControllerBackend()
        val controller = loaded(backgroundScope, backend)
        try {
            backend.changes = listOf(GitChange("tracked", indexStatus = ' ', worktreeStatus = 'M'))
            controller.refresh()
            runCurrent()
            assertIs<GitRead.Ready<List<GitBranch>>>(controller.state.value.branches)
            controller.stageFile("tracked")
            runCurrent()
            assertIs<GitStage.Applied>(controller.state.value.stage)
            // The entry state clears branches/worktrees; success must reread them instead of leaving
            // the page on Unselected or a Loading read that was fenced away.
            assertIs<GitRead.Ready<List<GitBranch>>>(controller.state.value.branches)
            assertIs<GitRead.Ready<List<GitWorktree>>>(controller.state.value.worktrees)
            assertIs<GitRead.Ready<GitStatusSnapshot>>(controller.state.value.status)
            assertNull(controller.state.value.stageDisabledReason)
        } finally { controller.close() }
    }

    private suspend fun TestScope.loaded(scope: CoroutineScope, backend: StageControllerBackend): GitController {
        val controller = GitController(scope, backend)
        controller.bind(STAGE_BINDING)
        controller.selectRepository("/repo")
        runCurrent()
        assertTrue(controller.state.value.canStage, controller.state.value.stageDisabledReason)
        return controller
    }
}

private val STAGE_BINDING = GitBinding(null, HostId("stage-controller"), 1, GitAccess.LocalReadOnly)
private val STAGE_HEAD = "a".repeat(40)

/**
 * Real state machine over a mutable change list: `applyStage` moves the reviewed entry from worktree
 * to index (or back) and answers with a genuine reread, exactly as the desktop backend does.
 */
private class StageControllerBackend : GitBackend, GitStager {
    var changes: List<GitChange> = emptyList()
    var head: String? = STAGE_HEAD
    var statusReads = 0
    var commits = 0
    val requests = mutableListOf<GitStageRequest>()
    var action: suspend (GitRepository, GitStageRequest) -> GitStageResult = { repo, request -> applied(repo, request) }

    fun repositoryOf(cwd: String): GitRepository {
        val root = if (cwd.startsWith("/repo") || cwd == "/sibling") cwd.substringBefore('?').let {
            if (it.startsWith("/repo")) "/repo" else "/sibling"
        } else cwd
        return GitRepository(STAGE_BINDING, root, if (root == "/repo") "/common" else "/other-common", "$root/git")
    }
    fun statusOf(repo: GitRepository) = GitStatusSnapshot(changes.toList(), "refs/heads/main", head)
    fun applied(repo: GitRepository, request: GitStageRequest): GitStageResult.Applied {
        val path = request.target.path
        val target = request.target
        changes = changes.mapNotNull { change ->
            if (change.path != path) change else when {
                // A hunk leaves the other hunks unstaged, so both sides stay populated.
                target is GitStageTarget.Hunk -> if (request.action == GitStageAction.Stage)
                    GitChange(path, change.originalPath, 'M', 'M') else GitChange(path, change.originalPath, ' ', 'M')
                request.action == GitStageAction.Stage -> GitChange(path, change.originalPath,
                    if (change.untracked) 'A' else change.indexStatus.takeUnless { it == ' ' } ?: 'M', ' ')
                head == null -> null
                else -> GitChange(path, change.originalPath, ' ', 'M')
            }
        }
        return GitStageResult.Applied(GitStageReceipt(repo, request, statusOf(repo)))
    }

    override suspend fun discover(binding: GitBinding, cwd: String) = repositoryOf(cwd)
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        statusReads++
        return statusOf(repository)
    }
    override suspend fun branches(repository: GitRepository) =
        listOf(GitBranch("refs/heads/main", head ?: "0".repeat(40), true, false))
    override suspend fun worktrees(repository: GitRepository) =
        listOf(GitWorktree(repository.root, head, "refs/heads/main"))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest) =
        GitDiffSnapshot(request, "diff --git a/tracked b/tracked\n", head)
    override fun canStage(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun applyStage(repository: GitRepository, request: GitStageRequest): GitStageResult {
        requests += request
        return action(repository, request)
    }
}

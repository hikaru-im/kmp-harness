package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

/** Pure review-panel decisions: filter counts, empty-state choice and header statistics. */
@OptIn(ExperimentalCoroutinesApi::class)
class GitChangesPanelTest {
    private val untracked = GitChange("new.kt", indexStatus = '?', worktreeStatus = '?')
    private val worktreeOnly = GitChange("edited.kt", indexStatus = ' ', worktreeStatus = 'M')
    private val both = GitChange("both.kt", indexStatus = 'M', worktreeStatus = 'M')
    private val stagedOnly = GitChange("added.kt", indexStatus = 'A', worktreeStatus = ' ')
    private val conflicted = GitChange("conflict.kt", indexStatus = 'U', worktreeStatus = 'U')
    private val changes = listOf(untracked, worktreeOnly, both, stagedOnly, conflicted)

    @Test
    fun countsFollowTheStagedAndUnstagedHalvesIncludingOverlap() {
        val counts = gitChangesCounts(changes)
        assertEquals(4, counts.unstaged) // untracked + worktreeOnly + both + conflicted
        assertEquals(3, counts.staged)   // both + stagedOnly + conflicted
        assertEquals(GitChangesCounts(0, 0), gitChangesCounts(emptyList()))
    }

    @Test
    fun emptyStateDistinguishesACleanTreeFromAFilterThatHidesEverything() {
        // Nothing at all: the generic 「尚无文件更改」 hint, never a per-filter one.
        assertEquals(GitChangesEmpty.NoChanges, gitChangesEmpty(emptyList(), GitChangeFilter.Unstaged))
        assertEquals(GitChangesEmpty.NoChanges, gitChangesEmpty(emptyList(), GitChangeFilter.Staged))
        // Only staged work: the unstaged half is empty for a real reason.
        assertEquals(GitChangesEmpty.NoUnstaged, gitChangesEmpty(listOf(stagedOnly), GitChangeFilter.Unstaged))
        assertNull(gitChangesEmpty(listOf(stagedOnly), GitChangeFilter.Staged))
        // Only worktree work: the staged half is empty.
        assertEquals(GitChangesEmpty.NoStaged, gitChangesEmpty(listOf(worktreeOnly, untracked), GitChangeFilter.Staged))
        assertNull(gitChangesEmpty(listOf(worktreeOnly, untracked), GitChangeFilter.Unstaged))
    }

    @Test
    fun contentSeparatesUnreadableLoadingFailedEmptyAndList() {
        val ready = GitBinding(null, HostId("h"), 1, GitAccess.LocalReadOnly)
        val unavailable = gitChangesContent(GitState(binding = null))
        assertIs<GitChangesContent.Unavailable>(unavailable)
        // A bound-but-unread repository is loading, never an empty repository.
        assertIs<GitChangesContent.Loading>(gitChangesContent(GitState(binding = ready)))
        assertIs<GitChangesContent.Loading>(gitChangesContent(GitState(binding = ready, status = GitRead.Loading)))
        val failed = gitChangesContent(GitState(binding = ready,
            status = GitRead.Failed(GitFailure(GitFailureKind.CommandFailed, "git status 失败"))))
        assertEquals("git status 失败", assertIs<GitChangesContent.Failed>(failed).message)

        val stagedOnly48 = GitState(binding = ready, changeFilter = GitChangeFilter.Staged,
            status = GitRead.Ready(GitStatusSnapshot(listOf(both, stagedOnly), "refs/heads/main", "a".repeat(40))))
        val listed = assertIs<GitChangesContent.Changes>(gitChangesContent(stagedOnly48))
        assertEquals(listOf(both, stagedOnly), listed.visible)
        // A list where every entry is staged has an empty unstaged half.
        val stagedOnlyState = stagedOnly48.copy(
            status = GitRead.Ready(GitStatusSnapshot(listOf(stagedOnly), "refs/heads/main", "a".repeat(40))),
            changeFilter = GitChangeFilter.Unstaged)
        val emptyHalf = assertIs<GitChangesContent.Empty>(gitChangesContent(stagedOnlyState))
        assertEquals(GitChangesEmpty.NoUnstaged, emptyHalf.state)
        val wholeEmpty = assertIs<GitChangesContent.Empty>(gitChangesContent(stagedOnly48.copy(
            status = GitRead.Ready(GitStatusSnapshot(emptyList(), "refs/heads/main", "a".repeat(40))))))
        assertEquals(GitChangesEmpty.NoChanges, wholeEmpty.state)
    }

    @Test
    fun lineStatsComeFromTheParsedPatchAndStayAbsentWhenNothingChanged() {
        val patch = "diff --git a/a.kt b/a.kt\n--- a/a.kt\n+++ b/a.kt\n@@ -1,2 +1,3 @@\n one\n+two\n three\n"
        val document = parseGitDiff(patch)
        assertEquals("+1 −0", gitChangesLineStats(document))
        assertNull(gitChangesLineStats(parseGitDiff("")))
        assertNull(gitChangesLineStats(parseGitDiff(
            "diff --git a/a.kt b/a.kt\nold mode 100644\nnew mode 100755\n")))
        val both = parseGitDiff("diff --git a/a b/a\n--- a/a\n+++ b/a\n@@ -1 +1 @@\n-old\n+new\n")
        assertEquals("+1 −1", gitChangesLineStats(both))
    }

    @Test
    fun eachFilterReadsTheDiffHalfItFilters() {
        assertEquals(GitDiffSource.WorkingTree, GitChangeFilter.Unstaged.diffSource)
        assertEquals(GitDiffSource.Index, GitChangeFilter.Staged.diffSource)
    }

    @Test
    fun reviewReadIsItsOwnSlotAndNeverRewritesTheSettingsDiffRequest() = runTest {
        val backend = ReviewReadBackend()
        val controller = GitController(backgroundScope, backend)
        try {
            controller.bind(binding)
            controller.selectRepository("/repo")
            runCurrent()
            // The Settings page is configured for a branch comparison; review must not move it.
            controller.selectDiff(GitDiffRequest(GitDiffSource.BaseBranch, "main"))
            runCurrent()
            assertEquals(GitDiffSource.BaseBranch, backend.requests.last().source)
            controller.refreshReviewDiff()
            runCurrent()
            val review = assertIs<GitRead.Ready<GitDiffSnapshot>>(controller.state.value.reviewDiff).value
            assertEquals(GitDiffSource.WorkingTree, review.request.source)
            assertEquals(GitDiffRequest(GitDiffSource.BaseBranch, "main"), controller.state.value.diffRequest)
            assertEquals(GitDiffSource.WorkingTree, backend.requests.last().source)

            // Switching to the staged half reads the index without touching the Settings request.
            controller.selectChangeFilter(GitChangeFilter.Staged)
            controller.refreshReviewDiff()
            runCurrent()
            assertEquals(GitDiffSource.Index, backend.requests.last().source)
            assertEquals(GitDiffSource.Index,
                assertIs<GitRead.Ready<GitDiffSnapshot>>(controller.state.value.reviewDiff).value.request.source)
            assertEquals(GitDiffRequest(GitDiffSource.BaseBranch, "main"), controller.state.value.diffRequest)

            // An ordinary refresh re-reads both halves once each: the Settings request and the review slot.
            val before = backend.requests.size
            controller.refresh()
            runCurrent()
            val reread = backend.requests.drop(before)
            assertEquals(2, reread.size, "one Settings diff read and one review read")
            assertEquals(setOf(GitDiffSource.BaseBranch, GitDiffSource.Index), reread.map { it.source }.toSet())
            assertEquals(GitDiffRequest(GitDiffSource.BaseBranch, "main"), controller.state.value.diffRequest)
        } finally { controller.close() }
    }

    @Test
    fun refreshLeavesTheReviewSlotUnreadUntilThePanelAsksForIt() = runTest {
        val backend = ReviewReadBackend()
        val controller = GitController(backgroundScope, backend)
        try {
            controller.bind(binding)
            controller.selectRepository("/repo")
            runCurrent()
            assertIs<GitRead.Unselected>(controller.state.value.reviewDiff)
            val before = backend.requests.size
            controller.refresh()
            runCurrent()
            // Every refresh read is the Settings comparison; an unopened review tab costs no read.
            assertEquals(listOf(GitDiffSource.WorkingTree), backend.requests.drop(before).map { it.source })
            assertIs<GitRead.Unselected>(controller.state.value.reviewDiff)
        } finally { controller.close() }
    }
}

private val binding = GitBinding(null, HostId("review-panel"), 1, GitAccess.LocalReadOnly)

/** Records every diff request; nothing here writes. */
private class ReviewReadBackend : GitBackend {
    val requests = mutableListOf<GitDiffRequest>()
    private fun repository(cwd: String = "/repo") = GitRepository(binding, cwd, "$cwd/.git", "$cwd/.git")
    override suspend fun discover(binding: GitBinding, cwd: String) = repository(cwd)
    override suspend fun status(repository: GitRepository) =
        GitStatusSnapshot(listOf(GitChange("edited.kt", indexStatus = ' ', worktreeStatus = 'M')),
            "refs/heads/main", "a".repeat(40))
    override suspend fun branches(repository: GitRepository) =
        listOf(GitBranch("refs/heads/main", "a".repeat(40), true, false))
    override suspend fun worktrees(repository: GitRepository) = listOf(GitWorktree(repository.root, "a".repeat(40), "refs/heads/main"))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot {
        requests += request
        return GitDiffSnapshot(request, "diff --git a/edited.kt b/edited.kt\n", "a".repeat(40))
    }
}

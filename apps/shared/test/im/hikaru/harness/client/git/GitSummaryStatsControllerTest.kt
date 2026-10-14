package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GitSummaryStatsControllerTest {
    @Test
    fun readsHeadDiffAndCountsThePatchForTheSummaryRow() = runTest {
        val backend = SummaryBackend().apply {
            patch = "diff --git a/a.kt b/a.kt\n--- a/a.kt\n+++ b/a.kt\n@@ -1 +1,2 @@\n-old\n+new\n+extra\n" +
                "diff --git a/b.kt b/b.kt\n--- a/b.kt\n+++ b/b.kt\n@@ -1 +0,0 @@\n-gone\n"
        }
        val controller = loaded(backend)
        controller.refreshSummaryStats()
        runCurrent()
        assertEquals(GitDiffStats(2, 2, 2), ready(controller.state.value.summaryStats).value)
        // Base is the resolved HEAD commit; the request never carries a blank or option-like ref.
        assertEquals(GitDiffRequest(GitDiffSource.BaseBranch, "HEAD"), backend.diffRequests.last())
        controller.close()
    }

    @Test
    fun summaryStatsStaySeparateFromThePageDiff() = runTest {
        val backend = SummaryBackend().apply {
            patch = "diff --git a/a.kt b/a.kt\n--- a/a.kt\n+++ b/a.kt\n@@ -1 +1 @@\n-x\n+y\n"
        }
        val controller = loaded(backend)
        controller.refreshSummaryStats()
        runCurrent()
        assertEquals(1, ready(controller.state.value.summaryStats).value.fileCount)
        // The page-selected diff request is untouched by the summary read.
        assertEquals(GitDiffSource.WorkingTree, controller.state.value.diffRequest.source)
        assertEquals("page patch", ready(controller.state.value.diff).value.patch)
        controller.close()
    }

    @Test
    fun unbornHeadSkipsTheReadInsteadOfReportingZero() = runTest {
        val backend = SummaryBackend().apply { currentOid = null }
        val controller = loaded(backend)
        val before = backend.diffRequests.size
        controller.refreshSummaryStats()
        runCurrent()
        assertIs<GitRead.Unselected>(controller.state.value.summaryStats)
        assertEquals(before, backend.diffRequests.size)
        controller.close()
    }

    @Test
    fun noSelectedRepositoryMeansNoRead() = runTest {
        val backend = SummaryBackend()
        val controller = GitController(backgroundScope, backend)
        controller.bind(binding())
        runCurrent()
        controller.refreshSummaryStats()
        runCurrent()
        assertIs<GitRead.Unselected>(controller.state.value.summaryStats)
        assertTrue(backend.diffRequests.isEmpty())
        controller.close()
    }

    private suspend fun TestScope.loaded(backend: SummaryBackend): GitController {
        val controller = GitController(backgroundScope, backend)
        controller.bind(binding())
        controller.selectRepository("/repo")
        runCurrent()
        return controller
    }

    private fun binding() = GitBinding(null, HostId("summary-controller"), 1, GitAccess.LocalReadOnly)

    private fun <T> ready(read: GitRead<T>): GitRead.Ready<T> = assertIs(read)
}

private class SummaryBackend : GitBackend {
    val diffRequests = mutableListOf<GitDiffRequest>()
    var currentRef: String? = "refs/heads/main"
    var currentOid: String? = "a".repeat(40)
    var patch = "page patch"
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository =
        GitRepository(binding, cwd, "$cwd/.git", "$cwd/.git")
    override suspend fun status(repository: GitRepository): GitStatusSnapshot =
        GitStatusSnapshot(emptyList(), currentRef, currentOid)
    override suspend fun branches(repository: GitRepository): List<GitBranch> = emptyList()
    override suspend fun worktrees(repository: GitRepository): List<GitWorktree> =
        listOf(GitWorktree(repository.root, currentOid, currentRef))
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot {
        diffRequests += request
        val body = if (request.source == GitDiffSource.BaseBranch) patch else "page patch"
        return GitDiffSnapshot(request, body, currentOid)
    }
}

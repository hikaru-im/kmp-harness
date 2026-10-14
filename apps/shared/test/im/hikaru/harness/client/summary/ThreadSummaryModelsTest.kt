package im.hikaru.harness.client.summary

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.GitAccess
import im.hikaru.harness.client.git.GitBinding
import im.hikaru.harness.client.git.GitChange
import im.hikaru.harness.client.git.GitDiffStats
import im.hikaru.harness.client.git.GitFailure
import im.hikaru.harness.client.git.GitFailureKind
import im.hikaru.harness.client.git.GitRead
import im.hikaru.harness.client.git.GitRepository
import im.hikaru.harness.client.git.GitState
import im.hikaru.harness.client.git.GitStatusSnapshot
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.ScheduledDestination
import im.hikaru.harness.client.scheduling.ScheduledRule
import im.hikaru.harness.client.scheduling.ScheduledTarget
import im.hikaru.harness.client.scheduling.ScheduledTask
import im.hikaru.harness.client.scheduling.ScheduledTaskStatus
import kotlin.test.*

class ThreadSummaryModelsTest {
    @Test
    fun branchDisplayNameStripsOnlyTheLocalHeadsPrefix() {
        assertEquals("main", threadBranchDisplayName("refs/heads/main"))
        assertEquals("codex/feature", threadBranchDisplayName("refs/heads/codex/feature"))
        assertEquals("refs/remotes/origin/main", threadBranchDisplayName("refs/remotes/origin/main"))
        assertEquals(null, threadBranchDisplayName(null))
    }

    @Test
    fun unavailableBindingReportsReasonInsteadOfNumbers() {
        val load = threadSummaryEnvironment(GitState(binding = binding(GitAccess.RemoteUnavailable)))
        assertIs<ThreadSummaryLoad.Unavailable>(load)
    }

    @Test
    fun unreadRepositoryOrStatusIsLoading() {
        assertIs<ThreadSummaryLoad.Loading>(threadSummaryEnvironment(GitState(binding = binding())))
        assertIs<ThreadSummaryLoad.Loading>(
            threadSummaryEnvironment(
                GitState(
                    binding = binding(),
                    repository = GitRead.Ready(repository()),
                    status = GitRead.Unselected,
                ),
            ),
        )
    }

    @Test
    fun readyProjectionCarriesBranchStatsAndTrackedCount() {
        val load = threadSummaryEnvironment(
            GitState(
                binding = binding(),
                selectedCwd = "/work/repo",
                repository = GitRead.Ready(repository()),
                status = GitRead.Ready(
                    GitStatusSnapshot(
                        changes = listOf(
                            GitChange("src/a.kt", indexStatus = 'M', worktreeStatus = ' '),
                            GitChange("src/b.kt", indexStatus = ' ', worktreeStatus = 'M'),
                            GitChange("notes.txt", indexStatus = '?', worktreeStatus = '?'),
                        ),
                        branch = "refs/heads/codex/summary",
                        headOid = "a".repeat(40),
                    ),
                ),
                summaryStats = GitRead.Ready(GitDiffStats(fileCount = 2, additions = 7, deletions = 3)),
            ),
        )
        val environment = assertIs<ThreadSummaryLoad.Ready>(load).environment
        assertEquals("codex/summary", environment.branchName)
        assertFalse(environment.detached)
        assertFalse(environment.unborn)
        assertEquals("/work/repo", environment.root)
        assertEquals(GitDiffStats(2, 7, 3), environment.stats)
        assertFalse(environment.statsLoading)
        // Untracked entries are not "uncommitted tracked changes".
        assertEquals(2, environment.uncommittedTrackedCount)
    }

    @Test
    fun loadingStatsKeepNumbersAbsentButMarkTheRow() {
        val load = threadSummaryEnvironment(readyState(summaryStats = GitRead.Loading))
        val environment = assertIs<ThreadSummaryLoad.Ready>(load).environment
        assertNull(environment.stats)
        assertTrue(environment.statsLoading)
    }

    @Test
    fun failedStatsOmitNumbersWithoutCollapsingTheSection() {
        val load = threadSummaryEnvironment(
            readyState(summaryStats = GitRead.Failed(GitFailure(GitFailureKind.CommandFailed, "boom"))),
        )
        val environment = assertIs<ThreadSummaryLoad.Ready>(load).environment
        assertNull(environment.stats)
        assertFalse(environment.statsLoading)
        assertEquals("refs/heads/main", environment.branchName?.let { "refs/heads/$it" })
    }

    @Test
    fun detachedHeadAndUnbornBranchAreDistinct() {
        val detached = assertIs<ThreadSummaryLoad.Ready>(
            threadSummaryEnvironment(
                readyState(status = GitStatusSnapshot(emptyList(), null, "a".repeat(40))),
            ),
        ).environment
        assertNull(detached.branchName)
        assertTrue(detached.detached)
        assertFalse(detached.unborn)

        val unborn = assertIs<ThreadSummaryLoad.Ready>(
            threadSummaryEnvironment(
                readyState(status = GitStatusSnapshot(emptyList(), "refs/heads/main", null)),
            ),
        ).environment
        // An unborn branch still has a symbolic HEAD name; there is simply no commit to diff against.
        assertEquals("main", unborn.branchName)
        assertFalse(unborn.detached)
        assertTrue(unborn.unborn)
    }

    @Test
    fun scheduledSectionKeepsOnlyTasksBoundToTheOpenConversation() {
        val bound = scheduled("heartbeat", ScheduledTarget.Heartbeat(SessionId("session-a")))
        val other = scheduled("elsewhere", ScheduledTarget.Heartbeat(SessionId("session-b")))
        val projectless = scheduled("cron", ScheduledTarget.Cron(ScheduledDestination.Projectless))
        val all = listOf(bound, other, projectless)

        assertEquals(listOf(bound), threadSummaryScheduledTasks(all, SessionId("session-a")))
        // A cwd cron is never attributed to a conversation, and a missing session selects nothing.
        assertTrue(threadSummaryScheduledTasks(all, SessionId("session-c")).isEmpty())
        assertTrue(threadSummaryScheduledTasks(all, null).isEmpty())
        // The durable snapshot order is preserved.
        assertEquals(listOf(bound, scheduled("second", ScheduledTarget.Heartbeat(SessionId("session-a")))),
            threadSummaryScheduledTasks(
                listOf(bound, ScheduledTarget.Heartbeat(SessionId("session-a")).let { scheduled("second", it) }),
                SessionId("session-a"),
            ))
    }

    private fun scheduled(id: String, target: ScheduledTarget) = ScheduledTask(
        id = id, revision = 1, name = id, prompt = "prompt", target = target,
        schedule = ScheduledRule.Daily(9, 0, "Asia/Shanghai"), model = null,
        status = ScheduledTaskStatus.Active,
    )

    private fun binding(access: GitAccess = GitAccess.LocalReadOnly) =
        GitBinding(null, HostId("summary-test"), 1, access)

    private fun repository() = GitRepository(binding(), "/work/repo", "/work/repo/.git", "/work/repo/.git")

    private fun readyState(
        status: GitStatusSnapshot = GitStatusSnapshot(emptyList(), "refs/heads/main", "a".repeat(40)),
        summaryStats: GitRead<GitDiffStats> = GitRead.Unselected,
    ) = GitState(
        binding = binding(),
        selectedCwd = "/work/repo",
        repository = GitRead.Ready(repository()),
        status = GitRead.Ready(status),
        summaryStats = summaryStats,
    )
}

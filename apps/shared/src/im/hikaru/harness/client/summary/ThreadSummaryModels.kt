package im.hikaru.harness.client.summary

import im.hikaru.harness.client.git.GitDiffStats
import im.hikaru.harness.client.git.GitRead
import im.hikaru.harness.client.git.GitState
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.ScheduledTask
import im.hikaru.harness.client.scheduling.ScheduledTarget

/**
 * Scheduled tasks durably bound to one conversation, for the summary island's 定时任务 section
 * (Codex `chatgptConversations.summaryPanel` + `automations.scheduled.destination.label`). Only an exact
 * `Heartbeat` target counts: a projectless or cwd cron is never attributed to a conversation, and a
 * missing session selects nothing. The durable snapshot order is preserved so the list is stable.
 */
public fun threadSummaryScheduledTasks(tasks: List<ScheduledTask>, sessionId: SessionId?): List<ScheduledTask> =
    if (sessionId == null) emptyList()
    else tasks.filter { (it.target as? ScheduledTarget.Heartbeat)?.sessionId == sessionId }

/**
 * Display name for a Git HEAD ref. The backend reports full refs (`refs/heads/main`); the summary
 * section shows the branch short name, and never rewrites any other ref shape.
 */
public fun threadBranchDisplayName(ref: String?): String? = when {
    ref == null -> null
    ref.startsWith(LOCAL_BRANCH_PREFIX) -> ref.removePrefix(LOCAL_BRANCH_PREFIX)
    else -> ref
}

private const val LOCAL_BRANCH_PREFIX = "refs/heads/"

/**
 * Read-only projection of the bound [GitState] into the thread summary "Environment" section. Every
 * field comes from a real Git read; a missing read is reported as absent rather than as zero.
 */
public data class ThreadSummaryEnvironment(
    /** Short branch name, or null on a detached HEAD or an unborn branch. */
    val branchName: String?,
    val detached: Boolean,
    val unborn: Boolean,
    /** Absolute checkout root the numbers describe. */
    val root: String?,
    /** Null while the diff is still loading, unavailable, or there is no HEAD base. */
    val stats: GitDiffStats?,
    val statsLoading: Boolean,
    /** Tracked (staged + unstaged) files; untracked entries are counted separately by Git and excluded. */
    val uncommittedTrackedCount: Int,
) {
    init {
        require(uncommittedTrackedCount >= 0)
        require(!detached || branchName == null)
        require(!(detached && unborn))
    }
}

/** The Environment section is either still reading, real, or explicitly unavailable. */
public sealed interface ThreadSummaryLoad {
    /** No Git service for this Host, or the selected directory is not a repository. */
    public data class Unavailable(val reason: String) : ThreadSummaryLoad
    public data object Loading : ThreadSummaryLoad
    public data class Ready(val environment: ThreadSummaryEnvironment) : ThreadSummaryLoad
}

/**
 * Pure projection. [GitState.summaryStats] is deliberately separate from the page diff, so selecting a
 * diff on the Git page cannot change these numbers and vice versa.
 */
public fun threadSummaryEnvironment(state: GitState): ThreadSummaryLoad {
    state.unavailableReason?.let { return ThreadSummaryLoad.Unavailable(it) }
    val repository = when (val read = state.repository) {
        is GitRead.Ready -> read.value
        is GitRead.Failed -> return ThreadSummaryLoad.Unavailable(read.failure.message)
        GitRead.Loading, GitRead.Unselected -> return ThreadSummaryLoad.Loading
    }
    val status = when (val read = state.status) {
        is GitRead.Ready -> read.value
        is GitRead.Failed -> return ThreadSummaryLoad.Unavailable(read.failure.message)
        GitRead.Loading, GitRead.Unselected -> return ThreadSummaryLoad.Loading
    }
    // A failed or absent diff read omits the +/- badges; the section still shows the real branch and
    // uncommitted counts instead of collapsing to an error.
    val stats = (state.summaryStats as? GitRead.Ready<GitDiffStats>)?.value
    val statsLoading = state.summaryStats is GitRead.Loading
    return ThreadSummaryLoad.Ready(
        ThreadSummaryEnvironment(
            branchName = threadBranchDisplayName(status.branch),
            detached = status.detached,
            unborn = status.unborn,
            root = repository.root,
            stats = stats,
            statsLoading = statsLoading,
            uncommittedTrackedCount = status.changes.count { !it.untracked },
        ),
    )
}

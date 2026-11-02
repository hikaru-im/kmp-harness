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

/** Section titles and empty states, from Codex `locales/zh-cn.ts` (`codex.localConversation.*`). */
public const val ThreadSummaryPlanTitle: String = "计划"
public const val ThreadSummaryOutputsTitle: String = "输出"
public const val ThreadSummaryOutputsEmpty: String = "暂无产物"
public const val ThreadSummarySideChatsTitle: String = "侧边聊天"
public const val ThreadSummarySourcesTitle: String = "来源"
public const val ThreadSummarySourcesEmpty: String = "暂无来源"

/** A tool (MCP app) or web source the turn used, as one Sources chip. */
public enum class ThreadSummarySourceKind { Tool, Web }

public data class ThreadSummarySource(
    val id: String,
    val name: String,
    val kind: ThreadSummarySourceKind = ThreadSummarySourceKind.Tool,
    /** Plugin display names joined for a tool source, or the page title for a web source. */
    val detail: String? = null,
    val url: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(kind == ThreadSummarySourceKind.Web || url == null)
    }
}

/** One output artifact the turn produced (`summary-panel-artifacts-list.tsx`). */
public data class ThreadSummaryOutput(
    val id: String,
    val name: String,
    /** Repository-relative path or absolute file target, when the artifact names one. */
    val target: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
    }
}

/** The plan document attached to the conversation (`plan-summary-card`). */
public data class ThreadSummaryPlan(
    val title: String,
    val content: String,
) {
    init {
        require(title.isNotBlank())
        require(content.isNotBlank())
    }
}

/** A side chat opened from this conversation (`thread-summary-side-chat-rows.tsx`). */
public data class ThreadSummarySideChat(
    val tabId: String,
    val title: String,
    val inProgress: Boolean = false,
) {
    init {
        require(tabId.isNotBlank())
        require(title.isNotBlank())
    }
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

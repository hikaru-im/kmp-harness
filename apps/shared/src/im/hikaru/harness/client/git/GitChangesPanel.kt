package im.hikaru.harness.client.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.*

/**
 * Read-only projection of the thread side panel's Changes (审查) tab — Codex `ReviewTabBody`
 * (`review-side-panel-tab-content.tsx` + `review-content.tsx`): a stage-filter header with per-filter
 * counts, the changed-file list (`review-file-tree.tsx`) and the diff area.
 *
 * Nothing here writes: no stage/unstage, commit, checkout or worktree creation. The patch and the
 * file list are the ones the app-owned [GitController] already read; this panel never starts a read
 * on its own, and [onSelectFilter]/[onRefresh] are read-only controller calls supplied by the host.
 */
@Composable
public fun GitChangesPanel(
    state: GitState,
    modifier: Modifier = Modifier,
    onSelectFilter: (GitChangeFilter) -> Unit = {},
    onRefresh: () -> Unit = {},
    onRequestPreviews: (List<String>) -> Unit = {},
) {
    val filter = state.changeFilter
    val ready = (state.status as? GitRead.Ready<GitStatusSnapshot>)?.value
    val readyDiff = (state.reviewDiff as? GitRead.Ready<GitDiffSnapshot>)?.value
        ?.takeIf { it.request.source == filter.diffSource }
    val document = if (readyDiff != null) remember(readyDiff.patch) { parseGitDiff(readyDiff.patch) } else null
    Column(modifier.fillMaxSize().testTag("git-changes-panel")) {
        GitChangesHeader(state, ready?.changes.orEmpty(), document, onSelectFilter, onRefresh)
        when (val content = gitChangesContent(state)) {
            is GitChangesContent.Unavailable -> GitChangesError(content.reason, refresh = null)
            GitChangesContent.Loading -> HarnessText("正在加载更改", Modifier.padding(12.dp).testTag("git-changes-loading"),
                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            is GitChangesContent.Failed -> GitChangesError(content.message, onRefresh.takeIf { state.canRefresh })
            is GitChangesContent.Empty -> HarnessEmptyState(
                when (content.state) {
                    GitChangesEmpty.NoChanges -> "尚无文件更改"
                    GitChangesEmpty.NoUnstaged -> "无未暂存更改"
                    GitChangesEmpty.NoStaged -> "无暂存更改"
                },
                Modifier.fillMaxWidth().testTag("git-changes-empty"),
                description = when (content.state) {
                    GitChangesEmpty.NoChanges -> "此项目中的更改将显示在此处。"
                    GitChangesEmpty.NoUnstaged -> "代码更改将在此处显示"
                    GitChangesEmpty.NoStaged -> "接受编辑内容并暂存"
                },
            )
            is GitChangesContent.Changes -> {
                Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("git-changes-files")
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    content.visible.forEach { change -> GitChangesFileRow(change, document) }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) { GitChangesDiff(state, document, onRefresh, onRequestPreviews) }
            }
        }
    }
}

@Composable
private fun GitChangesHeader(
    state: GitState,
    changes: List<GitChange>,
    document: GitDiffDocument?,
    onSelectFilter: (GitChangeFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    val counts = gitChangesCounts(changes)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("git-changes-header"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        GitChangeFilter.entries.forEach { entry ->
            val count = if (entry == GitChangeFilter.Unstaged) counts.unstaged else counts.staged
            // `renderDiffFilterLabelWithCount`: the badge only appears when there is something to count.
            val label = if (count > 0) "${entry.label} $count" else entry.label
            HarnessButton(label, { onSelectFilter(entry) },
                testTag = "git-changes-filter-" + entry.name.lowercase(),
                variant = if (entry == state.changeFilter) HarnessButtonVariant.Secondary else HarnessButtonVariant.Ghost,
                size = HarnessControlSize.Compact)
        }
        val stats = document?.let { gitChangesLineStats(it) }
        if (stats != null) HarnessText(stats, Modifier.testTag("git-changes-stats"),
            style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        Spacer(Modifier.weight(1f))
        // `codex.review.refreshGitQueries`（刷新）/ `.inProgress`（正在刷新更改）.
        val refreshing = state.reviewDiff is GitRead.Loading || state.status is GitRead.Loading
        HarnessButton(if (refreshing) "正在刷新更改" else "刷新",
            onRefresh, testTag = "git-changes-refresh",
            enabled = state.canRefresh && !state.pageMutationBusy,
            variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact)
    }
}

@Composable
private fun GitChangesFileRow(change: GitChange, document: GitDiffDocument?) {
    val file = document?.files?.firstOrNull { it.path == change.path || it.originalPath == change.path }
    Row(Modifier.fillMaxWidth().testTag("git-changes-file-" + change.path),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(56.dp)) {
            GitStatusLabel(when {
                change.conflicted -> "冲突"
                change.untracked -> "未跟踪"
                'D' in "${change.indexStatus}${change.worktreeStatus}" -> "删除"
                'R' in "${change.indexStatus}${change.worktreeStatus}" -> "重命名"
                'A' in "${change.indexStatus}${change.worktreeStatus}" -> "新增"
                else -> "修改"
            })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HarnessText((change.originalPath?.let { "$it → " } ?: "") + change.path,
                color = HarnessTokens.Colors.Text, style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            if (file != null && (file.addedLines > 0 || file.deletedLines > 0)) HarnessText(
                "+${file.addedLines} −${file.deletedLines}", style = MaterialTheme.typography.bodySmall,
                color = HarnessTokens.Colors.TextMuted)
        }
    }
}

@Composable
private fun GitChangesDiff(
    state: GitState,
    document: GitDiffDocument?,
    onRefresh: () -> Unit,
    onRequestPreviews: (List<String>) -> Unit,
) {
    val diff = state.reviewDiff
    when {
        diff is GitRead.Failed -> HarnessText("无法加载差异",
            Modifier.padding(12.dp).testTag("git-changes-diff-failed"),
            color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall)
        document == null -> HarnessText("正在加载差异",
            Modifier.padding(12.dp).testTag("git-changes-diff-pending"),
            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        document.files.isEmpty() -> HarnessText("所选比较没有差异。",
            Modifier.padding(12.dp).testTag("git-changes-diff-empty"),
            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        // GitPatch already owns a bounded viewport with its own scroll; wrapping it in another
        // verticalScroll would nest two scrollable axes and swallow the diff's own gestures.
        else -> Box(Modifier.fillMaxSize().testTag("git-changes-diff")) {
            GitPatch(patch = (diff as? GitRead.Ready<GitDiffSnapshot>)?.value?.patch.orEmpty(),
                previewFiles = state.previewFiles,
                onRequestPreviews = onRequestPreviews,
                onRefresh = onRefresh,
                refreshDisabled = !state.canRefresh || state.pageMutationBusy,
                // Expansion/context widening is a diff-source feature; review keeps the patch it read.
                contextExpansionAllowed = false)
        }
    }
}

@Composable
private fun GitChangesError(message: String, refresh: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(12.dp).testTag("git-changes-error"),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HarnessText("无法加载更改", style = MaterialTheme.typography.titleSmall)
        HarnessText(message, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
        if (refresh != null) HarnessButton("刷新", refresh, testTag = "git-changes-error-refresh",
            variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact)
    }
}

/** Which of the review header's counts a change list produces. Pure: no repository access. */
public data class GitChangesCounts(val unstaged: Int, val staged: Int)

public fun gitChangesCounts(changes: List<GitChange>): GitChangesCounts = GitChangesCounts(
    unstaged = GitChangeFilter.Unstaged.count(changes),
    staged = GitChangeFilter.Staged.count(changes),
)

/** `codex.review.stageFilter.empty.*`: which empty message the current half of the list needs. */
public enum class GitChangesEmpty { NoChanges, NoUnstaged, NoStaged }

/**
 * Empty hint for the selected half, or null when the filtered list has entries. A repository with
 * no changes at all reads 「尚无文件更改」; a filter that hides every existing change gets the
 * matching `codex.review.stageFilter.empty.*` message instead of pretending the tree is clean.
 */
public fun gitChangesEmpty(changes: List<GitChange>, filter: GitChangeFilter): GitChangesEmpty? = when {
    changes.isEmpty() -> GitChangesEmpty.NoChanges
    filter.filter(changes).isEmpty() -> when (filter) {
        GitChangeFilter.Unstaged -> GitChangesEmpty.NoUnstaged
        GitChangeFilter.Staged -> GitChangesEmpty.NoStaged
    }
    else -> null
}

/** The panel's single content decision: unavailable, loading, failed, an empty hint, or the list. */
public sealed interface GitChangesContent {
    public data class Unavailable(val reason: String) : GitChangesContent
    public data object Loading : GitChangesContent
    public data class Failed(val message: String) : GitChangesContent
    public data class Empty(val state: GitChangesEmpty) : GitChangesContent
    public data class Changes(val visible: List<GitChange>) : GitChangesContent
}

/**
 * Review reads happen through the app-owned controller, so the panel never invents a state: an
 * unread status is [GitChangesContent.Loading], a failed read keeps the real failure message, and
 * the change list is always the controller's own snapshot filtered by the selected stage filter.
 */
public fun gitChangesContent(state: GitState): GitChangesContent {
    state.unavailableReason?.let { return GitChangesContent.Unavailable(it) }
    val status = state.status
    return when (status) {
        GitRead.Unselected -> GitChangesContent.Loading
        GitRead.Loading -> GitChangesContent.Loading
        is GitRead.Failed -> GitChangesContent.Failed(status.failure.message)
        is GitRead.Ready -> {
            val changes = status.value.changes
            val empty = gitChangesEmpty(changes, state.changeFilter)
            if (empty != null) GitChangesContent.Empty(empty) else GitChangesContent.Changes(state.changeFilter.filter(changes))
        }
    }
}

/** 「+N −M」 for the header, counted from the patch the backend returned; null when it is all zero. */
public fun gitChangesLineStats(document: GitDiffDocument): String? =
    if (document.addedLines == 0 && document.deletedLines == 0) null
    else "+${document.addedLines} −${document.deletedLines}"

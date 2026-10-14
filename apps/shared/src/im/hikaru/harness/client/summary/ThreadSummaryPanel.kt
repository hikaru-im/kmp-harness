package im.hikaru.harness.client.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.git.GitDiffStats
import im.hikaru.harness.client.scheduling.ScheduledTask
import im.hikaru.harness.client.scheduling.scheduledSummaryCadence
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconButton
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessButtonState
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * Thread summary island. The Environment section is a read-only projection of real local Git reads:
 * the current branch, the uncommitted-to-HEAD +/- counts, and the tracked-change file count. It never
 * opens a shell, stages, checks out or invents numbers. Null consumers hide their row rather than
 * drawing a dead control.
 */
@Composable
public fun ThreadSummaryPanel(
    load: ThreadSummaryLoad,
    onOpenReview: (() -> Unit)?,
    onRefresh: (() -> Unit)?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subagents: List<ThreadSummaryActivity> = emptyList(),
    processes: List<ThreadSummaryActivity> = emptyList(),
    onOpenActivity: (ThreadSummaryActivity) -> Unit = {},
    /** True while the processed-terminal stop-all request is in flight. */
    processesStopping: Boolean = false,
    /** Last stop-all outcome that was not applied, exactly as the service reported it. */
    processesNotice: String? = null,
    /** Non-null shows the original island's `停止所有后台终端` action on every process row. */
    onStopProcesses: (() -> Unit)? = null,
    /** Scheduled tasks durably bound to this conversation (Codex summary island 定时任务 section). */
    scheduledTasks: List<ScheduledTask> = emptyList(),
    /** True while the scheduled store is still reading for this binding. */
    scheduledLoading: Boolean = false,
    /** True when the scheduled read failed; the section then offers its reload action. */
    scheduledError: Boolean = false,
    onOpenScheduled: ((String) -> Unit)? = null,
    onRetryScheduled: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(HarnessTokens.Colors.Surface)
            .semantics { testTag = "thread-summary-panel" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        ) {
            HarnessText(
                "摘要",
                modifier = Modifier.semantics { testTag = "thread-summary-title" },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            if (onRefresh != null) {
                HarnessIconButton("刷新摘要", onRefresh) {
                    HarnessIcon(HarnessIconName.Refresh, tint = HarnessTokens.Colors.TextMuted, size = 16.dp)
                }
            }
            if (onClose != null) {
                HarnessIconButton("关闭摘要", onClose) {
                    HarnessIcon(HarnessIconName.Close, tint = HarnessTokens.Colors.TextMuted, size = 16.dp)
                }
            }
        }
        HorizontalDivider(color = HarnessTokens.Colors.BorderSubtle)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        ) {
            when (load) {
                is ThreadSummaryLoad.Ready -> EnvironmentSection(load.environment, onOpenReview)
                ThreadSummaryLoad.Loading -> SummaryMessage("正在读取本机 Git 状态…", "thread-summary-loading")
                is ThreadSummaryLoad.Unavailable -> SummaryMessage(load.reason, "thread-summary-unavailable")
            }
            if (subagents.isNotEmpty()) {
                ActivitySection(ThreadSummarySubagentsTitle, "subagents", subagents, onOpenActivity)
            }
            if (processes.isNotEmpty()) {
                ActivitySection(
                    title = ThreadSummaryProcessesTitle,
                    slug = "processes",
                    items = processes,
                    onOpen = onOpenActivity,
                    stopping = processesStopping,
                    onStop = onStopProcesses,
                )
                processesNotice?.let {
                    HarnessText(
                        it,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics { testTag = "thread-summary-processes-notice" },
                        color = HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (scheduledTasks.isNotEmpty() || scheduledLoading || scheduledError) {
                ScheduledSection(scheduledTasks, scheduledLoading, scheduledError, onOpenScheduled, onRetryScheduled)
            }
        }
    }
}

/**
 * Conversation-bound scheduled tasks, mirroring the original summary island's `automations.scheduled
 * .destination.label` section: a title with the row count, then one icon + name + compact-cadence row
 * per task, plus the `重试加载已安排的任务` action when the scheduled read failed. A loading section with
 * no cached rows shows a reading line instead of an empty title. Clicking a row opens that task.
 */
@Composable
private fun ScheduledSection(
    tasks: List<ScheduledTask>,
    loading: Boolean,
    error: Boolean,
    onOpen: ((String) -> Unit)?,
    onRetry: (() -> Unit)?,
) {
    if (loading && tasks.isEmpty() && !error) {
        SummaryMessage("正在读取定时任务…", "thread-summary-scheduled-loading")
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { testTag = "thread-summary-section-scheduled" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
    ) {
        HarnessText(
            ThreadSummaryScheduledTitle,
            color = HarnessTokens.Colors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
        HarnessText(
            tasks.size.toString(),
            modifier = Modifier.semantics { testTag = "thread-summary-section-scheduled-count" },
            color = HarnessTokens.Colors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    tasks.forEach { task ->
        SummaryRow(
            title = task.name,
            leading = { HarnessIcon(HarnessIconName.Clock, tint = HarnessTokens.Colors.TextMuted, size = 14.dp) },
            trailing = {
                HarnessText(
                    scheduledSummaryCadence(task),
                    color = HarnessTokens.Colors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            testTag = "thread-summary-scheduled-" + task.id,
            onClick = onOpen?.let { open -> { open(task.id) } },
        )
    }
    if (error) {
        SummaryRow(
            title = ThreadSummaryScheduledRetryLabel,
            leading = { HarnessIcon(HarnessIconName.Refresh, tint = HarnessTokens.Colors.TextMuted, size = 14.dp) },
            testTag = "thread-summary-scheduled-retry",
            onClick = onRetry,
        )
    }
}

@Composable
private fun ActivitySection(
    title: String,
    slug: String,
    items: List<ThreadSummaryActivity>,
    onOpen: (ThreadSummaryActivity) -> Unit,
    stopping: Boolean = false,
    onStop: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { testTag = "thread-summary-section-$slug" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
    ) {
        HarnessText(title, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        HarnessText(
            items.size.toString(),
            modifier = Modifier.semantics { testTag = "thread-summary-section-$slug-count" },
            color = HarnessTokens.Colors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    items.forEach { item -> ActivityRow(item, onOpen, stopping, onStop) }
}

@Composable
private fun ActivityRow(
    item: ThreadSummaryActivity,
    onOpen: (ThreadSummaryActivity) -> Unit,
    stopping: Boolean = false,
    onStop: (() -> Unit)? = null,
) {
    val slug = if (item.kind == ThreadSummaryActivityKind.Subagent) "subagent" else "process"
    val row = @Composable {
        SummaryRow(
            title = item.label,
            leading = {
                HarnessIcon(
                    if (item.kind == ThreadSummaryActivityKind.Subagent) HarnessIconName.User else HarnessIconName.ChevronRight,
                    tint = HarnessTokens.Colors.TextMuted,
                    size = 14.dp,
                )
            },
            trailing = item.meta?.let { meta ->
                {
                    HarnessText(
                        meta,
                        color = if (item.running) HarnessTokens.Colors.Success else HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            testTag = "thread-summary-$slug-${item.id}",
            onClick = { onOpen(item) },
        )
    }
    if (slug == "process" && onStop != null) {
        // The original island attaches its one stop-all action to every terminal row.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { row() }
            HarnessIconButton(
                contentDescription = ThreadSummaryStopAllLabel,
                onClick = onStop,
                state = HarnessButtonState(enabled = !stopping),
                modifier = Modifier.padding(end = 4.dp),
                testTag = "thread-summary-process-stop-${item.id}",
            ) {
                HarnessIcon(HarnessIconName.Stop, tint = HarnessTokens.Colors.TextMuted, size = 14.dp)
            }
        }
    } else {
        row()
    }
}

@Composable
private fun EnvironmentSection(environment: ThreadSummaryEnvironment, onOpenReview: (() -> Unit)?) {
    HarnessText(
        "环境",
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { testTag = "thread-summary-section-environment" },
        color = HarnessTokens.Colors.TextMuted,
        style = MaterialTheme.typography.labelMedium,
    )
    ChangesRow(environment.stats, environment.statsLoading, onOpenReview)
    val branchLabel = environment.branchName ?: if (environment.detached) "Detached HEAD" else null
    if (branchLabel != null) BranchRow(branchLabel, copyable = environment.branchName != null)
    if (environment.uncommittedTrackedCount > 0) {
        SummaryRow("未提交：" + environment.uncommittedTrackedCount + " 个文件", null, "thread-summary-uncommitted")
    }
}

@Composable
private fun ChangesRow(stats: GitDiffStats?, loading: Boolean, onOpenReview: (() -> Unit)?) {
    SummaryRow(
        title = "变更",
        trailing = {
            when {
                loading -> HarnessText(
                    "…",
                    modifier = Modifier.semantics { testTag = "thread-summary-changes-loading" },
                    color = HarnessTokens.Colors.TextMuted,
                )
                stats != null -> DiffStatsBadge(stats)
            }
        },
        testTag = "thread-summary-changes",
        onClick = onOpenReview,
    )
}

@Composable
private fun DiffStatsBadge(stats: GitDiffStats) {
    Row(
        modifier = Modifier.semantics { testTag = "thread-summary-changes-stats" },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText("+" + stats.additions, color = HarnessTokens.Colors.Success, style = MaterialTheme.typography.labelMedium)
        HarnessText("−" + stats.deletions, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BranchRow(branchName: String, copyable: Boolean) {
    val clipboard = LocalClipboardManager.current
    SummaryRow(
        title = branchName,
        leading = { HarnessIcon(HarnessIconName.Copy, tint = HarnessTokens.Colors.TextMuted, size = 14.dp) },
        testTag = "thread-summary-branch",
        onClick = if (copyable) {
            { clipboard.setText(AnnotatedString(branchName)) }
        } else {
            null
        },
    )
}

@Composable
private fun SummaryRow(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(HarnessTokens.Corners.Medium))
    // Merged rows expose the title and the trailing numbers as one text for assertions and screen
    // readers; interactive rows also announce themselves as buttons.
    val interactive = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        modifier = interactive.padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                this.testTag = testTag
                if (onClick != null) role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
    ) {
        leading?.invoke()
        HarnessText(title, modifier = Modifier.weight(1f), maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(HarnessTokens.Spacing.One))
            trailing()
        }
    }
}

@Composable
private fun SummaryMessage(message: String, testTag: String) {
    HarnessText(
        message,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).semantics { this.testTag = testTag },
        color = HarnessTokens.Colors.TextMuted,
    )
}

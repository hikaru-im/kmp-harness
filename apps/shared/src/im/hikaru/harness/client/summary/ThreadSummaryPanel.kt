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
import androidx.compose.runtime.remember
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
import im.hikaru.harness.client.panels.ChildDiffStats
import im.hikaru.harness.client.scheduling.ScheduledTask
import im.hikaru.harness.client.scheduling.scheduledSummaryCadence
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconButton
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessButtonState
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * Thread summary island. Sections follow Codex `thread-summary-panel-sections.tsx`: automation, the
 * Git environment summary, plan, outputs, side chats, background agents, background tasks and
 * sources. The Environment section is a read-only projection of real local Git reads: the current
 * branch, the uncommitted-to-HEAD +/- counts, and the tracked-change file count. It never opens a
 * shell, stages, checks out or invents numbers. Null consumers hide their row rather than drawing a
 * dead control, and a section with no real data source stays hidden instead of fabricating rows.
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
    /** The conversation's plan document; null hides the Plan section. */
    plan: ThreadSummaryPlan? = null,
    /** Artifacts the turn produced. The section shows its empty state when this is empty. */
    outputs: List<ThreadSummaryOutput> = emptyList(),
    sideChats: List<ThreadSummarySideChat> = emptyList(),
    /** Tool and web sources. The section always renders and shows its empty state when this is empty. */
    sources: List<ThreadSummarySource> = emptyList(),
    onOpenSideChat: ((ThreadSummarySideChat) -> Unit)? = null,
    onOpenSource: ((ThreadSummarySource) -> Unit)? = null,
    /** Hoist this to keep expansion across panel close/open; defaults to panel-local memory. */
    sectionState: ThreadSummarySectionState? = null,
    autoCollapseDelayMillis: Long = ThreadSummarySectionAutoCollapseDelayMillis,
) {
    val sections = sectionState ?: remember { ThreadSummarySectionState() }
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
            // Codex `thread-summary-panel-sections.tsx` order: automation, git summary, plan,
            // outputs, side chats, background agents, background tasks, sources.
            if (scheduledTasks.isNotEmpty() || scheduledLoading || scheduledError) {
                ScheduledSection(
                    tasks = scheduledTasks,
                    loading = scheduledLoading,
                    error = scheduledError,
                    onOpen = onOpenScheduled,
                    onRetry = onRetryScheduled,
                    state = sections,
                    autoCollapseDelayMillis = autoCollapseDelayMillis,
                )
            }
            when (load) {
                is ThreadSummaryLoad.Ready -> EnvironmentSection(load.environment, onOpenReview)
                ThreadSummaryLoad.Loading -> SummaryMessage("正在读取本机 Git 状态…", "thread-summary-loading")
                is ThreadSummaryLoad.Unavailable -> SummaryMessage(load.reason, "thread-summary-unavailable")
            }
            plan?.let { PlanSection(it, sections, autoCollapseDelayMillis) }
            // Codex renders outputs instead of the Git summary for a conversation without a Git
            // workspace; the harness only knows that case as an unavailable Git read.
            if (load is ThreadSummaryLoad.Unavailable) {
                OutputsSection(outputs, sections, autoCollapseDelayMillis)
            }
            if (sideChats.isNotEmpty()) {
                SideChatsSection(sideChats, onOpenSideChat, sections, autoCollapseDelayMillis)
            }
            if (subagents.isNotEmpty()) {
                ActivitySection(
                    title = ThreadSummarySubagentsTitle,
                    slug = "subagents",
                    items = subagents,
                    onOpen = onOpenActivity,
                    state = sections,
                    autoCollapse = subagents.none { it.running },
                    autoCollapseDelayMillis = autoCollapseDelayMillis,
                )
            }
            if (processes.isNotEmpty()) {
                ActivitySection(
                    title = ThreadSummaryProcessesTitle,
                    slug = "processes",
                    items = processes,
                    onOpen = onOpenActivity,
                    stopping = processesStopping,
                    onStop = onStopProcesses,
                    state = sections,
                    autoCollapse = processes.none { it.running },
                    autoCollapseDelayMillis = autoCollapseDelayMillis,
                    footer = processesNotice?.let { notice ->
                        {
                            HarnessText(
                                notice,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    .semantics { testTag = "thread-summary-processes-notice" },
                                color = HarnessTokens.Colors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                )
            }
            SourcesSection(sources, onOpenSource, sections, autoCollapseDelayMillis)
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
    state: ThreadSummarySectionState,
    autoCollapseDelayMillis: Long,
) {
    if (loading && tasks.isEmpty() && !error) {
        SummaryMessage("正在读取定时任务…", "thread-summary-scheduled-loading")
        return
    }
    ThreadSummarySection(
        sectionKey = "scheduled",
        title = ThreadSummaryScheduledTitle,
        state = state,
        count = tasks.size,
        autoCollapseDelayMillis = autoCollapseDelayMillis,
    ) {
        tasks.forEach { task ->
            SummaryRow(
                title = task.name,
                leading = {
                    HarnessIcon(HarnessIconName.Clock, tint = HarnessTokens.Colors.TextMuted, size = 14.dp)
                },
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
                leading = {
                    HarnessIcon(HarnessIconName.Refresh, tint = HarnessTokens.Colors.TextMuted, size = 14.dp)
                },
                testTag = "thread-summary-scheduled-retry",
                onClick = onRetry,
            )
        }
    }
}

/** The plan document Codex's `plan-summary-card` renders inside its own summary section. */
@Composable
private fun PlanSection(
    plan: ThreadSummaryPlan,
    state: ThreadSummarySectionState,
    autoCollapseDelayMillis: Long,
) {
    ThreadSummarySection(
        sectionKey = "plan",
        title = ThreadSummaryPlanTitle,
        state = state,
        autoCollapseDelayMillis = autoCollapseDelayMillis,
    ) {
        HarnessText(
            plan.title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { testTag = "thread-summary-plan-title" },
            style = MaterialTheme.typography.bodyMedium,
        )
        HarnessText(
            plan.content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { testTag = "thread-summary-plan-content" },
            color = HarnessTokens.Colors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 8,
        )
    }
}

/**
 * Output artifacts. Codex shows this section only for a conversation without a Git summary
 * (`outputsSection` is rendered when `!isGitWorkspace`), and its list owns the `暂无产物` empty state.
 */
@Composable
private fun OutputsSection(
    outputs: List<ThreadSummaryOutput>,
    state: ThreadSummarySectionState,
    autoCollapseDelayMillis: Long,
) {
    ThreadSummarySection(
        sectionKey = "artifacts",
        title = ThreadSummaryOutputsTitle,
        state = state,
        count = outputs.size,
        autoCollapseDelayMillis = autoCollapseDelayMillis,
    ) {
        if (outputs.isEmpty()) {
            HarnessText(
                ThreadSummaryOutputsEmpty,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { testTag = "thread-summary-outputs-empty" },
                color = HarnessTokens.Colors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        outputs.forEach { output ->
            SummaryRow(
                title = output.name,
                leading = {
                    HarnessIcon(HarnessIconName.Folder, tint = HarnessTokens.Colors.TextMuted, size = 14.dp)
                },
                trailing = output.target?.takeIf { it != output.name }?.let { target ->
                    {
                        HarnessText(
                            target,
                            color = HarnessTokens.Colors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                },
                testTag = "thread-summary-output-" + output.id,
            )
        }
    }
}

@Composable
private fun SideChatsSection(
    sideChats: List<ThreadSummarySideChat>,
    onOpen: ((ThreadSummarySideChat) -> Unit)?,
    state: ThreadSummarySectionState,
    autoCollapseDelayMillis: Long,
) {
    ThreadSummarySection(
        sectionKey = "side-chats",
        title = ThreadSummarySideChatsTitle,
        state = state,
        count = sideChats.size,
        autoCollapseDelayMillis = autoCollapseDelayMillis,
    ) {
        sideChats.forEach { sideChat ->
            SummaryRow(
                title = sideChat.title,
                leading = {
                    HarnessIcon(
                        if (sideChat.inProgress) HarnessIconName.Clock else HarnessIconName.ChatBubble,
                        tint = HarnessTokens.Colors.TextMuted,
                        size = 14.dp,
                    )
                },
                testTag = "thread-summary-side-chat-" + sideChat.tabId,
                onClick = onOpen?.let { open -> { open(sideChat) } },
            )
        }
    }
}

/**
 * Tool and web sources. Codex always renders this section and lets the row list own the `暂无来源`
 * empty state (`thread-summary-source-rows.tsx`).
 */
@Composable
private fun SourcesSection(
    sources: List<ThreadSummarySource>,
    onOpen: ((ThreadSummarySource) -> Unit)?,
    state: ThreadSummarySectionState,
    autoCollapseDelayMillis: Long,
) {
    ThreadSummarySection(
        sectionKey = "sources",
        title = ThreadSummarySourcesTitle,
        state = state,
        count = sources.size,
        autoCollapseDelayMillis = autoCollapseDelayMillis,
        isLast = true,
    ) {
        if (sources.isEmpty()) {
            HarnessText(
                ThreadSummarySourcesEmpty,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { testTag = "thread-summary-sources-empty" },
                color = HarnessTokens.Colors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        sources.forEach { source ->
            SummaryRow(
                title = source.name,
                leading = {
                    HarnessIcon(HarnessIconName.ExternalLink, tint = HarnessTokens.Colors.TextMuted, size = 14.dp)
                },
                trailing = source.detail?.let { detail ->
                    {
                        HarnessText(
                            detail,
                            color = HarnessTokens.Colors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                },
                testTag = "thread-summary-source-" + source.id,
                onClick = onOpen?.let { open -> { open(source) } },
            )
        }
    }
}

@Composable
private fun ActivitySection(
    title: String,
    slug: String,
    items: List<ThreadSummaryActivity>,
    onOpen: (ThreadSummaryActivity) -> Unit,
    state: ThreadSummarySectionState,
    stopping: Boolean = false,
    onStop: (() -> Unit)? = null,
    autoCollapse: Boolean = false,
    autoCollapseDelayMillis: Long = ThreadSummarySectionAutoCollapseDelayMillis,
    footer: (@Composable () -> Unit)? = null,
) {
    ThreadSummarySection(
        sectionKey = slug,
        title = title,
        state = state,
        count = items.size,
        autoCollapse = autoCollapse,
        autoCollapseDelayMillis = autoCollapseDelayMillis,
    ) {
        items.forEach { item -> ActivityRow(item, onOpen, stopping, onStop) }
        footer?.invoke()
    }
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
            trailing = if (item.meta == null && item.diffStats == null) null else {
                {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.meta?.let { meta ->
                            HarnessText(
                                meta,
                                color = if (item.running) HarnessTokens.Colors.Success else HarnessTokens.Colors.TextMuted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        item.diffStats?.let { stats ->
                            DiffStatsBadge(stats, "thread-summary-$slug-${item.id}-stats")
                        }
                    }
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
private fun DiffStatsBadge(stats: GitDiffStats) =
    DiffStatsBadge(stats.additions, stats.deletions, "thread-summary-changes-stats")

@Composable
private fun DiffStatsBadge(stats: ChildDiffStats, tag: String) =
    DiffStatsBadge(stats.linesAdded, stats.linesRemoved, tag)

@Composable
private fun DiffStatsBadge(additions: Int, deletions: Int, tag: String) {
    Row(
        modifier = Modifier.semantics { testTag = tag },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessText("+" + additions, color = HarnessTokens.Colors.Success, style = MaterialTheme.typography.labelMedium)
        HarnessText("−" + deletions, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.labelMedium)
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

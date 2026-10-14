package im.hikaru.harness.client.summary

import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.panels.BackgroundTerminalSummary
import im.hikaru.harness.client.panels.ChildAgentStatus
import im.hikaru.harness.client.panels.ChildAgentSummary
import im.hikaru.harness.client.panels.PanelLoad
import im.hikaru.harness.client.panels.SubagentOverview

/**
 * The two background activity kinds the thread summary island lists next to the Environment rows.
 * They mirror the original `background-subagents` and `background-tasks` sections.
 */
public enum class ThreadSummaryActivityKind { Subagent, Process }

/**
 * One row of a background activity section. Every field is a projection of a real service read; a
 * section with no rows is hidden rather than filled with placeholder text.
 */
public data class ThreadSummaryActivity(
    val kind: ThreadSummaryActivityKind,
    val id: String,
    val label: String,
    val meta: String? = null,
    val running: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Activity id must not be blank" }
        require(label.isNotBlank()) { "Activity label must not be blank" }
        require(meta == null || meta.isNotBlank()) { "Activity meta must not be blank" }
    }
}

/** `codex.localConversation.backgroundTasks.title.subagents`. */
public const val ThreadSummarySubagentsTitle: String = "子智能体"

/** `codex.localConversation.backgroundTasks.title.backgroundProcesses`. */
public const val ThreadSummaryProcessesTitle: String = "后台进程"

/** `codex.localConversation.backgroundAgents.activeLabel`, shown for a working or waiting child. */
public const val ThreadSummaryRunningLabel: String = "运行中"

/** `codex.localConversation.backgroundTerminals.defaultLabel`, the row label without a command. */
public const val ThreadSummaryBackgroundTerminalLabel: String = "后台终端"

/** `codex.localConversation.backgroundTerminals.stop`, the summary stop-all action label. */
public const val ThreadSummaryStopAllLabel: String = "停止所有后台终端"

/** `automations.scheduled.destination.label`, the conversation summary's scheduled-task section title. */
public const val ThreadSummaryScheduledTitle: String = "定时任务"

/** `chatgptConversations.summaryPanel.scheduled.retry`, the section's reload action. */
public const val ThreadSummaryScheduledRetryLabel: String = "重试加载已安排的任务"

private const val TERMINAL_LABEL_LIMIT = 120

/**
 * Children of the bound conversation from a successful descendants read. An unavailable, loading,
 * empty, or errored read yields no rows, so the island draws nothing instead of inventing agents.
 * Running (active or waiting) children lead, then the rest by recency.
 */
public fun threadSummarySubagents(load: PanelLoad<SubagentOverview>): List<ThreadSummaryActivity> {
    val overview = (load as? PanelLoad.Ready)?.value ?: return emptyList()
    return overview.children
        .sortedWith(
            compareByDescending<ChildAgentSummary> { it.isRunning() }
                .thenByDescending { it.recencyAtEpochMillis ?: 0L },
        )
        .map { child ->
            ThreadSummaryActivity(
                kind = ThreadSummaryActivityKind.Subagent,
                id = child.key.sessionId.value,
                label = child.label,
                meta = if (child.isRunning()) ThreadSummaryRunningLabel else null,
                running = child.isRunning(),
            )
        }
}

private fun ChildAgentSummary.isRunning(): Boolean =
    status == ChildAgentStatus.Active || status == ChildAgentStatus.Waiting

/**
 * Finite producer inventory for the bound conversation; never an OS process scan. The row label is
 * the command's first non-blank line, or the original fallback when the command is unavailable.
 */
public fun threadSummaryProcesses(inventory: List<BackgroundTerminalSummary>): List<ThreadSummaryActivity> =
    inventory.map { summary ->
        ThreadSummaryActivity(
            kind = ThreadSummaryActivityKind.Process,
            id = summary.key.processId,
            label = summary.command?.let(::commandLabel) ?: ThreadSummaryBackgroundTerminalLabel,
            running = summary.lifecycle is BackgroundProcessLifecycle.Running,
        )
    }

private fun commandLabel(command: String): String? =
    command.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }?.take(TERMINAL_LABEL_LIMIT)

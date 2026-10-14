package im.hikaru.harness.client.scheduling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.*

/** The header stays visible while the task list scrolls independently. */
@Composable
public fun ScheduledManagementNavigator(
    state: ScheduledManagementState, query: String, onQueryChange: (String) -> Unit,
    onNew: () -> Unit, onSelect: (String) -> Unit, onRetry: () -> Unit,
    calendar: ScheduleCalendar? = null, nowEpochMillis: Long = 0,
    statusFilter: ScheduledTaskStatus? = null, onStatusFilterChange: ((ScheduledTaskStatus?) -> Unit)? = null,
    sort: ScheduledTaskSort = ScheduledTaskSort.Automatic, onSortChange: ((ScheduledTaskSort) -> Unit)? = null,
) {
    val latestState by rememberUpdatedState(state)
    fun current(): Boolean = latestState.scope == state.scope && latestState.context == state.context &&
        latestState.connected == state.connected && latestState.phase == state.phase &&
        latestState.draft == state.draft && latestState.mutation == state.mutation
    var searchOpen by remember(state.scope) { mutableStateOf(query.isNotBlank()) }
    var filterOpen by remember(state.scope) { mutableStateOf(false) }
    val ready = state.connected && state.phase == ScheduledLoadPhase.Ready
    val selectable = ready && state.draft == null && state.mutation == null
    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.Three)
            .semantics { testTag = "scheduled-management-navigator" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().semantics { testTag = "scheduled-navigator-header" },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Two),
                verticalAlignment = Alignment.CenterVertically) {
                HarnessText("定时任务", Modifier.weight(1f))
                if (onStatusFilterChange != null || onSortChange != null) {
                    HarnessIconButton("筛选任务", { if (current()) filterOpen = !filterOpen },
                        state = HarnessButtonState(selected = filterOpen || statusFilter != null ||
                            sort != ScheduledTaskSort.Automatic),
                        testTag = "scheduled-toggle-filter") {
                        HarnessIcon(HarnessIconName.Filter, tint = HarnessTokens.Colors.TextMuted)
                    }
                }
                HarnessIconButton("搜索任务", { if (current()) searchOpen = !searchOpen },
                    state = HarnessButtonState(), testTag = "scheduled-toggle-search") {
                    HarnessIcon(HarnessIconName.Search, tint = HarnessTokens.Colors.TextMuted)
                }
            }
            if (searchOpen) HarnessSearchField(query, { if (current()) onQueryChange(it) },
                "搜索任务", "搜索任务", Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Two),
                testTag = "scheduled-search")
            if (filterOpen && (onStatusFilterChange != null || onSortChange != null)) Column(
                Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Two),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            ) {
                if (onStatusFilterChange != null) {
                    HarnessText("状态", color = HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Row(Modifier.fillMaxWidth().semantics { testTag = "scheduled-status-filter" },
                        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                        NavigatorFilterOption("全部", statusFilter == null, "scheduled-filter-all") {
                            if (current()) onStatusFilterChange(null)
                        }
                        NavigatorFilterOption("已启用", statusFilter == ScheduledTaskStatus.Active, "scheduled-filter-active") {
                            if (current()) onStatusFilterChange(ScheduledTaskStatus.Active)
                        }
                        NavigatorFilterOption("已暂停", statusFilter == ScheduledTaskStatus.Paused, "scheduled-filter-paused") {
                            if (current()) onStatusFilterChange(ScheduledTaskStatus.Paused)
                        }
                    }
                }
                if (onSortChange != null) {
                    HarnessText("排序方式", color = HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Row(Modifier.fillMaxWidth().semantics { testTag = "scheduled-sort" },
                        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
                        NavigatorFilterOption("默认", sort == ScheduledTaskSort.Automatic, "scheduled-sort-auto") {
                            if (current()) onSortChange(ScheduledTaskSort.Automatic)
                        }
                        NavigatorFilterOption("下次运行", sort == ScheduledTaskSort.NextRun, "scheduled-sort-next") {
                            if (current()) onSortChange(ScheduledTaskSort.NextRun)
                        }
                        NavigatorFilterOption("最近更新", sort == ScheduledTaskSort.Updated, "scheduled-sort-updated") {
                            if (current()) onSortChange(ScheduledTaskSort.Updated)
                        }
                        NavigatorFilterOption("名称", sort == ScheduledTaskSort.Title, "scheduled-sort-title") {
                            if (current()) onSortChange(ScheduledTaskSort.Title)
                        }
                    }
                }
            }
            NavigatorNewTaskRow(
                enabled = state.canMutate && state.draft == null && state.context.targets.isNotEmpty(),
            ) { if (current() && selectable) onNew() }
            HarnessText("即将执行", Modifier.padding(horizontal = HarnessTokens.Spacing.Two),
                color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        val scroll = key(state.scope) { rememberScrollState() }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
            .semantics { testTag = "scheduled-navigator-list" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.scope == null || state.phase == ScheduledLoadPhase.Unbound ->
                    HarnessText("尚未选择 Host。", Modifier.padding(horizontal = HarnessTokens.Spacing.Two))
                !state.connected -> HarnessText("Host 未连接", Modifier.padding(horizontal = HarnessTokens.Spacing.Two))
                state.phase == ScheduledLoadPhase.Loading || state.phase == ScheduledLoadPhase.NotLoaded ->
                    HarnessText("正在读取…", Modifier.padding(horizontal = HarnessTokens.Spacing.Two))
                state.phase == ScheduledLoadPhase.Error -> {
                    HarnessText(state.loadError ?: "读取任务失败。", Modifier.padding(horizontal = HarnessTokens.Spacing.Two),
                        color = HarnessTokens.Colors.Danger)
                    HarnessButton("重试", { if (current()) onRetry() }, enabled = state.connected && state.mutation == null,
                        variant = HarnessButtonVariant.Secondary, testTag = "scheduled-retry")
                }
                ready -> {
                    val tasks = state.tasks
                        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                        .filter { statusFilter == null || it.status == statusFilter }
                    val ordered = when (sort) {
                        // Stable sort keeps the stored order inside each group.
                        ScheduledTaskSort.Automatic -> tasks.sortedBy {
                            if (it.status == ScheduledTaskStatus.Active) 0 else 1
                        }
                        ScheduledTaskSort.NextRun -> {
                            val cursors = state.effective?.cursors.orEmpty()
                            tasks.sortedWith(Comparator { a, b ->
                                val x = cursors.firstOrNull { it.taskId == a.id }?.nextNominalAt
                                val y = cursors.firstOrNull { it.taskId == b.id }?.nextNominalAt
                                when {
                                    x == null && y == null -> 0
                                    x == null -> 1
                                    y == null -> -1
                                    else -> x.compareTo(y)
                                }
                            })
                        }
                        ScheduledTaskSort.Title -> tasks.sortedBy { it.name }
                        // `updated_at_desc`: most recently written first; tasks without a recorded time last.
                        ScheduledTaskSort.Updated -> tasks.sortedWith(Comparator { a, b ->
                            val x = a.updatedAt
                            val y = b.updatedAt
                            when {
                                x == null && y == null -> 0
                                x == null -> 1
                                y == null -> -1
                                else -> y.compareTo(x)
                            }
                        })
                    }
                    if (ordered.isEmpty()) HarnessText(if (state.tasks.isEmpty()) "还没有定时任务" else "没有符合筛选条件的任务",
                        Modifier.padding(horizontal = HarnessTokens.Spacing.Two)
                            .semantics { testTag = "scheduled-navigator-empty" })
                    ordered.forEach { task ->
                        val cursor = state.effective?.cursors?.firstOrNull { it.taskId == task.id }
                        val running = state.effective?.runs.orEmpty().any { it.task.id == task.id && it.blocksTask }
                        HarnessRow(
                            title = task.name,
                            description = scheduledTaskSummary(task, cursor, running, nowEpochMillis, calendar),
                            state = HarnessRowState(selected = state.selectedTaskId == task.id, enabled = selectable),
                            onClick = { if (current() && selectable) onSelect(task.id) },
                            testTag = "scheduled-task:" + task.id,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One status-filter choice. Mirrors `automations.scheduled.table.statusOption` (全部/已开启/已暂停);
 * the selected option answers with the corresponding [ScheduledTaskStatus], or null for 全部.
 */
@Composable
private fun NavigatorFilterOption(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    HarnessButton(label, onClick, selected = selected, variant = HarnessButtonVariant.Ghost,
        size = HarnessControlSize.Compact, testTag = tag)
}

/**
 * Codex draws the scheduled navigator's new-task affordance as a left-aligned leading-plus row
 * ("+ 新建任务"), not a full-width filled button. The 2026-10-06 user-pages capture measures the plus
 * glyph at raw x 70..81 and the label at x 93 inside a 288px navigator, i.e. a one-row [HarnessTokens.Spacing.Two]
 * inset plus a 16px leading glyph. The tag and disabled semantics stay identical to the previous button.
 */
@Composable
private fun NavigatorNewTaskRow(enabled: Boolean, onClick: () -> Unit) {
    val content = if (enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled
    Row(
        Modifier.fillMaxWidth()
            .height(HarnessTokens.Control.NavRow)
            .clip(RoundedCornerShape(HarnessTokens.Control.NavigatorRowRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                testTag = "scheduled-new-task"
                if (!enabled) disabled()
            }
            .padding(horizontal = HarnessTokens.Spacing.Two),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIcon(HarnessIconName.Plus, tint = content, size = HarnessTokens.Control.NavigatorIcon)
        HarnessText("新建任务", color = content, maxLines = 1)
    }
}

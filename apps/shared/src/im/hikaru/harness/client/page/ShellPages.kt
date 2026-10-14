package im.hikaru.harness.client.page

import im.hikaru.harness.client.scheduled.label
import im.hikaru.harness.ui.foundation.HarnessSearchField

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import im.hikaru.harness.client.shell.RecentTaskItem
import im.hikaru.harness.client.shell.WorkspaceItemKind
import im.hikaru.harness.client.shell.WorkspaceItemNode
import im.hikaru.harness.client.shell.WorkspaceProjectNode
import im.hikaru.harness.client.scheduled.ScheduledLoadState
import im.hikaru.harness.client.scheduled.ScheduledRun
import im.hikaru.harness.client.scheduled.ScheduledTask
import im.hikaru.harness.client.scheduled.ScheduledTasksState
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonState
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconButton
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessRow
import im.hikaru.harness.ui.foundation.HarnessRowState
import im.hikaru.harness.ui.foundation.HarnessSection
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTextField
import im.hikaru.harness.ui.foundation.HarnessTokens

/** Home is the new-task entry: it starts a Thread/Task instead of acting as a connection dashboard. */
@Composable
public fun HomePage(
    hostLabel: String?,
    canOpenThread: Boolean,
    recentTasks: List<RecentTaskItem>,
    onOpenThread: () -> Unit,
    onOpenRecent: (RecentTaskItem) -> Unit,
) {
    PageScaffold(title = "新任务", description = "在当前工作区开始一个新的 Agent 任务。") {
        HarnessText(
            hostLabel ?: "本地 Harness 可在无账号、无网络时使用。",
            color = HarnessTokens.Colors.TextMuted,
        )
        HarnessButton(text = "开始新对话", onClick = onOpenThread, enabled = canOpenThread)
        if (recentTasks.isNotEmpty()) {
            HarnessText("最近任务", color = HarnessTokens.Colors.TextMuted)
            recentTasks.take(5).forEach { task ->
                TaskRow(title = task.title, subtitle = task.status, onClick = { onOpenRecent(task) })
            }
        }
    }
}

@Composable
public fun HomeComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIconButton(contentDescription = "Attach file", onClick = {}, enabled = enabled) {
            HarnessIcon(HarnessIconName.Attach)
        }
        TextButton(onClick = {}, enabled = enabled) { HarnessText("Agent") }
        TextButton(onClick = {}, enabled = enabled) { HarnessText("Model") }
        Spacer(Modifier.weight(1f))
        HarnessText("Enter to send", color = HarnessTokens.Colors.TextMuted)
    }
    HarnessTextField(
        value = prompt,
        onValueChange = onPromptChange,
        label = "消息",
        singleLine = false,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        HarnessIconButton(contentDescription = "Send", onClick = onSend, enabled = enabled && prompt.isNotBlank()) {
            HarnessIcon(HarnessIconName.Send)
        }
    }
}

@Composable
public fun RecentTasksPage(
    tasks: List<RecentTaskItem>,
    onOpen: (RecentTaskItem) -> Unit,
) {
    PageScaffold(
        title = "最近",
        description = "最近运行的 Task 与 Thread。同一个 Task 在项目和最近列表中共享 ID。",
    ) {
        if (tasks.isEmpty()) {
            EmptyHint("暂无最近任务。创建新对话后，任务会出现在这里。")
        } else {
            tasks.forEach { task ->
                TaskRow(title = task.title, subtitle = task.status, onClick = { onOpen(task) })
            }
        }
    }
}

@Composable
public fun ScheduledTasksPage(
    state: ScheduledTasksState,
    onNewTask: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleTask: (String) -> Unit,
    onShowOlder: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenRun: (ScheduledTask, ScheduledRun) -> Unit,
) {
    PageScaffold(
        title = "定时任务",
        description = "本地定时任务和运行记录。数据保存在当前客户端内存中。",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two), verticalAlignment = Alignment.CenterVertically) {
            HarnessButton(text = "新建任务", onClick = onNewTask, testTag = "scheduled-page-new-task")
            HarnessSearchField(
                value = state.query,
                onValueChange = onSearch,
                placeholder = "搜索任务",
                accessibilityLabel = "搜索定时任务",
                modifier = Modifier.weight(1f),
                testTag = "scheduled-page-search",
            )
        }
        when (state.loadState) {
            ScheduledLoadState.Loading -> HarnessText("正在加载定时任务…", color = HarnessTokens.Colors.TextMuted)
            ScheduledLoadState.Error -> {
                HarnessRow(
                    title = state.loadError ?: "定时任务加载失败",
                    state = HarnessRowState(control = im.hikaru.harness.ui.foundation.HarnessControlState.Error),
                    onRetry = onRetry,
                    retryLabel = "重试",
                    testTag = "scheduled-page-retry",
                )
            }
            ScheduledLoadState.Ready -> {
                if (state.visibleTasks.isEmpty()) {
                    EmptyHint(if (state.query.isBlank()) "暂无定时任务。点击新建任务开始。" else "没有匹配的定时任务。")
                }
                state.visibleTasks.forEach { task ->
                    val expanded = task.id in state.expandedTaskIds
                    HarnessSection(
                        title = task.title,
                        description = task.status.label,
                        headerAction = {
                            HarnessIconButton(
                                contentDescription = if (expanded) "收起 " + task.title else "展开 " + task.title,
                                onClick = { onToggleTask(task.id) },
                                state = HarnessButtonState(),
                                testTag = "scheduled-page-toggle-" + task.id,
                            ) {
                                HarnessIcon(HarnessIconName.ChevronDown)
                            }
                        },
                    ) {
                        if (expanded) {
                            state.visibleRuns(task).forEach { run ->
                                HarnessRow(
                                    title = run.summary ?: run.status.name,
                                    description = if (run.chatRef.isNullOrBlank()) "此运行没有关联会话" else run.id,
                                    onClick = if (run.chatRef.isNullOrBlank()) null else ({ onOpenRun(task, run) }),
                                    leading = { HarnessIcon(HarnessIconName.Clock, tint = HarnessTokens.Colors.TextMuted) },
                                    testTag = "scheduled-page-run-" + run.id,
                                )
                            }
                            if (state.hasOlderRuns(task)) {
                                HarnessButton(
                                    text = "显示更早运行",
                                    onClick = { onShowOlder(task.id) },
                                    variant = im.hikaru.harness.ui.foundation.HarnessButtonVariant.Ghost,
                                    size = im.hikaru.harness.ui.foundation.HarnessControlSize.Compact,
                                    testTag = "scheduled-page-older-" + task.id,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
public fun ProjectsPage(
    query: String,
    projects: List<WorkspaceProjectNode>,
    onCreate: () -> Unit,
    onOpenProject: (WorkspaceProjectNode) -> Unit,
    createUnavailableReason: String? = null,
) {
    PageScaffold(
        title = "项目",
        description = "项目是任务和线程的容器。第一版使用本地与远端 Harness Project。",
    ) {
        HarnessButton(text = "创建项目", onClick = onCreate, enabled = createUnavailableReason == null)
        createUnavailableReason?.let { EmptyHint(it) }
        val filtered = projects.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        when {
            projects.isEmpty() -> EmptyHint("创建项目以组织任务和线程，并让 Harness 访问本地文件夹。")
            filtered.isEmpty() -> EmptyHint("没有匹配的项目。")
            else ->
                filtered.forEach { project ->
                    ProjectSurfaceRow(
                        title = project.name,
                        subtitle = project.children.size.toString() + " 个任务或线程",
                        onClick = { onOpenProject(project) },
                    )
                }
        }
    }
}

@Composable
public fun ProjectPage(
    project: WorkspaceProjectNode?,
    onOpen: (WorkspaceItemNode) -> Unit,
    onOpenContext: () -> Unit,
) {
    PageScaffold(
        title = project?.name ?: "项目",
        description = "项目下的任务与线程。线程是主内容路由，项目是它的容器。",
    ) {
        SurfaceRow(
            icon = HarnessIconName.PanelRight,
            title = "项目上下文",
            subtitle = null,
            onClick = onOpenContext,
        )
        if (project == null) {
            EmptyHint("项目不存在或已归档。")
        } else if (project.children.isEmpty()) {
            EmptyHint("这个项目还没有任务或线程。")
        } else {
            project.children.forEach { child ->
                SurfaceRow(
                    icon = if (child.kind == WorkspaceItemKind.Task) HarnessIconName.Check else HarnessIconName.Menu,
                    title = child.title,
                    subtitle = child.status,
                    onClick = { onOpen(child) },
                )
            }
        }
    }
}

@Composable
private fun PageScaffold(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
    ) {
        HarnessText(title, color = HarnessTokens.Colors.Text)
        HarnessText(description, color = HarnessTokens.Colors.TextMuted)
        content()
    }
}

@Composable
private fun EmptyHint(text: String) {
    HarnessText(text, color = HarnessTokens.Colors.TextDisabled)
}

@Composable
private fun TaskRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    SurfaceRow(icon = HarnessIconName.Clock, title = title, subtitle = subtitle, onClick = onClick)
}

@Composable
private fun ProjectSurfaceRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    HarnessRow(
        title = title,
        description = subtitle,
        state = HarnessRowState(),
        onClick = onClick,
        leading = { HarnessIcon(HarnessIconName.Folder, tint = HarnessTokens.Colors.TextMuted) },
        testTag = "project-surface-row:$title",
    )
}

@Composable
private fun SurfaceRow(
    icon: HarnessIconName,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    val rowModifier = Modifier.fillMaxWidth()
    HarnessSurface(modifier = if (onClick != null) rowModifier.clickable(onClick = onClick) else rowModifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Three),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessIcon(icon, tint = HarnessTokens.Colors.TextMuted)
            Column(modifier = Modifier.weight(1f)) {
                HarnessText(title, color = HarnessTokens.Colors.Text)
                subtitle?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted) }
            }
        }
    }
}

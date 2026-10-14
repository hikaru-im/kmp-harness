package im.hikaru.harness.client.scheduled

import im.hikaru.harness.client.shell.NavigatorState
import im.hikaru.harness.client.shell.SidebarMode

public enum class ScheduledTaskStatus {
    Monitoring,
    Paused,
    Failed,
}

public val ScheduledTaskStatus.label: String
    get() = when (this) {
        ScheduledTaskStatus.Monitoring -> "监控中"
        ScheduledTaskStatus.Paused -> "已暂停"
        ScheduledTaskStatus.Failed -> "失败"
    }

public enum class ScheduledRunStatus {
    InProgress,
    Succeeded,
    Failed,
    Unconfirmed,
}

public enum class ScheduledLoadState {
    Loading,
    Ready,
    Error,
}

public data class ScheduledRun(
    val id: String,
    val status: ScheduledRunStatus,
    val summary: String? = null,
    val chatRef: String? = null,
    val unconfirmed: Boolean = false,
)

public data class ScheduledTask(
    val id: String,
    val title: String,
    val status: ScheduledTaskStatus = ScheduledTaskStatus.Paused,
    val runs: List<ScheduledRun> = emptyList(),
    val hasMoreRuns: Boolean = false,
)

public data class ScheduledRunNavigation(
    val threadId: String,
    val automationId: String,
    val runId: String,
    val navigationSource: String = "scheduled_page",
)

public data class ScheduledTasksState(
    override val mode: SidebarMode = SidebarMode.Expanded,
    val tasks: List<ScheduledTask> = emptyList(),
    val query: String = "",
    val expandedTaskIds: Set<String> = emptySet(),
    val visibleRunCounts: Map<String, Int> = emptyMap(),
    val selectedTaskId: String? = null,
    val loadState: ScheduledLoadState = ScheduledLoadState.Ready,
    val loadError: String? = null,
    val lastRunNavigation: ScheduledRunNavigation? = null,
) : NavigatorState {
    public val visibleTasks: List<ScheduledTask>
        get() {
            val normalized = query.trim()
            return tasks.filter { task ->
                val matchesQuery = normalized.isEmpty() || task.title.contains(normalized, ignoreCase = true)
                matchesQuery
            }
        }

    public fun visibleRuns(task: ScheduledTask): List<ScheduledRun> =
        task.runs.take(visibleRunCounts[task.id] ?: 1)

    public fun hasOlderRuns(task: ScheduledTask): Boolean = visibleRuns(task).size < task.runs.size || task.hasMoreRuns

    public fun withMode(mode: SidebarMode): ScheduledTasksState = copy(mode = mode)

    public companion object {
        public fun seed(): ScheduledTasksState {
            val tasks =
                listOf(
                    ScheduledTask(
                        id = "automation-koog-pr-1703",
                        title = "跟踪 Koog PR #1703",
                        status = ScheduledTaskStatus.Monitoring,
                        runs =
                            listOf(
                                ScheduledRun("run-koog-3", ScheduledRunStatus.InProgress, "正在监控", chatRef = "thread-koog-3"),
                                ScheduledRun("run-koog-2", ScheduledRunStatus.Succeeded, "已完成", chatRef = "thread-koog-2"),
                                ScheduledRun("run-koog-1", ScheduledRunStatus.Failed, "检查失败", chatRef = "thread-koog-1"),
                            ),
                        hasMoreRuns = true,
                    ),
                    ScheduledTask(
                        id = "automation-weekly-notes",
                        title = "整理每周工作摘要",
                        runs = listOf(ScheduledRun("run-notes-1", ScheduledRunStatus.Succeeded, "摘要已生成", chatRef = "thread-notes-1")),
                    ),
                )
            return ScheduledTasksState(
                tasks = tasks,
                visibleRunCounts = tasks.associate { it.id to 1 },
            )
        }
    }
}

/** Synchronous deterministic repository used by the local page and its state tests. */
public class ScheduledTasksRepository(
    initial: ScheduledTasksState = ScheduledTasksState(),
) {
    public var state: ScheduledTasksState = initial
        private set

    public fun setQuery(query: String) {
        state = state.copy(query = query)
    }

    public fun setMode(mode: SidebarMode) {
        state = state.withMode(mode)
    }

    public fun toggleTask(taskId: String) {
        state = state.copy(
            selectedTaskId = taskId,
            expandedTaskIds = if (taskId in state.expandedTaskIds) state.expandedTaskIds - taskId else state.expandedTaskIds + taskId,
        )
    }

    public fun showOlder(taskId: String) {
        val task = state.tasks.firstOrNull { it.id == taskId } ?: return
        val current = state.visibleRunCounts[taskId] ?: 1
        val next = (current + 1).coerceAtMost(task.runs.size)
        state =
            state.copy(
                visibleRunCounts = state.visibleRunCounts + (taskId to next),
                tasks = state.tasks.map { candidate ->
                    if (candidate.id == taskId && next >= candidate.runs.size) candidate.copy(hasMoreRuns = false) else candidate
                },
            )
    }

    public fun beginLoading() {
        state = state.copy(loadState = ScheduledLoadState.Loading, loadError = null)
    }

    public fun failLoading(message: String = "Scheduled tasks could not be loaded") {
        state = state.copy(loadState = ScheduledLoadState.Error, loadError = message)
    }

    public fun retry() {
        state = state.copy(loadState = ScheduledLoadState.Ready, loadError = null)
    }

    public fun createTask(title: String = "新建本地任务"): ScheduledTask {
        val id = "local-${state.tasks.size + 1}"
        val task =
            ScheduledTask(
                id = id,
                title = title,
                runs = emptyList(),
            )
        state =
            state.copy(
                tasks = listOf(task) + state.tasks,
                query = "",
                expandedTaskIds = state.expandedTaskIds + id,
                visibleRunCounts = state.visibleRunCounts + (id to 1),
                selectedTaskId = id,
            )
        return task
    }

    public fun openRun(task: ScheduledTask, run: ScheduledRun): ScheduledRunNavigation? {
        val threadId = run.chatRef?.takeIf { it.isNotBlank() } ?: return null
        val navigation =
            ScheduledRunNavigation(
                threadId = threadId,
                automationId = task.id,
                runId = run.id,
            )
        state = state.copy(selectedTaskId = task.id, lastRunNavigation = navigation)
        return navigation
    }

    public fun clearRunNavigation() {
        state = state.copy(lastRunNavigation = null)
    }
}

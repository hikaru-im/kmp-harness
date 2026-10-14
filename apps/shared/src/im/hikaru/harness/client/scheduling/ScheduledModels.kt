package im.hikaru.harness.client.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.SessionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Generation belongs to a live binding, never to the durable ownership key. */
public data class ScheduledScope(val hostId: HostId, val memberIdentity: MemberIdentity?, val generation: Long) {
    init { require(generation >= 0) }
    public val owner: ScheduledOwner get() = ScheduledOwner(hostId, memberIdentity)
}

@Serializable
public data class ScheduledOwner(val hostId: HostId, val memberIdentity: MemberIdentity?)

@Serializable
public sealed interface ScheduledDestination {
    @Serializable @SerialName("projectless") public data object Projectless : ScheduledDestination
    /** An absolute directory verified by the owning Host; no ~ sentinel or invented project ID. */
    @Serializable @SerialName("cwd") public data class Cwd(val path: String) : ScheduledDestination
}

@Serializable
public sealed interface ScheduledTarget {
    @Serializable @SerialName("cron") public data class Cron(val destination: ScheduledDestination) : ScheduledTarget
    /** Only an existing session from the bound Host catalog is eligible. */
    @Serializable @SerialName("heartbeat") public data class Heartbeat(val sessionId: SessionId) : ScheduledTarget
}

@Serializable
public data class ScheduledModelRef(val providerId: String, val modelId: String)

@Serializable
public enum class ScheduledWeekday { Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday }

@Serializable
public sealed interface ScheduledRule {
    /** Exact duration anchored to an instant; independent of DST and local clock changes. */
    @Serializable @SerialName("hourly")
    public data class Hourly(val intervalHours: Int, val anchorEpochMillis: Long) : ScheduledRule
    @Serializable @SerialName("daily")
    public data class Daily(val hour: Int, val minute: Int, val zoneId: String) : ScheduledRule
    @Serializable @SerialName("weekdays")
    public data class Weekdays(val hour: Int, val minute: Int, val zoneId: String) : ScheduledRule
    @Serializable @SerialName("weekly")
    public data class Weekly(val day: ScheduledWeekday, val hour: Int, val minute: Int, val zoneId: String) : ScheduledRule
}

@Serializable
public enum class ScheduledTaskStatus { Paused, Active }

@Serializable
public data class ScheduledTask(
    val id: String, val revision: Long, val name: String, val prompt: String,
    val target: ScheduledTarget, val schedule: ScheduledRule, val model: ScheduledModelRef?,
    val status: ScheduledTaskStatus = ScheduledTaskStatus.Paused,
    /**
     * Durable wall-clock instant of the last write to this task record, or null when none was recorded
     * (documents written before this field existed). The navigator's `最近更新` sort
     * (`automations.scheduled.table.sortOption` `updated_at_desc`) and the detail page's `上次更新` line
     * (`automations.scheduled.detailUpdateTimestampWithLabel`) read it; a null never becomes a made-up time.
     */
    val updatedAt: Long? = null,
)

/** Tasks, occurrences and run evidence share one durable revision. */
@Serializable
public data class ScheduledSnapshot(
    val owner: ScheduledOwner, val revision: Long = 0, val tasks: List<ScheduledTask> = emptyList(),
    val deletedTaskIds: List<String> = emptyList(),
    val runs: List<ScheduledRun> = emptyList(),
    val cursors: List<ScheduledCursor> = emptyList(),
)

/** These lists must come from the selected Host, not page fixtures or free-form ID fields. */
public data class ScheduledValidationContext(val targets: List<ScheduledTarget>, val models: List<ScheduledModelRef>)

public enum class ScheduledFrequency { Hourly, Daily, Weekdays, Weekly }

/**
 * Ordering choice of the navigator's "筛选和排序任务" control (`automations.scheduled.table.sort`).
 * [Automatic] mirrors the original default — active tasks before paused ones, stable within a group;
 * [NextRun] orders by the durable cursor's next instant (tasks without a cursor last); [Title] by name;
 * [Updated] mirrors `updated_at_desc` — most recently written task first (tasks without a recorded time last).
 */
public enum class ScheduledTaskSort { Automatic, NextRun, Updated, Title }

/** Raw numeric fields stay raw until validation, so clearing a field never silently changes it. */
public data class ScheduledRuleDraft(
    val frequency: ScheduledFrequency = ScheduledFrequency.Daily,
    val intervalHours: String = "1", val hour: String = "9", val minute: String = "0",
    val zoneId: String = "UTC", val day: ScheduledWeekday = ScheduledWeekday.Monday,
    val anchorEpochMillis: Long = 0,
) {
    public fun rule(): ScheduledRule? = when (frequency) {
        ScheduledFrequency.Hourly -> intervalHours.toIntOrNull()?.let { ScheduledRule.Hourly(it, anchorEpochMillis) }
        ScheduledFrequency.Daily -> wall { h, m -> ScheduledRule.Daily(h, m, zoneId.trim()) }
        ScheduledFrequency.Weekdays -> wall { h, m -> ScheduledRule.Weekdays(h, m, zoneId.trim()) }
        ScheduledFrequency.Weekly -> wall { h, m -> ScheduledRule.Weekly(day, h, m, zoneId.trim()) }
    }
    private fun wall(create: (Int, Int) -> ScheduledRule): ScheduledRule? {
        val h = hour.toIntOrNull() ?: return null
        val m = minute.toIntOrNull() ?: return null
        return create(h, m)
    }
}

public fun ScheduledRule.toDraft(): ScheduledRuleDraft = when (this) {
    is ScheduledRule.Hourly -> ScheduledRuleDraft(ScheduledFrequency.Hourly, intervalHours = intervalHours.toString(), anchorEpochMillis = anchorEpochMillis)
    is ScheduledRule.Daily -> ScheduledRuleDraft(ScheduledFrequency.Daily, hour = hour.toString(), minute = minute.toString(), zoneId = zoneId)
    is ScheduledRule.Weekdays -> ScheduledRuleDraft(ScheduledFrequency.Weekdays, hour = hour.toString(), minute = minute.toString(), zoneId = zoneId)
    is ScheduledRule.Weekly -> ScheduledRuleDraft(ScheduledFrequency.Weekly, hour = hour.toString(), minute = minute.toString(), zoneId = zoneId, day = day)
}

public data class ScheduledDraft(
    val taskId: String, val expectedTaskRevision: Long?, val draftRevision: Long = 0,
    val name: String = "", val prompt: String = "", val target: ScheduledTarget? = null,
    val schedule: ScheduledRuleDraft = ScheduledRuleDraft(), val model: ScheduledModelRef? = null,
)

public enum class ScheduledLoadPhase { Unbound, NotLoaded, Loading, Ready, Error }
public enum class ScheduledMutation { Save, Pause, Delete, Run }

public const val ScheduledExecutorUnavailable: String = "当前 Host 未接入真实调度与执行服务；只能保存暂停任务，不能启用或立即运行。"

/**
 * The local execution model shown on the page. This process owns the only scheduler; there is no
 * OS daemon, so a scheduled task never runs while the application is closed.
 */
public const val ScheduledLocalExecutionModel: String =
    "本机定时任务只在本应用运行期间执行；关闭应用后不再运行，也没有系统后台守护进程替代执行。"

public data class ScheduledManagementState(
    val scope: ScheduledScope? = null, val connected: Boolean = false,
    val context: ScheduledValidationContext = ScheduledValidationContext(emptyList(), emptyList()),
    val phase: ScheduledLoadPhase = ScheduledLoadPhase.Unbound,
    val effective: ScheduledSnapshot? = null, val draft: ScheduledDraft? = null,
    val selectedTaskId: String? = null, val mutation: ScheduledMutation? = null,
    val loadError: String? = null, val mutationError: String? = null,
    val durabilityWarning: String? = null, val validationErrors: Map<String, String> = emptyMap(),
    val executionUnavailableReason: String? = ScheduledExecutorUnavailable,
) {
    public val canMutate: Boolean get() = connected && phase == ScheduledLoadPhase.Ready && effective != null && mutation == null
    public val tasks: List<ScheduledTask> get() = effective?.tasks.orEmpty()
    public val selectedTask: ScheduledTask? get() = tasks.firstOrNull { it.id == selectedTaskId }
    // Both target kinds are deliverable now; the executor re-verifies the exact target before dispatch.
    public val canActivate: Boolean get() = canMutate && draft == null && executionUnavailableReason == null &&
        (selectedTask?.target is ScheduledTarget.Cron || selectedTask?.target is ScheduledTarget.Heartbeat)
    public val canRunNow: Boolean get() = canActivate && effective?.runs.orEmpty().none { it.task.id == selectedTaskId && it.blocksTask }
}

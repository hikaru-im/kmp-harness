package im.hikaru.harness.client.scheduling

/**
 * The scheduled-task row summary, mirroring the original table's cadence + next-run cells
 * (`automations.scheduled.table.frequency` and `automations.scheduled.table.nextRun` /
 * `taskSummaryWithMinutesAndRunStatus`). Every part is a projection of real stored state: the cadence
 * from the task rule, the next instant from the durable cursor, and the paused/running state from the
 * task status and its blocking runs. A platform that cannot format the instant degrades to the
 * cadence alone instead of inventing a time.
 */

/** `automations.scheduled.table.inactive` with a paused status. */
public const val ScheduledPausedLabel: String = "已暂停"

/** `automations.scheduled.taskSummaryWithMinutesAndRunStatus` running branch. */
public const val ScheduledRunningLabel: String = "正在运行"

/** Cadence of a rule without its configured time and zone, e.g. `每小时` / `每天` / `工作日` / `每周`. */
public fun ScheduledRule.cadenceLabel(): String = when (this) {
    is ScheduledRule.Hourly -> "每小时"
    is ScheduledRule.Daily -> "每天"
    is ScheduledRule.Weekdays -> "工作日"
    is ScheduledRule.Weekly -> "每周"
}

/** Zone the rule is anchored to, or null for fixed-duration hourly rules that have no zone. */
public fun ScheduledRule.zoneIdOrNull(): String? = when (this) {
    is ScheduledRule.Hourly -> null
    is ScheduledRule.Daily -> zoneId
    is ScheduledRule.Weekdays -> zoneId
    is ScheduledRule.Weekly -> zoneId
}

/**
 * One row's summary. Paused tasks show only the paused state; an active task with a blocking run shows
 * `正在运行`; otherwise the durable cursor's next instant is formatted by [calendar] (falling back to
 * the raw cadence when the platform cannot label it or the cursor is missing).
 */
public fun scheduledTaskSummary(
    task: ScheduledTask,
    cursor: ScheduledCursor?,
    running: Boolean,
    nowEpochMillis: Long,
    calendar: ScheduleCalendar?,
): String {
    if (task.status == ScheduledTaskStatus.Paused) return ScheduledPausedLabel
    val cadence = task.schedule.cadenceLabel()
    if (running) return cadence + " · " + ScheduledRunningLabel
    val next = cursor?.nextNominalAt?.takeIf { cursor.taskId == task.id }
    val zone = task.schedule.zoneIdOrNull() ?: calendar?.displayZoneId.orEmpty()
    val label = if (next != null && calendar != null && zone.isNotEmpty()) {
        calendar.nextRunLabel(next, nowEpochMillis, zone)
    } else null
    return if (label != null) cadence + " · 下次运行：" + label else cadence
}

/** `automations.scheduled.detailRunTiming` prefix shared by the detail page's last-run branches. */
public const val ScheduledLastRunPrefix: String = "上次运行于 "

/**
 * Instant of the last completed run: the newest terminal outcome's `endedAt`, else the newest run's
 * `claimedAt` when nothing has reached a terminal state yet. Null when the task has no run at all, so
 * the detail page never invents a last-run time.
 */
public fun lastRunInstant(runs: List<ScheduledRun>): Long? =
    runs.filter { it.terminal != null }.maxByOrNull { it.terminal!!.endedAt }?.terminal?.endedAt
        ?: runs.maxByOrNull { it.claimedAt }?.claimedAt

/**
 * Detail-page "上次运行于 …" line, mirroring `automations.scheduled.detailRunTiming`'s last-run branch:
 * a relative label when the run finished within the last day (`relativeRunLabel`), otherwise a day
 * label (`runDayLabel`). Null when there is no run, no calendar, or the platform cannot label the
 * instant — callers then render nothing instead of a fabricated time.
 */
public fun scheduledLastRunLabel(lastRunAt: Long?, nowEpochMillis: Long, calendar: ScheduleCalendar?): String? {
    if (lastRunAt == null || calendar == null) return null
    val zone = calendar.displayZoneId
    if (zone.isEmpty()) return null
    val label = calendar.relativeRunLabel(lastRunAt, nowEpochMillis, zone)
        ?: calendar.runDayLabel(lastRunAt, nowEpochMillis, zone)
    return label?.let { ScheduledLastRunPrefix + it }
}

/** `automations.scheduled.detailUpdateTimestampWithLabel` prefix when `showLastUpdateLabel` is set. */
public const val ScheduledLastUpdatedPrefix: String = "上次更新 · "

/**
 * Detail-page "上次更新 · …" line, mirroring `automations.scheduled.detailUpdateTimestampWithLabel`'s
 * `recent`/`other` branches. The original sets `showLastUpdateLabel` only when the relative branch is
 * taken, so the `上次更新 · ` prefix precedes a relative label within the last day (`relativeRunLabel`),
 * while an older instant renders as a bare day label (`runDayLabel`). Null when the task has no recorded
 * write time, no calendar, or the platform cannot label it — callers then render nothing instead of a time.
 */
public fun scheduledLastUpdatedLabel(updatedAt: Long?, nowEpochMillis: Long, calendar: ScheduleCalendar?): String? {
    if (updatedAt == null || calendar == null) return null
    val zone = calendar.displayZoneId
    if (zone.isEmpty()) return null
    calendar.relativeRunLabel(updatedAt, nowEpochMillis, zone)?.let { return ScheduledLastUpdatedPrefix + it }
    return calendar.runDayLabel(updatedAt, nowEpochMillis, zone)
}

/**
 * Compact cadence shown as the meta of one row in the conversation summary panel's 定时任务 section,
 * mirroring the original `compact-cadence` component (`variant: summary`): the editor token set
 * `automations.scheduled.editor.compactCadenceWithMinutes` plus the summary-only
 * `automations.scheduled.summary.compactCadenceWithTime` daily branch. A paused task shows only 已暂停,
 * an hourly rule 每小时 / 每 N 小时, a daily rule 每天 · HH:MM, the fixed working week 周一至周五, and a
 * single weekday 每周一…每周日. The original summary tokens carry an exact time only for the daily
 * recurrence, so the weekday rules deliberately omit it here too.
 */
public fun scheduledSummaryCadence(task: ScheduledTask): String = when (task.status) {
    ScheduledTaskStatus.Paused -> ScheduledPausedLabel
    ScheduledTaskStatus.Active -> when (val rule = task.schedule) {
        is ScheduledRule.Hourly ->
            if (rule.intervalHours == 1) "每小时" else "每 " + rule.intervalHours + " 小时"
        is ScheduledRule.Daily -> "每天 · " + compactClock(rule.hour, rule.minute)
        is ScheduledRule.Weekdays -> "周一至周五"
        is ScheduledRule.Weekly -> rule.day.compactWeekdayLabel()
    }
}

/** 24-hour `HH:MM` matching the zh-CN `formatTime({hour:'numeric',minute:'2-digit'})` output. */
private fun compactClock(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

/** `compactCadenceWithMinutes` tokens `MO`…`SU` = 每周一…每周日. */
private fun ScheduledWeekday.compactWeekdayLabel(): String = when (this) {
    ScheduledWeekday.Monday -> "每周一"
    ScheduledWeekday.Tuesday -> "每周二"
    ScheduledWeekday.Wednesday -> "每周三"
    ScheduledWeekday.Thursday -> "每周四"
    ScheduledWeekday.Friday -> "每周五"
    ScheduledWeekday.Saturday -> "每周六"
    ScheduledWeekday.Sunday -> "每周日"
}

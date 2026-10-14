package im.hikaru.harness.client.scheduling

/** Platform calendar adapter: no clock, I/O or mutable scheduler state. */
public interface ScheduleCalendar {
    public fun isValidZone(zoneId: String): Boolean
    public fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate
    public fun plusDays(date: ScheduleDate, days: Int): ScheduleDate
    /** Gaps shift forward by their duration; overlaps use the earlier offset, once per date. */
    public fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long

    /**
     * Zone a rule without its own zone (fixed-duration hourly) is labelled in; blank when the platform
     * has no viewer default. Readers then fall back to the cadence alone.
     */
    public val displayZoneId: String get() = ""

    /**
     * View-local label for a durable next-run instant in [zoneId], e.g. "今天 09:00" / "明天 09:00" /
     * "3月5日 09:00". Null when the platform cannot format it; callers then show only the cadence.
     */
    public fun nextRunLabel(nextEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? = null

    /**
     * View-local relative label for a run that finished within the last day, e.g. "22 分钟前" /
     * "3 小时前". Null when the instant is in the future, older than a day, or the platform cannot
     * format it; callers then fall back to [runDayLabel].
     */
    public fun relativeRunLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? = null

    /**
     * View-local day label for a run older than a day, e.g. "今天 14:30" / "昨天 09:00" / "星期三" /
     * "2026年10月1日". Null when the platform cannot format it; callers then show no last-run line.
     */
    public fun runDayLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? = null
}

public data class ScheduleDate(val year: Int, val month: Int, val day: Int, val weekday: ScheduledWeekday)

/** Calculates the next strictly future occurrence. This is not an executor or wake-up owner. */
public class ScheduleEngine(private val calendar: ScheduleCalendar) {
    public fun validationError(rule: ScheduledRule): String? {
        ScheduledValidator.ruleError(rule)?.let { return it }
        val zone = when (rule) {
            is ScheduledRule.Hourly -> return null
            is ScheduledRule.Daily -> rule.zoneId
            is ScheduledRule.Weekdays -> rule.zoneId
            is ScheduledRule.Weekly -> rule.zoneId
        }
        return if (calendar.isValidZone(zone)) null else "时区无效或当前平台不支持。"
    }

    public fun nextOccurrence(rule: ScheduledRule, afterEpochMillis: Long): Long? {
        if (afterEpochMillis < 0 || validationError(rule) != null) return null
        if (rule is ScheduledRule.Hourly) {
            if (afterEpochMillis < rule.anchorEpochMillis) return rule.anchorEpochMillis
            val step = rule.intervalHours.toLong() * 3_600_000L
            val steps = (afterEpochMillis - rule.anchorEpochMillis) / step + 1
            if (steps > (Long.MAX_VALUE - rule.anchorEpochMillis) / step) return null
            return rule.anchorEpochMillis + steps * step
        }
        val zone: String
        val hour: Int
        val minute: Int
        when (rule) {
            is ScheduledRule.Daily -> { zone = rule.zoneId; hour = rule.hour; minute = rule.minute }
            is ScheduledRule.Weekdays -> { zone = rule.zoneId; hour = rule.hour; minute = rule.minute }
            is ScheduledRule.Weekly -> { zone = rule.zoneId; hour = rule.hour; minute = rule.minute }
            is ScheduledRule.Hourly -> error("Handled above")
        }
        return try {
            val start = calendar.dateAt(afterEpochMillis, zone)
            (0..7).firstNotNullOfOrNull { offset ->
                val date = calendar.plusDays(start, offset)
                val eligible = when (rule) {
                    is ScheduledRule.Weekdays -> date.weekday != ScheduledWeekday.Saturday && date.weekday != ScheduledWeekday.Sunday
                    is ScheduledRule.Weekly -> date.weekday == rule.day
                    else -> true
                }
                if (!eligible) null else calendar.resolve(date, hour, minute, zone).takeIf { it > afterEpochMillis }
            }
        } catch (_: RuntimeException) { null }
    }
}

package im.hikaru.harness.desktop.scheduling

import im.hikaru.harness.client.scheduling.ScheduleCalendar
import im.hikaru.harness.client.scheduling.ScheduleDate
import im.hikaru.harness.client.scheduling.ScheduledWeekday
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Java time resolves gaps by shifting forward and overlaps to the earlier offset. */
public class DesktopScheduleCalendar : ScheduleCalendar {
    override fun isValidZone(zoneId: String): Boolean = try {
        ZoneId.of(zoneId)
        true
    } catch (_: java.time.DateTimeException) { false }

    override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(zoneId)).toLocalDate().scheduleDate()

    override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date.localDate().plusDays(days.toLong()).scheduleDate()

    override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long =
        LocalDateTime.of(date.localDate(), java.time.LocalTime.of(hour, minute)).atZone(ZoneId.of(zoneId))
            .toInstant().toEpochMilli()

    override val displayZoneId: String get() = ZoneId.systemDefault().id

    override fun nextRunLabel(nextEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? = try {
        val zone = ZoneId.of(zoneId)
        val next = Instant.ofEpochMilli(nextEpochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val time = java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", next.hour, next.minute)
        when (next.toLocalDate()) {
            today -> "今天 $time"
            today.plusDays(1) -> "明天 $time"
            else -> {
                val year = if (next.year == today.year) "" else next.year.toString() + "年"
                year + next.monthValue + "月" + next.dayOfMonth + "日 $time"
            }
        }
    } catch (_: java.time.DateTimeException) { null }

    override fun relativeRunLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? {
        val delta = nowEpochMillis - runEpochMillis
        if (delta < 0 || delta >= DAY_MILLIS) return null
        return if (delta < HOUR_MILLIS) {
            val minutes = maxOf(1L, Math.round(delta.toDouble() / MINUTE_MILLIS))
            minutes.toString() + " 分钟前"
        } else {
            val hours = maxOf(1L, Math.round(delta.toDouble() / HOUR_MILLIS))
            hours.toString() + " 小时前"
        }
    }

    override fun runDayLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? = try {
        val zone = ZoneId.of(zoneId)
        val run = Instant.ofEpochMilli(runEpochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(run.toLocalDate(), today)
        when {
            days <= 1L -> (if (days <= 0L) "今天 " else "昨天 ") + clock(run)
            days <= 7L -> weekdayLabel(run.dayOfWeek.value)
            else -> run.year.toString() + "年" + run.monthValue + "月" + run.dayOfMonth + "日"
        }
    } catch (_: java.time.DateTimeException) { null }

    private fun clock(at: java.time.ZonedDateTime): String =
        java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", at.hour, at.minute)

    private fun weekdayLabel(isoDayOfWeek: Int): String = "星期" + "一二三四五六日"[isoDayOfWeek - 1]

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 3_600_000L
        const val DAY_MILLIS = 86_400_000L
    }

    private fun ScheduleDate.localDate(): LocalDate = LocalDate.of(year, month, day)
    private fun LocalDate.scheduleDate(): ScheduleDate = ScheduleDate(year, monthValue, dayOfMonth,
        ScheduledWeekday.entries[dayOfWeek.value - 1])
}

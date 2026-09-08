package im.hikaru.ruoyi.framework.common.util.date

import im.hikaru.ruoyi.framework.common.enums.DateIntervalEnum
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

object LocalDateTimeUtils {
    @JvmField
    val EMPTY: LocalDateTime = buildTime(1970, 1, 1)

    @JvmField
    val UTC_MS_WITH_XXX_OFFSET_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    private val defaultZoneId = ZoneId.of(DateUtils.TIME_ZONE_DEFAULT)
    private val dateTimeFormatters = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern(DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    )

    @JvmStatic
    fun parse(time: String): LocalDateTime {
        try {
            return LocalDate.parse(time, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
        } catch (_: DateTimeParseException) {
            // Try date-time formats below.
        }
        for (formatter in dateTimeFormatters) {
            try {
                return LocalDateTime.parse(time, formatter)
            } catch (_: DateTimeParseException) {
                // Continue with the next supported format.
            }
        }
        throw DateTimeParseException("Unsupported local date-time", time, 0)
    }

    @JvmStatic fun addTime(duration: Duration): LocalDateTime = LocalDateTime.now().plus(duration)
    @JvmStatic fun minusTime(duration: Duration): LocalDateTime = LocalDateTime.now().minus(duration)
    @JvmStatic fun beforeNow(date: LocalDateTime): Boolean = date.isBefore(LocalDateTime.now())
    @JvmStatic fun afterNow(date: LocalDateTime): Boolean = date.isAfter(LocalDateTime.now())
    @JvmStatic fun ofEpochSecond(epochSecond: Long): LocalDateTime = ofEpochSecond(epochSecond, defaultZoneId)
    @JvmStatic fun ofEpochSecond(epochSecond: Long, zoneId: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), zoneId)
    @JvmStatic fun buildTime(year: Int, month: Int, day: Int): LocalDateTime = LocalDateTime.of(year, month, day, 0, 0)
    @JvmStatic fun buildBetweenTime(year1: Int, month1: Int, day1: Int, year2: Int, month2: Int, day2: Int) =
        arrayOf(buildTime(year1, month1, day1), buildTime(year2, month2, day2))

    @JvmStatic fun isBetween(startTime: LocalDateTime?, endTime: LocalDateTime?, time: Timestamp?): Boolean =
        isBetweenValue(startTime, endTime, time?.toLocalDateTime())
    @JvmStatic fun isBetween(startTime: LocalDateTime?, endTime: LocalDateTime?, time: String?): Boolean =
        isBetweenValue(startTime, endTime, time?.let(::parse))
    @JvmStatic fun isBetween(startTime: LocalDateTime?, endTime: LocalDateTime?): Boolean =
        isBetweenValue(startTime, endTime, LocalDateTime.now())
    @JvmStatic fun isBetween(startTime: String?, endTime: String?): Boolean {
        if (startTime == null || endTime == null) return false
        val today = LocalDate.now()
        return isBetweenValue(
            LocalDateTime.of(today, LocalTime.parse(startTime)),
            LocalDateTime.of(today, LocalTime.parse(endTime)),
            LocalDateTime.now(),
        )
    }

    @JvmStatic
    fun isOverlap(startTime1: LocalTime, endTime1: LocalTime, startTime2: LocalTime, endTime2: LocalTime): Boolean =
        !startTime1.isAfter(endTime2) && !startTime2.isAfter(endTime1)

    @JvmStatic fun beginOfMonth(date: LocalDateTime): LocalDateTime = date.with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN)
    @JvmStatic fun endOfMonth(date: LocalDateTime): LocalDateTime = date.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX)
    @JvmStatic fun getQuarterOfYear(date: LocalDateTime): Int = (date.monthValue - 1) / 3 + 1
    @JvmStatic fun between(dateTime: LocalDateTime): Long = ChronoUnit.DAYS.between(dateTime, LocalDateTime.now())
    @JvmStatic fun getToday(): LocalDateTime = LocalDate.now().atStartOfDay()
    @JvmStatic fun getYesterday(): LocalDateTime = LocalDate.now().minusDays(1).atStartOfDay()
    @JvmStatic fun getMonth(): LocalDateTime = beginOfMonth(LocalDateTime.now())
    @JvmStatic fun getYear(): LocalDateTime = LocalDate.now().with(TemporalAdjusters.firstDayOfYear()).atStartOfDay()
    @JvmStatic fun getLatestDays(days: Int): List<LocalDateTime> {
        require(days >= 0) { "days must not be negative" }
        val today = getToday()
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }

    @JvmStatic
    fun getDateRangeList(startTime: LocalDateTime, endTime: LocalDateTime, interval: Int?): List<Array<LocalDateTime>> {
        val intervalEnum = DateIntervalEnum.valueOf(interval) ?: throw IllegalArgumentException("Invalid interval: $interval")
        var cursor = startTime.toLocalDate().atStartOfDay()
        val rangeEnd = endTime.toLocalDate().atTime(LocalTime.MAX)
        if (cursor.isAfter(rangeEnd)) return emptyList()
        val ranges = mutableListOf<Array<LocalDateTime>>()
        while (!cursor.isAfter(rangeEnd)) {
            val naturalEnd = when (intervalEnum) {
                DateIntervalEnum.HOUR -> cursor.plusHours(1).minusNanos(1)
                DateIntervalEnum.DAY -> cursor.plusDays(1).minusNanos(1)
                DateIntervalEnum.WEEK -> cursor.with(DayOfWeek.SUNDAY).plusDays(1).minusNanos(1)
                DateIntervalEnum.MONTH -> cursor.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).minusNanos(1)
                DateIntervalEnum.QUARTER -> quarterEnd(cursor)
                DateIntervalEnum.YEAR -> cursor.with(TemporalAdjusters.lastDayOfYear()).plusDays(1).minusNanos(1)
            }
            val actualEnd = minOf(naturalEnd, rangeEnd)
            ranges += arrayOf(cursor, actualEnd)
            cursor = actualEnd.plusNanos(1)
        }
        return ranges
    }

    @JvmStatic fun getDateList(startDate: LocalDate, days: Int): List<LocalDate> {
        require(days >= 0) { "days must not be negative" }
        return (0 until days).map { startDate.plusDays(it.toLong()) }
    }

    @JvmStatic
    fun formatDateRange(startTime: LocalDateTime, @Suppress("UNUSED_PARAMETER") endTime: LocalDateTime, interval: Int?): String =
        when (DateIntervalEnum.valueOf(interval) ?: throw IllegalArgumentException("Invalid interval: $interval")) {
            DateIntervalEnum.HOUR -> startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            DateIntervalEnum.DAY -> startTime.format(DateTimeFormatter.ISO_LOCAL_DATE)
            DateIntervalEnum.WEEK -> "${startTime.format(DateTimeFormatter.ISO_LOCAL_DATE)} (week ${startTime.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())})"
            DateIntervalEnum.MONTH -> startTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            DateIntervalEnum.QUARTER -> "${startTime.year}-Q${getQuarterOfYear(startTime)}"
            DateIntervalEnum.YEAR -> startTime.year.toString()
        }

    @JvmStatic fun getQuarterStart(date: LocalDate): LocalDate = LocalDate.of(date.year, date.month.firstMonthOfQuarter(), 1)
    @JvmStatic fun getWeekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
    @JvmStatic fun toEpochSecond(sourceDateTime: LocalDateTime): Long = toEpochSecond(sourceDateTime, defaultZoneId)
    @JvmStatic fun toEpochSecond(sourceDateTime: LocalDateTime, zoneId: ZoneId): Long = sourceDateTime.atZone(zoneId).toEpochSecond()

    private fun isBetweenValue(start: LocalDateTime?, end: LocalDateTime?, value: LocalDateTime?): Boolean =
        start != null && end != null && value != null && !value.isBefore(start) && !value.isAfter(end)

    private fun quarterEnd(value: LocalDateTime): LocalDateTime {
        val quarter = getQuarterOfYear(value)
        return if (quarter == 4) {
            value.with(TemporalAdjusters.lastDayOfYear()).plusDays(1).minusNanos(1)
        } else {
            value.withMonth(quarter * 3 + 1).withDayOfMonth(1).minusNanos(1)
        }
    }
}

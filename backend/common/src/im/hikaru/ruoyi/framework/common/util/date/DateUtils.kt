package im.hikaru.ruoyi.framework.common.util.date

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

object DateUtils {
    const val TIME_ZONE_DEFAULT = "GMT+8"
    const val SECOND_MILLIS = 1000L
    const val FORMAT_YEAR_MONTH_DAY = "yyyy-MM-dd"
    const val FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND = "yyyy-MM-dd HH:mm:ss"

    @JvmStatic
    fun of(date: LocalDateTime?): Date? = date?.atZone(ZoneId.systemDefault())?.toInstant()?.let(Date::from)

    @JvmStatic
    fun of(date: Date?): LocalDateTime? = date?.toInstant()?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()) }

    @JvmStatic
    fun addTime(duration: Duration): Date = Date(System.currentTimeMillis() + duration.toMillis())

    @JvmStatic
    fun isExpired(time: LocalDateTime): Boolean = LocalDateTime.now().isAfter(time)

    @JvmStatic
    @JvmOverloads
    fun buildTime(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Date =
        of(LocalDateTime.of(year, month, day, hour, minute, second))!!

    @JvmStatic
    fun max(a: Date?, b: Date?): Date? = when {
        a == null -> b
        b == null -> a
        a > b -> a
        else -> b
    }

    @JvmStatic
    fun max(a: LocalDateTime?, b: LocalDateTime?): LocalDateTime? = when {
        a == null -> b
        b == null -> a
        a > b -> a
        else -> b
    }

    @JvmStatic
    fun isToday(date: LocalDateTime): Boolean = date.toLocalDate() == LocalDateTime.now().toLocalDate()

    @JvmStatic
    fun isYesterday(date: LocalDateTime): Boolean = date.toLocalDate() == LocalDateTime.now().toLocalDate().minusDays(1)
}

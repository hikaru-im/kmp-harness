package im.hikaru.ruoyi.framework.quartz.core.util

import org.quartz.CronExpression
import java.text.ParseException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Quartz Cron 表达式的工具类 (迁移自 Java, 去 Hutool LocalDateTimeUtil)
 *
 * 迁移说明：Hutool LocalDateTimeUtil.of(date) → Date.toInstant().atZone()
 *
 * @author 芋道源码
 */
object CronUtils {

    /**
     * 校验 CRON 表达式是否有效
     */
    @JvmStatic
    fun isValid(cronExpression: String?): Boolean =
        CronExpression.isValidExpression(cronExpression)

    /**
     * 基于 CRON 表达式，获得下 n 个满足执行的时间
     */
    @JvmStatic
    fun getNextTimes(cronExpression: String, n: Int): List<LocalDateTime> {
        // 1. 获得 CronExpression 对象
        val cron: CronExpression = try {
            CronExpression(cronExpression)
        } catch (e: ParseException) {
            throw IllegalArgumentException(e.message)
        }
        // 2. 从当前开始计算，n 个满足条件的
        var now = Date()
        val nextTimes = ArrayList<LocalDateTime>(n)
        for (i in 0 until n) {
            val nextTime = cron.getNextValidTimeAfter(now) ?: break
            nextTimes.add(toLocalDateTime(nextTime))
            now = nextTime
        }
        return nextTimes
    }

    private fun toLocalDateTime(date: Date): LocalDateTime =
        Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

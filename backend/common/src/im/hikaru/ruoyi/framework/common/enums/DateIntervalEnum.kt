package im.hikaru.ruoyi.framework.common.enums

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

/**
 * 时间间隔的枚举
 *
 * @author dhb52
 */
enum class DateIntervalEnum(
    val interval: Int,
    val label: String,
) : ArrayValuable<Int> {
    HOUR(0, "小时"), // 特殊：字典里，暂时不会有这个枚举！！！因为大多数情况下，用不到这个间隔
    DAY(1, "天"),
    WEEK(2, "周"),
    MONTH(3, "月"),
    QUARTER(4, "季度"),
    YEAR(5, "年"),
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        @JvmField
        val ARRAYS: Array<Int> = values().map { it.interval }.toTypedArray()

        @JvmStatic
        fun valueOf(interval: Int?): DateIntervalEnum? = values().firstOrNull { it.interval == interval }
    }
}

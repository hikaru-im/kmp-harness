package im.hikaru.ruoyi.framework.common.util.number

import java.math.BigDecimal
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object NumberUtils {
    @JvmStatic fun parseLong(value: String?): Long? = value?.takeIf(String::isNotEmpty)?.toLong()
    @JvmStatic fun parseInt(value: String?): Int? = value?.takeIf(String::isNotEmpty)?.toInt()
    @JvmStatic fun isAllNumber(values: List<String>?): Boolean = !values.isNullOrEmpty() && values.all { it.toBigDecimalOrNull() != null }
    @JvmStatic fun getDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val a = radLat1 - radLat2
        val b = Math.toRadians(lng1) - Math.toRadians(lng2)
        val distance = 2 * asin(sqrt(sin(a / 2).pow(2) + cos(radLat1) * cos(radLat2) * sin(b / 2).pow(2))) * 6378.137
        return round(distance * 10000.0) / 10000.0
    }
    @JvmStatic fun mul(vararg values: BigDecimal?): BigDecimal? =
        if (values.any { it == null }) null else values.filterNotNull().fold(BigDecimal.ONE, BigDecimal::multiply)
}

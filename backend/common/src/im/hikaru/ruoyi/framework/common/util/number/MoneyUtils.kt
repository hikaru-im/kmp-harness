package im.hikaru.ruoyi.framework.common.util.number

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyUtils {
    private const val PRICE_SCALE = 2
    @JvmField val PERCENT_100: BigDecimal = BigDecimal.valueOf(100)

    @JvmStatic fun calculateRatePrice(price: Int, rate: Double): Int =
        calculateRatePrice(price, rate, 0, RoundingMode.HALF_UP).toInt()
    @JvmStatic fun calculateRatePriceFloor(price: Int, rate: Double): Int =
        calculateRatePrice(price, rate, 0, RoundingMode.FLOOR).toInt()
    @JvmStatic fun calculator(price: Int, count: Int, percent: Int?): Int {
        val total = price * count
        return if (percent == null) total else calculateRatePriceFloor(total, percent / 100.0)
    }
    @JvmStatic fun calculateRatePrice(price: Number, rate: Number, scale: Int, roundingMode: RoundingMode): BigDecimal =
        price.toBigDecimal().multiply(rate.toBigDecimal()).divide(PERCENT_100, scale, roundingMode)
    @JvmStatic fun fenToYuan(fen: Int): BigDecimal = BigDecimal.valueOf(fen.toLong(), PRICE_SCALE)
    @JvmStatic fun fenToYuanStr(fen: Int): String = fenToYuan(fen).setScale(PRICE_SCALE).toPlainString()
    @JvmStatic fun priceMultiply(price: BigDecimal?, count: BigDecimal?): BigDecimal? =
        if (price == null || count == null) null else price.multiply(count).setScale(PRICE_SCALE, RoundingMode.HALF_UP)
    @JvmStatic fun priceMultiplyPercent(price: BigDecimal?, percent: BigDecimal?): BigDecimal? =
        if (price == null || percent == null) null else price.multiply(percent).divide(PERCENT_100, PRICE_SCALE, RoundingMode.HALF_UP)

    private fun Number.toBigDecimal(): BigDecimal = when (this) {
        is BigDecimal -> this
        else -> toString().toBigDecimal()
    }
}

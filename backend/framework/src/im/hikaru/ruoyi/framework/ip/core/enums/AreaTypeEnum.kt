package im.hikaru.ruoyi.framework.ip.core.enums

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

/**
 * 区域类型枚举 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
enum class AreaTypeEnum(
    val type: Int,
    val label: String,
) : ArrayValuable<Int> {
    COUNTRY(1, "国家"),
    PROVINCE(2, "省份"),
    CITY(3, "城市"),
    DISTRICT(4, "地区"), // 县、镇、区等
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        @JvmField
        val ARRAYS: Array<Int> = values().map { it.type }.toTypedArray()
    }
}

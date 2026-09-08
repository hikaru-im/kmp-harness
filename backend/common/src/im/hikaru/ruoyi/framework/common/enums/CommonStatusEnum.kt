package im.hikaru.ruoyi.framework.common.enums

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

/**
 * 通用状态枚举
 *
 * @author 芋道源码
 */
enum class CommonStatusEnum(
    val status: Int,
    val label: String,
) : ArrayValuable<Int> {
    ENABLE(0, "开启"),
    DISABLE(1, "关闭"),
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        @JvmField
        val ARRAYS: Array<Int> = values().map { it.status }.toTypedArray()

        @JvmStatic
        fun isEnable(status: Int?): Boolean = ENABLE.status == status

        @JvmStatic
        fun isDisable(status: Int?): Boolean = DISABLE.status == status
    }
}

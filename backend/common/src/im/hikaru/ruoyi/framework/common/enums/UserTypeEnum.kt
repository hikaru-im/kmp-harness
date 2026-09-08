package im.hikaru.ruoyi.framework.common.enums

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

/**
 * 全局用户类型枚举
 */
enum class UserTypeEnum(
    val value: Int,
    val label: String,
) : ArrayValuable<Int> {
    MEMBER(1, "会员"), // 面向 c 端，普通用户
    ADMIN(2, "管理员"), // 面向 b 端，管理后台
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        @JvmField
        val ARRAYS: Array<Int> = values().map { it.value }.toTypedArray()

        @JvmStatic
        fun valueOf(value: Int?): UserTypeEnum? = values().firstOrNull { it.value == value }
    }
}

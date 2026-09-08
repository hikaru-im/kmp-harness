package im.hikaru.ruoyi.module.system.enums.social

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class SocialTypeEnum(val type: Int, val source: String) : ArrayValuable<Int> {
    GITEE(10, "GITEE"),
    DINGTALK(20, "DINGTALK"),
    WECHAT_ENTERPRISE(30, "WECHAT_ENTERPRISE"),
    WECHAT_MP(31, "WECHAT_MP"),
    WECHAT_OPEN(32, "WECHAT_OPEN"),
    WECHAT_MINI_PROGRAM(34, "WECHAT_MINI_PROGRAM"),
    ALIPAY_MINI_PROGRAM(40, "ALIPAY");

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.type }.toTypedArray()

        fun valueOfType(type: Int?): SocialTypeEnum? = entries.firstOrNull { it.type == type }
    }
}

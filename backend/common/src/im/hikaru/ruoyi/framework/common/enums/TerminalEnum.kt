package im.hikaru.ruoyi.framework.common.enums

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

/**
 * 终端的枚举
 *
 * @author 芋道源码
 */
enum class TerminalEnum(
    val terminal: Int,
    val label: String,
) : ArrayValuable<Int> {
    UNKNOWN(0, "未知"), // 目的：在无法解析到 terminal 时，使用它
    WECHAT_MINI_PROGRAM(10, "微信小程序"),
    WECHAT_WAP(11, "微信公众号"),
    H5(20, "H5 网页"),
    APP(31, "手机 App"),
    ;

    override fun array(): Array<Int> = ARRAYS

    companion object {
        @JvmField
        val ARRAYS: Array<Int> = values().map { it.terminal }.toTypedArray()
    }
}

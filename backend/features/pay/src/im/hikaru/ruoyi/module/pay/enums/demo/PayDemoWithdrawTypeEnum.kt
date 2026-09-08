package im.hikaru.ruoyi.module.pay.enums.demo

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class PayDemoWithdrawTypeEnum(val type: Int, val displayName: String) : ArrayValuable<Int> {
    WECHAT(2, "微信"),
    ALIPAY(1, "支付宝"),
    WALLET(3, "钱包");

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.type }.toTypedArray()
    }
}

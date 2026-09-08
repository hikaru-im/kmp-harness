package im.hikaru.ruoyi.module.pay.enums.wallet

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class PayWalletBizTypeEnum(val type: Int, val description: String) : ArrayValuable<Int> {
    RECHARGE(1, "充值"),
    RECHARGE_REFUND(2, "充值退款"),
    PAYMENT(3, "支付"),
    PAYMENT_REFUND(4, "支付退款"),
    UPDATE_BALANCE(5, "更新余额"),
    TRANSFER(6, "转账");

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.type }.toTypedArray()
        fun valueOf(type: Int?): PayWalletBizTypeEnum? = entries.firstOrNull { it.type == type }
    }
}

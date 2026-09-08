package im.hikaru.ruoyi.module.pay.enums.notify

enum class PayNotifyTypeEnum(val type: Int, val displayName: String) {
    ORDER(1, "支付单"),
    REFUND(2, "退款单"),
    TRANSFER(3, "转账单");
}

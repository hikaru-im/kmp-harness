package im.hikaru.ruoyi.module.pay.enums.order

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class PayOrderStatusEnum(val status: Int, val displayName: String) : ArrayValuable<Int> {
    WAITING(0, "未支付"),
    SUCCESS(10, "支付成功"),
    REFUND(20, "已退款"),
    CLOSED(30, "支付关闭");

    override fun array(): Array<Int> = emptyArray()

    companion object {
        fun isWaiting(status: Int?): Boolean = status == WAITING.status
        fun isSuccess(status: Int?): Boolean = status == SUCCESS.status
        fun isRefund(status: Int?): Boolean = status == REFUND.status
        fun isSuccessOrRefund(status: Int?): Boolean = status == SUCCESS.status || status == REFUND.status
        fun isClosed(status: Int?): Boolean = status == CLOSED.status
    }
}

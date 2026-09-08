package im.hikaru.ruoyi.module.pay.enums.refund

enum class PayRefundStatusEnum(val status: Int, val displayName: String) {
    WAITING(0, "未退款"),
    SUCCESS(10, "退款成功"),
    FAILURE(20, "退款失败");
    companion object {
        fun isWaiting(value: Int?): Boolean = value == WAITING.status
        fun isSuccess(value: Int?): Boolean = value == SUCCESS.status
        fun isFailure(value: Int?): Boolean = value == FAILURE.status
    }
}

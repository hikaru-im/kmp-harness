package im.hikaru.ruoyi.module.pay.enums.transfer

enum class PayTransferStatusEnum(val status: Int, val displayName: String) {
    WAITING(0, "等待转账"),
    PROCESSING(5, "转账进行中"),
    SUCCESS(10, "转账成功"),
    CLOSED(20, "转账关闭");
    companion object {
        fun isWaiting(value: Int?): Boolean = value == WAITING.status
        fun isProcessing(value: Int?): Boolean = value == PROCESSING.status
        fun isSuccess(value: Int?): Boolean = value == SUCCESS.status
        fun isClosed(value: Int?): Boolean = value == CLOSED.status
        fun isWaitingOrProcessing(value: Int?): Boolean = isWaiting(value) || isProcessing(value)
        fun isSuccessOrClosed(value: Int?): Boolean = isSuccess(value) || isClosed(value)
    }
}

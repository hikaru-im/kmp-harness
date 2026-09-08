package im.hikaru.ruoyi.module.pay.enums.demo

enum class PayDemoWithdrawStatusEnum(val status: Int, val displayName: String) {
    WAITING(0, "Waiting"),
    SUCCESS(10, "Success"),
    CLOSED(20, "Closed");

    companion object {
        fun isWaiting(value: Int?): Boolean = value == WAITING.status
        fun isSuccess(value: Int?): Boolean = value == SUCCESS.status
        fun isClosed(value: Int?): Boolean = value == CLOSED.status
    }
}

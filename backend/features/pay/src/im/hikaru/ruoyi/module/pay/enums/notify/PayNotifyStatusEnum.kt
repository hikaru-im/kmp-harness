package im.hikaru.ruoyi.module.pay.enums.notify

enum class PayNotifyStatusEnum(val status: Int, val displayName: String) {
    WAITING(0, "等待通知"),
    SUCCESS(10, "通知成功"),
    FAILURE(20, "通知失败"),
    REQUEST_SUCCESS(21, "请求成功，但是结果失败"),
    REQUEST_FAILURE(22, "请求失败");
}

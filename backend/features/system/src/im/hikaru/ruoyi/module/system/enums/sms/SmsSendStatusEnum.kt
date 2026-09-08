package im.hikaru.ruoyi.module.system.enums.sms

enum class SmsSendStatusEnum(val status: Int) {
    INIT(0),
    SUCCESS(10),
    FAILURE(20),
    IGNORE(30),
}

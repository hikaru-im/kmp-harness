package im.hikaru.ruoyi.module.system.enums.mail

enum class MailSendStatusEnum(val status: Int) {
    INIT(0),
    SUCCESS(10),
    FAILURE(20),
    IGNORE(30),
}

package im.hikaru.ruoyi.module.system.enums.logger

enum class LoginResultEnum(val result: Int) {
    SUCCESS(0),
    BAD_CREDENTIALS(10),
    USER_DISABLED(20),
    CAPTCHA_NOT_FOUND(30),
    CAPTCHA_CODE_ERROR(31),
}

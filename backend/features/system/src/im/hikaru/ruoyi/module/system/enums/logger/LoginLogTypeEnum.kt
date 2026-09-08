package im.hikaru.ruoyi.module.system.enums.logger

enum class LoginLogTypeEnum(val type: Int) {
    LOGIN_USERNAME(100),
    LOGIN_SOCIAL(101),
    LOGIN_MOBILE(103),
    LOGIN_SMS(104),
    LOGOUT_SELF(200),
    LOGOUT_DELETE(202),
}

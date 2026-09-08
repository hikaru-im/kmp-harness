package im.hikaru.ruoyi.module.system.enums.sms

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class SmsSceneEnum(
    val scene: Int,
    val templateCode: String,
    val description: String,
) : ArrayValuable<Int> {
    MEMBER_LOGIN(1, "user-sms-login", "Member user - phone login"),
    MEMBER_UPDATE_MOBILE(2, "user-update-mobile", "Member user - update phone"),
    MEMBER_UPDATE_PASSWORD(3, "user-update-password", "Member user - update password"),
    MEMBER_RESET_PASSWORD(4, "user-reset-password", "Member user - reset password"),
    ADMIN_MEMBER_LOGIN(21, "admin-sms-login", "Admin user - phone login"),
    ADMIN_MEMBER_REGISTER(22, "admin-sms-register", "Admin user - phone registration"),
    ADMIN_MEMBER_RESET_PASSWORD(23, "admin-reset-password", "Admin user - reset password");

    override fun array(): Array<Int> = ARRAYS

    companion object {
        val ARRAYS: Array<Int> = entries.map { it.scene }.toTypedArray()

        fun getCodeByScene(scene: Int?): SmsSceneEnum? = entries.firstOrNull { it.scene == scene }
    }
}

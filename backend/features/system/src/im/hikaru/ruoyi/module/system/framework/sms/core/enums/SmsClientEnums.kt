package im.hikaru.ruoyi.module.system.framework.sms.core.enums

enum class SmsChannelEnum(val code: String) {
    DEBUG_DING_TALK("DEBUG_DING_TALK"),
    ALIYUN("ALIYUN"),
    TENCENT("TENCENT"),
    HUAWEI("HUAWEI"),
    QINIU("QINIU");

    companion object {
        fun fromCode(code: String?): SmsChannelEnum? = entries.firstOrNull { it.code == code }
    }
}

enum class SmsTemplateAuditStatusEnum(val status: Int) {
    CHECKING(1),
    SUCCESS(2),
    FAIL(3),
}

package im.hikaru.ruoyi.module.system.framework.sms.core.property

data class SmsChannelProperties(
    var id: Long? = null,
    var signature: String? = null,
    var code: String? = null,
    var apiKey: String? = null,
    var apiSecret: String? = null,
    var callbackUrl: String? = null,
)

package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.module.system.mq.message.sms.SmsSendMessage

interface SmsSendService {
    fun sendSingleSmsToAdmin(
        mobile: String?,
        userId: Long?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Long

    fun sendSingleSmsToMember(
        mobile: String?,
        userId: Long?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Long

    fun sendSingleSms(
        mobile: String?,
        userId: Long?,
        userType: Int?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Long

    fun sendBatchSms(
        mobiles: List<String>,
        userIds: List<Long>,
        userType: Int?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Unit = throw UnsupportedOperationException("Batch SMS sending is not supported yet")

    fun doSendSms(message: SmsSendMessage)
    fun receiveSmsStatus(channelCode: String, text: String)
}

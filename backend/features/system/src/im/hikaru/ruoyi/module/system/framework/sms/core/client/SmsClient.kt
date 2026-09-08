package im.hikaru.ruoyi.module.system.framework.sms.core.client

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsReceiveRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsSendRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.client.dto.SmsTemplateRespDTO
import im.hikaru.ruoyi.module.system.framework.sms.core.property.SmsChannelProperties

interface SmsClient {
    val id: Long?
    fun sendSms(logId: Long, mobile: String, apiTemplateId: String, templateParams: List<KeyValue<String, Any?>>): SmsSendRespDTO
    fun parseSmsReceiveStatus(text: String): List<SmsReceiveRespDTO>
    fun getSmsTemplate(apiTemplateId: String): SmsTemplateRespDTO?
}

interface SmsClientFactory {
    fun getSmsClient(channelId: Long): SmsClient?
    fun getSmsClient(channelCode: String): SmsClient?
    fun createOrUpdateSmsClient(properties: SmsChannelProperties): SmsClient
}

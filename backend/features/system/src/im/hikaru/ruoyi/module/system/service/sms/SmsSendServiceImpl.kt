package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_CHANNEL_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_SEND_MOBILE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_SEND_MOBILE_TEMPLATE_PARAM_MISS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SMS_SEND_TEMPLATE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.mq.message.sms.SmsSendMessage
import im.hikaru.ruoyi.module.system.mq.producer.sms.SmsDispatchPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class SmsSendServiceImpl(
    private val smsUserMobileResolver: SmsUserMobileResolver,
    private val smsChannelService: SmsChannelService,
    private val smsTemplateService: SmsTemplateService,
    private val smsLogService: SmsLogService,
    private val smsDispatchPublisher: SmsDispatchPublisher,
) : SmsSendService {
    override fun sendSingleSmsToAdmin(
        mobile: String?,
        userId: Long?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Long {
        val resolvedMobile = mobile?.takeIf(String::isNotBlank)
            ?: smsUserMobileResolver.resolve(userId, UserTypeEnum.ADMIN.value)
        return sendSingleSms(resolvedMobile, userId, UserTypeEnum.ADMIN.value, templateCode, templateParams)
    }

    override fun sendSingleSmsToMember(
        mobile: String?,
        userId: Long?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Long {
        val resolvedMobile = mobile?.takeIf(String::isNotBlank)
            ?: smsUserMobileResolver.resolve(userId, UserTypeEnum.MEMBER.value)
        return sendSingleSms(resolvedMobile, userId, UserTypeEnum.MEMBER.value, templateCode, templateParams)
    }

    override fun sendSingleSms(
        mobile: String?,
        userId: Long?,
        userType: Int?,
        templateCode: String,
        templateParams: Map<String, Any?>,
    ): Long {
        val template = validateSmsTemplate(templateCode)
        val channel = validateSmsChannel(requireNotNull(template.channelId))
        val resolvedMobile = validateMobile(mobile)
        val orderedParams = buildTemplateParams(template, templateParams)
        val shouldSend = CommonStatusEnum.isEnable(template.status) && CommonStatusEnum.isEnable(channel.status)
        val content = smsTemplateService.formatSmsTemplateContent(requireNotNull(template.content), templateParams)
        val logId = smsLogService.createSmsLog(
            resolvedMobile, userId, userType, shouldSend, template, content, templateParams,
        )
        if (shouldSend) {
            smsDispatchPublisher.publish(SmsSendMessage(
                logId = logId,
                mobile = resolvedMobile,
                channelId = requireNotNull(template.channelId),
                apiTemplateId = requireNotNull(template.apiTemplateId),
                templateParams = orderedParams,
            ))
        }
        return logId
    }

    override fun doSendSms(message: SmsSendMessage) {
        val client = smsChannelService.getSmsClient(message.channelId)
        if (client == null) {
            smsLogService.updateSmsSendResult(message.logId, false, "CLIENT_NOT_FOUND", "SMS client does not exist", null, null)
            return
        }
        try {
            val response = client.sendSms(
                message.logId, message.mobile, message.apiTemplateId, message.templateParams,
            )
            smsLogService.updateSmsSendResult(
                message.logId,
                response.success,
                response.apiCode,
                response.apiMsg,
                response.apiRequestId,
                response.serialNo,
            )
        } catch (ex: Exception) {
            log.error("Failed to send SMS log {}", message.logId, ex)
            smsLogService.updateSmsSendResult(
                message.logId, false, "EXCEPTION", rootMessage(ex), null, null,
            )
        }
    }

    override fun receiveSmsStatus(channelCode: String, text: String) {
        val client = smsChannelService.getSmsClient(channelCode) ?: throw exception(SMS_CHANNEL_NOT_EXISTS)
        client.parseSmsReceiveStatus(text).forEach { result ->
            smsLogService.updateSmsReceiveResult(
                result.logId,
                result.serialNo,
                result.success,
                result.receiveTime,
                result.errorCode,
                result.errorMsg,
            )
        }
    }

    internal fun validateSmsChannel(channelId: Long): SmsChannelDO =
        smsChannelService.getSmsChannel(channelId) ?: throw exception(SMS_CHANNEL_NOT_EXISTS)

    internal fun validateSmsTemplate(templateCode: String): SmsTemplateDO =
        smsTemplateService.getSmsTemplateByCodeFromCache(templateCode) ?: throw exception(SMS_SEND_TEMPLATE_NOT_EXISTS)

    internal fun buildTemplateParams(
        template: SmsTemplateDO,
        templateParams: Map<String, Any?>,
    ): List<KeyValue<String, Any?>> = template.params.orEmpty().map { key ->
        val value = templateParams[key] ?: throw exception(SMS_SEND_MOBILE_TEMPLATE_PARAM_MISS, key)
        KeyValue(key, value)
    }

    internal fun validateMobile(mobile: String?): String =
        mobile?.takeIf(String::isNotBlank) ?: throw exception(SMS_SEND_MOBILE_NOT_EXISTS)

    private fun rootMessage(ex: Throwable): String = generateSequence(ex) { it.cause }.last().message ?: ex.toString()

    private companion object {
        val log = LoggerFactory.getLogger(SmsSendServiceImpl::class.java)
    }
}

package im.hikaru.ruoyi.module.system.service.notify

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.NOTIFY_SEND_TEMPLATE_PARAM_MISS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.NOTIFY_TEMPLATE_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class NotifySendServiceImpl(
    private val notifyTemplateService: NotifyTemplateService,
    private val notifyMessageService: NotifyMessageService,
) : NotifySendService {
    override fun sendSingleNotifyToAdmin(userId: Long, templateCode: String, templateParams: Map<String, Any?>?): Long? =
        sendSingleNotify(userId, UserTypeEnum.ADMIN.value, templateCode, templateParams)

    override fun sendSingleNotifyToMember(userId: Long, templateCode: String, templateParams: Map<String, Any?>?): Long? =
        sendSingleNotify(userId, UserTypeEnum.MEMBER.value, templateCode, templateParams)

    override fun sendSingleNotify(
        userId: Long,
        userType: Int,
        templateCode: String,
        templateParams: Map<String, Any?>?,
    ): Long? {
        val template = validateNotifyTemplate(templateCode)
        if (CommonStatusEnum.isDisable(template.status)) return null
        val params = templateParams.orEmpty()
        validateTemplateParams(template, params)
        val content = notifyTemplateService.formatNotifyTemplateContent(requireNotNull(template.content), params)
        return notifyMessageService.createNotifyMessage(userId, userType, template, content, params)
    }

    internal fun validateNotifyTemplate(templateCode: String): NotifyTemplateDO =
        notifyTemplateService.getNotifyTemplateByCodeFromCache(templateCode)
            ?: throw ServiceExceptionUtil.exception(NOTIFY_TEMPLATE_NOT_EXISTS)

    internal fun validateTemplateParams(template: NotifyTemplateDO, params: Map<String, Any?>) {
        template.params.orEmpty().forEach { key ->
            if (params[key] == null) throw ServiceExceptionUtil.exception(NOTIFY_SEND_TEMPLATE_PARAM_MISS, key)
        }
    }
}

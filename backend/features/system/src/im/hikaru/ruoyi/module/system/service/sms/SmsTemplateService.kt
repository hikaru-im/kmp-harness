package im.hikaru.ruoyi.module.system.service.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO

interface SmsTemplateService {
    fun createSmsTemplate(req: SmsTemplateSaveReqVO): Long
    fun updateSmsTemplate(req: SmsTemplateSaveReqVO)
    fun deleteSmsTemplate(id: Long)
    fun deleteSmsTemplateList(ids: List<Long>)
    fun getSmsTemplate(id: Long): SmsTemplateDO?
    fun getSmsTemplateByCodeFromCache(code: String): SmsTemplateDO?
    fun getSmsTemplatePage(req: SmsTemplatePageReqVO): PageResult<SmsTemplateDO>
    fun getSmsTemplateListByStatus(status: Int): List<SmsTemplateDO>
    fun getSmsTemplateCountByChannelId(channelId: Long): Long
    fun formatSmsTemplateContent(content: String, params: Map<String, Any?>): String
}

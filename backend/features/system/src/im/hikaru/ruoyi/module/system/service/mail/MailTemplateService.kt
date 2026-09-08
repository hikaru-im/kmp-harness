package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplatePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplateSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO

interface MailTemplateService {
    fun createMailTemplate(req: MailTemplateSaveReqVO): Long
    fun updateMailTemplate(req: MailTemplateSaveReqVO)
    fun deleteMailTemplate(id: Long)
    fun deleteMailTemplateList(ids: Collection<Long>)
    fun getMailTemplate(id: Long): MailTemplateDO?
    fun getMailTemplateByCodeFromCache(code: String): MailTemplateDO?
    fun getMailTemplatePage(req: MailTemplatePageReqVO): PageResult<MailTemplateDO>
    fun getMailTemplateList(): List<MailTemplateDO>
    fun getMailTemplateListByStatus(status: Int): List<MailTemplateDO>
    fun formatMailTemplateContent(content: String, params: Map<String, Any?>): String
    fun getMailTemplateCountByAccountId(accountId: Long): Long
}

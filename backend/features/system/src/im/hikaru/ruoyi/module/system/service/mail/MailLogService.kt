package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log.MailLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailLogDO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO

interface MailLogService {
    fun getMailLogPage(req: MailLogPageReqVO): PageResult<MailLogDO>
    fun getMailLog(id: Long): MailLogDO?
    fun createMailLog(
        userId: Long?, userType: Int?,
        toMails: Collection<String>, ccMails: Collection<String>, bccMails: Collection<String>,
        account: MailAccountDO, template: MailTemplateDO,
        templateContent: String, templateParams: Map<String, Any?>, isSend: Boolean,
    ): Long
    fun updateMailSendResult(logId: Long, messageId: String?, exception: Exception?)
}

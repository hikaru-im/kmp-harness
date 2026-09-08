package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log.MailLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailLogDO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO
import im.hikaru.ruoyi.module.system.dal.mysql.mail.MailLogDao
import im.hikaru.ruoyi.module.system.enums.mail.MailSendStatusEnum
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MailLogServiceImpl : MailLogService {
    override fun getMailLogPage(req: MailLogPageReqVO): PageResult<MailLogDO> = MailLogDao.selectPage(req)

    override fun getMailLog(id: Long): MailLogDO? = MailLogDao.selectById(id)

    override fun createMailLog(
        userId: Long?, userType: Int?,
        toMails: Collection<String>, ccMails: Collection<String>, bccMails: Collection<String>,
        account: MailAccountDO, template: MailTemplateDO,
        templateContent: String, templateParams: Map<String, Any?>, isSend: Boolean,
    ): Long = MailLogDao.insert(MailLogDO().apply {
        this.userId = userId
        this.userType = userType
        this.toMails = toMails.toList()
        this.ccMails = ccMails.toList()
        this.bccMails = bccMails.toList()
        accountId = account.id
        fromMail = account.mail
        templateId = template.id
        templateCode = template.code
        templateNickname = template.nickname
        templateTitle = template.title
        this.templateContent = templateContent
        this.templateParams = templateParams
        sendStatus = if (isSend) MailSendStatusEnum.INIT.status else MailSendStatusEnum.IGNORE.status
    })

    override fun updateMailSendResult(logId: Long, messageId: String?, exception: Exception?) {
        val status = if (exception == null) MailSendStatusEnum.SUCCESS.status else MailSendStatusEnum.FAILURE.status
        MailLogDao.updateSendResult(logId, status, messageId, exception?.rootCauseMessage())
    }

    private fun Throwable.rootCauseMessage(): String {
        var root = this
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return root.message ?: root.javaClass.simpleName
    }
}

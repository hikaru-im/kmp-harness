package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailAccountDO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_ACCOUNT_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_SEND_MAIL_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_SEND_TEMPLATE_PARAM_MISS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MAIL_TEMPLATE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.mq.message.mail.MailSendMessage
import im.hikaru.ruoyi.module.system.mq.producer.mail.MailDispatchPublisher
import jakarta.mail.internet.InternetAddress
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.io.File
import java.util.Properties

@Service
@Validated
class MailSendServiceImpl(
    private val mailUserEmailResolver: MailUserEmailResolver,
    private val mailAccountService: MailAccountService,
    private val mailTemplateService: MailTemplateService,
    private val mailLogService: MailLogService,
    private val mailDispatchPublisher: MailDispatchPublisher,
) : MailSendService {
    override fun sendSingleMail(
        toMails: Collection<String>?, ccMails: Collection<String>?, bccMails: Collection<String>?,
        userId: Long?, userType: Int?, templateCode: String, templateParams: Map<String, Any?>?,
        attachments: Array<File>?,
    ): Long {
        val template = validateMailTemplate(templateCode)
        val account = validateMailAccount(requireNotNull(template.accountId))
        val params = templateParams.orEmpty()
        validateTemplateParams(template, params)

        val to = linkedSetOf<String>()
        mailUserEmailResolver.resolve(userId, userType)?.takeIf(::isValidEmail)?.let(to::add)
        toMails.orEmpty().filter(::isValidEmail).forEach(to::add)
        val cc = ccMails.orEmpty().filterTo(linkedSetOf(), ::isValidEmail)
        val bcc = bccMails.orEmpty().filterTo(linkedSetOf(), ::isValidEmail)
        if (to.isEmpty()) throw ServiceExceptionUtil.exception(MAIL_SEND_MAIL_NOT_EXISTS)

        val shouldSend = CommonStatusEnum.isEnable(template.status)
        val title = mailTemplateService.formatMailTemplateContent(requireNotNull(template.title), params)
        val content = mailTemplateService.formatMailTemplateContent(requireNotNull(template.content), params)
        val logId = mailLogService.createMailLog(
            userId, userType, to, cc, bcc, account, template, content, params, shouldSend,
        )
        if (shouldSend) {
            mailDispatchPublisher.publish(MailSendMessage(
                logId = logId,
                toMails = to,
                ccMails = cc,
                bccMails = bcc,
                accountId = requireNotNull(account.id),
                nickname = template.nickname,
                title = title,
                content = content,
                attachments = attachments?.copyOf() ?: emptyArray(),
            ))
        }
        return logId
    }

    override fun doSendMail(message: MailSendMessage) {
        val account = validateMailAccount(message.accountId)
        try {
            val sender = buildMailSender(account)
            val mimeMessage = sender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, message.attachments.isNotEmpty(), Charsets.UTF_8.name())
            if (message.nickname.isNullOrBlank()) {
                helper.setFrom(requireNotNull(account.mail))
            } else {
                helper.setFrom(requireNotNull(account.mail), message.nickname)
            }
            helper.setTo(message.toMails.toTypedArray())
            if (message.ccMails.isNotEmpty()) helper.setCc(message.ccMails.toTypedArray())
            if (message.bccMails.isNotEmpty()) helper.setBcc(message.bccMails.toTypedArray())
            helper.setSubject(message.title)
            helper.setText(message.content, true)
            message.attachments.filter(File::exists).forEach { helper.addAttachment(it.name, it) }
            sender.send(mimeMessage)
            mailLogService.updateMailSendResult(message.logId, mimeMessage.messageID, null)
        } catch (exception: Exception) {
            mailLogService.updateMailSendResult(message.logId, null, exception)
        }
    }

    internal fun validateMailTemplate(templateCode: String): MailTemplateDO =
        mailTemplateService.getMailTemplateByCodeFromCache(templateCode)
            ?: throw ServiceExceptionUtil.exception(MAIL_TEMPLATE_NOT_EXISTS)

    internal fun validateMailAccount(accountId: Long): MailAccountDO =
        mailAccountService.getMailAccountFromCache(accountId)
            ?: throw ServiceExceptionUtil.exception(MAIL_ACCOUNT_NOT_EXISTS)

    internal fun validateTemplateParams(template: MailTemplateDO, params: Map<String, Any?>) {
        template.params.orEmpty().forEach { key ->
            if (params[key] == null) throw ServiceExceptionUtil.exception(MAIL_SEND_TEMPLATE_PARAM_MISS, key)
        }
    }

    private fun isValidEmail(value: String): Boolean = runCatching {
        InternetAddress(value).validate()
        value.contains('@')
    }.getOrDefault(false)

    private fun buildMailSender(account: MailAccountDO) = JavaMailSenderImpl().apply {
        host = requireNotNull(account.host)
        port = requireNotNull(account.port)
        username = requireNotNull(account.username)
        password = requireNotNull(account.password)
        javaMailProperties = Properties().apply {
            this["mail.smtp.auth"] = "true"
            this["mail.smtp.ssl.enable"] = account.sslEnable.toString()
            this["mail.smtp.starttls.enable"] = account.starttlsEnable.toString()
        }
    }
}

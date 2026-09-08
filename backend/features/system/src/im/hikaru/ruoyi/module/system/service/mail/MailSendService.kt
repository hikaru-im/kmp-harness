package im.hikaru.ruoyi.module.system.service.mail

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.module.system.mq.message.mail.MailSendMessage
import java.io.File

interface MailSendService {
    fun sendSingleMailToAdmin(
        userId: Long?, toMails: Collection<String>?, ccMails: Collection<String>?, bccMails: Collection<String>?,
        templateCode: String, templateParams: Map<String, Any?>?, attachments: Array<File>? = null,
    ): Long = sendSingleMail(
        toMails, ccMails, bccMails, userId, UserTypeEnum.ADMIN.value,
        templateCode, templateParams, attachments,
    )

    fun sendSingleMailToMember(
        userId: Long?, toMails: Collection<String>?, ccMails: Collection<String>?, bccMails: Collection<String>?,
        templateCode: String, templateParams: Map<String, Any?>?, attachments: Array<File>? = null,
    ): Long = sendSingleMail(
        toMails, ccMails, bccMails, userId, UserTypeEnum.MEMBER.value,
        templateCode, templateParams, attachments,
    )

    fun sendSingleMail(
        toMails: Collection<String>?, ccMails: Collection<String>?, bccMails: Collection<String>?,
        userId: Long?, userType: Int?, templateCode: String, templateParams: Map<String, Any?>?,
        attachments: Array<File>? = null,
    ): Long

    fun doSendMail(message: MailSendMessage)
}

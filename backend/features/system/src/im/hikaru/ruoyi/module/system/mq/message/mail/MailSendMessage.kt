package im.hikaru.ruoyi.module.system.mq.message.mail

import java.io.File

data class MailSendMessage(
    val logId: Long,
    val toMails: Collection<String>,
    val ccMails: Collection<String>,
    val bccMails: Collection<String>,
    val accountId: Long,
    val nickname: String?,
    val title: String,
    val content: String,
    val attachments: Array<File> = emptyArray(),
)

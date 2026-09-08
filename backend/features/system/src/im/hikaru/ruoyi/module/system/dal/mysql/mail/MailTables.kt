package im.hikaru.ruoyi.module.system.dal.mysql.mail

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.module.system.dal.mysql.jsonAnyMap
import im.hikaru.ruoyi.module.system.dal.mysql.jsonStringList
import org.jetbrains.exposed.v1.datetime.datetime

object MailAccountTable : BaseTable("system_mail_account") {
    val id = long("id").autoIncrement("system_mail_account_seq")
    val mail = varchar("mail", 255)
    val username = varchar("username", 255)
    val password = varchar("password", 255)
    val host = varchar("host", 255)
    val port = integer("port")
    val sslEnable = bool("ssl_enable")
    val starttlsEnable = bool("starttls_enable")
    override val primaryKey = PrimaryKey(id)
}

object MailTemplateTable : BaseTable("system_mail_template") {
    val id = long("id").autoIncrement("system_mail_template_seq")
    val name = varchar("name", 63)
    val code = varchar("code", 63)
    val accountId = long("account_id")
    val nickname = varchar("nickname", 255).nullable()
    val title = varchar("title", 255)
    val content = varchar("content", 10240)
    val params = jsonStringList("params", 255)
    val status = integer("status")
    val remark = varchar("remark", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object MailLogTable : BaseTable("system_mail_log") {
    val id = long("id").autoIncrement("system_mail_log_seq")
    val userId = long("user_id").nullable()
    val userType = integer("user_type").nullable()
    val toMails = varchar("to_mails", 1024)
    val ccMails = varchar("cc_mails", 1024).nullable()
    val bccMails = varchar("bcc_mails", 1024).nullable()
    val accountId = long("account_id")
    val fromMail = varchar("from_mail", 255)
    val templateId = long("template_id")
    val templateCode = varchar("template_code", 63)
    val templateNickname = varchar("template_nickname", 255).nullable()
    val templateTitle = varchar("template_title", 255)
    val templateContent = text("template_content")
    val templateParams = jsonAnyMap("template_params", 255)
    val sendStatus = integer("send_status")
    val sendTime = datetime("send_time").nullable()
    val sendMessageId = varchar("send_message_id", 255).nullable()
    val sendException = varchar("send_exception", 4096).nullable()
    override val primaryKey = PrimaryKey(id)
}

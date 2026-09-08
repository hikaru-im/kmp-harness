package im.hikaru.ruoyi.module.system.dal.mysql.notify

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.module.system.dal.mysql.jsonAnyMap
import im.hikaru.ruoyi.module.system.dal.mysql.jsonStringList
import org.jetbrains.exposed.v1.datetime.datetime

object NotifyTemplateTable : BaseTable("system_notify_template") {
    val id = long("id").autoIncrement("system_notify_template_seq")
    val name = varchar("name", 63)
    val code = varchar("code", 64)
    val nickname = varchar("nickname", 255)
    val content = varchar("content", 1024)
    val type = integer("type")
    val params = jsonStringList("params", 255).nullable()
    val status = integer("status")
    val remark = varchar("remark", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object NotifyMessageTable : BaseTable("system_notify_message") {
    val id = long("id").autoIncrement("system_notify_message_seq")
    val userId = long("user_id")
    val userType = integer("user_type")
    val templateId = long("template_id")
    val templateCode = varchar("template_code", 64)
    val templateNickname = varchar("template_nickname", 63)
    val templateContent = varchar("template_content", 1024)
    val templateType = integer("template_type")
    val templateParams = jsonAnyMap("template_params", 255)
    val readStatus = bool("read_status")
    val readTime = datetime("read_time").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

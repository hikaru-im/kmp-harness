package im.hikaru.ruoyi.module.mp.dal.mysql.message

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MpMessageTemplateTable : BaseTable("mp_message_template") {
    val id = long("id").autoIncrement("mp_message_template_seq")
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val templateId = varchar("template_id", 128).nullable()
    val title = varchar("title", 255).nullable()
    val content = varchar("content", 2048).nullable()
    val example = varchar("example", 2048).nullable()
    val primaryIndustry = varchar("primary_industry", 255).nullable()
    val deputyIndustry = varchar("deputy_industry", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

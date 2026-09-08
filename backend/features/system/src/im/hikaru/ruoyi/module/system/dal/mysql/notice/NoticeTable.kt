package im.hikaru.ruoyi.module.system.dal.mysql.notice

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object NoticeTable : BaseTable("system_notice") {
    val id = long("id").autoIncrement("system_notice_seq")
    val title = varchar("title", 50)
    val content = text("content")
    val type = integer("type")
    val status = integer("status")
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

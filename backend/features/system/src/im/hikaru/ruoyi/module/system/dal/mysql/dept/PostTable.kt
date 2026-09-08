package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object PostTable : BaseTable("system_post") {
    val id = long("id").autoIncrement("system_post_seq")
    val code = varchar("code", 64)
    val name = varchar("name", 50)
    val sort = integer("sort")
    val status = integer("status")
    val remark = varchar("remark", 500).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.module.system.dal.mysql.jsonLongSet

object RoleTable : BaseTable("system_role") {
    val id = long("id").autoIncrement("system_role_seq"); val name = varchar("name", 30); val code = varchar("code", 100); val sort = integer("sort")
    val dataScope = integer("data_scope"); val dataScopeDeptIds = jsonLongSet("data_scope_dept_ids", 500); val status = integer("status"); val type = integer("type")
    val remark = varchar("remark", 500).nullable(); val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

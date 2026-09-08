package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object UserRoleTable : BaseTable("system_user_role") {
    val id = long("id").autoIncrement("system_user_role_seq"); val userId = long("user_id"); val roleId = long("role_id"); val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

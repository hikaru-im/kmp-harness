package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object RoleMenuTable : BaseTable("system_role_menu") {
    val id = long("id").autoIncrement("system_role_menu_seq"); val roleId = long("role_id"); val menuId = long("menu_id"); val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

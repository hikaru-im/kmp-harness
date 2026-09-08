package im.hikaru.ruoyi.module.system.dal.mysql.tenant

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.module.system.dal.mysql.jsonLongSet

object TenantPackageTable : BaseTable("system_tenant_package") {
    val id = long("id").autoIncrement("system_tenant_package_seq"); val name = varchar("name", 30); val status = integer("status"); val remark = varchar("remark", 256).nullable(); val menuIds = jsonLongSet("menu_ids", 4096)
    override val primaryKey = PrimaryKey(id)
}

package im.hikaru.ruoyi.module.system.dal.mysql.tenant

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.stringList
import org.jetbrains.exposed.v1.datetime.datetime

object TenantTable : BaseTable("system_tenant") {
    val id = long("id").autoIncrement("system_tenant_seq"); val name = varchar("name", 30); val contactUserId = long("contact_user_id").nullable(); val contactName = varchar("contact_name", 30)
    val contactMobile = varchar("contact_mobile", 500).nullable(); val status = integer("status"); val websites = stringList("websites", 1024).nullable(); val packageId = long("package_id")
    val expireTime = datetime("expire_time"); val accountCount = integer("account_count"); override val primaryKey = PrimaryKey(id)
}

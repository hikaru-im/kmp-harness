package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object DeptTable : BaseTable("system_dept") {
    val id = long("id").autoIncrement("system_dept_seq")
    val name = varchar("name", 30)
    val parentId = long("parent_id")
    val sort = integer("sort")
    val leaderUserId = long("leader_user_id").nullable()
    val phone = varchar("phone", 11).nullable()
    val email = varchar("email", 50).nullable()
    val status = integer("status")
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

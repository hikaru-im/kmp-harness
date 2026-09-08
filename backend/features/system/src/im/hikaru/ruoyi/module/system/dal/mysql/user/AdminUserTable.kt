package im.hikaru.ruoyi.module.system.dal.mysql.user

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.module.system.dal.mysql.jsonLongSet
import org.jetbrains.exposed.v1.datetime.datetime

object AdminUserTable : BaseTable("system_users") {
    val id = long("id").autoIncrement("system_users_seq")
    val username = varchar("username", 30)
    val password = varchar("password", 100)
    val nickname = varchar("nickname", 30)
    val remark = varchar("remark", 500).nullable()
    val deptId = long("dept_id").nullable()
    val postIds = jsonLongSet("post_ids", 255).nullable()
    val email = varchar("email", 50).nullable()
    val mobile = varchar("mobile", 11).nullable()
    val sex = integer("sex").nullable()
    val avatar = varchar("avatar", 512).nullable()
    val status = integer("status")
    val loginIp = varchar("login_ip", 50).nullable()
    val loginDate = datetime("login_date").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

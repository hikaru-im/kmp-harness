package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object UserPostTable : BaseTable("system_user_post") {
    val id = long("id").autoIncrement("system_user_post_seq")
    val userId = long("user_id")
    val postId = long("post_id")
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

package im.hikaru.ruoyi.module.member.dal.mysql.signin

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberSignInConfigTable : BaseTable("member_sign_in_config") {
    val id = long("id").autoIncrement("member_sign_in_config_seq")
    val day = integer("day").nullable()
    val point = integer("point").nullable()
    val experience = integer("experience").nullable()
    val status = integer("status").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

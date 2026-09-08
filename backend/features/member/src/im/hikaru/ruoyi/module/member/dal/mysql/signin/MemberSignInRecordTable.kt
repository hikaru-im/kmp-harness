package im.hikaru.ruoyi.module.member.dal.mysql.signin

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.date

object MemberSignInRecordTable : BaseTable("member_sign_in_record") {
    val id = long("id").autoIncrement("member_sign_in_record_seq")
    val userId = long("user_id").nullable()
    val day = integer("day").nullable()
    val point = integer("point").nullable()
    val experience = integer("experience").nullable()
    val signDate = date("sign_date").nullable()
    val tenantId = long("tenant_id")
    init {
        uniqueIndex("uk_member_sign_in_tenant_user_date", tenantId, userId, signDate)
    }
    override val primaryKey = PrimaryKey(id)
}

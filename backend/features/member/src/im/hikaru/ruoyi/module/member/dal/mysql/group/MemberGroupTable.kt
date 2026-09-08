package im.hikaru.ruoyi.module.member.dal.mysql.group

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberGroupTable : BaseTable("member_group") {
    val id = long("id").autoIncrement("member_group_seq")
    val name = varchar("name", 255).nullable()
    val remark = varchar("remark", 2048).nullable()
    val status = integer("status").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

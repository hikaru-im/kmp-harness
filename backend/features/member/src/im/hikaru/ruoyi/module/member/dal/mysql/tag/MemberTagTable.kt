package im.hikaru.ruoyi.module.member.dal.mysql.tag

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberTagTable : BaseTable("member_tag") {
    val id = long("id").autoIncrement("member_tag_seq")
    val name = varchar("name", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

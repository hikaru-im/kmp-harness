package im.hikaru.ruoyi.module.member.dal.mysql.point

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberPointRecordTable : BaseTable("member_point_record") {
    val id = long("id").autoIncrement("member_point_record_seq")
    val userId = long("user_id").nullable()
    val bizId = varchar("biz_id", 128).nullable()
    val bizType = integer("biz_type").nullable()
    val title = varchar("title", 255).nullable()
    val description = varchar("description", 2048).nullable()
    val point = integer("point").nullable()
    val totalPoint = integer("total_point").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

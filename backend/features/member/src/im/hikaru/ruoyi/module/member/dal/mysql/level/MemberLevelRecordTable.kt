package im.hikaru.ruoyi.module.member.dal.mysql.level

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberLevelRecordTable : BaseTable("member_level_record") {
    val id = long("id").autoIncrement("member_level_record_seq")
    val userId = long("user_id").nullable()
    val levelId = long("level_id").nullable()
    val level = integer("level").nullable()
    val discountPercent = integer("discount_percent").nullable()
    val experience = integer("experience").nullable()
    val userExperience = integer("user_experience").nullable()
    val remark = varchar("remark", 2048).nullable()
    val description = varchar("description", 2048).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

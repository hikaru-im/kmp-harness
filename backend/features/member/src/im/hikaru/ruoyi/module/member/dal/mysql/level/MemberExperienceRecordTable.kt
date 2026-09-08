package im.hikaru.ruoyi.module.member.dal.mysql.level

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberExperienceRecordTable : BaseTable("member_experience_record") {
    val id = long("id").autoIncrement("member_experience_record_seq")
    val userId = long("user_id").nullable()
    val bizType = integer("biz_type").nullable()
    val bizId = varchar("biz_id", 128).nullable()
    val title = varchar("title", 255).nullable()
    val description = varchar("description", 2048).nullable()
    val experience = integer("experience").nullable()
    val totalExperience = integer("total_experience").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

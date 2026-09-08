package im.hikaru.ruoyi.module.member.dal.mysql.level

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MemberLevelTable : BaseTable("member_level") {
    val id = long("id").autoIncrement("member_level_seq")
    val name = varchar("name", 255).nullable()
    val level = integer("level").nullable()
    val experience = integer("experience").nullable()
    val discountPercent = integer("discount_percent").nullable()
    val icon = varchar("icon", 255).nullable()
    val backgroundUrl = varchar("background_url", 2048).nullable()
    val status = integer("status").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

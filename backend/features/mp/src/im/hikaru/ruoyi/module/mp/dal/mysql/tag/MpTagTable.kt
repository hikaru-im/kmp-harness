package im.hikaru.ruoyi.module.mp.dal.mysql.tag

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MpTagTable : BaseTable("mp_tag") {
    val id = long("id").autoIncrement("mp_tag_seq")
    val tagId = long("tag_id").nullable()
    val name = varchar("name", 255).nullable()
    val count = integer("count").nullable()
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

package im.hikaru.ruoyi.module.mp.dal.mysql.material

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean

object MpMaterialTable : BaseTable("mp_material") {
    val id = long("id").autoIncrement("mp_material_seq")
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val mediaId = varchar("media_id", 128).nullable()
    val type = varchar("type", 128).nullable()
    val permanent = smallIntBoolean("permanent").nullable()
    val url = varchar("url", 2048).nullable()
    val name = varchar("name", 255).nullable()
    val mpUrl = varchar("mp_url", 2048).nullable()
    val title = varchar("title", 255).nullable()
    val introduction = varchar("introduction", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

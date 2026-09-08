package im.hikaru.ruoyi.module.mp.dal.mysql.user

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.longList
import org.jetbrains.exposed.v1.datetime.datetime

object MpUserTable : BaseTable("mp_user") {
    val id = long("id").autoIncrement("mp_user_seq")
    val openid = varchar("openid", 128).nullable()
    val unionId = varchar("union_id", 128).nullable()
    val subscribeStatus = integer("subscribe_status").nullable()
    val subscribeTime = datetime("subscribe_time").nullable()
    val unsubscribeTime = datetime("unsubscribe_time").nullable()
    val nickname = varchar("nickname", 255).nullable()
    val headImageUrl = varchar("head_image_url", 2048).nullable()
    val language = varchar("language", 255).nullable()
    val country = varchar("country", 255).nullable()
    val province = varchar("province", 255).nullable()
    val city = varchar("city", 255).nullable()
    val remark = varchar("remark", 2048).nullable()
    val tagIds = longList("tag_ids", 1024).nullable()
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

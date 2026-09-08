package im.hikaru.ruoyi.module.mp.dal.mysql.message

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.jsonList
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO

object MpMessageTable : BaseTable("mp_message") {
    val id = long("id").autoIncrement("mp_message_seq")
    val msgId = long("msg_id").nullable()
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val userId = long("user_id").nullable()
    val openid = varchar("openid", 128).nullable()
    val type = varchar("type", 128).nullable()
    val sendFrom = integer("send_from").nullable()
    val content = varchar("content", 2048).nullable()
    val mediaId = varchar("media_id", 128).nullable()
    val mediaUrl = varchar("media_url", 2048).nullable()
    val recognition = varchar("recognition", 255).nullable()
    val format = varchar("format", 255).nullable()
    val title = varchar("title", 255).nullable()
    val description = varchar("description", 2048).nullable()
    val thumbMediaId = varchar("thumb_media_id", 128).nullable()
    val thumbMediaUrl = varchar("thumb_media_url", 2048).nullable()
    val url = varchar("url", 2048).nullable()
    val locationX = double("location_x").nullable()
    val locationY = double("location_y").nullable()
    val scale = double("scale").nullable()
    val label = varchar("label", 255).nullable()
    val articles = jsonList("articles", MpMessageDO.Article::class.java, 8192).nullable()
    val musicUrl = varchar("music_url", 2048).nullable()
    val hqMusicUrl = varchar("hq_music_url", 2048).nullable()
    val event = varchar("event", 255).nullable()
    val eventKey = varchar("event_key", 255).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

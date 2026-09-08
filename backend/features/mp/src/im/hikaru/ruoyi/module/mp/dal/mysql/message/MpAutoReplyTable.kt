package im.hikaru.ruoyi.module.mp.dal.mysql.message

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.jsonList
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO

object MpAutoReplyTable : BaseTable("mp_auto_reply") {
    val id = long("id").autoIncrement("mp_auto_reply_seq")
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val type = integer("type").nullable()
    val requestKeyword = varchar("request_keyword", 255).nullable()
    val requestMatch = integer("request_match").nullable()
    val requestMessageType = varchar("request_message_type", 255).nullable()
    val responseMessageType = varchar("response_message_type", 255).nullable()
    val responseContent = varchar("response_content", 255).nullable()
    val responseMediaId = varchar("response_media_id", 128).nullable()
    val responseMediaUrl = varchar("response_media_url", 2048).nullable()
    val responseTitle = varchar("response_title", 255).nullable()
    val responseDescription = varchar("response_description", 255).nullable()
    val responseThumbMediaId = varchar("response_thumb_media_id", 128).nullable()
    val responseThumbMediaUrl = varchar("response_thumb_media_url", 2048).nullable()
    val responseArticles = jsonList("response_articles", MpMessageDO.Article::class.java, 8192).nullable()
    val responseMusicUrl = varchar("response_music_url", 2048).nullable()
    val responseHqMusicUrl = varchar("response_hq_music_url", 2048).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

package im.hikaru.ruoyi.module.mp.dal.mysql.menu

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.jsonList
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO

object MpMenuTable : BaseTable("mp_menu") {
    val id = long("id").autoIncrement("mp_menu_seq")
    val accountId = long("account_id").nullable()
    val appId = varchar("app_id", 128).nullable()
    val name = varchar("name", 255).nullable()
    val menuKey = varchar("menu_key", 255).nullable()
    val parentId = varchar("parent_id", 32).nullable()
    val type = varchar("type", 128).nullable()
    val url = varchar("url", 2048).nullable()
    val miniProgramAppId = varchar("mini_program_app_id", 128).nullable()
    val miniProgramPagePath = varchar("mini_program_page_path", 255).nullable()
    val articleId = varchar("article_id", 128).nullable()
    val replyMessageType = varchar("reply_message_type", 255).nullable()
    val replyContent = varchar("reply_content", 255).nullable()
    val replyMediaId = varchar("reply_media_id", 128).nullable()
    val replyMediaUrl = varchar("reply_media_url", 2048).nullable()
    val replyTitle = varchar("reply_title", 255).nullable()
    val replyDescription = varchar("reply_description", 255).nullable()
    val replyThumbMediaId = varchar("reply_thumb_media_id", 128).nullable()
    val replyThumbMediaUrl = varchar("reply_thumb_media_url", 2048).nullable()
    val replyArticles = jsonList("reply_articles", MpMessageDO.Article::class.java, 8192).nullable()
    val replyMusicUrl = varchar("reply_music_url", 2048).nullable()
    val replyHqMusicUrl = varchar("reply_hq_music_url", 2048).nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}

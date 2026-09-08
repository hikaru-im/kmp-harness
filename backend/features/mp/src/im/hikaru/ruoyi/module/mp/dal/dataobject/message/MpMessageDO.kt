package im.hikaru.ruoyi.module.mp.dal.dataobject.message

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.user.MpUserDO
import im.hikaru.ruoyi.module.mp.enums.message.MpMessageSendFromEnum
import jakarta.validation.constraints.NotEmpty
import java.io.Serializable
import kotlinx.datetime.LocalDateTime
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.mp.builder.kefu.NewsBuilder

class MpMessageDO : TenantBaseDO {
    var id: Long? = null
    var msgId: Long? = null
    var accountId: Long? = null
    var appId: String? = null
    var userId: Long? = null
    var openid: String? = null
    var type: String? = null
    var sendFrom: Int? = null
    var content: String? = null
    var mediaId: String? = null
    var mediaUrl: String? = null
    var recognition: String? = null
    var format: String? = null
    var title: String? = null
    var description: String? = null
    var thumbMediaId: String? = null
    var thumbMediaUrl: String? = null
    var url: String? = null
    var locationX: Double? = null
    var locationY: Double? = null
    var scale: Double? = null
    var label: String? = null
    var articles: List<Article>? = null
    var musicUrl: String? = null
    var hqMusicUrl: String? = null
    var event: String? = null
    var eventKey: String? = null

    class Article {
        @field:NotEmpty(message = "图文消息标题不能为空", groups = [NewsBuilder::class])
        var title: String? = null
        @field:NotEmpty(message = "图文消息描述不能为空", groups = [NewsBuilder::class])
        var description: String? = null
        @field:NotEmpty(message = "图片链接不能为空", groups = [NewsBuilder::class])
        var picUrl: String? = null
        @field:NotEmpty(message = "点击图文消息跳转链接不能为空", groups = [NewsBuilder::class])
        var url: String? = null
    }
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

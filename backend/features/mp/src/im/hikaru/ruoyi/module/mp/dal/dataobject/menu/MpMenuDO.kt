package im.hikaru.ruoyi.module.mp.dal.dataobject.menu

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import kotlinx.datetime.LocalDateTime
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.common.api.WxConsts.MenuButtonType

class MpMenuDO : TenantBaseDO {
    companion object {
        const val ID_ROOT: Long = 0L
    }

    var id: Long? = null
    var accountId: Long? = null
    var appId: String? = null
    var name: String? = null
    var menuKey: String? = null
    var parentId: Long? = null
    var type: String? = null
    var url: String? = null
    var miniProgramAppId: String? = null
    var miniProgramPagePath: String? = null
    var articleId: String? = null
    var replyMessageType: String? = null
    var replyContent: String? = null
    var replyMediaId: String? = null
    var replyMediaUrl: String? = null
    var replyTitle: String? = null
    var replyDescription: String? = null
    var replyThumbMediaId: String? = null
    var replyThumbMediaUrl: String? = null
    var replyArticles: List<MpMessageDO.Article>? = null
    var replyMusicUrl: String? = null
    var replyHqMusicUrl: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

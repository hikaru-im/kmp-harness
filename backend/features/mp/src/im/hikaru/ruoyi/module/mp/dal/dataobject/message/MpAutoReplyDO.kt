package im.hikaru.ruoyi.module.mp.dal.dataobject.message

import im.hikaru.ruoyi.framework.common.util.collection.SetUtils
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.enums.message.MpAutoReplyMatchEnum
import im.hikaru.ruoyi.module.mp.enums.message.MpAutoReplyTypeEnum
import kotlinx.datetime.LocalDateTime
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.common.api.WxConsts.XmlMsgType

class MpAutoReplyDO : TenantBaseDO {
    companion object {
        val REQUEST_MESSAGE_TYPE: Set<String> = setOf(
            WxConsts.XmlMsgType.TEXT,
            WxConsts.XmlMsgType.IMAGE,
            WxConsts.XmlMsgType.VOICE,
            WxConsts.XmlMsgType.VIDEO,
            WxConsts.XmlMsgType.SHORTVIDEO,
            WxConsts.XmlMsgType.LOCATION,
            WxConsts.XmlMsgType.LINK,
        )
    }

    var id: Long? = null
    var accountId: Long? = null
    var appId: String? = null
    var type: Int? = null
    var requestKeyword: String? = null
    var requestMatch: Int? = null
    var requestMessageType: String? = null
    var responseMessageType: String? = null
    var responseContent: String? = null
    var responseMediaId: String? = null
    var responseMediaUrl: String? = null
    var responseTitle: String? = null
    var responseDescription: String? = null
    var responseThumbMediaId: String? = null
    var responseThumbMediaUrl: String? = null
    var responseArticles: List<MpMessageDO.Article>? = null
    var responseMusicUrl: String? = null
    var responseHqMusicUrl: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

package im.hikaru.ruoyi.module.mp.dal.dataobject.material

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import kotlinx.datetime.LocalDateTime
import me.chanjar.weixin.common.api.WxConsts

class MpMaterialDO : TenantBaseDO {
    var id: Long? = null
    var accountId: Long? = null
    var appId: String? = null
    var mediaId: String? = null
    var type: String? = null
    var permanent: Boolean? = null
    var url: String? = null
    var name: String? = null
    var mpUrl: String? = null
    var title: String? = null
    var introduction: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

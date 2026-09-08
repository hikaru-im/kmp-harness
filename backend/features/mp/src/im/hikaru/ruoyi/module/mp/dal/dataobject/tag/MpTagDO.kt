package im.hikaru.ruoyi.module.mp.dal.dataobject.tag

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import kotlinx.datetime.LocalDateTime
import me.chanjar.weixin.mp.bean.tag.WxUserTag

class MpTagDO : TenantBaseDO {
    var id: Long? = null
    var tagId: Long? = null
    var name: String? = null
    var count: Int? = null
    var accountId: Long? = null
    var appId: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

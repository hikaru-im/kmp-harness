package im.hikaru.ruoyi.module.member.dal.dataobject.tag

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class MemberTagDO : TenantBaseDO {
    var id: Long? = null
    var name: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

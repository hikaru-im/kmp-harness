package im.hikaru.ruoyi.module.member.dal.dataobject.address

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class MemberAddressDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var name: String? = null
    var mobile: String? = null
    var areaId: Long? = null
    var detailAddress: String? = null
    var defaultStatus: Boolean? = null
    var version: Long = 1
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

package im.hikaru.ruoyi.module.member.dal.dataobject.config

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class MemberConfigDO : TenantBaseDO {
    var id: Long? = null
    var pointTradeDeductEnable: Boolean? = null
    var pointTradeDeductUnitPrice: Int? = null
    var pointTradeDeductMaxPrice: Int? = null
    var pointTradeGivePoint: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

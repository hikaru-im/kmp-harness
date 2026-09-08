package im.hikaru.ruoyi.module.pay.dal.dataobject.wallet

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class PayWalletDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var userType: Int? = null
    var balance: Int? = null
    var freezePrice: Int? = null
    var totalExpense: Int? = null
    var totalRecharge: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

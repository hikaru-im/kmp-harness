package im.hikaru.ruoyi.module.pay.dal.dataobject.wallet

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class PayWalletRechargePackageDO : TenantBaseDO {
    var id: Long? = null
    var name: String? = null
    var payPrice: Int? = null
    var bonusPrice: Int? = null
    var status: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

package im.hikaru.ruoyi.module.pay.dal.dataobject.wallet

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import kotlinx.datetime.LocalDateTime

class PayWalletTransactionDO : TenantBaseDO {
    var id: Long? = null
    var no: String? = null
    var walletId: Long? = null
    var bizType: Int? = null
    var bizId: String? = null
    var title: String? = null
    var price: Int? = null
    var balance: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

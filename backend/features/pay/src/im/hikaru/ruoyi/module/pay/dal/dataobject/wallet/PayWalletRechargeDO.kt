package im.hikaru.ruoyi.module.pay.dal.dataobject.wallet

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import kotlinx.datetime.LocalDateTime

class PayWalletRechargeDO : TenantBaseDO {
    var id: Long? = null
    var walletId: Long? = null
    var totalPrice: Int? = null
    var payPrice: Int? = null
    var bonusPrice: Int? = null
    var packageId: Long? = null
    var payStatus: Boolean? = null
    var payOrderId: Long? = null
    var payChannelCode: String? = null
    var payTime: LocalDateTime? = null
    var payRefundId: Long? = null
    var refundTotalPrice: Int? = null
    var refundPayPrice: Int? = null
    var refundBonusPrice: Int? = null
    var refundTime: LocalDateTime? = null
    var refundStatus: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

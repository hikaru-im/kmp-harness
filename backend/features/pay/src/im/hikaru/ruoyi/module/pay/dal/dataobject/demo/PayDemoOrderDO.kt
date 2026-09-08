package im.hikaru.ruoyi.module.pay.dal.dataobject.demo

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class PayDemoOrderDO : TenantBaseDO {
    var id: Long? = null
    var userId: Long? = null
    var spuId: Long? = null
    var spuName: String? = null
    var price: Int? = null
    var payStatus: Boolean? = null
    var payOrderId: Long? = null
    var payTime: LocalDateTime? = null
    var payChannelCode: String? = null
    var payRefundId: Long? = null
    var refundPrice: Int? = null
    var refundTime: LocalDateTime? = null
    var transferChannelPackageInfo: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

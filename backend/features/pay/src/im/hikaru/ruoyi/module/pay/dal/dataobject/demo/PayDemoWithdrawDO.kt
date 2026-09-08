package im.hikaru.ruoyi.module.pay.dal.dataobject.demo

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import im.hikaru.ruoyi.module.pay.enums.demo.PayDemoWithdrawStatusEnum
import im.hikaru.ruoyi.module.pay.enums.demo.PayDemoWithdrawTypeEnum
import kotlinx.datetime.LocalDateTime

class PayDemoWithdrawDO : TenantBaseDO {
    var id: Long? = null
    var subject: String? = null
    var price: Int? = null
    var userAccount: String? = null
    var userName: String? = null
    var type: Int? = null
    var status: Int? = null
    var payTransferId: Long? = null
    var transferChannelCode: String? = null
    var transferTime: LocalDateTime? = null
    var transferErrorMsg: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

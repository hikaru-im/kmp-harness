package im.hikaru.ruoyi.module.pay.dal.dataobject.notify

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.order.PayOrderDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.refund.PayRefundDO
import im.hikaru.ruoyi.module.pay.dal.dataobject.transfer.PayTransferDO
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyStatusEnum
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyTypeEnum
import kotlinx.datetime.LocalDateTime

class PayNotifyTaskDO : TenantBaseDO {
    var id: Long? = null
    var appId: Long? = null
    var type: Int? = null
    var dataId: Long? = null
    var merchantOrderId: String? = null
    var merchantRefundId: String? = null
    var merchantTransferId: String? = null
    var status: Int? = null
    var nextNotifyTime: LocalDateTime? = null
    var lastExecuteTime: LocalDateTime? = null
    var notifyTimes: Int? = null
    var maxNotifyTimes: Int? = null
    var notifyUrl: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null

    companion object {
        val NOTIFY_FREQUENCY_SECONDS: LongArray = longArrayOf(
            15,
            15,
            30,
            180,
            1800,
            1800,
            1800,
            3600,
        )
        const val DEFAULT_MAX_NOTIFY_TIMES = 9
    }
}

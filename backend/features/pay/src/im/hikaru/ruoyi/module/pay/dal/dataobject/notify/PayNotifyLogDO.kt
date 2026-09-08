package im.hikaru.ruoyi.module.pay.dal.dataobject.notify

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import im.hikaru.ruoyi.module.pay.enums.notify.PayNotifyStatusEnum
import kotlinx.datetime.LocalDateTime

class PayNotifyLogDO : TenantBaseDO {
    var id: Long? = null
    var taskId: Long? = null
    var notifyTimes: Int? = null
    var response: String? = null
    var status: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

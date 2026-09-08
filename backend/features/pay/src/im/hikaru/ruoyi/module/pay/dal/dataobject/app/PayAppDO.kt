package im.hikaru.ruoyi.module.pay.dal.dataobject.app

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class PayAppDO : TenantBaseDO {
    var id: Long? = null
    var appKey: String? = null
    var name: String? = null
    var status: Int? = null
    var remark: String? = null
    var orderNotifyUrl: String? = null
    var refundNotifyUrl: String? = null
    var transferNotifyUrl: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

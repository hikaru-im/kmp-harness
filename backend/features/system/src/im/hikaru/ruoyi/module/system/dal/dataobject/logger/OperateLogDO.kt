package im.hikaru.ruoyi.module.system.dal.dataobject.logger

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class OperateLogDO : TenantBaseDO {
    var id: Long? = null
    var traceId: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var type: String? = null
    var subType: String? = null
    var bizId: Long? = null
    var action: String? = null
    var extra: String? = null
    var requestMethod: String? = null
    var requestUrl: String? = null
    var userIp: String? = null
    var userAgent: String? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

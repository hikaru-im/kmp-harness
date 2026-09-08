package im.hikaru.ruoyi.module.system.dal.dataobject.logger

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class LoginLogDO : TenantBaseDO {
    var id: Long? = null
    var logType: Int? = null
    var traceId: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var username: String? = null
    var result: Int? = null
    var userIp: String? = null
    var userAgent: String? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

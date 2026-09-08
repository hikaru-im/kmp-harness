package im.hikaru.ruoyi.module.system.dal.dataobject.dept

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class DeptDO : TenantBaseDO {
    companion object { const val PARENT_ID_ROOT = 0L }
    var id: Long? = null
    var name: String? = null
    var parentId: Long? = null
    var sort: Int? = null
    var leaderUserId: Long? = null
    var phone: String? = null
    var email: String? = null
    var status: Int? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

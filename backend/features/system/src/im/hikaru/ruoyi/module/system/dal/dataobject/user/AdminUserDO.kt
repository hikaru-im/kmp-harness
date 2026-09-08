package im.hikaru.ruoyi.module.system.dal.dataobject.user

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class AdminUserDO : TenantBaseDO {
    var id: Long? = null
    var username: String? = null
    var password: String? = null
    var nickname: String? = null
    var remark: String? = null
    var deptId: Long? = null
    var postIds: Set<Long>? = null
    var email: String? = null
    var mobile: String? = null
    var sex: Int? = null
    var avatar: String? = null
    var status: Int? = null
    var loginIp: String? = null
    var loginDate: LocalDateTime? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    override var tenantId: Long? = null
}

package im.hikaru.ruoyi.module.system.dal.dataobject.permission

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class RoleDO : TenantBaseDO {
    var id: Long? = null; var name: String? = null; var code: String? = null; var sort: Int? = null; var status: Int? = null
    var type: Int? = null; var remark: String? = null; var dataScope: Int? = null; var dataScopeDeptIds: Set<Long>? = null
    override var createTime: LocalDateTime? = null; override var updateTime: LocalDateTime? = null; override var creator: String? = null; override var updater: String? = null
    override var deleted: Boolean = false; override var tenantId: Long? = null
}

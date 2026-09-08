package im.hikaru.ruoyi.module.system.dal.dataobject.permission

import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class RoleMenuDO : TenantBaseDO {
    var id: Long? = null; var roleId: Long? = null; var menuId: Long? = null
    override var createTime: LocalDateTime? = null; override var updateTime: LocalDateTime? = null; override var creator: String? = null; override var updater: String? = null
    override var deleted: Boolean = false; override var tenantId: Long? = null
}

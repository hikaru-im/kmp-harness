package im.hikaru.ruoyi.module.system.dal.dataobject.permission

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class UserRoleDO : BaseEntity {
    var id: Long? = null; var userId: Long? = null; var roleId: Long? = null; var tenantId: Long? = null
    override var createTime: LocalDateTime? = null; override var updateTime: LocalDateTime? = null; override var creator: String? = null; override var updater: String? = null
    override var deleted: Boolean = false
}

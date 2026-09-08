package im.hikaru.ruoyi.module.system.dal.dataobject.tenant

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class TenantPackageDO : BaseEntity {
    var id: Long? = null; var name: String? = null; var status: Int? = null; var remark: String? = null; var menuIds: Set<Long>? = null
    override var createTime: LocalDateTime? = null; override var updateTime: LocalDateTime? = null; override var creator: String? = null; override var updater: String? = null; override var deleted: Boolean = false
}

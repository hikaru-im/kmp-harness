package im.hikaru.ruoyi.module.system.dal.dataobject.notice

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.tenant.core.db.TenantBaseDO
import kotlinx.datetime.LocalDateTime

class NoticeDO : BaseEntity, TenantBaseDO {
    var id: Long? = null
    var title: String? = null
    var type: Int? = null
    var content: String? = null
    var status: Int? = null
    override var tenantId: Long? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}

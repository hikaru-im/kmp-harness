package im.hikaru.ruoyi.module.system.dal.dataobject.dept

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class PostDO : BaseEntity {
    var id: Long? = null
    var name: String? = null
    var code: String? = null
    var sort: Int? = null
    var status: Int? = null
    var remark: String? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    var tenantId: Long? = null
}

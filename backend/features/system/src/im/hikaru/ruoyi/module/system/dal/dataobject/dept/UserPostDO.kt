package im.hikaru.ruoyi.module.system.dal.dataobject.dept

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class UserPostDO : BaseEntity {
    var id: Long? = null
    var userId: Long? = null
    var postId: Long? = null
    override var createTime: LocalDateTime? = null
    override var updateTime: LocalDateTime? = null
    override var creator: String? = null
    override var updater: String? = null
    override var deleted: Boolean = false
    var tenantId: Long? = null
}

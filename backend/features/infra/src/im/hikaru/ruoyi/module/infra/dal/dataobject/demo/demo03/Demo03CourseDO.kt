package im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class Demo03CourseDO : BaseEntity {
    var id: Long? = null
    var studentId: Long? = null
    var name: String? = null
    var score: Int? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}
